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
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hal.hildebrand
 **/
public class E2ETest {
    private List<Sphinx> sphinxes;

    @AfterEach
    public void after() {
        if (sphinxes != null) {
            for (Sphinx sphinx : sphinxes) {
                sphinx.shutdown();
            }
        }
        sphinxes = null;
    }

    @BeforeEach
    public void before() {
        Utils.clean(new File("target/e2e"));
    }

    @Test
    public void smokin() throws Exception {
        byte[] associatedData = "Give me food or give me slack or kill me".getBytes(Charset.defaultCharset());
        var cardinality = 5;
        var threshold = 3;
        var secretByteSize = 1024;
        var shares = initialize(cardinality, secretByteSize, threshold);

        sphinxes.forEach(s -> s.start());

        // seed first
        unwrap(0, sphinxes.getFirst(), shares, EncryptionAlgorithm.DEFAULT, associatedData);

        // then the rest of the crew
        for (int i = 1; i < cardinality; i++) {
            unwrap(i, sphinxes.get(i), shares, EncryptionAlgorithm.DEFAULT, associatedData);
        }

        sphinxes.forEach(s -> s.shutdown());
    }

    private MtlsClient apiClient(int i, InetSocketAddress serverAddress) {
        CertificateWithPrivateKey clientCert = Utils.getMember(i);

        MtlsClient client = new MtlsClient(serverAddress, ClientAuth.REQUIRE, "foo", clientCert.getX509Certificate(),
                                           clientCert.getPrivateKey(), CertificateValidator.NONE);
        return client;
    }

    private InputStream configFor(STGroup g, Process process, int n, int k, Integer seedClusterPort) {
        var t = g.getInstanceOf("sky");
        t.add("clusterPort", process.clusterPort);
        t.add("apiPort", process.apiPort);
        t.add("memberId", process.memberId);
        t.add("seed", seedClusterPort);
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
        var encryptedShares = secrets.shares(secretByteSize, keys.stream().map(kp -> kp.getPublic()).toList(),
                                             threshold);

        var processes = IntStream.range(0, cardinality)
                                 .mapToObj(i -> new Process(Utils.allocatePort(), i, Utils.allocatePort(),
                                                            share(i, algorithm, keys, encryptedShares)))
                                 .toList();
        var seed = new Sphinx(configFor(g, processes.getFirst(), cardinality, threshold, null));
        sphinxes = new ArrayList<>();
        sphinxes.add(seed);

        processes.subList(1, cardinality)
                 .stream()
                 .map(p -> configFor(g, p, cardinality, threshold, processes.getFirst().clusterPort))
                 .map(is -> new Sphinx(is))
                 .forEach(s -> sphinxes.add(s));
        return processes.stream().map(p -> p.share).toList();
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

    private void unwrap(int i, Sphinx sphinx, List<Share> shares, EncryptionAlgorithm algorithm,
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
        } finally {
            if (client != null) {
                client.stop();
            }
        }
    }

    record Process(int clusterPort, int memberId, int apiPort, Share share) {
    }
}
