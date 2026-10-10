/*
 * Probe for PRoot's --net-ip extension (garden-common patch 0009). Built
 * static, as Go and Rust workloads are, and run inside PRoot. Each mode
 * prints "OK <what>" lines or "FAIL <what>: ..." and exits non-zero on a
 * failure.
 *
 *   net-ip-probe self OWN_IP              rules seen from one container
 *   net-ip-probe serve PORT               bind 0.0.0.0:PORT, answer "pong" once per accept, forever
 *   net-ip-probe ask ADDR PORT            connect, print what the server says
 */
#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

static int fails;

#define CHECK(cond, what, ...) do {						\
		if (cond) printf("OK %s\n", what);				\
		else { printf("FAIL %s: " __VA_ARGS__); printf("\n"); fails++; } \
	} while (0)

static void name_of(int fd, char *out, size_t size, int *port)
{
	struct sockaddr_storage ss;
	socklen_t len = sizeof(ss);

	getsockname(fd, (struct sockaddr *) &ss, &len);
	if (ss.ss_family == AF_INET) {
		struct sockaddr_in *in = (struct sockaddr_in *) &ss;
		inet_ntop(AF_INET, &in->sin_addr, out, size);
		*port = ntohs(in->sin_port);
	} else {
		struct sockaddr_in6 *in6 = (struct sockaddr_in6 *) &ss;
		inet_ntop(AF_INET6, &in6->sin6_addr, out, size);
		*port = ntohs(in6->sin6_port);
	}
}

static int listen4(const char *addr, int port, struct sockaddr_in *keep)
{
	int fd = socket(AF_INET, SOCK_STREAM, 0);
	int one = 1;
	struct sockaddr_in in = { .sin_family = AF_INET, .sin_port = htons(port) };

	setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));
	inet_pton(AF_INET, addr, &in.sin_addr);
	if (keep)
		*keep = in;
	if (bind(fd, (struct sockaddr *) &in, sizeof(in)) < 0 || listen(fd, 8) < 0) {
		printf("bind %s:%d: %s\n", addr, port, strerror(errno));
		close(fd);
		return -1;
	}
	if (keep && memcmp(keep, &in, sizeof(in)) != 0)
		printf("FAIL the application's sockaddr was modified\n"), fails++;
	return fd;
}

static int listen6(const char *addr, int port, int v6only)
{
	int fd = socket(AF_INET6, SOCK_STREAM, 0);
	int one = 1;
	struct sockaddr_in6 in6 = { .sin6_family = AF_INET6, .sin6_port = htons(port) };

	setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));
	setsockopt(fd, IPPROTO_IPV6, IPV6_V6ONLY, &v6only, sizeof(v6only));
	inet_pton(AF_INET6, addr, &in6.sin6_addr);
	if (bind(fd, (struct sockaddr *) &in6, sizeof(in6)) < 0 || listen(fd, 8) < 0) {
		printf("bind6 %s:%d: %s\n", addr, port, strerror(errno));
		close(fd);
		return -1;
	}
	return fd;
}

/* Connect to addr:port (family from the address) and return the socket. */
static int dial(const char *addr, int port)
{
	struct sockaddr_storage ss;
	socklen_t len;
	int fd;

	memset(&ss, 0, sizeof(ss));
	if (strchr(addr, ':')) {
		struct sockaddr_in6 *in6 = (struct sockaddr_in6 *) &ss;
		in6->sin6_family = AF_INET6;
		in6->sin6_port = htons(port);
		inet_pton(AF_INET6, addr, &in6->sin6_addr);
		len = sizeof(*in6);
	} else {
		struct sockaddr_in *in = (struct sockaddr_in *) &ss;
		in->sin_family = AF_INET;
		in->sin_port = htons(port);
		inet_pton(AF_INET, addr, &in->sin_addr);
		len = sizeof(*in);
	}
	fd = socket(ss.ss_family, SOCK_STREAM, 0);
	if (connect(fd, (struct sockaddr *) &ss, len) < 0) {
		close(fd);
		return -1;
	}
	return fd;
}

/* Accept one connection on lfd within the same process after dialling. */
static int roundtrip(int lfd, const char *addr, int port)
{
	int c = dial(addr, port);
	int a;

	if (c < 0)
		return 0;
	a = accept(lfd, NULL, NULL);
	write(c, "x", 1);
	char b = 0;
	read(a, &b, 1);
	close(a);
	close(c);
	return b == 'x';
}

