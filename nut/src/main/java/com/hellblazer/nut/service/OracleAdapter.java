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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.hellblazer.delphi.proto.*;
import com.salesforce.apollo.delphinius.Oracle;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import org.joou.ULong;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * @author hal.hildebrand
 **/
public class OracleAdapter implements Oracle {
    private final Oracle_Grpc.Oracle_FutureStub   asyncDelphi;
    private final Oracle_Grpc.Oracle_BlockingStub syncDelphi;
    private final ManagedChannel                  channel;

    public OracleAdapter(ManagedChannel channel) {
        this.channel = channel;
        this.asyncDelphi = Oracle_Grpc.newFutureStub(channel);
        this.syncDelphi = Oracle_Grpc.newBlockingStub(channel);
    }

    private static Assertion_ of(Assertion of) {
        return Assertion_.newBuilder().setObject(of(of.object())).setSubject(of(of.subject())).build();
    }

    private static Subject_ of(Subject subject) {
        return Subject_.newBuilder()
                       .setNamespace(of(subject.namespace()))
                       .setName(subject.name())
                       .setRelation(of(subject.relation()))
                       .build();
    }

    private static Object_ of(Object object) {
        return Object_.newBuilder()
                      .setNamespace(of(object.namespace()))
                      .setName(object.name())
                      .setRelation(of(object.relation()))
                      .build();
    }

    private static Relation_ of(Relation relation) {
        return Relation_.newBuilder().setNamespace(of(relation.namespace())).setName(relation.name()).build();
    }

    private static Namespace_ of(Namespace namespace) {
        return Namespace_.newBuilder().setName(namespace.name()).build();
    }

    public static Namespace namespace(String name) {
        return Oracle.namespace(name);
    }

