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
import io.grpc.inprocess.InProcessSocketAddress;
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

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.io.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
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

    public static final  String AES         = "AES";
    public static final  String ALGORITHM   = "AES/GCM/NoPadding";
    private static final int    TAG_LENGTH  = 128;
    private static final int    IV_LENGTH   = 12;
    private static final int    SALT_LENGTH = 16;
    private static final Logger log         = LoggerFactory.getLogger(Sphinx.class);

    private final    AtomicBoolean              started = new AtomicBoolean();
    private final    AtomicReference<char[]>    root    = new AtomicReference<>();
    private final    SkyConfiguration           configuration;
    private final    Service                    service = new Service();
    private volatile KeyPair                    sessionKeyPair;
    private volatile SkyApplication             application;
    private volatile StereotomyImpl             stereotomy;
    private volatile ControlledIdentifierMember member;
    private volatile KERL.AppendKERL            kerl;

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
            sessionKeyPair = configuration.identity.encryptionAlgorithm().generateKeyPair();
        }
    }

    public static Encrypted encrypt(SecretKey secretKey, Share share, byte[] tag) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        Entropy.nextSecureBytes(iv);

        var cipher = initCipher(Cipher.ENCRYPT_MODE, secretKey, iv);
        cipher.updateAAD(tag);
        return new Encrypted(iv, tag, cipher.doFinal(share.toByteArray()));
    }

    public static byte[] decrypt(SecretKey secretKey, Encrypted encrypted) {
        try {
            var cipher = initCipher(Cipher.DECRYPT_MODE, secretKey, encrypted.iv);
            cipher.updateAAD(encrypted.tag);
            return cipher.doFinal(encrypted.cipherText);
        } catch (InvalidAlgorithmParameterException | NoSuchPaddingException | NoSuchAlgorithmException |
        InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalArgumentException("Unable to decrypt", e);
        }
    }

    private static Cipher initCipher(int mode, SecretKey secretKey, byte[] iv)
    throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(mode, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
        return cipher;
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

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
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

    public enum UNWRAPPING {
        SHAMIR, DELEGATED;
    }

    public record Encrypted(byte[] iv, byte[] tag, byte[] cipherText) {
    }

    public class Service {
        private final Map<Integer, byte[]> shares = new ConcurrentHashMap<>();

        public Status apply(EncryptedShare eShare) {
            var secretKey = configuration.identity.encryptionAlgorithm()
                                                  .decapsulate(sessionKeyPair.getPrivate(),
                                                               eShare.getEncapsulation().toByteArray(), AES);
            var decrypted = decrypt(secretKey,
                                    new Encrypted(eShare.getIv().toByteArray(), eShare.getTag().toByteArray(),
                                                  eShare.getShare().toByteArray()));
            try {
                var share = Share.parseFrom(decrypted);
                shares.put(share.getKey(), share.getShare().toByteArray());
                return Status.newBuilder().setShares(shares.size()).setSuccess(true).build();
            } catch (InvalidProtocolBufferException e) {
                log.info("Not a valid share: {}", e);
                return Status.newBuilder().setShares(shares.size()).setSuccess(false).build();
            }
        }

        public Status seal() {
            root.set(new char[0]);
            sessionKeyPair = configuration.identity.encryptionAlgorithm().generateKeyPair();
            application.shutdown();
            return Status.newBuilder().setSuccess(true).build();
        }

        public PublicKey_ sessionKey() {
            return PublicKey_.newBuilder()
                             .setAlgorithm(
                             PublicKey_.algo.forNumber(configuration.identity.encryptionAlgorithm().getCode()))
                             .setPublicKey(ByteString.copyFrom(sessionKeyPair.getPublic().getEncoded()))
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
            shares.clear();
            Sphinx.this.unwrap(scheme.join(shares));
            return Status.newBuilder().setShares(shares.size()).setSuccess(true).build();
        }
    }
}
