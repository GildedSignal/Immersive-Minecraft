/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.nbt.CompoundTag
 */
package traben.entity_texture_features.features.state;

import java.util.IdentityHashMap;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import traben.entity_texture_features.features.state.ETFEntityRenderState;

public final class ETFFrameNbtCache {
    private static final IdentityHashMap<Object, CompoundTag> FRAME = new IdentityHashMap();
    private static Thread windowThread = null;

    private ETFFrameNbtCache() {
    }

    public static void beginFrame() {
        FRAME.clear();
        windowThread = Thread.currentThread();
    }

    public static void endFrame() {
        windowThread = null;
        FRAME.clear();
    }

    public static CompoundTag cache(ETFEntityRenderState state, UUID uuid, Supplier<CompoundTag> compute, Object owner) {
        if (windowThread != Thread.currentThread() || owner == null) {
            return state.cacheEntityNBTForState(uuid, compute);
        }
        return state.cacheEntityNBTForState(uuid, new Shared(compute, owner));
    }

    private static final class Shared
    implements Supplier<CompoundTag> {
        private final Supplier<CompoundTag> compute;
        private final Object owner;

        Shared(Supplier<CompoundTag> compute, Object owner) {
            this.compute = compute;
            this.owner = owner;
        }

        @Override
        public CompoundTag get() {
            if (windowThread != Thread.currentThread()) {
                return this.compute.get();
            }
            CompoundTag tag = FRAME.get(this.owner);
            if (tag == null && (tag = this.compute.get()) != null && windowThread == Thread.currentThread()) {
                FRAME.put(this.owner, tag);
            }
            return tag;
        }
    }
}

