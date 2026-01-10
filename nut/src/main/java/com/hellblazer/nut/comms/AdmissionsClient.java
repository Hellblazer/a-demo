/*
 * Copyright (c) 2022-2024 Hal Hildebrand. All rights reserved.
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

import com.hellblazer.delos.gorgoneion.client.client.comm.Admissions;
import com.hellblazer.delos.gorgoneion.client.client.comm.GorgoneionClientMetrics;
import com.hellblazer.delos.gorgoneion.proto.AdmissionsGrpc;
import com.hellblazer.delos.gorgoneion.proto.Credentials;
import com.hellblazer.delos.gorgoneion.proto.Establishment;
import com.hellblazer.delos.gorgoneion.proto.SignedNonce;
import com.hellblazer.delos.membership.Member;
import com.hellblazer.delos.stereotomy.event.proto.KERL_;
import io.grpc.ManagedChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author hal.hildebrand
 */
public class AdmissionsClient implements Admissions {

    private static final Logger                         log     = LoggerFactory.getLogger(AdmissionsClient.class);
    private static final Duration                       CLOSE_TIMEOUT = Duration.ofSeconds(10);
    private final        Lock                           closeLock     = new ReentrantLock();
    private final        ManagedChannel                 channel;
    private final        Member                         member;
    private final        AdmissionsGrpc.AdmissionsBlockingStub client;
    private final        GorgoneionClientMetrics        metrics;
    private volatile     boolean                        closed = false;

    public AdmissionsClient(Member member, ManagedChannel channel, GorgoneionClientMetrics metrics) {
        this.member = member;
        this.channel = channel;
        this.client = AdmissionsGrpc.newBlockingStub(channel).withCompression("gzip");
        this.metrics = metrics;
    }

    public SignedNonce apply(KERL_ application, Duration timeout) {
        if (metrics != null) {
            var serializedSize = application.getSerializedSize();
            metrics.outboundBandwidth().mark(serializedSize);
            metrics.outboundApplication().update(serializedSize);
        }

        SignedNonce result = client.withDeadlineAfter(timeout.toNanos(), TimeUnit.NANOSECONDS).apply(application);
        if (metrics != null) {
            var serializedSize = result.getSerializedSize();
            metrics.inboundBandwidth().mark(serializedSize);
            metrics.inboundApplication().update(serializedSize);
        }
        return result;
    }

    public void close() {
        closeLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            try {
                channel.shutdown();
                if (!channel.awaitTermination(CLOSE_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS)) {
                    log.warn("AdmissionsClient channel shutdown timeout for {} after {}, forcing termination",
                             member.getId(), CLOSE_TIMEOUT);
                    channel.shutdownNow();
                    if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.error("AdmissionsClient channel failed to terminate for {}", member.getId());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("AdmissionsClient channel shutdown interrupted for {}", member.getId(), e);
                channel.shutdownNow();
            }
        } finally {
            closeLock.unlock();
        }
    }

    public Member getMember() {
        return member;
    }

    public Establishment register(Credentials credentials, Duration timeout) {
        if (metrics != null) {
            var serializedSize = credentials.getSerializedSize();
            metrics.outboundBandwidth().mark(serializedSize);
            metrics.outboundCredentials().update(serializedSize);
        }

        var result = client.withDeadlineAfter(timeout.toNanos(), TimeUnit.NANOSECONDS).register(credentials);
        if (metrics != null) {
            try {
                var serializedSize = result.getSerializedSize();
                metrics.inboundBandwidth().mark(serializedSize);
                metrics.inboundInvitation().update(serializedSize);
            } catch (Throwable e) {
                log.trace("Error updating metrics for establishment: {}", member.getId(), e);
            }
        }
        return result;
    }
}
