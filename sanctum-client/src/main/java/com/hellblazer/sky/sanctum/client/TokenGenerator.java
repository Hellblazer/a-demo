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

package com.hellblazer.sky.sanctum.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.google.protobuf.ByteString;
import com.hellblazer.sanctorum.proto.Enclave_Grpc;
import com.hellblazer.sanctorum.proto.FernetToken;
import com.hellblazer.sanctorum.proto.FernetValidate;
import com.macasaet.fernet.Token;
import com.salesforce.apollo.cryptography.Digest;
import io.grpc.ManagedChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * @author hal.hildebrand
 **/
public class TokenGenerator {
    private final Logger log = LoggerFactory.getLogger(TokenGenerator.class);

    private final Cache<HashedToken, ByteString>    cached;
    private final Cache<Digest, Boolean>            invalid;
    private final Enclave_Grpc.Enclave_BlockingStub client;

    public TokenGenerator(ManagedChannel channel) {
        this.client = Enclave_Grpc.newBlockingStub(channel);
        cached = Caffeine.newBuilder()
                         .maximumSize(1_000)
                         .expireAfterWrite(Duration.ofDays(10))
                         .removalListener((HashedToken token, Object credentials, RemovalCause cause) -> log.trace(
                         "Validated Token: {} was removed due to: {}", token.hash(), cause))
                         .build();
        invalid = Caffeine.newBuilder()
                          .maximumSize(1_000)
                          .expireAfterWrite(Duration.ofSeconds(30))
                          .removalListener((Digest token, Boolean credentials, RemovalCause cause) -> log.trace(
                          "Invalid Token: {} was removed due to: {}", token, cause))
                          .build();
    }

    public boolean valid(HashedToken hashed) {
        if (invalid.getIfPresent(hashed.hash()) != null) {
            return false;
        }
        if (cached.getIfPresent(hashed) != null) {
            return true;
        }
        if (!client.verifyToken(FernetToken.newBuilder().setToken(hashed.token.serialise()).build()).getVerified()) {
            invalid.put(hashed.hash(), true);
            return false;
        }
        return true;
    }

    public ByteString validate(HashedToken hashed) {
        if (invalid.getIfPresent(hashed.hash()) != null) {
            log.info("Cached invalid Token: {}", hashed.hash());
            return null;
        }
        return cached.get(hashed, k -> client.validate(FernetValidate.newBuilder().build()).getB());
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
