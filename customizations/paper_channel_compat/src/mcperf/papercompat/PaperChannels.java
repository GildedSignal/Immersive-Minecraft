package mcperf.papercompat;
import java.util.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.*;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import org.slf4j.*;

/** Changes only public channel advertisement lists on identified Paper-family servers. */
public final class PaperChannels {
    private static final Logger LOG = LoggerFactory.getLogger("paper-channel-compat");
    private static final Map<Connection, ChannelBudget> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private static ChannelBudget state(Connection connection) { return STATES.computeIfAbsent(connection, c -> new ChannelBudget()); }
    public static Packet<?> outgoing(Connection connection, Packet<?> packet) {
        if (!(packet instanceof ServerboundCustomPayloadPacket custom) || !(custom.payload() instanceof RegistrationPayload registration)) return packet;
        if (connection.isMemoryConnection()) return packet;
        synchronized (STATES) {
            ChannelBudget budget=state(connection);
            String brand=connection.getPacketListener() instanceof ClientCommonPacketListenerImpl listener ? listener.serverBrand() : null;
            boolean paper=ChannelBudget.isPaper(brand,false);
            boolean add=registration.type().id().equals(RegistrationPayload.REGISTER.id());
            List<String> original=registration.channels().stream().map(Object::toString).toList();
            List<String> allowed=budget.apply(original,add,paper);
            if (!paper || allowed.size()==original.size()) return packet;
            var omitted=original.stream().filter(id -> !allowed.contains(id)).toList();
            LOG.info("[paper-channel-compat] brand={} registration={} retained={} omitted={} total={}/127; local receivers remain installed", brand, original.size(), allowed.size(), omitted, budget.size());
            return new ServerboundCustomPayloadPacket(new RegistrationPayload(registration.type(),allowed.stream().map(Identifier::parse).toList()));
        }
    }
    public static void incoming(Connection connection, Packet<?> packet) {
        if (connection.isMemoryConnection() || !(packet instanceof ClientboundCustomPayloadPacket custom) || !(custom.payload() instanceof RegistrationPayload registration)) return;
        synchronized (STATES) {
            state(connection).serverChannels(registration.channels().stream().map(Object::toString).toList(),registration.type().id().equals(RegistrationPayload.REGISTER.id()));
        }
    }
}
