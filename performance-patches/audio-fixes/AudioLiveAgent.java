import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.sun.tools.attach.VirtualMachine;

/** Bounded main-thread diagnostics; one explicit, narrow rain-setting repair. */
public final class AudioLiveAgent {
    static Class<?> loaded(Instrumentation i,String name){for(Class<?> c:i.getAllLoadedClasses())if(c.getName().equals(name))return c;throw new IllegalStateException("Not loaded: "+name);}
    static Object field(Object o,String name)throws Exception {for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    static Object call(Object o,String name)throws Exception{return o.getClass().getMethod(name).invoke(o);}
    static void write(Path p,String s)throws Exception{Files.writeString(p,Instant.now()+" "+s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    public static void agentmain(String args,Instrumentation inst)throws Exception {
        var parts=args.split("\\|",3);if(parts.length!=3)throw new IllegalArgumentException("mode|seconds|absolute-local-log");
        var output=Path.of(parts[2]).toAbsolutePath();Files.createDirectories(output.getParent());
        var mc=loaded(inst,"net.minecraft.client.Minecraft");var game=mc.getMethod("getInstance").invoke(null);var execute=mc.getMethod("execute",Runnable.class);
        if(parts[0].equals("rain-default")){
            var manager=loaded(inst,"pigcart.particlerain.config.ConfigManager");var done=new CountDownLatch(1);
            execute.invoke(game,(Runnable)()->{try{
                var config=manager.getMethod("getConfig").invoke(null);var sound=field(config,"sound");var volume=sound.getClass().getField("rainVolume");float before=volume.getFloat(sound);
                if(before!=0.0f&&before!=0.2f)throw new IllegalStateException("Rain volume changed independently; current="+before);
                // Keep an exact pre-repair copy; let the mod save its own live configuration.
                var gameDir=(java.io.File)field(game,"gameDirectory");var file=gameDir.toPath().resolve("config/particlerain/config.json");
                var backup=output.resolveSibling("particlerain-before-rain-repair.json");if(!Files.exists(backup))Files.copy(file,backup);
                volume.setFloat(sound,0.2f);manager.getMethod("save").invoke(null);
                write(output,"PASS rainVolume "+before+" -> "+volume.getFloat(sound)+"; saved by ConfigManager. Other settings untouched.");
            }catch(Throwable error){try{write(output,"FAIL "+error);}catch(Exception ignored){}}finally{done.countDown();}});
            if(!done.await(10,TimeUnit.SECONDS))write(output,"Repair queued; main thread busy.");return;
        }
        if(!parts[0].equals("sample"))throw new IllegalArgumentException("Unknown mode");
        int seconds=Integer.parseInt(parts[1]);if(seconds<1||seconds>300)throw new IllegalArgumentException("1..300 seconds");
        long deadline=System.nanoTime()+seconds*1_000_000_000L;var queued=new AtomicBoolean();
        var scheduler=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"Audio context sampler");t.setDaemon(true);return t;});
        scheduler.scheduleAtFixedRate(()->{if(System.nanoTime()>deadline){scheduler.shutdown();return;}if(!queued.compareAndSet(false,true))return;
            try{execute.invoke(game,(Runnable)()->{try{
                var level=field(game,"level");var player=field(game,"player");String state="focused="+call(game,"isWindowActive")+" fps="+call(game,"getFps");
                if(level!=null&&player!=null){
                    state+=" dimension="+call(level,"dimension")+" rain="+level.getClass().getMethod("getRainLevel",float.class).invoke(level,0f)+" thunder="+level.getClass().getMethod("getThunderLevel",float.class).invoke(level,0f);
                    state+=" eye="+call(player,"getEyePosition");
                    var soundManager=call(game,"getSoundManager");var engine=field(soundManager,"soundEngine");var channels=(java.util.Map<?,?>)field(engine,"instanceToChannel");
                    state+=" activeChannels="+channels.size();
                }
                var config=loaded(inst,"pigcart.particlerain.config.ConfigManager").getMethod("getConfig").invoke(null);state+=" rainVolume="+field(field(config,"sound"),"rainVolume");
                write(output,state);
            }catch(Throwable error){try{write(output,"ERROR "+error);}catch(Exception ignored){}}finally{queued.set(false);}});}catch(Exception error){scheduler.shutdown();}
        },0,1,TimeUnit.SECONDS);
    }
    public static void main(String[] args)throws Exception{var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]);}finally{vm.detach();}}
}
