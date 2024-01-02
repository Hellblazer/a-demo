/*
 * Copyright (c) 2023 Hal Hildebrand. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.hellblazer.nut.service;

import com.hellblazer.nut.Sky;
import com.salesforce.apollo.archipelago.EndpointProvider;
import com.salesforce.apollo.archipelago.MtlsServer;
import com.salesforce.apollo.archipelago.Router;
import com.salesforce.apollo.archipelago.StandardEpProvider;
import com.salesforce.apollo.choam.Parameters;
import com.salesforce.apollo.comm.grpc.ClientContextSupplier;
import com.salesforce.apollo.comm.grpc.ServerContextSupplier;
import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.cryptography.DigestAlgorithm;
import com.salesforce.apollo.cryptography.SignatureAlgorithm;
import com.salesforce.apollo.cryptography.cert.CertificateWithPrivateKey;
import com.salesforce.apollo.cryptography.ssl.CertificateValidator;
import com.salesforce.apollo.fireflies.View;
import com.salesforce.apollo.gorgoneion.Gorgoneion;
import com.salesforce.apollo.gorgoneion.proto.SignedAttestation;
import com.salesforce.apollo.membership.Member;
import com.salesforce.apollo.membership.stereotomy.ControlledIdentifierMember;
import com.salesforce.apollo.stereotomy.Stereotomy;
import com.salesforce.apollo.stereotomy.StereotomyImpl;
import com.salesforce.apollo.stereotomy.StereotomyValidator;
import com.salesforce.apollo.stereotomy.identifier.SelfAddressingIdentifier;
import com.salesforce.apollo.stereotomy.mem.MemKERL;
import com.salesforce.apollo.stereotomy.mem.MemKeyStore;
import com.salesforce.apollo.stereotomy.services.proto.ProtoKERLAdapter;
import com.salesforce.apollo.thoth.DirectPublisher;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Environment;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import java.net.SocketAddress;
import java.security.Principal;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static com.salesforce.apollo.cryptography.QualifiedBase64.digest;

/**
 * @author hal.hildebrand
 */
public class SkyApplication extends Application<SkyConfiguration> {

    private final Stereotomy                 stereotomy;
    private final ControlledIdentifierMember member;
    private       Sky                        node;
    private       MtlsServer                 clusterServer;
    private       Router                     clusterComms;
    private       Gorgoneion                 gorgoneion;
    private       CertificateWithPrivateKey  certWithKey;

    public SkyApplication() {
        stereotomy = new StereotomyImpl(new MemKeyStore(), new MemKERL(DigestAlgorithm.DEFAULT), new SecureRandom());
        member = new ControlledIdentifierMember(stereotomy.newIdentifier());
    }

    @Override
    public void run(SkyConfiguration configuration, Environment environment) throws Exception {
        certWithKey = member.getCertificateWithPrivateKey(Instant.now(), Duration.ofHours(1),
                                                          SignatureAlgorithm.DEFAULT);
        Function<Member, SocketAddress> resolver = m -> ((View.Participant) m).endpoint();
        var certValidator = new DelegatedCertificateValidator();
        EndpointProvider ep = new StandardEpProvider(configuration.clusterEndpoint, ClientAuth.REQUIRE, certValidator,
                                                     resolver);
        clusterServer = new MtlsServer(member, ep, clientContextSupplier(), serverContextSupplier(certWithKey));
        clusterComms = clusterServer.router(configuration.connectionCache);
        var runtime = Parameters.RuntimeParameters.newBuilder()
                                                  .setCommunications(clusterComms)
                                                  .setContext(configuration.context.build());
        node = new Sky(digest(configuration.group), member, configuration.processDomainParameters,
                       configuration.choamParameters, runtime, configuration.clusterEndpoint,
                       com.salesforce.apollo.fireflies.Parameters.newBuilder(), null);
        certValidator.setDelegate(new StereotomyValidator(node.getDht().getAni().verifiers(Duration.ofSeconds(30))));
        var k = node.getDht().asKERL();

        Function<String, TokenAuthenticator.Subject> validator = s -> null;
        environment.jersey()
                   .register(new AuthDynamicFeature(
                   new TokenAuthenticator.Builder<AuthenticatedSubject>().setValidator(validator)
                                                                         .setRealm("realm")
                                                                         .setPrefix("Bearer")
                                                                         .setAuthenticator(new SubjectAuthenticator())
                                                                         .buildAuthFilter()));

        environment.jersey().register(new AuthValueFactoryProvider.Binder<>(Principal.class));
        environment.jersey().register(RolesAllowedDynamicFeature.class);

        environment.jersey().register(new AdminResource(node.getDelphi(), Duration.ofSeconds(2)));
        environment.jersey().register(new StorageResource(node.getGeb()));
        environment.jersey().register(new AssertionResource(node.getDelphi(), Duration.ofSeconds(2)));

        environment.healthChecks().register("sky", new SkyHealthCheck());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown()));
        var gorgoneionParameters = configuration.gorgoneionParameters.setKerl(k).setVerifier(sa -> verify(sa));
        gorgoneion = new Gorgoneion(gorgoneionParameters.build(), member, runtime.getContext(),
                                    new DirectPublisher(new ProtoKERLAdapter(k)), clusterComms,
                                    Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory()), null,
                                    clusterComms);
        clusterComms.start();
        node.start();
    }

    private Function<Member, ClientContextSupplier> clientContextSupplier() {
        return m -> (ClientContextSupplier) (clientAuth, alias, validator, tlsVersion) -> MtlsServer.forClient(
        clientAuth, alias, certWithKey.getX509Certificate(), certWithKey.getPrivateKey(), validator);
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
                return ((SelfAddressingIdentifier) Stereotomy.decode(key)
                                                             .get()
                                                             .coordinates()
                                                             .getIdentifier()).getDigest();
            }
        };
    }

    private void shutdown() {
        if (node != null) {
            node.stop();
        }
        if (clusterComms != null) {
            clusterComms.close(Duration.ofMinutes(1));
        }
    }

    private boolean verify(SignedAttestation signedAttestation) {
        return true;
    }
}
