/**
 * Copyright (C) 2023 Hal Hildebrand. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 */
package com.hellblazer.nut;

import com.salesforce.apollo.archipelago.EndpointProvider;
import com.salesforce.apollo.archipelago.LocalServer;
import com.salesforce.apollo.archipelago.Router;
import com.salesforce.apollo.archipelago.ServerConnectionCache;
import com.salesforce.apollo.choam.Parameters;
import com.salesforce.apollo.choam.proto.FoundationSeal;
import com.salesforce.apollo.context.DynamicContextImpl;
import com.salesforce.apollo.context.ViewChange;
import com.salesforce.apollo.cryptography.Digest;
import com.salesforce.apollo.cryptography.DigestAlgorithm;
import com.salesforce.apollo.delphinius.Oracle;
import com.salesforce.apollo.fireflies.View;
import com.salesforce.apollo.membership.stereotomy.ControlledIdentifierMember;
import com.salesforce.apollo.model.ProcessDomain;
import com.salesforce.apollo.stereotomy.StereotomyImpl;
import com.salesforce.apollo.stereotomy.mem.MemKERL;
import com.salesforce.apollo.stereotomy.mem.MemKeyStore;
import com.salesforce.apollo.utils.Entropy;
import com.salesforce.apollo.utils.Utils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.salesforce.apollo.choam.Session.retryNesting;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hal.hildebrand
 **/
public class SkyTest {

    private static final int              CARDINALITY     = 5;
    private static final Digest           GENESIS_VIEW_ID = DigestAlgorithm.DEFAULT.digest(
    "Give me food or give me slack or kill me".getBytes());
    private final        List<Sky>        domains         = new ArrayList<>();
    private final        Map<Sky, Router> routers         = new HashMap<>();

    public static void main(String[] argv) throws Exception {
        var t = new SkyTest();
        try {
            t.before();
            t.smokin();
        } finally {
            t.after();
            System.exit(0);
        }
    }

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

            countDown.await(120, TimeUnit.SECONDS);
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
        assertTrue(viewable.contains(object123View), "Should contain: " + object123View);

        // Assert flagged technicians can directly view the document
        Oracle.Assertion grantTechs = flaggedTechnicianMembers.assertion(object123View);
        retryNesting(() -> oracle.add(grantTechs), 3).get(120, TimeUnit.SECONDS);

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
        assertTrue(oracle.check(object123View.assertion(jale)));
        assertTrue(oracle.check(object123View.assertion(egin)));
        assertFalse(oracle.check(object123View.assertion(helpDeskMembers)));

        // Remove them
        retryNesting(() -> oracle.remove(abcTechMembers, technicianMembers), 3).get(120, TimeUnit.SECONDS);

        assertFalse(oracle.check(object123View.assertion(jale)));
        assertTrue(oracle.check(object123View.assertion(egin)));
        assertFalse(oracle.check(object123View.assertion(helpDeskMembers)));

        // Remove our assertion
        retryNesting(() -> oracle.delete(tuple), 3).get(120, TimeUnit.SECONDS);

        assertFalse(oracle.check(object123View.assertion(jale)));
        assertFalse(oracle.check(object123View.assertion(egin)));
        assertFalse(oracle.check(object123View.assertion(helpDeskMembers)));

