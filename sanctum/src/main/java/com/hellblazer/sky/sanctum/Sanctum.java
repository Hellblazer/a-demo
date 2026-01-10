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

package com.hellblazer.sky.sanctum;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.hellblazer.delos.cryptography.Digest;
import com.hellblazer.delos.cryptography.SignatureAlgorithm;
import com.hellblazer.delos.membership.stereotomy.ControlledIdentifierMember;
import com.hellblazer.sanctorum.internal.v1.proto.Bytes;
import com.hellblazer.sanctorum.internal.v1.proto.Enclave_Grpc;
import com.hellblazer.sanctorum.internal.v1.proto.FernetValidate;
import com.jauntsdn.netty.channel.vsock.EpollVSockChannel;
import com.macasaet.fernet.Token;
import io.grpc.Channel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessSocketAddress;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.VSockAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.time.Duration;

/**
 * @author hal.hildebrand
 **/
public class Sanctum {
    private final static Logger                            log = LoggerFactory.getLogger(Sanctum.class);
    private final        EnclaveIdentifier                 identifier;
    private final        ControlledIdentifierMember        member;
    private final        Channel                           channel;
    private final        Cache<HashedToken, ByteString>    cached;
    private final        Cache<Digest, Boolean>            invalid;
    private final        Enclave_Grpc.Enclave_BlockingStub client;
    private final        Duration                          tokenCacheTtl;
    private final        Duration                          invalidTokenCacheTtl;

    public Sanctum(SignatureAlgorithm algorithm, SocketAddress enclaveAddress) {
        this(algorithm, channelFor(enclaveAddress), Duration.ofHours(1), Duration.ofHours(1));
    }

    public Sanctum(SignatureAlgorithm algorithm, Channel channel) {
        this(algorithm, channel, Duration.ofHours(1), Duration.ofHours(1));
    }

    public Sanctum(SignatureAlgorithm algorithm, SocketAddress enclaveAddress, Duration tokenCacheTtl,
                   Duration invalidTokenCacheTtl) {
        this(algorithm, channelFor(enclaveAddress), tokenCacheTtl, invalidTokenCacheTtl);
    }

    public Sanctum(SignatureAlgorithm algorithm, Channel channel, Duration tokenCacheTtl,
                   Duration invalidTokenCacheTtl) {
        this.tokenCacheTtl = tokenCacheTtl;
        this.invalidTokenCacheTtl = invalidTokenCacheTtl;
        identifier = new EnclaveIdentifier(algorithm, channel);
        member = new ControlledIdentifierMember(identifier);
        this.channel = channel;
        this.client = Enclave_Grpc.newBlockingStub(channel);
        log.info("Token cache TTL: {}, Invalid token cache TTL: {}", tokenCacheTtl, invalidTokenCacheTtl);
        cached = Caffeine.newBuilder()
                         .maximumSize(1_000)
                         .expireAfterWrite(tokenCacheTtl)
                         .recordStats()
                         .removalListener((HashedToken ht, Object credentials, RemovalCause cause) -> log.trace(
                         "Validated Token: {} was removed due to: {}", ht.hash, cause))
                         .build(hashed -> client.validate(
                         FernetValidate.newBuilder().setToken(hashed.token().serialise()).build()).getB());
        invalid = Caffeine.newBuilder()
                          .maximumSize(1_000)
                          .expireAfterWrite(invalidTokenCacheTtl)
                          .recordStats()
                          .removalListener((Digest token, Boolean credentials, RemovalCause cause) -> log.trace(
                          "Invalid Token: {} was removed due to: {}", token, cause))
                          .build();
    }

    public static Channel channelFor(SocketAddress enclaveAddress) {
        return switch (enclaveAddress) {
            case InProcessSocketAddress ipa -> InProcessChannelBuilder.forAddress(ipa).usePlaintext().build();
            case VSockAddress vs -> NettyChannelBuilder.forAddress(vs)
                                                       .withOption(ChannelOption.TCP_NODELAY, true)
                                                       .eventLoopGroup(new EpollEventLoopGroup())
                                                       .channelType(EpollVSockChannel.class)
                                                       .usePlaintext()
                                                       .build();
            default -> throw new IllegalArgumentException("Unsupported enclave address: " + enclaveAddress);
        };
    }

    public CacheStats cachedStats() {
        return cached.stats();
    }

    public Duration getCachedTokenTtl() {
        return tokenCacheTtl;
    }

    public Duration getInvalidTokenTtl() {
        return invalidTokenCacheTtl;
    }

    public Enclave_Grpc.Enclave_BlockingStub getClient() {
        return Enclave_Grpc.newBlockingStub(channel);
    }

    public Digest getId() {
        return member.getId();
    }

    public ControlledIdentifierMember getMember() {
        return member;
    }

    public CacheStats invalidStats() {
        return invalid.stats();
    }

    /**
     * Log current cache statistics for monitoring purposes
     */
    public void logCacheStatistics() {
        var validStats = cachedStats();
        var invalidStats = invalidStats();
        log.info("Token cache statistics: hits={}, misses={}, requests={}, evictions={}, requests_weight={}, " +
                 "expired={}", validStats.hitCount(), validStats.missCount(), validStats.requestCount(),
                 validStats.evictionCount(), validStats.totalLoadTime(), validStats.loadSuccessCount());
        log.info("Invalid token cache statistics: hits={}, misses={}, requests={}, evictions={}", invalidStats.hitCount(),
                 invalidStats.missCount(), invalidStats.requestCount(), invalidStats.evictionCount());
    }

    public TokenGenerator tokenGenerator() {
        return new TokenGenerator() {
            @Override
            public Token apply(byte[] bytes) {
                return Sanctum.this.apply(bytes);
            }

            @Override
            public ByteString validate(HashedToken hashed) {
                return Sanctum.this.validate(hashed);
            }
        };
    }

    public void unwrap() {
        getClient().unwrap(Empty.getDefaultInstance());
    }

    private Token apply(byte[] bytes) {
        var tok = client.generateToken(Bytes.newBuilder().setB(ByteString.copyFrom(bytes)).build());
        return Token.fromString(tok.getToken());
    }

    private ByteString validate(HashedToken hashed) {
        if (invalid.getIfPresent(hashed.hash()) != null) {
            log.info("Cached invalid Token: {}", hashed.hash());
            return null;
        }
        return cached.get(hashed, h -> {
            var validated = client.validate(FernetValidate.newBuilder().setToken(h.token().serialise()).build());
            log.info("Caching Token: {}", h.hash());
            return validated == null ? null : validated.getB();
        });
    }

    /**
     * This record provides the hash of the Token.serialized() string using the interceptor's DigestAlgorithm
     *
     * @param hash  - the hash of the serialized token String
     * @param token - the deserialized Token
     */
    public record HashedToken(Digest hash, Token token) {
        @Override
        public boolean equals(Object o) {
            if (o instanceof HashedToken ht) {
                return hash.equals(ht.hash);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return hash.hashCode();
        }
    }
}
