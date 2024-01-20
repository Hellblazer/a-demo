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

import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.cryptography.DigestAlgorithm;
import com.salesforce.apollo.membership.stereotomy.ControlledIdentifierMember;
import com.salesforce.apollo.stereotomy.KERL;
import com.salesforce.apollo.stereotomy.Stereotomy;
import com.salesforce.apollo.stereotomy.StereotomyImpl;
import com.salesforce.apollo.stereotomy.StereotomyKeyStore;
import com.salesforce.apollo.stereotomy.caching.CachingKERL;
import com.salesforce.apollo.stereotomy.db.UniKERLDirect;
import com.salesforce.apollo.stereotomy.event.proto.Ident;
import com.salesforce.apollo.stereotomy.identifier.Identifier;
import com.salesforce.apollo.stereotomy.identifier.SelfAddressingIdentifier;
import com.salesforce.apollo.stereotomy.jks.FileKeyStore;
import com.salesforce.apollo.utils.BbBackedInputStream;
import com.salesforce.apollo.utils.Hex;
import com.salesforce.apollo.utils.Utils;
import org.h2.jdbc.JdbcConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * encapsulation of ye olde thyme secrets n' associated sensitives
 *
 * @author hal.hildebrand
 **/
public class SanctumSanctorum {
    private static final Logger log = LoggerFactory.getLogger(SanctumSanctorum.class);

    private final    Digest                     id;
    private volatile char[]                     root;
    private volatile TokenGenerator             generator;
    private volatile SecureRandom               entropy;
    private volatile Key                        master;
    private volatile Stereotomy                 stereotomy;
    private volatile ControlledIdentifierMember member;
    private volatile KERL.AppendKERL            kerl;

    public SanctumSanctorum(byte[] root, DigestAlgorithm algorithm, SecureRandom entropy,
                            SkyConfiguration configuration) {
        this.member = member;
        this.stereotomy = stereotomy;
        this.entropy = entropy;
        this.root = Hex.hexChars(root);
        this.master = new SecretKeySpec(algorithm.digest(root).getBytes(), "AES");
        assert master.getEncoded().length == 32 : "Must result in a 32 byte AES key: " + master.getEncoded().length;
        generator = new TokenGenerator(master, entropy);
        initializeKerl(configuration);
        initializeIdentifier(configuration, initializeKeyStore(configuration));
        this.id = member.getId();
    }

    public Digest getId() {
        return id;
    }

    void clear() {
        master = null;
        root = new char[0];
        generator.clear();
    }

    ControlledIdentifierMember member() {
        return member;
    }

    char[] rootPassword() {
        return root;
    }

    private void initializeIdentifier(SkyConfiguration configuration, StereotomyKeyStore keyStore) {
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
        stereotomy = new StereotomyImpl(keyStore, new CachingKERL(f -> f.apply(kerl)), new SecureRandom());
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
    }

    private void initializeKerl(SkyConfiguration configuration) {
        Connection connection = null;
        try {
            connection = new JdbcConnection(configuration.identity.kerlURL(), new Properties(), "", "", false);
        } catch (SQLException e) {
            log.error("Unable to create JDBC connection: {}", configuration.identity.kerlURL());
        }
        kerl = new UniKERLDirect(connection, configuration.identity.digestAlgorithm());
    }

    private StereotomyKeyStore initializeKeyStore(SkyConfiguration configuration) {
        StereotomyKeyStore keyStore = null;
        var storeFile = configuration.identity.keyStore().toFile();
        InputStream is = null;
        try {
            if (storeFile.exists()) {
                is = new FileInputStream(storeFile);
            }
            final var ks = KeyStore.getInstance(configuration.identity.keyStoreType());
            ks.load(is, rootPassword());
            keyStore = new FileKeyStore(ks, () -> rootPassword(), storeFile);
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
        return keyStore;
    }

}
