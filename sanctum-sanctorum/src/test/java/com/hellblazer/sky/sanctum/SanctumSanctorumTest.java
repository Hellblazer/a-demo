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

package com.hellblazer.sky.sanctum;

import com.codahale.shamir.Scheme;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.hellblazer.sanctorum.proto.Enclave_Grpc;
import com.hellblazer.sanctorum.proto.EncryptedShare;
import com.hellblazer.sanctorum.proto.Share;
import com.salesforce.apollo.cryptography.DigestAlgorithm;
import com.salesforce.apollo.cryptography.EncryptionAlgorithm;
import com.salesforce.apollo.gorgoneion.proto.SignedNonce;
import io.grpc.ServerBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hal.hildebrandEnclave_
 **/
public class SanctumSanctorumTest {
    private static void clean() {
        new File("target/.id").delete();
        new File("target/.digest").delete();
        new File("target/kerl-state.mv.db").delete();
        new File("target/kerl-state.trace.db").delete();
    }

    @Test
    public void shamir() throws Exception {
        clean();
        var name = UUID.randomUUID().toString();
        var target = "target";
        var devSecret = "Give me food or give me slack or kill me";
        var parameters = new SanctumSanctorum.EnclaveParameters("jdbc:h2:mem:id-kerl;DB_CLOSE_DELAY=-1", "JCEKS",
                                                                Path.of(target, ".id"),
                                                                new SanctumSanctorum.Shamir(4, 3),
                                                                Path.of(target, ".digest"), DigestAlgorithm.DEFAULT);
        Function<SignedNonce, Any> attestation = null;
        ServerBuilder builder = InProcessServerBuilder.forName(name);
        SanctumSanctorum sanctum = new SanctumSanctorum(EncryptionAlgorithm.DEFAULT, parameters, attestation, builder);
        sanctum.start();

        var client = InProcessChannelBuilder.forName(name).usePlaintext().build();
        try {
            var sphynxClient = Enclave_Grpc.newBlockingStub(client);
            var status = sphynxClient.unseal(Empty.getDefaultInstance());

            assertNotNull(status);
            assertTrue(status.getSuccess());
            assertEquals(0, status.getShares());

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
                var encrypted = SanctumSanctorum.encrypt(wrapped.toByteArray(), secretKey, associatedData);
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
            client.shutdown();
            sanctum.shutdown();
        }
    }
}
