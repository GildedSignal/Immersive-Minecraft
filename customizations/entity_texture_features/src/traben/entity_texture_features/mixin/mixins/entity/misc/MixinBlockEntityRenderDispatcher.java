/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.sugar.Local
 *  com.llamalad7.mixinextras.sugar.Share
 *  com.llamalad7.mixinextras.sugar.ref.LocalRef
 *  net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher
 *  net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package traben.entity_texture_features.mixin.mixins.entity.misc;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_texture_features.features.state.ETFEntityRenderState;
import traben.entity_texture_features.features.state.ETFState;
import traben.entity_texture_features.features.state.HoldsETFRenderState;

@Mixin(value={BlockEntityRenderDispatcher.class})
public class MixinBlockEntityRenderDispatcher {
    private static final String RENDER_METHOD = "submit";

    @Inject(method={"submit"}, at={@At(value="HEAD")})
    private static <S extends BlockEntityRenderState> void etf$grabContext(CallbackInfo ci, @Local(argsOnly=true) S state, @Share(value="state_etf") LocalRef<ETFEntityRenderState> stateRef) {
        ETFEntityRenderState etf = ((HoldsETFRenderState)state).etf$getState();
        if (etf != null) {
            ETFState.mount(etf);
        }
        stateRef.set((Object)etf);
    }

    @Inject(method={"submit"}, at={@At(value="RETURN")})
    private static void etf$clearContext(CallbackInfo ci, @Share(value="state_etf") LocalRef<ETFEntityRenderState> stateRef) {
        if (stateRef.get() != null) {
            ETFState.stackVerify((ETFEntityRenderState)stateRef.get());
            ETFState.unMount();
        }
    }
}

