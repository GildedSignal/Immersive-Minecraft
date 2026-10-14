package mcperf.camera.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.zerw.autothirdperson.client.AutothirdpersonClient", remap=false)
public abstract class OldCameraMixin {
    @Inject(method="onClientTick", at=@At("HEAD"), cancellable=true, require=1, remap=false)
    private void replaceAirborneHeuristic(CallbackInfo ci) { ci.cancel(); }
}
