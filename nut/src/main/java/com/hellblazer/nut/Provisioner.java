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

import com.hellblazer.nut.service.Geb;
import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.delphinius.Oracle;
import com.salesforce.apollo.gorgoneion.proto.Attestation;
import com.salesforce.apollo.state.Mutator;

/**
 * Provision and validate an attested member.
 *
 * @author hal.hildebrand
 **/
abstract public class Provisioner {
    protected final Digest  id;
    protected final Oracle  oracle;
    protected final Geb     geb;
    protected final Mutator mutator;

    protected Provisioner(Digest id, Oracle oracle, Geb geb, Mutator mutator) {
        this.id = id;
        this.oracle = oracle;
        this.geb = geb;
        this.mutator = mutator;
    }

    abstract public boolean provision(Attestation attestation);
}
