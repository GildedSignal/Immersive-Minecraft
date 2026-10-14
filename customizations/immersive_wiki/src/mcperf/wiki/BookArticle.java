package mcperf.wiki;

import java.util.List;

/** Render-independent article model; remote HTML never enters the GUI. */
public record BookArticle(String title, long revision, long fetchedAt, String source,
                          String license, List<Block> blocks) {
    public record Span(String text, boolean bold, boolean italic, String target) {
        public static Span text(String text) { return new Span(text, false, false, ""); }
    }
    public record Block(String kind, String anchor, List<Span> spans) {
        public String text() { return spans.stream().map(Span::text).reduce("", String::concat); }
        public static Block paragraph(String text) { return new Block("paragraph", "", List.of(Span.text(text))); }
        public static Block link(String text, String target) {
            return new Block("paragraph", "", List.of(new Span(text, false, false, target)));
        }
    }
}
