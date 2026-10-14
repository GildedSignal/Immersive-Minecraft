package mcperf.compat.mixin;

import mcperf.compat.CompatFixes;
import mcperf.compat.SoundDispatch;
import net.minecraft.client.Minecraft;
import omar.projects.interactivestuff.scripts.ScriptInterpreter;
import omar.projects.interactivestuff.scripts.variables.Sound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ScriptInterpreter.class, remap=false)
public abstract class InteractiveSoundMixin {
    @Inject(method="onPlaySound", at=@At("HEAD"), cancellable=true)
    private static void runOnMinecraftThread(Sound sound, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            SoundDispatch.enqueue(mc::execute, CompatFixes.SOUND_GENERATION::get,
                () -> mc.level != null && mc.player != null, () -> ScriptInterpreter.onPlaySound(sound));
            ci.cancel();
        } else if (mc.level == null || mc.player == null) ci.cancel();
    }
    @Inject(method={"loadScripts", "clearScripts"}, at=@At("HEAD"))
    private static void invalidateQueuedSounds(CallbackInfo ci) {
        CompatFixes.SOUND_GENERATION.incrementAndGet();
    }
}
