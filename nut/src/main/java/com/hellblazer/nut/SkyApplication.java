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

package com.hellblazer.nut;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.macasaet.fernet.StringValidator;
import com.macasaet.fernet.Token;
import com.salesforce.apollo.archipelago.*;
import com.salesforce.apollo.archipelago.client.FernetCallCredentials;
import com.salesforce.apollo.archipelago.server.FernetServerInterceptor;
import com.salesforce.apollo.choam.Parameters;
import com.salesforce.apollo.comm.grpc.ClientContextSupplier;
import com.salesforce.apollo.comm.grpc.ServerContextSupplier;
import com.salesforce.apollo.context.DynamicContext;
import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.cryptography.SignatureAlgorithm;
import com.salesforce.apollo.cryptography.cert.CertificateWithPrivateKey;
import com.salesforce.apollo.cryptography.ssl.CertificateValidator;
import com.salesforce.apollo.fireflies.View;
import com.salesforce.apollo.gorgoneion.Gorgoneion;
import com.salesforce.apollo.gorgoneion.client.GorgoneionClient;
import com.salesforce.apollo.gorgoneion.client.client.comm.Admissions;
import com.salesforce.apollo.gorgoneion.proto.SignedAttestation;
import com.salesforce.apollo.gorgoneion.proto.SignedNonce;
import com.salesforce.apollo.membership.Member;
import com.salesforce.apollo.stereotomy.Stereotomy;
import com.salesforce.apollo.stereotomy.StereotomyValidator;
import com.salesforce.apollo.stereotomy.event.proto.Validations;
import com.salesforce.apollo.stereotomy.identifier.SelfAddressingIdentifier;
import com.salesforce.apollo.stereotomy.services.proto.ProtoKERLAdapter;
import com.salesforce.apollo.test.proto.ByteMessage;
import com.salesforce.apollo.thoth.DirectPublisher;
import io.grpc.ManagedChannel;
import io.grpc.NameResolver;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessSocketAddress;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.Provider;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author hal.hildebrand
 **/
public class SkyApplication {
    private static final Logger log = LoggerFactory.getLogger(SkyApplication.class);

    private final    Digest                        contextId;
    private final    Sky                           node;
    private final    Router                        clusterComms;
    private final    CertificateWithPrivateKey     certWithKey;
    private final    SanctumSanctorum              sanctorum;
    private final    Router                        admissionsComms;
    private final    Clock                         clock;
    private final    AtomicBoolean                 started   = new AtomicBoolean();
    private final    DelegatedCertificateValidator certificateValidator;
    private final    Lock                          tokenLock = new ReentrantLock();
    private volatile Token                         token;
    private volatile ManagedChannel                joinChannel;
    private          int                           retries   = 5;

