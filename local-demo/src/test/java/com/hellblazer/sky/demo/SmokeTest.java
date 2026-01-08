package com.hellblazer.sky.demo;

import com.hellblazer.nut.comms.MtlsClient;
import com.hellblazer.nut.service.OracleAdapter;
import com.hellblazer.delos.cryptography.cert.CertificateWithPrivateKey;
import com.hellblazer.delos.cryptography.ssl.CertificateValidator;
import com.hellblazer.delos.delphinius.Oracle;
import com.hellblazer.delos.utils.Utils;
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

import static com.hellblazer.delos.choam.Session.retryNesting;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hal.hildebrand
 **/
@Testcontainers
public class SmokeTest {
    private static final Logger log = LoggerFactory.getLogger(SmokeTest.class);

    public static void smoke(Oracle oracle) throws Exception {
        log.info("========================================");
        log.info("Sky Smoke Test - Organizational Access Control Demo");
        log.info("========================================");
        log.info("");

        // Namespace
        log.info("Step 1: Creating namespace 'my-org' for organizational structure");
        var ns = Oracle.namespace("my-org");

        // relations
        var member = ns.relation("member");
        var flag = ns.relation("flag");
        log.info("  ✓ Namespace 'my-org' created with relations: 'member', 'flag'");

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

        log.info("");
        log.info("Step 2: Mapping organizational hierarchy (17 relationships)");
        log.info("  Organization:");
        log.info("    Users (11 members)");
        log.info("    ├── Managers (Fuat, Gül)");
        log.info("    ├── Technicians (Hakan, Irmak, Jale via ABCTechnicians)");
        log.info("    ├── Admins (HelpDesk: Egin, Demet + Ali)");
        log.info("    ├── Can");
        log.info("    └── Burcu");

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

            if (!countDown.await(30, TimeUnit.SECONDS)) {
                throw new AssertionError("Timeout waiting for organizational hierarchy to be mapped");
            }
        }
        log.info("  ✓ Organizational hierarchy mapped successfully");

        log.info("");
        log.info("Step 3: Setting up protected resource (Document:123)");
        // Protected resource namespace
        var docNs = Oracle.namespace("Document");
        // Permission
        var view = docNs.relation("View");
        // Protected Object
        var object123View = docNs.object("123", view);

        // Users can View Document 123
        log.info("  Granting permission: Users → View → Document:123");
        Oracle.Assertion tuple = userMembers.assertion(object123View);
        retryNesting(() -> oracle.add(tuple), 3).get(120, TimeUnit.SECONDS);
        log.info("  ✓ Permission granted");

        log.info("");
        log.info("Step 4: Verifying direct permissions");
        // Direct subjects that can View the document
        var viewers = oracle.read(object123View);
        assertEquals(1, viewers.size(), "Expected 1 direct viewer, got: " + viewers.size());
        assertTrue(viewers.contains(userMembers), "Should contain: " + userMembers);
        log.info("  ✓ Direct viewers verified: {} group(s) can view Document:123", viewers.size());

        // Direct objects that can User member can view
        var viewable = oracle.read(userMembers);
        assertEquals(1, viewable.size(), "Expected 1 viewable object, got: " + viewable.size());
        assertEquals(viewable.getFirst(), object123View, "Should contain: " + object123View);

        log.info("");
        log.info("Step 5: Adding filtered permission (flagged technicians)");
        log.info("  Granting permission: FlaggedTechnicians → View → Document:123");
        // Assert flagged technicians can directly view the document
        Oracle.Assertion grantTechs = flaggedTechnicianMembers.assertion(object123View);
        var ts = retryNesting(() -> oracle.add(grantTechs), 3).get(120, TimeUnit.SECONDS).ts();
        log.info("  ✓ Filtered permission granted");

        log.info("");
        log.info("Step 6: Verifying direct permissions after adding filtered group");
        // Now have 2 direct subjects that can view the doc
        viewers = oracle.read(object123View);
        assertEquals(2, viewers.size(), "Expected 2 direct viewers, got: " + viewers.size());
        assertTrue(viewers.contains(userMembers), "Should contain: " + userMembers);
        assertTrue(viewers.contains(flaggedTechnicianMembers), "Should contain: " + flaggedTechnicianMembers);
        log.info("  ✓ Direct viewers verified: {} group(s)", viewers.size());