static int self(const char *own)
{
	char got[64];
	int port;
	struct sockaddr_in keep;

	int any = listen4("0.0.0.0", 23000, &keep);
	name_of(any, got, sizeof(got), &port);
	CHECK(strcmp(got, own) == 0 && port == 23000, "bind 0.0.0.0 binds the container address", "%s:%d", got, port);

	int low = listen4("127.0.0.1", 80, NULL);
	CHECK(low >= 0, "bind 127.0.0.1:80 succeeds unprivileged", "bind failed");
	if (low >= 0) {
		name_of(low, got, sizeof(got), &port);
		CHECK(strcmp(got, own) == 0 && port == 30080, "low port shifted to +30000", "%s:%d", got, port);
	}

	int dual = listen6("::", 23001, 0);
	name_of(dual, got, sizeof(got), &port);
	char mapped[64];
	snprintf(mapped, sizeof(mapped), "::ffff:%s", own);
	CHECK(strcmp(got, mapped) == 0 && port == 23001, "dual-stack [::] binds the mapped container address", "%s:%d", got, port);

	int v6only = listen6("::", 23002, 1);
	CHECK(v6only >= 0, "V6ONLY [::] bind still succeeds", "failed");
	if (v6only >= 0) {
		name_of(v6only, got, sizeof(got), &port);
		CHECK(strcmp(got, "::1") == 0 && port != 23002, "V6ONLY [::] becomes an inert [::1]:0", "%s:%d", got, port);
	}
	/* nginx: "listen 80; listen [::]:80;" in one container. */
	int nginx4 = listen4("0.0.0.0", 81, NULL);
	int nginx6 = listen6("::", 81, 1);
	CHECK(nginx4 >= 0 && nginx6 >= 0, "nginx-style listen 81 + [::]:81 ipv6only both succeed", "v4 %d v6 %d", nginx4, nginx6);

	CHECK(roundtrip(any, "127.0.0.1", 23000), "localhost reaches the container's own listener", "no");
	CHECK(roundtrip(any, "0.0.0.0", 23000), "connect to 0.0.0.0 reaches the container", "no");
	CHECK(roundtrip(any, own, 23000), "connect to its own address", "no");
	CHECK(roundtrip(dual, "::1", 23001), "[::1] reaches the dual-stack listener", "no");
	if (low >= 0)
		CHECK(roundtrip(low, "127.0.0.1", 80), "connect to 127.0.0.1:80 reaches the shifted listener", "no");

	/* UDP: bind wildcard, sendto loopback. */
	int u = socket(AF_INET, SOCK_DGRAM, 0);
	struct sockaddr_in ua = { .sin_family = AF_INET, .sin_port = htons(23053) };
	bind(u, (struct sockaddr *) &ua, sizeof(ua));
	name_of(u, got, sizeof(got), &port);
	CHECK(strcmp(got, own) == 0, "UDP bind 0.0.0.0 binds the container address", "%s", got);
	int s = socket(AF_INET, SOCK_DGRAM, 0);
	struct sockaddr_in to = { .sin_family = AF_INET, .sin_port = htons(23053) };
	inet_pton(AF_INET, "127.0.0.1", &to.sin_addr);
	sendto(s, "u", 1, 0, (struct sockaddr *) &to, sizeof(to));
	struct timeval tv = { .tv_sec = 2 };
	setsockopt(u, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
	char ub = 0;
	recv(u, &ub, 1, 0);
	CHECK(ub == 'u', "UDP sendto 127.0.0.1 reaches the container's own socket", "nothing received");
	return fails ? 1 : 0;
}

static int serve(int port)
{
	int lfd = listen4("0.0.0.0", port, NULL);
	if (lfd < 0)
		return 1;
	printf("serving\n");
	fflush(stdout);
	for (;;) {
		int a = accept(lfd, NULL, NULL);
		if (a < 0)
			continue;
		write(a, "pong\n", 5);
		close(a);
	}
}

static int ask(const char *addr, int port)
{
	char b[64] = { 0 };
	int fd = dial(addr, port);

	if (fd < 0) {
		printf("connect %s:%d: %s\n", addr, port, strerror(errno));
		return 1;
	}
	read(fd, b, sizeof(b) - 1);
	printf("%s", b);
	return 0;
}

int main(int argc, char **argv)
{
	setvbuf(stdout, NULL, _IONBF, 0);
	if (argc == 3 && strcmp(argv[1], "self") == 0)
		return self(argv[2]);
	if (argc == 3 && strcmp(argv[1], "serve") == 0)
		return serve(atoi(argv[2]));
	if (argc == 4 && strcmp(argv[1], "ask") == 0)
		return ask(argv[2], atoi(argv[3]));
	fprintf(stderr, "usage: net-ip-probe self OWN_IP | serve PORT | ask ADDR PORT\n");
	return 2;
}
