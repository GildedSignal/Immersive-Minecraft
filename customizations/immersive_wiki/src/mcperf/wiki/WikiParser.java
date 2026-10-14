package mcperf.wiki;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static mcperf.wiki.BookArticle.*;

/** Converts article semantics, not the site's layout, into book blocks. */
public final class WikiParser {
    public static final String LICENSE = "CC BY-NC-SA 3.0";
    private final List<Block> blocks = new ArrayList<>();
    private String article;
    private URI base;

    public BookArticle parse(String title, long revision, String html, String source) {
        if (html.length() > 8_000_000) throw new IllegalArgumentException("Article too large");
        blocks.clear(); article = title; base = URI.create(source);
        Document doc = Jsoup.parse(html, source);
        doc.select("script,style,noscript,iframe,form,nav,.navbox,.navigation-not-searchable,.mw-editsection," +
                ".toc,#toc,.mw-references-wrap,.reflist,.reference,.noprint,.nomobile,.hatnote," +
                ".infobox-imagearea,.infobox-invimages,.infobox-footer,.mcwiki-header").remove();
        Element root = doc.selectFirst(".mw-parser-output");
        if (root == null) root = doc.body();
        // The encyclopedia introduction belongs before its lengthy fact panel.
        List<Element> infoboxes = new ArrayList<>(root.select(".infobox"));
        infoboxes.forEach(Element::remove);
        walk(root);
        if (!infoboxes.isEmpty()) {
            int insert = 0;
            while (insert < blocks.size() && !blocks.get(insert).kind().equals("heading")) insert++;
            List<Block> body = new ArrayList<>(blocks);
            blocks.clear();
            blocks.add(new Block("heading", "fieldbook_facts", List.of(Span.text("Quick facts"))));
            infoboxes.forEach(this::walk);
            List<Block> facts = new ArrayList<>(blocks);
            blocks.clear(); blocks.addAll(body.subList(0, insert)); blocks.addAll(facts); blocks.addAll(body.subList(insert, body.size()));
        }
        if (blocks.isEmpty()) throw new IllegalArgumentException("This article has no readable text");
        blocks.add(new Block("heading", "fieldbook_source", List.of(Span.text("Source & credits"))));
        blocks.add(Block.paragraph("Minecraft Wiki contributors. Revision " + revision + ". Adapted into book pages; headings, lists and tables reformatted. Illustrations use in-game item models where available."));
        blocks.add(Block.paragraph(source + "?oldid=" + revision));
        blocks.add(Block.paragraph("Text: " + LICENSE + ". Attribution and article history remain at the source. https://creativecommons.org/licenses/by-nc-sa/3.0/"));
        return new BookArticle(title, revision, System.currentTimeMillis(), source, LICENSE, List.copyOf(blocks));
    }

    private void walk(Element e) {
        if (blocks.size() >= 6000) throw new IllegalArgumentException("Article has too many entries");
        String tag = e.normalName();
        if (tag.matches("h[1-6]")) {
            String anchor = e.id();
            if (anchor.isEmpty()) { Element id = e.selectFirst("[id]"); if (id != null) anchor = id.id(); }
            add("heading", anchor, inline(e)); return;
        }
        if (tag.equals("table")) { table(e); return; }
        if (tag.equals("ul") || tag.equals("ol")) {
            int i = 0;
            for (Element child : e.children()) if (child.normalName().equals("li")) {
                Element copy = child.clone(); copy.select("ul,ol").remove();
                List<Span> s = new ArrayList<>(); s.add(Span.text(tag.equals("ol") ? (++i) + ". " : "• "));
                s.addAll(inline(copy)); add("list", "", s);
                for (Element sub : child.children()) if (sub.normalName().matches("ul|ol")) walk(sub);
            }
            return;
        }
        if (tag.matches("p|dd|dt|pre|blockquote|figcaption")) {
            add(tag.equals("dt") ? "caption" : "paragraph", "", inline(e)); return;
        }
        if (tag.equals("figure") || e.hasClass("gallery")) {
            for (Element caption : e.select("figcaption,.gallerytext,.thumbcaption")) walk(caption);
            return;
        }
        if (e.children().isEmpty()) { add("paragraph", "", inline(e)); return; }
        // Preserve unwrapped prose; recurse through layout wrappers and tab panels.
        List<Span> pending = new ArrayList<>();
        for (Node node : e.childNodes()) {
            if (node instanceof TextNode t) pending.add(Span.text(t.text()));
            else if (node instanceof Element child) {
                add("paragraph", "", pending); pending = new ArrayList<>(); walk(child);
            }
        }
        add("paragraph", "", pending);
    }

