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
import com.salesforce.apollo.stereotomy.StereotomyKeyStore;
import com.salesforce.apollo.stereotomy.jks.FileKeyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author hal.hildebrand
 **/
public class Sphinx {
    private static final Logger                  log     = LoggerFactory.getLogger(Sphinx.class);
    private final        Map<Integer, byte[]>    parts   = new ConcurrentHashMap<>();
    private final        AtomicBoolean           started = new AtomicBoolean();
    private final        AtomicReference<char[]> root    = new AtomicReference<>();
    private volatile     Consumer<char[]>        trigger;
    private volatile     SkyApplication          application;

    public Sphinx(SkyConfiguration configuration) {
        this(configuration, null);
    }

    public Sphinx(SkyConfiguration configuration, String devSecret) {
        trigger = rs -> {
            root.set(rs);
            StereotomyKeyStore keyStore = null;
            var storeFile = configuration.keyStore.toFile();
            InputStream is = null;
            try {
                if (storeFile.exists()) {
                    is = new FileInputStream(storeFile);
                }
                final var ks = KeyStore.getInstance(configuration.keyStoreType);
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
            application = new SkyApplication(configuration, keyStore);
            application.start();
        };
        if (devSecret != null) {
            log.warn("Operating in development mode with dev secret");
            started.set(true);
            var t = trigger;
            trigger = null;
            t.accept(devSecret.toCharArray());
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
        var mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        var config = mapper.reader().readValue(file, SkyConfiguration.class);
        Sphinx sphinx;
        if (argv.length == 2) {
            sphinx = new Sphinx(config, argv[1]);
        }

        new Sphinx(config).start();
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

}
