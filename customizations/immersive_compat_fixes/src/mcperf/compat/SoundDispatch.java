package mcperf.compat;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Consumer;

/** Calls retain their order on Minecraft's executor; stale sessions/reloads are discarded. */
public final class SoundDispatch {
    public static void enqueue(Consumer<Runnable> executor, LongSupplier generation, BooleanSupplier active, Runnable callback) {
        long captured = generation.getAsLong();
        executor.accept(() -> {
            if (generation.getAsLong() == captured && active.getAsBoolean()) callback.run();
        });
    }
}
