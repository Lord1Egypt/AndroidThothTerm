/* chroot-probe DIR NAME: as fake root, chroot(DIR) and check it takes effect:
 * "/NAME" (a file in DIR) is visible, a file of the outer root is not, and
 * a second chroot into a subdirectory works too. Exit 0 when all hold. */
#include <errno.h>
#include <stdio.h>
#include <sys/stat.h>
#include <unistd.h>
int main(int argc, char **argv) {
	char path[256];
	struct stat st;
	if (argc < 3) return 2;
	if (chroot(argv[1]) != 0) { perror("chroot"); return 1; }
	if (chdir("/") != 0) { perror("chdir"); return 1; }
	snprintf(path, sizeof path, "/%s", argv[2]);
	if (stat(path, &st) != 0) { printf("marker %s not visible: %s\n", path, strerror(errno)); return 1; }
	if (stat("/etc/passwd", &st) == 0) { puts("outer /etc/passwd still visible"); return 1; }
	if (chroot("/sub") != 0) { perror("nested chroot"); return 1; }
	if (chdir("/") != 0 || stat("/inner", &st) != 0) { puts("nested root not effective"); return 1; }
	puts("chroot ok");
	return 0;
}
