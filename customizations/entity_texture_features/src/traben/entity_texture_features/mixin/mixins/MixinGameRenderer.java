/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.GameRenderer
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package traben.entity_texture_features.mixin.mixins;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_texture_features.features.state.ETFFrameNbtCache;
import traben.entity_texture_features.features.state.ETFState;

@Mixin(value={GameRenderer.class})
public class MixinGameRenderer {
    @Inject(method={"render"}, at={@At(value="HEAD")})
    private void emf$injectCounter(CallbackInfo ci) {
        ETFState.stackVerifyEmpty();
        ETFFrameNbtCache.beginFrame();
    }

    @Inject(method={"render"}, at={@At(value="RETURN")})
    private void etf$perfFrameNbtCacheEnd(CallbackInfo ci) {
        ETFFrameNbtCache.endFrame();
    }
}

