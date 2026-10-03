package com.thothterm.linux;

import java.io.FileInputStream;

/**
 * Prints the keyring script RootfsManager runs for an edition's distro.properties,
 * so validate-rootfs.sh can run the very script the app runs, in a container.
 * Compiled into the garden-common package to reach its package-private method.
 */
public final class DumpKeyringScript {
    public static void main(String[] args) throws Exception {
        try (FileInputStream in = new FileInputStream(args[0])) {
            System.out.print(RootfsManager.pacmanKeyringScript(DistroInfo.load(in)));
        }
    }
}
