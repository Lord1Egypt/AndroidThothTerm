/*
 * fchmodat2 / lchmod probe: run natively and under PRoot on the same kind of
 * tree (BASE/file, BASE/link -> file); the outputs must be identical.
 * tests/garden-common/proot/host-check.sh drives it.
 */
#define _GNU_SOURCE
#include <stdio.h>
#include <errno.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/syscall.h>
/* Prints only the base name so native and PRoot runs can be diffed. */
static void mode(const char *p) {
    struct stat st;
    const char *b = strrchr(p, '/') ? strrchr(p, '/') + 1 : p;
    if (lstat(p, &st) == 0) printf("   lstat %s mode=%o\n", b, st.st_mode & 07777);
    else printf("   lstat %s: %s\n", b, strerror(errno));
}
int main(int argc, char **argv) {
    int r;
    char F[512], L[512];
    const char *base = argc > 1 ? argv[1] : "/work";
    snprintf(F, sizeof F, "%s/file", base); snprintf(L, sizeof L, "%s/link", base);
    errno = 0; r = lchmod(L, 0777);
    printf("lchmod(/work/link)                      = %d errno=%s\n", r, r ? strerrorname_np(errno) : "-");
    errno = 0; r = fchmodat(AT_FDCWD, F, 0640, AT_SYMLINK_NOFOLLOW);
    printf("fchmodat(/work/file, 0640, NOFOLLOW)     = %d errno=%s\n", r, r ? strerrorname_np(errno) : "-"); mode(F);
    errno = 0; r = fchmodat(AT_FDCWD, L, 0600, 0);
    printf("fchmodat(/work/link, 0600, 0) follows   = %d errno=%s\n", r, r ? strerrorname_np(errno) : "-"); mode(F);
    errno = 0; r = syscall(452, AT_FDCWD, L, 0777, AT_SYMLINK_NOFOLLOW);
    printf("raw fchmodat2(/work/link, NOFOLLOW)      = %d errno=%s\n", r, r ? strerrorname_np(errno) : "-");
    errno = 0; r = syscall(452, AT_FDCWD, F, 0644, 0);
    printf("raw fchmodat2(/work/file, 0644, 0)       = %d errno=%s\n", r, r ? strerrorname_np(errno) : "-"); mode(F);
    errno = 0; r = syscall(452, AT_FDCWD, F, 0644, 0x8000);
    printf("raw fchmodat2(/work/file, bad flag)      = %d errno=%s\n", r, r ? strerrorname_np(errno) : "-");
    int fd = open(F, O_RDONLY);
    errno = 0; r = syscall(452, fd, "", 0600, AT_EMPTY_PATH);
    printf("raw fchmodat2(fd, \"\", 0600, EMPTY_PATH) = %d errno=%s\n", r, r ? strerrorname_np(errno) : "-"); mode(F);
    if (chdir(base)) return 2;
    errno = 0; r = syscall(452, AT_FDCWD, "link", 0777, AT_SYMLINK_NOFOLLOW);
    printf("raw fchmodat2(relative link, NOFOLLOW)   = %d errno=%s\n", r, r ? strerrorname_np(errno) : "-");
    return 0;
}
