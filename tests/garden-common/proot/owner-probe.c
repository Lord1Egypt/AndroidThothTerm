/* Ownership as a guest sees it, through every stat flavour libc and libalpm
 * use. usage: owner-probe PATH...   (absolute paths) */
#define _GNU_SOURCE
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>
static void show(const char *how, const char *p, int rc, const struct stat *st) {
	if (rc) printf("  %-34s failed\n", how);
	else printf("  %-34s type=%o mode=%o uid=%u gid=%u nlink=%lu\n", how, st->st_mode & S_IFMT,
		    st->st_mode & 07777, st->st_uid, st->st_gid, (unsigned long) st->st_nlink);
}
int main(int argc, char **argv) {
	for (int i = 1; i < argc; i++) {
		const char *p = argv[i];
		struct stat st;
		printf("%s\n", p);
		show("lstat(abs)", p, lstat(p, &st), &st);
		show("stat(abs)", p, stat(p, &st), &st);
		char dir[4096]; strncpy(dir, p, sizeof dir); char *slash = strrchr(dir, '/'); *slash = 0;
		int dfd = open(dir, O_RDONLY | O_DIRECTORY);
		show("fstatat(dirfd, base, NOFOLLOW)", p, fstatat(dfd, slash + 1, &st, AT_SYMLINK_NOFOLLOW), &st);
		show("fstatat(dirfd, base, 0)", p, fstatat(dfd, slash + 1, &st, 0), &st);
		int rfd = open("/", O_RDONLY | O_DIRECTORY);
		show("fstatat(rootfd, rel, NOFOLLOW)", p, fstatat(rfd, p + 1, &st, AT_SYMLINK_NOFOLLOW), &st);
		int fd = open(p, O_RDONLY);
		show("fstat(open fd)", p, fd < 0 ? 1 : fstat(fd, &st), &st);
		if (chdir(dir) == 0) {
			show("lstat(relative, after chdir)", p, lstat(slash + 1, &st), &st);
			show("fstatat(AT_FDCWD, rel, NOFOLLOW)", p, fstatat(AT_FDCWD, slash + 1, &st, AT_SYMLINK_NOFOLLOW), &st);
			show("stat(relative, after chdir)", p, stat(slash + 1, &st), &st);
			struct statx rx;
			if (syscall(SYS_statx, AT_FDCWD, slash + 1, AT_SYMLINK_NOFOLLOW, STATX_BASIC_STATS | STATX_BTIME, &rx) == 0)
				printf("  %-34s type=%o mode=%o uid=%u gid=%u nlink=%u\n", "statx(AT_FDCWD, rel, NOFOLLOW)",
				       rx.stx_mode & S_IFMT, rx.stx_mode & 07777, rx.stx_uid, rx.stx_gid, rx.stx_nlink);
		}
		int pfd = open(p, O_PATH | O_NOFOLLOW);
		show("fstat(O_PATH|O_NOFOLLOW fd)", p, pfd < 0 ? 1 : fstat(pfd, &st), &st);
		int nfd = open(p, O_RDONLY | O_NOFOLLOW);
		show("fstat(O_RDONLY|O_NOFOLLOW fd)", p, nfd < 0 ? 1 : fstat(nfd, &st), &st);
		int ofd = openat(rfd, p + 1, O_PATH | O_NOFOLLOW);
		show("fstat(openat(rootfd) O_PATH fd)", p, ofd < 0 ? 1 : fstat(ofd, &st), &st);
		struct statx sx;
		if (fd >= 0 && syscall(SYS_statx, fd, "", AT_EMPTY_PATH, STATX_BASIC_STATS, &sx) == 0)
			printf("  %-34s type=%o mode=%o uid=%u gid=%u nlink=%u\n", "statx(open fd, EMPTY_PATH)",
			       sx.stx_mode & S_IFMT, sx.stx_mode & 07777, sx.stx_uid, sx.stx_gid, sx.stx_nlink);
		show("fstatat(open fd, \"\", EMPTY_PATH)", p, fd < 0 ? 1 : fstatat(fd, "", &st, AT_EMPTY_PATH), &st);
		if (nfd >= 0 && syscall(SYS_statx, nfd, "", AT_EMPTY_PATH, STATX_BASIC_STATS, &sx) == 0)
			printf("  %-34s type=%o mode=%o uid=%u gid=%u nlink=%u\n", "statx(NOFOLLOW fd, EMPTY_PATH)",
			       sx.stx_mode & S_IFMT, sx.stx_mode & 07777, sx.stx_uid, sx.stx_gid, sx.stx_nlink);
		if (pfd >= 0 && syscall(SYS_statx, pfd, "", AT_EMPTY_PATH, STATX_BASIC_STATS, &sx) == 0)
			printf("  %-34s type=%o mode=%o uid=%u gid=%u nlink=%u\n", "statx(O_PATH fd, EMPTY_PATH)",
			       sx.stx_mode & S_IFMT, sx.stx_mode & 07777, sx.stx_uid, sx.stx_gid, sx.stx_nlink);
		if (syscall(SYS_statx, AT_FDCWD, p, AT_SYMLINK_NOFOLLOW, STATX_BASIC_STATS, &sx) == 0)
			printf("  %-34s type=%o mode=%o uid=%u gid=%u nlink=%u\n", "statx(abs, NOFOLLOW)",
			       sx.stx_mode & S_IFMT, sx.stx_mode & 07777, sx.stx_uid, sx.stx_gid, sx.stx_nlink);
		if (syscall(SYS_statx, rfd, p + 1, AT_SYMLINK_NOFOLLOW, STATX_BASIC_STATS, &sx) == 0)
			printf("  %-34s type=%o mode=%o uid=%u gid=%u nlink=%u\n", "statx(rootfd, rel, NOFOLLOW)",
			       sx.stx_mode & S_IFMT, sx.stx_mode & 07777, sx.stx_uid, sx.stx_gid, sx.stx_nlink);
	}
	return 0;
}
