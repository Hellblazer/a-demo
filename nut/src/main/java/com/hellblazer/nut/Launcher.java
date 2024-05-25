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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collections;

import static com.salesforce.apollo.cryptography.QualifiedBase64.digest;

/**
 * Simple launcher to dynamically configure seeds, approaches and genesis generation
 *
 * @author hal.hildebrand
 **/
public class Launcher {
    public final static String SEEDS_VAR      = "SEEDS";
    public final static String APPROACHES_VAR = "APPROACHES";
    public final static String GENESIS        = "GENESIS";

    private static final Logger log = LoggerFactory.getLogger(Sphinx.class);

    public static void main(String[] argv) throws Exception {
        if (argv.length < 1 || argv.length > 2) {
            System.err.println("Usage: Launcher <config file name> (<development secret>)");
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
        SkyConfiguration config;
        try (var fis = new FileInputStream(file)) {
            config = SkyConfiguration.from(fis);
        }

        var genesis = System.getenv(GENESIS) != null && Boolean.parseBoolean(System.getenv(GENESIS));
        log.info("Generating Genesis: {}", genesis);
        var seeds = System.getenv(SEEDS_VAR);
        var approaches = System.getenv(APPROACHES_VAR);
        if (seeds == null || approaches == null) {
            log.info("Environment [{}] and [{}}] are empty, bootstrapping", SEEDS_VAR, APPROACHES_VAR);
            config.seeds = Collections.emptyList();
            config.approaches = Collections.emptyList();
        } else {
            log.info("Seeds: [{}] Approaches: [{}}]", seeds, approaches);
            config.seeds = Arrays.stream(seeds.split(","))
                                 .map(String::trim)
                                 .map(s -> s.split("@"))
                                 .map(s -> new SkyConfiguration.Seedling(digest(s[0]), s[1]))
                                 .toList();
            config.approaches = Arrays.stream(approaches.split(",")).map(String::trim).toList();
        }
        config.choamParameters.setGenerateGenesis(genesis);
        Sphinx sphinx = argv.length == 1 ? new Sphinx(config) : new Sphinx(config, argv[1]);

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

}
