
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

import com.hellblazer.delos.choam.Parameters;
import com.hellblazer.delos.choam.proto.Transaction;
import com.hellblazer.delos.cryptography.Digest;
import com.hellblazer.delos.membership.stereotomy.ControlledIdentifierMember;
import com.hellblazer.delos.model.ProcessDomain;
import com.hellblazer.delos.state.Mutator;
import com.hellblazer.delos.state.proto.Migration;
import com.hellblazer.delos.state.proto.Txn;
import com.hellblazer.delos.stereotomy.services.grpc.StereotomyMetrics;

import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.Path.of;

/**
 * @author hal.hildebrand
 **/
public class Sky extends ProcessDomain {
    public Sky(Digest group, ControlledIdentifierMember member, ProcessDomain.ProcessDomainParameters params,
               Parameters.Builder builder, Parameters.RuntimeParameters.Builder runtime, String endpoint,
               com.hellblazer.delos.fireflies.Parameters.Builder ff, StereotomyMetrics stereotomyMetrics) {
        super(group, member, params, builder, runtime, endpoint, ff, stereotomyMetrics);
    }

    public Mutator getMutator() {
        return mutator;
    }

    public void register(FernetProvisioner.TokenValidator validator) {
        sqlStateMachine.register(FernetProvisioner.TOKEN_VALIDATOR, params -> validator);
    }

    @Override
    protected Transaction migrations() {
        Map<Path, URL> resources = new HashMap<>();
        resources.put(of("/schema/nut.xml"), Sky.class.getResource("/schema/nut.xml"));
        resources.put(of("/schema/initialize-nut.xml"), Sky.class.getResource("/schema/initialize-nut.xml"));
        return transactionOf(Txn.newBuilder()
                                .setMigration(Migration.newBuilder()
                                                       .setUpdate(
                                                       Mutator.changeLog(resources, "/schema/initialize-nut.xml"))
                                                       .build())
                                .build());
    }
}
