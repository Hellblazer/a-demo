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
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.salesforce.apollo.choam.Session.retryNesting;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hal.hildebrand
 **/
@Testcontainers
public class SmokeTest {
    private static final Logger log = LoggerFactory.getLogger(SmokeTest.class);

    public static void smoke(Oracle oracle) throws Exception {
        // Namespace
        var ns = Oracle.namespace("my-org");

        // relations
        var member = ns.relation("member");
        var flag = ns.relation("flag");

        // Group membership
        var userMembers = ns.subject("Users", member);
        var adminMembers = ns.subject("Admins", member);
        var helpDeskMembers = ns.subject("HelpDesk", member);
        var managerMembers = ns.subject("Managers", member);
        var technicianMembers = ns.subject("Technicians", member);
        var abcTechMembers = ns.subject("ABCTechnicians", member);
        var flaggedTechnicianMembers = ns.subject(abcTechMembers.name(), flag);

        // Flagged subjects for testing
        var egin = ns.subject("Egin", flag);
        var ali = ns.subject("Ali", flag);
        var gl = ns.subject("G l", flag);
        var fuat = ns.subject("Fuat", flag);

        // Subjects
        var jale = ns.subject("Jale");
        var irmak = ns.subject("Irmak");
        var hakan = ns.subject("Hakan");
        var demet = ns.subject("Demet");
        var can = ns.subject("Can");
        var burcu = ns.subject("Burcu");

        // Map direct edges. Transitive edges added as a side effect

        var countDown = new CountDownLatch(17);
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {

            retryNesting(() -> oracle.map(helpDeskMembers, adminMembers), 3).whenCompleteAsync(
            (_, _) -> countDown.countDown(), exec);
            retryNesting(() -> oracle.map(ali, adminMembers), 3).whenCompleteAsync((_, _) -> countDown.countDown(),
                                                                                   exec);
            retryNesting(() -> oracle.map(ali, userMembers), 3).whenCompleteAsync((_, _) -> countDown.countDown(),
                                                                                  exec);
            retryNesting(() -> oracle.map(burcu, userMembers), 3).whenCompleteAsync((_, _) -> countDown.countDown(),
                                                                                    exec);
            retryNesting(() -> oracle.map(can, userMembers), 3).whenCompleteAsync((_, _) -> countDown.countDown(),
                                                                                  exec);
            retryNesting(() -> oracle.map(managerMembers, userMembers), 3).whenCompleteAsync(
            (_, _) -> countDown.countDown(), exec);
            retryNesting(() -> oracle.map(technicianMembers, userMembers), 3).whenCompleteAsync(
            (_, _) -> countDown.countDown(), exec);
            retryNesting(() -> oracle.map(demet, helpDeskMembers), 3).whenCompleteAsync((_, _) -> countDown.countDown(),
                                                                                        exec);
            retryNesting(() -> oracle.map(egin, helpDeskMembers), 3).whenCompleteAsync((_, _) -> countDown.countDown(),
                                                                                       exec);
            retryNesting(() -> oracle.map(egin, userMembers), 3).whenCompleteAsync((_, _) -> countDown.countDown(),
                                                                                   exec);
            retryNesting(() -> oracle.map(fuat, managerMembers), 3).whenCompleteAsync((_, _) -> countDown.countDown(),
                                                                                      exec);
            retryNesting(() -> oracle.map(gl, managerMembers), 3).whenCompleteAsync((_, _) -> countDown.countDown(),
                                                                                    exec);
            retryNesting(() -> oracle.map(hakan, technicianMembers), 3).whenCompleteAsync(
            (_, _) -> countDown.countDown(), exec);
            retryNesting(() -> oracle.map(irmak, technicianMembers), 3).whenCompleteAsync(
            (_, _) -> countDown.countDown(), exec);
            retryNesting(() -> oracle.map(abcTechMembers, technicianMembers), 3).whenCompleteAsync(
            (_, _) -> countDown.countDown(), exec);
            retryNesting(() -> oracle.map(flaggedTechnicianMembers, technicianMembers), 3).whenCompleteAsync(
            (_, _) -> countDown.countDown(), exec);
            retryNesting(() -> oracle.map(jale, abcTechMembers), 3).whenCompleteAsync((_, _) -> countDown.countDown(),
                                                                                      exec);

            countDown.await(30, TimeUnit.SECONDS);
        }

        // Protected resource namespace
        var docNs = Oracle.namespace("Document");
        // Permission
        var view = docNs.relation("View");
        // Protected Object
        var object123View = docNs.object("123", view);

        // Users can View Document 123
        Oracle.Assertion tuple = userMembers.assertion(object123View);
        retryNesting(() -> oracle.add(tuple), 3).get(120, TimeUnit.SECONDS);

        // Direct subjects that can View the document
        var viewers = oracle.read(object123View);
        assertEquals(1, viewers.size());
        assertTrue(viewers.contains(userMembers), "Should contain: " + userMembers);

        // Direct objects that can User member can view
        var viewable = oracle.read(userMembers);
        assertEquals(1, viewable.size());
        assertEquals(viewable.getFirst(), object123View, "Should contain: " + object123View);

        // Assert flagged technicians can directly view the document
        Oracle.Assertion grantTechs = flaggedTechnicianMembers.assertion(object123View);
        var ts = retryNesting(() -> oracle.add(grantTechs), 3).get(120, TimeUnit.SECONDS).ts();

        // Now have 2 direct subjects that can view the doc
        viewers = oracle.read(object123View);
        assertEquals(2, viewers.size());
        assertTrue(viewers.contains(userMembers), "Should contain: " + userMembers);
        assertTrue(viewers.contains(flaggedTechnicianMembers), "Should contain: " + flaggedTechnicianMembers);

        // flagged has direct view
        viewable = oracle.read(flaggedTechnicianMembers);
        assertEquals(1, viewable.size());
        assertTrue(viewable.contains(object123View), "Should contain: " + object123View);

        // Filter direct on flagged relation
        var flaggedViewers = oracle.read(flag, object123View);
        assertEquals(1, flaggedViewers.size());
        assertTrue(flaggedViewers.contains(flaggedTechnicianMembers), "Should contain: " + flaggedTechnicianMembers);

        // Transitive subjects that can view the document
        var inferredViewers = oracle.expand(object123View);
        assertEquals(14, inferredViewers.size());
        for (var s : Arrays.asList(ali, jale, egin, irmak, hakan, gl, fuat, can, burcu, managerMembers,
                                   technicianMembers, abcTechMembers, userMembers, flaggedTechnicianMembers)) {
            assertTrue(inferredViewers.contains(s), "Should contain: " + s);
        }

        // Transitive subjects filtered by flag predicate
        var inferredFlaggedViewers = oracle.expand(flag, object123View);
        assertEquals(5, inferredFlaggedViewers.size());
        for (var s : Arrays.asList(egin, ali, gl, fuat, flaggedTechnicianMembers)) {
            assertTrue(inferredFlaggedViewers.contains(s), "Should contain: " + s);
        }

        // Check some assertions
        assertTrue(oracle.check(object123View.assertion(jale), ts));
        assertTrue(oracle.check(object123View.assertion(egin), ts));
        assertFalse(oracle.check(object123View.assertion(helpDeskMembers), ts));

        // Remove them
        retryNesting(() -> oracle.remove(abcTechMembers, technicianMembers), 3).get(60, TimeUnit.SECONDS);

        assertFalse(oracle.check(object123View.assertion(jale), ts));
        assertTrue(oracle.check(object123View.assertion(egin), ts));
        assertFalse(oracle.check(object123View.assertion(helpDeskMembers), ts));

        // Remove our assertion
        retryNesting(() -> oracle.delete(tuple), 3).get(20, TimeUnit.SECONDS);

        assertFalse(oracle.check(object123View.assertion(jale), ts));
        assertFalse(oracle.check(object123View.assertion(egin), ts));
        assertFalse(oracle.check(object123View.assertion(helpDeskMembers), ts));

        // Some deletes
        retryNesting(() -> oracle.delete(abcTechMembers), 3).get(20, TimeUnit.SECONDS);
        retryNesting(() -> oracle.delete(flaggedTechnicianMembers), 3).get(20, TimeUnit.SECONDS);
    }

