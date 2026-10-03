/* Fork/clone workloads for tests/garden-common/proot/fork-recovery-check.sh.
 * Each mode prints one "ok <mode> ..." line and exits 0 when every child it
 * made was created, ran and was reaped; anything else is a failure. */
#define _GNU_SOURCE
#include <pthread.h>
#include <sched.h>
#include <signal.h>
#include <spawn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

extern char **environ;

static int reap(pid_t pid, int expect)
{
	int status;
	if (waitpid(pid, &status, __WALL) != pid) return -1;
	return WIFEXITED(status) && WEXITSTATUS(status) == expect ? 0 : -1;
}

static int forks(int n)
{
	for (int i = 0; i < n; i++) {
		pid_t pid = fork();
		if (pid == 0) _exit(i % 100);
		if (pid < 0 || reap(pid, i % 100) != 0) return -1;
	}
	return 0;
}

static int vforks(int n)
{
	for (int i = 0; i < n; i++) {
		pid_t pid = vfork();
		if (pid == 0) _exit(7);
		if (pid < 0 || reap(pid, 7) != 0) return -1;
	}
	return 0;
}

static int spawns(int n)
{
	char *argv[] = {"true", NULL};
	for (int i = 0; i < n; i++) {
		pid_t pid;
		if (posix_spawnp(&pid, "true", NULL, NULL, argv, environ) != 0) return -1;
		if (reap(pid, 0) != 0) return -1;
	}
	return 0;
}

static int clone_parent(void)
{
	/* The grandchild becomes a sibling of the middle process: its parent
	 * is the middle process's parent, this one. */
	pid_t middle = fork();
	if (middle == 0) {
		long pid = syscall(SYS_clone, CLONE_PARENT | SIGCHLD, NULL, NULL, NULL, NULL);
		if (pid == 0) _exit(9);
		_exit(pid > 0 ? 0 : 1);
	}
	if (middle < 0 || reap(middle, 0) != 0) return -1;
	/* The CLONE_PARENT child is this process's to reap. */
	int status;
	pid_t got = waitpid(-1, &status, __WALL);
	return got > 0 && WIFEXITED(status) && WEXITSTATUS(status) == 9 ? 0 : -1;
}

static void *thread_forks(void *arg)
{
	return (void *) (long) forks((int) (long) arg);
}

static void *nothing(void *arg)
{
	return arg;
}

static void *thread_threads(void *arg)
{
	for (int i = 0; i < (int) (long) arg; i++) {
		pthread_t t;
		if (pthread_create(&t, NULL, nothing, NULL) != 0) return (void *) -1L;
		pthread_join(t, NULL);
	}
	return NULL;
}

static int in_threads(void *(*fn)(void *), int threads, int each)
{
	pthread_t t[16];
	int failed = 0;
	for (int i = 0; i < threads; i++)
		if (pthread_create(&t[i], NULL, fn, (void *) (long) each) != 0) return -1;
	for (int i = 0; i < threads; i++) {
		void *r;
		pthread_join(t[i], &r);
		if (r != NULL) failed = 1;
	}
	return failed ? -1 : 0;
}

int main(int argc, char **argv)
{
	const char *mode = argc > 1 ? argv[1] : "";
	int rc;
	if (!strcmp(mode, "fork")) rc = forks(200);
	else if (!strcmp(mode, "vfork")) rc = vforks(200);
	else if (!strcmp(mode, "spawn")) rc = spawns(100);
	else if (!strcmp(mode, "clone-parent")) rc = clone_parent();
	else if (!strcmp(mode, "thread")) rc = in_threads(thread_threads, 1, 50);
	else if (!strcmp(mode, "threads-concurrent")) rc = in_threads(thread_threads, 8, 25);
	else if (!strcmp(mode, "forks-concurrent")) rc = in_threads(thread_forks, 8, 25);
	else {
		fprintf(stderr, "usage: fork-probe fork|vfork|spawn|clone-parent|thread|"
			"threads-concurrent|forks-concurrent\n");
		return 2;
	}
	if (rc != 0) {
		printf("FAILED %s\n", mode);
		return 1;
	}
	printf("ok %s\n", mode);
	return 0;
}
