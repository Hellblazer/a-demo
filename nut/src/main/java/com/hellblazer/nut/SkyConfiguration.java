/*
 * Copyright (c) 2023 Hal Hildebrand. All rights reserved.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hellblazer.delos.archipelago.EndpointProvider;
import com.hellblazer.delos.archipelago.ServerConnectionCache;
import com.hellblazer.delos.choam.Parameters;
import com.hellblazer.delos.choam.Parameters.ProducerParameters;
import com.hellblazer.delos.context.DynamicContext;
import com.hellblazer.delos.cryptography.Digest;
import com.hellblazer.delos.cryptography.DigestAlgorithm;
import com.hellblazer.delos.cryptography.EncryptionAlgorithm;
import com.hellblazer.delos.cryptography.SignatureAlgorithm;
import com.hellblazer.delos.membership.Member;
import com.hellblazer.delos.model.ProcessDomain.ProcessDomainParameters;
import com.hellblazer.delos.utils.Utils;
import com.hellblazer.nut.support.DigestDeserializer;
import io.grpc.inprocess.InProcessSocketAddress;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * @author hal.hildebrand
 */
public class SkyConfiguration {
    @JsonProperty
    public Endpoints                                          endpoints          = new InterfaceEndpoints();
    @JsonProperty
    public Sphinx.UNWRAPPING                                  unwrapping;
    @JsonProperty
    public IdentityConfiguration                              identity;
    @JsonProperty
    public Shamir                                             shamir;
    @JsonProperty
    public Digest                                             group;
    @JsonProperty
    public Digest                                             genesisViewId;
    @JsonProperty
    public Parameters.Builder                                 choamParameters;
    @JsonProperty
    public ProcessDomainParameters                            domain;
    @JsonProperty
    public ServerConnectionCache.Builder                      connectionCache;
    @JsonProperty
    public DynamicContext.Builder<Member>                     context;
    @JsonProperty
    public com.hellblazer.delos.gorgoneion.Parameters.Builder gorgoneionParameters;
    @JsonProperty
    public List<String>                                       approaches         = Collections.emptyList();
    @JsonProperty
    public List<Seedling>                                     seeds              = Collections.emptyList();
    @JsonProperty
    public com.hellblazer.delos.fireflies.Parameters.Builder  viewParameters;
    @JsonProperty
    public ProducerParameters.Builder                         producerParameters;
    @JsonProperty
    public Duration                                           viewGossipDuration = Duration.ofMillis(10);
    @JsonProperty
    public String                                             provisionedToken;
    @JsonProperty
    public String                                             tag;
    @JsonProperty
    public SocketAddress                                      enclaveEndpoint    = new InProcessSocketAddress(
    UUID.randomUUID().toString());

    {
        // Default configuration
        var userDir = System.getProperty("user.dir", ".");
        var checkpointBaseDir = new File(userDir).toPath();
        genesisViewId = DigestAlgorithm.DEFAULT.digest("Give me food or give me slack or kill me".getBytes());

        identity = new IdentityConfiguration(Path.of(userDir, ".id"), "JCEKS", "jdbc:h2:mem:id-kerl;DB_CLOSE_DELAY=-1",
                                             Path.of(userDir, ".digest"), DigestAlgorithm.DEFAULT,
                                             SignatureAlgorithm.DEFAULT, EncryptionAlgorithm.DEFAULT);
        unwrapping = Sphinx.UNWRAPPING.SHAMIR;
        gorgoneionParameters = com.hellblazer.delos.gorgoneion.Parameters.newBuilder();
        shamir = new Shamir(3, 2);
        group = DigestAlgorithm.DEFAULT.digest("SLACK");
        connectionCache = ServerConnectionCache.newBuilder().setTarget(30);
        context = DynamicContext.newBuilder();
        context.setBias(3).setpByz(0.1);
        domain = new ProcessDomainParameters("jdbc:h2:mem:sql-state;DB_CLOSE_DELAY=-1", Duration.ofMinutes(1),
                                             "jdbc:h2:mem:dht-state;DB_CLOSE_DELAY=-1", checkpointBaseDir,
                                             Duration.ofMillis(5), 0.00125, Duration.ofMinutes(1), 3,
                                             Duration.ofMillis(100), 10, 0.1);
        choamParameters = Parameters.newBuilder().setGossipDuration(Duration.ofMillis(5)).setCheckpointBlockDelta(200);
        viewParameters = com.hellblazer.delos.fireflies.Parameters.newBuilder()
                                                                  .setFpr(0.000125)
                                                                  .setSeedingTimout(Duration.ofSeconds(10));
        producerParameters = ProducerParameters.newBuilder()
                                               .setGossipDuration(Duration.ofMillis(5))
                                               .setBatchInterval(Duration.ofMillis(100))
                                               .setMaxBatchByteSize(10 * 1024 * 1024)
                                               .setMaxBatchCount(3000);
    }

