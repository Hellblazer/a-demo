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

import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;

import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author hal.hildebrand
 **/
public class SimpleNameResolverFactory extends NameResolver.Factory {

    final List<EquivalentAddressGroup> addresses;

    SimpleNameResolverFactory(List<SocketAddress> addresses) {
        this.addresses = addresses.stream().map(EquivalentAddressGroup::new).collect(Collectors.toList());
    }

    @Override
    public String getDefaultScheme() {
        return "simple";
    }

    public NameResolver newNameResolver(URI notUsedUri, NameResolver.Args args) {
        return new NameResolver() {
            @Override
            public String getServiceAuthority() {
                return "tremlo";
            }

            public void shutdown() {
            }

            public void start(Listener2 listener) {
                listener.onResult(
                ResolutionResult.newBuilder().setAddresses(addresses).setAttributes(Attributes.EMPTY).build());
            }
        };
    }
}
