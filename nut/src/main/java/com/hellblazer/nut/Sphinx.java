/*
 * Copyright (c) 2024 Hal Hildebrand. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 *  more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.hellblazer.nut;

import com.codahale.shamir.Scheme;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hellblazer.nut.proto.EncryptedShare;
import com.hellblazer.nut.proto.PublicKey_;
import com.hellblazer.nut.proto.Share;
import com.hellblazer.nut.proto.Status;
import com.salesforce.apollo.comm.grpc.ServerContextSupplier;
import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.cryptography.cert.CertificateWithPrivateKey;
import com.salesforce.apollo.cryptography.cert.Certificates;
import com.salesforce.apollo.cryptography.ssl.CertificateValidator;
import com.salesforce.apollo.membership.stereotomy.ControlledIdentifierMember;
import com.salesforce.apollo.stereotomy.KERL;
import com.salesforce.apollo.stereotomy.StereotomyImpl;
import com.salesforce.apollo.stereotomy.StereotomyKeyStore;
import com.salesforce.apollo.stereotomy.db.UniKERLDirect;
import com.salesforce.apollo.stereotomy.event.proto.Ident;
import com.salesforce.apollo.stereotomy.identifier.Identifier;
import com.salesforce.apollo.stereotomy.identifier.SelfAddressingIdentifier;
import com.salesforce.apollo.stereotomy.jks.FileKeyStore;
import com.salesforce.apollo.thoth.LoggingOutputStream;
import com.salesforce.apollo.utils.BbBackedInputStream;
import com.salesforce.apollo.utils.Entropy;
import com.salesforce.apollo.utils.Utils;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.inprocess.InProcessSocketAddress;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.database.core.H2Database;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.ui.ConsoleUIService;
import liquibase.ui.UIService;
import org.h2.jdbc.JdbcConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Guards the entry to the Sky.  This is the initialization of identity, the unwrapping of key to the keystore for this
 * identity, the attestation of the identity, and finally joining the Sky cluster
 *
 * @author hal.hildebrand
 **/
public class Sphinx {

    private static final String                     AES        = "AES";
    private static final String                     ALGORITHM  = "AES/GCM/NoPadding";
    private static final int                        TAG_LENGTH = 128; // bits
    private static final int                        IV_LENGTH  = 12; // bytes
    private static final Logger                     log        = LoggerFactory.getLogger(Sphinx.class);
    private final        AtomicBoolean              started    = new AtomicBoolean();
    private final        AtomicReference<char[]>    root       = new AtomicReference<>();
    private final        SkyConfiguration           configuration;
    private final        Service                    service    = new Service();
    private volatile     SkyApplication             application;
    private volatile     StereotomyImpl             stereotomy;
    private volatile     ControlledIdentifierMember member;
    private volatile     KERL.AppendKERL            kerl;
    private volatile     Runnable                   closeApiServer;

    public Sphinx(InputStream configuration) {
        this(SkyConfiguration.from(configuration));
    }

    public Sphinx(SkyConfiguration configuration) {
        this(configuration, null);
    }

    public Sphinx(InputStream configuration, String devSecret) {
        this(SkyConfiguration.from(configuration), devSecret);
    }

    public Sphinx(SkyConfiguration configuration, String devSecret) {
        this.configuration = configuration;
        initializeSchema();
        Connection connection = null;
        try {
            connection = getConnection();
        } catch (SQLException e) {
            log.error("Unable to create JDBC connection: {}", configuration.identity.kerlURL());
        }
        kerl = new UniKERLDirect(connection, configuration.identity.digestAlgorithm());

        if (devSecret != null) {
            log.warn("Operating in development mode with dev secret");
            unwrap(devSecret.toCharArray());
            started.set(true);
        } else {
            log.info("Operating in sealed mode: {}", configuration.shamir);
        }
    }