    public SkyApplication(SkyConfiguration configuration, SanctumSanctorum sanctorum,
                          CompletableFuture<Void> onFailure) {
        this.clock = Clock.systemUTC();
        this.sanctorum = sanctorum;
        certWithKey = sanctorum.member()
                               .getCertificateWithPrivateKey(Instant.now(), Duration.ofHours(1),
                                                             SignatureAlgorithm.DEFAULT);
        var clusterEndpoint = configuration.clusterEndpoint.socketAddress();
        var local = clusterEndpoint instanceof InProcessSocketAddress;
        certificateValidator = new DelegatedCertificateValidator(CertificateValidator.NONE);

        final RouterSupplier clusterServer;
        if (local) {
            clusterServer = new LocalServer(((InProcessSocketAddress) clusterEndpoint).getName(), sanctorum.member());
        } else {
            Function<Member, SocketAddress> resolver = m -> ((View.Participant) m).endpoint();
            EndpointProvider ep = new StandardEpProvider(clusterEndpoint, ClientAuth.REQUIRE, certificateValidator,
                                                         resolver);
            clusterServer = new MtlsServer(sanctorum.member(), ep, clientContextSupplier(),
                                           serverContextSupplier(certWithKey));
        }
        Predicate<FernetServerInterceptor.HashedToken> validator = token -> {
            var current = sanctorum;
            if (current == null) {
                return false;
            }
            var generator = current.getGenerator();
            var result = generator == null ? null : generator.validate(token, new StringValidator() {
                @Override
                public TemporalAmount getTimeToLive() {
                    return Duration.ofDays(60);
                }
            });
            return result != null;
        };
        var credentials = FernetCallCredentials.blocking(() -> {
            var current = sanctorum;
            if (current == null) {
                return null;
            }
            var generator = current.getGenerator();
            return generator == null ? null : generate(generator);
        });
        clusterComms = clusterServer.router(configuration.connectionCache.setCredentials(credentials),
                                            RouterImpl::defaultServerLimit, null,
                                            Collections.singletonList(new FernetServerInterceptor()), validator);
        log.info("Cluster communications: {} on: {}", clusterEndpoint, sanctorum.getId());
        var runtime = Parameters.RuntimeParameters.newBuilder()
                                                  .setOnFailure(onFailure)
                                                  .setCommunications(clusterComms)
                                                  .setContext(configuration.context.setId(configuration.group).build());
        ((DynamicContext<Member>) runtime.getContext()).activate(sanctorum.member());
        var bind = local ? new InetSocketAddress(0) : (InetSocketAddress) clusterEndpoint;
        var choamParameters = configuration.choamParameters;
        choamParameters.setProducer(configuration.producerParameters.build());
        choamParameters.setGenesisViewId(configuration.genesisViewId);
        choamParameters.setViewSigAlgorithm(configuration.identity.signatureAlgorithm());
        choamParameters.setDigestAlgorithm(configuration.identity.digestAlgorithm());
        node = new Sky(configuration.group, sanctorum.member(), configuration.domain, choamParameters, runtime, bind,
                       configuration.viewParameters, null);
        var k = node.getDht().asKERL();

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        var gorgoneionParameters = configuration.gorgoneionParameters.setKerl(k);
        var approachEndpoint = configuration.approachEndpoint.socketAddress();

        RouterSupplier approachServer;
        if (local) {
            approachServer = new LocalServer(((InProcessSocketAddress) approachEndpoint).getName(), sanctorum.member());
        } else {
            Function<Member, SocketAddress> resolver = m -> ((View.Participant) m).endpoint();
            EndpointProvider ep = new StandardEpProvider(approachEndpoint, ClientAuth.OPTIONAL,
                                                         CertificateValidator.NONE, resolver);
            approachServer = new MtlsServer(sanctorum.member(), ep, clientContextSupplier(),
                                            serverContextSupplier(certWithKey));
        }
        log.info("Approach communications: {} on: {}", approachEndpoint, sanctorum.getId());

        admissionsComms = approachServer.router();
        new Gorgoneion(configuration.approaches.isEmpty(), this::attest, gorgoneionParameters.build(),
                       sanctorum.member(), runtime.getContext(),
                       new DirectPublisher(sanctorum.member().getId(), new ProtoKERLAdapter(k)), admissionsComms, null,
                       clusterComms);
        contextId = runtime.getContext().getId();
    }

    public SkyApplication(SkyConfiguration configuration, SanctumSanctorum sanctum) {
        this(configuration, sanctum, new CompletableFuture<>());
    }

    public boolean active() {
        return node.active();
    }

    public String logState() {
        return node.logState();
    }

    public void setCertificateValidatorAni() {
        certificateValidator.setDelegate(
        new StereotomyValidator(node.getDht().getAni().verifiers(Duration.ofSeconds(30))));
    }

    public void setCertificateValidatorNONE() {
        certificateValidator.setDelegate(CertificateValidator.NONE);
    }