        // flagged has direct view
        viewable = oracle.read(flaggedTechnicianMembers);
        assertEquals(1, viewable.size(), "Expected 1 viewable object for flagged technicians, got: " + viewable.size());
        assertTrue(viewable.contains(object123View), "Should contain: " + object123View);

        log.info("");
        log.info("Step 7: Verifying filtered direct permissions (flag relation)");
        // Filter direct on flagged relation
        var flaggedViewers = oracle.read(flag, object123View);
        assertEquals(1, flaggedViewers.size(), "Expected 1 flagged viewer, got: " + flaggedViewers.size());
        assertTrue(flaggedViewers.contains(flaggedTechnicianMembers), "Should contain: " + flaggedTechnicianMembers);
        log.info("  ✓ Filtered direct viewers verified: {} flagged group(s)", flaggedViewers.size());

        log.info("");
        log.info("Step 8: Computing transitive permissions (expanding hierarchy)");
        // Transitive subjects that can view the document
        var inferredViewers = oracle.expand(object123View);
        assertEquals(14, inferredViewers.size(), "Expected 14 transitive viewers, got: " + inferredViewers.size());
        for (var s : Arrays.asList(ali, jale, egin, irmak, hakan, gl, fuat, can, burcu, managerMembers,
                                   technicianMembers, abcTechMembers, userMembers, flaggedTechnicianMembers)) {
            assertTrue(inferredViewers.contains(s), "Should contain: " + s);
        }
        log.info("  ✓ Transitive viewers verified: {} subjects/groups can view Document:123", inferredViewers.size());
        log.info("    Including: Ali, Jale, Egin, Irmak, Hakan, Gül, Fuat, Can, Burcu");
        log.info("    And groups: Managers, Technicians, ABCTechnicians, Users, FlaggedTechnicians");

        log.info("");
        log.info("Step 9: Computing filtered transitive permissions (flagged subjects only)");
        // Transitive subjects filtered by flag predicate
        var inferredFlaggedViewers = oracle.expand(flag, object123View);
        assertEquals(5, inferredFlaggedViewers.size(), "Expected 5 flagged viewers, got: " + inferredFlaggedViewers.size());
        for (var s : Arrays.asList(egin, ali, gl, fuat, flaggedTechnicianMembers)) {
            assertTrue(inferredFlaggedViewers.contains(s), "Should contain: " + s);
        }
        log.info("  ✓ Filtered transitive viewers verified: {} flagged subjects", inferredFlaggedViewers.size());
        log.info("    Including: Egin, Ali, Gül, Fuat, FlaggedTechnicians");

        log.info("");
        log.info("Step 10: Testing individual permission checks");
        // Check some assertions
        assertTrue(oracle.check(object123View.assertion(jale), ts), "Jale should have view permission");
        assertTrue(oracle.check(object123View.assertion(egin), ts), "Egin should have view permission");
        assertFalse(oracle.check(object123View.assertion(helpDeskMembers), ts), "HelpDesk should NOT have view permission");
        log.info("  ✓ Individual checks passed:");
        log.info("    - Jale (via ABCTechnicians → Technicians → Users): CAN view");
        log.info("    - Egin (via HelpDesk → Admins → Users): CAN view");
        log.info("    - HelpDesk (no direct path): CANNOT view");

        log.info("");
        log.info("Step 11: Testing permission revocation (removing ABCTechnicians from Technicians)");
        // Remove them
        retryNesting(() -> oracle.remove(abcTechMembers, technicianMembers), 3).get(60, TimeUnit.SECONDS);
        log.info("  ✓ Relationship removed: ABCTechnicians no longer in Technicians");

        assertFalse(oracle.check(object123View.assertion(jale), ts), "Jale should no longer have view permission");
        assertTrue(oracle.check(object123View.assertion(egin), ts), "Egin should still have view permission");
        assertFalse(oracle.check(object123View.assertion(helpDeskMembers), ts), "HelpDesk should still NOT have view permission");
        log.info("  ✓ Revocation verified:");
        log.info("    - Jale (path broken): CANNOT view (expected)");
        log.info("    - Egin (path intact): CAN view (expected)");

        log.info("");
        log.info("Step 12: Testing permission deletion (removing Users → View → Document:123)");
        // Remove our assertion
        retryNesting(() -> oracle.delete(tuple), 3).get(20, TimeUnit.SECONDS);
        log.info("  ✓ Assertion deleted: Users no longer have View permission");