    static <T> CompletableFuture<T> fs(ListenableFuture<T> from) {
        var fs = new CompletableFuture<T>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                boolean cancelled = from.cancel(mayInterruptIfRunning);
                super.cancel(cancelled);
                return cancelled;
            }
        };
        Futures.addCallback(from, new FutureCallback<>() {
            @Override
            public void onFailure(Throwable ex) {
                fs.completeExceptionally(ex);
            }

            @Override
            public void onSuccess(T result) {
                fs.complete(result);
            }
        }, Runnable::run);
        return fs;
    }

    private static Object of(Object_ s) {
        return new Object(of(s.getNamespace()), s.getName(), of(s.getRelation()));
    }

    private static Subject of(Subject_ s) {
        return new Subject(of(s.getNamespace()), s.getName(), of(s.getRelation()));
    }

    private static Relation of(Relation_ relation) {
        return new Relation(of(relation.getNamespace()), relation.getName());
    }

    private static Namespace of(Namespace_ namespace) {
        return new Namespace(namespace.getName());
    }

    @Override
    public CompletableFuture<Asserted> add(Assertion assertion) {
        return fs(asyncDelphi.addAssertion(of(assertion))).thenApply(
        asserted -> new Asserted(ULong.valueOf(asserted.getTs()), asserted.getAdded()));
    }

    @Override
    public CompletableFuture<ULong> add(Namespace namespace) {
        return fs(asyncDelphi.addNamespace(of(namespace))).thenApply(ts -> ULong.valueOf(ts.getTs()));
    }

    @Override
    public CompletableFuture<ULong> add(Object object) {
        return fs(asyncDelphi.addObject(of(object))).thenApply(ts -> ULong.valueOf(ts.getTs()));
    }

    @Override
    public CompletableFuture<ULong> add(Relation relation) {
        return fs(asyncDelphi.addRelation(of(relation))).thenApply(ts -> ULong.valueOf(ts.getTs()));
    }

    @Override
    public CompletableFuture<ULong> add(Subject subject) {
        return fs(asyncDelphi.addSubject(of(subject))).thenApply(ts -> ULong.valueOf(ts.getTs()));
    }

    @Override
    public boolean check(Assertion assertion) throws SQLException {
        throw new SQLException(new UnsupportedOperationException("Must provide time stamp"));
    }

    @Override
    public boolean check(Assertion assertion, ULong valid) throws SQLException {
        try {
            var assertionAt = AssertionAt.newBuilder().setAssertion(of(assertion)).setTs(valid.longValue()).build();
            return syncDelphi.check(assertionAt).getResult();
        } catch (StatusRuntimeException e) {
            throw new SQLException(e);
        }
    }

    public void close() {
        channel.shutdown();
    }

    @Override
    public CompletableFuture<ULong> delete(Assertion assertion) {
        return fs(asyncDelphi.deleteAssertion(of(assertion))).thenApply(ts -> ULong.valueOf(ts.getTs()));
    }

    @Override
    public CompletableFuture<ULong> delete(Namespace namespace) {
        return fs(asyncDelphi.deleteNamespace(of(namespace))).thenApply(ts -> ULong.valueOf(ts.getTs()));
    }

    @Override
    public CompletableFuture<ULong> delete(Object object) {
        return fs(asyncDelphi.deleteObject(of(object))).thenApply(ts -> ULong.valueOf(ts.getTs()));
    }

    @Override
    public CompletableFuture<ULong> delete(Relation relation) {
        return fs(asyncDelphi.deleteRelation(of(relation))).thenApply(ts -> ULong.valueOf(ts.getTs()));
    }

    @Override
    public CompletableFuture<ULong> delete(Subject subject) {
        return fs(asyncDelphi.deleteSubject(of(subject))).thenApply(ts -> ULong.valueOf(ts.getTs()));
    }

    @Override
    public List<Subject> expand(Object object) throws SQLException {
        try {
            return syncDelphi.expandSubject(of(object)).getSubjectsList().stream().map(OracleAdapter::of).toList();
        } catch (StatusRuntimeException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public List<Subject> expand(Relation predicate, Object object) throws SQLException {
        try {
            return syncDelphi.expandSubjects(
                             ObjectPredicate.newBuilder().setPredicate(of(predicate)).setObject(of(object)).build())
                             .getSubjectsList()
                             .stream()
                             .map(OracleAdapter::of)
                             .toList();
        } catch (StatusRuntimeException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public List<Object> expand(Relation predicate, Subject subject) throws SQLException {
        try {
            return syncDelphi.expandObjects(
                             SubjectPredicate.newBuilder().setPredicate(of(predicate)).setSubject(of(subject)).build())
                             .getObjectsList()
                             .stream()
                             .map(OracleAdapter::of)
                             .toList();
        } catch (StatusRuntimeException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public List<Object> expand(Subject subject) throws SQLException {
        try {
            return syncDelphi.expandObject(of(subject)).getObjectsList().stream().map(OracleAdapter::of).toList();
        } catch (StatusRuntimeException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public CompletableFuture<ULong> map(Object parent, Object child) {
        var m = asyncDelphi.mapObject(ObjectMap.newBuilder().setParent(of(parent)).setChild(of(child)).build());
        return fs(m).thenApply(ts -> ULong.valueOf(ts.getTs()));
    }

    @Override
    public CompletableFuture<ULong> map(Relation parent, Relation child) {
        var m = asyncDelphi.mapRelation(RelationMap.newBuilder().setParent(of(parent)).setChild(of(child)).build());
        return fs(m).thenApply(ts -> ULong.valueOf(ts.getTs()));
    }

    @Override
    public CompletableFuture<ULong> map(Subject parent, Subject child) {
        var m = asyncDelphi.mapSubject(SubjectMap.newBuilder().setParent(of(parent)).setChild(of(child)).build());
        return fs(m).thenApply(ts -> ULong.valueOf(ts.getTs()));
    }

    @Override
    public List<Subject> read(Object... objects) throws SQLException {
        try {
            return syncDelphi.readSubjects(
                             Objects.newBuilder().addAllObjects(Arrays.stream(objects).map(OracleAdapter::of).toList()).build())
                             .getSubjectsList()
                             .stream()
                             .map(OracleAdapter::of)
                             .toList();
        } catch (StatusRuntimeException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public List<Subject> read(Relation predicate, Object... objects) throws SQLException {
        try {
            return syncDelphi.readSubjectsMatching(ObjectPredicates.newBuilder()
                                                                   .setPredicate(of(predicate))
                                                                   .addAllObjects(Arrays.stream(objects)
                                                                                        .map(OracleAdapter::of)
                                                                                        .toList())
                                                                   .build())
                             .getSubjectsList()
                             .stream()
                             .map(OracleAdapter::of)
                             .toList();
        } catch (StatusRuntimeException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public List<Object> read(Relation predicate, Subject... subjects) throws SQLException {
        try {
            return syncDelphi.readObjectsMatching(SubjectPredicates.newBuilder()
                                                                   .setPredicate(of(predicate))
                                                                   .addAllSubjects(Arrays.stream(subjects)
                                                                                         .map(OracleAdapter::of)
                                                                                         .toList())
                                                                   .build())
                             .getObjectsList()
                             .stream()
                             .map(OracleAdapter::of)
                             .toList();
        } catch (StatusRuntimeException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public List<Object> read(Subject... subjects) throws SQLException {
        try {
            return syncDelphi.readObjects(
                             Subjects.newBuilder().addAllSubjects(Arrays.stream(subjects).map(OracleAdapter::of).toList()).build())
                             .getObjectsList()
                             .stream()
                             .map(OracleAdapter::of)
                             .toList();
        } catch (StatusRuntimeException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public CompletableFuture<ULong> remove(Object parent, Object child) {
        var unMap = asyncDelphi.unmapObject(ObjectMap.newBuilder().setParent(of(parent)).setChild(of(child)).build());
        return fs(unMap).thenApply(ts -> ULong.valueOf(ts.getTs()));
    }

    @Override
    public CompletableFuture<ULong> remove(Relation parent, Relation child) {
        var unMap = asyncDelphi.unmapRelation(
        RelationMap.newBuilder().setParent(of(parent)).setChild(of(child)).build());
        return fs(unMap).thenApply(ts -> ULong.valueOf(ts.getTs()));
    }

    @Override
    public CompletableFuture<ULong> remove(Subject parent, Subject child) {
        var unMap = asyncDelphi.unmapSubject(SubjectMap.newBuilder().setParent(of(parent)).setChild(of(child)).build());
        return fs(unMap).thenApply(ts -> ULong.valueOf(ts.getTs()));
    }

    @Override
    public Stream<Subject> subjects(Relation predicate, Object object) throws SQLException {
        try {
            return syncDelphi.subjects(
                             ObjectPredicate.newBuilder().setPredicate(of(predicate)).setObject(of(object)).build())
                             .getSubjectsList()
                             .stream()
                             .map(OracleAdapter::of);
        } catch (StatusRuntimeException e) {
            throw new SQLException(e);
        }
    }
}
