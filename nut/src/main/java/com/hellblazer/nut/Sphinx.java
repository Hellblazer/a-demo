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
import com.hellblazer.nut.comms.SphynxServer;
import com.hellblazer.nut.proto.*;
import com.hellblazer.nut.comms.ApiServer;
import com.salesforce.apollo.archipelago.EndpointProvider;
import com.salesforce.apollo.comm.grpc.ServerContextSupplier;
import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.cryptography.DigestAlgorithm;
import com.salesforce.apollo.cryptography.cert.CertificateWithPrivateKey;
import com.salesforce.apollo.cryptography.cert.Certificates;
import com.salesforce.apollo.cryptography.proto.Digeste;
import com.salesforce.apollo.cryptography.ssl.CertificateValidator;
import com.salesforce.apollo.delphinius.Oracle;
import com.salesforce.apollo.fireflies.View;
import com.salesforce.apollo.stereotomy.Stereotomy;
import com.salesforce.apollo.stereotomy.identifier.SelfAddressingIdentifier;
import com.salesforce.apollo.thoth.LoggingOutputStream;
import com.salesforce.apollo.utils.Entropy;
import com.salesforce.apollo.utils.Utils;
import io.grpc.StatusRuntimeException;
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
import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.security.KeyPair;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Guards the entry to the Sky.  This is the initialization of identity, the unwrapping of key to the keystore for this
 * identity, the attestation of the identity, and finally joining the Sky cluster
 *
 * @author hal.hildebrand
 **/
public class Sphinx {

    public static final  String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    public static final  String AES                = "AES";
    public static final  int    TAG_LENGTH         = 128; // bits
    public static final  int    IV_LENGTH          = 16; // bytes
    private static final Logger log                = LoggerFactory.getLogger(Sphinx.class);

    private final    AtomicBoolean           started   = new AtomicBoolean();
    private final    SkyConfiguration        configuration;
    private final    Service                 service   = new Service();
    private final    SecureRandom            entropy;
    private final    CompletableFuture<Void> onStart   = new CompletableFuture<>();
    private volatile SanctumSanctorum        sanctum;
    private volatile SkyApplication          application;
    private volatile Runnable                closeApiServer;
    private          SocketAddress           apiAddress;
    private          CompletableFuture<Void> onFailure = new CompletableFuture<>();

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
        this.entropy = new SecureRandom();
        this.configuration = configuration;
        initializeSchema();