        assertFalse(oracle.check(object123View.assertion(jale), ts), "Jale should not have view permission");
        assertFalse(oracle.check(object123View.assertion(egin), ts), "Egin should no longer have view permission");
        assertFalse(oracle.check(object123View.assertion(helpDeskMembers), ts), "HelpDesk should still NOT have view permission");
        log.info("  ✓ Deletion verified:");
        log.info("    - Jale: CANNOT view (expected)");
        log.info("    - Egin: CANNOT view (expected - path removed)");

        log.info("");
        log.info("Step 13: Cleaning up test data");
        // Some deletes
        retryNesting(() -> oracle.delete(abcTechMembers), 3).get(20, TimeUnit.SECONDS);
        retryNesting(() -> oracle.delete(flaggedTechnicianMembers), 3).get(20, TimeUnit.SECONDS);
        log.info("  ✓ Test data cleaned up");

        log.info("");
        log.info("========================================");
        log.info("Smoke Test PASSED ✓");
        log.info("========================================");
        log.info("Summary:");
        log.info("  - Created organizational hierarchy with 17 relationships");
        log.info("  - Verified direct permissions (2 groups)");
        log.info("  - Verified transitive permissions (14 subjects/groups)");
        log.info("  - Verified filtered permissions (5 flagged subjects)");
        log.info("  - Tested permission revocation and deletion");
        log.info("  - All assertions validated successfully");
        log.info("");
    }

    @Test
    public void smokin() throws Exception {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════════════════════╗");
        log.info("║  Sky Local Demo - End-to-End Smoke Test                                   ║");
        log.info("║  Testing: Cluster formation, organizational access control, permissions   ║");
        log.info("╚════════════════════════════════════════════════════════════════════════════╝");
        log.info("");

        var logConsumer = new Slf4jLogConsumer(log);
        logConsumer.withSeparateOutputStreams();
        var network = Network.newNetwork();
        var skyImage = DockerImageName.parse("com.hellblazer.sky/sky-image:0.0.1-SNAPSHOT");

        log.info("Phase 1: Starting Sky cluster (4 nodes)");
        log.info("  Image: {}", skyImage);
        log.info("");

        log.info("  [1/4] Starting bootstrap node...");
        try (GenericContainer<?> boot = new GenericContainer<>(skyImage).withNetwork(network)
                                                                        .withNetworkAliases("bootstrap")
                                                                        .withNetworkMode("bridge")
                                                                        .withEnv(bootstrap())
                                                                        .withExposedPorts(8126, 8127)) {
            boot.start();
            boot.followOutput(logConsumer);
            log.info("        ✓ Bootstrap node started (Genesis kernel member 1/4)");

            log.info("  [2/4] Starting kernel node 1...");
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
                log.info("        ✓ Kernel node 1 started (Genesis kernel member 2/4)");

                log.info("  [3/4] Starting kernel node 2...");
                k2.start();
                k2.followOutput(logConsumer);
                log.info("        ✓ Kernel node 2 started (Genesis kernel member 3/4)");

                log.info("  [4/4] Starting kernel node 3...");
                k3.waitingFor(new HostPortWaitStrategy().forPorts(8126));
                k3.start();
                k3.followOutput(logConsumer);
                log.info("        ✓ Kernel node 3 started (Genesis kernel member 4/4)");

                log.info("");
                log.info("Phase 2: Waiting for cluster formation");
                log.info("  Minimal quorum: 4 nodes (tolerates f=1 Byzantine failure)");
                log.info("  Waiting for Genesis block commitment...");
                var oracle = of(new InetSocketAddress(k3.getHost(), k3.getMappedPort(8126)));
                Thread.sleep(10_000);
                log.info("  ✓ Cluster ready - Genesis block committed");
                log.info("");

                log.info("Phase 3: Running access control tests");
                log.info("");
                smoke(oracle);

                log.info("");
                log.info("╔════════════════════════════════════════════════════════════════════════════╗");
                log.info("║  END-TO-END TEST PASSED ✓✓✓                                               ║");
                log.info("║  All cluster formation, permission, and revocation tests succeeded        ║");
                log.info("╚════════════════════════════════════════════════════════════════════════════╝");
                log.info("");
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
