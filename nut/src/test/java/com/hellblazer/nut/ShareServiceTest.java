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
import com.hellblazer.nut.proto.Share;
import com.salesforce.apollo.cryptography.DigestAlgorithm;
import com.salesforce.apollo.cryptography.EncryptionAlgorithm;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author hal.hildebrand
 **/
public class ShareServiceTest {
    @Test
    public void smokin() throws Exception {
        var authTag = DigestAlgorithm.DEFAULT.digest("Slack");
        var algorithm = EncryptionAlgorithm.DEFAULT;
        var entropy = new SecureRandom();
        var secrets = new ShareService(authTag, entropy, algorithm);
        var keys = IntStream.range(0, 3).mapToObj(i -> algorithm.generateKeyPair()).toList();
        var encryptedShares = secrets.shares(1024, keys.stream().map(kp -> kp.getPublic()).toList(), 2);
        assertEquals(keys.size(), encryptedShares.size());
        var shares = new ArrayList<Share>();
        for (int i = 0; i < keys.size(); i++) {
            var key = algorithm.decapsulate(keys.get(i).getPrivate(),
                                            encryptedShares.get(i).getEncapsulation().toByteArray(), Sphinx.AES);
            var encrypted = new Sphinx.Encrypted(encryptedShares.get(i).getShare().toByteArray(),
                                                 encryptedShares.get(i).getIv().toByteArray(),
                                                 encryptedShares.get(i).getAssociatedData().toByteArray());
            var plainText = Sphinx.decrypt(encrypted, key);
            shares.add(Share.parseFrom(plainText));
        } assertEquals(keys.size(), shares.size());
        var scheme = new Scheme(entropy, keys.size(), 2);
        var secret = scheme.join(
        shares.stream().limit(2).collect(Collectors.toMap(s -> s.getKey(), s -> s.getShare().toByteArray())));
        assertNotNull(secret);
        assertEquals(1024, secret.length);
    }
}
