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

package com.hellblazer.nut.service;

import java.security.Principal;
import java.util.Objects;

/**
 * @author hal.hildebrand
 **/
public class AuthenticatedSubject implements Principal {
    private final TokenAuthenticator.Subject subject;

    public AuthenticatedSubject(TokenAuthenticator.Subject subject) {
        this.subject = subject;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AuthenticatedSubject that = (AuthenticatedSubject) o;
        return Objects.equals(subject, that.subject);
    }

    @Override
    public String getName() {
        return subject.subject().toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject);
    }
}
