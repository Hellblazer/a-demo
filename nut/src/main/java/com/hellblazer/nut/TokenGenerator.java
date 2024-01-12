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

import com.google.protobuf.Message;
import com.macasaet.fernet.Key;
import com.macasaet.fernet.Token;

import java.security.SecureRandom;
import java.util.function.Function;

/**
 * @author hal.hildebrand
 **/
public class TokenGenerator implements Function<Message, Token> {
    private final Key          master;
    private final SecureRandom entropy;

    public TokenGenerator(Key master, SecureRandom entropy) {
        this.master = master;
        this.entropy = entropy;
    }

    @Override
    public Token apply(Message message) {
        return Token.generate(entropy, master, message.toByteArray());
    }
}
