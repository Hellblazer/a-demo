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
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.hellblazer.nut.service;

import com.codahale.metrics.annotation.Timed;
import com.salesforce.apollo.delphinius.Oracle;
import com.salesforce.apollo.delphinius.Oracle.Object;
import com.salesforce.apollo.delphinius.Oracle.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * @author hal.hildebrand
 */
@Path("/admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    private final Oracle   oracle;
    private final Duration timeout;

    public AdminResource(Oracle oracle, Duration timeout) {
        this.oracle = oracle;
        this.timeout = timeout;
    }

    @POST
    @Timed
    @Path("assertion/add")
    public void add(Assertion assertion) {
        try {
            oracle.add(assertion).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        } catch (ExecutionException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (TimeoutException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        }
    }

    @POST
    @Timed
    @Path("namespace/add")
    public void add(Namespace namespace) {
        try {
            oracle.add(namespace).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        } catch (ExecutionException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (TimeoutException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        }
    }

    @POST
    @Timed
    @Path("object/add")
    public void add(Object object) {
        try {
            oracle.add(object).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        } catch (ExecutionException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (TimeoutException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        }
    }

    @POST
    @Timed
    @Path("relation/add")
    public void add(Relation relation) {
        try {
            oracle.add(relation).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        } catch (ExecutionException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (TimeoutException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        }
    }

    @POST
    @Timed
    @Path("subject/add")
    public void add(Subject subject) {
        try {
            oracle.add(subject).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        } catch (ExecutionException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (TimeoutException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        }
    }

    @POST
    @Timed
    @Path("assertion/delete")
    public void delete(Assertion assertion) {
        try {
            oracle.delete(assertion).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        } catch (ExecutionException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (TimeoutException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        }
    }

    @POST
    @Timed
    @Path("namespace/delete")
    public void delete(Namespace namespace) {
        try {
            oracle.delete(namespace).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        } catch (ExecutionException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (TimeoutException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        }
    }

    @POST
    @Timed
    @Path("object/delete")
    public void delete(Object object) {
        try {
            oracle.delete(object).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        } catch (ExecutionException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (TimeoutException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        }
    }

    @POST
    @Timed
    @Path("relation/delete")
    public void delete(Relation relation) {
        try {
            oracle.delete(relation).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        } catch (ExecutionException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (TimeoutException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        }
    }

    @POST
    @Timed
    @Path("subject/delete")
    public void delete(Subject subject) {
        try {
            oracle.delete(subject).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        } catch (ExecutionException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (TimeoutException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        }
    }

    @POST
    @Timed
    @Path("expand/object")
    public List<Subject> expand(Object object) {
        try {
            return oracle.expand(object);
        } catch (SQLException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Timed
    @Path("expand/objects")
    public List<Subject> expand(PredicateObject predicateObject) {
        try {
            return oracle.expand(predicateObject.predicate, predicateObject.object);
        } catch (SQLException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Timed
    @Path("expand/subject")
    public List<Object> expand(Subject subject) {
        try {
            return oracle.expand(subject);
        } catch (SQLException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Timed
    @Path("expand/subjects")
    public List<Object> expand(PredicateSubject predicateSubject) {
        try {
            return oracle.expand(predicateSubject.predicate, predicateSubject.subject);
        } catch (SQLException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Timed
    @Path("map/object")
    public void mapObject(Association<Object> association) {
        try {
            oracle.map(association.a, association.b).get();
        } catch (InterruptedException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        } catch (ExecutionException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Timed
    @Path("map/relation")
    public void mapRelation(Association<Relation> association) {
        try {
            oracle.map(association.a, association.b).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        } catch (ExecutionException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (TimeoutException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        }
    }

    @POST
    @Timed
    @Path("map/subject")
    public void mapSubject(Association<Subject> association) {
        try {
            oracle.map(association.a, association.b).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        } catch (ExecutionException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (TimeoutException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        }
    }

    @POST
    @Timed
    @Path("read/objects/subjects")
    public List<Subject> read(PredicateObjects predicateObjects) {
        try {
            return oracle.read(predicateObjects.predicate,
                               predicateObjects.objects.toArray(new Object[predicateObjects.objects.size()]));
        } catch (SQLException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Timed
    @Path("read/subjects/objects")
    public Response read(PredicateSubject predicateSubject) {
        return null;
    }

    @POST
    @Timed
    @Path("read/subjects")
    public List<Subject> readObjects(List<Object> objects) {
        try {
            return oracle.read(objects.toArray(new Object[objects.size()]));
        } catch (SQLException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Timed
    @Path("read/objects")
    public List<Object> readSubjects(List<Subject> subjects) {
        try {
            return oracle.read(subjects.toArray(new Subject[subjects.size()]));
        } catch (SQLException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Timed
    @Path("map/object/remove")
    public void removeObjectMapping(Association<Object> association) {
        try {
            oracle.remove(association.a, association.b).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        } catch (ExecutionException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (TimeoutException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        }
    }

    @POST
    @Timed
    @Path("map/relation/remove")
    public void removeRelationMapping(Association<Relation> association) {
        try {
            oracle.remove(association.a, association.b).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        } catch (ExecutionException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (TimeoutException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        }
    }

    @POST
    @Timed
    @Path("map/subject/remove")
    public void removeSubjectMapping(Association<Subject> association) {
        try {
            oracle.remove(association.a, association.b).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        } catch (ExecutionException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (TimeoutException e) {
            throw new WebApplicationException(e, Response.Status.REQUEST_TIMEOUT);
        }
    }

    @POST
    @Timed
    @Path("subjects")
    public Stream<Subject> subjects(PredicateObject predicateObject) {
        try {
            return oracle.subjects(predicateObject.predicate, predicateObject.object);
        } catch (SQLException e) {
            throw new WebApplicationException(e.getCause(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    public record PredicateObject(Relation predicate, Object object) {
    }

    public record Association<T>(T a, T b) {
    }

    public record PredicateObjects(Relation predicate, List<Object> objects) {
    }

    public record PredicateSubject(Relation predicate, Subject subject) {
    }
}