    static SkyConfiguration from(InputStream is) {
        SkyConfiguration config;
        var mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        var module = new SimpleModule().addDeserializer(Digest.class, new DigestDeserializer());
        mapper.registerModule(module);
        try {
            config = mapper.reader().readValue(is, SkyConfiguration.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot deserialize configuration", e);
        }
        return config;
    }

    @JsonSubTypes({ @JsonSubTypes.Type(value = LocalEndpoints.class, name = "local"),
                    @JsonSubTypes.Type(value = InterfaceEndpoints.class, name = "network"),
                    @JsonSubTypes.Type(value = SocketEndpoints.class, name = "socket") })
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "class")
    public interface Endpoints {
        SocketAddress apiEndpoint();

        SocketAddress approachEndpoint();

        SocketAddress clusterEndpoint();

        SocketAddress healthEndpoint();

        SocketAddress serviceEndpoint();
    }

    public static class InterfaceEndpoints implements Endpoints {
        @JsonProperty
        public boolean preferIpV6    = false;
        @JsonProperty
        public String  interfaceName = "eth0";
        @JsonProperty
        public int     apiPort       = 8123;
        @JsonProperty
        public int     approachPort  = 8124;
        @JsonProperty
        public int     clusterPort   = 8125;
        @JsonProperty
        public int     servicePort   = 8126;
        @JsonProperty
        public int     healthPort    = 8127;

        private SocketAddress resolvedApiEndpoint;
        private SocketAddress resolvedApproachEndpoint;
        private SocketAddress resolvedClusterEndpoint;
        private SocketAddress resolvedServiceEndpoint;
        private SocketAddress resolvedHealthEndpoint;

        @Override
        public SocketAddress apiEndpoint() {
            if (resolvedApiEndpoint != null) {
                return resolvedApiEndpoint;
            }
            var address = getAddress();
            resolvedApiEndpoint = new InetSocketAddress(address, apiPort == 0 ? Utils.allocatePort(address) : apiPort);
            return resolvedApiEndpoint;
        }

        @Override
        public SocketAddress approachEndpoint() {
            if (resolvedApproachEndpoint != null) {
                return resolvedApproachEndpoint;
            }
            var address = getAddress();
            resolvedApproachEndpoint = new InetSocketAddress(address, approachPort == 0 ? Utils.allocatePort(address)
                                                                                        : approachPort);
            return resolvedApproachEndpoint;
        }

        @Override
        public SocketAddress clusterEndpoint() {
            if (resolvedClusterEndpoint != null) {
                return resolvedClusterEndpoint;
            }
            var address = getAddress();
            resolvedClusterEndpoint = new InetSocketAddress(address, clusterPort == 0 ? Utils.allocatePort(address)
                                                                                      : clusterPort);
            return resolvedClusterEndpoint;
        }

        @Override
        public SocketAddress healthEndpoint() {
            if (resolvedHealthEndpoint != null) {
                return resolvedHealthEndpoint;
            }
            var address = getAddress();
            resolvedHealthEndpoint = new InetSocketAddress(address,
                                                           healthPort == 0 ? Utils.allocatePort(address) : healthPort);
            return resolvedHealthEndpoint;
        }

        @Override
        public SocketAddress serviceEndpoint() {
            if (resolvedServiceEndpoint != null) {
                return resolvedServiceEndpoint;
            }
            var address = getAddress();
            resolvedServiceEndpoint = new InetSocketAddress(address,
                                                            apiPort == 0 ? Utils.allocatePort(address) : servicePort);
            return resolvedServiceEndpoint;
        }

        @Override
        public String toString() {
            return "Interface {" + "preferIpV6=" + preferIpV6 + ", interface='" + interfaceName + ", api="
            + apiEndpoint() + ", approach=" + approachEndpoint() + ", cluster=" + clusterEndpoint() + ", service="
            + serviceEndpoint() + '}';
        }

        private InetAddress getAddress() {
            var inf = getNetworkInterface();
            if (preferIpV6) {
                var ipv6 = inf.getInterfaceAddresses()
                              .stream()
                              .map(InterfaceAddress::getAddress)
                              .filter(address -> address instanceof Inet6Address)
                              .findFirst();
                if (ipv6.isPresent()) {
                    return ipv6.get();
                }
            }
            LoggerFactory.getLogger(SkyConfiguration.class)
                         .trace("Network interface addresses: {}", inf.getInterfaceAddresses());
            var address = inf.getInterfaceAddresses()
                             .stream()
                             .map(InterfaceAddress::getAddress)
                             .filter(addAddress -> addAddress instanceof Inet4Address)
                             .findFirst();
            if (address.isPresent()) {
                return address.get();
            }
            throw new IllegalStateException("unable to resolve address of network interfaces: " + interfaceName);
        }

        private NetworkInterface getNetworkInterface() {
            try {
                return NetworkInterface.getByName(interfaceName);
            } catch (SocketException e) {
                throw new IllegalStateException("Cannot retrieve Network Interface: " + interfaceName, e);
            }
        }
    }

    public static class LocalEndpoints implements Endpoints {
        @JsonProperty
        public String unique = UUID.randomUUID().toString();
        @JsonProperty
        public String api;
        @JsonProperty
        public String approach;
        @JsonProperty
        public String cluster;
        @JsonProperty
        public String service;