    private record Cell(List<Span> spans, boolean header) {}
    private record Carry(Cell cell, int remaining) {}
    private void table(Element table) {
        Element caption = table.children().stream().filter(e -> e.normalName().equals("caption")).findFirst().orElse(null);
        if (caption != null) add("caption", "", inline(caption));
        List<String> headers = new ArrayList<>();
        Map<Integer, Carry> carried = new TreeMap<>();
        for (Element row : table.select("tr")) {
            if (row.closest("table") != table) continue;
            Map<Integer, Cell> slots = new TreeMap<>();
            Map<Integer, Carry> next = new TreeMap<>();
            for (var entry : carried.entrySet()) {
                slots.put(entry.getKey(), entry.getValue().cell());
                if (entry.getValue().remaining() > 1)
                    next.put(entry.getKey(), new Carry(entry.getValue().cell(), entry.getValue().remaining() - 1));
            }
            int col = 0;
            for (Element cell : row.children()) {
                if (!cell.normalName().matches("td|th")) continue;
                while (slots.containsKey(col)) col++;
                Cell value = new Cell(inline(cell), cell.normalName().equals("th"));
                int cs = span(cell.attr("colspan")), rs = span(cell.attr("rowspan"));
                for (int c = 0; c < cs && col < 40; c++, col++) {
                    slots.put(col, value);
                    if (rs > 1) next.put(col, new Carry(value, rs - 1));
                }
            }
            carried = next;
            if (slots.isEmpty()) continue;
            boolean headerOnly = slots.values().stream().allMatch(Cell::header);
            if (headerOnly) {
                headers.clear(); slots.values().forEach(c -> headers.add(plain(c.spans())));
                if (headers.stream().distinct().count() == 1) add("caption", "", slots.values().iterator().next().spans());
                continue;
            }
            List<Span> entry = new ArrayList<>(); Cell previous = null;
            for (var slot : slots.entrySet()) {
                Cell cell = slot.getValue();
                if (cell == previous || plain(cell.spans()).isBlank()) { previous = cell; continue; }
                if (!entry.isEmpty()) entry.add(Span.text("; "));
                String label = slot.getKey() < headers.size() ? headers.get(slot.getKey()) : "";
                if (!label.isBlank() && !label.equals(plain(cell.spans())))
                    entry.add(new Span(label + ": ", true, false, ""));
                entry.addAll(cell.spans()); previous = cell;
            }
            add("table", "", entry);
        }
    }
    private static int span(String s) { try { return Math.min(40, Math.max(1, Integer.parseInt(s))); } catch (NumberFormatException e) { return 1; } }

    private List<Span> inline(Element e) {
        List<Span> result = new ArrayList<>(); inlineNode(e, false, false, "", result); return tidy(result);
    }
    private void inlineNode(Node n, boolean bold, boolean italic, String link, List<Span> out) {
        if (n instanceof TextNode t) { out.add(new Span(t.text(), bold, italic, link)); return; }
        if (!(n instanceof Element e)) return;
        String tag = e.normalName();
        if (tag.equals("br")) { out.add(new Span("\n", bold, italic, link)); return; }
        if (tag.equals("img")) {
            // Inventory sprites often carry the only name in a recipe cell.
            String alt = e.attr("alt"), name = "";
            for (Element p = e.parent(); p != null && p != e.ownerDocument(); p = p.parent()) {
                if (p.hasAttr("title")) { name = p.attr("title"); break; }
                if (p.normalName().equals("td")) break;
            }
            if (name.isBlank() && alt.startsWith("Invicon ")) {
                int end = alt.indexOf(".png"); if (end > 8) name = alt.substring(8, end);
            }
            Element cell = e.closest("td,th");
            if (cell != null && !name.isBlank() && cell.text().contains(name)) return;
            if (!name.isBlank()) out.add(new Span(" " + name + " ", bold, italic, link));
            return;
        }
        if (tag.equals("a")) link = target(e.attr("href"));
        bold |= tag.equals("b") || tag.equals("strong") || tag.equals("th");
        italic |= tag.equals("i") || tag.equals("em");
        boolean boundary = tag.matches("p|div|tr|li|table");
        if (boundary && !out.isEmpty()) out.add(Span.text(" "));
        for (Node child : e.childNodes()) inlineNode(child, bold, italic, link, out);
        if (boundary) out.add(Span.text(" "));
    }
    private String target(String href) {
        try {
            URI uri = base.resolve(href);
            if (!Objects.equals(uri.getHost(), base.getHost()) || !uri.getPath().startsWith("/w/")) return "";
            String name = URLDecoder.decode(uri.getRawPath().substring(3).replace("+", "%2B"), StandardCharsets.UTF_8).replace('_', ' ');
            if (name.contains(":") || name.isBlank()) return "";
            String fragment = uri.getRawFragment();
            return name + (fragment == null ? "" : "#" + URLDecoder.decode(fragment.replace("+", "%2B"), StandardCharsets.UTF_8));
        } catch (RuntimeException e) { return ""; }
    }
    private void add(String kind, String anchor, List<Span> spans) {
        spans = tidy(spans);
        if (!plain(spans).isBlank()) blocks.add(new Block(kind, anchor, spans));
    }
    private static List<Span> tidy(List<Span> spans) {
        List<Span> clean = new ArrayList<>();
        boolean space = true;
        for (Span s : spans) {
            String normalized = s.text().replace('\u00a0', ' ').replaceAll("[\\t\\r ]+", " ").replaceAll("\\n[ ]+", "\n");
            if (space) normalized = normalized.replaceFirst("^ +", "");
            if (normalized.isEmpty()) continue;
            clean.add(new Span(normalized, s.bold(), s.italic(), s.target()));
            space = normalized.endsWith(" ") || normalized.endsWith("\n");
        }
        if (!clean.isEmpty()) {
            int i = clean.size() - 1; Span s = clean.get(i);
            clean.set(i, new Span(s.text().stripTrailing(), s.bold(), s.italic(), s.target()));
        }
        return List.copyOf(clean);
    }
    public static String plain(List<Span> spans) { return spans.stream().map(Span::text).reduce("", String::concat); }
}
