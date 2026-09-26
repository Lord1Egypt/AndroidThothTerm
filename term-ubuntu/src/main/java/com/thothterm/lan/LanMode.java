/*
 * Copyright (C) 2026 ThothTerm.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thothterm.lan;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * LAN Mode's on/off state, free of Android APIs.
 * <p>
 * OFF means nothing listens, no browser credential is valid and no browser
 * terminal exists. Every ON builds a new {@link LanAuth}, so credentials from
 * an earlier ON can never be valid again. Nothing is persisted: after the
 * process dies LAN Mode is simply off.
 */
final class LanMode {
    static final int DEFAULT_PORT = 7681;
    static final int PORT_ATTEMPTS = 10;
    static final long SWEEP_PERIOD_MS = 2_000;

    enum Failure { NONE, NO_NETWORK, PORT_IN_USE, NETWORK_LOST, START_FAILED }

    /** A consistent picture of LAN Mode for the phone's screen. */
    static final class Status {
        final boolean on;
        final String url;
        final int port;
        final LanAuth.PinView pin;
        final int pairedBrowsers;
        final int terminals;
        final int connectedTerminals;
        final Failure failure;

        Status(boolean on, String url, int port, LanAuth.PinView pin, int pairedBrowsers,
               int terminals, int connectedTerminals, Failure failure) {
            this.on = on;
            this.url = url;
            this.port = port;
            this.pin = pin;
            this.pairedBrowsers = pairedBrowsers;
            this.terminals = terminals;
            this.connectedTerminals = connectedTerminals;
            this.failure = failure;
        }
    }

    private final Pty.Factory ptys;
    private final LanServer.Assets assets;
    private final LanLog log;
    private final LanAuth.Clock clock;
    private final SecureRandom random;

    private LanAuth auth;
    private RemoteTerminals terminals;
    private LanServer server;
    private ScheduledExecutorService sweeper;
    private Failure failure = Failure.NONE;

    LanMode(Pty.Factory ptys, LanServer.Assets assets, LanLog log, LanAuth.Clock clock,
            SecureRandom random) {
        this.ptys = ptys;
        this.assets = assets;
        this.log = log;
        this.clock = clock;
        this.random = random;
    }

    synchronized boolean isOn() {
        return server != null;
    }

    /**
     * Start listening on {@code address}. Returns false, with
     * {@link Status#failure} set, when no port could be bound.
     */
    synchronized boolean start(InetAddress address, int firstPort, int attempts) {
        if (server != null) return true;
        LanAuth newAuth = new LanAuth(random, clock);
        RemoteTerminals newTerminals = new RemoteTerminals(ptys, clock, log, random);
        LanServer newServer = new LanServer(newAuth, newTerminals, assets, log);
        try {
            int port = newServer.start(address, firstPort, attempts);
            log.info("LAN Mode on; listening on port " + port);
        } catch (BindException e) {
            failure = Failure.PORT_IN_USE;
            log.warn("LAN Mode could not start: ports " + firstPort + "-"
                    + (firstPort + attempts - 1) + " are in use");
            return false;
        } catch (IOException e) {
            failure = Failure.START_FAILED;
            log.warn("LAN Mode could not start: " + e.getClass().getSimpleName());
            return false;
        }
        auth = newAuth;
        terminals = newTerminals;
        server = newServer;
        failure = Failure.NONE;
        auth.newPin();
        sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LAN sweeper");
            t.setDaemon(true);
            return t;
        });
        final RemoteTerminals swept = terminals;
        sweeper.scheduleWithFixedDelay(swept::sweep, SWEEP_PERIOD_MS, SWEEP_PERIOD_MS,
                TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Turn LAN Mode off: stop listening, drop every browser, hang up every
     * browser terminal and forget every credential. Phone windows are not
     * touched. {@code why} is recorded as the failure when it is not NONE.
     */
    synchronized void stop(Failure why) {
        if (server == null) {
            if (why != Failure.NONE) failure = why;
            return;
        }
        server.stop();
        auth.revokeAll();
        terminals.closeAll();
        sweeper.shutdownNow();
        server = null;
        auth = null;
        terminals = null;
        sweeper = null;
        failure = why;
        log.info("LAN Mode off" + (why == Failure.NONE ? "" : "; reason=" + why));
    }

    /** Record why LAN Mode could not start, without it having started. */
    synchronized void fail(Failure why) {
        failure = why;
    }

    synchronized LanAuth.PinView newPin() {
        if (auth == null) return null;
        LanAuth.PinView pin = auth.newPin();
        log.info("New pairing PIN issued");
        return pin;
    }

    synchronized Status status() {
        if (server == null) {
            return new Status(false, null, -1, null, 0, 0, 0, failure);
        }
        return new Status(true, server.url(), server.port(), auth.pinView(), auth.pairedBrowsers(),
                terminals.count(), terminals.attachedCount(), Failure.NONE);
    }

    /** Session-leader pids of the browser terminals, for Exit's final sweep. */
    synchronized int[] terminalPids() {
        return terminals == null ? new int[0] : terminals.pids();
    }
}
