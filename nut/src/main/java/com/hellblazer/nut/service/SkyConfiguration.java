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
package com.hellblazer.nut.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.salesforce.apollo.archipelago.ServerConnectionCache;
import com.salesforce.apollo.choam.Parameters;
import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.cryptography.DigestAlgorithm;
import com.salesforce.apollo.membership.Context;
import com.salesforce.apollo.membership.Member;
import com.salesforce.apollo.model.ProcessDomain;
import com.salesforce.apollo.utils.Utils;
import io.dropwizard.core.Configuration;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;

import static com.salesforce.apollo.cryptography.QualifiedBase64.qb64;

/**
 * @author hal.hildebrand
 */
public class SkyConfiguration extends Configuration {
    private static final Digest GENESIS_VIEW_ID = DigestAlgorithm.DEFAULT.digest(
    "Give me food or give me slack or kill me".getBytes());

    @JsonProperty
    public InetSocketAddress clusterEndpoint = new InetSocketAddress(InetAddress.getLoopbackAddress(),
                                                                     Utils.allocatePort());

    @JsonProperty
    public String group = qb64(DigestAlgorithm.DEFAULT.digest("SLACK"));

    @JsonProperty
    public String dbURL = "jdbc:h2:mem:";

    @JsonProperty
    public Parameters.Builder choamParameters = Parameters.newBuilder()
                                                          .setGenesisViewId(GENESIS_VIEW_ID)
                                                          .setGossipDuration(Duration.ofMillis(50))
                                                          .setProducer(Parameters.ProducerParameters.newBuilder()
                                                                                                    .setGossipDuration(
                                                                                                    Duration.ofMillis(
                                                                                                    50))
                                                                                                    .setBatchInterval(
                                                                                                    Duration.ofMillis(
                                                                                                    100))
                                                                                                    .setMaxBatchByteSize(
                                                                                                    1024 * 1024)
                                                                                                    .setMaxBatchCount(
                                                                                                    3000)
                                                                                                    .build())
                                                          .setCheckpointBlockDelta(200);

    public ProcessDomain.ProcessDomainParameters processDomainParameters = new ProcessDomain.ProcessDomainParameters(
    "jdbc:h2:mem:", Duration.ofMinutes(1), new File(System.getProperty("user.dir", ".")).toPath(),
    Duration.ofMillis(10), 0.00125, Duration.ofMinutes(1), 3, 10, 0.1);

    @JsonProperty
    public ServerConnectionCache.Builder connectionCache = ServerConnectionCache.newBuilder().setTarget(30);

    @JsonProperty
    public Context.Builder<Member> context = Context.newBuilder().setBias(3).setpByz(0.1);

    @JsonProperty
    public com.salesforce.apollo.gorgoneion.Parameters.Builder gorgoneionParameters = com.salesforce.apollo.gorgoneion.Parameters.newBuilder();
}
