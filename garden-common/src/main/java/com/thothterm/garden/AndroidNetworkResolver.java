/*
 * Copyright (C) 2026 ThothTerm.  All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.thothterm.garden;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;

import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

/** Keeps an app-private Linux resolver synchronized with Android networking. */
public final class AndroidNetworkResolver {
    private static volatile AndroidNetworkResolver sInstance;

    private final ConnectivityManager connectivity;
    private final File resolverFile;
    private boolean lastNetworkAvailable;
    private int lastServerCount = -1;

    private AndroidNetworkResolver(Context context) {
        Context app = context.getApplicationContext();
        connectivity = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        resolverFile = new File(new File(new File(app.getFilesDir(), "linux"), "runtime"),
                "resolv.conf");
        registerNetworkCallback();
    }

    public static void init(Context context) {
        if (sInstance == null) {
            synchronized (AndroidNetworkResolver.class) {
                if (sInstance == null) sInstance = new AndroidNetworkResolver(context);
            }
        }
    }

    public static AndroidNetworkResolver get() {
        AndroidNetworkResolver resolver = sInstance;
        if (resolver == null) throw new IllegalStateException("Network resolver not initialized");
        return resolver;
    }

    public File resolverFile() {
        return resolverFile;
    }

    /** Refresh synchronously before a session launches; lack of DNS does not block Linux. */
    public synchronized boolean refresh() {
        List<InetAddress> servers = Collections.emptyList();
        Network active = connectivity == null ? null : connectivity.getActiveNetwork();
        LinkProperties properties = active == null ? null : connectivity.getLinkProperties(active);
        if (properties != null) servers = properties.getDnsServers();

        int count = ResolverFile.usableServerCount(servers);
        boolean available = active != null;
        boolean changed = available != lastNetworkAvailable || count != lastServerCount;
        if (changed) {
            if (!available) {
                ThothLog.w(LogCategory.NETWORK, "No active Android network available");
            } else {
                ThothLog.i(LogCategory.NETWORK, "Android network available");
            }
            ThothLog.d(LogCategory.NETWORK, "DNS server count=" + count);
        }

        try {
            writeAtomically(ResolverFile.render(servers));
            if (count == 0 && changed) {
                ThothLog.w(LogCategory.NETWORK, "No active DNS servers available");
            } else if (changed) {
                ThothLog.i(LogCategory.NETWORK, "Linux resolver prepared");
            }
            lastNetworkAvailable = available;
            lastServerCount = count;
            return count > 0;
        } catch (IOException e) {
            ThothLog.e(LogCategory.NETWORK, "Linux resolver preparation failed", e);
            return false;
        }
    }

    private void registerNetworkCallback() {
        if (connectivity == null) return;
        try {
            connectivity.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    refresh();
                }

                @Override
                public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
                    refresh();
                }

                @Override
                public void onLost(Network network) {
                    refresh();
                }
            });
        } catch (RuntimeException e) {
            ThothLog.w(LogCategory.NETWORK, "Android network monitoring unavailable");
        }
    }

    private void writeAtomically(String text) throws IOException {
        File parent = resolverFile.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create resolver directory");
        }
        File temporary = new File(parent, "resolv.conf.tmp");
        FileOutputStream output = new FileOutputStream(temporary, false);
        try {
            output.write(text.getBytes("US-ASCII"));
            output.getFD().sync();
        } finally {
            output.close();
        }
        if (!temporary.renameTo(resolverFile)) {
            if (resolverFile.exists() && !resolverFile.delete()) {
                throw new IOException("Cannot replace resolver file");
            }
            if (!temporary.renameTo(resolverFile)) {
                throw new IOException("Cannot install resolver file");
            }
        }
    }
}
