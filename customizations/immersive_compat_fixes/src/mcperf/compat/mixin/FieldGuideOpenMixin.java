package mcperf.compat.mixin;

import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.gui.screens.FieldGuideJournalScreen;
import com.evandev.fieldguide.client.gui.screens.BookScreen;
import mcperf.compat.LocalJournal;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=FieldGuideClient.class, remap=false)
public abstract class FieldGuideOpenMixin {
    @Inject(method="openDefaultScreen", at=@At("HEAD"), cancellable=true)
    private static void openLocalJournal(CallbackInfo ci) {
        if (!LocalJournal.serverSupportsJournal()) {
            LocalJournal.load();
            Minecraft.getInstance().gui.setScreen(new FieldGuideJournalScreen(null,0));
            ci.cancel();
        }
    }
}
