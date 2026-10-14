/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.irisshaders.iris.api.v0.IrisApi
 *  net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package irisorder;

import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IrisTranslucentOrder {
    public static final Logger LOG = LoggerFactory.getLogger((String)"iris_translucent_order");
    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("iristranslucentorder", "true"));
    private static boolean logged;
    private static boolean failed;

    private IrisTranslucentOrder() {
    }

    public static boolean shouldReorder() {
        if (!ENABLED || failed) {
            return false;
        }
        try {
            if (!IrisApi.getInstance().isShaderPackInUse()) {
                return false;
            }
            boolean bl = WorldRenderingSettings.INSTANCE.shouldSeparateEntityDraws();
            if (!logged) {
                logged = true;
                LOG.info("[iris_translucent_order] active: shader pack in use, separateEntityDraws={} -> {}", (Object)bl, (Object)(bl ? "leaving vanilla 26.x order" : "drawing translucent features before deferred (pre-26.1 order)"));
            }
            return !bl;
        }
        catch (Throwable throwable) {
            failed = true;
            LOG.error("[iris_translucent_order] disabled after error", throwable);
            return false;
        }
    }
}

