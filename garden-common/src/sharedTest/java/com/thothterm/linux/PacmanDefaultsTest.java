package com.thothterm.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class PacmanDefaultsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final JvmFileOps ops = new JvmFileOps();

    private static String imageMirrorlist() throws Exception {
        try (InputStream in = PacmanDefaultsTest.class.getResourceAsStream("/garden/arch-mirrorlist-03a4c669ed6f")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private File rootWithMirrorlist(String text) throws Exception {
        File root = temporaryFolder.newFolder("rootfs");
        File dir = new File(root, "etc/pacman.d");
        assertTrue(dir.mkdirs());
        Files.write(new File(dir, "mirrorlist").toPath(), text.getBytes(StandardCharsets.UTF_8));
        return root;
    }

    private static List<String> activeServers(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n")) if (line.startsWith("Server = ")) out.add(line.substring(9));
        return out;
    }

    @Test
    public void fixtureIsTheImagesMirrorlist() throws Exception {
        assertEquals(PacmanDefaults.IMAGE_MIRRORLIST_SHA256, PacmanDefaults.sha256(imageMirrorlist()));
        assertEquals("only the redirector is active in the image", 1, activeServers(imageMirrorlist()).size());
    }

    @Test
    public void theImagesMirrorlistGetsFallbacksAfterTheRedirectorOnce() throws Exception {
        File root = rootWithMirrorlist(imageMirrorlist());
        assertTrue(PacmanDefaults.enableFallbackMirrors(ops, root));
        String now = new String(Files.readAllBytes(new File(root, "etc/pacman.d/mirrorlist").toPath()), StandardCharsets.UTF_8);
        List<String> servers = activeServers(now);
        assertEquals(13, servers.size());
        assertEquals("the redirector stays first", "http://mirror.archlinuxarm.org/$arch/$repo", servers.get(0));
        assertTrue(servers.contains("http://fl.us.mirror.archlinuxarm.org/$arch/$repo"));
        assertTrue(now.contains(PacmanDefaults.FALLBACK_NOTE));
        assertTrue("trailing newline kept", now.endsWith("\n"));
        // Only the Server lines changed: the same text with them commented out again is the image's.
        String back = now.replace(PacmanDefaults.FALLBACK_NOTE, "")
                .replaceAll("(?m)^Server = (http://[a-z0-9.]+\\.mirror\\.archlinuxarm\\.org)", "# Server = $1");
        assertEquals(imageMirrorlist(), back);
        assertFalse("never twice", PacmanDefaults.enableFallbackMirrors(ops, root));
    }

    @Test
    public void aChangedMirrorlistIsLeftAlone() throws Exception {
        String mine = imageMirrorlist().replace("# Server = http://de.mirror", "Server = http://de.mirror");
        File root = rootWithMirrorlist(mine);
        assertFalse(PacmanDefaults.enableFallbackMirrors(ops, root));
        assertEquals(mine, new String(Files.readAllBytes(new File(root, "etc/pacman.d/mirrorlist").toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void theHwdbHookIsMaskedWithADevNullSymlinkOnce() throws Exception {
        File root = rootWithMirrorlist(imageMirrorlist());
        assertTrue(PacmanDefaults.maskHwdbHook(ops, root));
        File link = new File(root, "etc/pacman.d/hooks/25-systemd-hwdb.hook");
        assertTrue(Files.isSymbolicLink(link.toPath()));
        assertEquals("/dev/null", Files.readSymbolicLink(link.toPath()).toString());
        assertFalse(PacmanDefaults.maskHwdbHook(ops, root));
    }

    @Test
    public void aHookTheUserWroteIsKept() throws Exception {
        File root = rootWithMirrorlist(imageMirrorlist());
        File hooks = new File(root, "etc/pacman.d/hooks");
        assertTrue(hooks.mkdirs());
        File own = new File(hooks, "25-systemd-hwdb.hook");
        Files.write(own.toPath(), "[Trigger]\n".getBytes(StandardCharsets.UTF_8));
        assertFalse(PacmanDefaults.maskHwdbHook(ops, root));
        assertFalse(Files.isSymbolicLink(own.toPath()));
    }
}
