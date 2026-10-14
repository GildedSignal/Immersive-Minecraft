package mcperf.camera;
import io.github.leawind.perspectiveapi.api.*;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.lwjgl.glfw.GLFW;
import org.joml.Quaternionf;
import org.joml.Vector3f;
public final class FlightMotion implements PerspectiveModifier {
    public static final FlightMotion INSTANCE=new FlightMotion();
    public static FlightMotionConfig config;
    public static KeyMapping left,right,up,down;
    private final FlightMotionMath motion=new FlightMotionMath();
    private final Quaternionf follow=new Quaternionf();
    private final Quaternionf inverse=new Quaternionf();
    private final Vector3f relative=new Vector3f();
    private Object player,level;
    private long inputAt,visualAt;
    private double previousYaw,filteredTurn;
    private boolean initialized;
    public static void registerKeys(){
        config=FlightMotionConfig.load();var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("immersive_flight_camera","flight"));
        left=key("turn_left",GLFW.GLFW_KEY_LEFT,category);right=key("turn_right",GLFW.GLFW_KEY_RIGHT,category);up=key("pitch_up",GLFW.GLFW_KEY_UP,category);down=key("pitch_down",GLFW.GLFW_KEY_DOWN,category);
    }
    public static void registerModifier(){PerspectiveAPI.getModifierChain().register("immersive-flight-motion",100,INSTANCE);}
    private static KeyMapping key(String name,int code,KeyMapping.Category category){return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.immersive_flight_camera."+name,code,category));}
    public static boolean gliding(Minecraft mc){return mc.player!=null&&mc.level!=null&&mc.player.isFallFlying()&&!mc.player.onClimbable()&&!mc.player.hasEffect(MobEffects.LEVITATION)&&!mc.player.isSpectator();}
    public static void tick(Minecraft mc){INSTANCE.steer(mc);}
    private void steer(Minecraft mc){
        if(config==null)return;
        long now=System.nanoTime();double dt=inputAt==0?0.05:Math.min(.10,(now-inputAt)/1e9);inputAt=now;
        if(player!=mc.player||level!=mc.level){player=mc.player;level=mc.level;motion.reset();initialized=false;visualAt=0;}
        if(!config.enabled||!config.arrowSteering||!gliding(mc)||mc.isPaused()||!mc.isWindowActive()||mc.gui.screen()!=null){motion.yawRate=motion.pitchRate=0;return;}
        motion.steering((right.isDown()?1:0)-(left.isDown()?1:0),(down.isDown()?1:0)-(up.isDown()?1:0),dt,config);
        if(Math.abs(motion.yawRate)+Math.abs(motion.pitchRate)>0.001)mc.player.turn(motion.yawRate*dt/.15,motion.pitchRate*dt/.15);
    }
    @Override public void apply(PerspectiveState.Mutable state,PerspectiveModifierContext context){
        var mc=Minecraft.getInstance();
        if(config==null||!config.enabled||mc.player==null||context.cameraEntity()!=mc.player||!PerspectiveAPI.isCurrent(FlightPolicy.THIRD)){initialized=false;visualAt=0;motion.bank=motion.distance=motion.fov=0;return;}
        boolean flying=gliding(mc);long now=System.nanoTime();double dt=visualAt==0?0:Math.min(.1,(now-visualAt)/1e9);visualAt=now;
        if(mc.isPaused())dt=0;
        double yaw=mc.player.getYRot();
        if(!initialized){follow.set(state.rotation());previousYaw=yaw;filteredTurn=0;initialized=true;}
        double turn=dt>0?FlightMotionMath.wrappedDelta(yaw-previousYaw)/dt:0;previousYaw=yaw;
        filteredTurn=FlightMotionMath.blend(filteredTurn,turn,dt,.10);
        motion.visual(flying,filteredTurn,mc.player.getDeltaMovement().length(),dt,config);
        if(!flying&&Math.abs(motion.bank)+motion.distance+motion.fov<.01){initialized=false;return;}
        follow.slerp(state.rotation(),(float)(1-Math.exp(-Math.log(2)*dt/config.followHalfLife)));
        Vec3 eye=mc.player.getEyePosition(context.partialTicks());var base=state.position();
        relative.set((float)(base.x()-eye.x),(float)(base.y()-eye.y),(float)(base.z()-eye.z)).rotate(inverse.set(state.rotation()).conjugate()).rotate(follow);
        state.rotation().set(follow).rotateZ((float)Math.toRadians(motion.bank));
        state.setFovDeg((float)Math.min(140,state.getFovDeg()+motion.fov));
        if(relative.length()>.01){
            Vec3 displacement=new Vec3(relative.x,relative.y,relative.z);Vec3 direction=displacement.normalize();double desired=displacement.length()+motion.distance,allowed=desired;
            Vec3 end=eye.add(direction.scale(desired));
            // Match the camera's small collision cube; no pullback through walls.
            for(int i=0;i<8;i++){Vec3 offset=new Vec3((i&1)==0?-.1:.1,(i&2)==0?-.1:.1,(i&4)==0?-.1:.1);var hit=mc.level.clip(new ClipContext(eye.add(offset),end.add(offset),ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,mc.player));if(hit.getType()==HitResult.Type.BLOCK)allowed=Math.min(allowed,Math.max(0,eye.add(offset).distanceTo(hit.getLocation())-.10));}
            Vec3 result=eye.add(direction.scale(allowed));state.position().set(result.x,result.y,result.z);
        }
    }
    public static float bodyBank(){return config!=null&&config.enabled&&config.playerBanking?(float)INSTANCE.motion.bank:0;}
}
