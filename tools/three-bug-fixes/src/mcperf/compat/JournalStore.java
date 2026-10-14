package mcperf.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Separate local-only journals, scoped to player + server, never sent to a server. */
public final class JournalStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public record Page(String title, String content, long timestamp) {}
    public record Journal(int schema, String title, List<Page> pages) {}
    public static Path file(Path game, String scope) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8));
            return game.resolve("fieldguide_local_journals").resolve(java.util.HexFormat.of().formatHex(hash)+".json");
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static void validate(Journal value) {
        if (value == null || value.schema != 1 || value.title == null || value.title.length()>64 || value.pages == null || value.pages.size()>127)
            throw new IllegalArgumentException("Invalid local journal");
        for (Page p : value.pages) if (p == null || p.title == null || p.content == null || p.title.length()>64 || p.content.length()>1024)
            throw new IllegalArgumentException("Invalid local journal page");
    }
    public static Journal load(Path file) throws java.io.IOException {
        if (!Files.exists(file)) return null;
        if (Files.size(file)>2_000_000) throw new java.io.IOException("Local journal is too large");
        Journal value = GSON.fromJson(Files.readString(file), Journal.class);
        validate(value); return value;
    }
    public static void save(Path file, Journal value) throws java.io.IOException {
        validate(value);
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName()+".tmp");
        Files.writeString(temp, GSON.toJson(value)+"\n");
        if (Files.exists(file)) Files.copy(file,file.resolveSibling(file.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
        try { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
    }
}
