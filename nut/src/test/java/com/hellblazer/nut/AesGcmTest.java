package com.hellblazer.nut;

import com.google.protobuf.ByteString;
import com.hellblazer.nut.proto.EncryptedShare;
import com.hellblazer.nut.proto.Share;
import com.salesforce.apollo.cryptography.EncryptionAlgorithm;
import com.salesforce.apollo.cryptography.QualifiedBase64;
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
    public void shareRoundTrip() throws Exception {
        var algorithm = EncryptionAlgorithm.X_25519;
        var sessionKeyPair = algorithm.generateKeyPair(secureRandom);
        var share = "give me food or give me slack or kill me".getBytes(StandardCharsets.UTF_8);
        var wrapped = Share.newBuilder().setKey(1).setShare(ByteString.copyFrom(share)).build();
        var associatedData = "Hello world    ".getBytes();

        var encapsulated = EncryptionAlgorithm.DEFAULT.encapsulated(sessionKeyPair.getPublic());
        var secretKey = new SecretKeySpec(encapsulated.key().getEncoded(), "AES");
        var encrypted = Sphinx.encrypt(wrapped.toByteArray(), secretKey, associatedData);

        var eShare = EncryptedShare.newBuilder()
                                   .setIv(ByteString.copyFrom(encrypted.iv()))
                                   .setAssociatedData(ByteString.copyFrom(associatedData))
                                   .setShare(ByteString.copyFrom(encrypted.cipherText()))
                                   .setEncapsulation(ByteString.copyFrom(encapsulated.encapsulation()))
                                   .build();

        var secretKey2 = algorithm.decapsulate(sessionKeyPair.getPrivate(), eShare.getEncapsulation().toByteArray(),
                                               Sphinx.AES);
        var en = new Sphinx.Encrypted(eShare.getShare().toByteArray(), eShare.getIv().toByteArray(),
                                      eShare.getAssociatedData().toByteArray());
        var decrypted = Sphinx.decrypt(en, secretKey2);
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
        SecretKey secretKey = new SecretKeySpec(key, Sphinx.AES);
        byte[] associatedData = "Something borrowed, something you".getBytes(StandardCharsets.UTF_8);

        String message = "Give me food or give me slack or kill me";

        var encrypted = Sphinx.encrypt(message.getBytes(StandardCharsets.UTF_8), secretKey, associatedData);
        String decrypted = new String(Sphinx.decrypt(encrypted, secretKey), StandardCharsets.UTF_8);

        assertEquals(message, decrypted);
    }

    @Test
    public void testIt() throws Exception {
       var d = QualifiedBase64.digest("FVfKoet2rqDRvpXe6dP7RWng9xj5WulOSUzDeyGy-wm4");
    }
}
