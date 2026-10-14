import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import mcperf.PhysicsBudgetV2;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

public final class PhysicsBudgetTestV2 {
  record Corpse(boolean dynamic,boolean kinematic,boolean fading) {}
  static Corpse mob(){return new Corpse(false,false,false);}
  static final Predicate<Object> counted=r->{Corpse c=(Corpse)r;return !c.dynamic&&!c.kinematic;};
  static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
  static int remainingLive(List<Corpse> input,int limit,boolean patched) {
    List<Corpse> bodies=new ArrayList<>(input);
    int alive=(int)bodies.stream().filter(c->!c.dynamic&&!c.kinematic&&!c.fading).count();
    int budget=alive-limit;
    for(int i=0;i<bodies.size()&&budget>0;i++) {
      Corpse c=bodies.get(i);
      if(c.dynamic || (patched&&(c.kinematic||c.fading)))continue;
      bodies.set(i,new Corpse(c.dynamic,c.kinematic,true));budget--;
    }
    return (int)bodies.stream().filter(c->!c.dynamic&&!c.kinematic&&!c.fading).count();
  }
  static void verify(String path,String target,String method)throws Exception {
    byte[] original=Files.readAllBytes(Path.of(path)), patched=PhysicsBudgetAgentV2.patch(target,original);
    ClassNode node=new ClassNode();new ClassReader(patched).accept(node,ClassReader.EXPAND_FRAMES);
    MethodNode modified=node.methods.stream().filter(m->m.name.equals(method)).findFirst().orElseThrow();
    new Analyzer<>(new BasicVerifier()).analyze(node.name,modified);
    if(target.equals(PhysicsBudgetAgentV2.WORLD)) {
      MethodNode handoff=node.methods.stream().filter(m->m.name.equals("addRagdoll")).findFirst().orElseThrow();
      new Analyzer<>(new BasicVerifier()).analyze(node.name,handoff);
      check(Arrays.stream(handoff.instructions.toArray()).filter(n->n instanceof MethodInsnNode c&&c.owner.equals("mcperf/PhysicsBudgetV2")&&c.name.equals("trackTransfer")).count()==1,"One transfer hook");
      MethodNode finished=node.methods.stream().filter(m->m.name.startsWith("lambda$addRagdoll$")).findFirst().orElseThrow();
      new Analyzer<>(new BasicVerifier()).analyze(node.name,finished);
      check(Arrays.stream(finished.instructions.toArray()).filter(n->n instanceof MethodInsnNode c&&c.owner.equals("mcperf/PhysicsBudgetV2")&&c.name.equals("finishTransfer")).count()==1,"One successful-live-insertion hook");
    }
    long helpers=Arrays.stream(modified.instructions.toArray()).filter(n->n instanceof MethodInsnNode c&&c.owner.equals("mcperf/PhysicsBudgetV2")).count();
    check(helpers==1,"Exactly one helper in "+method);
    Files.write(Path.of("tools/physics-patch/"+node.name.substring(node.name.lastIndexOf('/')+1)+"-staged.class"),patched);
    // Schema unchanged, required for safe JVM redefineClasses.
    ClassNode old=new ClassNode();new ClassReader(original).accept(old,0);
    check(old.fields.size()==node.fields.size()&&old.methods.size()==node.methods.size(),"Schema changed");
    for(int i=0;i<old.methods.size();i++)check(old.methods.get(i).name.equals(node.methods.get(i).name)&&old.methods.get(i).desc.equals(node.methods.get(i).desc),"Method schema changed");
    System.out.println("PASS installed-bytecode transform and ASM stack verification: "+method);
  }
  public static void main(String[] args)throws Exception {
    List<Corpse> live=new ArrayList<>(),pending=new ArrayList<>();
    check(!PhysicsBudgetV2.saturated(live,pending,16,counted),"Empty world admitted");
    for(int i=0;i<15;i++)live.add(mob());
    check(!PhysicsBudgetV2.saturated(live,pending,16,counted),"15 admitted");
    pending.add(mob());check(PhysicsBudgetV2.saturated(live,pending,16,counted),"Pending creation counted");
    pending.clear();live.set(0,new Corpse(false,false,true));pending.add(mob());
    check(PhysicsBudgetV2.saturated(live,pending,16,counted),"Fading corpse still occupies hard budget");
    live.set(0,new Corpse(true,false,false));check(!PhysicsBudgetV2.saturated(live,pending,16,counted),"Structural dynamic ragdoll excluded");
    live.set(0,new Corpse(false,true,false));check(!PhysicsBudgetV2.saturated(live,pending,16,counted),"Kinematic ragdoll excluded");
    check(PhysicsBudgetV2.saturated(List.of(),List.of(),0,counted),"Zero budget allows vanilla deaths");
    final int[] reads={0};Predicate<Object> tracked=r->{reads[0]++;return counted.test(r);};
    PhysicsBudgetV2.saturated(Collections.nCopies(10000,mob()),List.of(),16,tracked);
    check(reads[0]==16,"Over-budget scan bounded at16");
    List<Corpse> prune=new ArrayList<>();prune.add(new Corpse(false,false,true));for(int i=0;i<17;i++)prune.add(mob());
    check(remainingLive(prune,16,false)==17,"Reproduce current fading-corpse prune defect");
    check(remainingLive(prune,16,true)==16,"Fixed prune selects an un-fading corpse");
    prune.set(0,new Corpse(false,true,false));check(remainingLive(prune,16,false)==17&&remainingLive(prune,16,true)==16,"Kinematic prune mismatch fixed");
    verify("source-reference/physics-analysis/classes/PhysicsMod.class",PhysicsBudgetAgentV2.MOD,"blockifyEntity");
    verify("source-reference/classes/PhysicsWorld.class",PhysicsBudgetAgentV2.WORLD,"updatePhysicsObjects");
    System.out.println("PASS corpse budget/pending/fading/structural filters and pruning regression model");
  }
}
