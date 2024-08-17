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

import com.codahale.metrics.Timer.Context;
import com.google.protobuf.Empty;
import com.salesforce.apollo.stereotomy.event.proto.*;
import com.salesforce.apollo.stereotomy.services.grpc.StereotomyMetrics;
import com.salesforce.apollo.stereotomy.services.grpc.proto.*;
import com.salesforce.apollo.stereotomy.services.grpc.proto.KERLServiceGrpc.KERLServiceImplBase;
import com.salesforce.apollo.stereotomy.services.proto.ProtoKERLProvider;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.joou.ULong;

/**
 * @author hal.hildebrand
 */
public class EnclaveKERLServer extends KERLServiceImplBase {
    private final StereotomyMetrics metrics;
    private final ProtoKERLProvider service;

    public EnclaveKERLServer(ProtoKERLProvider service, StereotomyMetrics metrics) {
        this.metrics = metrics;
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
        Context timer = metrics != null ? metrics.getAttachmentService().time() : null;
        if (metrics != null) {
            final var serializedSize = request.getSerializedSize();
            metrics.inboundBandwidth().mark(serializedSize);
            metrics.inboundGetAttachmentRequest().mark(serializedSize);
        }
        var response = service.getAttachment(request);
        if (response == null) {
            if (timer != null) {
                timer.stop();
            }
            responseObserver.onNext(Attachment.getDefaultInstance());
            responseObserver.onCompleted();
        } else {
            if (timer != null) {
                timer.stop();
            }
            var attachment = response == null ? Attachment.getDefaultInstance() : response;
            responseObserver.onNext(attachment);
            responseObserver.onCompleted();
            if (metrics != null) {
                final var serializedSize = attachment.getSerializedSize();
                metrics.outboundBandwidth().mark(serializedSize);
                metrics.outboundGetAttachmentResponse().mark(serializedSize);
            }
        }
    }

    @Override
    public void getKERL(Ident request, StreamObserver<KERL_> responseObserver) {
        Context timer = metrics != null ? metrics.getKERLService().time() : null;
        if (metrics != null) {
            final var serializedSize = request.getSerializedSize();
            metrics.inboundBandwidth().mark(serializedSize);
            metrics.inboundGetKERLRequest().mark(serializedSize);
        }
        var response = service.getKERL(request);
        if (response == null) {
            if (timer != null) {
                timer.stop();
            }
            responseObserver.onNext(KERL_.getDefaultInstance());
            responseObserver.onCompleted();
        } else {
            if (timer != null) {
                timer.stop();
            }
            var kerl = response == null ? KERL_.getDefaultInstance() : response;
            responseObserver.onNext(kerl);
            responseObserver.onCompleted();
            if (metrics != null) {
                final var serializedSize = kerl.getSerializedSize();
                metrics.outboundBandwidth().mark(serializedSize);
                metrics.outboundGetKERLResponse().mark(serializedSize);
            }
        }
    }

    @Override
    public void getKeyEventCoords(EventCoords request, StreamObserver<KeyEvent_> responseObserver) {
        Context timer = metrics != null ? metrics.getKeyEventCoordsService().time() : null;
        if (metrics != null) {
            metrics.inboundBandwidth().mark(request.getSerializedSize());
            metrics.inboundGetKeyEventCoordsRequest().mark(request.getSerializedSize());
        }
        var response = service.getKeyEvent(request);
        if (response == null) {
            if (timer != null) {
                timer.stop();
            }
            responseObserver.onNext(KeyEvent_.getDefaultInstance());
            responseObserver.onCompleted();
        } else {
            if (timer != null) {
                timer.stop();
            }
            var event = response == null ? KeyEvent_.getDefaultInstance() : response;
            responseObserver.onNext(event);
            responseObserver.onCompleted();
            if (metrics != null) {
                final var serializedSize = event.getSerializedSize();
                metrics.outboundBandwidth().mark(serializedSize);
                metrics.outboundGetKeyEventCoordsResponse().mark(serializedSize);
            }
        }
    }

    @Override
    public void getKeyState(Ident request, StreamObserver<KeyState_> responseObserver) {
        Context timer = metrics != null ? metrics.getKeyStateService().time() : null;
        if (metrics != null) {
            final var serializedSize = request.getSerializedSize();
            metrics.inboundBandwidth().mark(serializedSize);
            metrics.inboundGetKeyStateRequest().mark(serializedSize);
        }
        var response = service.getKeyState(request);
        if (response == null) {
            if (timer != null) {
                timer.stop();
            }
            responseObserver.onNext(KeyState_.getDefaultInstance());
            responseObserver.onCompleted();
        } else {
            if (timer != null) {
                timer.stop();
            }
            var state = response == null ? KeyState_.getDefaultInstance() : response;
            responseObserver.onNext(state);
            responseObserver.onCompleted();
            if (metrics != null) {
                metrics.outboundBandwidth().mark(state.getSerializedSize());
                metrics.outboundGetKeyStateResponse().mark(state.getSerializedSize());
            }
        }
    }