    /**
     * Decrypts encrypted message (see {@link #encrypt(byte[], SecretKey, byte[])}).
     *
     * @param cipherMessage  iv with ciphertext
     * @param secretKey      used to decrypt
     * @param associatedData optional, additional (public) data to verify on decryption with GCM auth associatedData
     * @return original plaintext
     * @throws Exception if anything goes wrong
     */
    public static byte[] decrypt(byte[] cipherMessage, SecretKey secretKey, byte[] associatedData) {
        try {
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            //use first 12 bytes for iv
            AlgorithmParameterSpec gcmIv = new GCMParameterSpec(TAG_LENGTH, cipherMessage, 0, IV_LENGTH);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmIv);

            if (associatedData != null) {
                cipher.updateAAD(associatedData);
            }
            //use everything from 12 bytes on as ciphertext
            return cipher.doFinal(cipherMessage, IV_LENGTH, cipherMessage.length - IV_LENGTH);
        } catch (Throwable t) {
            throw new IllegalStateException("Unable to decrypt", t);
        }
    }

    /**
     * Encrypt a plaintext with given key.
     *
     * @param plaintext      to encrypt
     * @param secretKey      to encrypt, must be AES type, see {@link SecretKeySpec}
     * @param associatedData optional, additional (public) data to verify on decryption with GCM auth associatedData
     * @return encrypted message
     * @throws Exception if anything goes wrong
     */
    public static byte[] encrypt(byte[] plaintext, SecretKey secretKey, byte[] associatedData) {
        try {
            byte[] iv = new byte[IV_LENGTH]; //NEVER REUSE THIS IV WITH SAME KEY
            Entropy.nextSecureBytes(iv);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv); //128 bit auth associatedData length
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            if (associatedData != null) {
                cipher.updateAAD(associatedData);
            }

            byte[] cipherText = cipher.doFinal(plaintext);

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);
            return byteBuffer.array();
        } catch (Throwable t) {
            throw new IllegalStateException("Unable to encrypt", t);
        }
    }

    public static void main(String[] argv) throws Exception {
        if (argv.length < 1 || argv.length > 2) {
            System.err.println("Usage: Sphinx <config file name> <<devSecret>>");
            System.exit(1);
        }
        var file = new File(System.getProperty("user.dir"), argv[0]);
        if (!file.exists()) {
            System.err.printf("Configuration file: %s does not exist", argv[0]).println();
            System.exit(1);
        }
        if (!file.isFile()) {
            System.err.printf("Configuration file: %s is a directory", argv[0]).println();
            System.exit(1);
        }
        log.info("Reading configuration from: {}", file.getAbsolutePath());
        SkyConfiguration config = null;
        try (var fis = new FileInputStream(file)) {
            config = SkyConfiguration.from(fis);
        }

        Sphinx sphinx;
        if (argv.length == 2) {
            sphinx = new Sphinx(config, argv[1]);
        } else {
            sphinx = new Sphinx(config);
        }

        sphinx.start();
        var t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "Keeper");
        t.setDaemon(false);
        t.start();
    }

    public SocketAddress getApiEndpoint() {
        return configuration.apiEndpoint;
    }

    public SocketAddress getClusterEndpoint() {
        return configuration.clusterEndpoint;
    }

    public KeyPair getSessionKeyPair() {
        return service.sessionKeyPair;
    }

    public void shutdown() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        root.set(new char[0]);
        service.shares.clear();
        service.sessionKeyPair = null;
        final var current = closeApiServer;
        closeApiServer = null;
        if (current != null) {
            current.run();
        }
        final var currentApplication = application;
        application = null;
        if (currentApplication != null) {
            currentApplication.shutdown();
        }
        var id = member == null ? null : member.getId();
        member = null;
        kerl = null;
        stereotomy = null;
        log.warn("Server shutdown on: {}", id == null ? "<sealed>" : id.toString());
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        var local = configuration.apiEndpoint instanceof InProcessSocketAddress;
        if (local) {
            log.info("Starting in process API server: {}", configuration.apiEndpoint);
            var server = InProcessServerBuilder.forAddress(configuration.apiEndpoint)
                                               .addService(new SphynxServer(service))
                                               .executor(Executors.newVirtualThreadPerTaskExecutor())
                                               .build();
            closeApiServer = Utils.wrapped(() -> {
                server.shutdown();
            }, log);
            try {
                server.start();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to start local api server on: %s".formatted(member.getId()));
            }
        } else {
            log.info("Starting in MTLS API server: {}", configuration.apiEndpoint);
            var server = apiServer();
            closeApiServer = Utils.wrapped(server::stop, log);
            try {
                server.start();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to start local api server on: %s".formatted(member.getId()));
            }
        }
    }

    private ApiServer apiServer() {
        CertificateWithPrivateKey apiIdentity = createIdentity((InetSocketAddress) configuration.apiEndpoint);
        var server = new ApiServer(configuration.apiEndpoint, ClientAuth.REQUIRE, "foo", new ServerContextSupplier() {

            @Override
            public SslContext forServer(ClientAuth clientAuth, String alias, CertificateValidator validator,
                                        Provider provider) {
                return ApiServer.forServer(clientAuth, alias, apiIdentity.getX509Certificate(),
                                           apiIdentity.getPrivateKey(), validator);
            }

            @Override
            public Digest getMemberId(X509Certificate key) {
                return Digest.NONE;
            }
        }, validator(), new SphynxServer(service));
        return server;
    }

    private CertificateWithPrivateKey createIdentity(InetSocketAddress address) {
        KeyPair keyPair = configuration.identity.signatureAlgorithm().generateKeyPair();
        var notBefore = Instant.now();
        var notAfter = Instant.now().plusSeconds(10_000);
        X509Certificate generated = Certificates.selfSign(false, Utils.encode(
        configuration.identity.digestAlgorithm().getOrigin(), address.getHostName(), address.getPort(),
        keyPair.getPublic()), keyPair, notBefore, notAfter, Collections.emptyList());
        return new CertificateWithPrivateKey(generated, keyPair.getPrivate());
    }

    private ManagedChannel forSeeds() {
        var seeds = configuration.seeds;
        var local = seeds.isEmpty() ? false : seeds.getFirst() instanceof InProcessSocketAddress;
        var builder = local ? InProcessChannelBuilder.forTarget("service") : ManagedChannelBuilder.forTarget("service");
        return builder.nameResolverFactory(new SimpleNameResolverFactory(seeds))
                      .defaultLoadBalancingPolicy("round_robin")
                      .usePlaintext()
                      .build();
    }

    private JdbcConnection getConnection() throws SQLException {
        return new JdbcConnection(configuration.identity.kerlURL(), new Properties(), "", "", false);
    }

    private void initializeSchema() {
        ConsoleUIService service = (ConsoleUIService) Scope.getCurrentScope().get(Scope.Attr.ui, UIService.class);
        service.setOutputStream(new PrintStream(
        new LoggingOutputStream(LoggerFactory.getLogger("liquibase"), LoggingOutputStream.LogLevel.INFO)));
        var database = new H2Database();
        try (var connection = getConnection()) {
            database.setConnection(new liquibase.database.jvm.JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("stereotomy/initialize.xml", new ClassLoaderResourceAccessor(),
                                                     database)) {
                liquibase.update((String) null);
            } catch (LiquibaseException e) {
                throw new IllegalStateException(e);
            }
        } catch (SQLException e1) {
            throw new IllegalStateException(e1);
        }
    }

    private void startSky() {
        application = new SkyApplication(configuration, member);
        application.start();
    }

    private void testify() {
        log.info("Attesting identity, seeds: {} on: {}", configuration.seeds, member.getId());
    }

    private void unwrap(byte[] rs) {
        var pwd = new char[rs.length];
        for (var i = 0; i < rs.length; i++) {
            pwd[i] = (char) rs[i];
        }
        unwrap(pwd);
    }

    // Unwrap the root identity keystore and establish either a new identifier or resume the previous identifier
    private void unwrap(char[] pwd) {
        root.set(pwd);
        StereotomyKeyStore keyStore = null;
        var storeFile = configuration.identity.keyStore().toFile();
        InputStream is = null;
        try {
            if (storeFile.exists()) {
                is = new FileInputStream(storeFile);
            }
            final var ks = KeyStore.getInstance(configuration.identity.keyStoreType());
            ks.load(is, ((Supplier<char[]>) root::get).get());
            keyStore = new FileKeyStore(ks, root::get, storeFile);
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            log.error("Cannot load root: {}", storeFile.getAbsolutePath());
            throw new IllegalStateException("Cannot load root", e);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                // ignored
            }
        }
        Identifier id = null;
        try (var dis = new FileInputStream(configuration.identity.identityFile().toFile());
             var baos = new ByteArrayOutputStream()) {
            Utils.copy(dis, baos);
            id = Identifier.from(Ident.parseFrom(baos.toByteArray()));
        } catch (FileNotFoundException e) {
            // new identifier
        } catch (IOException e) {
            log.error("Unable to read identifier file: {}", configuration.identity.identityFile().toAbsolutePath(), e);
            throw new IllegalStateException(
            "Unable to read identifier file: %s".formatted(configuration.identity.identityFile().toAbsolutePath()), e);
        }
        stereotomy = new StereotomyImpl(keyStore, kerl, new SecureRandom());
        if (id == null) {
            member = new ControlledIdentifierMember(stereotomy.newIdentifier());
            try (var fos = new FileOutputStream(configuration.identity.identityFile().toFile());
                 var bbis = BbBackedInputStream.aggregate(
                 member.getIdentifier().getIdentifier().toIdent().toByteString())) {
                Utils.copy(bbis, fos);
            } catch (IOException e) {
                log.error("Unable to create identifier file: {}",
                          configuration.identity.identityFile().toAbsolutePath(), e);
                throw new IllegalStateException("Unable to create identifier file: %s".formatted(
                configuration.identity.identityFile().toAbsolutePath()), e);
            }
            log.info("New identifier: {} file: {}", member.getId(),
                     configuration.identity.identityFile().toAbsolutePath());
        } else {
            member = new ControlledIdentifierMember(stereotomy.controlOf((SelfAddressingIdentifier) id));
            log.info("Resuming identifier: {} file: {}", member.getId(),
                     configuration.identity.identityFile().toAbsolutePath());
        }
        testify();
    }

    private CertificateValidator validator() {
        return new CertificateValidator() {
            @Override
            public void validateClient(X509Certificate[] chain) throws CertificateException {
            }

            @Override
            public void validateServer(X509Certificate[] chain) throws CertificateException {
            }
        };
    }

    public enum UNWRAPPING {
        SHAMIR, DELEGATED;
    }

    public class Service {
        private final    Map<Integer, byte[]> shares = new ConcurrentHashMap<>();
        private volatile KeyPair              sessionKeyPair;

        public Status apply(EncryptedShare eShare) {
            var secretKey = configuration.identity.encryptionAlgorithm()
                                                  .decapsulate(sessionKeyPair.getPrivate(),
                                                               eShare.getEncapsulation().toByteArray(), AES);
            var decrypted = decrypt(eShare.getShare().toByteArray(), secretKey,
                                    eShare.getAssociatedData().toByteArray());
            try {
                var share = Share.parseFrom(decrypted);
                shares.put(share.getKey(), share.getShare().toByteArray());
                return Status.newBuilder().setShares(shares.size()).setSuccess(true).build();
            } catch (InvalidProtocolBufferException e) {
                log.info("Not a valid share: {}", e.toString());
                return Status.newBuilder().setShares(shares.size()).setSuccess(false).build();
            }
        }

        public Status seal() {
            root.set(new char[0]);
            sessionKeyPair = null;
            application.shutdown();
            return Status.newBuilder().setSuccess(true).build();
        }

        public PublicKey_ sessionKey() {
            var alg = configuration.identity.encryptionAlgorithm();
            return PublicKey_.newBuilder()
                             .setAlgorithm(PublicKey_.algo.forNumber(alg.getCode()))
                             .setPublicKey(ByteString.copyFrom(alg.encode(sessionKeyPair.getPublic())))
                             .build();
        }

        public Status unseal() {
            if (application != null) {
                return Status.newBuilder().setSuccess(true).setShares(shares.size()).build();
            }
            sessionKeyPair = configuration.identity.encryptionAlgorithm().generateKeyPair();
            return Status.getDefaultInstance();
        }

        public Status unwrap() {
            if (shares.size() < configuration.shamir.threshold()) {
                log.info("Cannot unwrap with: {} shares configured: {} out of {}", shares.size(),
                         configuration.shamir.threshold(), configuration.shamir.shares());
                return Status.newBuilder().setSuccess(false).setShares(shares.size()).build();
            }
            log.info("Unwrapping with: {} shares configured: {} out of {}", shares.size(),
                     configuration.shamir.threshold(), configuration.shamir.shares());
            var scheme = new Scheme(new SecureRandom(), configuration.shamir.shares(),
                                    configuration.shamir.threshold());
            Sphinx.this.unwrap(scheme.join(shares));
            var status = Status.newBuilder().setShares(shares.size()).setSuccess(true).build();
            shares.clear();
            sessionKeyPair = null;
            return status;
        }
    }
}
