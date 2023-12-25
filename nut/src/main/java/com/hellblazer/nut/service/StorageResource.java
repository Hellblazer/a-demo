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
import com.hellblazer.nut.Sky;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author hal.hildebrand
 **/
@Path("/storage")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)

public class StorageResource {

    private final Sky sky;

    public StorageResource(Sky sky) {
        this.sky = sky;
    }

    @DELETE
    @Timed
    @Path("/{key}")
    public void delete(@PathParam("key") String key) {

    }

    @GET
    @Timed
    @Path("/{key}")
    public OutputStream get(@PathParam("key") String key) {
        return null;
    }

    @PUT
    @Timed
    @Path("/{key}")
    public void put(@PathParam("key") String key, InputStream is) {

    }
}
