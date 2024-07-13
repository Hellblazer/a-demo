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
import com.hellblazer.nut.support.DigestDeserializer;
import com.salesforce.apollo.archipelago.EndpointProvider;
import com.salesforce.apollo.archipelago.ServerConnectionCache;
import com.salesforce.apollo.choam.Parameters;
import com.salesforce.apollo.choam.Parameters.ProducerParameters;
import com.salesforce.apollo.context.DynamicContext;
import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.cryptography.DigestAlgorithm;
import com.salesforce.apollo.cryptography.EncryptionAlgorithm;
import com.salesforce.apollo.cryptography.SignatureAlgorithm;
import com.salesforce.apollo.membership.Member;
import com.salesforce.apollo.model.ProcessDomain.ProcessDomainParameters;
import com.salesforce.apollo.utils.Utils;
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
    public Endpoints                                           endpoints;
    @JsonProperty
    public Sphinx.UNWRAPPING                                   unwrapping;
    @JsonProperty
    public IdentityConfiguration                               identity;
    @JsonProperty
    public Shamir                                              shamir;
    @JsonProperty
    public Digest                                              group;
    @JsonProperty
    public Digest                                              genesisViewId;
    @JsonProperty
    public Parameters.Builder                                  choamParameters;
    public ProcessDomainParameters                             domain;
    @JsonProperty
    public ServerConnectionCache.Builder                       connectionCache;
    @JsonProperty
    public DynamicContext.Builder<Member>                      context;
    @JsonProperty
    public com.salesforce.apollo.gorgoneion.Parameters.Builder gorgoneionParameters;
    @JsonProperty
    public List<String>                                        approaches         = Collections.emptyList();
    @JsonProperty
    public List<Seedling>                                      seeds              = Collections.emptyList();
    @JsonProperty
    public com.salesforce.apollo.fireflies.Parameters.Builder  viewParameters;
    @JsonProperty
    public ProducerParameters.Builder                          producerParameters;
    @JsonProperty
    public Duration                                            viewGossipDuration = Duration.ofMillis(10);

    {
        // Default configuration
        var userDir = System.getProperty("user.dir", ".");
        var checkpointBaseDir = new File(userDir).toPath();
        genesisViewId = DigestAlgorithm.DEFAULT.digest("Give me food or give me slack or kill me".getBytes());

        identity = new IdentityConfiguration(Path.of(userDir, ".id"), "JCEKS", "jdbc:h2:mem:id-kerl;DB_CLOSE_DELAY=-1",
                                             Path.of(userDir, ".digest"), DigestAlgorithm.DEFAULT,
                                             SignatureAlgorithm.DEFAULT, EncryptionAlgorithm.DEFAULT);
        unwrapping = Sphinx.UNWRAPPING.SHAMIR;
        gorgoneionParameters = com.salesforce.apollo.gorgoneion.Parameters.newBuilder();
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
        viewParameters = com.salesforce.apollo.fireflies.Parameters.newBuilder()
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
                    @JsonSubTypes.Type(value = NetworkInterface.class, name = "network"),
                    @JsonSubTypes.Type(value = SocketEndpoints.class, name = "socket") })
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "class")
    public interface Endpoints {
        SocketAddress apiEndpoint();

        SocketAddress approachEndpoint();

        SocketAddress clusterEndpoint();

        SocketAddress serviceEndpoint();
    }

    public static class InterfaceEndpoints implements Endpoints {
        @JsonProperty
        public boolean preferIpV6   = false;
        @JsonProperty
        public String  interfaceName;
        @JsonProperty
        public int     apiPort      = 0;
        @JsonProperty
        public int     approachPort = 0;
        @JsonProperty
        public int     clusterPort  = 0;
        @JsonProperty
        public int     servicePort  = 0;

        private SocketAddress resolvedApiEndpoint;
        private SocketAddress resolvedApproachEndpoint;
        private SocketAddress resolvedClusterEndpoint;
        private SocketAddress resolvedServiceEndpoint;

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

        private SocketAddress resolvedApiEndpoint;
        private SocketAddress resolvedApproachEndpoint;
        private SocketAddress resolvedClusterEndpoint;
        private SocketAddress resolvedServiceEndpoint;

        @Override
        public SocketAddress apiEndpoint() {
            if (resolvedApiEndpoint != null) {
                return resolvedApiEndpoint;
            }
            resolvedApiEndpoint = EndpointProvider.reify(api);
            return resolvedApiEndpoint;
        }

        @Override
        public SocketAddress approachEndpoint() {
            if (resolvedApproachEndpoint != null) {
                return resolvedApproachEndpoint;
            }
            resolvedApproachEndpoint = EndpointProvider.reify(approach);
            return resolvedApproachEndpoint;
        }

        @Override
        public SocketAddress clusterEndpoint() {
            if (resolvedClusterEndpoint != null) {
                return resolvedClusterEndpoint;
            }
            resolvedClusterEndpoint = EndpointProvider.reify(cluster);
            return resolvedClusterEndpoint;
        }

        @Override
        public SocketAddress serviceEndpoint() {
            if (resolvedServiceEndpoint != null) {
                return resolvedServiceEndpoint;
            }
            resolvedServiceEndpoint = EndpointProvider.reify(service);
            return resolvedServiceEndpoint;
        }

        @Override
        public String toString() {
            return "Socket {api=" + apiEndpoint() + ", approach=" + approachEndpoint() + ", cluster="
            + clusterEndpoint() + '}';
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
