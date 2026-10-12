/* unix-probe serve PATH | connect PATH: a Unix stream socket at PATH.
 *   serve:   bind, print "listening <getsockname path>", answer one client
 *            with "pong", then wait for "quit" on stdin and unlink PATH
 *   connect: connect, read the answer, print "<answer> peer=<getpeername>" */
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
int main(int argc, char **argv) {
	struct sockaddr_un a = { .sun_family = AF_UNIX }, b;
	socklen_t len = sizeof b;
	if (argc < 3 || strlen(argv[2]) >= sizeof a.sun_path) return 2;
	strcpy(a.sun_path, argv[2]);
	int s = socket(AF_UNIX, SOCK_STREAM, 0);
	if (strcmp(argv[1], "serve") == 0) {
		if (bind(s, (void *)&a, sizeof a) || listen(s, 1)) { perror("bind/listen"); return 1; }
		memset(&b, 0, sizeof b);
		getsockname(s, (void *)&b, &len);
		printf("listening %s\n", b.sun_path);
		fflush(stdout);
		int c = accept(s, NULL, NULL);
		write(c, "pong", 4);
		close(c);
		char line[16];
		fgets(line, sizeof line, stdin);
		unlink(argv[2]);
		return 0;
	}
	if (connect(s, (void *)&a, sizeof a)) { perror("connect"); return 1; }
	char buf[8] = { 0 };
	read(s, buf, 4);
	memset(&b, 0, sizeof b);
	getpeername(s, (void *)&b, &len);
	printf("%s peer=%s\n", buf, b.sun_path);
	return 0;
}