        // Some deletes
        retryNesting(() -> oracle.delete(abcTechMembers), 3).get(120, TimeUnit.SECONDS);
        retryNesting(() -> oracle.delete(flaggedTechnicianMembers), 3).get(120, TimeUnit.SECONDS);
    }

    @AfterEach
    public void after() {
        domains.forEach(ProcessDomain::stop);
        domains.clear();
        routers.values().forEach(r -> r.close(Duration.ofSeconds(1)));
        routers.clear();
    }

    @BeforeEach
    public void before() throws Exception {

        final var commsDirectory = Path.of("target/comms");
        commsDirectory.toFile().mkdirs();

        var ffParams = com.salesforce.apollo.fireflies.Parameters.newBuilder();
        var entropy = SecureRandom.getInstance("SHA1PRNG");
        entropy.setSeed(new byte[] { 6, 6, 6 });
        final var prefix = UUID.randomUUID().toString();
        Path checkpointDirBase = Path.of("target", "ct-chkpoints-" + Entropy.nextBitsStreamLong());
        Utils.clean(checkpointDirBase.toFile());
        var params = params();
        var stereotomy = new StereotomyImpl(new MemKeyStore(), new MemKERL(params.getDigestAlgorithm()), entropy);

        var identities = IntStream.range(0, CARDINALITY)
                                  .mapToObj(i -> stereotomy.newIdentifier())
                                  .collect(Collectors.toMap(controlled -> controlled.getIdentifier().getDigest(),
                                                            controlled -> controlled));

        Digest group = DigestAlgorithm.DEFAULT.getOrigin();
        var sealed = FoundationSeal.newBuilder().build();
        identities.forEach((digest, id) -> {
            var context = new DynamicContextImpl<>(DigestAlgorithm.DEFAULT.getLast(), CARDINALITY, 0.2, 3);
            final var member = new ControlledIdentifierMember(id);
            var localRouter = new LocalServer(prefix, member).router(ServerConnectionCache.newBuilder().setTarget(30));
            var pdParams = new ProcessDomain.ProcessDomainParameters(
            "jdbc:h2:mem:sql-%s;DB_CLOSE_DELAY=-1".formatted(digest), Duration.ofMinutes(1),
            "jdbc:h2:mem:dht-%s".formatted(digest), checkpointDirBase, Duration.ofMillis(10), 0.00125,
            Duration.ofMinutes(1), 3, 10, 0.1);
            var node = new Sky(group, member, pdParams, params, Parameters.RuntimeParameters.newBuilder()
                                                                                            .setFoundation(sealed)
                                                                                            .setContext(context)
                                                                                            .setCommunications(
                                                                                            localRouter),
                               EndpointProvider.allocatePort(), ffParams, null);
            domains.add(node);
            routers.put(node, localRouter);
            localRouter.start();
        });
    }

    @Test
    public void smokin() throws Exception {
        final var gossipDuration = Duration.ofMillis(10);
        long then = System.currentTimeMillis();
        final var countdown = new CountDownLatch(domains.size());
        final var seeds = Collections.singletonList(
        new View.Seed(domains.getFirst().getMember().getIdentifier().getIdentifier(), EndpointProvider.allocatePort()));
        domains.forEach(d -> {
            Consumer<ViewChange> c = viewChange -> {
                if (viewChange.context().cardinality() == CARDINALITY) {
                    System.out.printf("Full view: %s members: %s on: %s%n", viewChange.diadem(),
                                      viewChange.context().cardinality(), d.getMember().getId());
                    countdown.countDown();
                } else {
                    System.out.printf("Members joining: %s members: %s on: %s%n", viewChange.diadem(),
                                      viewChange.context().cardinality(), d.getMember().getId());
                }
            };
            d.getFoundation().register("SkyTest", c);
        });
        // start seed
        final var started = new AtomicReference<>(new CountDownLatch(1));

        domains.getFirst()
               .getFoundation()
               .start(() -> started.get().countDown(), gossipDuration, Collections.emptyList());
        assertTrue(started.get().await(10, TimeUnit.SECONDS), "Cannot start up kernel");

        started.set(new CountDownLatch(CARDINALITY - 1));
        domains.subList(1, domains.size()).parallelStream().forEach(d -> {
            d.getFoundation().start(() -> started.get().countDown(), gossipDuration, seeds);
        });
        assertTrue(started.get().await(30, TimeUnit.SECONDS), "could not start views: " + (domains.stream()
                                                                                                  .filter(
                                                                                                  c -> !c.active())
                                                                                                  .map(
                                                                                                  d -> d.logState())
                                                                                                  .toList()));

        var joined = countdown.await(30, TimeUnit.SECONDS);
        assertTrue(joined, "Could not join all members in all views: " + (domains.stream()
                                                                                 .filter(c -> !c.active())
                                                                                 .map(d -> d.logState())
                                                                                 .toList()));

        assertTrue(Utils.waitForCondition(60_000, 1_000, () -> {
            return domains.stream().filter(d -> d.getFoundation().getContext().activeCount() != domains.size()).count()
            == 0;
        }));
        System.out.println();
        System.out.println("******");
        System.out.println(
        "View has stabilized in " + (System.currentTimeMillis() - then) + " Ms across all " + domains.size()
        + " members");
        System.out.println("******");
        System.out.println();
        domains.parallelStream().forEach(n -> n.start());
        final var activated = Utils.waitForCondition(60_000, 1_000,
                                                     () -> domains.stream().filter(c -> !c.active()).count() == 0);
        assertTrue(activated, "Domains did not become active : " + (domains.stream()
                                                                           .filter(c -> !c.active())
                                                                           .map(d -> d.logState())
                                                                           .toList()));
        System.out.println();
        System.out.println("******");
        System.out.println(
        "Domains have activated in " + (System.currentTimeMillis() - then) + " Ms across all " + domains.size()
        + " members");
        System.out.println("******");
        System.out.println();
        var oracle = domains.get(0).getDelphi();
        oracle.add(new Oracle.Namespace("test")).get();
        smoke(oracle);
    }

    private Parameters.Builder params() {
        var params = Parameters.newBuilder()
                               .setGenerateGenesis(true)
                               .setGenesisViewId(GENESIS_VIEW_ID)
                               .setGossipDuration(Duration.ofMillis(50))
                               .setProducer(Parameters.ProducerParameters.newBuilder()
                                                                         .setGossipDuration(Duration.ofMillis(50))
                                                                         .setBatchInterval(Duration.ofMillis(100))
                                                                         .setMaxBatchByteSize(1024 * 1024)
                                                                         .setMaxBatchCount(3000)
                                                                         .build())
                               .setCheckpointBlockDelta(200);

        params.getProducer().ethereal().setNumberOfEpochs(5);
        return params;
    }
}
