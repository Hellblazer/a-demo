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
import com.hellblazer.delos.cryptography.DigestAlgorithm;
import com.hellblazer.delos.cryptography.EncryptionAlgorithm;
import com.hellblazer.delos.cryptography.SignatureAlgorithm;
import com.hellblazer.delos.gorgoneion.proto.SignedNonce;
import com.hellblazer.sanctorum.proto.*;
import com.hellblazer.sky.constants.Constants;
import com.hellblazer.sky.sanctum.sanctorum.SanctumSanctorum;
import com.hellblazer.sky.sanctum.sanctorum.TokenGenerator;
import com.macasaet.fernet.Validator;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessSocketAddress;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hal.hildebrand
 **/
public class EnclaveTest {

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
            var encrypted = SanctumSanctorum.encrypt(wrapped.toByteArray(), secretKey, Constants.SHAMIR_TAG);
            var encryptedShare = EncryptedShare.newBuilder()
                                               .setIv(ByteString.copyFrom(encrypted.iv()))
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
        var address = new InProcessSocketAddress(UUID.randomUUID().toString());
        var devSecret = "Give me food or give me slack or kill me";
        var parameters = new SanctumSanctorum.Parameters(new SanctumSanctorum.Shamir(4, 3), DigestAlgorithm.DEFAULT,
                                                         EncryptionAlgorithm.DEFAULT, Constants.SHAMIR_TAG, address,
                                                         null);
        Function<SignedNonce, Any> attestation = n -> Any.getDefaultInstance();
        SanctumSanctorum sanctum = new SanctumSanctorum(parameters, attestation);
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
            var tokenGenerator = sanctum.getGenerator();
            var token = tokenGenerator.apply(Bytes.newBuilder().setB(ByteString.copyFrom(contents)).build());
            assertNotNull(token);

            var bytes = sanctumClient.validate(FernetValidate.newBuilder().setToken(token.serialise()).buildPartial());
            assertArrayEquals(contents, bytes.getB().toByteArray());
        } finally {
            client.shutdown();
            sanctum.shutdown();
        }
    }

    @Test
    public void testGenerator() throws Exception {
        var address = new InProcessSocketAddress(UUID.randomUUID().toString());
        var devSecret = "Give me food or give me slack or kill me";
        var parameters = new SanctumSanctorum.Parameters(new SanctumSanctorum.Shamir(4, 3), DigestAlgorithm.DEFAULT,
                                                         EncryptionAlgorithm.DEFAULT, Constants.SHAMIR_TAG, address,
                                                         null);
        Function<SignedNonce, Any> attestation = n -> Any.getDefaultInstance();
        SanctumSanctorum sanctum = new SanctumSanctorum(parameters, attestation);
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
            var tokenGenerator = sanctum.getGenerator();
            var token = tokenGenerator.apply(Bytes.newBuilder().setB(ByteString.copyFrom(contents)).build());
            assertNotNull(token);

            var hashed = new TokenGenerator.HashedToken(DigestAlgorithm.DEFAULT.digest(token.serialise()), token);

            var result = tokenGenerator.validate(new Validator<Bytes>() {
                @Override
                public Function<byte[], Bytes> getTransformer() {
                    return b -> Bytes.newBuilder().setB(ByteString.copyFrom(b)).build();
                }
            }, hashed);
            assertNotNull(result);

            for (int i = 0; i < 10; i++) {
                result = tokenGenerator.validate(new Validator<Bytes>() {
                    @Override
                    public Function<byte[], Bytes> getTransformer() {
                        return b -> Bytes.newBuilder().setB(ByteString.copyFrom(b)).build();
                    }
                }, hashed);
                assertNotNull(result);
            }
        } finally {
            client.shutdown();
            sanctum.shutdown();
        }
    }
}
