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
import com.salesforce.apollo.cryptography.DigestAlgorithm;
import com.salesforce.apollo.cryptography.JohnHancock;
import com.salesforce.apollo.stereotomy.EventCoordinates;
import com.salesforce.apollo.stereotomy.KEL;
import com.salesforce.apollo.stereotomy.KERL;
import com.salesforce.apollo.stereotomy.KERL.EventWithAttachments;
import com.salesforce.apollo.stereotomy.KeyState;
import com.salesforce.apollo.stereotomy.event.EstablishmentEvent;
import com.salesforce.apollo.stereotomy.event.KeyEvent;
import com.salesforce.apollo.stereotomy.event.KeyStateWithEndorsementsAndValidations;
import com.salesforce.apollo.stereotomy.event.proto.*;
import com.salesforce.apollo.stereotomy.event.protobuf.AttachmentEventImpl;
import com.salesforce.apollo.stereotomy.event.protobuf.ProtobufEventFactory;
import com.salesforce.apollo.stereotomy.identifier.Identifier;
import com.salesforce.apollo.stereotomy.services.proto.ProtoKERLService;
import org.joou.ULong;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author hal.hildebrand
 */
public class ProtoKERLReadAdapter implements ProtoKERLService {

    private final Supplier<KERL.AppendKERL> kerl;

    public ProtoKERLReadAdapter(Supplier<KERL.AppendKERL> kerl) {
        this.kerl = kerl;
    }

    @Override
    public List<KeyState_> append(KERL_ k) {
        List<KeyEvent> events = new ArrayList<>();
        List<com.salesforce.apollo.stereotomy.event.AttachmentEvent> attachments = new ArrayList<>();
        k.getEventsList().stream().map(ProtobufEventFactory::from).forEach(ewa -> {
            events.add(ewa.event());
            attachments.add(
            ProtobufEventFactory.INSTANCE.attachment((EstablishmentEvent) ewa.event(), ewa.attachments()));
        });
        return getKerl().append(events, attachments)
                        .stream()
                        .map(ks -> ks == null ? KeyState_.getDefaultInstance() : ks.toKeyState_())
                        .toList();
    }

    @Override
    public List<KeyState_> append(List<KeyEvent_> keyEventList) {
        KeyEvent[] events = new KeyEvent[keyEventList.size()];
        int i = 0;
        for (KeyEvent event : keyEventList.stream().map(ProtobufEventFactory::from).toList()) {
            events[i++] = event;
        }
        List<KeyState> keyStates = getKerl().append(events);
        return keyStates == null ? Collections.emptyList() : (keyStates.stream()
                                                                       .map(
                                                                       ks -> ks == null ? KeyState_.getDefaultInstance()
                                                                                        : ks.toKeyState_())
                                                                       .toList());
    }

    @Override
    public List<KeyState_> append(List<KeyEvent_> eventsList, List<AttachmentEvent> attachmentsList) {
        return getKerl().append(eventsList.stream().map(ProtobufEventFactory::from).toList(), attachmentsList.stream()
                                                                                                             .map(
                                                                                                             AttachmentEventImpl::new)
                                                                                                             .map(
                                                                                                             e -> (com.salesforce.apollo.stereotomy.event.AttachmentEvent) e)
                                                                                                             .toList())
                        .stream()
                        .map(ks -> ks == null ? null : ks.toKeyState_())
                        .toList();
    }

    @Override
    public Empty appendAttachments(List<AttachmentEvent> attachments) {
        getKerl().append(attachments.stream()
                                    .map(AttachmentEventImpl::new)
                                    .map(e -> (com.salesforce.apollo.stereotomy.event.AttachmentEvent) e)
                                    .toList());
        return Empty.getDefaultInstance();
    }

    @Override
    public Empty appendValidations(Validations validations) {
        getKerl().appendValidations(EventCoordinates.from(validations.getCoordinates()),
                                    validations.getValidationsList()
                                               .stream()
                                               .collect(Collectors.toMap(v -> EventCoordinates.from(v.getValidator()),
                                                                         v -> JohnHancock.from(v.getSignature()))));
        return Empty.getDefaultInstance();
    }

    @Override
    public Attachment getAttachment(EventCoords coordinates) {
        var attch = getKerl().getAttachment(EventCoordinates.from(coordinates));
        return attch == null ? Attachment.getDefaultInstance() : attch.toAttachemente();
    }

    public DigestAlgorithm getDigestAlgorithm() {
        return getKerl().getDigestAlgorithm();
    }

    @Override
    public KERL_ getKERL(Ident identifier) {
        List<EventWithAttachments> kerl = this.getKerl().kerl(Identifier.from(identifier));
        return kerl == null ? KERL_.getDefaultInstance() : kerl(kerl);
    }

    @Override
    public KeyEvent_ getKeyEvent(EventCoords coordinates) {
        var event = getKerl().getKeyEvent(EventCoordinates.from(coordinates));
        return event == null ? KeyEvent_.getDefaultInstance() : event.toKeyEvent_();
    }

    @Override
    public KeyState_ getKeyState(EventCoords coordinates) {
        KeyState ks = getKerl().getKeyState(EventCoordinates.from(coordinates));
        return ks == null ? KeyState_.getDefaultInstance() : ks.toKeyState_();
    }

    @Override
    public KeyState_ getKeyState(Ident identifier, ULong sequenceNumber) {
        KeyState ks = getKerl().getKeyState(Identifier.from(identifier), sequenceNumber);
        return ks == null ? KeyState_.getDefaultInstance() : ks.toKeyState_();
    }

    @Override
    public KeyState_ getKeyState(Ident identifier) {
        KeyState ks = getKerl().getKeyState(Identifier.from(identifier));
        return ks == null ? KeyState_.getDefaultInstance() : ks.toKeyState_();
    }

    @Override
    public KeyState_ getKeyStateSeqNum(IdentAndSeq request) {
        return getKeyState(request.getIdentifier(), ULong.valueOf(request.getSequenceNumber()));
    }

    @Override
    public KeyStateWithAttachments_ getKeyStateWithAttachments(EventCoords coords) {
        KEL.KeyStateWithAttachments ksa = getKerl().getKeyStateWithAttachments(EventCoordinates.from(coords));
        return ksa == null ? KeyStateWithAttachments_.getDefaultInstance() : ksa.toEvente();
    }

    @Override
    public KeyStateWithEndorsementsAndValidations_ getKeyStateWithEndorsementsAndValidations(EventCoords coordinates) {
        KeyStateWithEndorsementsAndValidations ks = getKerl().getKeyStateWithEndorsementsAndValidations(
        EventCoordinates.from(coordinates));
        return ks == null ? KeyStateWithEndorsementsAndValidations_.getDefaultInstance() : ks.toKS();
    }

    @Override
    public Validations getValidations(EventCoords coords) {
        Map<EventCoordinates, JohnHancock> vs = getKerl().getValidations(EventCoordinates.from(coords));
        return Validations.newBuilder()
                          .addAllValidations(vs.entrySet()
                                               .stream()
                                               .map(e -> Validation_.newBuilder()
                                                                    .setValidator(e.getKey().toEventCoords())
                                                                    .setSignature(e.getValue().toSig())
                                                                    .build())
                                               .toList())
                          .build();
    }

    private KERL.AppendKERL getKerl() {
        return kerl.get();
    }

    private KERL_ kerl(List<EventWithAttachments> k) {
        var builder = KERL_.newBuilder();
        k.forEach(ewa -> builder.addEvents(ewa.toKeyEvente()));
        return builder.build();
    }
}
