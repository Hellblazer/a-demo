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

package com.hellblazer.nut.support;

import com.codahale.shamir.Scheme;
import com.google.protobuf.ByteString;
import com.hellblazer.nut.Sphinx;
import com.hellblazer.sanctorum.proto.EncryptedShare;
import com.hellblazer.sanctorum.proto.Share;
import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.cryptography.EncryptionAlgorithm;

import javax.crypto.spec.SecretKeySpec;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Simple class to generate a new shared secret and distribute shares securely
 *
 * @author hal.hildebrand
 **/
public class ShareService {
    private final SecureRandom        entropy;
    private final EncryptionAlgorithm algorithm;
    private final Digest              authenticationTag;

    public ShareService(Digest authenticationTag, SecureRandom entropy, EncryptionAlgorithm algorithm) {
        this.entropy = entropy;
        this.algorithm = algorithm;
        this.authenticationTag = authenticationTag;
    }

    public List<EncryptedShare> shares(int secretByteSize, List<PublicKey> keys, int threshold) {
        var scheme = new Scheme(entropy, keys.size(), threshold);
        var secret = new byte[secretByteSize];
        entropy.nextBytes(secret);
        var shares = scheme.split(secret)
                           .entrySet()
                           .stream()
                           .map(e -> Share.newBuilder()
                                          .setKey(e.getKey())
                                          .setShare(ByteString.copyFrom(e.getValue()))
                                          .build())
                           .toList();
        return IntStream.range(0, keys.size()).mapToObj(i -> encrypt(shares.get(i), keys.get(i))).toList();
    }

    private EncryptedShare encrypt(Share s, PublicKey publicKey) {
        var encapsulated = algorithm.encapsulated(publicKey);
        var key = new SecretKeySpec(encapsulated.key().getEncoded(), Sphinx.AES);
        var associatedData = authenticationTag.getBytes();
        var encrypted = Sphinx.encrypt(s.toByteArray(), key, associatedData);
        return EncryptedShare.newBuilder()
                             .setAssociatedData(ByteString.copyFrom(associatedData))
                             .setIv(ByteString.copyFrom(encrypted.iv()))
                             .setEncapsulation(ByteString.copyFrom(encapsulated.encapsulation()))
                             .setShare(ByteString.copyFrom(encrypted.cipherText()))
                             .build();
    }
}
