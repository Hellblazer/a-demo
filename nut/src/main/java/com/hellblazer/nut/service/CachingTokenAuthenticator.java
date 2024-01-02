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

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheBuilderSpec;
import com.google.common.cache.CacheStats;
import com.macasaet.fernet.Token;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;

import java.security.Principal;
import java.util.AbstractMap.SimpleEntry;
import java.util.Optional;
import java.util.function.Predicate;

import static com.codahale.metrics.MetricRegistry.name;

public class CachingTokenAuthenticator<P extends Principal> implements Authenticator<TokenAuthenticator.Subject, P> {

    private final Authenticator<TokenAuthenticator.Subject, P>                       authenticator;
    private final Cache<Token, SimpleEntry<TokenAuthenticator.Subject, Optional<P>>> cache;
    private final Meter                                                              cacheMisses;
    private final Timer                                                              gets;

    /**
     * Creates a new cached authenticator.
     *
     * @param metricRegistry the application's registry of metrics
     * @param authenticator  the underlying authenticator
     * @param cacheSpec      a {@link CacheBuilderSpec}
     */
    public CachingTokenAuthenticator(final MetricRegistry metricRegistry,
                                     final Authenticator<TokenAuthenticator.Subject, P> authenticator,
                                     final CacheBuilderSpec cacheSpec) {
        this(metricRegistry, authenticator, CacheBuilder.from(cacheSpec));
    }

    /**
     * Creates a new cached authenticator.
     *
     * @param metricRegistry the application's registry of metrics
     * @param authenticator  the underlying authenticator
     * @param builder        a {@link CacheBuilder}
     */
    public CachingTokenAuthenticator(final MetricRegistry metricRegistry,
                                     final Authenticator<TokenAuthenticator.Subject, P> authenticator,
                                     final CacheBuilder<Object, Object> builder) {
        this.authenticator = authenticator;
        this.cacheMisses = metricRegistry.meter(name(authenticator.getClass(), "cache-misses"));
        this.gets = metricRegistry.timer(name(authenticator.getClass(), "gets"));
        this.cache = builder.recordStats().build();
    }

    @Override
    public Optional<P> authenticate(TokenAuthenticator.Subject context) throws AuthenticationException {
        try (final Timer.Context timer = gets.time()) {
            final SimpleEntry<TokenAuthenticator.Subject, Optional<P>> cacheEntry = cache.getIfPresent(context.token());
            if (cacheEntry != null) {
                return cacheEntry.getValue();
            }

            cacheMisses.mark();
            final Optional<P> principal = authenticator.authenticate(context);
            if (principal.isPresent()) {
                cache.put(context.token(), new SimpleEntry<>(context, principal));
            }
            return principal;
        }
    }

    /**
     * Discards any cached principal for the given credentials.
     *
     * @param credentials a set of credentials
     */
    public void invalidate(TokenAuthenticator.Subject credentials) {
        cache.invalidate(credentials.token());
    }

    /**
     * Discards any cached principal for the given collection of credentials.
     *
     * @param credentials a collection of credentials
     */
    public void invalidateAll(Iterable<TokenAuthenticator.Subject> credentials) {
        credentials.forEach(context -> cache.invalidate(context.token()));
    }

    /**
     * Discards any cached principal for the collection of credentials satisfying the given predicate.
     *
     * @param predicate a predicate to filter credentials
     */
    public void invalidateAll(Predicate<? super TokenAuthenticator.Subject> predicate) {
        cache.asMap()
             .values()
             .stream()
             .map(SimpleEntry::getKey)
             .filter(predicate)
             .map(TokenAuthenticator.Subject::token)
             .forEach(cache::invalidate);
    }

    /**
     * Discards all cached principals.
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * Returns the number of cached principals.
     *
     * @return the number of cached principals
     */
    public long size() {
        return cache.size();
    }

    /**
     * Returns a set of statistics about the cache contents and usage.
     *
     * @return a set of statistics about the cache contents and usage
     */
    public CacheStats stats() {
        return cache.stats();
    }
}
