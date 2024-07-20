package com.hellblazer.nut.comms;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.hellblazer.delphi.proto.*;
import com.salesforce.apollo.delphinius.Oracle;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * @author hal.hildebrand
 **/
public class DelphiAdapter implements Oracle {
    private final Oracle_Grpc.Oracle_FutureStub   asyncDelphi;
    private final Oracle_Grpc.Oracle_BlockingStub syncDelphi;

    public DelphiAdapter(ManagedChannel channel) {
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
    public CompletableFuture<Void> add(Assertion assertion) {
        return fs(asyncDelphi.addAssertion(of(assertion))).thenApply(_ -> null);
    }

    @Override
    public CompletableFuture<Void> add(Namespace namespace) {
        return fs(asyncDelphi.addNamespace(of(namespace))).thenApply(_ -> null);
    }

    @Override
    public CompletableFuture<Void> add(Object object) {
        return fs(asyncDelphi.addObject(of(object))).thenApply(_ -> null);
    }

    @Override
    public CompletableFuture<Void> add(Relation relation) {
        return fs(asyncDelphi.addRelation(of(relation))).thenApply(_ -> null);
    }

    @Override
    public CompletableFuture<Void> add(Subject subject) {
        return fs(asyncDelphi.addSubject(of(subject))).thenApply(_ -> null);
    }

    @Override
    public boolean check(Assertion assertion) throws SQLException {
        try {
            return syncDelphi.check(of(assertion)).getResult();
        } catch (StatusRuntimeException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public CompletableFuture<Void> delete(Assertion assertion) {
        return fs(asyncDelphi.deleteAssertion(of(assertion))).thenApply(_ -> null);
    }

    @Override
    public CompletableFuture<Void> delete(Namespace namespace) {
        return fs(asyncDelphi.deleteNamespace(of(namespace))).thenApply(_ -> null);
    }

    @Override
    public CompletableFuture<Void> delete(Object object) {
        return fs(asyncDelphi.deleteObject(of(object))).thenApply(_ -> null);
    }

    @Override
    public CompletableFuture<Void> delete(Relation relation) {
        return fs(asyncDelphi.deleteRelation(of(relation))).thenApply(_ -> null);
    }

    @Override
    public CompletableFuture<Void> delete(Subject subject) {
        return fs(asyncDelphi.deleteSubject(of(subject))).thenApply(_ -> null);
    }

    @Override
    public List<Subject> expand(Object object) throws SQLException {
        try {
            return syncDelphi.expandSubject(of(object)).getSubjectsList().stream().map(DelphiAdapter::of).toList();
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
                             .map(DelphiAdapter::of)
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
                             .map(DelphiAdapter::of)
                             .toList();
        } catch (StatusRuntimeException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public List<Object> expand(Subject subject) throws SQLException {
        try {
            return syncDelphi.expandObject(of(subject)).getObjectsList().stream().map(DelphiAdapter::of).toList();
        } catch (StatusRuntimeException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public CompletableFuture<Void> map(Object parent, Object child) {
        var m = asyncDelphi.mapObject(ObjectMap.newBuilder().setParent(of(parent)).setChild(of(child)).build());
        return fs(m).thenApply(_ -> null);
    }

    @Override
    public CompletableFuture<Void> map(Relation parent, Relation child) {
        var m = asyncDelphi.mapRelation(RelationMap.newBuilder().setParent(of(parent)).setChild(of(child)).build());
        return fs(m).thenApply(_ -> null);
    }

    @Override
    public CompletableFuture<Void> map(Subject parent, Subject child) {
        var m = asyncDelphi.mapSubject(SubjectMap.newBuilder().setParent(of(parent)).setChild(of(child)).build());
        return fs(m).thenApply(_ -> null);
    }

    @Override
    public List<Subject> read(Object... objects) throws SQLException {
        try {
            return syncDelphi.readSubjects(
                             Objects.newBuilder().addAllObjects(Arrays.stream(objects).map(DelphiAdapter::of).toList()).build())
                             .getSubjectsList()
                             .stream()
                             .map(DelphiAdapter::of)
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
                                                                                        .map(DelphiAdapter::of)
                                                                                        .toList())
                                                                   .build())
                             .getSubjectsList()
                             .stream()
                             .map(DelphiAdapter::of)
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
                                                                                         .map(DelphiAdapter::of)
                                                                                         .toList())
                                                                   .build())
                             .getObjectsList()
                             .stream()
                             .map(DelphiAdapter::of)
                             .toList();
        } catch (StatusRuntimeException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public List<Object> read(Subject... subjects) throws SQLException {
        try {
            return syncDelphi.readObjects(
                             Subjects.newBuilder().addAllSubjects(Arrays.stream(subjects).map(DelphiAdapter::of).toList()).build())
                             .getObjectsList()
                             .stream()
                             .map(DelphiAdapter::of)
                             .toList();
        } catch (StatusRuntimeException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public CompletableFuture<Void> remove(Object parent, Object child) {
        var unMap = asyncDelphi.unmapObject(ObjectMap.newBuilder().setParent(of(parent)).setChild(of(child)).build());
        return fs(unMap).thenApply(_ -> null);
    }

    @Override
    public CompletableFuture<Void> remove(Relation parent, Relation child) {
        var unMap = asyncDelphi.unmapRelation(
        RelationMap.newBuilder().setParent(of(parent)).setChild(of(child)).build());
        return fs(unMap).thenApply(_ -> null);
    }

    @Override
    public CompletableFuture<Void> remove(Subject parent, Subject child) {
        var unMap = asyncDelphi.unmapSubject(SubjectMap.newBuilder().setParent(of(parent)).setChild(of(child)).build());
        return fs(unMap).thenApply(_ -> null);
    }

    @Override
    public Stream<Subject> subjects(Relation predicate, Object object) throws SQLException {
        try {
            return syncDelphi.subjects(
                             ObjectPredicate.newBuilder().setPredicate(of(predicate)).setObject(of(object)).build())
                             .getSubjectsList()
                             .stream()
                             .map(DelphiAdapter::of);
        } catch (StatusRuntimeException e) {
            throw new SQLException(e);
        }
    }
}
