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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;

/**
 * Which address LAN Mode may listen on: a private (RFC 1918) IPv4 address of
 * a Wi-Fi or Ethernet network, Wi-Fi first. Never loopback, never a mobile
 * data or VPN interface, never a public address.
 */
final class LanAddresses {
    static final class Candidate {
        final boolean wifi;
        final boolean ethernet;
        final InetAddress address;
        /** The platform network the address belongs to. */
        final Object network;

        Candidate(boolean wifi, boolean ethernet, InetAddress address, Object network) {
            this.wifi = wifi;
            this.ethernet = ethernet;
            this.address = address;
            this.network = network;
        }
    }

    private LanAddresses() {
    }

    static boolean isPrivateIpv4(InetAddress address) {
        // Inet4Address.isSiteLocalAddress() is exactly 10/8, 172.16/12 and 192.168/16.
        return address instanceof Inet4Address && address.isSiteLocalAddress();
    }

    /** The candidate to listen on, or null when there is no usable LAN. */
    static Candidate choose(List<Candidate> candidates) {
        for (Candidate c : candidates) {
            if (c.wifi && isPrivateIpv4(c.address)) return c;
        }
        for (Candidate c : candidates) {
            if (c.ethernet && isPrivateIpv4(c.address)) return c;
        }
        return null;
    }
}
