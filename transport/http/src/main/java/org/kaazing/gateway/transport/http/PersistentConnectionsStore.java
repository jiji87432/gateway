/**
 * Copyright (c) 2007-2014 Kaazing Corporation. All rights reserved.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.kaazing.gateway.transport.http;

import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.kaazing.gateway.resource.address.ResourceAddress;
import org.kaazing.gateway.transport.BridgeSession;
import org.kaazing.gateway.transport.http.bridge.filter.HttpFilterAdapter;
import org.kaazing.mina.core.session.IoSessionEx;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PersistentConnectionsStore {

    private static final String IDLE_FILTER = HttpProtocol.NAME + "#idle";

    // transport address -> set of persistent connections
    private final Map<ResourceAddress, Set<IoSessionEx>> connections;
    private final Logger logger;
    private final CloseListener closeListener;
    private final HttpConnectIdleFilter idleFilter;

    PersistentConnectionsStore(Logger logger) {
        this.connections = new HashMap<>();
        this.logger = logger;
        this.closeListener = new CloseListener(this);
        this.idleFilter = new HttpConnectIdleFilter(this, logger);
    }

    /*
     * Recycle existing transport session so that it can be used as a http
     * persistent connection
     */
    public void recycle(IoSessionEx session, Integer keepAliveTimeout) {
        assert keepAliveTimeout != null;
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Recycling (adding to pool) http persistent connection %s", session));
        }
        ResourceAddress address = BridgeSession.REMOTE_ADDRESS.get(session);
        Set<IoSessionEx> sessions = connections.get(address);
        if (sessions == null) {
            sessions = new HashSet<>();
            connections.put(address, sessions);
        }
        sessions.add(session);
        CloseFuture closeFuture = session.getCloseFuture();
        closeFuture.addListener(closeListener);
        session.getConfig().setBothIdleTime(keepAliveTimeout);
        session.getFilterChain().addLast(IDLE_FILTER, idleFilter);
    }

    /*
     * Returns an existing transport session for the resource address that can be reused
     *
     * @return a reusable IoSession for the address
     *         otherwise null
     */
    public IoSession take(ResourceAddress address) {
        Set<IoSessionEx> sessions = connections.get(address);
        if (sessions != null && !sessions.isEmpty()) {
            IoSessionEx session = sessions.iterator().next();
            sessions.remove(session);

            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Reusing (removing from pool) http persistent connection %s", session));
            }
            session.getCloseFuture().removeListener(closeListener);
            session.getConfig().setBothIdleTime(0);
            session.getFilterChain().remove(IDLE_FILTER);

            return session;
        }

        return null;
    }

    /*
     * Returns an existing transport session for the given resource address
     * that can be reused
     *
     * @return a reusable IoSession for the address
     *         otherwise null
     */
    public void remove(IoSessionEx session) {
        ResourceAddress address = BridgeSession.REMOTE_ADDRESS.get(session);
        Set<IoSessionEx> sessions = connections.get(address);
        if (sessions != null && !sessions.isEmpty()) {
            boolean removed = sessions.remove(session);
            if (removed && logger.isDebugEnabled()) {
                logger.debug(String.format("Removed http persistent connection %s", session));
            }
        }
    }

    /*
     * If a session is closed, it will be removed from this store using this
     * CloseFuture listener
     */
    private static class CloseListener implements IoFutureListener<CloseFuture> {

        private final PersistentConnectionsStore store;

        CloseListener(PersistentConnectionsStore store) {
            this.store = store;
        }

        @Override
        public void operationComplete(CloseFuture future) {
            IoSessionEx session = (IoSessionEx) future.getSession();
            store.remove(session);
        }
    }

    /*
     * Filter to detect if a persistent connection is idle
     */
    private static class HttpConnectIdleFilter extends HttpFilterAdapter<IoSessionEx> {
        private final PersistentConnectionsStore store;
        private final Logger logger;

        HttpConnectIdleFilter(PersistentConnectionsStore store, Logger logger) {
            this.store = store;
            this.logger = logger;
        }

        @Override
        public void sessionIdle(NextFilter nextFilter, IoSession session, IdleStatus status) throws Exception {
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Idle http persistent connection %s", session));
            }
            store.remove((IoSessionEx)session);
            session.close(false);
            super.sessionIdle(nextFilter, session, status);
        }
    }

}
