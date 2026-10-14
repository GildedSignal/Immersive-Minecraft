package mcperf.camera.mixin;
import mcperf.camera.ImmersiveCamera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class CameraTickMixin {
    @Inject(method="tick", at=@At("TAIL"), require=1)
    private void immersiveCamera(CallbackInfo ci) { ImmersiveCamera.tick((Minecraft)(Object)this); }
}
