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

import com.salesforce.apollo.delphinius.Oracle;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * @author hal.hildebrand
 **/
public class Delphi {
    private final Oracle oracle;

    public Delphi(Oracle oracle) {
        this.oracle = oracle;
    }

    public CompletableFuture<Void> add(Oracle.Assertion assertion) {
        return oracle.add(assertion);
    }

    public CompletableFuture<Void> add(Oracle.Namespace namespace) {
        return oracle.add(namespace);
    }

    public CompletableFuture<Void> add(Oracle.Object object) {
        return oracle.add(object);
    }

    public CompletableFuture<Void> add(Oracle.Relation relation) {
        return oracle.add(relation);
    }

    public CompletableFuture<Void> add(Oracle.Subject subject) {
        return oracle.add(subject);
    }

    public boolean check(Oracle.Assertion assertion) throws SQLException {
        return oracle.check(assertion);
    }

    public CompletableFuture<Void> delete(Oracle.Assertion assertion) {
        return oracle.delete(assertion);
    }

    public CompletableFuture<Void> delete(Oracle.Namespace namespace) {
        return oracle.delete(namespace);
    }

    public CompletableFuture<Void> delete(Oracle.Object object) {
        return oracle.delete(object);
    }

    public CompletableFuture<Void> delete(Oracle.Relation relation) {
        return oracle.delete(relation);
    }

    public CompletableFuture<Void> delete(Oracle.Subject subject) {
        return oracle.delete(subject);
    }

    public List<Oracle.Subject> expand(Oracle.Object object) throws SQLException {
        return oracle.expand(object);
    }

    public List<Oracle.Subject> expand(Oracle.Relation predicate, Oracle.Object object) throws SQLException {
        return oracle.expand(predicate, object);
    }

    public List<Oracle.Object> expand(Oracle.Relation predicate, Oracle.Subject subject) throws SQLException {
        return oracle.expand(predicate, subject);
    }

    public List<Oracle.Object> expand(Oracle.Subject subject) throws SQLException {
        return oracle.expand(subject);
    }

    public CompletableFuture<Void> map(Oracle.Object parent, Oracle.Object child) {
        return oracle.map(parent, child);
    }

    public CompletableFuture<Void> map(Oracle.Relation parent, Oracle.Relation child) {
        return oracle.map(parent, child);
    }

    public CompletableFuture<Void> map(Oracle.Subject parent, Oracle.Subject child) {
        return oracle.map(parent, child);
    }

    public List<Oracle.Subject> read(Oracle.Object... objects) throws SQLException {
        return oracle.read(objects);
    }

    public List<Oracle.Subject> read(Oracle.Relation predicate, Oracle.Object... objects) throws SQLException {
        return oracle.read(predicate, objects);
    }

    public List<Oracle.Object> read(Oracle.Relation predicate, Oracle.Subject... subjects) throws SQLException {
        return oracle.read(predicate, subjects);
    }

    public List<Oracle.Object> read(Oracle.Subject... subjects) throws SQLException {
        return oracle.read(subjects);
    }

    public CompletableFuture<Void> remove(Oracle.Object parent, Oracle.Object child) {
        return oracle.remove(parent, child);
    }

    public CompletableFuture<Void> remove(Oracle.Relation parent, Oracle.Relation child) {
        return oracle.remove(parent, child);
    }

    public CompletableFuture<Void> remove(Oracle.Subject parent, Oracle.Subject child) {
        return oracle.remove(parent, child);
    }

    public Stream<Oracle.Subject> subjects(Oracle.Relation predicate, Oracle.Object object) throws SQLException {
        return oracle.subjects(predicate, object);
    }
}
