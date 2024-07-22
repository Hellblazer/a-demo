package com.hellblazer.sky.demo;

import com.hellblazer.nut.comms.MtlsClient;
import com.hellblazer.nut.service.OracleAdapter;
import com.salesforce.apollo.cryptography.cert.CertificateWithPrivateKey;
import com.salesforce.apollo.cryptography.ssl.CertificateValidator;
import com.salesforce.apollo.delphinius.Oracle;
import com.salesforce.apollo.utils.Utils;
import io.netty.handler.ssl.ClientAuth;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * @author hal.hildebrand
 **/
@Testcontainers
public class SmokeTest {
    private static final Logger log = LoggerFactory.getLogger(SmokeTest.class);

    @Test
    public void smokin() throws Exception {
        var logConsumer = new Slf4jLogConsumer(log);
        logConsumer.withSeparateOutputStreams();
        var network = Network.newNetwork();
        var skyImage = DockerImageName.parse("com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT");
        var host = "bootstrap";
        try (GenericContainer<?> boot = new GenericContainer<>(skyImage).withNetwork(network)
                                                                        .withNetworkAliases(host)
                                                                        .withNetworkMode("bridge")
                                                                        .withEnv(boostrap())) {
            boot.start();
            boot.followOutput(logConsumer);
            try (GenericContainer<?> k1 = new GenericContainer<>(skyImage).withNetwork(network)
                                                                          .withNetworkMode("bridge")
                                                                          .withEnv(kernel(host));
                 GenericContainer<?> k2 = new GenericContainer<>(skyImage).withNetwork(network)
                                                                          .withNetworkMode("bridge")
                                                                          .withEnv(kernel(host));
                 GenericContainer<?> k3 = new GenericContainer<>(skyImage).withNetwork(network)
                                                                          .withNetworkMode("bridge")
                                                                          .withEnv(kernel(host))) {
                k1.start();
                k1.followOutput(logConsumer);
                k2.start();
                k2.followOutput(logConsumer);
                k3.start();
                k3.followOutput(logConsumer);
                Thread.sleep(120_000);
            }
        }
    }

    private MtlsClient apiClient(int i, InetSocketAddress serverAddress) {
        CertificateWithPrivateKey clientCert = Utils.getMember(i);

        return new MtlsClient(serverAddress, ClientAuth.REQUIRE, "foo", clientCert.getX509Certificate(),
                              clientCert.getPrivateKey(), CertificateValidator.NONE);
    }

    private Map<String, String> boostrap() {
        Map<String, String> env = new HashMap<>();
        env.put("GENESIS", "true");
        env.put("BIND_INTERFACE", "eth0");
        env.put("API", "50000");
        env.put("APPROACH", "50001");
        env.put("CLUSTER", "50002");
        env.put("SERVICE", "50003");
        env.put("HEALTH", "50004");
        return env;
    }

    private Map<String, String> kernel(String network) {
        Map<String, String> env = new HashMap<>();
        env.put("GENESIS", "true");
        env.put("BIND_INTERFACE", "eth0");
        env.put("APPROACHES", "%s:5001".formatted(network));
        env.put("SEEDS", "%s:5002#50000".formatted(network));
        return env;
    }

    private Oracle of(SocketAddress endpoint) {
        var client = apiClient(0, (InetSocketAddress) endpoint);
        return new OracleAdapter(client.getChannel());
    }
}
