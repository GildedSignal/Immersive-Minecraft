package mcperf.wiki;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

public final class BookSettings {
    public String language = "en";
    public float soundVolume = .35f;
    public String openSound = "immersive_wiki:book_open";
    public String pageSound = "immersive_wiki:page_turn";
    public String closeSound = "immersive_wiki:book_close";
    public String bookmarkSound = "immersive_wiki:bookmark";
    public List<String> bookmarks = new ArrayList<>();
    public String lastArticle = "";
    public int lastPage = 0;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static BookSettings load(Path path) {
        try {
            BookSettings value = JSON.fromJson(Files.readString(path), BookSettings.class);
            if (value == null) return new BookSettings();
            if (!Set.of("en", "fr").contains(value.language)) value.language = "en";
            value.soundVolume = Float.isFinite(value.soundVolume) ? Math.clamp(value.soundVolume, 0f, 1f) : .35f;
            if (value.bookmarks == null) value.bookmarks = new ArrayList<>();
            value.bookmarks = new ArrayList<>(value.bookmarks.stream().filter(Objects::nonNull).filter(t -> t.length() <= 180 && !t.isBlank()).distinct().limit(64).toList());
            if (value.lastArticle == null) value.lastArticle = "";
            value.lastPage = Math.clamp(value.lastPage, 0, 6000);
            return value;
        } catch (Exception e) { return new BookSettings(); }
    }
    /** Called on the librarian worker only, never on the rendering thread. */
    public static void save(Path path, BookSettings snapshot) {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = Files.createTempFile(path.getParent(), ".fieldbook-", ".tmp");
            try {
                Files.writeString(temporary, JSON.toJson(snapshot));
                try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
        } catch (Exception ignored) { }
    }
    public BookSettings snapshot() { return JSON.fromJson(JSON.toJson(this), BookSettings.class); }
}
