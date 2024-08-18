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

package com.hellblazer.sky.sanctum;

import com.google.protobuf.Empty;
import com.hellblazer.sanctorum.proto.Enclave_Grpc;
import com.jauntsdn.netty.channel.vsock.EpollVSockChannel;
import com.hellblazer.delos.cryptography.Digest;
import com.hellblazer.delos.cryptography.SignatureAlgorithm;
import com.hellblazer.delos.membership.stereotomy.ControlledIdentifierMember;
import io.grpc.Channel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessSocketAddress;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.VSockAddress;

import java.net.SocketAddress;

/**
 * @author hal.hildebrand
 **/
public class Sanctum {
    private final EnclaveIdentifier          identifier;
    private final ControlledIdentifierMember member;
    private final Channel                    channel;

    public Sanctum(SignatureAlgorithm algorithm, SocketAddress enclaveAddress) {
        this(algorithm, channelFor(enclaveAddress));
    }

    public Sanctum(SignatureAlgorithm algorithm, Channel channel) {
        identifier = new EnclaveIdentifier(algorithm, channel);
        member = new ControlledIdentifierMember(identifier);
        this.channel = channel;
    }

    public static Channel channelFor(SocketAddress enclaveAddress) {
        return switch (enclaveAddress) {
            case InProcessSocketAddress ipa -> InProcessChannelBuilder.forAddress(ipa).usePlaintext().build();
            case VSockAddress vs -> NettyChannelBuilder.forAddress(vs)
                                                       .withOption(ChannelOption.TCP_NODELAY, true)
                                                       .eventLoopGroup(new EpollEventLoopGroup())
                                                       .channelType(EpollVSockChannel.class)
                                                       .usePlaintext()
                                                       .build();
            default -> throw new IllegalArgumentException("Unsupported enclave address: " + enclaveAddress);
        };
    }

    public Enclave_Grpc.Enclave_BlockingStub getClient() {
        return Enclave_Grpc.newBlockingStub(channel);
    }

    public Digest getId() {
        return member.getId();
    }

    public ControlledIdentifierMember getMember() {
        return member;
    }

    public TokenGenerator tokenGenerator() {
        return new TokenGenerator(channel);
    }

    public void unwrap() {
        getClient().unwrap(Empty.getDefaultInstance());
    }
}
