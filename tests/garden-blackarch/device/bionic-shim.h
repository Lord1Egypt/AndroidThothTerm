/* Lets tests/garden-common/proot/fchmodat2-probe.c (written for glibc) build for Android's
 * bionic without changing that file: lchmod and strerrorname_np. The raw fchmodat2 (452)
 * calls, the substance of the 0006 check, are libc independent. */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
static inline int lchmod(const char *p, mode_t m) { return fchmodat(AT_FDCWD, p, m, AT_SYMLINK_NOFOLLOW); }
static inline const char *strerrorname_np(int e) {
    switch (e) {
    case ENOENT: return "ENOENT"; case EBADF: return "EBADF"; case EINVAL: return "EINVAL";
    case EPERM: return "EPERM"; case EACCES: return "EACCES"; case EOPNOTSUPP: return "EOPNOTSUPP";
    case ENOSYS: return "ENOSYS"; case ELOOP: return "ELOOP"; default: return "E?";
    }
}
