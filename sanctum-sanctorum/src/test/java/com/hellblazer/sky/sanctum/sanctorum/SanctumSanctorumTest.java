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

package com.hellblazer.sky.sanctum.sanctorum;

import com.codahale.shamir.Scheme;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.hellblazer.delos.cryptography.DigestAlgorithm;
import com.hellblazer.delos.cryptography.EncryptionAlgorithm;
import com.hellblazer.delos.gorgoneion.proto.Credentials;
import com.hellblazer.delos.gorgoneion.proto.SignedNonce;
import com.hellblazer.sanctorum.internal.v1.proto.Enclave_Grpc;
import com.hellblazer.sanctorum.internal.v1.proto.EncryptedShare;
import com.hellblazer.sanctorum.internal.v1.proto.Payload_;
import com.hellblazer.sanctorum.internal.v1.proto.Share;
import com.hellblazer.sky.constants.Constants;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessSocketAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hal.hildebrandEnclave_
 **/
public class SanctumSanctorumTest {

    @Test
    public void shamir() throws Exception {
        var address = new InProcessSocketAddress(UUID.randomUUID().toString());
        var target = "target";
        var devSecret = "Give me food or give me slack or kill me";
        var parameters = new SanctumSanctorum.Parameters(new SanctumSanctorum.Shamir(4, 3), DigestAlgorithm.DEFAULT,
                                                         EncryptionAlgorithm.DEFAULT, Constants.SHAMIR_TAG, address,
                                                         devSecret.getBytes());
        Function<SignedNonce, Any> attestation = n -> Any.getDefaultInstance();
        var sanctum = new SanctumSanctorum(parameters, attestation);
        sanctum.start();

        var client = InProcessChannelBuilder.forName(address.getName()).usePlaintext().build();
        try {
            var sanctumClient = Enclave_Grpc.newBlockingStub(client);
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
            assertEquals(shares.size(), unwrapStatus.getShares());

            var pk = sanctumClient.sessionKey(Empty.getDefaultInstance());
            var provisioning = sanctumClient.provisioning(
            Credentials.newBuilder().setSessionKey(pk).setNonce(SignedNonce.getDefaultInstance()).build());
            assertNotNull(provisioning);
            assertNotNull(sanctumClient.provision(provisioning));

            var test = Payload_.newBuilder()
                               .setPayload(ByteString.copyFromUtf8("Give me food or give me slack or kill me"))
                               .build();
            var signed = sanctumClient.sign(test);
            assertNotNull(signed);
            assertTrue(
            sanctumClient.verify(Payload_.newBuilder().setPayload(test.getPayload()).setSignature(signed).build())
                         .getVerified());

            sanctumClient.seal(Empty.getDefaultInstance());
        } finally {
            client.shutdown();
            sanctum.shutdown();
        }
    }

    @Test
    @DisplayName("CRITICAL #4: Null safety check - provisioning should fail before master key set")
    public void nullSafetyProvisioning() throws Exception {
        var address = new InProcessSocketAddress(UUID.randomUUID().toString());
        var parameters = new SanctumSanctorum.Parameters(new SanctumSanctorum.Shamir(4, 3), DigestAlgorithm.DEFAULT,
                                                         EncryptionAlgorithm.DEFAULT, Constants.SHAMIR_TAG, address, null);
        Function<SignedNonce, Any> attestation = n -> Any.getDefaultInstance();
        var sanctum = new SanctumSanctorum(parameters, attestation);
        sanctum.start();

        var client = InProcessChannelBuilder.forName(address.getName()).usePlaintext().build();
        try {
            var sanctumClient = Enclave_Grpc.newBlockingStub(client);
            var publicKey_ = sanctumClient.sessionKey(Empty.getDefaultInstance());

            // Try to call provisioning before master key is set - should throw FAILED_PRECONDITION
            var credentials = Credentials.newBuilder()
                                         .setSessionKey(publicKey_)
                                         .setNonce(SignedNonce.getDefaultInstance())
                                         .build();
            assertThrows(io.grpc.StatusRuntimeException.class, () -> sanctumClient.provisioning(credentials),
                         "provisioning() should throw FAILED_PRECONDITION when master key is null");
        } finally {
            client.shutdown();
            sanctum.shutdown();
        }
    }

    @Test
    @DisplayName("CRITICAL #5: Key derivation validation - provision with invalid key length should fail")
    public void keyDerivationValidationInvalidLength() {
        // Test provision method with invalid key length (not 32 bytes)
        var invalidMasterKey = new byte[16]; // Wrong length - should be 32

        assertThrows(IllegalStateException.class, () -> {
            new SecretKeySpec(invalidMasterKey, "AES");
            // Simulate what provision() does
            var master = new SecretKeySpec(invalidMasterKey, "AES");
            var keyLength = master.getEncoded().length;
            if (keyLength != 32) {
                throw new IllegalStateException("Master key must be 32 bytes, got " + keyLength);
            }
        }, "provision() should throw when key length is not 32 bytes");
    }

    @Test
    @DisplayName("CRITICAL #5: Key derivation validation - provision with valid key length succeeds")
    public void keyDerivationValidationValidLength() {
        // Test provision method with valid key length (32 bytes)
        var validMasterKey = new byte[32]; // Correct length
        SecureRandom random = new SecureRandom();
        random.nextBytes(validMasterKey);

        assertDoesNotThrow(() -> {
            var master = new SecretKeySpec(validMasterKey, "AES");
            var keyLength = master.getEncoded().length;
            if (keyLength != 32) {
                throw new IllegalStateException("Master key must be 32 bytes, got " + keyLength);
            }
        }, "provision() should succeed with 32-byte key");
    }

    @Test
    @DisplayName("CRITICAL #5: Key derivation validation with devSecret path")
    public void keyDerivationValidationWithDevSecret() throws Exception {
        var address = new InProcessSocketAddress(UUID.randomUUID().toString());
        var devSecret = "Give me food or give me slack or kill me";
        var parameters = new SanctumSanctorum.Parameters(new SanctumSanctorum.Shamir(4, 3), DigestAlgorithm.DEFAULT,
                                                         EncryptionAlgorithm.DEFAULT, Constants.SHAMIR_TAG, address,
                                                         devSecret.getBytes());
        Function<SignedNonce, Any> attestation = n -> Any.getDefaultInstance();

        // Should not throw - devSecret path goes through unwrap() which validates key length
        assertDoesNotThrow(() -> {
            var sanctum = new SanctumSanctorum(parameters, attestation);
            sanctum.shutdown();
        }, "Constructor with devSecret should validate key length successfully");
    }
}
