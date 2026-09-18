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

#include <limits.h>
#include <fcntl.h>
#include <stdio.h>
#include <errno.h>
#include <string.h>
#include <unistd.h>

#include "appinfo.h"

extern char *__progname;

extern int appcmd_open(const char *path, int flags, mode_t mode);

extern FILE *appcmd_fopen(const char *path, const char *mode);


/* application response starts with requested file name
 * as indicator for successful "open" operation
 */
static int/*boolean*/
response_file(const char *path, int sock) {
    char pathname[PATH_MAX + 1]; /* plus eof */
    size_t len, res;

    len = strlen(path);
    if (len >= sizeof(pathname)) goto err;

    memset(&pathname, 0, len);
    res = atomicio(read, sock, pathname, len);
    if (res != len) goto err;
    pathname[res] = '\0';

    if (strcmp(path, pathname) != 0) goto err;

    return 1;

    err:
    errno = EEXIST;
    return 0;
}


/* open application configuration
 * - request
 *   open sysconfig
 *   [command]
 *   [sysconfig path]
 *   <eol>
 * - response
 *   path without end of line
 *   [file content written to socket]
 */
static int
open_sysconfig(const char *cmd, const char *path) {
    int sock;
    int ret = -1;

    sock = open_appsocket();
    if (sock == -1) return -1;

    {   /* request */
        char msg[4096];
        int msgres;

        msgres = snprintf(msg, sizeof(msg), "open sysconfig\n%s\n%s\n<eol>\n", cmd, path);
        if (msgres < 0) {
            fprintf(stderr, "binary: snprintf() fail: %d(%s)\n", errno, strerror(errno));
            goto done;
        }
        if (msgres >= sizeof(msg)) {
            fprintf(stderr, "binary: too long command\n");
            goto done;
        }
        if (!write_msg(sock, msg))
            goto done;
    }

    if (!response_file(path, sock)) return -1;

    ret = sock;

    done:
    return ret;
}


int
appcmd_open(const char *path, int flags, mode_t mode) {
    char *s;

    (void) mode;
    if (flags & O_WRONLY) goto err;

    s = strstr(path, "/etc/");
    if (s == NULL) goto err;

    return open_sysconfig(__progname, s);

    err:
    errno = ENOSYS;
    return -1;
}

FILE *
appcmd_fopen(const char *path, const char *mode) {
    char *s;

    if (strcmp(mode, "r") != 0) goto err;

    s = strstr(path, "/etc/");
    if (s == NULL) goto err;

    return fdopen(open_sysconfig(__progname, s), mode);

    err:
    errno = ENOSYS;
    return NULL;
}
