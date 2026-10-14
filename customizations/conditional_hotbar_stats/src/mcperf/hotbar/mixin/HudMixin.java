package mcperf.hotbar.mixin;

import mcperf.hotbar.HotbarStats;
import mod.crend.autohud.hud.Hud;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Hud.class, remap = false)
public abstract class HudMixin {
    @Inject(method = "tickState", at = @At("TAIL"), remap = false)
    private static void conditionalHotbarStats(LocalPlayer player, CallbackInfo callback) {
        HotbarStats.synchronize(player);
    }
}