    @Override
    public void getKeyStateCoords(EventCoords request, StreamObserver<KeyState_> responseObserver) {
        Context timer = metrics != null ? metrics.getKeyStateCoordsService().time() : null;
        if (metrics != null) {
            final var serializedSize = request.getSerializedSize();
            metrics.inboundBandwidth().mark(serializedSize);
            metrics.inboundGetKeyStateCoordsRequest().mark(serializedSize);
        }
        var response = service.getKeyState(request);
        if (response == null) {
            if (timer != null) {
                timer.stop();
            }
            responseObserver.onNext(KeyState_.getDefaultInstance());
            responseObserver.onCompleted();
        }
        if (timer != null) {
            timer.stop();
        }
        var state = response == null ? KeyState_.getDefaultInstance() : response;
        responseObserver.onNext(state);
        responseObserver.onCompleted();
        if (metrics != null) {
            final var serializedSize = state.getSerializedSize();
            metrics.outboundBandwidth().mark(serializedSize);
            metrics.outboundGetKeyStateCoordsResponse().mark(serializedSize);
        }
    }

    @Override
    public void getKeyStateSeqNum(IdentAndSeq request, StreamObserver<KeyState_> responseObserver) {
        Context timer = metrics != null ? metrics.getKeyStateService().time() : null;
        if (metrics != null) {
            final var serializedSize = request.getSerializedSize();
            metrics.inboundBandwidth().mark(serializedSize);
            metrics.inboundGetKeyStateRequest().mark(serializedSize);
        }
        var response = service.getKeyState(request.getIdentifier(), ULong.valueOf(request.getSequenceNumber()));
        if (response == null) {
            if (timer != null) {
                timer.stop();
            }
            responseObserver.onNext(KeyState_.getDefaultInstance());
            responseObserver.onCompleted();
        } else {
            if (timer != null) {
                timer.stop();
            }
            var state = response == null ? KeyState_.getDefaultInstance() : response;
            responseObserver.onNext(state);
            responseObserver.onCompleted();
            if (metrics != null) {
                metrics.outboundBandwidth().mark(state.getSerializedSize());
                metrics.outboundGetKeyStateResponse().mark(state.getSerializedSize());
            }
        }
    }

    @Override
    public void getKeyStateWithAttachments(EventCoords request,
                                           StreamObserver<KeyStateWithAttachments_> responseObserver) {
        Context timer = metrics != null ? metrics.getKeyStateService().time() : null;
        if (metrics != null) {
            final var serializedSize = request.getSerializedSize();
            metrics.inboundBandwidth().mark(serializedSize);
            metrics.inboundGetKeyStateRequest().mark(serializedSize);
        }
        var response = service.getKeyStateWithAttachments(request);
        if (response == null) {
            if (timer != null) {
                timer.stop();
            }
            responseObserver.onNext(KeyStateWithAttachments_.getDefaultInstance());
            responseObserver.onCompleted();
        } else {
            if (timer != null) {
                timer.stop();
            }
            var state = response == null ? KeyStateWithAttachments_.getDefaultInstance() : response;
            responseObserver.onNext(state);
            responseObserver.onCompleted();
            if (metrics != null) {
                metrics.outboundBandwidth().mark(state.getSerializedSize());
                metrics.outboundGetKeyStateResponse().mark(state.getSerializedSize());
            }
        }
    }

    @Override
    public void getValidations(EventCoords request, StreamObserver<Validations> responseObserver) {
        Context timer = metrics != null ? metrics.getAttachmentService().time() : null;
        if (metrics != null) {
            final var serializedSize = request.getSerializedSize();
            metrics.inboundBandwidth().mark(serializedSize);
            metrics.inboundGetAttachmentRequest().mark(serializedSize);
        }
        var response = service.getValidations(request);
        if (response == null) {
            if (timer != null) {
                timer.stop();
            }
            responseObserver.onNext(Validations.getDefaultInstance());
            responseObserver.onCompleted();
        } else {
            if (timer != null) {
                timer.stop();
            }
            var validations = response == null ? Validations.getDefaultInstance() : response;
            responseObserver.onNext(validations);
            responseObserver.onCompleted();
            if (metrics != null) {
                final var serializedSize = validations.getSerializedSize();
                metrics.outboundBandwidth().mark(serializedSize);
                metrics.outboundGetAttachmentResponse().mark(serializedSize);
            }
        }
    }
}
