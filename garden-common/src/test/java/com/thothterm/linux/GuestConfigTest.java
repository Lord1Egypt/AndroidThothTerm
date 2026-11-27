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
        String once = GuestConfig.ensurePasswd(BASE_PASSWD, "thoth");
        assertTrue(once.contains("daemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin\n"));
        assertTrue(once.contains("thoth:x:1000:1000:Thoth User:/home/thoth:/bin/bash\n"));

        String twice = GuestConfig.ensurePasswd(once, "thoth");
        assertEquals(once, twice);
        assertEquals(1, occurrences(twice, "thoth:x:1000:1000:"));
    }

    @Test
    public void groupAddsThothAndMembershipIdempotently() {
        String once = GuestConfig.ensureGroup(BASE_GROUP, "sudo", "thoth");
        assertTrue(once.contains("thoth:x:1000:\n"));
        assertTrue(once.contains("sudo:x:27:thoth\n"));

        String twice = GuestConfig.ensureGroup(once, "sudo", "thoth");
        assertEquals(once, twice);
        assertEquals(1, occurrences(twice, "thoth:x:1000:"));
    }

    @Test
    public void groupPreservesExistingSudoMembers() {
        String text = "sudo:x:27:alice\n";
        String updated = GuestConfig.ensureGroup(text, "sudo", "thoth");
        assertTrue(updated.contains("sudo:x:27:alice,thoth\n"));
        assertFalse(updated.contains("alice\n,thoth"));
    }

    /** Arch: administrators are the existing wheel group, never a new gid 27. */
    @Test
    public void groupUsesTheEditionsAdminGroup() {
        String arch = "root:x:0:root\nwheel:x:998:\nusers:x:984:\n";
        String once = GuestConfig.ensureGroup(arch, "wheel", "thoth");
        assertTrue(once.contains("wheel:x:998:thoth\n"));
        assertFalse(once.contains("sudo:"));
        assertEquals(once, GuestConfig.ensureGroup(once, "wheel", "thoth"));
        // A missing admin group other than Debian's sudo is never invented.
        String noWheel = GuestConfig.ensureGroup("root:x:0:\n", "wheel", "thoth");
        assertFalse(noWheel.contains("wheel"));
        assertTrue(noWheel.contains("thoth:x:1000:\n"));
    }

    @Test
    public void groupRecognisesExistingMembership() {
        String text = "thoth:x:1000:\nsudo:x:27:bob,thoth\n";
        assertEquals(text, GuestConfig.ensureGroup(text, "sudo", "thoth"));
    }

    @Test
    public void shadowAddsLockedThothEntryOnce() {
        String once = GuestConfig.ensureShadow(BASE_SHADOW, "thoth");
        assertTrue(once.contains("thoth:!:19000:0:99999:7:::\n"));
        assertEquals(once, GuestConfig.ensureShadow(once, "thoth"));
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

    @Test
    public void hostsAddsLoopbackEntriesAndIsIdempotent() {
        String once = GuestConfig.ensureHosts("", "thothterm");
        assertTrue(once.contains("127.0.0.1 localhost\n"));
        assertTrue(once.contains("::1 localhost ip6-localhost ip6-loopback\n"));
        assertEquals(once, GuestConfig.ensureHosts(once, "thothterm"));
    }

    @Test
    public void hostsPreservesUserEntriesAndRecognisesExistingLoopback() {
        String base = "10.0.0.5 myhost\n127.0.0.1 localhost myhost\n";
        String result = GuestConfig.ensureHosts(base, "thothterm");
        assertTrue(result.startsWith("10.0.0.5 myhost\n"));
        assertTrue(result.contains("127.0.0.1 localhost myhost\n"));
        assertEquals(1, occurrences(result, "127.0.0.1 "));
        assertTrue(result.contains("::1 localhost ip6-localhost ip6-loopback\n"));
    }

    @Test
    public void groupsNamesAndroidSupplementaryGidsAndIsIdempotent() {
        String once = GuestConfig.ensureGroups("", 10698);
        assertTrue(once.contains("aid_inet:x:3003:\n"));
        assertTrue(once.contains("aid_everybody:x:9997:\n"));
        // uid 10698 -> appId 698 -> cache 20698, shared 50698
        assertTrue(once.contains("aid_cache:x:20698:\n"));
        assertTrue(once.contains("aid_all:x:50698:\n"));
        assertEquals(once, GuestConfig.ensureGroups(once, 10698));
    }

    @Test
    public void groupsPreserveExistingEntriesAndNeverDuplicateAGid() {
        String base = "root:x:0:\nthoth:x:1000:\naid_inet:x:3003:\n";
        String result = GuestConfig.ensureGroups(base, 10042);
        assertTrue(result.startsWith("root:x:0:\nthoth:x:1000:\n"));
        assertEquals(1, occurrences(result, "aid_inet:x:3003:"));
        assertEquals(1, occurrences(result, ":3003:"));
        assertTrue(result.contains("aid_cache:x:20042:\n"));
        assertTrue(result.contains("aid_all:x:50042:\n"));
    }

    @Test
    public void groupsSkipDerivedEntriesForNonAppUid() {
        // A system uid below AID_APP_START has no app-scoped cache/shared gid.
        String result = GuestConfig.ensureGroups("", 1000);
        assertTrue(result.contains("aid_inet:x:3003:\n"));
        assertFalse(result.contains("aid_cache"));
        assertFalse(result.contains("aid_all"));
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

    private static final String DPKG_STATUS =
            "Package: sudo-common\nStatus: install ok installed\nVersion: 1.2ubuntu\n\n"
                    + "Package: sudo\nStatus: install ok half-configured\nPriority: optional\n"
                    + "Version: 1.9.17p2-1ubuntu3.1\n\n"
                    + "Package: tree\nStatus: deinstall ok config-files\nVersion: 2.2.1-1\n";

    @Test
    public void readsTheDpkgStateOfAPackage() {
        // After an interrupted upgrade: present but unfinished, never "absent".
        assertEquals(GuestConfig.PackageState.UNFINISHED, GuestConfig.packageState(DPKG_STATUS, "sudo"));
        // "sudo-common" must not be mistaken for "sudo", nor the reverse.
        assertEquals(GuestConfig.PackageState.INSTALLED, GuestConfig.packageState(DPKG_STATUS, "sudo-common"));
        assertEquals(GuestConfig.PackageState.ABSENT, GuestConfig.packageState(DPKG_STATUS, "tree"));
        assertEquals(GuestConfig.PackageState.ABSENT, GuestConfig.packageState(DPKG_STATUS, "missing"));
        assertEquals(GuestConfig.PackageState.ABSENT, GuestConfig.packageState("", "sudo"));
    }

    @Test
    public void readsThePacmanStateOfAPackage() {
        String[] local = {"ALPM_DB_VERSION", "sudo-1.9.17.p2-2", "sudo-extra-1.0-1",
                "archlinuxarm-keyring-20240419-2", "lz4-1:1.10.0-2"};
        assertEquals(GuestConfig.PackageState.INSTALLED, GuestConfig.pacmanPackageState(local, "sudo"));
        assertEquals(GuestConfig.PackageState.INSTALLED, GuestConfig.pacmanPackageState(local, "archlinuxarm-keyring"));
        // An epoch is part of the version; a longer name is another package.
        assertEquals(GuestConfig.PackageState.INSTALLED, GuestConfig.pacmanPackageState(local, "lz4"));
        assertEquals(GuestConfig.PackageState.ABSENT, GuestConfig.pacmanPackageState(local, "sudo-extra-1.0"));
        assertEquals(GuestConfig.PackageState.ABSENT, GuestConfig.pacmanPackageState(local, "archlinuxarm"));
        assertEquals(GuestConfig.PackageState.ABSENT, GuestConfig.pacmanPackageState(new String[]{"sudo-extra-1.0-1"}, "sudo"));
        assertEquals(GuestConfig.PackageState.ABSENT, GuestConfig.pacmanPackageState(null, "sudo"));
    }

    @Test
    public void classifiesEveryDpkgCurrentState() {
        String[][] cases = {
                {"install ok installed", "INSTALLED"},
                {"install ok unpacked", "UNFINISHED"},
                {"install ok half-configured", "UNFINISHED"},
                {"install ok triggers-awaited", "UNFINISHED"},
                {"install ok triggers-pending", "UNFINISHED"},
                {"install reinstreq half-installed", "UNFINISHED"},
                {"deinstall ok config-files", "ABSENT"},
                {"purge ok not-installed", "ABSENT"},
        };
        for (String[] c : cases) {
            assertEquals(c[0], GuestConfig.PackageState.valueOf(c[1]),
                    GuestConfig.packageState("Package: sudo\nStatus: " + c[0] + "\n", "sudo"));
        }
    }

    @Test
    public void sudoersKeepsOnlyTheChrootHintAndGrantsThoth() {
        assertEquals("Defaults:thoth env_keep += \"SYSTEMD_IN_CHROOT\"\n"
                + "thoth ALL=(ALL:ALL) NOPASSWD: ALL\n", GuestConfig.sudoersEntry("thoth"));
    }

    /** A fresh rootfs gets the account the user chose, under that name from the start. */
    @Test
    public void accountIsCreatedUnderTheChosenName() {
        String passwd = GuestConfig.ensurePasswd(BASE_PASSWD, "alice");
        assertTrue(passwd.contains("alice:x:1000:1000:Thoth User:/home/alice:/bin/bash\n"));
        assertFalse(passwd.contains("thoth"));
        String group = GuestConfig.ensureGroup(BASE_GROUP, "sudo", "alice");
        assertTrue(group.contains("alice:x:1000:\n"));
        assertTrue(group.contains("sudo:x:27:alice\n"));
        assertTrue(GuestConfig.ensureShadow(BASE_SHADOW, "alice").contains("alice:!:19000:0:99999:7:::\n"));
        assertEquals("Defaults:alice env_keep += \"SYSTEMD_IN_CHROOT\"\n"
                + "alice ALL=(ALL:ALL) NOPASSWD: ALL\n", GuestConfig.sudoersEntry("alice"));
        assertEquals("/home/alice", GuestConfig.homeOf("alice"));
        assertTrue(GuestConfig.hasAccount(passwd, "alice"));
        assertFalse(GuestConfig.hasAccount(passwd, "thoth"));
    }

    @Test
    public void renamePasswdChangesTheNameAndItsHomeOnly() {
        String passwd = GuestConfig.ensurePasswd(BASE_PASSWD, "thoth");
        String renamed = GuestConfig.renamePasswd(passwd, "thoth", "alice");
        assertTrue(renamed.startsWith(BASE_PASSWD));
        assertTrue(renamed.contains("alice:x:1000:1000:Thoth User:/home/alice:/bin/bash\n"));
        assertFalse(renamed.contains("thoth"));
        // Idempotent, so an interrupted rename can be run again.
        assertEquals(renamed, GuestConfig.renamePasswd(renamed, "thoth", "alice"));
        assertEquals(renamed, GuestConfig.ensurePasswd(renamed, "alice"));
        // A home the user moved elsewhere, and another shell, stay as they are.
        assertEquals("alice:x:1000:1000::/data/thoth:/bin/zsh\n",
                GuestConfig.renamePasswd("thoth:x:1000:1000::/data/thoth:/bin/zsh\n", "thoth", "alice"));
        // An account whose name merely starts the same is another account.
        String other = "thothy:x:1001:1001::/home/thothy:/bin/sh\n";
        assertEquals(other, GuestConfig.renamePasswd(other, "thoth", "alice"));
    }

    @Test
    public void renameGroupChangesTheOwnGroupAndEveryMembership() {
        String group = "root:x:0:\nsudo:x:27:bob,thoth\nthoth:x:1000:\nwheel:x:998:thoth\n"
                + "users:x:100:alice\nthothy:x:1001:thothy\n";
        String renamed = GuestConfig.renameGroup(group, "thoth", "alice");
        assertEquals("root:x:0:\nsudo:x:27:bob,alice\nalice:x:1000:\nwheel:x:998:alice\n"
                + "users:x:100:alice\nthothy:x:1001:thothy\n", renamed);
        assertEquals(renamed, GuestConfig.renameGroup(renamed, "thoth", "alice"));
        assertEquals(renamed, GuestConfig.ensureGroup(renamed, "sudo", "alice"));
    }

    @Test
    public void renameShadowAndGshadowEntries() {
        String shadow = GuestConfig.ensureShadow(BASE_SHADOW, "thoth");
        String renamed = GuestConfig.renameShadow(shadow, "thoth", "alice");
        assertTrue(renamed.startsWith(BASE_SHADOW));
        assertTrue(renamed.contains("alice:!:19000:0:99999:7:::\n"));
        assertFalse(renamed.contains("thoth"));
        assertEquals(renamed, GuestConfig.renameShadow(renamed, "thoth", "alice"));
        // gshadow: name, administrators and members.
        String gshadow = "root:*::\nsudo:*:thoth:bob,thoth\nthoth:!::\n";
        assertEquals("root:*::\nsudo:*:alice:bob,alice\nalice:!::\n",
                GuestConfig.renameGshadow(gshadow, "thoth", "alice"));
    }

    @Test
    public void hostsKeepsTheGuestsOwnNameOnItsOwnLine() {
        String once = GuestConfig.ensureHosts("", "thothterm");
        assertTrue(once.contains("127.0.0.1 localhost\n"));
        assertTrue(once.contains("127.0.1.1 thothterm\n"));
        assertEquals(once, GuestConfig.ensureHosts(once, "thothterm"));
        // A new host name replaces the managed line and nothing else.
        String renamed = GuestConfig.ensureHosts(once, "pocket");
        assertTrue(renamed.contains("127.0.1.1 pocket\n"));
        assertFalse(renamed.contains("thothterm"));
        assertEquals(1, occurrences(renamed, "127.0.1.1 "));
        assertTrue(renamed.contains("127.0.0.1 localhost\n"));
        assertTrue(renamed.contains("::1 localhost ip6-localhost ip6-loopback\n"));
        // User lines on other addresses stay; the image's own 127.0.1.1 line is taken over.
        String base = "10.0.0.5 nas\n127.0.1.1\told-name alias\n";
        String result = GuestConfig.ensureHosts(base, "pocket");
        assertTrue(result.startsWith("10.0.0.5 nas\n127.0.1.1 pocket\n"));
        assertFalse(result.contains("old-name"));
    }

    @Test
    public void validatesTheNamesTheUserMayChoose() {
        assertTrue(GuestConfig.isValidUserName("thoth"));
        assertTrue(GuestConfig.isValidUserName("_svc-1"));
        assertTrue(GuestConfig.isValidUserName(repeat('a', 32)));
        assertFalse(GuestConfig.isValidUserName(repeat('a', 33)));
        assertFalse(GuestConfig.isValidUserName("root"));
        assertFalse(GuestConfig.isValidUserName("Alice"));
        assertFalse(GuestConfig.isValidUserName("1abc"));
        assertFalse(GuestConfig.isValidUserName("a b"));
        assertFalse(GuestConfig.isValidUserName("a:b"));
        assertFalse(GuestConfig.isValidUserName(""));
        assertFalse(GuestConfig.isValidUserName(null));

        assertTrue(GuestConfig.isValidHostName("thothterm"));
        assertTrue(GuestConfig.isValidHostName("My-Phone2"));
        assertTrue(GuestConfig.isValidHostName(repeat('h', 63)));
        assertFalse(GuestConfig.isValidHostName(repeat('h', 64)));
        assertFalse(GuestConfig.isValidHostName("-x"));
        assertFalse(GuestConfig.isValidHostName("a.b"));
        assertFalse(GuestConfig.isValidHostName("a b"));
        assertFalse(GuestConfig.isValidHostName("a/b"));
        assertFalse(GuestConfig.isValidHostName(""));
        assertFalse(GuestConfig.isValidHostName(null));
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) builder.append(c);
        return builder.toString();
    }
}
