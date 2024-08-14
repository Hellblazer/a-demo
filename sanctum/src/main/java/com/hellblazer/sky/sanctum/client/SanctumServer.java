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
import com.hellblazer.sanctorum.proto.EncryptedShare;
import com.hellblazer.sanctorum.proto.Sanctum_Grpc;
import com.hellblazer.sanctorum.proto.Status;
import com.hellblazer.sanctorum.proto.UnwrapStatus;
import com.salesforce.apollo.cryptography.proto.Digeste;
import com.salesforce.apollo.gorgoneion.proto.PublicKey_;
import io.grpc.stub.StreamObserver;

public class SanctumServer extends Sanctum_Grpc.Sanctum_ImplBase {
    private final SanctumService service;

    public SanctumServer(SanctumService service) {
        this.service = service;
    }

    @Override
    public void apply(EncryptedShare request, StreamObserver<Status> responseObserver) {
        var status = service.apply(request);
        responseObserver.onNext(status);
        responseObserver.onCompleted();
    }

    @Override
    public void identifier(Empty request, StreamObserver<Digeste> responseObserver) {
        var id = service.identifier();
        responseObserver.onNext(id);
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
