package mcperf.compat.mixin;

import com.evandev.fieldguide.client.progress.ProgressManager;
import com.evandev.fieldguide.client.gui.screens.BookScreen;
import com.evandev.fieldguide.client.gui.screens.FieldGuideJournalScreen;
import mcperf.compat.LocalJournal;
import mcperf.compat.LocalGuide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ProgressManager.class, remap=false)
public abstract class FieldGuideProgressMixin {
    @Inject(method="sendJournalUpdate", at=@At("HEAD"), cancellable=true)
    private void saveLocalJournal(CallbackInfo ci) {
        if (!LocalJournal.serverSupportsJournal()) { LocalJournal.save(); ci.cancel(); }
    }
    @Inject(method={"onWorldLoad", "onWorldUnload"}, at=@At("HEAD"))
    private void resetLocalJournal(CallbackInfo ci) {
        LocalJournal.reset();
        LocalGuide.reset();
        BookScreen.lastOpenedScreen=null;
        FieldGuideJournalScreen.lastOpenedJournalPage=0;
    }
}