        private SocketAddress resolvedApiEndpoint;
        private SocketAddress resolvedApproachEndpoint;
        private SocketAddress resolvedClusterEndpoint;
        private SocketAddress resolvedServiceEndpoint;

        @Override
        public SocketAddress apiEndpoint() {
            if (resolvedApiEndpoint != null) {
                return resolvedApiEndpoint;
            }
            resolvedApiEndpoint = new InProcessSocketAddress("%s:%s".formatted(unique, api));
            return resolvedApiEndpoint;
        }

        @Override
        public SocketAddress approachEndpoint() {
            if (resolvedApproachEndpoint != null) {
                return resolvedApproachEndpoint;
            }
            resolvedApproachEndpoint = new InProcessSocketAddress("%s:%s".formatted(unique, approach));
            return resolvedApproachEndpoint;
        }

        @Override
        public SocketAddress clusterEndpoint() {
            if (resolvedClusterEndpoint != null) {
                return resolvedClusterEndpoint;
            }
            resolvedClusterEndpoint = new InProcessSocketAddress("%s:%s".formatted(unique, cluster));
            return resolvedClusterEndpoint;
        }

        @Override
        public SocketAddress healthEndpoint() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SocketAddress serviceEndpoint() {
            if (resolvedServiceEndpoint != null) {
                return resolvedServiceEndpoint;
            }
            resolvedServiceEndpoint = new InProcessSocketAddress("%s:%s".formatted(unique, service));
            return resolvedServiceEndpoint;
        }

        @Override
        public String toString() {
            return "Local {api=" + apiEndpoint() + ", approach=" + approachEndpoint() + ", cluster=" + clusterEndpoint()
            + '}';
        }
    }

    public static class SocketEndpoints implements Endpoints {
        @JsonProperty
        public String api;
        @JsonProperty
        public String approach;
        @JsonProperty
        public String cluster;
        @JsonProperty
        public String service;
        @JsonProperty
        public String health;

        private SocketAddress resolvedApiEndpoint;
        private SocketAddress resolvedApproachEndpoint;
        private SocketAddress resolvedClusterEndpoint;
        private SocketAddress resolvedServiceEndpoint;
        private SocketAddress resolvedHealthEndpoint;

        @Override
        public SocketAddress apiEndpoint() {
            if (resolvedApiEndpoint != null) {
                return resolvedApiEndpoint;
            }
            var raw = EndpointProvider.reify(api);
            resolvedApiEndpoint =
            raw.getPort() == 0 ? new InetSocketAddress(raw.getAddress(), Utils.allocatePort(raw.getAddress())) : raw;
            return resolvedApiEndpoint;
        }

        @Override
        public SocketAddress approachEndpoint() {
            if (resolvedApproachEndpoint != null) {
                return resolvedApproachEndpoint;
            }
            var raw = EndpointProvider.reify(approach);
            resolvedApproachEndpoint =
            raw.getPort() == 0 ? new InetSocketAddress(raw.getAddress(), Utils.allocatePort(raw.getAddress())) : raw;
            return resolvedApproachEndpoint;
        }

        @Override
        public SocketAddress clusterEndpoint() {
            if (resolvedClusterEndpoint != null) {
                return resolvedClusterEndpoint;
            }
            var raw = EndpointProvider.reify(cluster);
            resolvedClusterEndpoint =
            raw.getPort() == 0 ? new InetSocketAddress(raw.getAddress(), Utils.allocatePort(raw.getAddress())) : raw;
            return resolvedClusterEndpoint;
        }

        @Override
        public SocketAddress healthEndpoint() {
            if (resolvedHealthEndpoint != null) {
                return resolvedHealthEndpoint;
            }
            var raw = EndpointProvider.reify(health);
            resolvedHealthEndpoint =
            raw.getPort() == 0 ? new InetSocketAddress(raw.getAddress(), Utils.allocatePort(raw.getAddress())) : raw;
            return resolvedHealthEndpoint;
        }

        @Override
        public SocketAddress serviceEndpoint() {
            if (resolvedServiceEndpoint != null) {
                return resolvedServiceEndpoint;
            }
            var raw = EndpointProvider.reify(service);
            resolvedServiceEndpoint =
            raw.getPort() == 0 ? new InetSocketAddress(raw.getAddress(), Utils.allocatePort(raw.getAddress())) : raw;
            return resolvedServiceEndpoint;
        }

        @Override
        public String toString() {
            String health = "";
            try {
                health = ", health=" + healthEndpoint();
            } catch (Throwable e) {
                // ignore
            }
            return "Socket {api=" + apiEndpoint() + ", approach=" + approachEndpoint() + ", cluster="
            + clusterEndpoint() + ", health=" + health + '}';
        }
    }

    public record IdentityConfiguration(Path keyStore, String keyStoreType, String kerlURL, Path identityFile,
                                        DigestAlgorithm digestAlgorithm, SignatureAlgorithm signatureAlgorithm,
                                        EncryptionAlgorithm encryptionAlgorithm) {

    }

    public record Shamir(int shares, int threshold) {
    }

    public record Seedling(Digest identifier, String endpoint) {
    }
}
