package com.hellblazer.nut.comms;

import com.hellblazer.delos.comm.grpc.ServerContextSupplier;
import com.hellblazer.delos.cryptography.Digest;
import com.hellblazer.delos.cryptography.ssl.CertificateValidator;
import com.hellblazer.delos.cryptography.ssl.NodeKeyManagerFactory;
import com.hellblazer.delos.cryptography.ssl.NodeTrustManagerFactory;
import com.hellblazer.delos.cryptography.ssl.TlsInterceptor;
import com.hellblazer.delos.protocols.ClientIdentity;
import io.grpc.*;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.*;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.SocketAddress;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executors;

/**
 * A generic API GRPC MTLS server
 *
 * @author hal.hildebrand
 */
public class ApiServer implements ClientIdentity {
    public static final  String   TL_SV1_3      = "TLSv1.3";
    private static final Provider PROVIDER_JSSE = Security.getProvider("SunJSSE");

    private final ServerContextSupplier   supplier;
    private final Server                  server;
    private final Context.Key<SSLSession> sslSessionContext = Context.key("SSLSession");

    public ApiServer(SocketAddress address, ClientAuth clientAuth, String alias, ServerContextSupplier supplier,
                     CertificateValidator validator, BindableService... services) {
        var interceptor = new TlsInterceptor(sslSessionContext);
        this.supplier = supplier;
        NettyServerBuilder builder = NettyServerBuilder.forAddress(address)
                                                       .withOption(ChannelOption.SO_REUSEADDR, true)
                                                       .sslContext(
                                                       supplier.forServer(clientAuth, alias, validator, PROVIDER_JSSE))
                                                       .withChildOption(ChannelOption.TCP_NODELAY, true)
                                                       .intercept(interceptor)
                                                       .intercept(EnableCompressionInterceptor.SINGLETON);
        for (BindableService service : services) {
            builder.addService(service);
        }
        builder.executor(Executors.newVirtualThreadPerTaskExecutor());
        server = builder.build();
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
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
        return supplier.getMemberId(getCert());
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
     * Currently grpc-java doesn't return compressed responses, even if the client has sent a compressed payload. This
     * turns on gzip compression for all responses.
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
