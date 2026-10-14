package mcperf.compat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import java.util.concurrent.atomic.AtomicLong;

public final class CompatFixes implements ClientModInitializer {
    public static final AtomicLong SOUND_GENERATION = new AtomicLong();
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> SOUND_GENERATION.incrementAndGet());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SOUND_GENERATION.incrementAndGet());
        if(FabricLoader.getInstance().isModLoaded("sound_physics_perfected")) {
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
                try {
                    Class<?> helper=Class.forName("com.redsmods.sound_physics_perfected.RaycastingHelper",false,client.getClass().getClassLoader());
                    helper.getMethod("shutdown").invoke(null);
                    org.slf4j.LoggerFactory.getLogger("immersive-compat-fixes").info("Sound Physics executors shut down during client exit");
                } catch(Exception e) {
                    org.slf4j.LoggerFactory.getLogger("immersive-compat-fixes").error("Cannot stop Sound Physics executors",e);
                }
            });
        }
    }
}
