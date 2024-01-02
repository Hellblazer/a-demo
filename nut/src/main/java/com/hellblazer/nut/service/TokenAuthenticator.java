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

import com.macasaet.fernet.IllegalTokenException;
import com.macasaet.fernet.Token;
import com.salesforce.apollo.delphinius.Oracle;
import io.dropwizard.auth.AuthFilter;
import io.dropwizard.auth.AuthenticationException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.SecurityContext;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static io.dropwizard.logback.shaded.guava.base.Preconditions.checkNotNull;
import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;

/**
 * @author hal.hildebrand
 **/
public class TokenAuthenticator<P extends Principal> extends AuthFilter<TokenAuthenticator.Subject, P> {
    private final String                    cookieName;
    private final Function<String, Subject> validator;

    public TokenAuthenticator(Function<String, Subject> validator, String cookieName) {
        this.cookieName = cookieName;
        this.validator = validator;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        final Optional<String> optionalToken = getTokenFromCookieOrHeader(requestContext);

        if (optionalToken.isPresent()) {
            try {
                final var subject = verifyToken(optionalToken.get());
                final Optional<P> principal = authenticator.authenticate(subject);

                if (principal.isPresent()) {
                    requestContext.setSecurityContext(new SecurityContext() {

                        @Override
                        public String getAuthenticationScheme() {
                            return SecurityContext.BASIC_AUTH;
                        }

                        @Override
                        public Principal getUserPrincipal() {
                            return principal.get();
                        }

                        @Override
                        public boolean isSecure() {
                            return requestContext.getSecurityContext().isSecure();
                        }

                        @Override
                        public boolean isUserInRole(String role) {
                            return authorizer.authorize(principal.get(), role, requestContext);
                        }

                    });
                    return;
                }
            } catch (IllegalTokenException ex) {
                logger.warn("Error decoding credentials: " + ex.getMessage(), ex);
            } catch (AuthenticationException ex) {
                logger.warn("Error authenticating credentials", ex);
                throw new InternalServerErrorException();
            }
        }

        throw new WebApplicationException(unauthorizedHandler.buildResponse(prefix, realm));
    }

    private Optional<String> getTokenFromCookie(ContainerRequestContext requestContext) {
        final Map<String, Cookie> cookies = requestContext.getCookies();

        if (cookieName != null && cookies.containsKey(cookieName)) {
            final Cookie tokenCookie = cookies.get(cookieName);
            final String rawToken = tokenCookie.getValue();
            return Optional.of(rawToken);
        }

        return Optional.empty();
    }

    private Optional<String> getTokenFromCookieOrHeader(ContainerRequestContext requestContext) {
        final Optional<String> headerToken = getTokenFromHeader(requestContext.getHeaders());
        return headerToken.isPresent() ? headerToken : getTokenFromCookie(requestContext);
    }

    private Optional<String> getTokenFromHeader(MultivaluedMap<String, String> headers) {
        final String header = headers.getFirst(AUTHORIZATION);
        if (header != null) {
            int space = header.indexOf(' ');
            if (space > 0) {
                final String method = header.substring(0, space);
                if (prefix.equalsIgnoreCase(method)) {
                    final String rawToken = header.substring(space + 1);
                    return Optional.of(rawToken);
                }
            }
        }

        return Optional.empty();
    }

    private Subject verifyToken(String rawToken) throws IllegalTokenException {
        return validator.apply(rawToken);
    }

    public record Subject(long id, Oracle.Subject subject, Token token) {
    }

    public static class Builder<P extends Principal> extends AuthFilterBuilder<Subject, P, TokenAuthenticator<P>> {

        private Function<String, Subject> validator;
        private String                    cookieName;

        public Builder<P> setCookieName(String cookieName) {
            this.cookieName = cookieName;
            return this;
        }

        public Builder<P> setValidator(Function<String, Subject> validator) {
            checkNotNull(validator, "Validator must be non null");
            this.validator = validator;
            return this;
        }

        @Override
        protected TokenAuthenticator<P> newInstance() {
            checkNotNull(validator, "Validator must be non null");
            return new TokenAuthenticator<>(validator, cookieName);
        }
    }
}
