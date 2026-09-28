import org.objectweb.asm.*;
import java.io.*;
import java.util.zip.*;
import java.util.*;

// Cloud host tests only (tools/garden/cloud-hosttest/run.sh): no Android SDK is
// reachable there, so the unit-test android.jar is made from Robolectric's
// android-all the way AGP makes its mockable jar from the SDK's.

/** Rough equivalent of AGP's MockableJarGenerator: every method of the Android
 *  framework classes throws "Method ... not mocked." (or returns a default value),
 *  no static initialiser runs, compile-time constants are kept. */
public class MockGen {
  public static void main(String[] a) throws Exception {
    boolean defaults = a[2].equals("defaults");
    try (ZipInputStream in = new ZipInputStream(new FileInputStream(a[0]));
         ZipOutputStream out = new ZipOutputStream(new FileOutputStream(a[1]))) {
      ZipEntry e; Set<String> seen = new HashSet<>();
      while ((e = in.getNextEntry()) != null) {
        String n = e.getName();
        if (!n.endsWith(".class")) continue;                  // no resources
        if (n.startsWith("java/") || n.startsWith("javax/") || n.startsWith("sun/") || n.startsWith("jdk/")) continue; // the JDK's win
        if (!seen.add(n)) continue;
        byte[] b = in.readAllBytes();
        boolean mock = n.startsWith("android/") || n.startsWith("com/android/") || n.startsWith("dalvik/") || n.startsWith("org/json/") || n.startsWith("org/xmlpull/") || n.startsWith("org/apache/http/");
        if (mock) b = rewrite(b, defaults);
        out.putNextEntry(new ZipEntry(n)); out.write(b); out.closeEntry();
      }
    }
  }
  static byte[] rewrite(byte[] b, boolean defaults) {
    ClassReader r = new ClassReader(b);
    ClassWriter w = new ClassWriter(0);
    r.accept(new ClassVisitor(Opcodes.ASM9, w) {
      String owner;
      public void visit(int v, int acc, String name, String sig, String sup, String[] itf) { owner = name; super.visit(v, acc, name, sig, sup, itf); }
      public FieldVisitor visitField(int acc, String name, String desc, String sig, Object value) {
        return super.visitField(acc & ~Opcodes.ACC_FINAL | ((value != null) ? (acc & Opcodes.ACC_FINAL) : 0), name, desc, sig, value);
      }
      public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] ex) {
        if (name.equals("<clinit>")) return null;
        if ((acc & Opcodes.ACC_ABSTRACT) != 0) return super.visitMethod(acc, name, desc, sig, ex);
        MethodVisitor mv = super.visitMethod(acc & ~Opcodes.ACC_NATIVE, name, desc, sig, ex);
        mv.visitCode();
        Type rt = Type.getReturnType(desc);
        int stack = 3;
        if (name.equals("<init>")) {
          // constructors must call a super constructor; with defaults they just return,
          // otherwise they throw (the verifier accepts a throw before super()).
          if (defaults) { mv.visitVarInsn(Opcodes.ALOAD, 0); mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superOf(r), "<init>", "()V", false); mv.visitInsn(Opcodes.RETURN); }
          else throwIt(mv, owner, name);
        } else if (defaults) {
          switch (rt.getSort()) {
            case Type.VOID: mv.visitInsn(Opcodes.RETURN); break;
            case Type.BOOLEAN: case Type.CHAR: case Type.BYTE: case Type.SHORT: case Type.INT: mv.visitInsn(Opcodes.ICONST_0); mv.visitInsn(Opcodes.IRETURN); break;
            case Type.LONG: mv.visitInsn(Opcodes.LCONST_0); mv.visitInsn(Opcodes.LRETURN); break;
            case Type.FLOAT: mv.visitInsn(Opcodes.FCONST_0); mv.visitInsn(Opcodes.FRETURN); break;
            case Type.DOUBLE: mv.visitInsn(Opcodes.DCONST_0); mv.visitInsn(Opcodes.DRETURN); break;
            default: mv.visitInsn(Opcodes.ACONST_NULL); mv.visitInsn(Opcodes.ARETURN);
          }
        } else throwIt(mv, owner, name);
        int locals = Type.getArgumentsAndReturnSizes(desc) >> 2;
        mv.visitMaxs(stack, locals + 1);
        mv.visitEnd();
        return null;
      }
    }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
    return w.toByteArray();
  }
  static String superOf(ClassReader r) { String s = r.getSuperName(); return s == null ? "java/lang/Object" : s; }
  static void throwIt(MethodVisitor mv, String owner, String name) {
    mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException");
    mv.visitInsn(Opcodes.DUP);
    mv.visitLdcInsn("Method " + name + " in " + owner.replace('/', '.') + " not mocked. See https://developer.android.com/r/studio-ui/build/not-mocked");
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false);
    mv.visitInsn(Opcodes.ATHROW);
  }
}
