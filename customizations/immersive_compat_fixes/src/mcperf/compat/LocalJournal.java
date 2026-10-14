package mcperf.compat;

import com.evandev.fieldguide.client.progress.ProgressManager;
import com.evandev.fieldguide.client.data.JournalPage;
import com.evandev.fieldguide.network.UpdateJournalPacket;
import mcperf.compat.mixin.FieldGuideProgressAccess;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

public final class LocalJournal {
    private static Path loaded;
    private static boolean writable;
    public static boolean serverSupportsJournal() {
        return Minecraft.getInstance().getConnection()!=null && ClientPlayNetworking.canSend(UpdateJournalPacket.TYPE);
    }
    public static void reset() { loaded=null; writable=false; }
    private static Path file() {
        Minecraft mc=Minecraft.getInstance();
        var server=mc.getCurrentServer();
        String scope=mc.getUser().getProfileId()+"\n"+(server==null ? "local-test" : server.ip.trim().toLowerCase(java.util.Locale.ROOT));
        return JournalStore.file(mc.gameDirectory.toPath(),scope);
    }
    public static void load() {
        Path file=file();
        if (file.equals(loaded)) return;
        loaded=file; writable=false;
        try {
            var value=JournalStore.load(file);
            var access=(FieldGuideProgressAccess)ProgressManager.getInstance();
            if(value!=null) {
                access.localTitle(value.title()); access.localPages().clear();
                for(var page:value.pages()) access.localPages().add(new JournalPage(page.title(),page.content(),page.timestamp()));
            }
            writable=true;
        } catch(Exception e) {
            LoggerFactory.getLogger("immersive-compat-fixes").error("Cannot read local Field Guide journal; original file retained",e);
        }
    }
    public static void save() {
        if(loaded==null || !writable) return;
        var access=(FieldGuideProgressAccess)ProgressManager.getInstance();
        var pages=access.localPages().stream().map(p -> new JournalStore.Page(p.title,p.content,p.timestamp)).toList();
        try { JournalStore.save(loaded,new JournalStore.Journal(1,access.localTitle()==null ? "" : access.localTitle(),pages)); }
        catch(Exception e) { LoggerFactory.getLogger("immersive-compat-fixes").error("Cannot save local Field Guide journal",e); }
    }
}
