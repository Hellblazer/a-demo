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

package com.hellblazer.sky.sanctum.sanctorum;

import com.google.protobuf.Empty;
import com.hellblazer.delos.stereotomy.event.proto.*;
import com.hellblazer.delos.stereotomy.services.grpc.proto.*;
import com.hellblazer.delos.stereotomy.services.grpc.proto.KERLServiceGrpc.KERLServiceImplBase;
import com.hellblazer.delos.stereotomy.services.proto.ProtoKERLProvider;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.joou.ULong;

/**
 * @author hal.hildebrand
 */
public class EnclaveKERLServer extends KERLServiceImplBase {
    private final ProtoKERLProvider service;

    public EnclaveKERLServer(ProtoKERLProvider service) {
        this.service = service;
    }

    @Override
    public void append(KeyEventsContext request, StreamObserver<KeyStates> responseObserver) {
        responseObserver.onError(new StatusRuntimeException(Status.PERMISSION_DENIED));
    }

    @Override
    public void appendAttachments(AttachmentsContext request, StreamObserver<Empty> responseObserver) {
        responseObserver.onError(new StatusRuntimeException(Status.PERMISSION_DENIED));
    }

    @Override
    public void appendKERL(KERLContext request, StreamObserver<KeyStates> responseObserver) {
        responseObserver.onError(new StatusRuntimeException(Status.PERMISSION_DENIED));
    }

    @Override
    public void appendValidations(Validations request, StreamObserver<Empty> responseObserver) {
        responseObserver.onError(new StatusRuntimeException(Status.PERMISSION_DENIED));
    }

    @Override
    public void appendWithAttachments(KeyEventWithAttachmentsContext request,
                                      StreamObserver<KeyStates> responseObserver) {
        responseObserver.onError(new StatusRuntimeException(Status.PERMISSION_DENIED));
    }

    @Override
    public void getAttachment(EventCoords request, StreamObserver<Attachment> responseObserver) {
        var response = service.getAttachment(request);
        var attachment = response == null ? Attachment.getDefaultInstance() : response;
        responseObserver.onNext(attachment);
        responseObserver.onCompleted();
    }

    @Override
    public void getKERL(Ident request, StreamObserver<KERL_> responseObserver) {
        var response = service.getKERL(request);
        var kerl = response == null ? KERL_.getDefaultInstance() : response;
        responseObserver.onNext(kerl);
        responseObserver.onCompleted();
    }

    @Override
    public void getKeyEventCoords(EventCoords request, StreamObserver<KeyEvent_> responseObserver) {
        var response = service.getKeyEvent(request);
        var event = response == null ? KeyEvent_.getDefaultInstance() : response;
        responseObserver.onNext(event);
        responseObserver.onCompleted();
    }

    @Override
    public void getKeyState(Ident request, StreamObserver<KeyState_> responseObserver) {
        var response = service.getKeyState(request);
        var state = response == null ? KeyState_.getDefaultInstance() : response;
        responseObserver.onNext(state);
        responseObserver.onCompleted();
    }

    @Override
    public void getKeyStateCoords(EventCoords request, StreamObserver<KeyState_> responseObserver) {
        var response = service.getKeyState(request);
        var state = response == null ? KeyState_.getDefaultInstance() : response;
        responseObserver.onNext(state);
        responseObserver.onCompleted();
    }

    @Override
    public void getKeyStateSeqNum(IdentAndSeq request, StreamObserver<KeyState_> responseObserver) {
        var response = service.getKeyState(request.getIdentifier(), ULong.valueOf(request.getSequenceNumber()));
        var state = response == null ? KeyState_.getDefaultInstance() : response;
        responseObserver.onNext(state);
        responseObserver.onCompleted();
    }

    @Override
    public void getKeyStateWithAttachments(EventCoords request,
                                           StreamObserver<KeyStateWithAttachments_> responseObserver) {
        var response = service.getKeyStateWithAttachments(request);
        var state = response == null ? KeyStateWithAttachments_.getDefaultInstance() : response;
        responseObserver.onNext(state);
        responseObserver.onCompleted();
    }

    @Override
    public void getValidations(EventCoords request, StreamObserver<Validations> responseObserver) {
        var response = service.getValidations(request);
        var validations = response == null ? Validations.getDefaultInstance() : response;
        responseObserver.onNext(validations);
        responseObserver.onCompleted();
    }
}
