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
import com.salesforce.apollo.cryptography.SigningThreshold;
import com.salesforce.apollo.cryptography.Verifier;
import com.salesforce.apollo.stereotomy.event.protobuf.ProtobufEventFactory;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Verifier using the SanctumService enclave
 *
 * @author hal.hildebrand
 **/
public class EnclaveVerifier implements Verifier {
    private final Enclave_Grpc.Enclave_BlockingStub client;

    public EnclaveVerifier(Enclave_Grpc.Enclave_BlockingStub client) {
        this.client = client;
    }

    @Override
    public boolean verify(JohnHancock signature, InputStream message) {
        try {
            return client.verify(
                         Payload_.newBuilder().setSignature(signature.toSig()).setPayload(ByteString.readFrom(message)).build())
                         .getVerified();
        } catch (IOException e) {
            LoggerFactory.getLogger(EnclaveVerifier.class).error("Error verifying signature", e);
            return false;
        }
    }

    @Override
    public boolean verify(SigningThreshold threshold, JohnHancock signature, InputStream message) {
        try {
            return client.verify(Payload_.newBuilder()
                                         .setSignature(signature.toSig())
                                         .setThreshold(ProtobufEventFactory.toSigningThreshold(threshold))
                                         .setPayload(ByteString.readFrom(message))
                                         .build()).getVerified();
        } catch (IOException e) {
            LoggerFactory.getLogger(EnclaveVerifier.class).error("Error verifying signature", e);
            return false;
        }
    }
}
