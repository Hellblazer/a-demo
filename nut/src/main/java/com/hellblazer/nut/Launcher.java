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

import com.google.common.net.HostAndPort;
import com.google.protobuf.Empty;
import com.hellblazer.nut.comms.MtlsClient;
import com.hellblazer.nut.proto.SphynxGrpc;
import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.cryptography.cert.CertificateWithPrivateKey;
import com.salesforce.apollo.cryptography.ssl.CertificateValidator;
import com.salesforce.apollo.utils.Utils;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.netty.handler.ssl.ClientAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;

import static com.salesforce.apollo.cryptography.QualifiedBase64.digest;

/**
 * Simple launcher to dynamically configure seeds, approaches and genesis generation
 *
 * @author hal.hildebrand
 **/
public class Launcher {
    public final static String SEEDS_VAR           = "SEEDS";
    public final static String APPROACHES_VAR      = "APPROACHES";
    public final static String GENESIS             = "GENESIS";
    public final static String API_ENDPOINT        = "API_ENDPOINT";
    public final static String APPROACH_ENDPOINT   = "APPROACH_ENDPOINT";
    public final static String CLUSTER_ENDPOINT    = "CLUSTER_ENDPOINT";
    public final static String ADVERTISED_ENDPOINT = "ADVERTISED_ENDPOINT";

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
                                 .map(s -> (s.length == 1) ? resolve(s[0])
                                                           : new SkyConfiguration.Seedling(digest(s[0]), s[1]))
                                 .toList();
            config.approaches = Arrays.stream(approaches.split(",")).map(String::trim).toList();
        }
        var apiEndpoint = System.getenv(API_ENDPOINT);
        var clusterEndpoint = System.getenv(CLUSTER_ENDPOINT);
        var approachEndpoint = System.getenv(APPROACH_ENDPOINT);

        if (apiEndpoint != null) {
            config.apiEndpoint = apiEndpoint;
        }
        if (clusterEndpoint != null) {
            config.clusterEndpoint = clusterEndpoint;
        }
        if (approachEndpoint != null) {
            config.approachEndpoint = approachEndpoint;
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

    // Cheesy work around for full bootstrap - do better HSH
    private static SkyConfiguration.Seedling resolve(String seedSpec) {
        var split = seedSpec.split("#");
        if (split.length != 2) {
            throw new IllegalArgumentException("Invalid seed spec: " + seedSpec);
        }
        var endpoint = HostAndPort.fromString(split[0]);
        var apiPort = Integer.parseInt(split[1]);
        var apiEndpoint = HostAndPort.fromParts(endpoint.getHost(), apiPort);
        var client = apiClient(new InetSocketAddress(endpoint.getHost(), apiPort));
        try {
            while (true) {
                try {
                    log.info("resolving server: {}", apiEndpoint);
                    var sphynxClient = SphynxGrpc.newBlockingStub(client.getChannel());
                    var identifier = Digest.from(sphynxClient.identifier(Empty.getDefaultInstance()));
                    return new SkyConfiguration.Seedling(identifier, split[0]);
                } catch (StatusRuntimeException e) {
                    if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
                        log.info("server: {} unavailable", apiEndpoint, e);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        throw e;
                    }
                }
            }
        } finally {
            client.stop();
        }
    }

    private static MtlsClient apiClient(InetSocketAddress serverAddress) {
        CertificateWithPrivateKey clientCert = Utils.getMember(0);
        return new MtlsClient(serverAddress, ClientAuth.REQUIRE, "foo", clientCert.getX509Certificate(),
                              clientCert.getPrivateKey(), CertificateValidator.NONE);
    }
}
