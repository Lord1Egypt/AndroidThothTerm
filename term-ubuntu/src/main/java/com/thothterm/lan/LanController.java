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

import android.content.Context;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * LAN Mode for the app: picks the LAN address, keeps LAN Mode tied to that
 * network and to the terminal service, and tells the screen and the
 * notification when anything changes.
 * <p>
 * LAN Mode can only be on while the terminal service runs; the service
 * turns it off when it stops. It is off at every process start and is never
 * turned on by anything but the user.
 */
public final class LanController {
    public interface Listener {
        /** Called on the main thread. */
        void onLanChanged();
    }

    /** What the LAN Mode screen shows. */
    public static final class State {
        public final boolean on;
        public final String url;
        /** The PIN while it can be used, else null. */
        public final String pin;
        public final long pinExpiresAtMs;
        public final PinState pinState;
        public final int pairedBrowsers;
        public final int browserTerminals;
        public final int connectedTerminals;
        public final Problem problem;

        State(LanMode.Status s) {
            on = s.on;
            url = s.url;
            pin = s.pin == null ? null : s.pin.pin;
            pinExpiresAtMs = s.pin == null ? 0 : s.pin.expiresAtMs;
            pinState = s.pin == null ? PinState.NONE : PinState.valueOf(s.pin.state.name());
            pairedBrowsers = s.pairedBrowsers;
            browserTerminals = s.terminals;
            connectedTerminals = s.connectedTerminals;
            problem = Problem.valueOf(s.failure.name());
        }
    }

    public enum PinState { NONE, ACTIVE, USED, EXPIRED, LOCKED }

    public enum Problem { NONE, NO_NETWORK, PORT_IN_USE, NETWORK_LOST, START_FAILED }

    private static volatile LanController sInstance;

    private final ConnectivityManager connectivity;
    private final LanMode mode;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private boolean hostRunning;
    private Network boundNetwork;
    private InetAddress boundAddress;
    private ConnectivityManager.NetworkCallback networkCallback;

    private LanController(Context context) {
        Context app = context.getApplicationContext();
        connectivity = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        AssetManager assetManager = app.getAssets();
        LanServer.Assets assets = name -> read(assetManager, "lan/" + name);
        LanLog log = new LanLog() {
            @Override
            public void info(String message) {
                ThothLog.i(LogCategory.WEB, message);
            }

            @Override
            public void warn(String message) {
                ThothLog.w(LogCategory.SECURITY, message);
            }
        };
        mode = new LanMode(UbuntuPty.FACTORY, assets, log, System::currentTimeMillis,
                new SecureRandom());
    }

    public static void init(Context context) {
        if (sInstance == null) {
            synchronized (LanController.class) {
                if (sInstance == null) sInstance = new LanController(context);
            }
        }
    }

    public static LanController get() {
        LanController controller = sInstance;
        if (controller == null) throw new IllegalStateException("LAN Mode not initialized");
        return controller;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** The terminal service started; LAN Mode may now be turned on. */
    public void hostStarted() {
        hostRunning = true;
    }

    /** The terminal service is stopping: LAN Mode goes with it. */
    public void hostStopped() {
        hostRunning = false;
        stop();
    }

    public boolean isOn() {
        return mode.isOn();
    }

    public boolean canStart() {
        return hostRunning;
    }

    /** Turn LAN Mode on. Main thread. Returns whether it is now on. */
    public boolean start() {
        if (mode.isOn()) return true;
        if (!hostRunning) {
            mode.fail(LanMode.Failure.START_FAILED);
            notifyChanged();
            return false;
        }
        LanAddresses.Candidate lan = pickAddress();
        if (lan == null) {
            ThothLog.w(LogCategory.WEB, "LAN Mode not started: no Wi-Fi or Ethernet LAN address");
            mode.fail(LanMode.Failure.NO_NETWORK);
            notifyChanged();
            return false;
        }
        boolean on = mode.start(lan.address, LanMode.DEFAULT_PORT, LanMode.PORT_ATTEMPTS);
        if (on) {
            boundNetwork = (Network) lan.network;
            boundAddress = lan.address;
            watchNetwork();
        }
        notifyChanged();
        return on;
    }

    /** Turn LAN Mode off; the phone's own windows are not touched. */
    public void stop() {
        stop(LanMode.Failure.NONE);
    }

    private void stop(LanMode.Failure why) {
        boolean wasOn = mode.isOn();
        unwatchNetwork();
        mode.stop(why);
        boundNetwork = null;
        boundAddress = null;
        if (wasOn || why != LanMode.Failure.NONE) notifyChanged();
    }

    public State state() {
        return new State(mode.status());
    }

    public void newPin() {
        mode.newPin();
        notifyChanged();
    }

    /** Session leaders of the browser terminals, for Exit's final sweep. */
    public int[] terminalPids() {
        return mode.terminalPids();
    }

    @SuppressWarnings("deprecation") // getAllNetworks(): still the direct way to list them all.
    private LanAddresses.Candidate pickAddress() {
        if (connectivity == null) return null;
        List<LanAddresses.Candidate> candidates = new ArrayList<>();
        for (Network network : connectivity.getAllNetworks()) {
            NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
            LinkProperties link = connectivity.getLinkProperties(network);
            if (caps == null || link == null) continue;
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
            boolean wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            boolean ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
            for (LinkAddress la : link.getLinkAddresses()) {
                candidates.add(new LanAddresses.Candidate(wifi, ethernet, la.getAddress(), network));
            }
        }
        return LanAddresses.choose(candidates);
    }

    /** Off, fail closed, as soon as the LAN the server is bound to goes away. */
    private void watchNetwork() {
        if (connectivity == null) return;
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLost(Network network) {
                main.post(() -> {
                    if (network.equals(boundNetwork)) networkGone();
                });
            }

            @Override
            public void onLinkPropertiesChanged(Network network, LinkProperties link) {
                main.post(() -> {
                    if (!network.equals(boundNetwork) || boundAddress == null) return;
                    for (LinkAddress la : link.getLinkAddresses()) {
                        if (la.getAddress().equals(boundAddress)) return;
                    }
                    networkGone();
                });
            }
        };
        connectivity.registerNetworkCallback(request, networkCallback);
    }

    private void networkGone() {
        ThothLog.w(LogCategory.WEB, "LAN network lost; turning LAN Mode off");
        stop(LanMode.Failure.NETWORK_LOST);
    }

    private void unwatchNetwork() {
        if (networkCallback == null || connectivity == null) return;
        try {
            connectivity.unregisterNetworkCallback(networkCallback);
        } catch (IllegalArgumentException ignore) {
            // Already unregistered.
        }
        networkCallback = null;
    }

    private void notifyChanged() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            for (Listener l : listeners) l.onLanChanged();
        } else {
            main.post(this::notifyChanged);
        }
    }

    private static byte[] read(AssetManager assets, String path) throws IOException {
        InputStream in;
        try {
            in = assets.open(path);
        } catch (IOException e) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
