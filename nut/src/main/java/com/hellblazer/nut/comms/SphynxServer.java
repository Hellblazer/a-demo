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

package com.hellblazer.nut.comms;

import com.google.protobuf.Empty;
import com.hellblazer.nut.proto.EncryptedShare;
import com.hellblazer.nut.proto.Share;
import com.hellblazer.nut.proto.SphynxGrpc;
import com.hellblazer.nut.proto.Status;
import io.grpc.stub.StreamObserver;

public class SphynxServer extends SphynxGrpc.SphynxImplBase {
    @Override
    public void apply(EncryptedShare request, StreamObserver<Status> responseObserver) {
        super.apply(request, responseObserver);
    }

    @Override
    public void seal(Empty request, StreamObserver<Status> responseObserver) {
        super.seal(request, responseObserver);
    }

    @Override
    public void unseal(Empty request, StreamObserver<Status> responseObserver) {
        super.unseal(request, responseObserver);
    }
}
