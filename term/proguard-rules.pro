# ThothTerm Terminal -- R8 keep rules.
#
# Everything R8 can work out for itself is deliberately absent from this file.
# Manifest components, classes named from layout XML (ExtraKeysView,
# TermViewFlipper, WindowListFragment) and AndroidX's own requirements all
# arrive as generated rules, through aapt_rules.txt and the libraries' consumer
# rules; repeating them here would only hide a future mistake. The AIDL
# interfaces need nothing either: Binder dispatch is keyed on the DESCRIPTOR
# string constant, which R8 does not rewrite, so ITerminal and ICommand stay
# wire-compatible with com.thothterm.securebox and the addon sample under any
# renaming. The reflection in PRNGFixes targets platform classes only.
#
# What follows is the one contract R8 cannot see.

# JNI. libterm-system.so does not rely on the Java_* naming convention: it calls
# RegisterNatives from JNI_OnLoad against class names and method names written
# out as literals in libtermexec/src/main/cpp/termio.c and process.c --
# "com/thothterm/TermIO$Native" and "com/thothterm/Process$Native".
#
# The platform configuration already keeps the names of classes with native
# methods, but only while R8 considers those methods used: a method listed in a
# C JNINativeMethod table that Java stops calling would be removed, and
# RegisterNatives then fails the whole table, which surfaces as an
# UnsatisfiedLinkError at the first PTY. These rules pin that table.
#
# Only the class name and the native method names are pinned. Nothing else in
# either class is kept, the outer TermIO and Process classes stay fully
# optimizable, and the <methods> form is used rather than spelled-out
# signatures so that a future signature change cannot silently stop matching.
-keep class com.thothterm.TermIO$Native {
    native <methods>;
}
-keep class com.thothterm.Process$Native {
    native <methods>;
}
