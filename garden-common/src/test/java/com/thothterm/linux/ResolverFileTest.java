/* Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0. */
package com.thothterm.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;

public class ResolverFileTest {
    @Test
    public void rendersIpv4AndIpv6WithoutShellSyntax() throws Exception {
        String text = ResolverFile.render(Arrays.asList(
                InetAddress.getByName("192.0.2.53"),
                InetAddress.getByName("2001:db8::53")));

        assertTrue(text.contains("nameserver 192.0.2.53\n"));
        assertTrue(text.contains("nameserver 2001:db8:0:0:0:0:0:53\n"));
        assertFalse(text.contains(";") || text.contains("`"));
        assertEquals(2, ResolverFile.usableServerCount(Arrays.asList(
                InetAddress.getByName("192.0.2.53"),
                InetAddress.getByName("2001:db8::53"))));
    }

    @Test
    public void emptyDnsSetProducesExplicitManagedFile() {
        String text = ResolverFile.render(Collections.emptyList());
        assertTrue(text.startsWith("# Managed by ThothTerm"));
        assertFalse(text.contains("nameserver"));
        assertEquals(0, ResolverFile.usableServerCount(Collections.emptyList()));
    }

    @Test
    public void deduplicatesServers() throws Exception {
        InetAddress address = InetAddress.getByName("192.0.2.53");
        assertEquals(1, ResolverFile.usableServerCount(Arrays.asList(address, address)));
    }
}
