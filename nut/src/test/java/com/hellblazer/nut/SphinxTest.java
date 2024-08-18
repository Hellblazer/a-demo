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

import com.codahale.shamir.Scheme;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.hellblazer.nut.comms.MtlsClient;
import com.hellblazer.nut.proto.SphynxGrpc;
import com.hellblazer.sanctorum.proto.EncryptedShare;
import com.hellblazer.sanctorum.proto.Share;
import com.hellblazer.delos.cryptography.EncryptionAlgorithm;
import com.hellblazer.delos.cryptography.cert.CertificateWithPrivateKey;
import com.hellblazer.delos.cryptography.ssl.CertificateValidator;
import com.hellblazer.delos.utils.Utils;
import io.netty.handler.ssl.ClientAuth;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.net.InetSocketAddress;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hal.hildebrand
 **/
public class SphinxTest {
    private static void clean() {
        new File("target/.id").delete();
        new File("target/.digest").delete();
        new File("target/kerl-state.mv.db").delete();
        new File("target/kerl-state.trace.db").delete();
    }

    @Test
    public void development() throws Exception {
        clean();
        var devSecret = "Give me food or give me slack or kill me";
        Sphinx sphinx;
        try (var is = getClass().getResourceAsStream("/sky-test.yaml")) {
            sphinx = new Sphinx(is, devSecret);
        }
        assertNotNull(sphinx);
        sphinx.start();
        sphinx.shutdown();
    }

    @Test
    public void shamir() throws Exception {
        clean();
        var devSecret = "Give me food or give me slack or kill me";
        Sphinx sphinx;
        try (var is = getClass().getResourceAsStream("/sky-test.yaml")) {
            sphinx = new Sphinx(is);
        }
        assertNotNull(sphinx);
        sphinx.start();
        assertNotNull(sphinx.getApiEndpoint());

        var client = client((InetSocketAddress) sphinx.getApiEndpoint());
        try {
            var sphynxClient = SphynxGrpc.newBlockingStub(client.getChannel());
            sphynxClient.unseal(Empty.getDefaultInstance());

            var publicKey_ = sphynxClient.sessionKey(Empty.getDefaultInstance());
            assertNotNull(publicKey_);

            var publicKey = EncryptionAlgorithm.lookup(publicKey_.getAlgorithmValue())
                                               .publicKey(publicKey_.getPublicKey().toByteArray());
            assertNotNull(publicKey);

            var algorithm = EncryptionAlgorithm.DEFAULT;

            var entropy = SecureRandom.getInstance("SHA1PRNG");
            entropy.setSeed(new byte[] { 6, 6, 6 });
            var scheme = new Scheme(entropy, 3, 2);
            var shares = scheme.split(devSecret.getBytes());

            var encapsulated = algorithm.encapsulated(publicKey);

            var secretKey = new SecretKeySpec(encapsulated.key().getEncoded(), "AES");

            int count = 0;
            for (var share : shares.entrySet()) {
                var wrapped = Share.newBuilder()
                                   .setKey(share.getKey())
                                   .setShare(ByteString.copyFrom(share.getValue()))
                                   .build();
                var associatedData = "Hello world    ".getBytes();
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

            sphynxClient.seal(Empty.getDefaultInstance());
        } finally {
            client.stop();
            sphinx.shutdown();
        }

    }

    private MtlsClient client(InetSocketAddress serverAddress) {
        CertificateWithPrivateKey clientCert = Utils.getMember(0);

        MtlsClient client = new MtlsClient(serverAddress, ClientAuth.REQUIRE, "foo", clientCert.getX509Certificate(),
                                           clientCert.getPrivateKey(), CertificateValidator.NONE);
        return client;
    }
}
