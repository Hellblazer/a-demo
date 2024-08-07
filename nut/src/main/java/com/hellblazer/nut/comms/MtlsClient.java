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

package com.hellblazer.nut.comms;

import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.grpc.client.GrpcClientLimiterBuilder;
import com.netflix.concurrency.limits.grpc.client.GrpcClientRequestContext;
import com.salesforce.apollo.archipelago.RouterImpl;
import com.salesforce.apollo.comm.grpc.ClientContextSupplier;
import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.cryptography.ssl.CertificateValidator;
import io.grpc.ManagedChannel;
import io.grpc.NameResolver;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.ClientAuth;

import java.net.SocketAddress;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.hellblazer.nut.comms.ApiServer.forClient;

public class MtlsClient {
    private static final Executor       exec = Executors.newVirtualThreadPerTaskExecutor();
    private final        ManagedChannel channel;

    public MtlsClient(SocketAddress address, ClientAuth clientAuth, String alias, ClientContextSupplier supplier,
                      CertificateValidator validator, Executor exec) {

        channel = NettyChannelBuilder.forAddress(address)
                                     .executor(exec)
                                     .sslContext(supplier.forClient(clientAuth, alias, validator, ApiServer.TL_SV1_3))
                                     .build();

    }

    public MtlsClient(NameResolver.Factory resolver, ClientAuth clientAuth, String alias,
                      ClientContextSupplier supplier, CertificateValidator validator, Executor exec) {

        Limiter<GrpcClientRequestContext> limiter = new GrpcClientLimiterBuilder().blockOnLimit(false).build();
        channel = NettyChannelBuilder.forTarget("approach")
                                     .nameResolverFactory(resolver)
                                     .executor(exec)
                                     .sslContext(supplier.forClient(clientAuth, alias, validator, ApiServer.TL_SV1_3))
                                     .build();

    }

    public MtlsClient(SocketAddress address, ClientAuth clientAuth, String alias, X509Certificate certificate,
                      PrivateKey privateKey, CertificateValidator validator) {

        Limiter<GrpcClientRequestContext> limiter = new GrpcClientLimiterBuilder().blockOnLimit(false).build();
        channel = NettyChannelBuilder.forAddress(address)
                                     .executor(exec)
                                     .sslContext(forClient(clientAuth, alias, certificate, privateKey, validator))
                                     .build();

    }

    public MtlsClient(NameResolver.Factory factory, ClientAuth clientAuth, String alias, X509Certificate certificate,
                      PrivateKey privateKey, CertificateValidator validator, Digest context) {

        Limiter<GrpcClientRequestContext> limiter = new GrpcClientLimiterBuilder().blockOnLimit(false).build();
        channel = NettyChannelBuilder.forTarget("approach")
                                     .nameResolverFactory(factory)
                                     .defaultLoadBalancingPolicy("round_robin")
                                     .executor(exec)
                                     .sslContext(forClient(clientAuth, alias, certificate, privateKey, validator))
                                     .intercept(RouterImpl.clientInterceptor(context))
                                     .build();

    }

    public ManagedChannel getChannel() {
        return channel;
    }

    public void stop() {
        channel.shutdown();
    }
}
