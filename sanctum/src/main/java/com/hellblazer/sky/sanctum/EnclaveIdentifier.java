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

import com.google.protobuf.Empty;
import com.hellblazer.delos.cryptography.*;
import com.hellblazer.delos.cryptography.cert.BcX500NameDnImpl;
import com.hellblazer.delos.cryptography.cert.CertExtension;
import com.hellblazer.delos.cryptography.cert.CertificateWithPrivateKey;
import com.hellblazer.delos.cryptography.cert.Certificates;
import com.hellblazer.delos.stereotomy.*;
import com.hellblazer.delos.stereotomy.event.AttachmentEvent;
import com.hellblazer.delos.stereotomy.event.DelegatedRotationEvent;
import com.hellblazer.delos.stereotomy.event.EstablishmentEvent;
import com.hellblazer.delos.stereotomy.event.InceptionEvent;
import com.hellblazer.delos.stereotomy.event.proto.KeyState_;
import com.hellblazer.delos.stereotomy.identifier.BasicIdentifier;
import com.hellblazer.delos.stereotomy.identifier.Identifier;
import com.hellblazer.delos.stereotomy.identifier.QualifiedBase64Identifier;
import com.hellblazer.delos.stereotomy.identifier.SelfAddressingIdentifier;
import com.hellblazer.delos.stereotomy.identifier.spec.IdentifierSpecification;
import com.hellblazer.delos.stereotomy.identifier.spec.InteractionSpecification;
import com.hellblazer.delos.stereotomy.identifier.spec.RotationSpecification;
import com.hellblazer.delos.stereotomy.services.grpc.kerl.CommonKERLClient;
import com.hellblazer.delos.stereotomy.services.grpc.kerl.KERLAdapter;
import com.hellblazer.delos.stereotomy.services.grpc.proto.KERLServiceGrpc;
import com.hellblazer.sanctorum.proto.Enclave_Grpc;
import io.grpc.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * "Most" of a ControlledIdentifier, backed by the SanctumService Enclave.  Basically a blocking key management enclave.
 * Provides enough to wrap using a SigningMember for Delos integration.
 *
 * @author hal.hildebrand
 **/
public class EnclaveIdentifier implements ControlledIdentifier<SelfAddressingIdentifier> {
    private final static Logger log = LoggerFactory.getLogger(EnclaveIdentifier.class);

    private final Enclave_Grpc.Enclave_BlockingStub client;
    private final SignatureAlgorithm                algorithm;
    private final SelfAddressingIdentifier          identifier;
    private final Signer                            signer;
    private final KERL                              kerl;
    private final KeyState                          state;

    public EnclaveIdentifier(SignatureAlgorithm algorithm, Channel channel) {
        this.client = Enclave_Grpc.newBlockingStub(channel);
        this.algorithm = algorithm;
        identifier = new SelfAddressingIdentifier(Digest.from(client.identifier(Empty.getDefaultInstance())));
        this.signer = new EnclaveSigner(channel, algorithm);
        var stub = KERLServiceGrpc.newBlockingStub(channel);
        kerl = new KERLAdapter(new CommonKERLClient(stub, null), DigestAlgorithm.DEFAULT);
        this.state = kerl.getKeyState(identifier);
    }

    @Override
    public SignatureAlgorithm algorithm() {
        return algorithm;
    }

    @Override
    public BoundIdentifier<SelfAddressingIdentifier> bind() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Void commit(DelegatedRotationEvent delegation, AttachmentEvent commitment) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<InceptionEvent.ConfigurationTrait> configurationTraits() {
        return state.configurationTraits();
    }

    @Override
    public DelegatedRotationEvent delegateRotate(RotationSpecification.Builder spec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getBytes() {
        return new byte[0];
    }

    @Override
    public EventCoordinates getCoordinates() {
        return state.getCoordinates();
    }

    @Override
    public Optional<Identifier> getDelegatingIdentifier() {
        return state.getDelegatingIdentifier();
    }

    @Override
    public Digest getDigest() {
        return identifier.getDigest();
    }

    @Override
    public SelfAddressingIdentifier getIdentifier() {
        return identifier;
    }

    @Override
    public List<KERL.EventWithAttachments> getKerl() {
        return kerl.kerl(this.getIdentifier());
    }

    @Override
    public List<PublicKey> getKeys() {
        return state.getKeys();
    }

    @Override
    public EstablishmentEvent getLastEstablishingEvent() {
        return (EstablishmentEvent) kerl.getKeyEvent(state.getLastEstablishmentEvent());
    }

    @Override
    public EventCoordinates getLastEstablishmentEvent() {
        return state.getLastEstablishmentEvent();
    }

    @Override
    public EventCoordinates getLastEvent() {
        return state.getLastEvent();
    }

    @Override
    public Optional<Digest> getNextKeyConfigurationDigest() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Signer getSigner() {
        return signer;
    }

    @Override
    public SigningThreshold getSigningThreshold() {
        return state.getSigningThreshold();
    }

    @Override
    public Optional<Verifier> getVerifier() {
        return Optional.of(new KerlVerifier(identifier, kerl));
    }

    @Override
    public int getWitnessThreshold() {
        return state.getWitnessThreshold();
    }

    @Override
    public List<BasicIdentifier> getWitnesses() {
        return state.getWitnesses();
    }

    @Override
    public Optional<KeyPair> newEphemeral() {
        IdentifierSpecification.Builder<BasicIdentifier> builder = IdentifierSpecification.newBuilder().setBasic();
        return Optional.ofNullable(builder.getSignatureAlgorithm().generateKeyPair());
    }

    @Override
    public <I extends Identifier> ControlledIdentifier<I> newIdentifier(IdentifierSpecification.Builder<I> newBuilder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CertificateWithPrivateKey provision(Instant validFrom, Duration valid, List<CertExtension> extensions,
                                               SignatureAlgorithm algo) {
        Signer signer = this.getSigner();
        KeyPair keyPair = algo.generateKeyPair();
        JohnHancock signature = signer.sign(QualifiedBase64Identifier.qb64(new BasicIdentifier(keyPair.getPublic())));
        String formatted = String.format("UID=%s, DC=%s", Base64.getUrlEncoder()
                                                                .withoutPadding()
                                                                .encodeToString(
                                                                state.getIdentifier().toIdent().toByteArray()),
                                         Base64.getUrlEncoder()
                                               .withoutPadding()
                                               .encodeToString(signature.toSig().toByteArray()));
        BcX500NameDnImpl dn = new BcX500NameDnImpl(formatted);
        return new CertificateWithPrivateKey(
        Certificates.selfSign(false, dn, keyPair, validFrom, validFrom.plus(valid), extensions), keyPair.getPrivate());
    }

    @Override
    public Void rotate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Void rotate(RotationSpecification.Builder spec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EventCoordinates seal(InteractionSpecification.Builder spec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public KeyState_ toKeyState_() {
        return state.toKeyState_();
    }
}
