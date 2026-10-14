package mcperf.compat.mixin;
import com.evandev.fieldguide.platform.NetworkHelper;
import mcperf.compat.LocalGuide;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=NetworkHelper.class,remap=false)
public abstract class FieldGuideNetworkMixin {
    @Inject(method="sendToServer",at=@At("HEAD"),cancellable=true)
    private void keepPersonalDiscoveriesLocal(CustomPacketPayload payload,CallbackInfo ci) {
        if(LocalGuide.consume(payload))ci.cancel();
    }
}
