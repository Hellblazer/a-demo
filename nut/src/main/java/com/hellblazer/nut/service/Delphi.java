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

import com.google.protobuf.Empty;
import com.hellblazer.delphi.proto.*;
import com.salesforce.apollo.delphinius.Oracle;
import io.grpc.stub.StreamObserver;

import java.sql.SQLException;

/**
 * @author hal.hildebrand
 **/
public class Delphi extends Oracle_Grpc.Oracle_ImplBase {
    private final Oracle oracle;

    public Delphi(Oracle oracle) {
        this.oracle = oracle;
    }

    public static Oracle.Assertion assertion(Assertion_ assertion) {
        return new Oracle.Assertion(subject(assertion.getSubject()), object(assertion.getObject()));
    }

    public static Oracle.Object object(Object_ object) {
        return new Oracle.Object(namespace(object.getNamespace()), object.getName(), relation(object.getRelation()));
    }

    private static Oracle.Namespace namespace(Namespace_ namespace) {
        return new Oracle.Namespace(namespace.getName());
    }

    public static Oracle.Subject subject(Subject_ subject) {
        return new Oracle.Subject(namespace(subject.getNamespace()), subject.getName(),
                                  relation(subject.getRelation()));
    }

    public static Oracle.Relation relation(Relation_ relation) {
        return new Oracle.Relation(namespace(relation.getNamespace()), relation.getName());
    }

    public static Object_.Builder object_(Oracle.Object o) {
        return Object_.newBuilder().setNamespace(namespace_(o.namespace()));
    }

    public static Subject_.Builder subject_(Oracle.Subject o) {
        return Subject_.newBuilder()
                       .setNamespace(namespace_(o.namespace()))
                       .setName(o.name())
                       .setRelation(relation_(o.relation()));
    }

    public static Relation_.Builder relation_(Oracle.Relation o) {
        return Relation_.newBuilder().setNamespace(namespace_(o.namespace())).setName(o.name());
    }

    public static Namespace_.Builder namespace_(Oracle.Namespace o) {
        return Namespace_.newBuilder().setName(o.name());
    }

    @Override
    public void addAssertion(Assertion_ request, StreamObserver<Empty> responseObserver) {
        oracle.add(assertion(request)).whenComplete((_, t) -> {
            if (t != null) {
                responseObserver.onError(t);
            } else {
                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
            }
        });
    }

    @Override
    public void addNamespace(Namespace_ request, StreamObserver<Empty> responseObserver) {
        oracle.add(namespace(request)).whenComplete((_, t) -> {
            if (t != null) {
                responseObserver.onError(t);
            } else {
                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
            }
        });
    }

    @Override
    public void addObject(Object_ request, StreamObserver<Empty> responseObserver) {
        oracle.add(object(request)).whenComplete((_, t) -> {
            if (t != null) {
                responseObserver.onError(t);
            } else {
                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
            }
        });
    }

    @Override
    public void addRelation(Relation_ request, StreamObserver<Empty> responseObserver) {
        oracle.add(relation(request)).whenComplete((_, t) -> {
            if (t != null) {
                responseObserver.onError(t);
            } else {
                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
            }
        });
    }

