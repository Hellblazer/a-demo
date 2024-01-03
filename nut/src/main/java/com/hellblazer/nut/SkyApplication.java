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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;

import java.io.File;
import java.net.SocketAddress;
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
 **/
public class SkyApplication {
    private final Sky                       node;
    private final Router                    clusterComms;
    private final Gorgoneion                gorgoneion;
    private final CertificateWithPrivateKey certWithKey;

    public SkyApplication(SkyConfiguration configuration) {
        Stereotomy stereotomy = new StereotomyImpl(new MemKeyStore(), new MemKERL(DigestAlgorithm.DEFAULT),
                                                   new SecureRandom());
        ControlledIdentifierMember member = new ControlledIdentifierMember(stereotomy.newIdentifier());
        certWithKey = member.getCertificateWithPrivateKey(Instant.now(), Duration.ofHours(1),
                                                          SignatureAlgorithm.DEFAULT);
        Function<Member, SocketAddress> resolver = m -> ((View.Participant) m).endpoint();
        var certValidator = new DelegatedCertificateValidator();
        EndpointProvider ep = new StandardEpProvider(configuration.clusterEndpoint, ClientAuth.REQUIRE, certValidator,
                                                     resolver);
        MtlsServer clusterServer = new MtlsServer(member, ep, clientContextSupplier(),
                                                  serverContextSupplier(certWithKey));
        clusterComms = clusterServer.router(configuration.connectionCache);
        var runtime = Parameters.RuntimeParameters.newBuilder()
                                                  .setCommunications(clusterComms)
                                                  .setContext(configuration.context.build());
        node = new Sky(digest(configuration.group), member, configuration.processDomainParameters,
                       configuration.choamParameters, runtime, configuration.clusterEndpoint,
                       com.salesforce.apollo.fireflies.Parameters.newBuilder(), null);
        certValidator.setDelegate(new StereotomyValidator(node.getDht().getAni().verifiers(Duration.ofSeconds(30))));
        var k = node.getDht().asKERL();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown()));
        var gorgoneionParameters = configuration.gorgoneionParameters.setKerl(k).setVerifier(sa -> verify(sa));
        gorgoneion = new Gorgoneion(gorgoneionParameters.build(), member, runtime.getContext(),
                                    new DirectPublisher(new ProtoKERLAdapter(k)), clusterComms,
                                    Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory()), null,
                                    clusterComms);
    }

    public static void main(String[] argv) throws Exception {
        if (argv.length != 1) {
            System.err.println("Usage: SkyApplication <config file name>");
            System.exit(1);
        }
        var file = new File(System.getProperty("user.dir"), argv[0]);
        if (!file.exists()) {
            System.err.printf("Configuration file: %s does not exist", argv[0]).println();
            System.err.println("Usage: SkyApplication <config file name>");
            System.exit(1);
        }
        if (!file.isFile()) {
            System.err.printf("Configuration file: %s is a directory", argv[0]).println();
            System.err.println("Usage: SkyApplication <config file name>");
            System.exit(1);
        }
        var mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        var config = mapper.reader().readValue(file, SkyConfiguration.class);
        new SkyApplication(config).start();
        var t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "Keeper");
        t.setDaemon(false);
        t.start();
    }

    public void shutdown() {
        if (node != null) {
            node.stop();
        }
        if (clusterComms != null) {
            clusterComms.close(Duration.ofMinutes(1));
        }
    }

    public void start() {
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

    private boolean verify(SignedAttestation signedAttestation) {
        return true;
    }
}
