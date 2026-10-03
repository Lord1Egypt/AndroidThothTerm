import com.thothterm.linux.*;
import java.io.File;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
// Codex QA round 3 PoC shape: swap the checked path for a symlink right after
// the implementation's last lstat, then let chmodNoFollow continue.
public class ChmodRacePoc {
    public static void main(String[] a) throws Exception {
        Path dir = Files.createTempDirectory("race");
        Path checked = dir.resolve("checked"), aside = dir.resolve("aside"), outside = dir.resolve("outside");
        Files.write(checked, new byte[]{1}); Files.write(outside, new byte[]{2});
        Files.setPosixFilePermissions(checked, PosixFilePermissions.fromString("-w-------"));
        Files.setPosixFilePermissions(outside, PosixFilePermissions.fromString("rw-------"));
        final int[] calls = {0};
        JvmFileOps ops = new JvmFileOps() {
            @Override public Type type(File f) throws java.io.IOException {
                Type t = super.type(f);
                if (f.toPath().equals(checked) && ++calls[0] == 2) {
                    Files.move(checked, aside, StandardCopyOption.ATOMIC_MOVE);
                    Files.createSymbolicLink(checked, outside);
                }
                return t;
            }
        };
        System.out.println("before: outside=" + mode(outside));
        try { ops.chmodNoFollow(checked.toFile(), 0640); System.out.println("chmod returned normally"); }
        catch (Exception e) { System.out.println("chmod threw: " + e); }
        System.out.println("after: outside=" + mode(outside) + " calls=" + calls[0]);
        System.exit(mode(outside).equals("600") ? 0 : 1);
    }
    static String mode(Path p) throws Exception {
        return Integer.toOctalString((Integer) Files.getAttribute(p, "unix:mode", LinkOption.NOFOLLOW_LINKS) & 07777);
    }
}
