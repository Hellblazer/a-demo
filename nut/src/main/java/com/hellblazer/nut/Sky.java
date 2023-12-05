package com.hellblazer.nut;

import com.salesforce.apollo.archipelago.Router;
import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.cryptography.DigestAlgorithm;
import com.salesforce.apollo.gorgoneion.Gorgoneion;
import com.salesforce.apollo.gorgoneion.comm.GorgoneionMetrics;
import com.salesforce.apollo.membership.Context;
import com.salesforce.apollo.membership.Member;
import com.salesforce.apollo.membership.stereotomy.ControlledIdentifierMember;
import com.salesforce.apollo.stereotomy.ControlledIdentifier;
import com.salesforce.apollo.stereotomy.KERL;
import com.salesforce.apollo.stereotomy.Stereotomy;
import com.salesforce.apollo.stereotomy.StereotomyImpl;
import com.salesforce.apollo.stereotomy.identifier.SelfAddressingIdentifier;
import com.salesforce.apollo.stereotomy.jks.JksKeyStore;
import com.salesforce.apollo.stereotomy.services.grpc.StereotomyMetrics;
import com.salesforce.apollo.stereotomy.services.proto.ProtoEventObserver;
import com.salesforce.apollo.thoth.KerlDHT;
import com.salesforce.apollo.thoth.Thoth;
import com.salesforce.apollo.thoth.grpc.ThothServer;
import com.salesforce.apollo.utils.Hex;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.h2.jdbcx.JdbcConnectionPool;

import java.io.IOException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author hal.hildebrand
 **/
public class Sky {
    private final    KERL                       kerl;
    private final    ThothServer                thoth;
    private final    JdbcConnectionPool         connectionPool;
    private final    Server                     thothService;
    private final    AtomicBoolean              started = new AtomicBoolean();
    private final    Context<Member>            context;
    private volatile KerlDHT                    dht;
    private volatile Gorgoneion                 gorgoneion;
    private volatile ControlledIdentifierMember member;
    private volatile Router                     communications;

