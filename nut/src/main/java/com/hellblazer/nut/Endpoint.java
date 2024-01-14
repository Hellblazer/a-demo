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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.grpc.inprocess.InProcessSocketAddress;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * A unified endpoint, either InProcess or Socket based
 *
 * @author hal.hildebrand
 **/
public record Endpoint(@JsonProperty("hostName") String hostName, @JsonProperty("port") int port,
                       @JsonProperty("name") String name) {

    public SocketAddress socketAddress() {
        if (name == null) {
            return new InetSocketAddress(hostName, port);
        } else {
            return new InProcessSocketAddress(name);
        }
    }

    public String toString() {
        return socketAddress().toString();
    }
}
