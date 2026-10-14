package mcperf.compat.mixin;

import com.evandev.fieldguide.client.progress.ProgressManager;
import com.evandev.fieldguide.client.data.JournalPage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(value=ProgressManager.class, remap=false)
public interface FieldGuideProgressAccess {
    @Accessor("journalTitle") String localTitle();
    @Accessor("journalTitle") void localTitle(String title);
    @Accessor("journalPages") List<JournalPage> localPages();
    @Accessor("unlockedEntries") java.util.Set<String> localUnlocked();
    @Accessor("seenEntries") java.util.Set<String> localSeen();
    @Accessor("discoveryTimes") java.util.Map<String,Long> localTimes();
    @Accessor("discoveryGameTimes") java.util.Map<String,Long> localGameTimes();
    @Accessor("customNames") java.util.Map<String,String> localNames();
    @Accessor("customDescriptions") java.util.Map<String,String> localDescriptions();
    @Accessor("selectedVariants") java.util.Map<String,String> localVariants();
}
