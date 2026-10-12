/* setgroups-probe keep|drop: the syscall order of util-linux setpriv
 * --reuid=999 --regid=999 --clear-groups, run as fake root under PRoot.
 *   keep: PR_SET_KEEPCAPS before the UID change -> setgroups succeeds
 *   drop: no KEEPCAPS -> the capability is gone, setgroups fails (EPERM)
 * Prints the result and exits 0 when it matches. */
#define _GNU_SOURCE
#include <errno.h>
#include <grp.h>
#include <linux/capability.h>
#include <stdio.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>
int main(int argc, char **argv) {
	int keep = argc > 1 && strcmp(argv[1], "keep") == 0;
	if (keep && prctl(PR_SET_KEEPCAPS, 1, 0, 0, 0) != 0) { perror("keepcaps"); return 2; }
	if (setresgid(999, 999, 999) != 0 || setresuid(999, 999, 999) != 0) { perror("setres*id"); return 2; }
	if (keep) {
		struct __user_cap_header_struct h = { _LINUX_CAPABILITY_VERSION_3, 0 };
		struct __user_cap_data_struct d[2] = { 0 };
		d[0].effective = d[0].permitted = 1u << CAP_SETGID;
		syscall(SYS_capset, &h, d);
	}
	int r = setgroups(0, NULL);
	printf("%s: setgroups -> %s\n", argv[1], r == 0 ? "0" : strerror(errno));
	return keep ? r != 0 : !(r == -1 && errno == EPERM);
}
