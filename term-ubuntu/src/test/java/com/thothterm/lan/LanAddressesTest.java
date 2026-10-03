/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package com.thothterm.lan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;

public class LanAddressesTest {
    private static InetAddress ip(String s) throws Exception {
        return InetAddress.getByName(s);
    }

    @Test
    public void onlyPrivateIpv4IsAcceptable() throws Exception {
        assertTrue(LanAddresses.isPrivateIpv4(ip("192.168.1.103")));
        assertTrue(LanAddresses.isPrivateIpv4(ip("10.0.0.5")));
        assertTrue(LanAddresses.isPrivateIpv4(ip("172.16.0.1")));
        assertTrue(LanAddresses.isPrivateIpv4(ip("172.31.255.254")));
        assertFalse(LanAddresses.isPrivateIpv4(ip("172.32.0.1")));
        assertFalse(LanAddresses.isPrivateIpv4(ip("8.8.8.8")));
        assertFalse(LanAddresses.isPrivateIpv4(ip("100.64.0.1")));   // carrier-grade NAT
        assertFalse(LanAddresses.isPrivateIpv4(ip("127.0.0.1")));
        assertFalse(LanAddresses.isPrivateIpv4(ip("169.254.1.1")));
        assertFalse(LanAddresses.isPrivateIpv4(ip("0.0.0.0")));
        assertFalse(LanAddresses.isPrivateIpv4(ip("fe80::1")));
        assertFalse(LanAddresses.isPrivateIpv4(ip("fd00::1")));
    }

    @Test
    public void prefersWifiThenEthernetAndNeverOtherTransports() throws Exception {
        LanAddresses.Candidate cellular = new LanAddresses.Candidate(false, false, ip("10.1.2.3"), "rmnet");
        LanAddresses.Candidate wifiV6 = new LanAddresses.Candidate(true, false, ip("fe80::2"), "wlan");
        LanAddresses.Candidate wifi = new LanAddresses.Candidate(true, false, ip("192.168.1.103"), "wlan");
        LanAddresses.Candidate eth = new LanAddresses.Candidate(false, true, ip("10.0.0.7"), "eth");
        LanAddresses.Candidate wifiPublic = new LanAddresses.Candidate(true, false, ip("203.0.113.9"), "wlan");

        assertEquals(wifi, LanAddresses.choose(Arrays.asList(cellular, eth, wifiV6, wifi)));
        assertEquals(eth, LanAddresses.choose(Arrays.asList(cellular, eth, wifiPublic)));
        assertNull(LanAddresses.choose(Arrays.asList(cellular, wifiPublic, wifiV6)));
        assertNull(LanAddresses.choose(Collections.emptyList()));
    }
}
