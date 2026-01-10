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

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hellblazer.delos.archipelago.EndpointProvider;
import com.hellblazer.delos.cryptography.Digest;
import com.hellblazer.delos.cryptography.EncryptionAlgorithm;
import com.hellblazer.delos.cryptography.cert.CertificateWithPrivateKey;
import com.hellblazer.delos.cryptography.ssl.CertificateValidator;
import com.hellblazer.delos.delphinius.Oracle;
import com.hellblazer.delos.utils.Utils;
import com.hellblazer.nut.comms.MtlsClient;
import com.hellblazer.nut.proto.SphinxGrpc;
import com.hellblazer.nut.service.OracleAdapter;
import com.hellblazer.nut.support.ShareService;
import com.hellblazer.sanctorum.proto.EncryptedShare;
import com.hellblazer.sanctorum.proto.Share;
import io.netty.handler.ssl.ClientAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.hellblazer.delos.choam.Session.retryNesting;
import static com.hellblazer.delos.cryptography.QualifiedBase64.qb64;
import static com.hellblazer.sky.constants.Constants.SHAMIR_TAG;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hal.hildebrand
 **/
public class E2ETest {
    private static final Logger log         = LoggerFactory.getLogger(E2ETest.class);
    private final static int    SHARES      = 7;
    private final static int    CARDINALITY = 7;
    private final static int    THRESHOLD   = 4;

    private List<Proc>    processes;
    private List<Sphinx>  sphinxes;
    private AtomicBoolean failures;

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
            retryNesting(() -> oracle.map(helpDeskMembers, adminMembers), 3).whenCompleteAsync((h, _) -> {
                log.info("mapping helpdesk members to admin members @ {}", h.longValue());
                countDown.countDown();
            }, exec);
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
        var t1 = retryNesting(() -> oracle.add(tuple), 3).get(120, TimeUnit.SECONDS);

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
        var t2 = retryNesting(() -> oracle.add(grantTechs), 3).get(120, TimeUnit.SECONDS).ts();

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
        assertTrue(oracle.check(object123View.assertion(jale), t2));
        assertTrue(oracle.check(object123View.assertion(egin), t2));
        assertFalse(oracle.check(object123View.assertion(helpDeskMembers), t2));

        // Remove them
        var t3 = retryNesting(() -> oracle.remove(abcTechMembers, technicianMembers), 3).get(60, TimeUnit.SECONDS);

        assertFalse(oracle.check(object123View.assertion(jale), t3));
        assertTrue(oracle.check(object123View.assertion(egin), t3));
        assertFalse(oracle.check(object123View.assertion(helpDeskMembers), t3));

        // Remove our assertion (userMembers -> object123View)
        // Use the deletion timestamp for subsequent checks, not t3 (which was before the deletion)
        var t4 = retryNesting(() -> oracle.delete(tuple), 3).get(20, TimeUnit.SECONDS);

        // After deleting tuple, check at the NEW timestamp t4 (not the old t3)
        // jale and egin both lose access via userMembers since tuple was deleted
        assertFalse(oracle.check(object123View.assertion(jale), t4));
        assertFalse(oracle.check(object123View.assertion(egin), t4));
        assertFalse(oracle.check(object123View.assertion(helpDeskMembers), t4));

