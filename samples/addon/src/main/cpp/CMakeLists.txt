##
# Copyright (C) 2023-2026 Roumen Petrov.  All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
##

#cmake_minimum_required(VERSION 3.10)
# Note target_link_options(...) is introduced in CMake 3.13.
cmake_minimum_required(VERSION 3.13)

project("addon-c")

# Android NDK r26 and lower
if (ANDROID_ABI STREQUAL "arm64-v8a")
    set(MAX_PAGE_SIZE "-Wl,-z,max-page-size=16384")
elseif (ANDROID_ABI STREQUAL "x86_64")
    set(MAX_PAGE_SIZE "-Wl,-z,max-page-size=16384")
else ()
    set(MAX_PAGE_SIZE "")
endif ()

add_executable(
        libcmd-addon.so
        cmd.c
        cmdwrap.c
)
target_compile_options(
        libcmd-addon.so
        PRIVATE
        -fPIC
        # build without fortify!
        -U_FORTIFY_SOURCE
)
target_link_libraries(
        libcmd-addon.so
        addon
        appwrap
        -pie
        -Wl,--wrap=open
)
target_link_options(
        libcmd-addon.so
        PRIVATE
        "${MAX_PAGE_SIZE}"
)

add_library(
        addon
        SHARED
        cmdext.c
)

add_library(
        appwrap
        SHARED
        t1p-stub.c
)
