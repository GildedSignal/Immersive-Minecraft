package mcperf.camera;

import io.github.leawind.perspectiveapi.api.*;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ImmersiveCamera implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("immersive-flight-camera");
    private static final FlightPolicy POLICY = new FlightPolicy();
    private static boolean ready, failed;
    private static Object player, level;
    private static long pausedAt = -1, pausedNanos;
    @Override public void onInitializeClient() {
        PerspectiveAPI.runWhenReady("immersive-flight-camera", () -> ready = true);
    }
    public static void tick(Minecraft mc) {
        if (!ready || failed || !PerspectiveAPI.isEnabled()) return;
        try {
            var selected = PerspectiveAPI.getSwitcherManager().getSelectedSwitcher();
            if (!(selected instanceof PerspectiveSwitcherBehavior behavior)) return;
            String current = behavior.getSelectedPerspectiveId();
            if (current == null) return;
            LocalPlayer p = mc.player;
            if (player != p || level != mc.level) {
                switchTo(behavior, POLICY.reset(current));
                player = p; level = mc.level;
                pausedAt = -1; pausedNanos = 0;
                current = behavior.getSelectedPerspectiveId();
            }
            if (p == null || mc.level == null) return;
            long now = System.nanoTime();
            if (mc.isPaused()) { if (pausedAt < 0) pausedAt = now; return; }
            if (pausedAt >= 0) { pausedNanos += now - pausedAt; pausedAt = -1; }
            boolean glide = p.isFallFlying() && !p.onClimbable()
                    && !p.hasEffect(MobEffects.LEVITATION) && !p.isSpectator();
            String third = PerspectiveAPI.getRegistry().contains(FlightPolicy.THIRD)
                    ? FlightPolicy.THIRD : "perspective_api.third_person_back";
            String target = POLICY.tick(now - pausedNanos, glide, p.getVehicle() != null,
                    mc.options.keyJump.isDown(), mc.isWindowActive() && mc.gui.screen() == null,
                    current, third);
            switchTo(behavior, target);
        } catch (RuntimeException | LinkageError error) {
            failed = true;
            LOG.error("Camera compatibility failed; automatic switching disabled for this launch", error);
        }
    }
    private static void switchTo(PerspectiveSwitcherBehavior behavior, String id) {
        if (id == null) return;
        Perspective target = PerspectiveAPI.getRegistry().get(id);
        if (target == null || !target.isAvailable()) return;
        // This updates the switcher's active selection. The API then applies its
        // normal camera transition and updates vanilla CameraType itself.
        behavior.onActivated(target);
        if (!id.equals(behavior.getSelectedPerspectiveId()))
            throw new IllegalStateException("Selected perspective rejected " + id);
    }
}
