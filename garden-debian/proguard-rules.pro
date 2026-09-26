# ThothTerm Debian -- R8 keep rules.
#
# Everything R8 can work out for itself is deliberately absent. Manifest
# components (including those merged from garden-common), classes named from
# layout XML and AndroidX's own requirements arrive as generated rules.
#
# JNI. libterm-system.so (libtermexec) does not use the Java_* naming
# convention: JNI_OnLoad calls RegisterNatives against class and method names
# written out as literals in libtermexec/src/main/cpp/termio.c and process.c.
# The platform configuration keeps the names of classes with native methods, but
# lets R8 remove a native method Java no longer calls, and RegisterNatives then
# fails the whole table -- an UnsatisfiedLinkError at the first PTY.
-keep class com.thothterm.TermIO$Native {
    native <methods>;
}
-keep class com.thothterm.Process$Native {
    native <methods>;
}