    public void shutdown() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        log.info("Shutting down on: {}", node.getMember().getId());
        token = null;
        if (joinChannel != null) {
            joinChannel.shutdown();
        }
        if (node != null) {
            node.stop();
        }
        if (clusterComms != null) {
            clusterComms.close(Duration.ofMinutes(1));
        }
        if (admissionsComms != null) {
            admissionsComms.close(Duration.ofMinutes(1));
        }
    }

    public void start(Duration viewGossipDuration, List<View.Seed> seeds, CompletableFuture<Void> onStart) {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        clusterComms.start();
        admissionsComms.start();
        node.setDhtVerifiers();
        node.start();
        node.getFoundation().start(onStart, viewGossipDuration, seeds);
        log.info("Started Sky: {}", sanctorum.getId());
    }

    void bootstrap(Duration viewGossipDuration, CompletableFuture<Void> onStart, SocketAddress myApproach) {
        log.info("Bootstrapping on: {}", sanctorum.getId());
        start(viewGossipDuration, Collections.emptyList(), onStart);
        join(Collections.singletonList(myApproach));
    }

    void testify(Duration viewGossipDuration, List<SocketAddress> approaches, CompletableFuture<Void> onStart,
                 List<View.Seed> seeds) {
        start(viewGossipDuration, seeds, onStart);
        join(approaches);
    }

    private Any attest(SignedNonce signedNonce) {
        return Any.getDefaultInstance();
    }

    private boolean attest(SignedAttestation signedAttestation) {
        return true;
    }

    private Function<Member, ClientContextSupplier> clientContextSupplier() {
        return m -> (ClientContextSupplier) (clientAuth, alias, validator, tlsVersion) -> MtlsServer.forClient(
        clientAuth, alias, certWithKey.getX509Certificate(), certWithKey.getPrivateKey(), validator);
    }

    private void enable() {

    }

    private ManagedChannel forApproaches(List<SocketAddress> approaches) {
        var local = !approaches.isEmpty() && approaches.getFirst() instanceof InProcessSocketAddress;
        NameResolver.Factory factory = new SimpleNameResolverFactory(approaches);
        if (local) {
            return InProcessChannelBuilder.forTarget("approach")
                                          .nameResolverFactory(factory)
                                          .intercept(RouterImpl.clientInterceptor(contextId))
                                          .defaultLoadBalancingPolicy("round_robin")
                                          .usePlaintext()
                                          .build();
        } else {
            MtlsClient client = new MtlsClient(factory, ClientAuth.REQUIRE, "foo", certWithKey.getX509Certificate(),
                                               certWithKey.getPrivateKey(), CertificateValidator.NONE, contextId);
            return client.getChannel();
        }
    }

    private Token generate(TokenGenerator generator) {
        tokenLock.lock();
        try {
            var current = token;
            if (current == null && started.get()) {
                var msg = ByteMessage.newBuilder().setContents(ByteString.copyFromUtf8("My test message")).build();
                token = generator.apply(msg);
                log.info("Generating recognition token on context: {} on: {}", contextId, sanctorum.getId());
                return token;
            }
            return current;
        } finally {
            tokenLock.unlock();
        }
    }

    private void join(List<SocketAddress> approaches) {
        int attempt = retries;
        while (started.get() && attempt > 0) {
            log.info("Attesting identity, attempt: {}, approaches: {} on: {}", retries - attempt, approaches,
                     sanctorum.getId());
            attempt--;
            joinChannel = forApproaches(approaches);
            try {
                Admissions admissions = new AdmissionsClient(sanctorum.member(), joinChannel, null);
                var client = new GorgoneionClient(sanctorum.member(), sn -> attest(sn), clock, admissions);

                final var invitation = client.apply(Duration.ofSeconds(120));
                assert invitation != null : "NULL invitation";
                assert !Validations.getDefaultInstance().equals(invitation) : "Empty invitation";
                assert invitation.getValidationsCount() > 0 : "No validations";
                log.info("Successful application on: {}", sanctorum.getId());
                break;
            } catch (StatusRuntimeException e) {
                log.error("Error during application: {} on: {}", e.getMessage(), sanctorum.getId());
                try {
                    Thread.sleep(Duration.ofMillis(500));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } finally {
                var jc = joinChannel;
                joinChannel = null;
                if (jc != null) {
                    jc.shutdown();
                }
            }
        }

    }

    private ServerContextSupplier serverContextSupplier(CertificateWithPrivateKey certWithKey) {
        return new ServerContextSupplier() {
            @Override
            public SslContext forServer(ClientAuth clientAuth, String alias, CertificateValidator validator,
                                        Provider provider) {
                return MtlsServer.forServer(clientAuth, alias, certWithKey.getX509Certificate(),
                                            certWithKey.getPrivateKey(), validator);
            }

            @Override
            public Digest getMemberId(X509Certificate key) {
                var decoded = Stereotomy.decode(key);
                if (decoded.isEmpty()) {
                    throw new NoSuchElementException(
                    "Cannot decode certificate: %s on: %s".formatted(key, node.getMember().getId()));
                }
                return ((SelfAddressingIdentifier) decoded.get().identifier()).getDigest();
            }
        };
    }
}
