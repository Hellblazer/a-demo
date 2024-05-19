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

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hellblazer.nut.proto.EncryptedShare;
import com.hellblazer.nut.proto.Share;
import com.hellblazer.nut.proto.SphynxGrpc;
import com.salesforce.apollo.archipelago.EndpointProvider;
import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.cryptography.DigestAlgorithm;
import com.salesforce.apollo.cryptography.EncryptionAlgorithm;
import com.salesforce.apollo.cryptography.cert.CertificateWithPrivateKey;
import com.salesforce.apollo.cryptography.ssl.CertificateValidator;
import com.salesforce.apollo.delphinius.Oracle;
import com.salesforce.apollo.utils.Utils;
import io.netty.handler.ssl.ClientAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.salesforce.apollo.cryptography.QualifiedBase64.qb64;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hal.hildebrand
 **/
public class E2ETest {
    private final int           cardinality = 10;
    private final int           threshold   = 6;
    private       List<Proc>    processes;
    private       List<Sphinx>  sphinxes;
    private       AtomicBoolean failures;

    @AfterEach
    public void after() {
        if (sphinxes != null) {
            for (Sphinx sphinx : sphinxes) {
                sphinx.shutdown();
            }
        }
        sphinxes = null;
        if (processes != null) {
            processes = null;
        }
    }

    @BeforeEach
    public void before() {
        Utils.clean(new File("target/e2e"));
        failures = new AtomicBoolean();
    }

    @Test
    public void smokin() throws Exception {
        byte[] associatedData = "Give me food or give me slack or kill me".getBytes(Charset.defaultCharset());
        var secretByteSize = 1024;
        var shares = initialize(cardinality, secretByteSize, threshold);
        var seed = sphinxes.getFirst();
        System.out.println();
        System.out.println("** Starting m 1 (seed)");
        System.out.println();
        var seedStart = seed.start();
        var identifier = qb64(unwrap(0, seed, shares, EncryptionAlgorithm.DEFAULT, associatedData));
        seedStart.get(30, TimeUnit.SECONDS);

        initializeKernel(cardinality, threshold, identifier);
        initializeRest(cardinality, threshold, identifier);

        // Bring up a minimal quorum
        var kernel = sphinxes.subList(1, 4);
        var m = new AtomicInteger(1);
        kernel.parallelStream().forEach(s -> {
            var mi = m.incrementAndGet() - 1;
            System.out.println();
            System.out.println("** Starting m " + (mi + 1));
            System.out.println();
            var start = s.start();
            start.whenComplete((v, t) -> {
                System.out.format("** Member %s has joined the view\n", (mi + 1));
            });
            unwrap(mi, s, shares, EncryptionAlgorithm.DEFAULT, associatedData);
            System.out.println();
            System.out.format("** Member: %s has been started\n", (mi + 1));
            System.out.println();
        });

        System.out.println();
        System.out.println("** Minimal quorum has started their views");
        System.out.println();

        var domains = sphinxes.subList(0, 4);
        Utils.waitForCondition(120_000, 1_000,
                               () -> failures.get() ? true : domains.stream().allMatch(s -> s.active()));
        assertTrue(domains.stream().allMatch(s -> s.active()),
                   "** Minimal quorum did not become active : " + (domains.stream()
                                                                          .filter(c -> !c.active())
                                                                          .map(d -> d.logState())
                                                                          .toList()));
        System.out.println();
        System.out.println("** Minimal quorum is active: " + domains.stream().map(s -> s.id()).toList());
        System.out.println();

        // Bring up the rest of the nodes.
        var remaining = sphinxes.subList(4, sphinxes.size());
        remaining.parallelStream().forEach(s -> {
            var mi = m.incrementAndGet() - 1;
            System.out.println();
            System.out.println("** Starting m " + (mi + 1));
            System.out.println();
            var start = s.start();
            start.whenComplete((v, t) -> {
                System.out.format("** %s has joined the view\n", (mi + 1));
            });
            unwrap(mi, s, shares, EncryptionAlgorithm.DEFAULT, associatedData);
            System.out.println();
            System.out.format("** Member: %s has been started\n", (mi + 1));
            System.out.println();
        });

        Utils.waitForCondition(120_000, 1_000,
                               () -> failures.get() ? true : sphinxes.stream().allMatch(Sphinx::active));
        if (!sphinxes.stream().allMatch(Sphinx::active)) {
            System.out.println();
            fail("\n\nNodes did not fully activate: \n" + (sphinxes.stream()
                                                                   .filter(c -> !c.active())
                                                                   .map(Sphinx::logState)
                                                                   .map(s -> "\t" + s + "\n")
                                                                   .toList()) + "\n\n");
        }
        System.out.println();
        System.out.println("** All nodes are active");
        System.out.println();

        Thread.sleep(1000);

        var oracle = sphinxes.get(0).getDelphi();
        oracle.add(new Oracle.Namespace("test")).get(120, TimeUnit.SECONDS);
        SkyTest.smoke(oracle);
    }

