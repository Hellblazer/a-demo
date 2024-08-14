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

import com.google.protobuf.Empty;
import com.hellblazer.sanctorum.proto.Enclave_Grpc;
import com.hellblazer.sanctorum.proto.EncryptedShare;
import com.hellblazer.sanctorum.proto.Status;
import com.hellblazer.sanctorum.proto.UnwrapStatus;
import com.salesforce.apollo.cryptography.proto.Digeste;
import com.salesforce.apollo.gorgoneion.proto.PublicKey_;
import io.grpc.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author hal.hildebrand
 **/
public class SanctumService {
    private static final Logger log = LoggerFactory.getLogger(SanctumService.class);

    private final Enclave_Grpc.Enclave_BlockingStub client;

    public SanctumService(Channel channel) {
        client = Enclave_Grpc.newBlockingStub(channel);
    }

    public Status apply(EncryptedShare eShare) {
        log.info("Applying encrypted share");
        return client.apply(eShare);
    }

    public Digeste identifier() {
        return client.identifier(Empty.getDefaultInstance());
    }

    public Status seal() {
        return client.seal(Empty.getDefaultInstance());
    }

    public PublicKey_ sessionKey() {
        return client.sessionKey(Empty.getDefaultInstance());
    }

    public Status unseal() {
        return client.unseal(Empty.getDefaultInstance());
    }

    public UnwrapStatus unwrap() {
        return client.unwrap(Empty.getDefaultInstance());
    }
}
