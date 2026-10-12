/* lock-keepcaps COMMAND...: set SECBIT_KEEP_CAPS_LOCKED (what Android's zygote
 * does for apps; needs CAP_SETPCAP, e.g. in an unprivileged user namespace),
 * then exec COMMAND. PR_SET_KEEPCAPS then fails with EPERM in the kernel. */
#include <stdio.h>
#include <sys/prctl.h>
#include <unistd.h>
#ifndef PR_SET_SECUREBITS
#define PR_SET_SECUREBITS 28
#endif
int main(int argc, char **argv) {
	if (argc < 2) return 2;
	if (prctl(PR_SET_SECUREBITS, 1 << 5 /* SECBIT_KEEP_CAPS_LOCKED */, 0, 0, 0) != 0) { perror("securebits"); return 2; }
	execvp(argv[1], argv + 1);
	perror("exec");
	return 127;
}
