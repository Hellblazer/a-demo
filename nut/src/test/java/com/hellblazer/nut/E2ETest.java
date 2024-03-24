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
import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.cryptography.DigestAlgorithm;
import com.salesforce.apollo.cryptography.EncryptionAlgorithm;
import com.salesforce.apollo.cryptography.cert.CertificateWithPrivateKey;
import com.salesforce.apollo.cryptography.ssl.CertificateValidator;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.salesforce.apollo.cryptography.QualifiedBase64.qb64;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hal.hildebrand
 **/
public class E2ETest {
    public  List<com.hellblazer.nut.E2ETest.Proc> processes;
    private List<Sphinx>                          sphinxes;

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
    }

    @Test
    public void smokin() throws Exception {
        byte[] associatedData = "Give me food or give me slack or kill me".getBytes(Charset.defaultCharset());
        var cardinality = 7;
        var threshold = 4;
        var secretByteSize = 1024;
        var shares = initialize(cardinality, secretByteSize, threshold);
        var seedStart = new CompletableFuture<Void>();
        var seed = sphinxes.getFirst();
        seed.setOnStart(seedStart);
        System.out.println();
        System.out.println("** Starting m 1 (seed)");
        System.out.println();
        seed.start();
        var identifier = qb64(unwrap(0, seed, shares, EncryptionAlgorithm.DEFAULT, associatedData));
        seedStart.get(30, TimeUnit.SECONDS);

        initializeKernel(cardinality, threshold, identifier);
        initializeRest(cardinality, threshold, identifier);
        var sphinx = sphinxes.get(1);
        var nextStart = new CompletableFuture<Void>();
        sphinx.setOnStart(nextStart);
        System.out.println();
        System.out.println("** Starting m 2");
        System.out.println();
        sphinx.start();
        unwrap(1, sphinx, shares, EncryptionAlgorithm.DEFAULT, associatedData);
        nextStart.get(30, TimeUnit.SECONDS);

        // Bring up a minimal quorum
        var kernel = sphinxes.subList(2, 4);
        var m = new AtomicInteger(2);
        kernel.parallelStream().forEach(s -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            var mi = m.incrementAndGet() - 1;
            var start = new CompletableFuture<Void>();
            s.setOnStart(start.whenComplete((v, t) -> {
                System.out.format("** Member %s has joined the view", (mi + 1));
            }));
            System.out.println();
            System.out.println("** Starting m " + (mi + 1));
            System.out.println();
            s.start();
            unwrap(mi, s, shares, EncryptionAlgorithm.DEFAULT, associatedData);
            System.out.println();
            System.out.format("** Member: %s has been started", (mi + 1));
            System.out.println();
        });

        System.out.println();
        System.out.println("** Minimal quorum has been started and have joined the view");
        System.out.println();

        var domains = sphinxes.subList(0, 4);
        var activated = Utils.waitForCondition(360_000, 1_000,
                                               () -> domains.stream().filter(c -> !c.active()).count() == 0);
        assertTrue(activated, "** Minimal quorum did not become active : " + (domains.stream()
                                                                                     .filter(c -> !c.active())
                                                                                     .map(d -> d.logState())
                                                                                     .toList()));
        System.out.println();
        System.out.println("** Minimal quorum is active");
        System.out.println();

        // Bring up the rest of the nodes.
        var remaining = sphinxes.subList(4, sphinxes.size());
        remaining.parallelStream().forEach(s -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
            var mi = m.incrementAndGet() - 1;
            var start = new CompletableFuture<Void>();
            s.setOnStart(start.whenComplete((v, t) -> {
                System.out.format("** %s has joined the view", (mi + 1));
            }));
            System.out.println();
            System.out.println("** Starting m " + (mi + 1));
            System.out.println();
            s.start();
            unwrap(mi, s, shares, EncryptionAlgorithm.DEFAULT, associatedData);
            System.out.println();
            System.out.format("** Member: %s has been started", (mi + 1));
            System.out.println();
        });

        activated = Utils.waitForCondition(180_000, 1_000, () -> sphinxes.stream().allMatch(Sphinx::active));
        if (!activated) {
            System.out.println();
            System.out.println("\n\nNodes did not fully activate: \n" + (sphinxes.stream()
                                                                                 .filter(c -> !c.active())
                                                                                 .map(Sphinx::logState)
                                                                                 .map(s -> "\t" + s + "\n")
                                                                                 .toList()) + "\n\n");
            System.out.println();
        } else {
            System.out.println();
            System.out.println("** All nodes are active");
            System.out.println();
        }
    }

    private MtlsClient apiClient(int i, InetSocketAddress serverAddress) {
        CertificateWithPrivateKey clientCert = Utils.getMember(i);

        return new MtlsClient(serverAddress, ClientAuth.REQUIRE, "foo", clientCert.getX509Certificate(),
                              clientCert.getPrivateKey(), CertificateValidator.NONE);
    }

    private InputStream configFor(STGroup g, com.hellblazer.nut.E2ETest.Proc process, int n, int k, Integer approach,
                                  Integer seed, String seedId, boolean genesis) {
        var t = g.getInstanceOf("sky");
        t.add("clusterPort", process.clusterPort);
        t.add("apiPort", process.apiPort);
        t.add("approachPort", process.approachPort);
        t.add("memberId", process.memberId);
        t.add("approach", approach);
        t.add("seedPort", seed);
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
                             .mapToObj(
                             i -> new com.hellblazer.nut.E2ETest.Proc(Utils.allocatePort(), i, Utils.allocatePort(),
                                                                      share(i, algorithm, keys, encryptedShares),
                                                                      Utils.allocatePort()))
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
                 .map(p -> configFor(g, p, cardinality, threshold, first.approachPort, first.clusterPort, seedId, true))
                 .map(c -> new Sphinx(c))
                 .forEach(s -> sphinxes.add(s));
    }

    private void initializeRest(int cardinality, int threshold, String seedId) {
        STGroup g = new STGroupFile("src/test/resources/sky.stg");

        com.hellblazer.nut.E2ETest.Proc first = processes.getFirst();
        processes.subList(4, cardinality)
                 .stream()
                 .map(
                 p -> configFor(g, p, cardinality, threshold, first.approachPort, first.clusterPort, seedId, false))
                 .map(c -> new Sphinx(c))
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
            var present = 4;
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

    public record Proc(int clusterPort, int memberId, int apiPort, Share share, int approachPort) {
    }
}
