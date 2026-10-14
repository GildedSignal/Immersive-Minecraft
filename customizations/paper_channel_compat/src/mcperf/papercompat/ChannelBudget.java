package mcperf.papercompat;
import java.util.*;

/** Per-connection channel accounting. One spare slot avoids Paper's pre-dedup limit check. */
public final class ChannelBudget {
    private final Set<String> registered = new HashSet<>();
    private final Set<String> supportedNamespaces = new HashSet<>();
    public void serverChannels(Collection<String> channels, boolean add) {
        // Namespace hints only prioritize advertisement; they never grant packet permissions.
        if (add) for (String channel : channels) supportedNamespaces.add(namespace(channel));
    }
    public List<String> apply(Collection<String> channels, boolean add, boolean paper) {
        if (!add) { registered.removeAll(channels); return new ArrayList<>(channels); }
        if (!paper) { registered.addAll(channels); return new ArrayList<>(channels); }
        var candidates = channels.stream().distinct().sorted(Comparator.comparingInt(this::rank).thenComparing(x -> x)).toList();
        var result = new ArrayList<String>();
        for (String channel : candidates) {
            if (registered.contains(channel) || registered.size() < 127) { registered.add(channel); result.add(channel); }
        }
        return result;
    }
    private static String namespace(String channel) { int i=channel.indexOf(':');return i<0 ? channel : channel.substring(0,i); }
    private int rank(String channel) {
        String ns=namespace(channel);
        if (supportedNamespaces.contains(ns)) return 0;
        return switch(ns) { case "c", "fabric" -> 1; case "voicechat" -> 2; case "journeymap", "worldinfo" -> 3; case "fieldguide" -> 100; default -> 10; };
    }
    public int size() { return registered.size(); }
    public static boolean isPaper(String brand, boolean local) {
        if (local || brand == null) return false;
        String lower=brand.toLowerCase(Locale.ROOT);
        return lower.equals("paper") || lower.startsWith("paper ") || lower.equals("purpur") || lower.equals("folia");
    }
}
