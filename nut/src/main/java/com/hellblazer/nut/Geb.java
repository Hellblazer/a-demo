
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

package com.hellblazer.nut;

import com.salesforce.apollo.choam.Parameters;
import com.salesforce.apollo.choam.proto.Transaction;
import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.membership.stereotomy.ControlledIdentifierMember;
import com.salesforce.apollo.model.ProcessDomain;
import com.salesforce.apollo.state.Mutator;
import com.salesforce.apollo.state.proto.Migration;
import com.salesforce.apollo.state.proto.Txn;
import com.salesforce.apollo.stereotomy.EventValidation;
import com.salesforce.apollo.stereotomy.identifier.SelfAddressingIdentifier;
import com.salesforce.apollo.stereotomy.identifier.spec.IdentifierSpecification;

import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.Path.of;

/**
 * @author hal.hildebrand
 **/
public class Geb extends ProcessDomain {
    public Geb(Digest group, ControlledIdentifierMember member, Parameters.Builder builder, String dbURL,
               Path checkpointBaseDir, Parameters.RuntimeParameters.Builder runtime, InetSocketAddress endpoint,
               Path commDirectory, com.salesforce.apollo.fireflies.Parameters.Builder ff,
               EventValidation eventValidation,
               IdentifierSpecification.Builder<SelfAddressingIdentifier> subDomainSpecification) {
        super(group, member, builder, dbURL, checkpointBaseDir, runtime, endpoint, commDirectory, ff, eventValidation,
              subDomainSpecification);
    }

    @Override
    protected Transaction migrations() {
        Map<Path, URL> resources = new HashMap<>();
        resources.put(of("/schema/nut.xml"), Geb.class.getResource("/schema/nut.xml"));
        return transactionOf(Txn.newBuilder()
                                .setMigration(Migration.newBuilder()
                                                       .setUpdate(Mutator.changeLog(resources, "/initialize.xml"))
                                                       .build())
                                .build());
    }
}
