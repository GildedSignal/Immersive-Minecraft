package mcperf.compat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import java.util.concurrent.atomic.AtomicLong;

public final class CompatFixes implements ClientModInitializer {
    public static final AtomicLong SOUND_GENERATION = new AtomicLong();
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> SOUND_GENERATION.incrementAndGet());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SOUND_GENERATION.incrementAndGet());
    }
}
