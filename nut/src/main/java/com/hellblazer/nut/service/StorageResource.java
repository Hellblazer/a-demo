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
 *  more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.hellblazer.nut.service;

import com.codahale.metrics.annotation.Timed;
import com.hellblazer.nut.Geb;
import com.hellblazer.nut.Sky;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * @author hal.hildebrand
 **/
@Path("/storage")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)

public class StorageResource {

    private final Geb geb;

    public StorageResource(Geb geb) {
        this.geb = geb;
    }

    @POST
    @Timed
    @Path("delete")
    public void delete(Geb.KeyVersion key) {

    }

    @POST
    @Timed
    @Path("get")
    public String get(Geb.KeyVersion key) {
        return null;
    }

    @POST
    @Timed
    @Path("put")
    public void put(Geb.PutValue value) {

    }
}
