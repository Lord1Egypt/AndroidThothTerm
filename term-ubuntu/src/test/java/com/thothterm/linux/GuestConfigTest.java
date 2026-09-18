/*
 * Copyright (C) 2026 ThothTerm.  All rights reserved.
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

package com.thothterm.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GuestConfigTest {
    private static final String BASE_PASSWD =
            "root:x:0:0:root:/root:/bin/bash\n"
                    + "daemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin\n";

    private static final String BASE_GROUP =
            "root:x:0:\n"
                    + "sudo:x:27:\n"
                    + "users:x:100:\n";

    private static final String BASE_SHADOW =
            "root:*:20691:0:99999:7:::\n"
                    + "daemon:*:20691:0:99999:7:::\n";

    @Test
    public void passwdAddsThothOnceAndPreservesOtherEntries() {
        String once = GuestConfig.ensurePasswd(BASE_PASSWD);
        assertTrue(once.contains("daemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin\n"));
        assertTrue(once.contains("thoth:x:1000:1000:Thoth User:/home/thoth:/bin/bash\n"));

        String twice = GuestConfig.ensurePasswd(once);
        assertEquals(once, twice);
        assertEquals(1, occurrences(twice, "thoth:x:1000:1000:"));
    }

    @Test
    public void groupAddsThothAndMembershipIdempotently() {
        String once = GuestConfig.ensureGroup(BASE_GROUP);
        assertTrue(once.contains("thoth:x:1000:\n"));
        assertTrue(once.contains("sudo:x:27:thoth\n"));

        String twice = GuestConfig.ensureGroup(once);
        assertEquals(once, twice);
        assertEquals(1, occurrences(twice, "thoth:x:1000:"));
    }

    @Test
    public void groupPreservesExistingSudoMembers() {
        String text = "sudo:x:27:alice\n";
        String updated = GuestConfig.ensureGroup(text);
        assertTrue(updated.contains("sudo:x:27:alice,thoth\n"));
        assertFalse(updated.contains("alice\n,thoth"));
    }

    @Test
    public void groupRecognisesExistingMembership() {
        String text = "thoth:x:1000:\nsudo:x:27:bob,thoth\n";
        assertEquals(text, GuestConfig.ensureGroup(text));
    }

    @Test
    public void shadowAddsLockedThothEntryOnce() {
        String once = GuestConfig.ensureShadow(BASE_SHADOW);
        assertTrue(once.contains("thoth:!:19000:0:99999:7:::\n"));
        assertEquals(once, GuestConfig.ensureShadow(once));
    }

    @Test
    public void passwordlessSuCustomizationIsRemoved() {
        String base = "auth sufficient pam_permit.so\n"
                + "auth sufficient pam_rootok.so\n@include common-auth\n";
        String cleaned = GuestConfig.removePasswordlessSu(base);
        assertFalse(cleaned.contains("pam_permit.so"));
        assertTrue(cleaned.contains("auth sufficient pam_rootok.so"));
        assertEquals(cleaned, GuestConfig.removePasswordlessSu(cleaned));
    }

    @Test
    public void passwordlessSuRemovalLeavesStockPolicyUntouched() {
        String stock = "auth sufficient pam_rootok.so\n@include common-auth\n";
        assertEquals(stock, GuestConfig.removePasswordlessSu(stock));
    }

    @Test
    public void debconfConfigAppendsCorrectStanza() {
        String base = "Name: base-passwd/group-add\nTemplate: base-passwd/group-add\n";
        String once = GuestConfig.ensureDebconfFrontendConfig(base);
        assertTrue(once.contains("Name: debconf/frontend\nTemplate: debconf/frontend\nValue: Teletype\n"));
        assertEquals(once, GuestConfig.ensureDebconfFrontendConfig(once));
    }

    @Test
    public void debconfConfigUpdatesExistingValue() {
        String base = "Name: debconf/frontend\nTemplate: debconf/frontend\nValue: Dialog\n"
                + "Owners: debconf\n\nName: other\nTemplate: other\n";
        String updated = GuestConfig.ensureDebconfFrontendConfig(base);
        assertTrue(updated.contains("Value: Teletype\n"));
        assertFalse(updated.contains("Value: Dialog\n"));
        assertTrue(updated.contains("Name: other\nTemplate: other\n"));
    }

    @Test
    public void debconfTemplateUsesNameFieldAndIsIdempotent() {
        String base = "Name: base-passwd/group-add\nType: boolean\n";
        String once = GuestConfig.ensureDebconfFrontendTemplate(base);
        assertTrue(once.contains("Name: debconf/frontend\nType: select\n"));
        assertFalse(once.contains("Template: debconf/frontend"));
        assertEquals(once, GuestConfig.ensureDebconfFrontendTemplate(once));
    }

    @Test
    public void managedSudoHelperDetection() {
        assertTrue(GuestConfig.isManagedSudoHelper(
                "#!/bin/bash\n# Managed by ThothTerm fallback sudo\n"));
        assertTrue(GuestConfig.isManagedSudoHelper(
                "# Managed by ThothTerm for the embedded Ubuntu/PRoot guest.\n"));
        assertFalse(GuestConfig.isManagedSudoHelper("#!/bin/sh\necho user replacement\n"));
        assertFalse(GuestConfig.isManagedSudoHelper(null));
    }

    @Test
    public void localeFixDoesNotEvalPaths() {
        String script = GuestConfig.localeFixScript();
        assertTrue(script.contains("export LANG=C.UTF-8"));
        assertFalse(script.contains("eval"));
        assertFalse(script.contains("locale-check"));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
