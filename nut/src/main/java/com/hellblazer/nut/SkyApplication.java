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
import com.salesforce.apollo.archipelago.*;
import com.salesforce.apollo.choam.Parameters;
import com.salesforce.apollo.comm.grpc.ClientContextSupplier;
import com.salesforce.apollo.comm.grpc.ServerContextSupplier;
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
import com.salesforce.apollo.thoth.DirectPublisher;
import io.grpc.ManagedChannel;
import io.grpc.NameResolver;
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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author hal.hildebrand
 **/
public class SkyApplication {
    private static final Logger log = LoggerFactory.getLogger(SkyApplication.class);

    private final    Digest                    contextId;
    private final    Sky                       node;
    private final    Router                    clusterComms;
    private final    Gorgoneion                gorgoneion;
    private final    CertificateWithPrivateKey certWithKey;
    private final    SanctumSanctorum          sanctorum;
    private final    Router                    admissionsComms;
    private final    Clock                     clock;
    private volatile ManagedChannel            joinChannel;

    public SkyApplication(SkyConfiguration configuration, SanctumSanctorum sanctorum) {
        this.clock = Clock.systemUTC();
        this.sanctorum = sanctorum;
        certWithKey = sanctorum.member()
                               .getCertificateWithPrivateKey(Instant.now(), Duration.ofHours(1),
                                                             SignatureAlgorithm.DEFAULT);
        var clusterEndpoint = configuration.clusterEndpoint.socketAddress();
        var local = clusterEndpoint instanceof InProcessSocketAddress;
        DelegatedCertificateValidator certValidator = new DelegatedCertificateValidator();

        final RouterSupplier clusterServer;
        if (local) {
            clusterServer = new LocalServer(((InProcessSocketAddress) clusterEndpoint).getName(), sanctorum.member());
        } else {
            Function<Member, SocketAddress> resolver = m -> ((View.Participant) m).endpoint();
            EndpointProvider ep = new StandardEpProvider(clusterEndpoint, ClientAuth.REQUIRE, certValidator, resolver);
            clusterServer = new MtlsServer(sanctorum.member(), ep, clientContextSupplier(),
                                           serverContextSupplier(certWithKey));
        }
        clusterComms = clusterServer.router(configuration.connectionCache);
        log.info("Cluster communications: {} on: {}", clusterEndpoint, sanctorum.getId());
        var runtime = Parameters.RuntimeParameters.newBuilder()
                                                  .setCommunications(clusterComms)
                                                  .setContext(configuration.context.build());
        runtime.getContext().activate(sanctorum.member());
        var bind = local ? new InetSocketAddress(0) : (InetSocketAddress) clusterEndpoint;
        node = new Sky(configuration.group, sanctorum.member(), configuration.domain, configuration.choamParameters,
                       runtime, bind, com.salesforce.apollo.fireflies.Parameters.newBuilder(), null);
        certValidator.setDelegate(new StereotomyValidator(node.getDht().getAni().verifiers(Duration.ofSeconds(30))));
        var k = node.getDht().asKERL();

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        var gorgoneionParameters = configuration.gorgoneionParameters.setKerl(k);
        Predicate<SignedAttestation> verifier = null;

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
        gorgoneion = new Gorgoneion(this::attest, gorgoneionParameters.build(), sanctorum.member(),
                                    runtime.getContext(), new DirectPublisher(new ProtoKERLAdapter(k)), admissionsComms,
                                    null, clusterComms);
        contextId = runtime.getContext().getId();
    }

    public void shutdown() {
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

    public void start() {
        clusterComms.start();
        admissionsComms.start();
        node.start();
        log.info("Started Sky on: {}", sanctorum.getId());
    }

    public void testify(List<SocketAddress> seeds) {
        if (seeds.isEmpty()) {
            log.info("Bootstrapping on: {}", sanctorum.getId());
            start();
        } else {
            join(seeds);
        }
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

    private ManagedChannel forSeeds(List<SocketAddress> seeds) {
        var local = seeds.isEmpty() ? false : seeds.getFirst() instanceof InProcessSocketAddress;
        NameResolver.Factory factory = new SimpleNameResolverFactory(seeds);
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

    private void join(List<SocketAddress> seeds) {
        log.info("Attesting identity, seeds: {} on: {}", seeds, sanctorum.getId());
        joinChannel = forSeeds(seeds);
        Admissions admissions = new AdmissionsClient(sanctorum.member(), joinChannel, null);
        var client = new GorgoneionClient(sanctorum.member(), sn -> attest(sn), clock, admissions);

        final var invitation = client.apply(Duration.ofSeconds(120));
        assert invitation != null : "NULL invitation";
        assert !Validations.getDefaultInstance().equals(invitation) : "Empty invitation";
        assert invitation.getValidationsCount() > 0 : "No validations";
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
                return ((SelfAddressingIdentifier) decoded.get().coordinates().getIdentifier()).getDigest();
            }
        };
    }
}
