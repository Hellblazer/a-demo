/*
 * Copyright (c) 2023 Hal Hildebrand. All rights reserved.
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

package com.hellblazer.nut.support;

import com.salesforce.apollo.delphinius.Oracle;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * @author hal.hildebrand
 **/
public class DelegatedOracle implements Oracle {
    private final Oracle delegate;

    public DelegatedOracle(Oracle delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<Void> add(Assertion assertion) {
        return delegate.add(assertion);
    }

    @Override
    public CompletableFuture<Void> add(Namespace namespace) {
        return delegate.add(namespace);
    }

    @Override
    public CompletableFuture<Void> add(Object object) {
        return delegate.add(object);
    }

    @Override
    public CompletableFuture<Void> add(Relation relation) {
        return delegate.add(relation);
    }

    @Override
    public CompletableFuture<Void> add(Subject subject) {
        return delegate.add(subject);
    }

    @Override
    public boolean check(Assertion assertion) throws SQLException {
        return delegate.check(assertion);
    }

    @Override
    public CompletableFuture<Void> delete(Assertion assertion) {
        return delegate.delete(assertion);
    }

    @Override
    public CompletableFuture<Void> delete(Namespace namespace) {
        return delegate.delete(namespace);
    }

    @Override
    public CompletableFuture<Void> delete(Object object) {
        return delegate.delete(object);
    }

    @Override
    public CompletableFuture<Void> delete(Relation relation) {
        return delegate.delete(relation);
    }

    @Override
    public CompletableFuture<Void> delete(Subject subject) {
        return delegate.delete(subject);
    }

    @Override
    public List<Subject> expand(Object object) throws SQLException {
        return delegate.expand(object);
    }

    @Override
    public List<Subject> expand(Relation predicate, Object object) throws SQLException {
        return delegate.expand(predicate, object);
    }

    @Override
    public List<Object> expand(Relation predicate, Subject subject) throws SQLException {
        return delegate.expand(predicate, subject);
    }

    @Override
    public List<Object> expand(Subject subject) throws SQLException {
        return delegate.expand(subject);
    }

    @Override
    public CompletableFuture<Void> map(Object parent, Object child) {
        return delegate.map(parent, child);
    }

    @Override
    public CompletableFuture<Void> map(Relation parent, Relation child) {
        return delegate.map(parent, child);
    }

    @Override
    public CompletableFuture<Void> map(Subject parent, Subject child) {
        return delegate.map(parent, child);
    }

    @Override
    public List<Subject> read(Object... objects) throws SQLException {
        return delegate.read(objects);
    }

    @Override
    public List<Subject> read(Relation predicate, Object... objects) throws SQLException {
        return delegate.read(predicate, objects);
    }

    @Override
    public List<Object> read(Relation predicate, Subject... subjects) throws SQLException {
        return delegate.read(predicate, subjects);
    }

    @Override
    public List<Object> read(Subject... subjects) throws SQLException {
        return delegate.read(subjects);
    }

    @Override
    public CompletableFuture<Void> remove(Object parent, Object child) {
        return delegate.remove(parent, child);
    }

    @Override
    public CompletableFuture<Void> remove(Relation parent, Relation child) {
        return delegate.remove(parent, child);
    }

    @Override
    public CompletableFuture<Void> remove(Subject parent, Subject child) {
        return delegate.remove(parent, child);
    }

    @Override
    public Stream<Subject> subjects(Relation predicate, Object object) throws SQLException {
        return delegate.subjects(predicate, object);
    }
}