    private MtlsClient apiClient(int i, InetSocketAddress serverAddress) {
        CertificateWithPrivateKey clientCert = Utils.getMember(i);

        return new MtlsClient(serverAddress, ClientAuth.REQUIRE, "foo", clientCert.getX509Certificate(),
                              clientCert.getPrivateKey(), CertificateValidator.NONE);
    }

    private InputStream configFor(STGroup g, com.hellblazer.nut.E2ETest.Proc process, int n, int k,
                                  List<String> approach, String seed, String seedId, boolean genesis) {
        var t = g.getInstanceOf("sky");
        t.add("clusterEndpoint", process.clusterEndpoint);
        t.add("apiEndpoint", process.apiEndpoint);
        t.add("approachEndpoint", process.approachEndpoint);
        t.add("memberId", process.memberId);
        t.add("approach", approach);
        t.add("seedEndpoint", seed);
        t.add("seedId", seedId);
        t.add("n", n);
        t.add("k", k);
        t.add("genesis", genesis);
        var rendered = t.render();
        return new ByteArrayInputStream(rendered.getBytes(Charset.defaultCharset()));
    }

    private List<Share> initialize(int cardinality, int secretByteSize, int threshold) {
        STGroup g = new STGroupFile("src/test/resources/sky.stg");
        var authTag = DigestAlgorithm.DEFAULT.digest("Slack");

        var algorithm = EncryptionAlgorithm.DEFAULT;
        var entropy = new SecureRandom();
        var secrets = new ShareService(authTag, entropy, algorithm);
        var keys = IntStream.range(0, cardinality).mapToObj(i -> algorithm.generateKeyPair()).toList();
        var encryptedShares = secrets.shares(secretByteSize, keys.stream().map(KeyPair::getPublic).toList(), threshold);

        processes = IntStream.range(0, cardinality)
                             .mapToObj(i -> new com.hellblazer.nut.E2ETest.Proc(EndpointProvider.allocatePort(), i,
                                                                                EndpointProvider.allocatePort(),
                                                                                share(i, algorithm, keys,
                                                                                      encryptedShares),
                                                                                EndpointProvider.allocatePort()))
                             .toList();
        com.hellblazer.nut.E2ETest.Proc first = processes.getFirst();
        var seed = new Sphinx(configFor(g, first, cardinality, threshold, null, null, null, true));
        sphinxes = new ArrayList<>();
        sphinxes.add(seed);
        return processes.stream().map(p -> p.share).toList();
    }

    private void initializeKernel(int cardinality, int threshold, String seedId) {
        STGroup g = new STGroupFile("src/test/resources/sky.stg");

        com.hellblazer.nut.E2ETest.Proc first = processes.getFirst();
        processes.subList(1, 4)
                 .stream()
                 .map(p -> configFor(g, p, cardinality, threshold, Collections.singletonList(first.approachEndpoint),
                                     first.clusterEndpoint, seedId, true))
                 .map(c -> {
                     var sphinx = new Sphinx(c);
                     sphinx.setOnFailure(new CompletableFuture<Void>().whenComplete((v, t) -> {
                         failures.set(true);
                     }));
                     return sphinx;
                 })
                 .forEach(s -> sphinxes.add(s));
    }

