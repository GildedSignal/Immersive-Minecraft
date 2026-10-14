import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.sun.tools.attach.VirtualMachine;

/** Read-only, bounded sampling of chest sound paths on the client thread. */
public final class ChestPathProbeV2 {
    static final ClassValue<Map<String,Method>> methods=new ClassValue<>(){protected Map<String,Method> computeValue(Class<?> c){var m=new HashMap<String,Method>();for(var x:c.getMethods())if(x.getParameterCount()==0)m.putIfAbsent(x.getName(),x);return m;}};
    static final ClassValue<Map<String,Field>> fields=new ClassValue<>(){protected Map<String,Field> computeValue(Class<?> c){var m=new HashMap<String,Field>();for(;c!=null;c=c.getSuperclass())for(var f:c.getDeclaredFields())try{f.setAccessible(true);m.putIfAbsent(f.getName(),f);}catch(Exception ignored){}return m;}};
    static Object call(Object o,String name)throws Exception{if(o==null)return null;var method=methods.get(o.getClass()).get(name);return method==null?null:method.invoke(o);}
    static Object field(Object o,String name)throws Exception{if(o==null)return null;var f=fields.get(o.getClass()).get(name);return f==null?null:f.get(o);}
    static Object original(Object sound)throws Exception{for(int n=0;n<8;n++){var next=call(sound,"getOriginal");if(next==null)next=call(sound,"getWrapped");if(next==null||next==sound)return sound;sound=next;}return sound;}
    static String csv(Object... values){var text=new StringJoiner(",");for(var v:values)text.add("\""+String.valueOf(v).replace("\"","\"\"").replace('\n',' ')+"\"");return text.toString();}
    @SuppressWarnings({"unchecked","rawtypes"})
    public static void agentmain(String argument,Instrumentation inst)throws Exception{
        var parts=argument.split("\\|",2);int seconds=Integer.parseInt(parts[0]);if(seconds<1||seconds>300)throw new IllegalArgumentException("1..300 seconds");
        var output=Path.of(parts[1]).toAbsolutePath();Files.createDirectories(output.getParent());
        Class<?> mc=null;for(var c:inst.getAllLoadedClasses())if(c.getName().equals("net.minecraft.client.Minecraft"))mc=c;if(mc==null)throw new IllegalStateException("Game not loaded");
        var loader=mc.getClassLoader();var game=mc.getMethod("getInstance").invoke(null);var execute=mc.getMethod("execute",Runnable.class);
        var vec=Class.forName("net.minecraft.world.phys.Vec3",false,loader);var blockPos=Class.forName("net.minecraft.core.BlockPos",false,loader);var levelType=Class.forName("net.minecraft.world.level.Level",false,loader);var playerType=Class.forName("net.minecraft.world.entity.player.Player",false,loader);
        var soundType=Class.forName("net.minecraft.client.resources.sounds.SoundInstance",false,loader);var ray=Class.forName("com.redsmods.sound_physics_perfected.RaycastingHelper",false,loader);
        var sight=ray.getMethod("hasLineOfSight",levelType,playerType,soundType);var between=ray.getMethod("countBlocksBetween",levelType,vec,vec,playerType);
        var vector=vec.getConstructor(double.class,double.class,double.class);var position=blockPos.getConstructor(int.class,int.class,int.class);var getState=levelType.getMethod("getBlockState",blockPos);
        var queue=new ArrayBlockingQueue<String>(1000);var finish=new AtomicBoolean();var pending=new AtomicBoolean();var samples=new AtomicInteger();var errors=new AtomicInteger();var nanos=new AtomicLong();var max=new AtomicLong();long deadline=System.nanoTime()+seconds*1_000_000_000L;
        var writer=new Thread(()->{try(var out=Files.newBufferedWriter(output)){out.write("utc,event,instance,volume,originalVolume,targetVolume,muffle,attenuation,eye,camera,originalPosition,perceivedPosition,lineOfSight,materialBetween,sourceBlock\n");while(!finish.get()||!queue.isEmpty()){var line=queue.poll(250,TimeUnit.MILLISECONDS);if(line!=null){out.write(line);out.newLine();}}}catch(Exception e){errors.incrementAndGet();}},"Chest path writer");writer.setDaemon(true);writer.start();
        var scheduler=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"Chest path sampler");t.setDaemon(true);return t;});
        scheduler.scheduleAtFixedRate(()->{
            if(System.nanoTime()>deadline||samples.get()>=1000){finish.set(true);scheduler.shutdown();try{Files.writeString(Path.of(output+".summary.txt"),"samples="+samples+" errors="+errors+" totalMainThreadMs="+nanos.get()/1e6+" maxMainThreadMs="+max.get()/1e6);}catch(Exception ignored){}return;}
            if(!pending.compareAndSet(false,true))return;
            try{execute.invoke(game,(Runnable)()->{long begin=System.nanoTime();try{
                var world=field(game,"level");var player=field(game,"player");if(world==null||player==null)return;
                var soundManager=call(game,"getSoundManager");var engine=field(soundManager,"soundEngine");var channels=(Map<?,?>)field(engine,"instanceToChannel");
                for(var sound:channels.keySet()){
                    var id=String.valueOf(call(sound,"getIdentifier"));if(!id.contains("chest"))continue;
                    var origin=original(sound);double x=((Number)call(origin,"getX")).doubleValue(),y=((Number)call(origin,"getY")).doubleValue(),z=((Number)call(origin,"getZ")).doubleValue();
                    var end=vector.newInstance(x,y,z);var eye=call(player,"getEyePosition");var camera=call(call(field(game,"gameRenderer"),"mainCamera"),"position");
                    var at=position.newInstance((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z));var source=getState.invoke(world,at);
                    var row=csv(Instant.now(),id,System.identityHashCode(sound),call(sound,"getVolume"),call(origin,"getVolume"),field(sound,"targetVolume"),field(sound,"permeationIndex"),field(sound,"attenuationMultiplier"),eye,camera,end,vector.newInstance(((Number)call(sound,"getX")).doubleValue(),((Number)call(sound,"getY")).doubleValue(),((Number)call(sound,"getZ")).doubleValue()),sight.invoke(null,world,player,sound),between.invoke(null,world,eye,end,player),source);
                    if(queue.offer(row))samples.incrementAndGet();
                }
            }catch(Exception e){if(errors.incrementAndGet()==1)queue.offer(csv(Instant.now(),"ERROR",e));}finally{long duration=System.nanoTime()-begin;nanos.addAndGet(duration);max.accumulateAndGet(duration,Math::max);pending.set(false);}});}catch(Exception e){finish.set(true);scheduler.shutdown();}
        },0,200,TimeUnit.MILLISECONDS);
    }
    public static void main(String[] args)throws Exception{var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]);}finally{vm.detach();}}
}
