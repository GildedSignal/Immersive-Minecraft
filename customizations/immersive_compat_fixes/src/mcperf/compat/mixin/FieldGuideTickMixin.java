package mcperf.compat.mixin;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import mcperf.compat.LocalGuide;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ClientFieldGuideManager.class,remap=false)
public abstract class FieldGuideTickMixin {
    @Inject(method="onClientTick",at=@At("HEAD"))
    private void loadLocalDiscovery(Minecraft mc,CallbackInfo ci) {
        if(mc.level!=null&&mc.player!=null&&!LocalGuide.serverSupportsGuide())LocalGuide.ensure();
    }
}