    public Sky(Parameters parameters) throws Exception {
        this.connectionPool = parameters.connectionPool;
        this.kerl = parameters.kerl;
        context = Context.newBuilder().setId(parameters.context).setCardinality(3).build();

        final var pwd = new byte[64];
        parameters.entropy.nextBytes(pwd);
        final var password = Hex.hexChars(pwd);
        final Supplier<char[]> passwordProvider = () -> password;

        final var keystore = KeyStore.getInstance("JKS");

        keystore.load(null, password);

        Stereotomy stereotomy = new StereotomyImpl(new JksKeyStore(keystore, passwordProvider), kerl,
                                                   parameters.entropy);

        Consumer<ControlledIdentifier<SelfAddressingIdentifier>> onInception = identifier -> start(identifier,
                                                                                                   parameters);
        thoth = new ThothServer(new Thoth(stereotomy, onInception));
        this.thothService = parameters.thothService.addService(thoth).build();
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        try {
            thothService.start();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start thoth service for");
        }
    }

    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        communications.start();
        dht.stop();
    }

    private void start(ControlledIdentifier<SelfAddressingIdentifier> identifier, Parameters parameters) {
        member = new ControlledIdentifierMember(identifier);
        communications = parameters.routerFactory.apply(member);
        dht = new KerlDHT(parameters.operationsFrequency, context, member, connectionPool, DigestAlgorithm.DEFAULT,
                          communications, parameters.operationTimeout, parameters.falsePositiveRate,
                          parameters.stereotomyMetrics);
        ProtoEventObserver observer = null;
        this.gorgoneion = new Gorgoneion(parameters.gorgoneion.setKerl(dht.asKERL()).build(), member, context, observer,
                                         communications,
                                         Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory()),
                                         parameters.gorgoneionMetrics);
        communications.start();
        dht.start(parameters.replicationFrequency);
    }

    public record Parameters(Digest context, com.salesforce.apollo.gorgoneion.Parameters.Builder gorgoneion,
                             JdbcConnectionPool connectionPool, ServerBuilder thothService,
                             Function<ControlledIdentifierMember, Router> routerFactory, KERL kerl,
                             SecureRandom entropy, Duration operationTimeout, GorgoneionMetrics gorgoneionMetrics,
                             double falsePositiveRate, Duration operationsFrequency,
                             StereotomyMetrics stereotomyMetrics, Duration replicationFrequency) {
        public static Builder newBuilder() {
            return new Builder();
        }

        public static class Builder {

            private Digest                                              context;
            private com.salesforce.apollo.gorgoneion.Parameters.Builder gorgoneion           = new com.salesforce.apollo.gorgoneion.Parameters.Builder();
            private JdbcConnectionPool                                  connectionPool;
            private ServerBuilder                                       thothService;
            private Function<ControlledIdentifierMember, Router>        routerFactory;
            private KERL                                                kerl;
            private SecureRandom                                        entropy;
            private Duration                                            operationTimeout     = Duration.ofSeconds(3);
            private GorgoneionMetrics                                   gorgoneionMetrics;
            private double                                              falsePositiveRate    = 0.000125;
            private Duration                                            operationsFrequency  = Duration.ofMillis(3);
            private StereotomyMetrics                                   stereotomyMetrics;
            private Duration                                            replicationFrequency = Duration.ofMillis(10);

            public Parameters build() {
                return new Parameters(context, gorgoneion, connectionPool, thothService, routerFactory, kerl, entropy,
                                      operationTimeout, gorgoneionMetrics, falsePositiveRate, operationsFrequency,
                                      stereotomyMetrics, replicationFrequency);
            }

            public JdbcConnectionPool getConnectionPool() {
                return connectionPool;
            }

            public Builder setConnectionPool(JdbcConnectionPool connectionPool) {
                this.connectionPool = connectionPool;
                return this;
            }

            public Digest getContext() {
                return context;
            }

            public Builder setContext(Digest context) {
                this.context = context;
                return this;
            }

            public SecureRandom getEntropy() {
                return entropy;
            }

            public Builder setEntropy(SecureRandom entropy) {
                this.entropy = entropy;
                return this;
            }

            public double getFalsePositiveRate() {
                return falsePositiveRate;
            }

            public Builder setFalsePositiveRate(double falsePositiveRate) {
                this.falsePositiveRate = falsePositiveRate;
                return this;
            }

            public com.salesforce.apollo.gorgoneion.Parameters.Builder getGorgoneion() {
                return gorgoneion;
            }

            public Builder setGorgoneion(com.salesforce.apollo.gorgoneion.Parameters.Builder gorgoneion) {
                this.gorgoneion = gorgoneion;
                return this;
            }

            public GorgoneionMetrics getGorgoneionMetrics() {
                return gorgoneionMetrics;
            }

            public Builder setGorgoneionMetrics(GorgoneionMetrics gorgoneionMetrics) {
                this.gorgoneionMetrics = gorgoneionMetrics;
                return this;
            }

            public KERL getKerl() {
                return kerl;
            }

            public Builder setKerl(KERL kerl) {
                this.kerl = kerl;
                return this;
            }

            public Duration getOperationTimeout() {
                return operationTimeout;
            }

            public Builder setOperationTimeout(Duration operationTimeout) {
                this.operationTimeout = operationTimeout;
                return this;
            }

            public Duration getOperationsFrequency() {
                return operationsFrequency;
            }

            public Builder setOperationsFrequency(Duration operationsFrequency) {
                this.operationsFrequency = operationsFrequency;
                return this;
            }

            public Duration getReplicationFrequency() {
                return replicationFrequency;
            }

            public Builder setReplicationFrequency(Duration replicationFrequency) {
                this.replicationFrequency = replicationFrequency;
                return this;
            }

            public Function<ControlledIdentifierMember, Router> getRouterFactory() {
                return routerFactory;
            }

            public Builder setRouterFactory(Function<ControlledIdentifierMember, Router> routerFactory) {
                this.routerFactory = routerFactory;
                return this;
            }

            public StereotomyMetrics getStereotomyMetrics() {
                return stereotomyMetrics;
            }

            public Builder setStereotomyMetrics(StereotomyMetrics stereotomyMetrics) {
                this.stereotomyMetrics = stereotomyMetrics;
                return this;
            }

            public ServerBuilder getThothService() {
                return thothService;
            }

            public Builder setThothService(ServerBuilder thothService) {
                this.thothService = thothService;
                return this;
            }
        }
    }
}
