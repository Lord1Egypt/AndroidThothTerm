/* setpriv-probe keep|drop|quit, run as fake root under PRoot.
 *   keep: util-linux setpriv's order for the Redis 8 entrypoint
 *         (PR_SET_KEEPCAPS, setresgid/setresuid, capset, setgroups,
 *         bounding-set drop, ambient raise): every call succeeds
 *   drop: no KEEPCAPS: after the UID drop, setgroups and the bounding-set
 *         drop fail with EPERM, as on Linux
 *   quit: wait for SIGQUIT in a handler, exit 3 when it arrives
 * Exits 0 when the behaviour matches (quit: 3). */
#define _GNU_SOURCE
#include <errno.h>
#include <grp.h>
#include <linux/capability.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>

static void on_quit(int s) { (void)s; _exit(3); }

int main(int argc, char **argv) {
	const char *mode = argc > 1 ? argv[1] : "keep";
	if (strcmp(mode, "quit") == 0) {
		signal(SIGQUIT, on_quit);
		printf("waiting pgid=%d\n", (int)getpgrp());
		fflush(stdout);
		for (;;)
			pause();
	}
	int keep = strcmp(mode, "keep") == 0;
	if (keep && prctl(PR_SET_KEEPCAPS, 1, 0, 0, 0) != 0) { perror("keepcaps"); return 2; }
	if (setresgid(999, 999, 999) != 0 || setresuid(999, 999, 999) != 0) { perror("setres*id"); return 2; }
	int r_cap = 0;
	if (keep) {
		struct __user_cap_header_struct h = { _LINUX_CAPABILITY_VERSION_3, 0 };
		struct __user_cap_data_struct d[2] = { 0 };
		d[0].effective = d[0].permitted = (1u << CAP_SETGID) | (1u << CAP_SETPCAP);
		d[0].inheritable = 1u << CAP_SYS_RESOURCE;
		r_cap = (int)syscall(SYS_capset, &h, d);
	}
	int r_grp = setgroups(0, NULL);
	int e_grp = errno;
	int r_bnd = prctl(PR_CAPBSET_DROP, CAP_SYS_ADMIN, 0, 0, 0);
	int e_bnd = errno;
	int r_amb = keep ? prctl(PR_CAP_AMBIENT, PR_CAP_AMBIENT_RAISE, CAP_SYS_RESOURCE, 0, 0) : 0;
	printf("%s: capset %d, setgroups %d (%s), bounding drop %d (%s), ambient raise %d\n", mode, r_cap,
	       r_grp, r_grp ? strerror(e_grp) : "ok", r_bnd, r_bnd ? strerror(e_bnd) : "ok", r_amb);
	if (keep)
		return r_cap || r_grp || r_bnd || r_amb;
	return !(r_grp == -1 && e_grp == EPERM && r_bnd == -1 && e_bnd == EPERM);
}
