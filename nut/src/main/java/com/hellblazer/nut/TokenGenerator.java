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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.google.protobuf.Message;
import com.macasaet.fernet.Key;
import com.macasaet.fernet.Token;
import com.macasaet.fernet.Validator;
import com.salesforce.apollo.archipelago.server.FernetServerInterceptor;
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
    private static final Logger                                             log = LoggerFactory.getLogger(
    TokenGenerator.class);
    private final        SecureRandom                                       entropy;
    private final        Cache<FernetServerInterceptor.HashedToken, Object> cached;
    private final        Cache<Digest, Boolean>                             invalid;
    private volatile     Key                                                master;

    public TokenGenerator(java.security.Key master, SecureRandom entropy) {
        this(new Key(master.getEncoded()), entropy);
    }

    public TokenGenerator(Key master, SecureRandom entropy) {
        this.master = master;
        this.entropy = entropy;
        cached = Caffeine.newBuilder()
                         .maximumSize(1_000)
                         .expireAfterWrite(Duration.ofMinutes(10))
                         .removalListener(
                         (FernetServerInterceptor.HashedToken token, Object credentials, RemovalCause cause) -> log.trace(
                         "Validated Token: {} was removed due to: {}", token.hash(), cause))
                         .build();
        invalid = Caffeine.newBuilder()
                          .maximumSize(1_000)
                          .expireAfterWrite(Duration.ofMinutes(10))
                          .removalListener((Digest token, Boolean credentials, RemovalCause cause) -> log.trace(
                          "Invalid Token: {} was removed due to: {}", token, cause))
                          .build();
    }

    @Override
    public Token apply(Message message) {
        return master != null ? Token.generate(entropy, master, message.toByteArray()) : null;
    }

    public <T> T validate(FernetServerInterceptor.HashedToken hashed, Validator<T> validator) {
        return (T) cached.get(hashed,
                              k -> master != null ? k.token().validateAndDecrypt(master, validator) : null);
    }

    void clear() {
        master = null;
    }
}
