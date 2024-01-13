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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hellblazer.nut.proto.EncryptedShare;
import com.hellblazer.nut.proto.Share;
import com.salesforce.apollo.cryptography.DigestAlgorithm;
import com.salesforce.apollo.cryptography.EncryptionAlgorithm;
import com.salesforce.apollo.utils.Utils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

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

    @Test
    public void smokin() throws Exception {
        var cardinality = 5;
        var secretByteSize = 1024;

        STGroup g = new STGroupFile("src/test/resources/sky.stg");
        var authTag = DigestAlgorithm.DEFAULT.digest("Slack");

        var algorithm = EncryptionAlgorithm.DEFAULT;
        var entropy = new SecureRandom();
        var secrets = new ShareService(authTag, entropy, algorithm);
        var keys = IntStream.range(0, cardinality).mapToObj(i -> algorithm.generateKeyPair()).toList();
        var encryptedShares = secrets.shares(secretByteSize, keys.stream().map(kp -> kp.getPublic()).toList(), 2);

        var processes = IntStream.range(0, cardinality)
                                 .mapToObj(i -> new Process(Utils.allocatePort(), i, Utils.allocatePort(),
                                                            share(i, algorithm, keys, encryptedShares)))
                                 .toList();
        var seeds = Collections.singletonList(new Endpoint("localhost", processes.getFirst().clusterPort, null));
        sphinxes = processes.stream().map(p -> configFor(g, p, seeds)).map(is -> new Sphinx(is)).toList();
        sphinxes.forEach(s -> s.start());

        sphinxes.forEach(s -> s.shutdown());
    }

    private InputStream configFor(STGroup g, Process process, List<Endpoint> seeds) {
        var t = g.getInstanceOf("sky");
        t.add("clusterPort", process.clusterPort);
        t.add("apiPort", process.apiPort);
        t.add("memberId", process.memberId);
        var rendered = t.render();
        return new ByteArrayInputStream(rendered.getBytes(Charset.defaultCharset()));
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

    record Process(int clusterPort, int memberId, int apiPort, Share share) {
    }
}
