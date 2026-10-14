package mcperf.wiki;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** A single bounded background worker handles HTTP, parsing and cache I/O. */
public final class WikiLibrary implements AutoCloseable {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(2), r -> { Thread t = new Thread(r, "Immersive Wiki librarian"); t.setDaemon(true); return t; });
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final LinkedHashMap<String, BookArticle> memory = new LinkedHashMap<>(32, .75f, true);
    private final Path cache;
    private final String host;
    public WikiLibrary(Path cache, String language) {
        this.cache = cache; host = language.equals("fr") ? "fr.minecraft.wiki" : "minecraft.wiki";
    }
    public record Reply<T>(T value, String error, boolean cached) {}
    public String host() { return host; }
    public void saveSettings(Path path, BookSettings settings) {
        BookSettings snapshot = settings.snapshot();
        try { worker.execute(() -> BookSettings.save(path, snapshot)); }
        catch (RejectedExecutionException ignored) { }
    }
    private <T> void submit(Callable<Reply<T>> task, Consumer<Reply<T>> reply) {
        try {
            worker.execute(() -> {
                Reply<T> result;
                try { result = task.call(); }
                catch (Exception e) {
                    String reason = e instanceof HttpTimeoutException || e instanceof SocketTimeoutException
                            ? "The librarian could not reach the wiki in time. Try again, or read a saved article."
                            : e instanceof NoSuchElementException ? e.getMessage()
                            : "The wiki could not be read. Try again, or use a saved article.";
                    result = new Reply<>(null, reason, false);
                }
                reply.accept(result);
            });
        } catch (RejectedExecutionException e) { reply.accept(new Reply<>(null, "The librarian is busy. Try again in a moment.", false)); }
    }
    public void search(String query, Consumer<Reply<List<String>>> callback) {
        submit(() -> {
            JsonArray result = request("action=opensearch&namespace=0&limit=12&format=json&search=" + encode(query)).getAsJsonArray();
            List<String> titles = new ArrayList<>();
            for (JsonElement title : result.get(1).getAsJsonArray()) titles.add(title.getAsString());
            return new Reply<>(List.copyOf(titles), "", false);
        }, callback);
    }
    public void article(String title, boolean refresh, Consumer<Reply<BookArticle>> callback) {
        submit(() -> {
            validateTitle(title);
            BookArticle previous = readCache(title);
            if (!refresh && previous != null) return new Reply<>(previous, "", true);
            try {
                JsonObject response = request("action=parse&redirects=1&prop=text%7Crevid%7Cdisplaytitle&format=json&formatversion=2&page=" + encode(title)).getAsJsonObject();
                if (response.has("error")) throw new NoSuchElementException("No article was found for ‘" + title + "’. Search the index for a related name.");
                JsonObject p = response.getAsJsonObject("parse");
                String canonical = p.get("title").getAsString();
                BookArticle article = new WikiParser().parse(canonical, p.get("revid").getAsLong(), p.get("text").getAsString(), source(canonical));
                remember(title, article); remember(canonical, article);
                try {
                    writeCache(title, article);
                    if (!canonical.equals(title)) writeCache(canonical, article);
                } catch (IOException e) { /* Still readable if cache is unavailable. */ }
                return new Reply<>(article, "", false);
            } catch (Exception e) {
                if (previous != null) return new Reply<>(previous, "Saved copy — the wiki is currently unavailable.", true);
                throw e;
            }
        }, callback);
    }
    private BookArticle readCache(String title) {
        BookArticle inMemory = memory.get(title);
        if (inMemory != null) return inMemory;
        try {
            Path file = file(title); if (!Files.isRegularFile(file) || Files.size(file) > 4_000_000) return null;
            BookArticle article = JSON.fromJson(Files.readString(file), BookArticle.class);
            if (article == null || article.blocks() == null || article.blocks().size() > 6004 || !article.source().startsWith("https://" + host + "/w/")) return null;
            remember(title, article); return article;
        } catch (Exception e) { return null; }
    }
    private void remember(String title, BookArticle article) {
        memory.put(title, article);
        while (memory.size() > 24) memory.remove(memory.keySet().iterator().next());
    }
    private void writeCache(String title, BookArticle article) throws IOException {
        Files.createDirectories(cache);
        Path file = file(title), temporary = Files.createTempFile(cache, ".article-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(article), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
        try (var files = Files.list(cache)) {
            List<Path> sorted = files.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}\\.json"))
                    .sorted(Comparator.comparingLong(WikiLibrary::modified).reversed()).toList();
            long bytes = 0;
            for (int i = 0; i < sorted.size(); i++) {
                Path p = sorted.get(i); bytes += Files.size(p);
                if (i >= 64 || bytes > 32_000_000) Files.deleteIfExists(p);
            }
        }
    }
    private static long modified(Path p) { try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return 0; } }
    private Path file(String title) {
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((host + "\n" + title).getBytes(StandardCharsets.UTF_8)));
            return cache.resolve(digest + ".json");
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private JsonElement request(String query) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://" + host + "/api.php?" + query))
                .timeout(Duration.ofSeconds(18)).header("User-Agent", "ImmersiveMinecraftFieldbook/0.1 (personal native-book wiki reader; https://minecraft.wiki/)")
                .header("Accept", "application/json").GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) throw new IOException("Wiki HTTP " + response.statusCode());
            byte[] bytes = body.readNBytes(8_000_001);
            if (bytes.length > 8_000_000) throw new IOException("Wiki response too large");
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
        }
    }
    public String source(String title) { return "https://" + host + "/w/" + encode(title.replace(' ', '_')); }
    public static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20"); }
    public static void validateTitle(String title) {
        if (title == null || title.isBlank() || title.length() > 180 || title.indexOf('\n') >= 0 || title.indexOf(':') >= 0)
            throw new IllegalArgumentException("Invalid article name");
    }
    @Override public void close() { worker.shutdownNow(); http.close(); }
}
