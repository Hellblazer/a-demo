/**
 * Copyright (C) 2023 Hal Hildebrand. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 */
package com.hellblazer.nut;

import com.google.protobuf.Empty;
import com.salesforce.apollo.thoth.proto.Thoth_Grpc;
import com.salesforce.apollo.archipelago.LocalServer;
import com.salesforce.apollo.cryptography.DigestAlgorithm;
import com.salesforce.apollo.stereotomy.ControlledIdentifier;
import com.salesforce.apollo.stereotomy.EventCoordinates;
import com.salesforce.apollo.stereotomy.Stereotomy;
import com.salesforce.apollo.stereotomy.StereotomyImpl;
import com.salesforce.apollo.stereotomy.event.InceptionEvent;
import com.salesforce.apollo.stereotomy.event.RotationEvent;
import com.salesforce.apollo.stereotomy.event.Seal;
import com.salesforce.apollo.stereotomy.event.protobuf.ProtobufEventFactory;
import com.salesforce.apollo.stereotomy.identifier.Identifier;
import com.salesforce.apollo.stereotomy.identifier.SelfAddressingIdentifier;
import com.salesforce.apollo.stereotomy.identifier.spec.InteractionSpecification;
import com.salesforce.apollo.stereotomy.mem.MemKERL;
import com.salesforce.apollo.stereotomy.mem.MemKeyStore;
import io.grpc.Channel;
import io.grpc.ServerBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author hal.hildebrand
 **/
public class SkyTest {
    @Test
    public void smokin() throws Exception {
        var entropy = new SecureRandom();
        var ks = new MemKeyStore();
        var kerl = new MemKERL(DigestAlgorithm.DEFAULT);
        Stereotomy stereotomy = new StereotomyImpl(ks, kerl, entropy);
        var thothP = UUID.randomUUID().toString();
        ServerBuilder<?> serverBuilder = InProcessServerBuilder.forName(thothP);
        var gorgonP = thothP + "-gorgon";

        final var url = String.format("jdbc:h2:mem:%s;DB_CLOSE_ON_EXIT=FALSE", thothP);
        JdbcConnectionPool connectionPool = JdbcConnectionPool.create(url, "", "");
        connectionPool.setMaxConnections(10);

        var builder = Sky.Parameters.newBuilder()
                                    .setConnectionPool(connectionPool)
                                    .setThothService(serverBuilder)
                                    .setRouterFactory(member -> new LocalServer(gorgonP, member).router())
                                    .setKerl(kerl)
                                    .setContext(DigestAlgorithm.DEFAULT.getOrigin())
                                    .setEntropy(entropy);
        builder.getGorgoneion().setKerl(kerl);

        var nut = new Sky(builder.build());
        nut.start();

        var channel = InProcessChannelBuilder.forName(thothP).usePlaintext().build();
        var thoth = new ThothClient(channel);

        ControlledIdentifier<SelfAddressingIdentifier> controller = stereotomy.newIdentifier();

        // delegated inception
        var incp = thoth.inception(controller.getIdentifier());
        assertNotNull(incp);

        var seal = Seal.EventSeal.construct(incp.getIdentifier(), incp.hash(stereotomy.digestAlgorithm()),
                                            incp.getSequenceNumber().longValue());

        var builder1 = InteractionSpecification.newBuilder().addAllSeals(Collections.singletonList(seal));

        // Commit
        EventCoordinates coords = controller.seal(builder1);
        thoth.commit(coords);
        assertNotNull(thoth.identifier());

        // Delegated rotation
        var rot = thoth.rotate();

        assertNotNull(rot);

        seal = Seal.EventSeal.construct(rot.getIdentifier(), rot.hash(stereotomy.digestAlgorithm()),
                                        rot.getSequenceNumber().longValue());

        var builder2 = InteractionSpecification.newBuilder().addAllSeals(Collections.singletonList(seal));

        // Commit
        coords = controller.seal(builder2);
        thoth.commit(coords);
    }

    private static class ThothClient {
        private Thoth_Grpc.Thoth_BlockingStub client;

        private ThothClient(Channel channel) {
            this.client = Thoth_Grpc.newBlockingStub(channel);
        }

        public void commit(EventCoordinates coordinates) {
            client.commit(coordinates.toEventCoords());
        }

        public SelfAddressingIdentifier identifier() {
            return (SelfAddressingIdentifier) Identifier.from(client.identifier(Empty.getDefaultInstance()));
        }

        public InceptionEvent inception(SelfAddressingIdentifier identifier) {
            return ProtobufEventFactory.toKeyEvent(client.inception(identifier.toIdent()));
        }

        public RotationEvent rotate() {
            return ProtobufEventFactory.toKeyEvent(client.rotate(Empty.getDefaultInstance()));
        }
    }
}
