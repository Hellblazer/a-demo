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
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.hellblazer.nut;

import com.google.common.net.HostAndPort;
import com.hellblazer.delos.cryptography.Digest;
import com.hellblazer.delos.cryptography.QualifiedBase64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and merges Sky configuration with precedence handling:
 * CLI arguments > Environment variables > YAML file > Defaults
 *
 * @author hal.hildebrand
 */
public class ConfigurationLoader {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationLoader.class);

    /**
     * Load configuration with full precedence handling
     *
     * @param yamlPath Path to YAML configuration file (can be null for defaults only)
     * @param cliArgs Command-line arguments in format --key=value
     * @return Fully resolved SkyConfiguration
     * @throws IOException if YAML file cannot be read
     */
    public static SkyConfiguration load(String yamlPath, String[] cliArgs) throws IOException {
        // Step 1: Load base configuration (YAML or defaults)
        SkyConfiguration config = loadBaseConfiguration(yamlPath);

        // Step 2: Apply environment variable overrides
        applyEnvironmentVariableOverrides(config);

        // Step 3: Apply CLI argument overrides (highest precedence)
        applyCliArgumentOverrides(config, cliArgs);

        return config;
    }

    /**
     * Load configuration from YAML only (for backward compatibility, tests)
     */
    public static SkyConfiguration fromYaml(InputStream is) {
        return SkyConfiguration.from(is);
    }

    // ============================================================================
    // Private implementation
    // ============================================================================

    /**
     * Load base configuration from YAML or create default
     */
    private static SkyConfiguration loadBaseConfiguration(String yamlPath) throws IOException {
        if (yamlPath == null || yamlPath.isBlank()) {
            log.debug("No YAML file specified, using defaults");
            return new SkyConfiguration();
        }

        File file = new File(yamlPath);
        if (!file.exists()) {
            throw new IOException("Configuration file not found: " + yamlPath);
        }
        if (!file.isFile()) {
            throw new IOException("Configuration path is not a file: " + yamlPath);
        }

        log.debug("Loading configuration from: {}", file.getAbsolutePath());
        try (var fis = new FileInputStream(file)) {
            return SkyConfiguration.from(fis);
        }
    }

    /**
     * Apply environment variable overrides with backward compatibility
     */
    private static void applyEnvironmentVariableOverrides(SkyConfiguration config) {
        // GENESIS -> choamParameters.generateGenesis
        var genesis = System.getenv("GENESIS");
        if (genesis != null) {
            boolean genValue = Boolean.parseBoolean(genesis);
            config.choamParameters.setGenerateGenesis(genValue);
            log.debug("Set GENESIS from environment: {}", genValue);
        }

        // USE_SERVICE_LAYER -> useServiceLayer
        var useServiceLayer = System.getenv("USE_SERVICE_LAYER");
        if (useServiceLayer != null) {
            config.useServiceLayer = Boolean.parseBoolean(useServiceLayer);
            log.debug("Set USE_SERVICE_LAYER from environment: {}", config.useServiceLayer);
        }

        // BIND_INTERFACE -> endpoints (create InterfaceEndpoints if needed)
        var bindInterface = System.getenv("BIND_INTERFACE");
        if (bindInterface != null) {
            if (!(config.endpoints instanceof SkyConfiguration.InterfaceEndpoints)) {
                config.endpoints = new SkyConfiguration.InterfaceEndpoints();
            }
            var endpoints = (SkyConfiguration.InterfaceEndpoints) config.endpoints;
            endpoints.interfaceName = bindInterface;
            log.debug("Set BIND_INTERFACE from environment: {}", bindInterface);

            // Port overrides (only if BIND_INTERFACE is set)
            var api = System.getenv("API_PORT");
            if (api != null) {
                endpoints.apiPort = Integer.parseInt(api);
                log.debug("Set API_PORT from environment: {}", endpoints.apiPort);
            }
            var approach = System.getenv("APPROACH_PORT");
            if (approach != null) {
                endpoints.approachPort = Integer.parseInt(approach);
                log.debug("Set APPROACH_PORT from environment: {}", endpoints.approachPort);
            }
            var cluster = System.getenv("CLUSTER_PORT");
            if (cluster != null) {
                endpoints.clusterPort = Integer.parseInt(cluster);
                log.debug("Set CLUSTER_PORT from environment: {}", endpoints.clusterPort);
            }
            var service = System.getenv("SERVICE_PORT");
            if (service != null) {
                endpoints.servicePort = Integer.parseInt(service);
                log.debug("Set SERVICE_PORT from environment: {}", endpoints.servicePort);
            }
            var health = System.getenv("HEALTH_PORT");
            if (health != null) {
                endpoints.healthPort = Integer.parseInt(health);
                log.debug("Set HEALTH_PORT from environment: {}", endpoints.healthPort);
            }
        }

        // PROVISIONED -> provisionedToken
        var provisioned = System.getenv(Launcher.PROVISIONED_TOKEN);
        if (provisioned != null) {
            config.provisionedToken = provisioned;
            log.debug("Set provisioned token from environment variable: {}", Launcher.PROVISIONED_TOKEN);
        }

        // SEEDS -> config.seeds
        var seeds = System.getenv("SEEDS");
        if (seeds != null && !seeds.isBlank()) {
            config.seeds = Arrays.stream(seeds.split(","))
                                 .map(String::trim)
                                 .map(s -> s.split("@"))
                                 .map(s -> (s.length == 1) ? resolveSeedling(s[0])
                                                           : new SkyConfiguration.Seedling(QualifiedBase64.digest(s[0]), s[1]))
                                 .toList();
            log.debug("Set SEEDS from environment: {} entries", config.seeds.size());
        }

        // APPROACHES -> config.approaches
        var approaches = System.getenv("APPROACHES");
        if (approaches != null && !approaches.isBlank()) {
            config.approaches = Arrays.stream(approaches.split(","))
                                      .map(String::trim)
                                      .toList();
            log.debug("Set APPROACHES from environment: {} entries", config.approaches.size());
        }
    }

    /**
     * Apply CLI argument overrides (highest precedence)
     */
    private static void applyCliArgumentOverrides(SkyConfiguration config, String[] cliArgs) {
        if (cliArgs == null || cliArgs.length == 0) {
            return;
        }

        Map<String, String> cliParams = new HashMap<>();
        for (String arg : cliArgs) {
            if (arg.startsWith("--")) {
                String param = arg.substring(2);
                String[] parts = param.split("=", 2);
                if (parts.length == 2) {
                    cliParams.put(parts[0], parts[1]);
                    log.debug("Parsed CLI argument: {}={}", parts[0], parts[1]);
                }
            }
        }

        // useServiceLayer CLI override
        if (cliParams.containsKey("useServiceLayer")) {
            config.useServiceLayer = Boolean.parseBoolean(cliParams.get("useServiceLayer"));
            log.debug("Set useServiceLayer from CLI: {}", config.useServiceLayer);
        }

        // endpoints.apiPort CLI override
        if (cliParams.containsKey("endpoints.apiPort")) {
            ensureInterfaceEndpoints(config);
            ((SkyConfiguration.InterfaceEndpoints) config.endpoints).apiPort =
                Integer.parseInt(cliParams.get("endpoints.apiPort"));
            log.debug("Set endpoints.apiPort from CLI");
        }

        // endpoints.approachPort CLI override
        if (cliParams.containsKey("endpoints.approachPort")) {
            ensureInterfaceEndpoints(config);
            ((SkyConfiguration.InterfaceEndpoints) config.endpoints).approachPort =
                Integer.parseInt(cliParams.get("endpoints.approachPort"));
            log.debug("Set endpoints.approachPort from CLI");
        }

        // endpoints.clusterPort CLI override
        if (cliParams.containsKey("endpoints.clusterPort")) {
            ensureInterfaceEndpoints(config);
            ((SkyConfiguration.InterfaceEndpoints) config.endpoints).clusterPort =
                Integer.parseInt(cliParams.get("endpoints.clusterPort"));
            log.debug("Set endpoints.clusterPort from CLI");
        }

        // endpoints.servicePort CLI override
        if (cliParams.containsKey("endpoints.servicePort")) {
            ensureInterfaceEndpoints(config);
            ((SkyConfiguration.InterfaceEndpoints) config.endpoints).servicePort =
                Integer.parseInt(cliParams.get("endpoints.servicePort"));
            log.debug("Set endpoints.servicePort from CLI");
        }

        // endpoints.healthPort CLI override
        if (cliParams.containsKey("endpoints.healthPort")) {
            ensureInterfaceEndpoints(config);
            ((SkyConfiguration.InterfaceEndpoints) config.endpoints).healthPort =
                Integer.parseInt(cliParams.get("endpoints.healthPort"));
            log.debug("Set endpoints.healthPort from CLI");
        }

        // endpoints.interfaceName CLI override
        if (cliParams.containsKey("endpoints.interfaceName")) {
            ensureInterfaceEndpoints(config);
            ((SkyConfiguration.InterfaceEndpoints) config.endpoints).interfaceName =
                cliParams.get("endpoints.interfaceName");
            log.debug("Set endpoints.interfaceName from CLI");
        }
    }

    /**
     * Ensure endpoints is an InterfaceEndpoints instance for modification
     */
    private static void ensureInterfaceEndpoints(SkyConfiguration config) {
        if (!(config.endpoints instanceof SkyConfiguration.InterfaceEndpoints)) {
            var newEndpoints = new SkyConfiguration.InterfaceEndpoints();
            // Copy current settings if possible
            config.endpoints = newEndpoints;
        }
    }

    /**
     * Resolve a seed specification (host:port format)
     * This is kept from original Launcher for consistency
     */
    private static SkyConfiguration.Seedling resolveSeedling(String seedSpec) {
        var split = seedSpec.split("#");
        if (split.length != 2) {
            throw new IllegalArgumentException("Invalid seed spec: " + seedSpec);
        }
        var endpoint = HostAndPort.fromString(split[0]);
        var apiPort = Integer.parseInt(split[1]);
        // For now, just use the host:apiPort from the spec
        // In Launcher, this is resolved dynamically via gRPC call
        // Here we just return a seedling with the endpoint
        var hostPort = HostAndPort.fromParts(endpoint.getHost(), apiPort);
        return new SkyConfiguration.Seedling(
            QualifiedBase64.digest(split[0]),  // Hash of spec for unique digest
            hostPort.toString()
        );
    }
}
