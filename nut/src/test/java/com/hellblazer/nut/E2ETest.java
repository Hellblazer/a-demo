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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.salesforce.apollo.cryptography.QualifiedBase64.qb64;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hal.hildebrand
 **/
public class E2ETest {
    private List<Sphinx>  sphinxes;
    private List<Process> processes;

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
        System.out.println("** Starting M 1 seed");
        seed.start();
        var identifier = qb64(unwrap(0, seed, shares, EncryptionAlgorithm.DEFAULT, associatedData));
        seedStart.get(30, TimeUnit.SECONDS);

        initializeRest(cardinality, threshold, identifier);
        var sphinx = sphinxes.get(1);
        var nextStart = new CompletableFuture<Void>();
        sphinx.setOnStart(nextStart);
        System.out.println("** Starting M 2");
        sphinx.start();
        unwrap(1, sphinx, shares, EncryptionAlgorithm.DEFAULT, associatedData);
        nextStart.get(30, TimeUnit.SECONDS);

        int i = 2;
        for (var s : sphinxes.subList(2, sphinxes.size())) {
            Thread.sleep(1000);
            var start = new CompletableFuture<Void>();
            s.setOnStart(start);
            System.out.println("** Starting m " + (i + 1));
            s.start();
            unwrap(i, s, shares, EncryptionAlgorithm.DEFAULT, associatedData);
            start.get(60, TimeUnit.SECONDS);
            i++;
        }
        System.out.println("All nodes have been started and joined the View");
        final var activated = Utils.waitForCondition(5_000, 1_000, () -> sphinxes.stream().allMatch(Sphinx::active));
        if (!activated) {
            System.out.println("\n\nNodes did not fully activate: \n" + (sphinxes.stream()
                                                                                 .filter(c -> !c.active())
                                                                                 .map(Sphinx::logState)
                                                                                 .map(s -> "\t" + s + "\n")
                                                                                 .toList()) + "\n\n");
        }
    }

    private MtlsClient apiClient(int i, InetSocketAddress serverAddress) {
        CertificateWithPrivateKey clientCert = Utils.getMember(i);

        return new MtlsClient(serverAddress, ClientAuth.REQUIRE, "foo", clientCert.getX509Certificate(),
                              clientCert.getPrivateKey(), CertificateValidator.NONE);
    }

    private InputStream configFor(STGroup g, Process process, int n, int k, Integer approach, Integer seed,
                                  String seedId) {
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
                             .mapToObj(i -> new Process(Utils.allocatePort(), i, Utils.allocatePort(),
                                                        share(i, algorithm, keys, encryptedShares),
                                                        Utils.allocatePort()))
                             .toList();
        var seed = new Sphinx(configFor(g, processes.getFirst(), cardinality, threshold, null, null, null));
        sphinxes = new ArrayList<>();
        sphinxes.add(seed);
        return processes.stream().map(p -> p.share).toList();
    }

    private void initializeRest(int cardinality, int threshold, String seedId) {
        STGroup g = new STGroupFile("src/test/resources/sky.stg");

        processes.subList(1, cardinality)
                 .stream()
                 .map(p -> configFor(g, p, cardinality, threshold, processes.getFirst().approachPort,
                                     processes.getFirst().clusterPort, seedId))
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
            for (var wrapped : shares) {
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
            assertEquals(shares.size(), unwrapStatus.getShares());
            return Digest.from(unwrapStatus.getIdentifier());
        } finally {
            client.stop();
        }
    }

    record Process(int clusterPort, int memberId, int apiPort, Share share, int approachPort) {
    }
}
