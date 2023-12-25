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
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.io.OutputStream;

/**
 * @author hal.hildebrand
 **/
@Path("/storage")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)

public class Storage {

    private final Sky sky;

    public Storage(Sky sky) {
        this.sky = sky;
    }

    @PUT
    @Timed
    @Path("delete")
    public void delete() {

    }

    @PUT
    @Timed
    @Path("get")
    public OutputStream get() {
        return null;
    }

    @PUT
    @Timed
    @Path("put")
    public void put() {

    }
}
