package com.hellblazer.nut;

import com.google.protobuf.ByteString;
import com.hellblazer.nut.proto.EncryptedShare;
import com.hellblazer.nut.proto.Share;
import com.salesforce.apollo.cryptography.EncryptionAlgorithm;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A simple showcase for encryption and decryption with AES + GCM in Java
 */
public class AesGcmTest {
    private final static int          GCM_IV_LENGTH = 12;
    private final        SecureRandom secureRandom  = new SecureRandom();

    @Test
    public void applyScenario() throws Exception {
        var algorithm = EncryptionAlgorithm.X_25519;
        var sessionKeyPair = algorithm.generateKeyPair(secureRandom);
        var share = new byte[] { 'a', 'b', 'a', 'b', 'a', 'b', 'a', 'b', 'a', 'b', 'a', 'b', 'a', 'b', 'a', 'b', 'a',
                                 'b', 'a', 'b', 'a', 'b', 'a', 'b' };
        var wrapped = Share.newBuilder().setKey(1).setShare(ByteString.copyFrom(share)).build();
        var associatedData = "Hello world    ".getBytes();

        var encapsulated = EncryptionAlgorithm.DEFAULT.encapsulated(sessionKeyPair.getPublic());
        var secretKey = new SecretKeySpec(encapsulated.key().getEncoded(), "AES");
        var encrypted = Sphinx.encrypt(wrapped.toByteArray(), secretKey, associatedData);

        var eShare = EncryptedShare.newBuilder()
                                   .setAssociatedData(ByteString.copyFrom(associatedData))
                                   .setShare(ByteString.copyFrom(encrypted))
                                   .setEncapsulation(ByteString.copyFrom(encapsulated.encapsulation()))
                                   .build();

        var secretKey2 = algorithm.decapsulate(sessionKeyPair.getPrivate(), eShare.getEncapsulation().toByteArray(),
                                               "AES");
        var decrypted = Sphinx.decrypt(eShare.getShare().toByteArray(), secretKey2,
                                       eShare.getAssociatedData().toByteArray());
        var result = Share.parseFrom(decrypted);

        assertNotNull(result);
        assertEquals(1, result.getKey());

        assertArrayEquals(share, result.getShare().toByteArray());
    }

    @Test
    public void smokin() throws Exception {
        //create new random key
        byte[] key = new byte[16];
        secureRandom.nextBytes(key);
        SecretKey secretKey = new SecretKeySpec(key, "AES");
        byte[] associatedData = "ProtocolVersion1".getBytes(
        StandardCharsets.UTF_8); //meta data you want to verify with the secret message

        String message = "the secret message";

        byte[] cipherText = Sphinx.encrypt(message.getBytes(StandardCharsets.UTF_8), secretKey, associatedData);
        String decrypted = new String(Sphinx.decrypt(cipherText, secretKey, associatedData), StandardCharsets.UTF_8);

        assertEquals(message, decrypted);
    }
}
