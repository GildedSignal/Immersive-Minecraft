package mcperf.camera.mixin;
import mcperf.camera.FlightMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AvatarRenderer.class)
public abstract class FlightBodyBankMixin {
    @Inject(method="setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",at=@At("RETURN"))
    private void bankLocalGlider(AvatarRenderState state,PoseStack pose,float bodyYaw,float scale,CallbackInfo ci){
        var mc=Minecraft.getInstance();if(FlightMotion.gliding(mc)&&state.id==mc.player.getId())pose.mulPose(Axis.YP.rotationDegrees(FlightMotion.bodyBank()));
    }
}
