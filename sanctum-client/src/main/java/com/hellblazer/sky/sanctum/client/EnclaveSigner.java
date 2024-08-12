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

import com.google.protobuf.ByteString;
import com.hellblazer.sanctorum.proto.Enclave_Grpc;
import com.hellblazer.sanctorum.proto.Payload_;
import com.salesforce.apollo.cryptography.JohnHancock;
import com.salesforce.apollo.cryptography.SignatureAlgorithm;
import com.salesforce.apollo.cryptography.Signer;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author hal.hildebrand
 **/
public class EnclaveSigner implements Signer {
    private final Enclave_Grpc.Enclave_BlockingStub client;
    private final SignatureAlgorithm                signatureAlgorithm;

    public EnclaveSigner(Enclave_Grpc.Enclave_BlockingStub client, SignatureAlgorithm signatureAlgorithm) {
        this.client = client;
        this.signatureAlgorithm = signatureAlgorithm;
    }

    @Override
    public SignatureAlgorithm algorithm() {
        return signatureAlgorithm;
    }

    @Override
    public JohnHancock sign(InputStream message) {
        try {
            var sig = client.sign(Payload_.newBuilder().setPayload(ByteString.readFrom(message)).build());
            return JohnHancock.from(sig);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
