import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
public final class EndermanAtlasPatch {
  static final String TARGET="net/diebuddies/physics/PhysicsMod";
  public static byte[] patch(byte[] original)throws Exception {
    ClassNode node=new ClassNode();new ClassReader(original).accept(node,ClassReader.EXPAND_FRAMES);
    if(!node.name.equals(TARGET))throw new IllegalArgumentException("Unexpected target");
    int edits=0;
    for(MethodNode m:node.methods)if(m.name.equals("createParticlesFromCuboids")){
      if(!m.desc.equals("(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/util/List;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/EntityRenderer;Lnet/minecraft/client/renderer/entity/layers/RenderLayer;ZIFFF)V"))throw new IllegalStateException("Changed capture signature");
      boolean box=false,texture=false;
      for(LocalVariableNode l:m.localVariables){if(l.name.equals("box")&&l.index==31)box=true;if(l.name.equals("textureID")&&l.index==18)texture=true;}
      if(!box||!texture)throw new IllegalStateException("Verified cube/texture locals changed");
      for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) {
        if(n instanceof MethodInsnNode c&&c.owner.equals("org/joml/Vector2f")&&c.name.equals("<init>")&&c.desc.equals("(FF)V")) {
          AbstractInsnNode before=n.getPrevious();while(before!=null&&before.getOpcode()<0)before=before.getPrevious();
          if(!(before instanceof MethodInsnNode v&&v.owner.equals("net/minecraft/client/model/geom/ModelPart$Vertex")&&v.name.equals("v")&&v.desc.equals("()F")))continue;
          InsnList hook=new InsnList();
          hook.add(new VarInsnNode(Opcodes.ALOAD,31));hook.add(new VarInsnNode(Opcodes.ALOAD,3));hook.add(new VarInsnNode(Opcodes.ALOAD,18));hook.add(new VarInsnNode(Opcodes.ALOAD,5));
          hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"mcperf/EndermanAtlasFix","remap","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));
          m.instructions.insert(n,hook);edits++;
        }
      }
    }
    if(edits!=1)throw new IllegalStateException("Expected one UV capture hook, got"+edits);
    ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);byte[] result=writer.toByteArray();
    ClassNode checked=new ClassNode();new ClassReader(result).accept(checked,0);
    for(MethodNode m:checked.methods)if(m.instructions.size()>0)new Analyzer<>(new BasicVerifier()).analyze(checked.name,m);
    return result;
  }
  public static void main(String[] args)throws Exception{
    byte[] original=Files.readAllBytes(Path.of(args[0])),patched=patch(original);Files.write(Path.of(args[1]),patched);
    System.out.println("PASS exactly one UV insertion, all PhysicsMod methods ASM BasicVerifier; schema unchanged.");
  }
}
