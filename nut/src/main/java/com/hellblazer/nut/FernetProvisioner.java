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
package com.hellblazer.nut;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.hellblazer.nut.proto.InitialProvisioning;
import com.hellblazer.nut.service.Geb;
import com.hellblazer.sanctorum.proto.FernetToken;
import com.hellblazer.sky.sanctum.TokenGenerator;
import com.hellblazer.delos.choam.support.InvalidTransaction;
import com.hellblazer.delos.cryptography.Digest;
import com.hellblazer.delos.cryptography.DigestAlgorithm;
import com.hellblazer.delos.delphinius.AbstractOracle;
import com.hellblazer.delos.delphinius.Oracle;
import com.hellblazer.delos.gorgoneion.proto.Attestation;
import com.hellblazer.delos.h2.SessionServices;
import com.hellblazer.delos.state.Mutator;
import com.hellblazer.delos.state.SqlStateMachine;
import com.hellblazer.delos.stereotomy.event.EstablishmentEvent;
import com.hellblazer.delos.stereotomy.event.proto.KERL_;
import com.hellblazer.delos.stereotomy.event.protobuf.ProtobufEventFactory;
import com.hellblazer.delos.stereotomy.identifier.Identifier;
import com.hellblazer.delos.stereotomy.identifier.SelfAddressingIdentifier;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static com.hellblazer.delos.cryptography.QualifiedBase64.qb64;

/**
 * @author hal.hildebrand
 **/
public class FernetProvisioner extends Provisioner {
    public static final  String TOKEN_VALIDATOR = "TOKEN_VALIDATOR";
    private static final Logger log             = LoggerFactory.getLogger(FernetProvisioner.class);

    private final Function<TokenGenerator.HashedToken, InitialProvisioning> validator;
    private final DigestAlgorithm                                           algorithm;
    private final Duration                                                  timeout;

    public FernetProvisioner(Digest id, Oracle oracle, Geb geb, DigestAlgorithm algorithm,
                             TokenGenerator tokenGenerator, Mutator mutator, Duration timeout) {
        super(id, oracle, geb, mutator);
        this.validator = hashedToken -> {
            try {
                return InitialProvisioning.parseFrom(
                tokenGenerator.validate(new TokenGenerator.HashedToken(hashedToken.hash(), hashedToken.token())));
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException(
                "Cannot parse validated token: %s on: %s".formatted(hashedToken.token(), id), e);
            }
        };
        this.algorithm = algorithm;
        this.timeout = timeout;
    }

    private static Identifier identifier(KERL_ kerl) {
        if (ProtobufEventFactory.from(kerl.getEvents(kerl.getEventsCount() - 1))
                                .event() instanceof EstablishmentEvent establishment) {
            return establishment.getIdentifier();
        }
        return null;
    }

    /**
     * Transactional provisioning
     */
    public static boolean tokenProvision(DSLContext dsl, SessionServices services, String subject, String token) {
        return dsl.transactionResult(ctx -> {
            var context = DSL.using(ctx);
            TokenValidator validator = services.call(TOKEN_VALIDATOR);
            var validated = (ValidatedToken<InitialProvisioning>) validator.apply(token);
            if (validated == null) {
                return false;
            }
            var message = validated.message;
            var boundToken = qb64(validated.token.hash());
            var namespace = message.getNamespace();

            var resolved = AbstractOracle.resolveObj(context, namespace, boundToken, 0L);
            // Have we used this token?
            if (resolved != null) {
                return false;
            }
            // Mark the token as used
            AbstractOracle.addObj(context, namespace, boundToken, new Oracle.NamespacedId(namespace, 0L, 0L));

            // Provision the subject with all predicate relationships
            message.getRelationshipsList().forEach(rel -> {
                AbstractOracle.addObj(context, namespace, subject, new Oracle.NamespacedId(namespace, rel, 0L));
            });

            // Provision the subject with all mappings
            for (var entry : message.getMappingsMap().entrySet()) {
                var rel = entry.getKey();
                var subj = entry.getValue();
                AbstractOracle.addSubj(context, namespace, subject, new Oracle.NamespacedId(namespace, rel, 0L));
                var s = AbstractOracle.resolveSubj(context, namespace, subject, rel);
                try {
                    AbstractOracle.addEdge(context, s.value1(), Oracle.SUBJECT_TYPE, subj);
                } catch (SQLException e) {
                    log.warn("Exception while adding edge {}:{}", subj, s.value1(), e);
                    return false;
                }
            }
            for (var entry : message.getAssertionsMap().entrySet()) {
                var rel = entry.getKey();
                var obj = entry.getValue();
                AbstractOracle.addSubj(context, namespace, subject, new Oracle.NamespacedId(namespace, rel, 0L));
                var s = AbstractOracle.resolveSubj(context, namespace, subject, rel);
                AbstractOracle.addAssert(context, s.value1(), obj);
            }
            return true;
        });
    }

    public static boolean tokenProvision(Connection connection, SessionServices services, String subject,
                                         String token) {
        return tokenProvision(DSL.using(connection, SQLDialect.H2), services, subject, token);
    }

    @Override
    public boolean provision(Attestation attestation) {
        if (true) {
            return true;
        }
        FernetToken fernetAttestation;
        try {
            fernetAttestation = attestation.getAttestation().unpack(FernetToken.class);
        } catch (InvalidProtocolBufferException e) {
            return false;
        }
        var identifier = (SelfAddressingIdentifier) identifier(attestation.getKerl());
        if (identifier == null) {
            return false;
        }
        var token = fernetAttestation.getToken();
        return provision(identifier, token);
    }

    private boolean provision(SelfAddressingIdentifier identifier, String token) {
        var call = mutator.call("{ ? = call nut.tokenProvision(?, ?) }", Collections.singletonList(JDBCType.BOOLEAN),
                                qb64(identifier.getDigest()), token);
        CompletableFuture<SqlStateMachine.CallResult> submitted;
        try {
            submitted = mutator.execute(call, timeout);
        } catch (InvalidTransaction e) {
            throw new IllegalStateException(e);
        }
        try {
            return (Boolean) submitted.thenApply(callResult -> callResult.outValues.get(0))
                                      .get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            log.warn("Cannot provision: {}", identifier.getDigest(), e.getCause());
            return false;
        } catch (TimeoutException e) {
            log.warn("Timed out provisioning: {}", identifier.getDigest());
            return false;
        }
    }

    public interface TokenValidator extends Function<String, ValidatedToken<? extends Message>> {
    }

    public record ValidatedToken<T extends Message>(TokenGenerator.HashedToken token, T message) {
    }
}
