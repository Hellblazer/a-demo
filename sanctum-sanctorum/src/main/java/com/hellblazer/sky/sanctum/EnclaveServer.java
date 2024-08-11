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

import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import com.hellblazer.sanctorum.proto.*;
import com.salesforce.apollo.cryptography.proto.Digeste;
import com.salesforce.apollo.gorgoneion.proto.SignedNonce;
import io.grpc.stub.StreamObserver;

/**
 * A generic API GRPC MTLS server
 *
 * @author hal.hildebrand
 */
public class EnclaveServer extends Enclave_Grpc.Enclave_ImplBase {
    private final SanctumSanctorum.Service service;

    public EnclaveServer(SanctumSanctorum.Service service) {
        this.service = service;
    }

    @Override
    public void apply(EncryptedShare request, StreamObserver<Status> responseObserver) {
        var status = service.apply(request);
        responseObserver.onNext(status);
        responseObserver.onCompleted();
    }

    @Override
    public void attestation(SignedNonce request, StreamObserver<Any> responseObserver) {
        var attestation = service.attestation(request);
        responseObserver.onNext(attestation);
        responseObserver.onCompleted();
    }

    @Override
    public void identifier(Empty request, StreamObserver<Digeste> responseObserver) {
        var id = service.identifier();
        responseObserver.onNext(id);
        responseObserver.onCompleted();
    }

    @Override
    public void provision(Provisioning_ request, StreamObserver<Empty> responseObserver) {
        service.provision(request);
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void seal(Empty request, StreamObserver<Status> responseObserver) {
        var status = service.seal();
        responseObserver.onNext(status);
        responseObserver.onCompleted();
    }

    @Override
    public void sessionKey(Empty request, StreamObserver<PublicKey_> responseObserver) {
        var key = service.sessionKey();
        responseObserver.onNext(key);
        responseObserver.onCompleted();
    }

    @Override
    public void unseal(Empty request, StreamObserver<Status> responseObserver) {
        var status = service.unseal();
        responseObserver.onNext(status);
        responseObserver.onCompleted();
    }

    @Override
    public void unwrap(Empty request, StreamObserver<UnwrapStatus> responseObserver) {
        var status = service.unwrap();
        responseObserver.onNext(status);
        responseObserver.onCompleted();
    }
}
