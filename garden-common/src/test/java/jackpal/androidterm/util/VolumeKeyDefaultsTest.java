package jackpal.androidterm.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * The volume keys must not be Ctrl and Fn by default. Two presses of Volume
 * Down used to lock Ctrl -- a triangle in the cursor, and every letter then
 * sent as a control character -- without the volume changing, so a user
 * turning the volume down found the terminal no longer typed.
 */
public class VolumeKeyDefaultsTest {
    private static String defaultOf(String xml, String name) {
        Matcher m = Pattern.compile("<string name=\"" + name + "\"[^>]*>([^<]*)</string>").matcher(xml);
        if (!m.find()) throw new AssertionError(name + " missing");
        return m.group(1);
    }

    @Test
    public void noKeyIsAModifierByDefault() throws Exception {
        File here = new File("").getAbsoluteFile();
        File repo = new File(here, "third_party").isDirectory() ? here : here.getParentFile();
        String xml = new String(Files.readAllBytes(
                new File(repo, "garden-common/src/main/res/values/defaults.xml").toPath()), StandardCharsets.UTF_8);
        assertEquals(TermSettings.CONTROL_KEY_ID_NONE, Integer.parseInt(defaultOf(xml, "pref_controlkey_default")));
        assertEquals(TermSettings.FN_KEY_ID_NONE, Integer.parseInt(defaultOf(xml, "pref_fnkey_default")));
    }
}
