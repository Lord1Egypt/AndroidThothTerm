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

#include <stdio.h>
#include <sysexits.h>
#include <string.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>

extern void dump_environment(void);

int
main(int argc, char *argv[]/*, char *envp[]*/) {
    int k;

    printf("Hello world!\n");
    for (k = 0; k < argc; k++) {
        printf("arg[%d]: '%s'\n", k, argv[k]);
    }

    dump_environment();

    {
        char *conf = getenv("ADDON_CONF");
        if (conf != NULL) {
            int fd = open(conf, O_CLOEXEC);
            if (fd == -1)
                fprintf(stderr, "open fail: %s\n", strerror(errno));
            else {
                printf("conf '%s':\n", conf);
                while (1) {
                    char buf[4096];
                    ssize_t len = read(fd, buf, sizeof(buf));
                    if (len <= 0) break;
                    write(STDOUT_FILENO, buf, len);
                }
                fsync(STDOUT_FILENO);
                close(fd);
            }
        }
    }

    return EX_OK;
}
