/*
 * Copyright (C) 2023-2024 Roumen Petrov.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <fcntl.h>
#include <stdio.h>
#include <errno.h>
#include <unistd.h>

#ifdef __BIONIC_FORTIFY_INLINE
/* wrap below does not work with _FORTIFY_SOURCE>0 as inline definition
 * "redirects" functions */
# error "macro __BIONIC_FORTIFY_INLINE is defined"
#endif
extern char *__progname;

/* Terminal application will try to open configuration provided
 * by an command "addon" application. Error ENOSYS is returned if
 * open request write access to file and path does not contain
 * string "/etc/" i.e. not sysconfig file - see t1p-wrap.c.
 */
extern int appcmd_open(const char *path, int flags, mode_t mode);


/* wrap open(2) */
extern int __real_open(const char *path, int flags, mode_t mode);

int
__wrap_open(const char *path, int flags, mode_t mode) {
    int ret;
    int oerrno;

    oerrno = errno;
    ret = appcmd_open(path, flags, mode);
    if ((ret == -1) && (errno == ENOSYS)) {
        errno = oerrno;
        ret = __real_open(path, flags, mode);
    }
    return ret;
}
