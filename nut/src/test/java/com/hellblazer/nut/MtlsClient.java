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

import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.grpc.client.ConcurrencyLimitClientInterceptor;
import com.netflix.concurrency.limits.grpc.client.GrpcClientLimiterBuilder;
import com.netflix.concurrency.limits.grpc.client.GrpcClientRequestContext;
import com.salesforce.apollo.comm.grpc.ClientContextSupplier;
import com.salesforce.apollo.cryptography.ssl.CertificateValidator;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.ClientAuth;

import java.net.SocketAddress;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.hellblazer.nut.ApiServer.forClient;

public class MtlsClient {
    private static final Executor       exec = Executors.newVirtualThreadPerTaskExecutor();
    private final        ManagedChannel channel;

    public MtlsClient(SocketAddress address, ClientAuth clientAuth, String alias, ClientContextSupplier supplier,
                      CertificateValidator validator, Executor exec) {

        Limiter<GrpcClientRequestContext> limiter = new GrpcClientLimiterBuilder().blockOnLimit(false).build();
        channel = NettyChannelBuilder.forAddress(address)
                                     .executor(exec)
                                     .sslContext(supplier.forClient(clientAuth, alias, validator, ApiServer.TL_SV1_3))
                                     .intercept(new ConcurrencyLimitClientInterceptor(limiter,
                                                                                      () -> Status.RESOURCE_EXHAUSTED.withDescription(
                                                                                      "Client side concurrency limit exceeded")))
                                     .build();

    }

    public MtlsClient(SocketAddress address, ClientAuth clientAuth, String alias, X509Certificate certificate,
                      PrivateKey privateKey, CertificateValidator validator) {

        Limiter<GrpcClientRequestContext> limiter = new GrpcClientLimiterBuilder().blockOnLimit(false).build();
        channel = NettyChannelBuilder.forAddress(address)
                                     .executor(exec)
                                     .sslContext(forClient(clientAuth, alias, certificate, privateKey, validator))
                                     .intercept(new ConcurrencyLimitClientInterceptor(limiter,
                                                                                      () -> Status.RESOURCE_EXHAUSTED.withDescription(
                                                                                      "Client side concurrency limit exceeded")))
                                     .build();

    }

    public ManagedChannel getChannel() {
        return channel;
    }

    public void stop() {
        channel.shutdown();
    }
}