        // Some deletes
        retryNesting(() -> oracle.delete(abcTechMembers), 3).get(20, TimeUnit.SECONDS);
        retryNesting(() -> oracle.delete(flaggedTechnicianMembers), 3).get(20, TimeUnit.SECONDS);
    }

    @AfterEach
    public void after() {
        if (sphinxes != null) {
            for (Sphinx sphinx : sphinxes) {
                sphinx.shutdown();
            }
        }
        sphinxes = null;
        if (processes != null) {
            processes = null;
        }
    }

    @BeforeEach
    public void before() {
        Utils.clean(new File("target/e2e"));
        failures = new AtomicBoolean();
    }

    @Test
    public void smokin() throws Exception {
        var secretByteSize = 1024;
        var shares = initialize(secretByteSize);
        var seed = sphinxes.getFirst();
        System.out.println();
        System.out.println("** Starting m 1 (seed)");
        System.out.println();
        var seedStart = seed.start();
        var identifier = qb64(unwrap(0, seed, shares, EncryptionAlgorithm.DEFAULT, SHAMIR_TAG));
        seedStart.get(60, TimeUnit.SECONDS);

        initializeKernel(identifier);
        initializeRest(identifier);

        // Bring up a minimal quorum
        var kernel = sphinxes.subList(1, 4);
        var m = new AtomicInteger(1);
        kernel.parallelStream().forEach(s -> {
            var mi = m.incrementAndGet() - 1;
            System.out.println();
            System.out.println("** Starting m " + (mi + 1));
            System.out.println();
            var start = s.start();
            start.whenComplete((v, t) -> System.out.format("** Member %s has joined the view\n", (mi + 1)));
            unwrap(mi, s, shares, EncryptionAlgorithm.DEFAULT, SHAMIR_TAG);
            System.out.println();
            System.out.format("** Member: %s started\n", (mi + 1));
            System.out.println();
        });

        System.out.println();
        System.out.println("** Minimal quorum have started their views");
        System.out.println();

        var domains = sphinxes.subList(0, 4);
        Utils.waitForCondition(120_000, 1_000, () -> failures.get() || domains.stream().allMatch(Sphinx::active));
        assertTrue(domains.stream().allMatch(Sphinx::active),
                   "** Minimal quorum did not become active : " + (domains.stream()
                                                                          .filter(c -> !c.active())
                                                                          .map(Sphinx::logState)
                                                                          .toList()));
        System.out.println();
        System.out.println("** Minimal quorum is active: " + domains.stream().map(Sphinx::id).toList());
        System.out.println();

        // Bring up the rest of the nodes.
        var remaining = sphinxes.subList(4, sphinxes.size());
        remaining.parallelStream().forEach(s -> {
            var mi = m.incrementAndGet() - 1;
            System.out.println();
            System.out.println("** Starting m " + (mi + 1));
            System.out.println();
            var start = s.start();
            start.whenComplete((v, t) -> System.out.format("** %s has joined the view\n", (mi + 1)));
            unwrap(mi, s, shares, EncryptionAlgorithm.DEFAULT, SHAMIR_TAG);
            System.out.println();
            System.out.format("** Member: %s has been started\n", (mi + 1));
            System.out.println();
        });

        Utils.waitForCondition(300_000, 1_000, () -> failures.get() || sphinxes.stream().allMatch(Sphinx::active));
        if (!sphinxes.stream().allMatch(Sphinx::active)) {
            System.out.println();
            fail("\n\nNodes did not fully activate: \n" + (sphinxes.stream()
                                                                   .filter(c -> !c.active())
                                                                   .map(Sphinx::logState)
                                                                   .map(s -> "\t" + s + "\n")
                                                                   .toList()) + "\n\n");
        }
        System.out.println();
        System.out.println("** All nodes are active");
        System.out.println();

        Thread.sleep(1000);
        var oracle = of(sphinxes.getFirst().getServiceEndpoint());
        assertTrue(sphinxes.getFirst().active());
        oracle.add(new Oracle.Namespace("test")).get(20, TimeUnit.SECONDS);
        smoke(oracle);
    }

    private MtlsClient apiClient(int i, InetSocketAddress serverAddress) {
        CertificateWithPrivateKey clientCert = Utils.getMember(i);

        return new MtlsClient(serverAddress, ClientAuth.REQUIRE, "foo", clientCert.getX509Certificate(),
                              clientCert.getPrivateKey(), CertificateValidator.NONE);
    }

    private InputStream configFor(STGroup g, Proc process, List<String> approach, String seed, String seedId,
                                  boolean genesis) {
        var t = g.getInstanceOf("sky");
        t.add("clusterEndpoint", process.clusterEndpoint);
        t.add("apiEndpoint", process.apiEndpoint);
        t.add("approachEndpoint", process.approachEndpoint);
        t.add("serviceEndpoint", process.serviceEndpoint);
        t.add("memberId", process.memberId);
        t.add("approach", approach);
        t.add("seedEndpoint", seed);
        t.add("seedId", seedId);
        t.add("n", SHARES);
        t.add("k", THRESHOLD);
        t.add("genesis", genesis);
        var rendered = t.render();
        return new ByteArrayInputStream(rendered.getBytes(Charset.defaultCharset()));
    }

    private List<Share> initialize(int secretByteSize) {
        STGroup g = new STGroupFile("src/test/resources/sky.stg");

        var algorithm = EncryptionAlgorithm.DEFAULT;
        var entropy = new SecureRandom();
        var secrets = new ShareService(entropy, algorithm);
        var keys = IntStream.range(0, SHARES).mapToObj(i -> algorithm.generateKeyPair()).toList();
        var encryptedShares = secrets.shares(secretByteSize, keys.stream().map(KeyPair::getPublic).toList(), THRESHOLD);
        var shares = IntStream.range(0, SHARES).mapToObj(i -> share(i, algorithm, keys, encryptedShares)).toList();
        processes = IntStream.range(0, CARDINALITY)
                             .mapToObj(i -> new com.hellblazer.nut.E2ETest.Proc(EndpointProvider.allocatePort(), i,
                                                                                EndpointProvider.allocatePort(),
                                                                                EndpointProvider.allocatePort(),
                                                                                EndpointProvider.allocatePort()))
                             .toList();
        com.hellblazer.nut.E2ETest.Proc first = processes.getFirst();
        var seed = new Sphinx(configFor(g, first, null, null, null, true));
        sphinxes = new ArrayList<>();
        sphinxes.add(seed);
        return shares;
    }

    private void initializeKernel(String seedId) {
        STGroup g = new STGroupFile("src/test/resources/sky.stg");

        var first = processes.getFirst();
        processes.subList(1, 4)
                 .stream()
                 .map(
                 p -> configFor(g, p, Collections.singletonList(first.approachEndpoint), first.clusterEndpoint, seedId,
                                true))
                 .map(c -> {
                     var sphinx = new Sphinx(c);
                     sphinx.setOnFailure(new CompletableFuture<Void>().whenComplete((v, t) -> failures.set(true)));
                     return sphinx;
                 })
                 .forEach(s -> sphinxes.add(s));
    }

    private void initializeRest(String seedId) {
        STGroup g = new STGroupFile("src/test/resources/sky.stg");

        com.hellblazer.nut.E2ETest.Proc first = processes.getFirst();
        processes.subList(4, CARDINALITY)
                 .stream()
                 .map(
                 p -> configFor(g, p, Collections.singletonList(first.approachEndpoint), first.clusterEndpoint, seedId,
                                false))
                 .map(c -> {
                     var sphinx = new Sphinx(c);
                     sphinx.setOnFailure(new CompletableFuture<Void>().whenComplete((v, t) -> failures.set(true)));
                     return sphinx;
                 })
                 .forEach(s -> sphinxes.add(s));
    }

    private Oracle of(SocketAddress endpoint) {
        var client = apiClient(0, (InetSocketAddress) endpoint);
        return new OracleAdapter(client.getChannel());
    }

    private Share share(int i, EncryptionAlgorithm algorithm, List<KeyPair> keys,
                        List<EncryptedShare> encryptedShares) {
        i = i % SHARES;
        var key = algorithm.decapsulate(keys.get(i).getPrivate(),
                                        encryptedShares.get(i).getEncapsulation().toByteArray(), Sphinx.AES);
        var encrypted = new Sphinx.Encrypted(encryptedShares.get(i).getShare().toByteArray(),
                                             encryptedShares.get(i).getIv().toByteArray(), SHAMIR_TAG);
        var plainText = Sphinx.decrypt(encrypted, key);
        try {
            return Share.parseFrom(plainText);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private Digest unwrap(int i, Sphinx sphinx, List<Share> shares, EncryptionAlgorithm algorithm,
                          byte[] associatedData) {
        var client = apiClient(i, (InetSocketAddress) sphinx.getApiEndpoint());

        try {
            var sphinxClient = SphinxGrpc.newBlockingStub(client.getChannel());
            var status = sphinxClient.unseal(Empty.getDefaultInstance());

            assertNotNull(status);
            assertTrue(status.getSuccess());
            assertEquals(0, status.getShares());

            var publicKey_ = sphinxClient.sessionKey(Empty.getDefaultInstance());
            assertNotNull(publicKey_);

            var publicKey = EncryptionAlgorithm.lookup(publicKey_.getAlgorithmValue())
                                               .publicKey(publicKey_.getPublicKey().toByteArray());
            assertNotNull(publicKey);

            var encapsulated = algorithm.encapsulated(publicKey);

            var secretKey = new SecretKeySpec(encapsulated.key().getEncoded(), "AES");

            int count = 0;
            var selected = new ArrayList<>(shares);
            Collections.shuffle(selected);
            var present = THRESHOLD + 1;
            for (var wrapped : selected.subList(0, present)) {
                var encrypted = Sphinx.encrypt(wrapped.toByteArray(), secretKey, associatedData);
                var encryptedShare = EncryptedShare.newBuilder()
                                                   .setIv(ByteString.copyFrom(encrypted.iv()))
                                                   .setShare(ByteString.copyFrom(encrypted.cipherText()))
                                                   .setEncapsulation(ByteString.copyFrom(encapsulated.encapsulation()))
                                                   .build();
                var result = sphinxClient.apply(encryptedShare);
                count++;
                assertEquals(count, result.getShares());
            }

            var unwrapStatus = sphinxClient.unwrap(Empty.getDefaultInstance());
            assertTrue(unwrapStatus.getSuccess());
            assertEquals(present, unwrapStatus.getShares());
            return Digest.from(unwrapStatus.getIdentifier());
        } finally {
            client.stop();
        }
    }

    public record Proc(String clusterEndpoint, int memberId, String apiEndpoint, String approachEndpoint,
                       String serviceEndpoint) {
    }
}
