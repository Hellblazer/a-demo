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
import com.salesforce.apollo.membership.Context;
import com.salesforce.apollo.membership.Member;
import com.salesforce.apollo.membership.stereotomy.ControlledIdentifierMember;
import com.salesforce.apollo.stereotomy.EventValidation;
import com.salesforce.apollo.stereotomy.Stereotomy;
import com.salesforce.apollo.stereotomy.StereotomyImpl;
import com.salesforce.apollo.stereotomy.identifier.SelfAddressingIdentifier;
import com.salesforce.apollo.stereotomy.mem.MemKERL;
import com.salesforce.apollo.stereotomy.mem.MemKeyStore;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Environment;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;

import java.net.SocketAddress;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

import static com.salesforce.apollo.cryptography.QualifiedBase64.digest;

/**
 * @author hal.hildebrand
 */
public class SkyApplication extends Application<SkyConfiguration> {

    private final Stereotomy                 stereotomy;
    private final ControlledIdentifierMember member;
    private       Sky                        node;
    private       MtlsServer                 mtlsServer;
    private       Router                     communications;

    public SkyApplication() {
        stereotomy = new StereotomyImpl(new MemKeyStore(), new MemKERL(DigestAlgorithm.DEFAULT), new SecureRandom());
        member = new ControlledIdentifierMember(stereotomy.newIdentifier());
    }

    @Override
    public void run(SkyConfiguration configuration, Environment environment) throws Exception {

        CertificateWithPrivateKey certWithKey = member.getCertificateWithPrivateKey(Instant.now(), Duration.ofHours(1),
                                                                                    SignatureAlgorithm.DEFAULT);
        var validator = EventValidation.NONE;
        Function<Member, SocketAddress> resolver = m -> ((View.Participant) m).endpoint();
        EndpointProvider ep = new StandardEpProvider(configuration.endpoint, ClientAuth.REQUIRE,
                                                     CertificateValidator.NONE, resolver);
        Function<Member, ClientContextSupplier> clientContextSupplier = null;
        mtlsServer = new MtlsServer(member, ep, clientContextSupplier, serverContextSupplier(certWithKey));
        communications = mtlsServer.router(configuration.connectionCache);
        var runtime = Parameters.RuntimeParameters.newBuilder()
                                                  .setCommunications(communications)
                                                  .setContext(Context.newBuilder()
                                                                     .setBias(3)
                                                                     .setpByz(configuration.probabilityByzantine)
                                                                     .build());
        node = new Sky(digest(configuration.group), member, configuration.params, configuration.dbURL,
                       configuration.checkpointBaseDir, runtime, configuration.endpoint,
                       com.salesforce.apollo.fireflies.Parameters.newBuilder(), validator);
        environment.jersey().register(new AdminResource(node.getDelphi(), Duration.ofSeconds(2)));
        environment.jersey().register(new Storage(node));
        environment.jersey().register(new AssertionResource(node.getDelphi(), Duration.ofSeconds(2)));
        environment.healthChecks().register("sky", new SkyHealthCheck());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown()));
        communications.start();
        node.start();
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
        if (communications != null) {
            communications.close(Duration.ofMinutes(1));
        }
    }
}
