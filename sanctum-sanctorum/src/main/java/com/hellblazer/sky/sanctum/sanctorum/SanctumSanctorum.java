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

import com.codahale.shamir.Scheme;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hellblazer.delos.cryptography.Digest;
import com.hellblazer.delos.cryptography.DigestAlgorithm;
import com.hellblazer.delos.cryptography.EncryptionAlgorithm;
import com.hellblazer.delos.cryptography.JohnHancock;
import com.hellblazer.delos.cryptography.proto.Digeste;
import com.hellblazer.delos.cryptography.proto.Sig;
import com.hellblazer.delos.gorgoneion.proto.Credentials;
import com.hellblazer.delos.gorgoneion.proto.PublicKey_;
import com.hellblazer.delos.gorgoneion.proto.SignedNonce;
import com.hellblazer.delos.stereotomy.ControlledIdentifier;
import com.hellblazer.delos.stereotomy.KERL;
import com.hellblazer.delos.stereotomy.Stereotomy;
import com.hellblazer.delos.stereotomy.StereotomyImpl;
import com.hellblazer.delos.stereotomy.caching.CachingKERL;
import com.hellblazer.delos.stereotomy.db.UniKERLDirect;
import com.hellblazer.delos.stereotomy.event.protobuf.ProtobufEventFactory;
import com.hellblazer.delos.stereotomy.identifier.SelfAddressingIdentifier;
import com.hellblazer.delos.stereotomy.mem.MemKeyStore;
import com.hellblazer.delos.utils.Entropy;
import com.hellblazer.sanctorum.internal.v1.proto.*;
import com.jauntsdn.netty.channel.vsock.ServerVSockChannel;
import com.macasaet.fernet.Token;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.inprocess.InProcessSocketAddress;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.VSockAddress;
import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.database.core.H2Database;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.ui.ConsoleUIService;
import liquibase.ui.UIService;
import org.h2.jdbc.JdbcConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.PrintStream;
import java.net.SocketAddress;
import java.security.Key;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.hellblazer.delos.cryptography.QualifiedBase64.qb64;
import static com.hellblazer.sky.constants.Constants.SHAMIR_TAG;

/**
 * encapsulation of ye olde thyme secrets n' associated sensitives
 *
 * @author hal.hildebrand
 **/
public class SanctumSanctorum {
    public static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    public static final String AES                = "AES";
    public static final int    TAG_LENGTH         = 128; // bits
    public static final int    IV_LENGTH          = 16; // bytes
    public static final String SANCTUM_DB_URL     = "jdbc:h2:mem:sanctum;DB_CLOSE_DELAY=-1";

    private static final Logger log = LoggerFactory.getLogger(SanctumSanctorum.class);

    private final SanctumSanctorum.Service                       service;
    private final Server                                         server;
    private final Parameters                                     parameters;
    private final Function<SignedNonce, Any>                     attestation;
    private final AtomicReference<SignedNonce>                   currentAttestation = new AtomicReference<>();
    private final Digest                                         id;
    private final ControlledIdentifier<SelfAddressingIdentifier> member;
    private final Stereotomy                                     stereotomy;
    private final KERL.AppendKERL                                kerl;
    private final KeyPair                                        sessionKeyPair;

    private volatile TokenGenerator generator;
    private volatile Key            master;

    public SanctumSanctorum(Parameters parameters, Function<SignedNonce, Any> attestation) {
        this(parameters, attestation, processBuilderFor(parameters.enclaveAddress), new SecureRandom());
    }

    public SanctumSanctorum(Parameters parameters, Function<SignedNonce, Any> attestation, ServerBuilder builder,
                            SecureRandom entropy) {
        this.parameters = parameters;
        initializeSchema();
        kerl = initializeKerl();
        stereotomy = new StereotomyImpl(new MemKeyStore(), new CachingKERL(f -> f.apply(kerl)), entropy);
        member = stereotomy.newIdentifier();
        log.info("New identifier: {}", member.getIdentifier().getDigest());
        this.id = member.getIdentifier().getDigest();
        service = new SanctumSanctorum.Service();
        this.attestation = attestation;
        server = builder.addService(new EnclaveServer(service))
                        .addService(new EnclaveKERLServer(new ProtoKERLReadAdapter(() -> kerl), null))
                        .build();
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
        sessionKeyPair = parameters.encryptionAlgorithm.generateKeyPair();
        if (parameters.devSecret != null) {
            unwrap(parameters.devSecret);
        }
    }

