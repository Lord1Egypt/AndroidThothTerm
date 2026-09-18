/*
 * Copyright (C) 2026 ThothTerm.  All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.thothterm.linux;

import java.net.InetAddress;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Renders trusted Android {@link InetAddress} values as resolv.conf text. */
final class ResolverFile {
    private ResolverFile() {
    }

    static String render(List<InetAddress> servers) {
        StringBuilder text = new StringBuilder("# Managed by ThothTerm from Android's active network.\n");
        Set<String> unique = new LinkedHashSet<>();
        if (servers != null) {
            for (InetAddress server : servers) {
                if (server == null || server.isAnyLocalAddress()) continue;
                unique.add(server.getHostAddress());
            }
        }
        for (String address : unique) {
            text.append("nameserver ").append(address).append('\n');
        }
        return text.toString();
    }

    static int usableServerCount(List<InetAddress> servers) {
        if (servers == null) return 0;
        Set<String> unique = new LinkedHashSet<>();
        for (InetAddress server : servers) {
            if (server != null && !server.isAnyLocalAddress()) {
                unique.add(server.getHostAddress());
            }
        }
        return unique.size();
    }
}
