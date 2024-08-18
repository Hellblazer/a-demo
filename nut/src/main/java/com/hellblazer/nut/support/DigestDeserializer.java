package com.hellblazer.nut.support;/*
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

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.hellblazer.delos.cryptography.Digest;

import java.io.IOException;

import static com.hellblazer.delos.cryptography.QualifiedBase64.digest;

/**
 * @author hal.hildebrand
 **/
public class DigestDeserializer extends FromStringDeserializer {
    public DigestDeserializer() {
        super(Digest.class);
    }

    @Override
    protected Digest _deserialize(String value, DeserializationContext ctxt) throws IOException {
        return digest(value);
    }
}
