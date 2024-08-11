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
import com.google.protobuf.Message;
import com.macasaet.fernet.Key;
import com.macasaet.fernet.Token;
import com.macasaet.fernet.TokenValidationException;
import com.macasaet.fernet.Validator;
import com.salesforce.apollo.cryptography.Digest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.function.Function;

/**
 * @author hal.hildebrand
 **/
public class TokenGenerator implements Function<Message, Token> {
    private static final Logger log = LoggerFactory.getLogger(TokenGenerator.class);

    private final    SecureRandom                entropy;
    private final    Cache<HashedToken, Message> cached;
    private final    Cache<Digest, Boolean>      invalid;
    private volatile Key                         master;

    public TokenGenerator(java.security.Key master, SecureRandom entropy) {
        this(new Key(master.getEncoded()), entropy);
    }

    public TokenGenerator(Key master, SecureRandom entropy) {
        this.master = master;
        this.entropy = entropy;
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

    @Override
    public Token apply(Message message) {
        return master != null ? Token.generate(entropy, master, message.toByteArray()) : null;
    }

    public void clear() {
        master = null;
    }

    public String shared() {
        return master.serialise();
    }

    public boolean valid(HashedToken hashed) {
        if (invalid.getIfPresent(hashed.hash()) != null) {
            return false;
        }
        if (cached.getIfPresent(hashed) != null) {
            return true;
        }
        if (!hashed.token().isValidSignature(master)) {
            invalid.put(hashed.hash(), true);
            return false;
        }
        return true;
    }

    public Message validate(HashedToken hashed, Validator<Message> validator) {
        if (invalid.getIfPresent(hashed.hash()) != null) {
            log.info("Cached invalid Token: {}", hashed.hash());
            return null;
        }
        return cached.get(hashed, k -> master != null ? validate(validator, k) : null);
    }

    private Message validate(Validator<Message> validator, HashedToken k) {
        try {
            var decrypt = k.token().validateAndDecrypt(master, validator);
            log.info("Decrypted Token: {} was cached: {}", k, decrypt);
            return decrypt;
        } catch (TokenValidationException e) {
            log.info("Invalid Token: {}", k.hash());
            invalid.put(k.hash(), Boolean.TRUE);
            return null;
        }
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
