import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
public final class EndermanAtlasPatchV2 {
  public static byte[] patch(byte[] bytes)throws Exception {
    ClassNode n=new ClassNode();new ClassReader(bytes).accept(n,ClassReader.EXPAND_FRAMES);int replaced=0,newCalls=0;
    if(!n.name.equals("net/diebuddies/physics/PhysicsMod"))throw new IllegalArgumentException("Wrong class");
    for(MethodNode m:n.methods)for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode c){
      if(c.owner.equals("mcperf/EndermanAtlasFixV2"))newCalls++;
      if(c.owner.equals("mcperf/EndermanAtlasFix")){
        if(!m.name.equals("createParticlesFromCuboids")||!c.name.equals("remap")||!c.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))throw new IllegalStateException("Unexpected existing atlas hook");
        c.owner="mcperf/EndermanAtlasFixV2";replaced++;
      }
    }
    if(newCalls!=0||replaced>1)throw new IllegalStateException("Unexpected atlas hooks old="+replaced+" new="+newCalls);
    // A new game launch loads the now-persistent budget class, with no old UV
    // helper. Insert once into that class; an existing V1 hook is replaced once.
    if(replaced==0)return patch(EndermanAtlasPatch.patch(bytes));
    ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(out);byte[] result=out.toByteArray();ClassNode checked=new ClassNode();new ClassReader(result).accept(checked,0);
    for(MethodNode m:checked.methods)if(m.instructions.size()>0)new Analyzer<>(new BasicVerifier()).analyze(checked.name,m);
    return result;
  }
}
