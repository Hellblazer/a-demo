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
import com.hellblazer.delos.archipelago.RouterImpl;
import com.hellblazer.delos.comm.grpc.ClientContextSupplier;
import com.hellblazer.delos.cryptography.Digest;
import com.hellblazer.delos.cryptography.ssl.CertificateValidator;
import io.grpc.ManagedChannel;
import io.grpc.NameResolver;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.ClientAuth;

import java.net.SocketAddress;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hellblazer.nut.comms.ApiServer.forClient;

public class MtlsClient {
    private static final Executor       exec = Executors.newVirtualThreadPerTaskExecutor();
    private final        ManagedChannel channel;

    /**
     * Apply gRPC timeout and keepalive configuration to a NettyChannelBuilder
     */
    private static NettyChannelBuilder applyGrpcConfig(NettyChannelBuilder builder, Duration keepaliveTime,
                                                        Duration keepaliveTimeout, Duration idleTimeout) {
        return builder.keepAliveTime(keepaliveTime.toNanos(), TimeUnit.NANOSECONDS)
                      .keepAliveTimeout(keepaliveTimeout.toNanos(), TimeUnit.NANOSECONDS)
                      .idleTimeout(idleTimeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    public MtlsClient(SocketAddress address, ClientAuth clientAuth, String alias, ClientContextSupplier supplier,
                      CertificateValidator validator, Executor exec) {
        this(address, clientAuth, alias, supplier, validator, exec, Duration.ofSeconds(30), Duration.ofSeconds(10),
             Duration.ofMinutes(5));
    }

    public MtlsClient(SocketAddress address, ClientAuth clientAuth, String alias, ClientContextSupplier supplier,
                      CertificateValidator validator, Executor exec, Duration keepaliveTime,
                      Duration keepaliveTimeout, Duration idleTimeout) {

        channel = applyGrpcConfig(NettyChannelBuilder.forAddress(address)
                                                     .executor(exec)
                                                     .sslContext(supplier.forClient(clientAuth, alias, validator,
                                                                                     ApiServer.TL_SV1_3)),
                                  keepaliveTime, keepaliveTimeout, idleTimeout)
                                     .build();

    }

    public MtlsClient(NameResolver.Factory resolver, ClientAuth clientAuth, String alias,
                      ClientContextSupplier supplier, CertificateValidator validator, Executor exec) {
        this(resolver, clientAuth, alias, supplier, validator, exec, Duration.ofSeconds(30), Duration.ofSeconds(10),
             Duration.ofMinutes(5));
    }

    public MtlsClient(NameResolver.Factory resolver, ClientAuth clientAuth, String alias,
                      ClientContextSupplier supplier, CertificateValidator validator, Executor exec,
                      Duration keepaliveTime, Duration keepaliveTimeout, Duration idleTimeout) {

        Limiter<GrpcClientRequestContext> limiter = new GrpcClientLimiterBuilder().blockOnLimit(false).build();
        channel = applyGrpcConfig(NettyChannelBuilder.forTarget("approach")
                                                     .nameResolverFactory(resolver)
                                                     .executor(exec)
                                                     .sslContext(supplier.forClient(clientAuth, alias, validator,
                                                                                     ApiServer.TL_SV1_3)),
                                  keepaliveTime, keepaliveTimeout, idleTimeout)
                                     .build();

    }

    public MtlsClient(SocketAddress address, ClientAuth clientAuth, String alias, X509Certificate certificate,
                      PrivateKey privateKey, CertificateValidator validator) {
        this(address, clientAuth, alias, certificate, privateKey, validator, Duration.ofSeconds(30),
             Duration.ofSeconds(10), Duration.ofMinutes(5));
    }

    public MtlsClient(SocketAddress address, ClientAuth clientAuth, String alias, X509Certificate certificate,
                      PrivateKey privateKey, CertificateValidator validator, Duration keepaliveTime,
                      Duration keepaliveTimeout, Duration idleTimeout) {

        Limiter<GrpcClientRequestContext> limiter = new GrpcClientLimiterBuilder().blockOnLimit(false).build();
        channel = applyGrpcConfig(NettyChannelBuilder.forAddress(address)
                                                     .executor(exec)
                                                     .sslContext(forClient(clientAuth, alias, certificate,
                                                                           privateKey, validator)),
                                  keepaliveTime, keepaliveTimeout, idleTimeout)
                                     .build();

    }

    public MtlsClient(NameResolver.Factory factory, ClientAuth clientAuth, String alias, X509Certificate certificate,
                      PrivateKey privateKey, CertificateValidator validator, Digest context) {
        this(factory, clientAuth, alias, certificate, privateKey, validator, context, Duration.ofSeconds(30),
             Duration.ofSeconds(10), Duration.ofMinutes(5));
    }

    public MtlsClient(NameResolver.Factory factory, ClientAuth clientAuth, String alias, X509Certificate certificate,
                      PrivateKey privateKey, CertificateValidator validator, Digest context, Duration keepaliveTime,
                      Duration keepaliveTimeout, Duration idleTimeout) {

        Limiter<GrpcClientRequestContext> limiter = new GrpcClientLimiterBuilder().blockOnLimit(false).build();
        channel = applyGrpcConfig(NettyChannelBuilder.forTarget("approach")
                                                     .nameResolverFactory(factory)
                                                     .defaultLoadBalancingPolicy("round_robin")
                                                     .executor(exec)
                                                     .sslContext(forClient(clientAuth, alias, certificate,
                                                                           privateKey, validator))
                                                     .intercept(RouterImpl.clientInterceptor(context)),
                                  keepaliveTime, keepaliveTimeout, idleTimeout)
                                     .build();

    }

    public ManagedChannel getChannel() {
        return channel;
    }

    public void stop() {
        channel.shutdown();
    }
}
