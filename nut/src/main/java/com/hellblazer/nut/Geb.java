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

import com.macasaet.fernet.Token;
import com.salesforce.apollo.delphinius.Oracle;
import com.salesforce.apollo.state.Mutator;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * The secrets storage
 *
 * @author hal.hildebrand
 **/
public class Geb {

    private final DSLContext dslCtx;
    private final Mutator    mutator;

    public Geb(Connection connection, Mutator mutator) {
        this.dslCtx = DSL.using(connection);
        this.mutator = mutator;
    }

    public static String get(DSLContext dslCtx) throws SQLException {
        return null;
    }

    public void delete(KeyVersion key) throws SQLException {
    }

    public String get(KeyVersion key)throws SQLException {
        return null;
    }

    public int put(PutValue value)throws SQLException {
        return 0;
    }

    public record PutValue(Oracle.Object key, String value, int cas) {
    }

    public record KeyVersion(Oracle.Object key, int version) {
    }
}
