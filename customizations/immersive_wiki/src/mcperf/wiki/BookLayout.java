package mcperf.wiki;

import java.util.*;
import static mcperf.wiki.BookArticle.*;

/** Layout only when article, size or font changes; no wrapping in render(). */
public final class BookLayout {
    @FunctionalInterface public interface Width { int measure(Span span); }
    public record Run(Span span, int x, int width) {}
    public record Line(List<Run> runs, boolean heading) {}
    public record Chapter(String title, String anchor, int page) {}
    public record Result(List<List<Line>> pages, List<Chapter> chapters, Map<String, Integer> anchors) {}

    public static Result paginate(BookArticle article, int pageWidth, int pageLines, Width width) {
        if (pageWidth < 12 || pageLines < 2) throw new IllegalArgumentException("Page too small");
        List<List<Line>> pages = new ArrayList<>();
        List<Line> page = new ArrayList<>(); pages.add(page);
        List<Chapter> chapters = new ArrayList<>(); Map<String, Integer> anchors = new LinkedHashMap<>();
        for (Block block : article.blocks()) {
            boolean heading = block.kind().equals("heading");
            List<Line> lines = wrap(block, pageWidth, width, heading || block.kind().equals("caption"));
            // Keep chapter titles with at least the first two lines of their text.
            if (heading && !page.isEmpty() && page.size() + Math.min(pageLines, lines.size() + 2) > pageLines) {
                page = new ArrayList<>(); pages.add(page);
            }
            if (heading) {
                chapters.add(new Chapter(block.text().strip(), block.anchor(), pages.size() - 1));
                if (!block.anchor().isEmpty()) anchors.putIfAbsent(block.anchor(), pages.size() - 1);
            }
            for (Line line : lines) {
                if (page.size() >= pageLines) { page = new ArrayList<>(); pages.add(page); }
                page.add(line);
            }
            if (page.size() < pageLines) page.add(new Line(List.of(), false));
        }
        return new Result(pages.stream().map(List::copyOf).toList(), List.copyOf(chapters), Map.copyOf(anchors));
    }
    private static List<Line> wrap(Block block, int limit, Width width, boolean heading) {
        List<Line> result = new ArrayList<>(); List<Run> runs = new ArrayList<>(); int x = 0;
        for (Span source : block.spans()) {
            // Unicode codepoints are retained even when one word exceeds the page.
            for (String token : source.text().split("(?<=\\s)|(?=\\s)", -1)) {
                if (token.isEmpty()) continue;
                if (token.equals("\n")) { result.add(new Line(List.copyOf(runs), heading)); runs.clear(); x = 0; continue; }
                Span span = new Span(token, source.bold() || heading, source.italic(), source.target());
                int w = width.measure(span);
                if (x + w > limit && !runs.isEmpty()) { result.add(new Line(List.copyOf(runs), heading)); runs.clear(); x = 0; }
                if (x == 0 && token.isBlank()) continue;
                if (w <= limit) { runs.add(new Run(span, x, w)); x += w; continue; }
                StringBuilder part = new StringBuilder();
                for (int cp : token.codePoints().toArray()) {
                    String next = part.toString() + new String(Character.toChars(cp));
                    Span candidate = new Span(next, span.bold(), span.italic(), span.target());
                    int candidateWidth = width.measure(candidate);
                    if (x + candidateWidth > limit && !part.isEmpty()) {
                        Span fitted = new Span(part.toString(), span.bold(), span.italic(), span.target());
                        int fittedWidth = width.measure(fitted); runs.add(new Run(fitted, x, fittedWidth));
                        result.add(new Line(List.copyOf(runs), heading)); runs.clear(); x = 0; part.setLength(0);
                    }
                    part.appendCodePoint(cp);
                }
                if (!part.isEmpty()) { Span last = new Span(part.toString(), span.bold(), span.italic(), span.target()); int lastWidth = width.measure(last); runs.add(new Run(last, x, lastWidth)); x += lastWidth; }
            }
        }
        if (!runs.isEmpty()) result.add(new Line(List.copyOf(runs), heading));
        return result;
    }
}
