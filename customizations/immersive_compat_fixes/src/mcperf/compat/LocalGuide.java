package mcperf.compat;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.manager.ClientCategoryManager;
import com.evandev.fieldguide.client.progress.ProgressManager;
import com.evandev.fieldguide.network.*;
import com.evandev.fieldguide.server.FieldGuideDatapackLoader;
import mcperf.compat.mixin.FieldGuideProgressAccess;
import mcperf.compat.mixin.FieldGuideScanAccess;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.*;

public final class LocalGuide {
    private static Path loaded;
    private static boolean writable;
    private static boolean loading;
    public static boolean serverSupportsGuide() {
        return Minecraft.getInstance().getConnection()!=null && ClientPlayNetworking.canSend(ScanUnlockPacket.TYPE);
    }
    public static void reset() {loaded=null;writable=false;loading=false;}
    private static Path file() {
        var mc=Minecraft.getInstance();var server=mc.getCurrentServer();
        String scope=mc.getUser().getProfileId()+"\n"+(server==null?"local-test":server.ip.trim().toLowerCase(Locale.ROOT));
        return mc.gameDirectory.toPath().resolve("fieldguide_local_progress").resolve(JournalStore.file(mc.gameDirectory.toPath(),scope).getFileName());
    }
    public static boolean ensure() {
        if(serverSupportsGuide())return false;
        if(Minecraft.getInstance().level==null || Minecraft.getInstance().player==null)return false;
        Path path=file();if(path.equals(loaded))return writable;
        if(loading)return false;loading=true;loaded=path;writable=false;
        try {
            var mc=Minecraft.getInstance();
            // Borrow the client's packs for their data sections. Do not close this
            // view: ownership and closing remain with the client's resource manager.
            var dataResources=new MultiPackResourceManager(PackType.SERVER_DATA,mc.getResourceManager().listPacks().toList());
            var data=FieldGuideDatapackLoader.load(dataResources);
            var manager=ClientFieldGuideManager.getInstance();
            manager.updateCategoriesFromServer(new ArrayList<>(data.categories.values()),new ArrayList<>(data.allEntries.values()),data.redirects,true,true);
            manager.updateModifiers(data.biomeAdditions,data.biomeRemovals,data.lootAdditions,data.lootRemovals,true);
            manager.updateVariants(data.variants);
            ClientCategoryManager.getInstance().resolveAllEntries();
            var value=GuideStore.load(path);
            if(value!=null)ProgressManager.getInstance().applyServerUpdate(new ProgressUpdatePacket.Builder().silent(true)
                .unlocked(value.unlocked()).seen(value.seen()).discoveryTimes(value.times()).discoveryGameTimes(value.gameTimes())
                .customNames(value.names()).customDescriptions(value.descriptions()).selectedVariants(value.variants()).build());
            writable=!ClientFieldGuideManager.getCategories().isEmpty();
            LoggerFactory.getLogger("immersive-compat-fixes").info("Local discovery categories={} entries={}",ClientFieldGuideManager.getCategories().size(),ClientFieldGuideManager.getValidEntries().size());
        } catch(Exception e){LoggerFactory.getLogger("immersive-compat-fixes").error("Cannot load local Field Guide discoveries; original file retained",e);}
        finally{loading=false;}
        return writable;
    }
    public static void save() {
        if(!writable||loaded==null||serverSupportsGuide())return;
        var a=(FieldGuideProgressAccess)ProgressManager.getInstance();
        var value=new GuideStore.Progress(1,new ArrayList<>(a.localUnlocked()),new ArrayList<>(a.localSeen()),new HashMap<>(a.localTimes()),
            new HashMap<>(a.localGameTimes()),new HashMap<>(a.localNames()),new HashMap<>(a.localDescriptions()),new HashMap<>(a.localVariants()));
        try{GuideStore.save(loaded,value);}catch(Exception e){LoggerFactory.getLogger("immersive-compat-fixes").error("Cannot save local Field Guide discoveries",e);}
    }
    public static boolean consume(CustomPacketPayload payload) {
        if(serverSupportsGuide()||!payload.type().id().getNamespace().equals("fieldguide"))return false;
        if(payload instanceof UpdateJournalPacket) {LocalJournal.save();return true;}
        if(payload instanceof ScanUnlockPacket scan && ensure()) {
            var access=(FieldGuideScanAccess)scan;String id=access.localEntry().toString();
            String variant=access.localVariant();var ids=new ArrayList<String>();ids.add(id);
            if(variant!=null&&!variant.isEmpty())ids.add(id+"#"+variant);
            long now=System.currentTimeMillis();var times=new HashMap<String,Long>();var gameTimes=new HashMap<String,Long>();
            for(var key:ids){times.put(key,now);gameTimes.put(key,Minecraft.getInstance().level==null?0L:Minecraft.getInstance().level.getGameTime());}
            ProgressManager.getInstance().applyServerUpdate(new ProgressUpdatePacket.Builder().unlocked(ids).discoveryTimes(times).discoveryGameTimes(gameTimes).build());
        }
        save();return true;
    }
}
