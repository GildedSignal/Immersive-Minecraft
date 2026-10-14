import java.lang.instrument.*;
import java.nio.file.*;
import java.util.concurrent.*;
import com.sun.tools.attach.VirtualMachine;
public final class ContainerSourceLiveCheck {
    public static void agentmain(String file,Instrumentation inst)throws Exception{
        Class<?> mc=null;for(var c:inst.getAllLoadedClasses())if(c.getName().equals("net.minecraft.client.Minecraft"))mc=c;if(mc==null)throw new IllegalStateException("Minecraft unavailable");
        var game=mc.getMethod("getInstance").invoke(null);var loader=mc.getClassLoader();var pos=Class.forName("net.minecraft.core.BlockPos",false,loader);var constructor=pos.getConstructor(int.class,int.class,int.class);var compare=Class.forName("mcperf.audio.ContainerSource",true,null).getMethod("isSource",Object.class,Object.class,Object.class);var levelField=mc.getField("level");var done=new CountDownLatch(1);
        mc.getMethod("execute",Runnable.class).invoke(game,(Runnable)()->{try{
            var world=levelField.get(game);var getState=world.getClass().getMethod("getBlockState",pos);var source=constructor.newInstance(26,66,54);var partner=constructor.newInstance(26,66,53);var floor=constructor.newInstance(26,65,54);
            boolean a=(boolean)compare.invoke(null,partner,source,world),b=(boolean)compare.invoke(null,source,partner,world),wall=(boolean)compare.invoke(null,floor,source,world);
            var text="Source="+getState.invoke(world,source)+"\nPartner="+getState.invoke(world,partner)+"\nFloor="+getState.invoke(world,floor)+"\nConnected source halves: "+a+" / "+b+"; surrounding floor excluded: "+wall+"\n";
            if(!a||!b||wall)text+="FAIL: live pair integration not verified.\n";else text+="PASS: actual loaded double-chest states match both ways; neighboring floor remains occluding.\n";
            Files.writeString(Path.of(file),text);
        }catch(Throwable e){try{Files.writeString(Path.of(file),"FAIL "+e);}catch(Exception ignored){}}finally{done.countDown();}});done.await(10,TimeUnit.SECONDS);
    }
    public static void main(String[] args)throws Exception{var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]);}finally{vm.detach();}}
}