    @Override
    public void check(Assertion_ request, StreamObserver<AssertionCheck> responseObserver) {
        try {
            responseObserver.onNext(AssertionCheck.newBuilder().setResult(oracle.check(assertion(request))).build());
            responseObserver.onCompleted();
        } catch (SQLException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void deleteAssertion(Assertion_ request, StreamObserver<Empty> responseObserver) {
        oracle.delete(assertion(request)).whenComplete((_, t) -> {
            if (t != null) {
                responseObserver.onError(t);
            } else {
                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
            }
        });
    }

    @Override
    public void deleteNamespace(Namespace_ request, StreamObserver<Empty> responseObserver) {
        oracle.delete(namespace(request)).whenComplete((_, t) -> {
            if (t != null) {
                responseObserver.onError(t);
            } else {
                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
            }
        });
    }

    @Override
    public void deleteObject(Object_ request, StreamObserver<Empty> responseObserver) {
        oracle.delete(object(request)).whenComplete((_, t) -> {
            if (t != null) {
                responseObserver.onError(t);
            } else {
                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
            }
        });
    }

    @Override
    public void deleteRelation(Relation_ request, StreamObserver<Empty> responseObserver) {
        oracle.delete(relation(request)).whenComplete((_, t) -> {
            if (t != null) {
                responseObserver.onError(t);
            } else {
                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
            }
        });
    }

    @Override
    public void expandObject(Subject_ request, StreamObserver<Objects> responseObserver) {
        try {
            var result = Objects.newBuilder();
            oracle.expand(subject(request)).stream().map(Delphi::object_).forEach(result::addObjects);
            responseObserver.onNext(result.build());
            responseObserver.onCompleted();
        } catch (SQLException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void expandObjects(SubjectPredicate request, StreamObserver<Objects> responseObserver) {
        var result = Objects.newBuilder();
        try {
            oracle.expand(relation(request.getPredicate()), subject(request.getSubject()))
                  .stream()
                  .map(Delphi::object_)
                  .forEach(result::addObjects);
            responseObserver.onNext(result.build());
            responseObserver.onCompleted();
        } catch (SQLException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void expandSubject(Object_ request, StreamObserver<Subjects> responseObserver) {
        try {
            var result = Subjects.newBuilder();
            oracle.expand(object(request)).stream().map(Delphi::subject_).forEach(result::addSubjects);
            responseObserver.onNext(result.build());
            responseObserver.onCompleted();
        } catch (SQLException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void expandSubjects(ObjectPredicate request, StreamObserver<Subjects> responseObserver) {
        var result = Subjects.newBuilder();
        try {
            oracle.expand(relation(request.getPredicate()), object(request.getObject()))
                  .stream()
                  .map(Delphi::subject_)
                  .forEach(result::addSubjects);
            responseObserver.onNext(result.build());
            responseObserver.onCompleted();
        } catch (SQLException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void mapObject(ObjectMap request, StreamObserver<Empty> responseObserver) {
        oracle.map(object(request.getParent()), object(request.getChild())).whenComplete((_, t) -> {
            if (t != null) {
                responseObserver.onError(t);
            } else {
                responseObserver.onNext(Empty.getDefaultInstance());
            }
        });
    }

    @Override
    public void mapRelation(RelationMap request, StreamObserver<Empty> responseObserver) {
        oracle.map(relation(request.getParent()), relation(request.getChild())).whenComplete((_, t) -> {
            if (t != null) {
                responseObserver.onError(t);
            } else {
                responseObserver.onNext(Empty.getDefaultInstance());
            }
        });
    }

    @Override
    public void mapSubject(SubjectMap request, StreamObserver<Empty> responseObserver) {
        oracle.map(subject(request.getParent()), subject(request.getChild())).whenComplete((_, t) -> {
            if (t != null) {
                responseObserver.onError(t);
            } else {
                responseObserver.onNext(Empty.getDefaultInstance());
            }
        });
    }

    @Override
    public void readObjects(Subjects request, StreamObserver<Objects> responseObserver) {
        try {
            var subjects = request.getSubjectsList().stream().map(Delphi::subject).toArray(Oracle.Subject[]::new);
            var objects = Objects.newBuilder();
            oracle.read(subjects).stream().map(Delphi::object_).forEach(objects::addObjects);
            responseObserver.onNext(objects.build());
            responseObserver.onCompleted();
        } catch (SQLException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void readObjectsMatching(SubjectPredicates request, StreamObserver<Objects> responseObserver) {
        var subjects = request.getSubjectsList().stream().map(Delphi::subject).toArray(Oracle.Subject[]::new);
        try {
            var objects = Objects.newBuilder();
            oracle.read(relation(request.getPredicate()), subjects)
                  .stream()
                  .map(Delphi::object_)
                  .forEach(objects::addObjects);
            responseObserver.onNext(objects.build());
            responseObserver.onCompleted();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readSubjects(Objects request, StreamObserver<Subjects> responseObserver) {
        try {
            var objects = request.getObjectsList().stream().map(Delphi::object).toArray(Oracle.Object[]::new);
            var subjects = Subjects.newBuilder();
            oracle.read(objects).stream().map(Delphi::subject_).forEach(subjects::addSubjects);
            responseObserver.onNext(subjects.build());
            responseObserver.onCompleted();
        } catch (SQLException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void readSubjectsMatching(ObjectPredicates request, StreamObserver<Subjects> responseObserver) {
        var objects = request.getObjectsList().stream().map(Delphi::object).toArray(Oracle.Object[]::new);
        try {
            var subjects = Subjects.newBuilder();
            oracle.read(relation(request.getPredicate()), objects);
            responseObserver.onNext(subjects.build());
            responseObserver.onCompleted();
        } catch (SQLException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void subjects(ObjectPredicate request, StreamObserver<Subjects> responseObserver) {
        try {
            var subjects = Subjects.newBuilder();
            oracle.subjects(relation(request.getPredicate()), object(request.getObject()))
                  .map(Delphi::subject_)
                  .forEach(subjects::addSubjects);
            responseObserver.onNext(subjects.build());
            responseObserver.onCompleted();
        } catch (SQLException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void unmapObject(ObjectMap request, StreamObserver<Empty> responseObserver) {
        oracle.remove(object(request.getParent()), object(request.getChild())).whenComplete((_, t) -> {
            if (t != null) {
                responseObserver.onError(t);
            } else {
                responseObserver.onNext(Empty.getDefaultInstance());
            }
        });
    }

    @Override
    public void unmapRelation(RelationMap request, StreamObserver<Empty> responseObserver) {
        oracle.remove(relation(request.getParent()), relation(request.getChild())).whenComplete((_, t) -> {
            if (t != null) {
                responseObserver.onError(t);
            } else {
                responseObserver.onNext(Empty.getDefaultInstance());
            }
        });
    }

    @Override
    public void unmapSubject(SubjectMap request, StreamObserver<Empty> responseObserver) {
        oracle.remove(subject(request.getParent()), subject(request.getChild())).whenComplete((_, t) -> {
            if (t != null) {
                responseObserver.onError(t);
            } else {
                responseObserver.onNext(Empty.getDefaultInstance());
            }
        });
    }
}
