import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;

/** Patches the original on-disk mod, never transformed live/Mixin bytes. */
public class PersistSoundPatch {
  static final String TARGET="com/redsmods/sound_physics_perfected/RaycastingHelper.class";
  static final String INPUT_SHA="d20d20139ce0a43a5e47965e40ba64901a00434606b6f40d7a75bf3fefe7d80a";
  static byte[] patch(byte[] input) {
    int[] edits={0,0};
    ClassReader r=new ClassReader(input); ClassWriter w=new ClassWriter(0);
    r.accept(new ClassVisitor(Opcodes.ASM9,w) {
      @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
        MethodVisitor m=super.visitMethod(a,n,d,s,e);
        if(!n.startsWith("lambda$castRedRay$")&&!n.startsWith("lambda$castGreenRay$"))return m;
        return new MethodVisitor(Opcodes.ASM9,m) {
          @Override public void visitTypeInsn(int op,String t) {
            if(op==Opcodes.NEW&&t.equals("java/util/concurrent/CopyOnWriteArrayList")){t="mcperf/AppendHitList";edits[0]++;}
            super.visitTypeInsn(op,t);
          }
          @Override public void visitMethodInsn(int op,String o,String n,String d,boolean i) {
            if(op==Opcodes.INVOKESPECIAL&&o.equals("java/util/concurrent/CopyOnWriteArrayList")&&n.equals("<init>")&&d.equals("()V")){o="mcperf/AppendHitList";edits[1]++;}
            super.visitMethodInsn(op,o,n,d,i);
          }
        };
      }
    },0);
    if(edits[0]!=7||edits[1]!=7)throw new IllegalStateException("Expected 7 matched factories, got "+Arrays.toString(edits));
    return w.toByteArray();
  }
  public static void main(String[] args)throws Exception {
    Path input=Path.of(args[0]), output=Path.of(args[1]), helper=Path.of(args[2]);
    String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input)));
    if(!hash.equals(INPUT_SHA))throw new IllegalStateException("Unexpected input jar SHA256: "+hash);
    if(Files.exists(output))throw new IllegalStateException("Output already exists: "+output);
    try(JarFile original=new JarFile(input.toFile()); JarOutputStream out=new JarOutputStream(Files.newOutputStream(output,StandardOpenOption.CREATE_NEW))) {
      var entries=original.entries(); boolean found=false;
      while(entries.hasMoreElements()) {
        JarEntry entry=entries.nextElement(); byte[] bytes=original.getInputStream(entry).readAllBytes();
        if(entry.getName().equals(TARGET)){bytes=patch(bytes);found=true;}
        JarEntry copy=new JarEntry(entry.getName());copy.setTime(entry.getTime());
        out.putNextEntry(copy);out.write(bytes);out.closeEntry();
      }
      if(!found)throw new IllegalStateException("Missing target class");
      out.putNextEntry(new JarEntry("mcperf/AppendHitList.class"));out.write(Files.readAllBytes(helper));out.closeEntry();
    }
    try(JarFile original=new JarFile(input.toFile());JarFile patched=new JarFile(output.toFile())) {
      int unchanged=0;var entries=original.entries();
      while(entries.hasMoreElements()) {
        var e=entries.nextElement();if(e.getName().equals(TARGET))continue;
        if(!Arrays.equals(original.getInputStream(e).readAllBytes(),patched.getInputStream(patched.getJarEntry(e.getName())).readAllBytes()))throw new IllegalStateException("Changed unrelated entry "+e.getName());
        unchanged++;
      }
      if(patched.size()!=original.size()+1)throw new IllegalStateException("Unexpected entry count");
      System.out.println("PASS: 7 factories patched; "+unchanged+" original entries byte-identical; one helper class added.");
    }
  }
}