        if (devSecret != null) {
            log.warn("Operating in development mode with dev secret");
            unwrap(configuration.viewGossipDuration, devSecret.getBytes(Charset.defaultCharset()));
        } else {
            log.info("Operating in sealed mode: {}", configuration.shamir);
        }
    }

    public static byte[] decrypt(Encrypted encrypted, SecretKey secretKey) {
        try {
            final Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            AlgorithmParameterSpec gcmIv = new GCMParameterSpec(TAG_LENGTH, encrypted.iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmIv);

            if (encrypted.associatedData != null) {
                cipher.updateAAD(encrypted.associatedData);
            }
            return cipher.doFinal(encrypted.cipherText);
        } catch (Throwable t) {
            throw new IllegalStateException("Unable to decrypt", t);
        }
    }

    public static Encrypted encrypt(byte[] plaintext, SecretKey secretKey, byte[] associatedData) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            Entropy.nextSecureBytes(iv);
            final Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv); //128 bit auth associatedData length
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            if (associatedData != null) {
                cipher.updateAAD(associatedData);
            }

            return new Encrypted(cipher.doFinal(plaintext), iv, associatedData);
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
        SkyConfiguration config;
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

    public boolean active() {
        var current = application;
        return current != null && current.active();
    }

    public SocketAddress getApiEndpoint() {
        return apiAddress;
    }

    public CompletableFuture<Void> getOnFailure() {
        return onFailure;
    }

    public void setOnFailure(CompletableFuture<Void> onFailure) {
        this.onFailure = onFailure;
    }

    public Digest id() {
        var current = sanctum;
        return current != null ? current.getId() : null;
    }

    public String logState() {
        var current = application;
        return current == null ? "Unavailable" : current.logState();
    }

    public void shutdown() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        var id = sanctum == null ? null : sanctum.getId();
        service.seal();
        final var current = closeApiServer;
        closeApiServer = null;
        if (current != null) {
            current.run();
        }
        log.warn("Server shutdown on: {}", id == null ? "<sealed>" : id.toString());
    }

    /**
     * @return the future completed when the View has been started
     */
    public CompletableFuture<Void> start() {
        if (!started.compareAndSet(false, true)) {
            log.info("Already started: {}", sanctum.getId());
            return onStart;
        }
        var socketAddress = configuration.endpoints.apiEndpoint();
        var local = socketAddress instanceof InProcessSocketAddress;
        if (local) {
            log.info("Starting in process API server: {}", socketAddress);
            var server = InProcessServerBuilder.forAddress(socketAddress)
                                               .addService(new SphynxServer(service))
                                               .executor(Executors.newVirtualThreadPerTaskExecutor())
                                               .build();
            apiAddress = socketAddress;
            closeApiServer = Utils.wrapped(() -> {
                server.shutdown();
            }, log);
            try {
                server.start();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to start local api server on: %s".formatted(sanctum.getId()));
            }
        }
        log.info("Starting in MTLS API server: {}", configuration.endpoints.apiEndpoint());
        var server = apiServer();
        closeApiServer = Utils.wrapped(server::stop, log);
        try {
            server.start();
        } catch (IOException e) {
            throw new IllegalStateException(
            "Unable to start local api server on: %s".formatted(sanctum == null ? "<null>" : sanctum.getId()));
        }
        apiAddress = server.getAddress();
        log.info("Started API server: {}", apiAddress);
        return onStart;
    }

    protected Oracle getDelphi() {
        return application.getSky().getDelphi();
    }

    private ApiServer apiServer() {
        var address = configuration.endpoints.apiEndpoint();
        log.info("Api server address: {}", address);
        CertificateWithPrivateKey apiIdentity = createIdentity((InetSocketAddress) address);
        return new ApiServer(address, ClientAuth.REQUIRE, "foo", new ServerContextSupplier() {

            @Override
            public SslContext forServer(ClientAuth clientAuth, String alias, CertificateValidator validator,
                                        Provider provider) {
                return ApiServer.forServer(clientAuth, alias, apiIdentity.getX509Certificate(),
                                           apiIdentity.getPrivateKey(), validator);
            }

            @Override
            public Digest getMemberId(X509Certificate key) {
                var decoded = Stereotomy.decode(key);
                if (decoded.isEmpty()) {
                    throw new NoSuchElementException("Cannot decode certificate: %s".formatted(key));
                }
                return ((SelfAddressingIdentifier) decoded.get().identifier()).getDigest();
            }
        }, validator(), new SphynxServer(service));
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

    // Unwrap the root identity keystore and establish either a new identifier or resume the previous identifier
    private Digest unwrap(Duration viewGossipDuration, byte[] master) {
        sanctum = new SanctumSanctorum(master, DigestAlgorithm.BLAKE2S_256, entropy, configuration);
        application = new SkyApplication(configuration, sanctum, onFailure);

        List<SocketAddress> approaches = configuration.approaches == null ? Collections.emptyList()
                                                                          : configuration.approaches.stream()
                                                                                                    .map(
                                                                                                    ep -> EndpointProvider.reify(
                                                                                                    ep))
                                                                                                    .map(
                                                                                                    e -> (SocketAddress) e)
                                                                                                    .toList();
        var seeds = configuration.seeds.stream()
                                       .map(
                                       s -> new View.Seed(new SelfAddressingIdentifier(s.identifier()), s.endpoint()))
                                       .toList();
        var current = application;
        if (current == null) {
            throw new IllegalStateException("application is null");
        }
        Thread.ofVirtual().start(Utils.wrapped(() -> {
            if (approaches.isEmpty()) {
                current.bootstrap(viewGossipDuration, onStart, configuration.endpoints.approachEndpoint());
            } else {
                current.testify(Duration.ofMillis(10), approaches, onStart, seeds);
            }
        }, log));

        return sanctum.getId();
    }

    private CertificateValidator validator() {
        return new CertificateValidator() {
            @Override
            public void validateClient(X509Certificate[] chain) {
            }

            @Override
            public void validateServer(X509Certificate[] chain) {
            }
        };
    }

    public enum UNWRAPPING {
        SHAMIR, DELEGATED
    }

    public record Encrypted(byte[] cipherText, byte[] iv, byte[] associatedData) {
    }

    public class Service {
        private final    Map<Integer, byte[]> shares = new ConcurrentHashMap<>();
        private volatile KeyPair              sessionKeyPair;

        public Status apply(EncryptedShare eShare) {
            log.info("Applying encrypted share");
            byte[] decrypted;
            try {
                var secretKey = configuration.identity.encryptionAlgorithm()
                                                      .decapsulate(sessionKeyPair.getPrivate(),
                                                                   eShare.getEncapsulation().toByteArray(), AES);
                var encrypted = new Encrypted(eShare.getShare().toByteArray(), eShare.getIv().toByteArray(),
                                              eShare.getAssociatedData().toByteArray());
                decrypted = decrypt(encrypted, secretKey);
            } catch (Throwable t) {
                log.warn("Cannot decrypt share", t);
                return Status.newBuilder().setSuccess(false).build();
            }
            try {
                var share = Share.parseFrom(decrypted);
                shares.put(share.getKey(), share.getShare().toByteArray());
                log.warn("Share applied: {}", share.getKey());
                return Status.newBuilder().setShares(shares.size()).setSuccess(true).build();
            } catch (InvalidProtocolBufferException e) {
                log.info("Not a valid share: {}", e.toString());
                return Status.newBuilder().setShares(shares.size()).setSuccess(false).build();
            }
        }

        public Digeste identifier() {
            var id = id();
            if (id == null) {
                log.warn("No identifier");
                throw new StatusRuntimeException(io.grpc.Status.FAILED_PRECONDITION);
            }
            log.warn("Identifier requested on: {}", sanctum.getId());
            return id.toDigeste();
        }

        public Status seal() {
            var id = sanctum == null ? null : sanctum.getId();
            final var sanctorum = sanctum;
            sanctum = null;
            if (sanctorum != null) {
                sanctorum.clear();
            }
            sessionKeyPair = null;
            service.shares.clear();
            final var currentApplication = application;
            application = null;
            if (currentApplication != null) {
                currentApplication.shutdown();
            }
            log.info("Service has been sealed on: {}", id);
            return Status.newBuilder().setSuccess(true).build();
        }

        public PublicKey_ sessionKey() {
            if (sessionKeyPair == null) {
                log.info("Session key pair not available");
                throw new StatusRuntimeException(io.grpc.Status.FAILED_PRECONDITION);
            }
            log.info("Requesting session key pair");
            var alg = configuration.identity.encryptionAlgorithm();
            return PublicKey_.newBuilder()
                             .setAlgorithm(PublicKey_.algo.forNumber(alg.getCode()))
                             .setPublicKey(ByteString.copyFrom(alg.encode(sessionKeyPair.getPublic())))
                             .build();
        }

        public Status unseal() {
            if (application != null) {
                log.info("Service already unwrapped on: {}", sanctum.getId());
                return Status.newBuilder().setSuccess(true).setShares(shares.size()).build();
            }
            log.info("Unsealing service");
            sessionKeyPair = configuration.identity.encryptionAlgorithm().generateKeyPair();
            return Status.newBuilder().setSuccess(true).setShares(0).build();
        }

        public UnwrapStatus unwrap() {
            if (shares.size() < configuration.shamir.threshold()) {
                log.info("Cannot unwrap with: {} shares configured: {} out of {}", shares.size(),
                         configuration.shamir.threshold(), configuration.shamir.shares());
                return UnwrapStatus.newBuilder()
                                   .setSuccess(false)
                                   .setShares(shares.size())
                                   .setMessage(
                                   "Cannot unwrap with: %s shares configured: %s out of %s".formatted(shares.size(),
                                                                                                      configuration.shamir.threshold(),
                                                                                                      configuration.shamir.shares()))
                                   .build();
            }
            log.info("Unwrapping service with: {} shares configured: {} out of {}", shares.size(),
                     configuration.shamir.threshold(), configuration.shamir.shares());
            var scheme = new Scheme(new SecureRandom(), configuration.shamir.shares(),
                                    configuration.shamir.threshold());
            var status = UnwrapStatus.newBuilder().setShares(shares.size()).setSuccess(true);
            var clone = new HashMap<>(shares);
            shares.clear();
            sessionKeyPair = null;
            var identifier = Sphinx.this.unwrap(configuration.viewGossipDuration, scheme.join(clone));
            return status.setIdentifier(identifier.toDigeste()).build();
        }
    }
}