    @Test
    public void smokin() throws Exception {
        var logConsumer = new Slf4jLogConsumer(log);
        logConsumer.withSeparateOutputStreams();
        var network = Network.newNetwork();
        var skyImage = DockerImageName.parse("com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT");
        try (GenericContainer<?> boot = new GenericContainer<>(skyImage).withNetwork(network)
                                                                        .withNetworkAliases("bootstrap")
                                                                        .withNetworkMode("bridge")
                                                                        .withEnv(bootstrap())
                                                                        .withExposedPorts(8126, 8127)) {
            boot.start();
            boot.followOutput(logConsumer);
            try (GenericContainer<?> k1 = new GenericContainer<>(skyImage).withNetwork(network)
                                                                          .withNetworkMode("bridge")
                                                                          .withNetworkAliases("k1")
                                                                          .withEnv(kernel());
                 GenericContainer<?> k2 = new GenericContainer<>(skyImage).withNetwork(network)
                                                                          .withNetworkMode("bridge")
                                                                          .withNetworkAliases("k2")
                                                                          .withEnv(kernel());
                 GenericContainer<?> k3 = new GenericContainer<>(skyImage).withNetwork(network)
                                                                          .withNetworkMode("bridge")
                                                                          .withNetworkAliases("k3")
                                                                          .withEnv(kernel())
                                                                          .withExposedPorts(8126, 8127)) {
                k1.start();
                k1.followOutput(logConsumer);
                k2.start();
                k2.followOutput(logConsumer);
                k3.waitingFor(new HostPortWaitStrategy().forPorts(8126));
                k3.start();
                k3.followOutput(logConsumer);
                var oracle = of(new InetSocketAddress(k3.getHost(), k3.getMappedPort(8126)));
                Thread.sleep(10_000);
                smoke(oracle);
            }
        }
    }

    private MtlsClient apiClient(int i, InetSocketAddress serverAddress) {
        CertificateWithPrivateKey clientCert = Utils.getMember(i);

        return new MtlsClient(serverAddress, ClientAuth.REQUIRE, "foo", clientCert.getX509Certificate(),
                              clientCert.getPrivateKey(), CertificateValidator.NONE);
    }

    private Map<String, String> bootstrap() {
        Map<String, String> env = new HashMap<>();
        env.put("GENESIS", "true");
        return env;
    }

    private Map<String, String> kernel() {
        Map<String, String> env = bootstrap();
        env.put("APPROACHES", "bootstrap:8124");
        env.put("SEEDS", "bootstrap:8125#8123");
        return env;
    }

    private Oracle of(SocketAddress endpoint) {
        var client = apiClient(0, (InetSocketAddress) endpoint);
        return new OracleAdapter(client.getChannel());
    }
}
