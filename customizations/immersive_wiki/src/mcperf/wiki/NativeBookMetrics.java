package mcperf.wiki;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.util.HashMap;
import java.util.Map;

/** Snapshot glyph advances once instead of traversing native font components for every word. */
final class NativeBookMetrics implements BookLayout.Width {
    private final Map<Integer, Float> advances = new HashMap<>();
    NativeBookMetrics(Font font, BookArticle article) {
        for (var block : article.blocks()) for (var span : block.spans()) {
            boolean italic = span.italic();
            span.text().codePoints().forEach(cp -> {
                if (cp == '\n' || cp == '\r') return;
                for (int weight = 0; weight < 2; weight++) {
                    boolean bold = weight == 1;
                    int key = key(cp, bold, italic);
                    if (!advances.containsKey(key)) {
                        Component glyph = Component.literal(new String(Character.toChars(cp)))
                                .setStyle(Style.EMPTY.withBold(bold).withItalic(italic));
                        advances.put(key, font.getSplitter().stringWidth(glyph));
                    }
                }
            });
        }
    }
    private static int key(int cp, boolean bold, boolean italic) { return (cp << 2) | (bold ? 2 : 0) | (italic ? 1 : 0); }
    @Override public int measure(BookArticle.Span span) {
        float total = 0;
        for (int i = 0; i < span.text().length();) {
            int cp = span.text().codePointAt(i); i += Character.charCount(cp);
            total += advances.getOrDefault(key(cp, span.bold(), span.italic()), 0f);
        }
        return (int)Math.ceil(total);
    }
}
