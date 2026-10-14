package mcperf.wiki;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/** Small leather-backed tabs rather than browser buttons. */
final class BookButton extends AbstractButton {
    private final Runnable action;
    BookButton(int x, int y, int w, String label, Runnable action) {
        super(x, y, w, 18, Component.literal(label)); this.action = action;
    }
    @Override public void onPress(InputWithModifiers input) { if (active) action.run(); }
    @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        boolean hover = isHoveredOrFocused() && active;
        int x = getX(), y = getY();
        g.fill(x, y, x + width, y + height, 0xff30221d);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, hover ? 0xff9d7948 : 0xff65513b);
        g.fill(x + 1, y + 1, x + width - 1, y + 2, hover ? 0xffd3b274 : 0xff9c8056);
        String label = Minecraft.getInstance().font.plainSubstrByWidth(getMessage().getString(), Math.max(0, width - 6));
        int tx = x + (width - Minecraft.getInstance().font.width(label)) / 2;
        g.text(Minecraft.getInstance().font, label, tx, y + 5, active ? 0xfff4e3b3 : 0xffa89d85, false);
    }
    @Override public void playDownSound(SoundManager manager) { /* Navigation owns a single paper sound. */ }
    @Override protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
}
