/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package emfjit;

import emfjit.EvalSplitter;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EmfJit {
    public static final Logger LOG = LoggerFactory.getLogger((String)"emf_jit");
    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("emfjit", "true"));
    public static final int CHUNK = Integer.getInteger("emfjit.chunk", 3000);
    public static final boolean VERBOSE = Boolean.getBoolean("emfjit.verbose");
    public static final AtomicInteger SEEN = new AtomicInteger();
    public static final AtomicInteger SPLIT = new AtomicInteger();
    public static final AtomicInteger FALLBACK = new AtomicInteger();
    private static volatile boolean disabled;
    private static volatile boolean announced;

    private EmfJit() {
    }

    public static byte[] process(byte[] byArray) {
        if (!ENABLED || disabled) {
            return byArray;
        }
        if (!announced) {
            announced = true;
            LOG.info("[emf_jit] active: splitting EMF animation eval methods larger than {} bytes into <= ~{} byte chunks (disable with -Demfjit=false)", (Object)8000, (Object)CHUNK);
        }
        try {
            SEEN.incrementAndGet();
            EvalSplitter.Result result = EvalSplitter.split(byArray, CHUNK);
            if (result.split()) {
                SPLIT.incrementAndGet();
                if (VERBOSE) {
                    LOG.info("[emf_jit] eval {} bytes -> {} chunks (max {} bytes)", new Object[]{result.originalCodeSize(), result.chunks(), result.maxChunkCodeSize()});
                }
                return result.bytes();
            }
            if (result.originalCodeSize() >= 8000 || result.originalCodeSize() < 0) {
                FALLBACK.incrementAndGet();
                LOG.warn("[emf_jit] left an EMF eval method unsplit ({} bytes): {}", (Object)result.originalCodeSize(), (Object)result.note());
            }
            return byArray;
        }
        catch (Throwable throwable) {
            disabled = true;
            LOG.error("[emf_jit] unexpected error, disabling for the rest of the session (EMF keeps working unchanged)", throwable);
            return byArray;
        }
    }
}

