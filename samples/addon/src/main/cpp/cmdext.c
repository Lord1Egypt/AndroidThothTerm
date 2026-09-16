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

#include <string.h>
#include <stdio.h>

extern char **environ;

void dump_environment(void);


static const
char *android_system_env[] = {
        "ANDROID_BOOTLOGO",
        "ANDROID_ROOT",
        "ANDROID_ASSETS",
        "ANDROID_DATA",
        "ANDROID_STORAGE",
        "ANDROID_ART_ROOT",
        "ANDROID_I18N_ROOT",
        "ANDROID_TZDATA_ROOT",
        "EXTERNAL_STORAGE",
        "ASEC_MOUNTPOINT",
        "BOOTCLASSPATH",
        "DEX2OATBOOTCLASSPATH",
        "SYSTEMSERVERCLASSPATH",
        /*Zygote defaults*/
        "STANDALONE_SYSTEMSERVER_JARS",
        "ANDROID_SOCKET_zygote",
        "ANDROID_SOCKET_zygote_secondary",
        "ANDROID_SOCKET_usap_pool_primary",
        "ANDROID_SOCKET_usap_pool_secondary",
        /*obsolete?*/
        "ANDROID_RUNTIME_ROOT",
        "LOOP_MOUNTPOINT",
        "ANDROID_PROPERTY_WORKSPACE",
        "SECONDARY_STORAGE",
        "SD_EXT_DIRECTORY"
};

static int/*bool*/
is_system_env(char *env) {
    int k;
    for (k = 0; k < sizeof(android_system_env) / sizeof(android_system_env[0]); k++) {
        const char *s = android_system_env[k];
        size_t l = strlen(s);
        if ((strncmp(env, s, l) == 0) && (env[l] == '='))
            return 1;
    }
    return 0;
}


void
dump_environment(void) {
    char **env;
    for (env = environ; *env != NULL; env++) {
        /*exclude "system" environment for demo command*/
        if (is_system_env(*env)) continue;
        printf("env: '%s'\n", *env);
    }
}
