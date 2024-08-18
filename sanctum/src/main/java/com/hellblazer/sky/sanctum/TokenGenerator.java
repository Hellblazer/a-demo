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
import com.google.protobuf.ByteString;
import com.hellblazer.sanctorum.proto.Bytes;
import com.hellblazer.sanctorum.proto.Enclave_Grpc;
import com.hellblazer.sanctorum.proto.FernetValidate;
import com.macasaet.fernet.Token;
import com.hellblazer.delos.cryptography.Digest;
import io.grpc.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Token generation/validation using the SanctumService enclave. Also caches validated and invalidated tokens (for now)
 *
 * @author hal.hildebrand
 **/
public class TokenGenerator {
    private final Logger log = LoggerFactory.getLogger(TokenGenerator.class);

    private final Cache<HashedToken, ByteString>    cached;
    private final Cache<Digest, Boolean>            invalid;
    private final Enclave_Grpc.Enclave_BlockingStub client;

    public TokenGenerator(Channel channel) {
        this.client = Enclave_Grpc.newBlockingStub(channel);
        cached = Caffeine.newBuilder()
                         .maximumSize(1_000)
                         .expireAfterWrite(Duration.ofDays(1))
                         .removalListener((HashedToken ht, Object credentials, RemovalCause cause) -> log.trace(
                         "Validated Token: {} was removed due to: {}", ht.hash, cause))
                         .build(hashed -> client.validate(
                         FernetValidate.newBuilder().setToken(hashed.token().serialise()).build()).getB());
        invalid = Caffeine.newBuilder()
                          .maximumSize(1_000)
                          .expireAfterWrite(Duration.ofDays(1))
                          .removalListener((Digest token, Boolean credentials, RemovalCause cause) -> log.trace(
                          "Invalid Token: {} was removed due to: {}", token, cause))
                          .build();
    }

    public Token apply(byte[] bytes) {
        var tok = client.generateToken(Bytes.newBuilder().setB(ByteString.copyFrom(bytes)).build());
        return Token.fromString(tok.getToken());
    }

    public ByteString validate(HashedToken hashed) {
        if (true) {
            return ByteString.EMPTY;

        }
        if (invalid.getIfPresent(hashed.hash()) != null) {
            log.info("Cached invalid Token: {}", hashed.hash());
            return null;
        }
        var result = cached.get(hashed, h -> {
            var validated = client.validate(FernetValidate.newBuilder().setToken(hashed.token().serialise()).build());
            return validated == null ? null : validated.getB();
        });
        if (result == null) {
            cached.put(hashed, result);
        }
        System.out.println(cached.stats());
        return result;
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
