package mcperf;

import java.lang.reflect.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Predicate;

/** Bootstrap helper: no game types linked into the bootstrap loader, no native cleanup. */
public final class PhysicsBudgetV2 {
  private static volatile Access access;
  public static volatile long admitted, overflow, failures;
  private static volatile boolean failed;
  private static WeakReference<Object> trackedWorld=new WeakReference<>(null);
  private static final IdentityHashMap<Object,Boolean> transferring=new IdentityHashMap<>();
  private PhysicsBudgetV2() {}

  private static final class Access {
    final Method instance, mobSetting, mobType, ragdolls, kinematic, rigid, entity, despawning;
    final Field physicsWorld, pending, limit;
    final Class<?> dynamic, player;
    final ClassValue<Field> linkedBodies = new ClassValue<>() {
      protected Field computeValue(Class<?> c) { try { return c.getField("btBodies"); } catch(Exception e){throw new IllegalStateException(e);} }
    };
    Access(ClassLoader loader) throws Exception {
      Class<?> mod=Class.forName("net.diebuddies.physics.PhysicsMod",false,loader);
      Class<?> level=Class.forName("net.minecraft.client.multiplayer.ClientLevel",false,loader);
      Class<?> e=Class.forName("net.minecraft.world.entity.Entity",false,loader);
      Class<?> mobs=Class.forName("net.diebuddies.config.ConfigMobs",false,loader);
      Class<?> rag=Class.forName("net.diebuddies.physics.ragdoll.Ragdoll",false,loader);
      Class<?> link=Class.forName("net.diebuddies.physics.ragdoll.Ragdoll$LinkedBody",false,loader);
      Class<?> rb=Class.forName("net.diebuddies.physics.IRigidBody",false,loader);
      Class<?> render=Class.forName("net.diebuddies.physics.PhysicsRenderable",false,loader);
      dynamic=Class.forName("net.diebuddies.physics.ragdoll.DynamicRagdoll",false,loader);
      player=Class.forName("net.minecraft.world.entity.player.Player",false,loader);
      instance=mod.getMethod("getInstance",level);
      mobSetting=mobs.getMethod("getMobSetting",e); mobType=mobSetting.getReturnType().getMethod("getType");
      physicsWorld=mod.getField("physicsWorld"); pending=mod.getField("ragdolls");
      ragdolls=physicsWorld.getType().getMethod("getRagdolls");
      kinematic=rag.getMethod("isKinematic"); rigid=link.getMethod("rigid");
      entity=rb.getMethod("getEntity"); despawning=render.getMethod("isDespawning");
      limit=Class.forName("net.diebuddies.config.ConfigClient",false,loader).getField("mobRagdollLimit");
    }
    boolean ordinaryMob(Object r) throws Exception { return !dynamic.isInstance(r) && !(boolean)kinematic.invoke(r); }
    boolean pruningSkip(Object r) throws Exception {
      if(!ordinaryMob(r))return true;
      for(Object link:(Iterable<?>)linkedBodies.get(r.getClass()).get(r)) {
        Object rendered=entity.invoke(rigid.invoke(link));
        // Corpses share a lifetime; first live body is the same sentinel used by upstream.
        if((boolean)despawning.invoke(rendered))return true;
        return false;
      }
      return true;
    }
  }
  private static Access init(ClassLoader loader) throws Exception {
    Access a=access;
    if(a==null)synchronized(PhysicsBudgetV2.class){if((a=access)==null)access=a=new Access(loader);}
    return a;
  }
  /** Counts to the configured bound then stops; includes fading corpses and queued creation. */
  public static boolean saturated(Iterable<?> live, Iterable<?> pending, int limit, Predicate<Object> eligible) {
    if(limit<=0)return true;
    int count=0;
    for(Object r:live)if(eligible.test(r)&&++count>=limit)return true;
    for(Object r:pending)if(eligible.test(r)&&++count>=limit)return true;
    return false;
  }
  /** Called after upstream records this death, before model extraction, EMF changes or body creation. */
  public static boolean shouldSkip(Object level,Object mob) {
    if(failed)return false;
    try {
      Access a=init(level.getClass().getClassLoader());
      if(a.player.isInstance(mob))return false;
      String type=((Enum<?>)a.mobType.invoke(a.mobSetting.invoke(null,mob))).name();
      if(!type.equals("RAGDOLL")&&!type.equals("RAGDOLL_BREAK")&&!type.equals("RAGDOLL_BREAK_BLOOD"))return false;
      Object mod=a.instance.invoke(null,level), world=a.physicsWorld.get(mod);
      boolean full;
      synchronized(PhysicsBudgetV2.class) {
        setWorld(world);
        Iterable<?> live=(Iterable<?>)a.ragdolls.invoke(world);
        // A native body is created immediately, while list insertion is queued. Retain that
        // small handoff interval until the actual live list takes ownership.
        for(Object r:live)transferring.remove(r);
        Iterable<?> queued=(Iterable<?>)a.pending.get(mod);
        Iterable<Object> extra=()->new Iterator<>() {
          final Iterator<?> queue=queued.iterator(), transfer=transferring.keySet().iterator();
          public boolean hasNext(){return queue.hasNext()||transfer.hasNext();}
          public Object next(){return queue.hasNext()?queue.next():transfer.next();}
        };
        full=saturated(live,extra,a.limit.getInt(null),r->{
          try{return a.ordinaryMob(r);}catch(Exception e){throw new IllegalStateException(e);}
        });
      }
      if(full)overflow++;else admitted++;
      return full;
    }catch(Exception e){failed=true;failures++;System.err.println("[PhysicsBudget] Disabled after reflection failure: "+e);return false;}
  }
  private static void setWorld(Object world) {
    if(trackedWorld.get()!=world){trackedWorld=new WeakReference<>(world);transferring.clear();}
  }
  /** Called only after the upstream native add succeeds; does not change body ownership. */
  public static synchronized void trackTransfer(Object world,Object ragdoll) {
    if(failed)return;
    try {
      Access a=init(world.getClass().getClassLoader());setWorld(world);
      if(a.ordinaryMob(ragdoll))transferring.put(ragdoll,Boolean.TRUE);
    }catch(Exception e){failed=true;failures++;System.err.println("[PhysicsBudget] Disabled transfer tracking: "+e);}
  }
  /** Queued list insertion has completed; ownership is live even if no further death occurs. */
  public static synchronized void finishTransfer(Object world,Object ragdoll) {
    if(trackedWorld.get()==world)transferring.remove(ragdoll);
  }
  public static boolean pruningSkip(Object ragdoll) {
    try{return init(ragdoll.getClass().getClassLoader()).pruningSkip(ragdoll);}
    catch(Exception e){
      failed=true;failures++;
      // Preserve the original skip test if reflection fails; never interrupt physics cleanup.
      for(Class<?> c=ragdoll.getClass();c!=null;c=c.getSuperclass())
        if(c.getName().equals("net.diebuddies.physics.ragdoll.DynamicRagdoll"))return true;
      return false;
    }
  }
}
