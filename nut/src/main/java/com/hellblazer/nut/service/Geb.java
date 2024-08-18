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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.hellblazer.delos.comm.grpc.ServerContextSupplier;
import com.hellblazer.delos.cryptography.Digest;
import com.hellblazer.delos.cryptography.ssl.CertificateValidator;
import com.hellblazer.delos.cryptography.ssl.NodeKeyManagerFactory;
import com.hellblazer.delos.cryptography.ssl.NodeTrustManagerFactory;
import com.hellblazer.delos.cryptography.ssl.TlsInterceptor;
import com.hellblazer.delos.delphinius.Oracle;
import com.hellblazer.delos.protocols.ClientIdentity;
import com.hellblazer.delos.state.Mutator;
import io.grpc.*;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.*;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.SocketAddress;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/**
 * The secrets storage
 *
 * @author hal.hildebrand
 **/
public class Geb {

    private final DSLContext dslCtx;
    private final Mutator    mutator;

    public Geb(Connection connection, Mutator mutator) {
        this.dslCtx = DSL.using(connection);
        this.mutator = mutator;
    }

    public void delete(KeyVersion key) throws SQLException {
    }

    public String get(KeyVersion key) throws SQLException {
        return null;
    }

    public int put(PutValue value) throws SQLException {
        return 0;
    }

    public record PutValue(Oracle.Object key, String value, int cas) {
    }

    public record KeyVersion(Oracle.Object key, int version) {
    }

}
