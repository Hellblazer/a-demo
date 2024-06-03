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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.salesforce.apollo.comm.grpc.ServerContextSupplier;
import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.cryptography.ssl.CertificateValidator;
import com.salesforce.apollo.cryptography.ssl.NodeKeyManagerFactory;
import com.salesforce.apollo.cryptography.ssl.NodeTrustManagerFactory;
import com.salesforce.apollo.cryptography.ssl.TlsInterceptor;
import com.salesforce.apollo.delphinius.Oracle;
import com.salesforce.apollo.protocols.ClientIdentity;
import com.salesforce.apollo.state.Mutator;
import io.grpc.*;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.*;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.SocketAddress;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

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

    public String get(KeyVersion key) throws SQLException {
        return null;
    }

    public int put(PutValue value) throws SQLException {
        return 0;
    }

    public record PutValue(Oracle.Object key, String value, int cas) {
    }

    public record KeyVersion(Oracle.Object key, int version) {
    }

    /**
     * @author hal.hildebrand
     */
    public static class ApiServer implements ClientIdentity {
        public static final  String   TL_SV1_3      = "TLSv1.3";
        private static final Provider PROVIDER_JSSE = Security.getProvider("SunJSSE");

        private final LoadingCache<X509Certificate, Digest> cachedMembership;
        private final TlsInterceptor                        interceptor;
        private final Server                                server;
        private final Context.Key<SSLSession>               sslSessionContext = Context.key("SSLSession");

        public ApiServer(SocketAddress address, ClientAuth clientAuth, String alias, ServerContextSupplier supplier,
                         CertificateValidator validator, BindableService service) {
            interceptor = new TlsInterceptor(sslSessionContext);
            cachedMembership = CacheBuilder.newBuilder().build(new CacheLoader<X509Certificate, Digest>() {
                @Override
                public Digest load(X509Certificate key) throws Exception {
                    return supplier.getMemberId(key);
                }
            });
            NettyServerBuilder builder = NettyServerBuilder.forAddress(address)
                                                           .withOption(ChannelOption.SO_REUSEADDR, true)
                                                           .addService(service)
                                                           .sslContext(supplier.forServer(clientAuth, alias, validator,
                                                                                          PROVIDER_JSSE))
                                                           .withChildOption(ChannelOption.TCP_NODELAY, true)
                                                           .intercept(interceptor)
                                                           .intercept(EnableCompressionInterceptor.SINGLETON);
            builder.executor(Executors.newVirtualThreadPerTaskExecutor());
            server = builder.build();
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    server.shutdown();
                }
            });
        }

        public static SslContext forClient(ClientAuth clientAuth, String alias, X509Certificate certificate,
                                           PrivateKey privateKey, CertificateValidator validator) {
            SslContextBuilder builder = SslContextBuilder.forClient()
                                                         .sslContextProvider(PROVIDER_JSSE)
                                                         .keyManager(
                                                         new NodeKeyManagerFactory(alias, certificate, privateKey,
                                                                                   PROVIDER_JSSE));
            GrpcSslContexts.configure(builder, SslProvider.JDK);
            builder.protocols(TL_SV1_3)
                   .sslContextProvider(PROVIDER_JSSE)
                   .trustManager(new NodeTrustManagerFactory(validator, PROVIDER_JSSE))
                   .clientAuth(clientAuth)
                   .applicationProtocolConfig(new ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,
                                                                            // NO_ADVERTISE is currently the only mode
                                                                            // supported by both OpenSsl and JDK
                                                                            // providers.
                                                                            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                                                                            // ACCEPT is currently the only mode supported
                                                                            // by both OpenSsl and JDK
                                                                            // providers.
                                                                            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                                                                            ApplicationProtocolNames.HTTP_2,
                                                                            ApplicationProtocolNames.HTTP_1_1));
            try {
                return builder.build();
            } catch (SSLException e) {
                throw new IllegalStateException("Cannot build ssl client context", e);
            }

        }

        public static SslContext forServer(ClientAuth clientAuth, String alias, X509Certificate certificate,
                                           PrivateKey privateKey, CertificateValidator validator) {
            SslContextBuilder builder = SslContextBuilder.forServer(
            new NodeKeyManagerFactory(alias, certificate, privateKey, PROVIDER_JSSE));
            GrpcSslContexts.configure(builder, SslProvider.JDK);
            builder.protocols(TL_SV1_3)
                   .sslContextProvider(PROVIDER_JSSE)
                   .trustManager(new NodeTrustManagerFactory(validator, PROVIDER_JSSE))
                   .clientAuth(clientAuth)
                   .applicationProtocolConfig(new ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,
                                                                            // NO_ADVERTISE is currently the only mode
                                                                            // supported by both OpenSsl and JDK
                                                                            // providers.
                                                                            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                                                                            // ACCEPT is currently the only mode supported
                                                                            // by both OpenSsl and JDK
                                                                            // providers.
                                                                            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                                                                            ApplicationProtocolNames.HTTP_2,
                                                                            ApplicationProtocolNames.HTTP_1_1));
            try {
                return builder.build();
            } catch (SSLException e) {
                throw new IllegalStateException("Cannot build ssl client context", e);
            }

        }

        public SocketAddress getAddress() {
            return server.getListenSockets().getFirst();
        }

        @Override
        public Digest getFrom() {
            try {
                return cachedMembership.get(getCert());
            } catch (ExecutionException e) {
                throw new IllegalStateException("Unable to derive member id from cert", e.getCause());
            }
        }

        public void start() throws IOException {
            server.start();
        }

        public void stop() {
            server.shutdownNow();
        }

        private X509Certificate getCert() {
            try {
                return (X509Certificate) sslSessionContext.get().getPeerCertificates()[0];
            } catch (SSLPeerUnverifiedException e) {
                throw new IllegalStateException(e);
            }
        }

        /**
         * Currently grpc-java doesn't return compressed responses, even if the client has sent a compressed payload.
         * This turns on gzip compression for all responses.
         */
        public static class EnableCompressionInterceptor implements ServerInterceptor {
            public final static EnableCompressionInterceptor SINGLETON = new EnableCompressionInterceptor();

            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                                                                         ServerCallHandler<ReqT, RespT> next) {
                call.setCompression("gzip");
                return next.startCall(call, headers);
            }
        }
    }
}
