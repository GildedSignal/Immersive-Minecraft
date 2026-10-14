import java.lang.instrument.*;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import com.sun.tools.attach.VirtualMachine;

/** Staged experiment. Installation and restoration each redefine both targets atomically. */
public class PhysicsBudgetAgentV2 {
  static final String MOD="net/diebuddies/physics/PhysicsMod", WORLD="net/diebuddies/physics/PhysicsWorld";
  static final Path ROOT=Path.of("C:/Users/PASCAL/Desktop/Minecraft modpack/tools/physics-patch");
  static final Map<Class<?>,byte[]> originals=new LinkedHashMap<>();
  static AbstractInsnNode nextCode(AbstractInsnNode n){do{n=n.getNext();}while(n!=null&&n.getOpcode()<0);return n;}
  public static byte[] patch(String target,byte[] original) {
    ClassNode node=new ClassNode();new ClassReader(original).accept(node,ClassReader.EXPAND_FRAMES);
    if(!node.name.equals(target))throw new IllegalArgumentException("Wrong class: "+node.name);
    int edits=0;
    for(MethodNode m:node.methods) {
      if(target.equals(MOD)&&m.name.equals("blockifyEntity")&&m.desc.equals("(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/entity/LivingEntity;)V")) {
        boolean seen=false;
        for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) {
          if(n instanceof FieldInsnNode f && f.owner.equals(MOD)&&f.name.equals("alreadyBlockified")&&f.getOpcode()==Opcodes.GETFIELD)seen=true;
          if(seen && n instanceof MethodInsnNode c && c.owner.equals("java/util/Set")&&c.name.equals("add")&&c.desc.equals("(Ljava/lang/Object;)Z")) {
            AbstractInsnNode pop=nextCode(n);if(pop.getOpcode()!=Opcodes.POP)throw new IllegalStateException("Death-set add no longer pops result");
            FrameNode preceding=null;
            for(AbstractInsnNode p=pop.getPrevious();p!=null;p=p.getPrevious())if(p instanceof FrameNode f){preceding=f;break;}
            if(preceding==null||preceding.local.size()<4||!preceding.local.get(0).equals("net/minecraft/client/multiplayer/ClientLevel")||!preceding.local.get(1).equals("net/minecraft/world/entity/LivingEntity")||!preceding.local.get(2).equals(MOD))
              throw new IllegalStateException("Death budget locals no longer match the verified method");
            InsnList gate=new InsnList();LabelNode keep=new LabelNode();
            gate.add(new VarInsnNode(Opcodes.ALOAD,0));gate.add(new VarInsnNode(Opcodes.ALOAD,1));
            gate.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"mcperf/PhysicsBudgetV2","shouldSkip","(Ljava/lang/Object;Ljava/lang/Object;)Z",false));
            gate.add(new JumpInsnNode(Opcodes.IFEQ,keep));gate.add(new InsnNode(Opcodes.RETURN));gate.add(keep);
            gate.add(new FrameNode(Opcodes.F_NEW,preceding.local.size(),preceding.local.toArray(),0,new Object[0]));
            m.instructions.insert(pop,gate);edits++;break;
          }
        }
      }
      if(target.equals(WORLD)&&m.name.equals("updatePhysicsObjects")&&m.desc.equals("(D)V")) {
        int occurrences=0;
        for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) {
          if(n instanceof TypeInsnNode t&&t.getOpcode()==Opcodes.INSTANCEOF&&t.desc.equals("net/diebuddies/physics/ragdoll/DynamicRagdoll")) {
            if(++occurrences==2){m.instructions.set(t,new MethodInsnNode(Opcodes.INVOKESTATIC,"mcperf/PhysicsBudgetV2","pruningSkip","(Ljava/lang/Object;)Z",false));edits++;}
          }
        }
        if(occurrences!=2)throw new IllegalStateException("Expected exactly two dynamic checks, got "+occurrences);
      }
      if(target.equals(WORLD)&&m.name.equals("addRagdoll")&&m.desc.equals("(Lnet/diebuddies/physics/ragdoll/Ragdoll;)V")) {
        for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) {
          if(n instanceof MethodInsnNode c&&c.owner.equals("net/diebuddies/physics/ragdoll/Ragdoll")&&c.name.equals("add")&&c.desc.equals("(Lnet/diebuddies/physics/PhysicsWorld;)V")) {
            InsnList tracked=new InsnList();tracked.add(new VarInsnNode(Opcodes.ALOAD,0));tracked.add(new VarInsnNode(Opcodes.ALOAD,1));
            tracked.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"mcperf/PhysicsBudgetV2","trackTransfer","(Ljava/lang/Object;Ljava/lang/Object;)V",false));
            m.instructions.insert(n,tracked);edits++;break;
          }
        }
      }
      if(target.equals(WORLD)&&m.name.startsWith("lambda$addRagdoll$")&&m.desc.equals("(Lnet/diebuddies/physics/ragdoll/Ragdoll;)V")) {
        for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) {
          if(n instanceof MethodInsnNode c&&c.owner.equals("net/diebuddies/util/DoublyLinkedList")&&c.name.equals("add")&&c.desc.equals("(Lnet/diebuddies/util/DoublyLinkedList$NodeStorage;)V")) {
            InsnList finished=new InsnList();finished.add(new VarInsnNode(Opcodes.ALOAD,0));finished.add(new VarInsnNode(Opcodes.ALOAD,1));
            finished.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"mcperf/PhysicsBudgetV2","finishTransfer","(Ljava/lang/Object;Ljava/lang/Object;)V",false));
            m.instructions.insert(n,finished);edits++;break;
          }
        }
      }
    }
    int expected=target.equals(WORLD)?3:1;
    if(edits!=expected)throw new IllegalStateException("Expected "+expected+" edits in "+target+", got "+edits);
    ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
  }
  static void log(String s)throws Exception{Files.writeString(ROOT.resolve("agent-v2.log"),java.time.Instant.now()+" "+s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
  public static void agentmain(String mode,Instrumentation inst)throws Exception {
    if(mode.equals("restore")) {
      if(originals.size()!=2)throw new IllegalStateException("No complete saved installation");
      List<ClassDefinition> defs=new ArrayList<>();for(var e:originals.entrySet())defs.add(new ClassDefinition(e.getKey(),e.getValue()));
      inst.redefineClasses(defs.toArray(ClassDefinition[]::new));log("Restored original methods; configured body limit unchanged");return;
    }
    if(!mode.equals("install")||!originals.isEmpty())throw new IllegalArgumentException("Unknown mode or already captured");
    Map<String,Class<?>> types=new LinkedHashMap<>();for(Class<?> c:inst.getAllLoadedClasses()) {
      String name=c.getName().replace('.','/');if(name.equals(MOD)||name.equals(WORLD))types.put(name,c);
    }
    if(types.size()!=2)throw new IllegalStateException("Both Physics Mod classes must be loaded");
    Map<String,byte[]> bytes=new HashMap<>();
    ClassFileTransformer capture=new ClassFileTransformer(){public byte[] transform(ClassLoader loader,String name,Class<?> redefined,ProtectionDomain pd,byte[] b){if(types.containsKey(name))bytes.put(name,b.clone());return null;}};
    inst.addTransformer(capture,true);try{inst.retransformClasses(types.values().toArray(Class<?>[]::new));}finally{inst.removeTransformer(capture);}
    if(bytes.size()!=2)throw new IllegalStateException("Capture incomplete");
    List<ClassDefinition> defs=new ArrayList<>();
    for(var e:types.entrySet()) {
      byte[] b=bytes.get(e.getKey()),updated=patch(e.getKey(),b);
      Files.write(ROOT.resolve(e.getKey().substring(e.getKey().lastIndexOf('/')+1)+"-before-live.class"),b);
      defs.add(new ClassDefinition(e.getValue(),updated));
    }
    inst.appendToBootstrapClassLoaderSearch(new JarFile(ROOT.resolve("physics-budget-helper-v2.jar").toFile()));
    Class.forName("mcperf.PhysicsBudgetV2",true,null);
    inst.redefineClasses(defs.toArray(ClassDefinition[]::new));
    for(var e:types.entrySet())originals.put(e.getValue(),bytes.get(e.getKey()));
    log("Installed early corpse budget plus consistent prune filter; body limit unchanged");
  }
  public static void main(String[] args)throws Exception {
    VirtualMachine vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]);}finally{vm.detach();}
  }
}
