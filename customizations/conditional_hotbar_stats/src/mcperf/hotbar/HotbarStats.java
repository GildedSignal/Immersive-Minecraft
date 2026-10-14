package mcperf.hotbar;

import mod.crend.autohud.hud.Hud;
import mod.crend.autohud.hud.element.HudElement;
import mod.crend.autohud.hud.element.HudElements;
import net.minecraft.client.player.LocalPlayer;

/** Adds conditional hotbar reveal without replacing AutoHUD's independent triggers. */
public final class HotbarStats {
    private HotbarStats() {}
    public static void synchronize(LocalPlayer player) {
        if (player == null || !Hud.actDynamic() || Hud.inConfigScreen || !HudElements.HOTBAR.hasState()) return;
        HudElement<?> health = HudElements.HEALTH_BAR;
        HudElement<?> food = HudElements.FOOD_BAR;
        if (health.isActive() && player.getHealth() < player.getMaxHealth()) health.synchronizeFrom(HudElements.HOTBAR);
        if (food.isActive() && player.getFoodData().getFoodLevel() < 20) food.synchronizeFrom(HudElements.HOTBAR);
    }
}