    private void initializeRest(int cardinality, int threshold, String seedId) {
        STGroup g = new STGroupFile("src/test/resources/sky.stg");

        com.hellblazer.nut.E2ETest.Proc first = processes.getFirst();
        processes.subList(4, cardinality)
                 .stream()
                 .map(p -> configFor(g, p, cardinality, threshold, Collections.singletonList(first.approachEndpoint),
                                     first.clusterEndpoint, seedId, false))
                 .map(c -> {
                     var sphinx = new Sphinx(c);
                     sphinx.setOnFailure(new CompletableFuture<Void>().whenComplete((v, t) -> {
                         failures.set(true);
                     }));
                     return sphinx;
                 })
                 .forEach(s -> sphinxes.add(s));
    }

    private Share share(int i, EncryptionAlgorithm algorithm, List<KeyPair> keys,
                        List<EncryptedShare> encryptedShares) {
        var key = algorithm.decapsulate(keys.get(i).getPrivate(),
                                        encryptedShares.get(i).getEncapsulation().toByteArray(), Sphinx.AES);
        var encrypted = new Sphinx.Encrypted(encryptedShares.get(i).getShare().toByteArray(),
                                             encryptedShares.get(i).getIv().toByteArray(),
                                             encryptedShares.get(i).getAssociatedData().toByteArray());
        var plainText = Sphinx.decrypt(encrypted, key);
        try {
            return Share.parseFrom(plainText);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private Digest unwrap(int i, Sphinx sphinx, List<Share> shares, EncryptionAlgorithm algorithm,
                          byte[] associatedData) {
        var client = apiClient(i, (InetSocketAddress) sphinx.getApiEndpoint());

        try {
            var sphynxClient = SphynxGrpc.newBlockingStub(client.getChannel());
            var status = sphynxClient.unseal(Empty.getDefaultInstance());

            assertNotNull(status);
            assertTrue(status.getSuccess());
            assertEquals(0, status.getShares());

            var publicKey_ = sphynxClient.sessionKey(Empty.getDefaultInstance());
            assertNotNull(publicKey_);

            var publicKey = EncryptionAlgorithm.lookup(publicKey_.getAlgorithmValue())
                                               .publicKey(publicKey_.getPublicKey().toByteArray());
            assertNotNull(publicKey);

            var encapsulated = algorithm.encapsulated(publicKey);

            var secretKey = new SecretKeySpec(encapsulated.key().getEncoded(), "AES");

            int count = 0;
            var selected = new ArrayList<>(shares);
            Collections.shuffle(selected);
            var present = threshold + 1;
            for (var wrapped : selected.subList(0, present)) {
                var encrypted = Sphinx.encrypt(wrapped.toByteArray(), secretKey, associatedData);
                var encryptedShare = EncryptedShare.newBuilder()
                                                   .setIv(ByteString.copyFrom(encrypted.iv()))
                                                   .setAssociatedData(ByteString.copyFrom(associatedData))
                                                   .setShare(ByteString.copyFrom(encrypted.cipherText()))
                                                   .setEncapsulation(ByteString.copyFrom(encapsulated.encapsulation()))
                                                   .build();
                var result = sphynxClient.apply(encryptedShare);
                count++;
                assertEquals(count, result.getShares());
            }

            var unwrapStatus = sphynxClient.unwrap(Empty.getDefaultInstance());
            assertTrue(unwrapStatus.getSuccess());
            assertEquals(present, unwrapStatus.getShares());
            return Digest.from(unwrapStatus.getIdentifier());
        } finally {
            client.stop();
        }
    }

    public record Proc(String clusterEndpoint, int memberId, String apiEndpoint, Share share, String approachEndpoint) {
    }
}
