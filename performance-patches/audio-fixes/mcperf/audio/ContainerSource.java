package mcperf.audio;
import java.lang.invoke.*;
import java.lang.reflect.*;
/** A double container must not occlude its own sound at the seam between halves. */
public final class ContainerSource {
    record Access(Method state,Method block,Method value,Method connected,Method relative,Object type,Object facing,Class<?> chest){}
    static final ClassValue<MethodHandle> DISTANCE=new ClassValue<>(){protected MethodHandle computeValue(Class<?> position){try{
        var vec=Class.forName("net.minecraft.core.Vec3i",false,position.getClassLoader());
        return MethodHandles.publicLookup().findVirtual(vec,"distManhattan",MethodType.methodType(int.class,vec)).asType(MethodType.methodType(int.class,Object.class,Object.class));
    }catch(Exception e){throw new IllegalStateException(e);}}};
    static final ClassValue<Access> ACCESS=new ClassValue<>(){protected Access computeValue(Class<?> world){try{
        var loader=world.getClassLoader();var pos=Class.forName("net.minecraft.core.BlockPos",false,loader);var state=Class.forName("net.minecraft.world.level.block.state.BlockState",false,loader);var prop=Class.forName("net.minecraft.world.level.block.state.properties.Property",false,loader);var direction=Class.forName("net.minecraft.core.Direction",false,loader);var chest=Class.forName("net.minecraft.world.level.block.ChestBlock",false,loader);
        return new Access(world.getMethod("getBlockState",pos),state.getMethod("getBlock"),state.getMethod("getValue",prop),chest.getMethod("getConnectedDirection",state),pos.getMethod("relative",direction),chest.getField("TYPE").get(null),chest.getField("FACING").get(null),chest);
    }catch(Exception e){throw new IllegalStateException(e);}}};
    public static boolean pairPolicy(boolean sameBlock,String hitType,String sourceType,boolean sameFacing,boolean hitPointsToSource,boolean sourcePointsToHit){
        return sameBlock&&sameFacing&&hitPointsToSource&&sourcePointsToHit&&((hitType.equals("LEFT")&&sourceType.equals("RIGHT"))||(hitType.equals("RIGHT")&&sourceType.equals("LEFT")));
    }
    public static boolean isSource(Object hit,Object end,Object world){
        if(hit.equals(end))return true;
        try{
            if((int)DISTANCE.get(hit.getClass()).invokeExact(hit,end)!=1)return false;
            var a=ACCESS.get(world.getClass());var h=a.state.invoke(world,hit);var e=a.state.invoke(world,end);var hb=a.block.invoke(h);var eb=a.block.invoke(e);
            if(hb!=eb||!a.chest.isInstance(hb))return false;
            var ht=(Enum<?>)a.value.invoke(h,a.type);var et=(Enum<?>)a.value.invoke(e,a.type);
            if(ht.name().equals("SINGLE")||et.name().equals("SINGLE")||ht==et)return false;
            if(!a.value.invoke(h,a.facing).equals(a.value.invoke(e,a.facing)))return false;
            return pairPolicy(true,ht.name(),et.name(),true,a.relative.invoke(hit,a.connected.invoke(null,h)).equals(end),a.relative.invoke(end,a.connected.invoke(null,e)).equals(hit));
        }catch(Throwable unsupported){return false;} // Preserve ordinary occlusion when a mod changes the API.
    }
}
