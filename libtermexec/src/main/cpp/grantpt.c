/*
 * Copyright (C) 2024 Roumen Petrov.  All rights reserved.
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

/* Note use separate compilation unit to avoid impact from
 * inline definition from legacy_stdlib_inlines.h on NDK < 26.
 */
#include <stddef.h>

int android_grantpt(int fd);

int
android_grantpt(int fd) {
    extern int grantpt(int fd) __attribute__((weak));

    if (grantpt != NULL)
        return grantpt(fd);
    /* API Level < 21 */
    return 0;
}
