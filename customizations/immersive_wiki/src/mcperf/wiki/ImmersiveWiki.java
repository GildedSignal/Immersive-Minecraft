package mcperf.wiki;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.*;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.*;
import java.nio.file.Path;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public final class ImmersiveWiki implements ClientModInitializer {
    public static BookSettings settings;
    public static WikiLibrary library;
    public static Path settingsFile;
    public static KeyMapping openKey;
    @Override public void onInitializeClient() {
        settingsFile = FabricLoader.getInstance().getConfigDir().resolve("immersive-wiki.json");
        settings = BookSettings.load(settingsFile);
        library = new WikiLibrary(FabricLoader.getInstance().getGameDir().resolve("cache/immersive-wiki"), settings.language);
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("immersive_wiki", "fieldbook"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.immersive_wiki.open", InputConstants.Type.KEYSYM,
                InputConstants.KEY_F8, category));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) {
                if (mc.player != null && mc.gui.screen() == null) open("");
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                literal("fieldbook").executes(ctx -> { open(""); return 1; })
                .then(argument("article", StringArgumentType.greedyString()).executes(ctx -> {
                    open(StringArgumentType.getString(ctx, "article")); return 1;
                }))));
    }
    public static void open(String title) {
        Minecraft mc = Minecraft.getInstance();
        BookScreen screen = new BookScreen(mc.gui.screen());
        mc.gui.setScreen(screen);
        sound(settings.openSound, SoundEvents.BOOK_PUT, .92f);
        if (!title.isBlank()) screen.read(title, true);
    }
    public static void sound(String name, SoundEvent fallback, float pitch) {
        try {
            Identifier id = Identifier.tryParse(name);
            SoundEvent event = id == null ? fallback : SoundEvent.createVariableRangeEvent(id);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, settings.soundVolume));
        } catch (RuntimeException ignored) { }
    }
    public static String contextTitle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return "";
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK)
            return mc.level.getBlockState(hit.getBlockPos()).getBlock().getName().getString();
        if (mc.hitResult instanceof EntityHitResult hit) return hit.getEntity().getType().getDescription().getString();
        if (!mc.player.getMainHandItem().isEmpty()) return mc.player.getMainHandItem().getItem().getName(mc.player.getMainHandItem()).getString();
        if (!mc.player.getOffhandItem().isEmpty()) return mc.player.getOffhandItem().getItem().getName(mc.player.getOffhandItem()).getString();
        return "";
    }
}
