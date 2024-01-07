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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hellblazer.nut.proto.EncryptedShare;
import com.hellblazer.nut.proto.Share;
import com.hellblazer.nut.proto.Status;
import com.salesforce.apollo.membership.stereotomy.ControlledIdentifierMember;
import com.salesforce.apollo.stereotomy.KERL;
import com.salesforce.apollo.stereotomy.StereotomyImpl;
import com.salesforce.apollo.stereotomy.StereotomyKeyStore;
import com.salesforce.apollo.stereotomy.db.UniKERLDirect;
import com.salesforce.apollo.stereotomy.jks.FileKeyStore;
import com.salesforce.apollo.utils.Entropy;
import org.h2.jdbc.JdbcConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

    public static final  String AES        = "AES";
    public static final  String ALGORITHM  = "AES/GCM/NoPadding";
    private static final int    TAG_LENGTH = 128;
    private static final int    IV_LENGTH  = 12;
    private static final Logger log        = LoggerFactory.getLogger(Sphinx.class);

    private final    AtomicBoolean              started = new AtomicBoolean();
    private final    AtomicReference<char[]>    root    = new AtomicReference<>();
    private final    SkyConfiguration           configuration;
    private volatile KeyPair                    sessionKeyPair;
    private volatile SkyApplication             application;
    private volatile StereotomyImpl             stereotomy;
    private volatile ControlledIdentifierMember member;
    private volatile KERL.AppendKERL            kerl;

    public Sphinx(SkyConfiguration configuration) {
        this(configuration, null);
    }

    public Sphinx(SkyConfiguration configuration, String devSecret) {
        this.configuration = configuration;
        Connection connection = null;
        try {
            connection = new JdbcConnection(configuration.identity.kerlURL(), new Properties(), "", "", false);
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

    public static Encrypted encrypt(SecretKey secretKey, Share share) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        Entropy.nextSecureBytes(iv);
        return new Encrypted(iv, initCipher(Cipher.ENCRYPT_MODE, secretKey, iv).doFinal(share.toByteArray()));
    }

    public static byte[] decrypt(SecretKey secretKey, Encrypted encrypted) {
        try {
            return initCipher(Cipher.DECRYPT_MODE, secretKey, encrypted.iv).doFinal(encrypted.cipherText);
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
        var mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        var config = mapper.reader().readValue(file, SkyConfiguration.class);
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

    private void startSky() {
        application = new SkyApplication(configuration, member);
        application.start();
    }

    private void unwrap(byte[] rs) {
        var pwd = new char[rs.length];
        for (var i = 0; i < rs.length; i++) {
            pwd[i] = (char) rs[i];
        }
        unwrap(pwd);
    }

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
        log.error("Successfully opened root: {}", storeFile.getAbsolutePath());
        stereotomy = new StereotomyImpl(keyStore, kerl, new SecureRandom());
        member = new ControlledIdentifierMember(stereotomy.newIdentifier());
    }

    public enum UNWRAPPING {
        SHAMIR, DELEGATED;
    }

    public record Encrypted(byte[] iv, byte[] cipherText) {
    }

    public class Service {
        private final Map<Integer, ByteString> shares = new ConcurrentHashMap<>();

        public Status apply(EncryptedShare eShare) {
            var secretKey = configuration.identity.encryptionAlgorithm()
                                                  .decapsulate(sessionKeyPair.getPrivate(),
                                                               eShare.getEncapsulation().toByteArray(), AES);
            var decrypted = decrypt(secretKey,
                                    new Encrypted(eShare.getIv().toByteArray(), eShare.getShare().toByteArray()));
            try {
                var share = Share.parseFrom(decrypted);
                shares.putIfAbsent(share.getKey(), share.getShare());
                return Status.newBuilder().setSuccess(true).build();
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Not a valid share", e);
            }
        }
    }
}
