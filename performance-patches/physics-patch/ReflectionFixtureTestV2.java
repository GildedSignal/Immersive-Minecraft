import java.nio.file.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import javax.tools.ToolProvider;
import mcperf.PhysicsBudgetV2;

/** Runs the real reflection helper against independently compiled game-shaped fixtures. */
public final class ReflectionFixtureTestV2 {
  static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
  public static void main(String[] args)throws Exception {
    Map<String,String> sources=new LinkedHashMap<>();
    sources.put("net.minecraft.client.multiplayer.ClientLevel","public class ClientLevel {}");
    sources.put("net.minecraft.world.entity.Entity","public class Entity { public int getId(){return 1;} }");
    sources.put("net.minecraft.world.entity.LivingEntity","public class LivingEntity extends Entity {}");
    sources.put("net.minecraft.world.entity.player.Player","public class Player extends net.minecraft.world.entity.LivingEntity {}");
    sources.put("net.diebuddies.physics.settings.mobs.MobPhysicsType","public enum MobPhysicsType {OFF,RAGDOLL,RAGDOLL_BREAK,RAGDOLL_BREAK_BLOOD,OTHER}");
    sources.put("net.diebuddies.physics.settings.mobs.MobSetting","public class MobSetting { public MobPhysicsType getType(){return MobPhysicsType.RAGDOLL;} }");
    sources.put("net.diebuddies.config.ConfigMobs","public class ConfigMobs {public static net.diebuddies.physics.settings.mobs.MobSetting getMobSetting(net.minecraft.world.entity.Entity e){return new net.diebuddies.physics.settings.mobs.MobSetting();}}");
    sources.put("net.diebuddies.config.ConfigClient","public class ConfigClient {public static int mobRagdollLimit=16;}");
    sources.put("net.diebuddies.physics.PhysicsRenderable","public class PhysicsRenderable {public boolean fading;public boolean isDespawning(){return fading;}}");
    sources.put("net.diebuddies.physics.IRigidBody","public class IRigidBody {public PhysicsRenderable entity=new PhysicsRenderable();public PhysicsRenderable getEntity(){return entity;}}");
    sources.put("net.diebuddies.physics.ragdoll.Ragdoll","public class Ragdoll {public boolean kinematic;public java.util.List<LinkedBody> btBodies=new java.util.ArrayList<>();public boolean isKinematic(){return kinematic;}public record LinkedBody(net.diebuddies.physics.IRigidBody rigid){} }");
    sources.put("net.diebuddies.physics.ragdoll.DynamicRagdoll","public class DynamicRagdoll extends Ragdoll {}");
    sources.put("net.diebuddies.physics.PhysicsWorld","public class PhysicsWorld {public java.util.List<Object> live=new java.util.ArrayList<>();public Iterable<?> getRagdolls(){return live;}}");
    sources.put("net.diebuddies.physics.PhysicsMod","public class PhysicsMod {public static PhysicsMod current=new PhysicsMod();public PhysicsWorld physicsWorld=new PhysicsWorld();public java.util.concurrent.ConcurrentLinkedQueue<Object> ragdolls=new java.util.concurrent.ConcurrentLinkedQueue<>();public static PhysicsMod getInstance(net.minecraft.client.multiplayer.ClientLevel l){return current;}}");
    Path src=Path.of("tools/physics-patch/fixture-src"),out=Path.of("tools/physics-patch/fixture-classes");Files.createDirectories(out);
    List<String> flags=new ArrayList<>(List.of("-d",out.toString()));
    for(var e:sources.entrySet()) {
      Path p=src.resolve(e.getKey().replace('.','/')+".java");Files.createDirectories(p.getParent());
      Files.writeString(p,"package "+e.getKey().substring(0,e.getKey().lastIndexOf('.'))+";"+e.getValue());flags.add(p.toString());
    }
    check(ToolProvider.getSystemJavaCompiler().run(null,null,null,flags.toArray(String[]::new))==0,"Fixture compilation");
    try(URLClassLoader l=new URLClassLoader(new URL[]{out.toUri().toURL()},ReflectionFixtureTestV2.class.getClassLoader())) {
      Object level=l.loadClass("net.minecraft.client.multiplayer.ClientLevel").getConstructor().newInstance();
      Object mob=l.loadClass("net.minecraft.world.entity.LivingEntity").getConstructor().newInstance();
      Class<?> modType=l.loadClass("net.diebuddies.physics.PhysicsMod"),ragType=l.loadClass("net.diebuddies.physics.ragdoll.Ragdoll");
      Object mod=modType.getField("current").get(null),world=modType.getField("physicsWorld").get(mod);
      @SuppressWarnings("unchecked") List<Object> live=(List<Object>)world.getClass().getField("live").get(world);
      @SuppressWarnings("unchecked") Queue<Object> queue=(Queue<Object>)modType.getField("ragdolls").get(mod);
      check(!PhysicsBudgetV2.shouldSkip(level,mob),"Ordinary death admitted");
      for(int i=0;i<15;i++)live.add(ragType.getConstructor().newInstance());
      check(!PhysicsBudgetV2.shouldSkip(level,mob),"15 live admits16th");
      Object last=ragType.getConstructor().newInstance();queue.add(last);
      check(PhysicsBudgetV2.shouldSkip(level,mob),"Pending16th closes budget");
      queue.remove(last);PhysicsBudgetV2.trackTransfer(world,last);
      check(PhysicsBudgetV2.shouldSkip(level,mob),"Native-to-list transfer still closes budget");
      live.add(last);PhysicsBudgetV2.finishTransfer(world,last);
      // No intervening shouldSkip: reproduce complete lifetime between two deaths.
      live.remove(last);check(!PhysicsBudgetV2.shouldSkip(level,mob),"Inserted then cleaned between deaths must reopen budget");
      queue.add(last);check(PhysicsBudgetV2.shouldSkip(level,mob),"Pending closes reopened budget");
      queue.remove(last);PhysicsBudgetV2.trackTransfer(world,last);live.add(last);PhysicsBudgetV2.finishTransfer(world,last);
      check(PhysicsBudgetV2.shouldSkip(level,mob),"Live list ownership closes budget without duplicate transfer");
      live.remove(last);check(!PhysicsBudgetV2.shouldSkip(level,mob),"Cleanup reopens budget; transfer not double-counted");
      Object rb=l.loadClass("net.diebuddies.physics.IRigidBody").getConstructor().newInstance();
      Object rendered=rb.getClass().getField("entity").get(rb);
      Object link=l.loadClass("net.diebuddies.physics.ragdoll.Ragdoll$LinkedBody").getConstructors()[0].newInstance(rb);
      @SuppressWarnings("unchecked") List<Object> links=(List<Object>)ragType.getField("btBodies").get(last);links.add(link);
      check(!PhysicsBudgetV2.pruningSkip(last),"Fresh corpse is pruning candidate");
      rendered.getClass().getField("fading").setBoolean(rendered,true);
      check(PhysicsBudgetV2.pruningSkip(last),"Fading corpse excluded from repeated prune");
      live.add(last);check(PhysicsBudgetV2.shouldSkip(level,mob),"Fading corpse INCLUDED in early hard budget");
      ragType.getField("kinematic").setBoolean(last,true);
      check(PhysicsBudgetV2.pruningSkip(last)&&!PhysicsBudgetV2.shouldSkip(level,mob),"Kinematic skip consistent");
      Object player=l.loadClass("net.minecraft.world.entity.player.Player").getConstructor().newInstance();
      check(!PhysicsBudgetV2.shouldSkip(level,player),"Player death unchanged");
      check(PhysicsBudgetV2.failures==0,"Reflection no failures");
    }
    System.out.println("PASS real helper reflection, pending/native-transfer/live ownership, fade/kinematic pruning, reopened budget");
  }
}
