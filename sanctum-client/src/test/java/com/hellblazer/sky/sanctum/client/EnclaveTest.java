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

package com.hellblazer.sky.sanctum.client;

import com.codahale.shamir.Scheme;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.hellblazer.sanctorum.proto.Enclave_Grpc;
import com.hellblazer.sanctorum.proto.EncryptedShare;
import com.hellblazer.sanctorum.proto.FernetValidate;
import com.hellblazer.sanctorum.proto.Share;
import com.hellblazer.sky.sanctum.SanctumSanctorum;
import com.salesforce.apollo.cryptography.DigestAlgorithm;
import com.salesforce.apollo.cryptography.EncryptionAlgorithm;
import com.salesforce.apollo.cryptography.SignatureAlgorithm;
import com.salesforce.apollo.gorgoneion.proto.SignedNonce;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessSocketAddress;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hal.hildebrand
 **/
public class EnclaveTest {
    private static void clean() {
        new File("target/.id").delete();
        new File("target/.digest").delete();
        new File("target/kerl-state.mv.db").delete();
        new File("target/kerl-state.trace.db").delete();
    }

    private static void unwrap(Enclave_Grpc.Enclave_BlockingStub sanctumClient, String devSecret)
    throws NoSuchAlgorithmException {
        var status = sanctumClient.unseal(Empty.getDefaultInstance());

        assertNotNull(status);
        assertTrue(status.getSuccess());
        assertEquals(0, status.getShares());

        var publicKey_ = sanctumClient.sessionKey(Empty.getDefaultInstance());
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
            var associatedData = "Hello world".getBytes();
            var encrypted = SanctumSanctorum.encrypt(wrapped.toByteArray(), secretKey, associatedData);
            var encryptedShare = EncryptedShare.newBuilder()
                                               .setIv(ByteString.copyFrom(encrypted.iv()))
                                               .setAssociatedData(ByteString.copyFrom(associatedData))
                                               .setShare(ByteString.copyFrom(encrypted.cipherText()))
                                               .setEncapsulation(ByteString.copyFrom(encapsulated.encapsulation()))
                                               .build();
            var result = sanctumClient.apply(encryptedShare);
            count++;
            assertEquals(count, result.getShares());
        }

        var unwrapStatus = sanctumClient.unwrap(Empty.getDefaultInstance());
        assertTrue(unwrapStatus.getSuccess());
    }

    @Test
    public void smokin() throws Exception {
        clean();
        var address = new InProcessSocketAddress(UUID.randomUUID().toString());
        var target = "target";
        var devSecret = "Give me food or give me slack or kill me";
        var parameters = new SanctumSanctorum.Parameters("jdbc:h2:mem:enclave-%s;DB_CLOSE_DELAY=-1".formatted(address),
                                                         "JCEKS", Path.of(target, ".id"),
                                                         new SanctumSanctorum.Shamir(4, 3), Path.of(target, ".digest"),
                                                         DigestAlgorithm.DEFAULT);
        Function<SignedNonce, Any> attestation = n -> Any.getDefaultInstance();
        SanctumSanctorum sanctum = new SanctumSanctorum(EncryptionAlgorithm.DEFAULT, parameters, attestation, address);
        sanctum.start();

        var client = InProcessChannelBuilder.forName(address.getName()).usePlaintext().build();
        try {
            var sanctumClient = Enclave_Grpc.newBlockingStub(client);
            unwrap(sanctumClient, devSecret);
            var identifier = new EnclaveIdentifier(SignatureAlgorithm.DEFAULT, client);
            assertNotNull(identifier.getIdentifier().getDigest());
            assertEquals(sanctum.getId(), identifier.getIdentifier().getDigest());

            var kerl = identifier.getKerl();
            assertNotNull(kerl);
            assertEquals(1, kerl.size());

            var test = "Give me food or give me slack or kill me";
            var signed = identifier.getSigner().sign(test);
            assertNotNull(signed);
            assertTrue(identifier.getVerifier().get().verify(signed, "Give me food or give me slack or kill me"));

            var contents = new byte[] { 6, 6, 6 };
            var tokenGenerator = new TokenGenerator(client);
            var token = tokenGenerator.apply(contents);
            assertNotNull(token);

            var bytes = sanctumClient.validate(FernetValidate.newBuilder().setToken(token.serialise()).buildPartial());
            assertArrayEquals(contents, bytes.getB().toByteArray());
        } finally {
            client.shutdown();
            sanctum.shutdown();
        }
    }
}
