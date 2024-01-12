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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.salesforce.apollo.archipelago.ServerConnectionCache;
import com.salesforce.apollo.choam.Parameters;
import com.salesforce.apollo.choam.Parameters.ProducerParameters;
import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.cryptography.DigestAlgorithm;
import com.salesforce.apollo.cryptography.EncryptionAlgorithm;
import com.salesforce.apollo.cryptography.SignatureAlgorithm;
import com.salesforce.apollo.membership.Context;
import com.salesforce.apollo.membership.Member;
import com.salesforce.apollo.model.ProcessDomain.ProcessDomainParameters;
import com.salesforce.apollo.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * @author hal.hildebrand
 */
public class SkyConfiguration {

    @JsonProperty
    public Sphinx.UNWRAPPING                                   unwrapping;
    @JsonProperty
    public IdentityConfiguration                               identity;
    @JsonProperty
    public Shamir                                              shamir;
    @JsonProperty
    public Endpoint                                            clusterEndpoint;
    @JsonProperty
    public Endpoint                                            apiEndpoint;
    @JsonProperty
    public Digest                                              group;
    @JsonProperty
    public Parameters.Builder                                  choamParameters;
    public ProcessDomainParameters                             domain;
    @JsonProperty
    public ServerConnectionCache.Builder                       connectionCache;
    @JsonProperty
    public Context.Builder<Member>                             context;
    @JsonProperty
    public com.salesforce.apollo.gorgoneion.Parameters.Builder gorgoneionParameters;
    public List<SocketAddress>                                 seeds = Collections.emptyList();
    public boolean                                             local = false;

    {
        // Default configuration
        var userDir = System.getProperty("user.dir", ".");
        var checkpointBaseDir = new File(userDir).toPath();
        var genesisViewId = DigestAlgorithm.DEFAULT.digest("Give me food or give me slack or kill me".getBytes());

        identity = new IdentityConfiguration(Path.of(userDir, ".id"), "JCEKS", "jdbc:h2:mem:id-kerl;DB_CLOSE_DELAY=-1",
                                             Path.of(userDir, ".digest"), DigestAlgorithm.DEFAULT,
                                             SignatureAlgorithm.DEFAULT, EncryptionAlgorithm.DEFAULT);
        unwrapping = Sphinx.UNWRAPPING.SHAMIR;
        gorgoneionParameters = com.salesforce.apollo.gorgoneion.Parameters.newBuilder();
        shamir = new Shamir(3, 2);
        var localhost = InetAddress.getLoopbackAddress().getHostName();
        clusterEndpoint = new Endpoint(localhost, Utils.allocatePort(), null);
        apiEndpoint = new Endpoint(localhost, Utils.allocatePort(), null);
        group = DigestAlgorithm.DEFAULT.digest("SLACK");
        connectionCache = ServerConnectionCache.newBuilder().setTarget(30);
        context = Context.newBuilder().setBias(3).setpByz(0.1);
        domain = new ProcessDomainParameters("jdbc:h2:mem:sql-state;DB_CLOSE_DELAY=-1", Duration.ofMinutes(1),
                                             "jdbc:h2:mem:dht-state;DB_CLOSE_DELAY=-1", checkpointBaseDir,
                                             Duration.ofMillis(10), 0.00125, Duration.ofMinutes(1), 3, 10, 0.1);
        choamParameters = Parameters.newBuilder()
                                    .setViewSigAlgorithm(identity.signatureAlgorithm)
                                    .setDigestAlgorithm(identity.digestAlgorithm)
                                    .setGenesisViewId(genesisViewId)
                                    .setGossipDuration(Duration.ofMillis(50))
                                    .setProducer(ProducerParameters.newBuilder()
                                                                   .setGossipDuration(Duration.ofMillis(50))
                                                                   .setBatchInterval(Duration.ofMillis(100))
                                                                   .setMaxBatchByteSize(10 * 1024 * 1024)
                                                                   .setMaxBatchCount(3000)
                                                                   .build())
                                    .setCheckpointBlockDelta(200);
    }

    static SkyConfiguration from(InputStream is) {
        SkyConfiguration config;
        var mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        try {
            config = mapper.reader().readValue(is, SkyConfiguration.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot deserialize configuration", e);
        }
        return config;
    }

    public record IdentityConfiguration(Path keyStore, String keyStoreType, String kerlURL, Path identityFile,
                                        DigestAlgorithm digestAlgorithm, SignatureAlgorithm signatureAlgorithm,
                                        EncryptionAlgorithm encryptionAlgorithm) {

    }

    public record Shamir(int shares, int threshold) {
    }
}
