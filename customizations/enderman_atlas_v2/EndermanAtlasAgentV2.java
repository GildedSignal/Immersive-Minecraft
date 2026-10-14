import java.lang.instrument.*;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.util.jar.JarFile;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.tools.attach.VirtualMachine;
import java.util.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
/** Staged install/restore entry point. Root review is required before any live invocation. */
public final class EndermanAtlasAgentV2 {
  static final Path ROOT=Path.of("C:/Users/PASCAL/Desktop/Minecraft modpack/tools/enderman-fix");
  static Class<?> target,helper;static byte[] original;
  static List<String> budgetCalls(byte[] bytes){
    ClassNode n=new ClassNode();new ClassReader(bytes).accept(n,0);List<String> calls=new ArrayList<>();
    for(MethodNode m:n.methods)for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals("mcperf/PhysicsBudgetV2"))calls.add(m.name+m.desc+":"+c.name+c.desc);
    String expected="blockifyEntity(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/entity/LivingEntity;)V:shouldSkip(Ljava/lang/Object;Ljava/lang/Object;)Z";
    if(!calls.equals(List.of(expected)))throw new IllegalStateException("Expected exact existing PhysicsBudgetV2.shouldSkip hook, found "+calls);
    return calls;
  }
  static void log(String s)throws Exception{Files.writeString(ROOT.resolve("atlas-agent-v2.log"),java.time.Instant.now()+" "+s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
  public static void agentmain(String mode,Instrumentation inst)throws Exception {
    if(mode.equals("status")){
      if(helper==null){log("not-installed");return;}
      log("enabled="+helper.getField("enabled").getBoolean(null)+" remapped="+helper.getField("remapped").getLong(null)+" skipped="+helper.getField("skipped").getLong(null)+" failures="+helper.getField("failures").getLong(null)+" lastError="+helper.getField("lastError").get(null));return;
    }
    if(mode.equals("restore")){
      if(original==null)throw new IllegalStateException("No live original captured");
      budgetCalls(original);
      helper.getField("enabled").setBoolean(null,false);
      inst.redefineClasses(new ClassDefinition(target,original));log("RESTORED captured live PhysicsMod; UV helper disabled");return;
    }
    if(!mode.equals("install")||original!=null)throw new IllegalStateException("Invalid mode or already installed");
    for(Class<?> c:inst.getAllLoadedClasses())if(c.getName().equals("net.diebuddies.physics.PhysicsMod"))target=c;
    if(target==null)throw new IllegalStateException("PhysicsMod not loaded");
    AtomicReference<byte[]> captured=new AtomicReference<>();
    ClassFileTransformer capture=new ClassFileTransformer(){public byte[] transform(ClassLoader loader,String n,Class<?> redef,ProtectionDomain p,byte[] b){if(redef==target)captured.set(b.clone());return null;}};
    inst.addTransformer(capture,true);try{inst.retransformClasses(target);}finally{inst.removeTransformer(capture);}
    byte[] before=captured.get();if(before==null)throw new IllegalStateException("Capture failed");
    List<String> existingBudget=budgetCalls(before);
    byte[] patched=EndermanAtlasPatchV2.patch(before);
    if(!budgetCalls(patched).equals(existingBudget))throw new IllegalStateException("Budget calls changed");
    Files.write(ROOT.resolve("PhysicsMod-before-atlas-v2-live.class"),before);
    inst.appendToBootstrapClassLoaderSearch(new JarFile(ROOT.resolve("enderman-atlas-helper-v2-staged.jar").toFile()));
    helper=Class.forName("mcperf.EndermanAtlasFixV2",true,null);
    // Helper defaults disabled so a failed redefine cannot partly change capture behavior.
    inst.redefineClasses(new ClassDefinition(target,patched));original=before;
    helper.getField("enabled").setBoolean(null,true);
    log("INSTALLED guarded Enderman body-only UV atlas correction, eyes untouched");
  }
  public static void main(String[] args)throws Exception {
    VirtualMachine vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]);}finally{vm.detach();}
  }
}

