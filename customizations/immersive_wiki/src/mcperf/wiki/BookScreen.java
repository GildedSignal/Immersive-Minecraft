package mcperf.wiki;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.concurrent.*;
import static mcperf.wiki.BookArticle.*;

public final class BookScreen extends Screen {
    private static final int INK = 0xff3e3024, LINK = 0xff416148;
    private static final ThreadPoolExecutor PAGINATOR = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), task -> { Thread t = new Thread(task, "Immersive Wiki page layout"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.DiscardOldestPolicy());
    private final Screen parent;
    private BookArticle article, readingArticle;
    private BookLayout.Result layout;
    private List<List<VisualLine>> visual = List.of();
    private ItemStack illustration = ItemStack.EMPTY;
    private String mode = "home", status = "", query = "";
    private int left, top, bookWidth, bookHeight, pageWidth, pageLines, page, readingPage;
    private long requestVersion, turnAt;
    private long layoutNanos;
    private long workerLayoutNanos, layoutVersion;
    private String pendingAnchor = "";
    private final Deque<Visit> history = new ArrayDeque<>();
    private final List<Hit> links = new ArrayList<>();
    private EditBox search;
    private BookButton previous, next, mark, contents, refresh;
    private record Visit(BookArticle article, int page) {}
    private record Hit(int x, int y, int width, String target) {}
    private record VisualRun(Component text, int x, int width, String target) {}
    private record VisualLine(List<VisualRun> runs, boolean heading) {}
    private record Prepared(BookLayout.Result layout, List<List<VisualLine>> visual, long nanos) {}

    public BookScreen(Screen parent) { super(Component.literal("The Fieldbook")); this.parent = parent; }
    @Override protected void init() {
        bookWidth = Math.min(440, width - 24); bookHeight = Math.min(294, height - 48);
        left = (width - bookWidth) / 2; top = (height - bookHeight) / 2;
        pageWidth = (bookWidth - 48) / 2; pageLines = Math.max(3, (bookHeight - 94) / 11);
        search = addRenderableWidget(new EditBox(font, left + 17, top + 11, Math.max(70, bookWidth - 108), 14, Component.literal("Search the encyclopedia")));
        search.setBordered(false); search.setTextShadow(false); search.setTextColor(INK); search.setMaxLength(120);
        search.setValue(query); search.setResponder(s -> query = s); search.setHint(Component.literal("Search the encyclopedia…"));
        add(left + bookWidth - 83, top + 6, 48, "Search", this::search);
        add(left + bookWidth - 30, top + 6, 20, "×", this::onClose);
        int y = top + bookHeight + 5;
        int tab = Math.min(46, (bookWidth - 18) / 7), step = tab + 3;
        add(left, y, tab, "Home", this::home);
        add(left + step, y, tab, "Back", this::back);
        contents = add(left + step * 2, y, tab, "Index", this::contents);
        add(left + step * 3, y, tab, "Saved", this::saved);
        mark = add(left + step * 4, y, tab, "Mark", this::bookmark);
        add(left + step * 5, y, tab, tab < 42 ? "Look" : "Look up", this::context);
        refresh = add(left + step * 6, y, tab, tab < 42 ? "Fetch" : "Update", this::refresh);
        previous = add(left + 13, top + bookHeight - 24, 24, "‹", () -> turn(-2));
        next = add(left + bookWidth - 37, top + bookHeight - 24, 24, "›", () -> turn(2));
        if (article == null) home(); else paginate();
    }
    private BookButton add(int x, int y, int w, String label, Runnable action) {
        BookButton button = addRenderableWidget(new BookButton(x, y, w, label, action));
        button.setTooltip(Tooltip.create(Component.literal(switch (label) {
            case "Look up", "Look" -> "Read about the targeted block or creature, or your held item";
            case "Mark" -> "Bookmark this article";
            case "Update", "Fetch" -> "Fetch the latest article; a saved copy remains available offline";
            case "Index" -> "Chapter index for this article";
            default -> label;
        }))); return button;
    }
    private BookArticle local(String title, List<Block> blocks) {
        return new BookArticle(title, 0, 0, "", "Original fieldbook text", List.copyOf(blocks));
    }
    private void display(BookArticle book, String mode, int requestedPage) {
        if (!mode.equals("read")) { pendingAnchor = ""; continuePage = -1; }
        this.article = book; this.mode = mode; page = Math.max(0, requestedPage); paginate();
    }
    private void navigate() { requestVersion++; status = ""; paper(); }
    private void home() {
        if (article == null) { requestVersion++; status = ""; } else navigate();
        List<Block> b = new ArrayList<>();
        b.add(new Block("heading", "", List.of(Span.text("A traveller’s companion"))));
        b.add(Block.paragraph("A book of creatures, places and curious things. Search for a subject above, or look at something in your world and choose Look up."));
        b.add(Block.paragraph("Turn pages with the arrows or mouse wheel. Green ink leads to another article. Index lists the chapters; Mark saves an article for later."));
        if (!ImmersiveWiki.settings.lastArticle.isBlank()) b.add(Block.link("Continue: " + ImmersiveWiki.settings.lastArticle, "@continue"));
        b.add(new Block("heading", "", List.of(Span.text("Begin exploring"))));
        for (String topic : List.of("Elytra", "Enchanting", "Enderman", "Chest", "Brewing", "Happy Ghast", "Redstone", "Nether")) b.add(Block.link(topic, topic));
        b.add(Block.paragraph("Articles are read from Minecraft Wiki and kept locally. No account or server installation is needed. World play continues on multiplayer servers while you read."));
        b.add(Block.link("About this book & its sources", "@credits"));
        display(local("The Fieldbook", b), "home", 0);
    }
    private void search() {
        String q = query.strip(); if (q.isEmpty()) return;
        long token = ++requestVersion; status = "The librarian is searching…";
        ImmersiveWiki.library.search(q, reply -> minecraft.execute(() -> {
            if (!current(token)) return;
            status = reply.error(); List<Block> b = new ArrayList<>();
            b.add(Block.paragraph("Subjects matching ‘" + q + "’. Choose a title to open its pages."));
            if (reply.value() != null && !reply.value().isEmpty()) reply.value().forEach(t -> b.add(Block.link(t, t)));
            else { b.add(Block.paragraph(reply.error().isBlank() ? "No matching title. Try a simpler name." : reply.error())); b.add(Block.link("Try the exact article: " + q, q)); }
            display(local("The index", b), "search", 0); paper();
        }));
    }
    public void read(String target, boolean pushHistory) {
        clearFocus();
        if (target.startsWith("@")) { special(target); return; }
        int hash = target.indexOf('#'); String title = hash < 0 ? target : target.substring(0, hash);
        String anchor = hash < 0 ? "" : target.substring(hash + 1);
        if (article != null && mode.equals("read") && title.equals(article.title())) {
            if (anchor.isEmpty()) page = 0; else page = chapterPage(anchor);
            page -= page % 2; controls(); paper(); return;
        }
        long token = ++requestVersion; status = "Finding ‘" + title + "’…";
        ImmersiveWiki.library.article(title, false, reply -> minecraft.execute(() -> {
            if (!current(token)) return;
            if (reply.value() == null) { status = reply.error(); display(local("An empty shelf", List.of(Block.paragraph(reply.error()), Block.link("Search for “" + title + "”", "@search:" + title))), "error", 0); return; }
            if (pushHistory && article != null && mode.equals("read")) {
                history.addLast(new Visit(article, page)); while (history.size() > 16) history.removeFirst();
            }
            status = reply.error().isBlank() ? (reply.cached() ? "Saved edition" : "Fresh from Minecraft Wiki") : reply.error();
            pendingAnchor = anchor; display(reply.value(), "read", 0); paper();
        }));
    }
    private boolean current(long token) { return requestVersion == token && minecraft.gui.screen() == this; }
    private int chapterPage(String anchor) {
        Integer exact = layout.anchors().get(anchor);
        if (exact != null) return exact;
        String normalized = anchor.replace('_', ' ');
        return layout.chapters().stream().filter(c -> c.title().equalsIgnoreCase(normalized)).map(BookLayout.Chapter::page).findFirst().orElse(0);
    }
    private void special(String target) {
        if (target.equals("@credits")) {
            navigate(); display(local("The colophon", List.of(
                    Block.paragraph("This fieldbook is an original client-side Minecraft interface. Encyclopedia articles are by Minecraft Wiki contributors and carry CC BY-NC-SA 3.0; each article ends with its revision, source and attribution."),
                    Block.paragraph("HTML is parsed into chapters, paragraphs, lists and labelled table entries. Site navigation and advertising are excluded. In-game item models replace article header illustrations where possible; web diagrams and gallery images are not rendered in this edition."),
                    Block.paragraph("Book interface code: MIT. HTML parser: jsoup, MIT. No webpage, scripts or browser engine run inside the game."),
                    Block.paragraph("Page, opening, closing and bookmark sound events can be replaced through a resource pack. Custom ElevenLabs sounds can be added later without sharing an API key with your friends."))), "credits", 0);
        } else if (target.equals("@continue")) {
            int saved = ImmersiveWiki.settings.lastPage;
            read(ImmersiveWiki.settings.lastArticle, true);
            // Apply the saved leaf after the asynchronous edition arrives.
            continuePage = saved;
        } else if (target.startsWith("@chapter:")) {
            navigate(); display(readingArticle, "read", Integer.parseInt(target.substring(9)));
        } else if (target.startsWith("@search:")) {
            query = target.substring(8); search.setValue(query); search();
        }
    }
    private int continuePage = -1;
    private void contents() {
        if (!mode.equals("read")) return;
        navigate(); readingArticle = article; readingPage = page;
        List<Block> entries = new ArrayList<>(); entries.add(Block.link("Return to your page", "@chapter:" + page));
        for (BookLayout.Chapter c : layout.chapters()) entries.add(Block.link(c.title() + "  ·  " + (c.page() + 1), "@chapter:" + (c.page() - c.page() % 2)));
        display(local("Chapters · " + article.title(), entries), "contents", 0);
    }
    private void saved() {
        navigate(); List<Block> b = new ArrayList<>();
        if (ImmersiveWiki.settings.bookmarks.isEmpty()) b.add(Block.paragraph("Your ribbon marks are empty. Open an article and choose Mark to keep it here."));
        else ImmersiveWiki.settings.bookmarks.forEach(t -> b.add(Block.link(t, t)));
        display(local("Ribbon marks", b), "saved", 0);
    }
    private void bookmark() {
        if (!mode.equals("read")) return;
        List<String> saved = ImmersiveWiki.settings.bookmarks;
        if (!saved.remove(article.title())) { saved.add(article.title()); if (saved.size() > 64) saved.removeFirst(); }
        ImmersiveWiki.library.saveSettings(ImmersiveWiki.settingsFile, ImmersiveWiki.settings);
        ImmersiveWiki.sound(ImmersiveWiki.settings.bookmarkSound, SoundEvents.BOOK_PAGE_TURN, 1.1f); controls();
    }
    private void context() {
        String title = ImmersiveWiki.contextTitle();
        if (title.isBlank()) { status = "Look at a block or creature, or hold an item, then try Look up."; return; }
        search.setValue(title); read(title, true);
    }
    private void refresh() {
        if (!mode.equals("read")) return;
        long token = ++requestVersion; int oldPage = page; status = "Fetching the latest edition…";
        ImmersiveWiki.library.article(article.title(), true, reply -> minecraft.execute(() -> {
            if (!current(token)) return;
            status = reply.error(); if (reply.value() != null) display(reply.value(), "read", oldPage);
        }));
    }
    private void back() {
        navigate();
        if (mode.equals("contents") && readingArticle != null) display(readingArticle, "read", readingPage);
        else if (!history.isEmpty()) { Visit last = history.removeLast(); display(last.article(), "read", last.page()); }
        else home();
    }
    private void paginate() {
        if (article == null) return;
        long layoutStarted = System.nanoTime();
        NativeBookMetrics metrics = new NativeBookMetrics(font, article);
        long mainNanos = System.nanoTime() - layoutStarted;
        BookArticle book = article; int requestedPage = page, width = pageWidth, lines = pageLines;
        long generation = ++layoutVersion;
        if (book.blocks().size() <= 32) {
            applyPrepared(prepare(book, width, lines, metrics), requestedPage, mainNanos);
        } else {
            visual = List.of(); layout = new BookLayout.Result(List.of(), List.of(), Map.of()); illustration = ItemStack.EMPTY; controls();
            PAGINATOR.execute(() -> {
                Prepared prepared = prepare(book, width, lines, metrics);
                minecraft.execute(() -> {
                    if (generation != layoutVersion || article != book) return;
                    applyPrepared(prepared, requestedPage, mainNanos);
                });
            });
        }
    }
    private static Prepared prepare(BookArticle article, int width, int lines, NativeBookMetrics metrics) {
        long started = System.nanoTime();
        BookLayout.Result layout = BookLayout.paginate(article, width, lines, metrics);
        List<List<VisualLine>> visual = layout.pages().stream().map(page -> page.stream().map(line -> new VisualLine(line.runs().stream()
                .map(run -> new VisualRun(component(run.span()), run.x(), run.width(), run.span().target())).toList(), line.heading())).toList()).toList();
        return new Prepared(layout, visual, System.nanoTime() - started);
    }
    private void applyPrepared(Prepared prepared, int requestedPage, long mainNanos) {
        long started = System.nanoTime(); layout = prepared.layout(); visual = prepared.visual();
        workerLayoutNanos = article.blocks().size() > 32 ? prepared.nanos() : 0;
        if (workerLayoutNanos == 0) mainNanos += prepared.nanos();
        page = Math.min(requestedPage - requestedPage % 2, ((visual.size() - 1) / 2) * 2);
        if (mode.equals("read") && continuePage >= 0) { page = Math.min(continuePage - continuePage % 2, ((visual.size() - 1) / 2) * 2); continuePage = -1; }
        if (mode.equals("read") && !pendingAnchor.isEmpty()) { page = chapterPage(pendingAnchor); page -= page % 2; pendingAnchor = ""; }
        illustration = ItemStack.EMPTY;
        if (mode.equals("read")) {
            String item = article.title().toLowerCase(Locale.ROOT).replace(' ', '_');
            Identifier id = Identifier.tryParse("minecraft:" + item);
            // In 26.2 item components are bound by the world's registry context.
            if (minecraft.level != null && id != null && BuiltInRegistries.ITEM.containsKey(id))
                illustration = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            ImmersiveWiki.settings.lastArticle = article.title(); ImmersiveWiki.settings.lastPage = page;
        }
        controls();
        layoutNanos = mainNanos + System.nanoTime() - started;
    }
    private static Component component(Span span) {
        return Component.literal(span.text()).setStyle(Style.EMPTY.withBold(span.bold()).withItalic(span.italic()).withColor(span.target().isEmpty() ? INK & 0xffffff : LINK & 0xffffff));
    }
    private void controls() {
        if (previous == null) return;
        previous.active = !visual.isEmpty() && page > 0; next.active = page + 2 < visual.size();
        mark.active = contents.active = refresh.active = mode.equals("read") && !visual.isEmpty();
        mark.setMessage(Component.literal(mode.equals("read") && ImmersiveWiki.settings.bookmarks.contains(article.title()) ? "Unmark" : "Mark"));
    }
    private void turn(int by) {
        int target = Math.clamp(page + by, 0, ((visual.size() - 1) / 2) * 2);
        if (target == page) return;
        page = target; if (mode.equals("read")) ImmersiveWiki.settings.lastPage = page;
        controls(); paper();
    }
    private void paper() { turnAt = System.nanoTime(); ImmersiveWiki.sound(ImmersiveWiki.settings.pageSound, SoundEvents.BOOK_PAGE_TURN, 1f); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // A translucent veil avoids another expensive shader-blur pass.
        g.fill(0, 0, width, height, 0x650c100e);
        drawBook(g);
        String title = font.plainSubstrByWidth(article == null ? "The Fieldbook" : article.title(), bookWidth - 88);
        g.text(font, title, left + 20, top + 31, INK, false);
        if (!illustration.isEmpty()) g.fakeItem(illustration, left + bookWidth - 37, top + 28);
        g.fill(left + 14, top + 46, left + bookWidth - 14, top + 47, 0xffb99e6c);
        links.clear(); drawPage(g, page, left + 17, mouseX, mouseY); drawPage(g, page + 1, left + bookWidth / 2 + 7, mouseX, mouseY);
        if (visual.isEmpty()) g.text(font, "Opening the pages…", left + 17, top + 54, INK, false);
        if (!status.isBlank()) {
            String shortStatus = font.plainSubstrByWidth(status, bookWidth - 92);
            g.text(font, shortStatus, left + 45, top + bookHeight - 19, 0xff766246, false);
            if (mouseY >= top + bookHeight - 25 && mouseY < top + bookHeight)
                g.setTooltipForNextFrame(font, Component.literal(status), mouseX, mouseY);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
    private void drawBook(GuiGraphicsExtractor g) {
        int x = left, y = top, w = bookWidth, h = bookHeight, mid = x + w / 2;
        g.fill(x + 4, y + 5, x + w + 5, y + h + 6, 0x90000000);
        g.fill(x, y + 3, x + w, y + h - 2, 0xff503629);
        g.fill(x + 3, y, x + w - 3, y + h, 0xff503629);
        g.fill(x + 6, y + 4, x + w - 6, y + h - 4, 0xffc3ae7b);
        g.fill(x + 9, y + 5, mid - 3, y + h - 7, 0xffeee0b7);
        g.fill(mid + 3, y + 5, x + w - 9, y + h - 7, 0xfff4e6bf);
        g.fill(x + 10, y + 6, mid - 6, y + 8, 0xfffaf0d3);
        g.fill(mid + 5, y + 6, x + w - 10, y + 8, 0xfffff2d4);
        g.fill(mid - 3, y + 7, mid + 3, y + h - 8, 0xffac8e59);
        g.fill(mid - 6, y + 8, mid - 3, y + h - 9, 0xffd1ba88);
        g.fill(mid + 3, y + 8, mid + 6, y + h - 9, 0xffdaca9e);
        for (int i = 0; i < 3; i++) g.fill(x + 11, y + h - 6 + i, x + w - 11, y + h - 5 + i, i % 2 == 0 ? 0xffe1cb95 : 0xffbaa074);
        // Brief ink-safe spine shimmer suggests a page turn without moving glyphs.
        long elapsed = System.nanoTime() - turnAt;
        if (elapsed >= 0 && elapsed < 160_000_000L) {
            int alpha = (int)(40 * (1 - elapsed / 160_000_000.0));
            g.fill(mid + 6, y + 48, mid + 10, y + h - 27, alpha << 24 | 0xffffff);
        }
    }
    private void drawPage(GuiGraphicsExtractor g, int leaf, int x, int mouseX, int mouseY) {
        if (leaf >= visual.size()) return;
        int y = top + 54;
        g.enableScissor(x, y, x + pageWidth, y + pageLines * 11);
        for (VisualLine line : visual.get(leaf)) {
            for (VisualRun run : line.runs()) {
                g.text(font, run.text(), x + run.x(), y, INK, false);
                if (!run.target().isEmpty() && !run.text().getString().isBlank()) {
                    links.add(new Hit(x + run.x(), y, run.width(), run.target()));
                    if (mouseX >= x + run.x() && mouseX < x + run.x() + run.width() && mouseY >= y && mouseY < y + 10)
                        g.fill(x + run.x(), y + 9, x + run.x() + run.width(), y + 10, LINK);
                }
            }
            y += 11;
        }
        g.disableScissor();
        String number = (leaf + 1) + " / " + visual.size();
        g.text(font, number, x + (pageWidth - font.width(number)) / 2, top + bookHeight - 34, 0xff89714d, false);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == 0) for (Hit h : links)
            if (event.x() >= h.x() && event.x() < h.x() + h.width() && event.y() >= h.y() && event.y() < h.y() + 10) {
                read(h.target(), true); return true;
            }
        return false;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0 && x >= left && x <= left + bookWidth && y >= top && y <= top + bookHeight) { turn(vertical < 0 ? 2 : -2); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (search.isFocused()) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { search(); return true; }
        } else {
            if (event.key() == GLFW.GLFW_KEY_RIGHT || event.key() == GLFW.GLFW_KEY_PAGE_DOWN) { turn(2); return true; }
            if (event.key() == GLFW.GLFW_KEY_LEFT || event.key() == GLFW.GLFW_KEY_PAGE_UP) { turn(-2); return true; }
            if (ImmersiveWiki.openKey.matches(event)) { onClose(); return true; }
        }
        return super.keyPressed(event);
    }
    @Override public boolean isPauseScreen() { return true; }
    @Override public void onClose() {
        requestVersion++;
        ImmersiveWiki.library.saveSettings(ImmersiveWiki.settingsFile, ImmersiveWiki.settings);
        ImmersiveWiki.sound(ImmersiveWiki.settings.closeSound, SoundEvents.BOOK_PUT, .8f);
        minecraft.gui.setScreen(parent);
    }
    @Override public void removed() { requestVersion++; layoutVersion++; }
}