    private static ServerBuilder<?> processBuilderFor(SocketAddress enclaveAddress) {
        return switch (enclaveAddress) {
            case VSockAddress vs -> NettyServerBuilder.forAddress(vs)
                                                      .withOption(ChannelOption.SO_REUSEADDR, true)
                                                      .channelType(ServerVSockChannel.class)
                                                      .workerEventLoopGroup(new EpollEventLoopGroup())
                                                      .withChildOption(ChannelOption.TCP_NODELAY, true);
            case InProcessSocketAddress vs -> InProcessServerBuilder.forAddress(vs);
            default -> throw new IllegalArgumentException("Unsupported enclave address: " + enclaveAddress);
        };
    }

    public static SanctumSanctorum.Encrypted encrypt(byte[] plaintext, SecretKey secretKey, byte[] associatedData) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            Entropy.nextSecureBytes(iv);
            final Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv); //128 bit auth associatedData length
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            if (associatedData != null) {
                cipher.updateAAD(associatedData);
            }

            return new SanctumSanctorum.Encrypted(cipher.doFinal(plaintext), iv, associatedData);
        } catch (Throwable t) {
            throw new IllegalStateException("Unable to encrypt", t);
        }
    }

    public static byte[] decrypt(SanctumSanctorum.Encrypted encrypted, SecretKey secretKey) {
        try {
            final Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            AlgorithmParameterSpec gcmIv = new GCMParameterSpec(TAG_LENGTH, encrypted.iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmIv);

            if (encrypted.associatedData != null) {
                cipher.updateAAD(encrypted.associatedData);
            }
            return cipher.doFinal(encrypted.cipherText);
        } catch (Throwable t) {
            throw new IllegalStateException("Unable to decrypt", t);
        }
    }

    public void clear() {
        master = null;
        generator.clear();
    }

    public TokenGenerator getGenerator() {
        return generator;
    }

    public Digest getId() {
        return id;
    }

    public KERL getKERL() {
        return kerl;
    }

    public ControlledIdentifier<SelfAddressingIdentifier> member() {
        return member;
    }

    public void shutdown() {
        server.shutdown();
        try {
            server.awaitTermination();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void start() throws IOException {
        server.start();
    }

    private FernetToken generateToken(Bytes request) {
        return FernetToken.newBuilder().setToken(generator.apply(request).serialise()).build();
    }

    private JdbcConnection getConnection() throws SQLException {
        return new JdbcConnection(SANCTUM_DB_URL, new Properties(), "", "", false);
    }

    private Digeste identifier() {
        var id = getId();
        if (id == null) {
            log.error("No identifier provided");
            throw new StatusRuntimeException(io.grpc.Status.FAILED_PRECONDITION);
        }
        log.debug("Identifier requested on: {}", getId());
        return id.toDigeste();
    }

    private UniKERLDirect initializeKerl() {
        Connection connection = null;
        try {
            connection = new JdbcConnection(SANCTUM_DB_URL, new Properties(), "", "", false);
        } catch (SQLException e) {
            log.error("Unable to create JDBC connection: {}", SANCTUM_DB_URL);
        }
        return new UniKERLDirect(connection, parameters.algorithm);
    }

    private void initializeSchema() {
        ConsoleUIService service = (ConsoleUIService) Scope.getCurrentScope().get(Scope.Attr.ui, UIService.class);
        service.setOutputStream(new PrintStream(
        new LoggingOutputStream(LoggerFactory.getLogger("liquibase"), LoggingOutputStream.LogLevel.INFO)));
        var database = new H2Database();
        try (var connection = getConnection()) {
            database.setConnection(new liquibase.database.jvm.JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("stereotomy/initialize.xml", new ClassLoaderResourceAccessor(),
                                                     database)) {
                liquibase.update((String) null);
            } catch (LiquibaseException e) {
                throw new IllegalStateException(e);
            }
        } catch (SQLException e1) {
            throw new IllegalStateException(e1);
        }
    }

    private void provision(Provisioning_ request) {
        var signedNonce = currentAttestation.get();
        log.info("Provisioning with nonce: {} on: {}", signedNonce, getId());
        byte[] decrypted;
        try {
            var secretKey = parameters.encryptionAlgorithm.decapsulate(sessionKeyPair.getPrivate(),
                                                                       request.getEncapsulation().toByteArray(), AES);
            var encrypted = new SanctumSanctorum.Encrypted(request.getProvisioned().toByteArray(),
                                                           request.getIv().toByteArray(), signedNonce.toByteArray());
            decrypted = decrypt(encrypted, secretKey);
        } catch (Throwable t) {
            log.warn("Cannot decrypt share", t);
            return;
        }
        provision(ByteString.copyFrom(decrypted).toByteArray());
    }

    private void provision(byte[] master) {
        this.master = new SecretKeySpec(master, "AES");
        var keyLength = this.master.getEncoded().length;
        if (keyLength != 32) {
            throw new IllegalStateException("Master key must be 32 bytes, got " + keyLength);
        }
        generator = new TokenGenerator(this.master, new SecureRandom());
        log.info("Sanctum Sanctorum provisioned: {}", qb64(id));
    }

    private Provisioning_ provisioning(Credentials request) {
        if (master == null) {
            log.warn("Master key not provisioned");
            throw new StatusRuntimeException(io.grpc.Status.FAILED_PRECONDITION.withDescription("Master key not provisioned"));
        }
        if (!currentAttestation.compareAndSet(null, request.getNonce())) {
            return Provisioning_.getDefaultInstance();
        }
        var publicKey = EncryptionAlgorithm.lookup(request.getSessionKey().getAlgorithmValue())
                                           .publicKey(request.getSessionKey().getPublicKey().toByteArray());
        var encapsulated = parameters.encryptionAlgorithm.encapsulated(publicKey);
        var secretKey = new SecretKeySpec(encapsulated.key().getEncoded(), AES);
        var encrypted = SanctumSanctorum.encrypt(master.getEncoded(), secretKey, request.getNonce().toByteArray());
        return Provisioning_.newBuilder()
                            .setIv(ByteString.copyFrom(encrypted.iv()))
                            .setProvisioned(ByteString.copyFrom(encrypted.cipherText()))
                            .setEncapsulation(ByteString.copyFrom(encapsulated.encapsulation()))
                            .build();
    }

    private Status seal() {
        var id = getId();
        clear();
        service.shares.clear();
        log.info("Service has been sealed on: {}", id);
        return Status.newBuilder().setSuccess(true).build();
    }

    private Sig sign(Payload_ request) {
        return member.getSigner().sign(request.getPayload()).toSig();
    }

    private UnwrapStatus unwrap(Scheme scheme, HashMap<Integer, byte[]> clone, UnwrapStatus.Builder status) {
        unwrap(scheme.join(clone));
        var identifier = getId();
        return status.setIdentifier(identifier.toDigeste()).build();
    }

    private void unwrap(byte[] root) {
        this.master = new SecretKeySpec(parameters.algorithm.digest(root).getBytes(), "AES");
        var keyLength = master.getEncoded().length;
        if (keyLength != 32) {
            throw new IllegalStateException("Master key must be 32 bytes, got " + keyLength);
        }
        generator = new TokenGenerator(master, new SecureRandom());
        log.info("Sanctum Sanctorum unwrapped: {}", qb64(id));
    }

    private Bytes validate(FernetValidate request) {
        var hashed = new TokenGenerator.HashedToken(kerl.getDigestAlgorithm().digest(request.getTokenBytes()),
                                                    Token.fromString(request.getToken()));
        return generator.validate(() -> b -> Bytes.newBuilder().setB(ByteString.copyFrom(b)).build(), hashed);
    }

    private Verified_ verify(Payload_ request) {
        var from = JohnHancock.from(request.getSignature());
        var payload = request.getPayload();
        var verifier = member.getVerifier();
        var threshold = ProtobufEventFactory.toSigningThreshold(request.getThreshold());
        var verified = verifier.isPresent() && verifier.get().verify(threshold, from, payload);
        return Verified_.newBuilder().setVerified(verified).build();
    }

    private Verified_ verifyToken(FernetToken request) {
        if (generator.valid(new TokenGenerator.HashedToken(kerl.getDigestAlgorithm().digest(request.getTokenBytes()),
                                                           Token.fromString(request.getToken())))) {
            return Verified_.newBuilder().setVerified(true).build();
        }
        return Verified_.newBuilder().setVerified(false).build();
    }

    public record Parameters(SanctumSanctorum.Shamir shamir, DigestAlgorithm algorithm,
                             EncryptionAlgorithm encryptionAlgorithm, byte[] nonce, SocketAddress enclaveAddress,
                             byte[] devSecret) {
    }

    public record Shamir(int shares, int threshold) {
    }

    public record Encrypted(byte[] cipherText, byte[] iv, byte[] associatedData) {
    }

    public class Service {

        private final Map<Integer, byte[]> shares = new ConcurrentHashMap<>();

        public Status apply(EncryptedShare eShare) {
            log.info("Applying encrypted share");
            byte[] decrypted;
            try {
                var secretKey = parameters.encryptionAlgorithm.decapsulate(sessionKeyPair.getPrivate(),
                                                                           eShare.getEncapsulation().toByteArray(),
                                                                           AES);
                var encrypted = new SanctumSanctorum.Encrypted(eShare.getShare().toByteArray(),
                                                               eShare.getIv().toByteArray(), SHAMIR_TAG);
                decrypted = decrypt(encrypted, secretKey);
            } catch (Throwable t) {
                log.warn("Cannot decrypt share", t);
                return Status.newBuilder().setSuccess(false).build();
            }
            try {
                var share = Share.parseFrom(decrypted);
                shares.put(share.getKey(), share.getShare().toByteArray());
                log.warn("Share applied: {}", share.getKey());
                return Status.newBuilder().setShares(shares.size()).setSuccess(true).build();
            } catch (InvalidProtocolBufferException e) {
                log.info("Not a valid share: {}", e.toString());
                return Status.newBuilder().setShares(shares.size()).setSuccess(false).build();
            }
        }

        public Any attestation(SignedNonce request) {
            return attestation.apply(request);
        }

        public FernetToken generateToken(Bytes request) {
            return SanctumSanctorum.this.generateToken(request);
        }

        public Digeste identifier() {
            return SanctumSanctorum.this.identifier();
        }

        public void provision(Provisioning_ request) {
            SanctumSanctorum.this.provision(request);
        }

        public Provisioning_ provisioning(Credentials request) {
            return SanctumSanctorum.this.provisioning(request);
        }

        public Status seal() {
            return SanctumSanctorum.this.seal();
        }

        public PublicKey_ sessionKey() {
            if (sessionKeyPair == null) {
                log.info("Session key pair not available");
                throw new StatusRuntimeException(io.grpc.Status.FAILED_PRECONDITION);
            }
            log.info("Requesting session key pair");
            var alg = parameters.encryptionAlgorithm;
            return PublicKey_.newBuilder()
                             .setAlgorithm(PublicKey_.algo.forNumber(alg.getCode()))
                             .setPublicKey(ByteString.copyFrom(alg.encode(sessionKeyPair.getPublic())))
                             .build();
        }

        public Sig sign(Payload_ request) {
            return SanctumSanctorum.this.sign(request);
        }

        public Status unseal() {
            log.info("Unsealing service");
            return Status.newBuilder().setSuccess(true).setShares(0).build();
        }

        public UnwrapStatus unwrap() {
            if (shares.size() < parameters.shamir.threshold()) {
                log.info("Cannot unwrap with: {} shares configured: {} out of {}", shares.size(),
                         parameters.shamir.threshold(), parameters.shamir.shares());
                return UnwrapStatus.newBuilder()
                                   .setSuccess(false)
                                   .setShares(shares.size())
                                   .setMessage(
                                   "Cannot unwrap with: %s shares configured: %s out of %s".formatted(shares.size(),
                                                                                                      parameters.shamir.threshold(),
                                                                                                      parameters.shamir.shares()))
                                   .build();
            }
            log.info("Unwrapping service with: {} shares configured: {} out of {}", shares.size(),
                     parameters.shamir.threshold(), parameters.shamir.shares());
            var scheme = new Scheme(new SecureRandom(), parameters.shamir.shares(), parameters.shamir.threshold());
            var status = UnwrapStatus.newBuilder().setShares(shares.size()).setSuccess(true);
            var clone = new HashMap<>(shares);
            shares.clear();
            return SanctumSanctorum.this.unwrap(scheme, clone, status);
        }

        public Bytes validate(FernetValidate request) {
            return SanctumSanctorum.this.validate(request);
        }

        public Verified_ verify(Payload_ request) {
            return SanctumSanctorum.this.verify(request);
        }

        public Verified_ verifyToken(FernetToken request) {
            return SanctumSanctorum.this.verifyToken(request);
        }
    }
}
