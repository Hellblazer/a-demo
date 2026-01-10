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

package com.hellblazer.sky.sanctum.sanctorum;

import com.hellblazer.sanctorum.internal.v1.proto.Bytes;
import com.macasaet.fernet.Key;
import com.macasaet.fernet.Token;
import com.macasaet.fernet.TokenValidationException;
import com.macasaet.fernet.Validator;
import com.hellblazer.delos.cryptography.Digest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;

/**
 * @author hal.hildebrand
 **/
public class TokenGenerator {
    private static final Logger log = LoggerFactory.getLogger(TokenGenerator.class);

    private final    SecureRandom entropy;
    private volatile Key          master;

    public TokenGenerator(java.security.Key master, SecureRandom entropy) {
        this(new Key(master.getEncoded()), entropy);
    }

    public TokenGenerator(Key master, SecureRandom entropy) {
        this.master = master;
        this.entropy = entropy;
    }

    public Token apply(Bytes message) {
        if (master == null) {
            return null;
        }

        var token = Token.generate(entropy, master, message.getB().toByteArray());
        log.debug("Generated token: {}", token);
        return token;
    }

    public void clear() {
        master = null;
    }

    public boolean valid(HashedToken hashed) {
        return !validate(() -> null, hashed).equals(Bytes.getDefaultInstance());
    }

    public Bytes validate(Validator<Bytes> validator, HashedToken k) {
        try {
            var decrypt = k.token().validateAndDecrypt(master, validator);
            log.debug("Decrypted Token: {} is valid: {}", k.hash(), k.token);
            return decrypt;
        } catch (TokenValidationException e) {
            log.debug("Invalid Token: {}", k.hash());
            return Bytes.getDefaultInstance();
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
