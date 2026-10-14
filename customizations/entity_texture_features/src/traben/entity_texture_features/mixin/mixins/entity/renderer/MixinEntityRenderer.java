/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.ModifyReturnValue
 *  com.llamalad7.mixinextras.sugar.Local
 *  net.minecraft.client.renderer.entity.EntityRenderer
 *  net.minecraft.client.renderer.entity.state.EntityRenderState
 *  net.minecraft.world.entity.Entity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package traben.entity_texture_features.mixin.mixins.entity.renderer;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_texture_features.ETF;
import traben.entity_texture_features.features.state.ETFState;
import traben.entity_texture_features.features.state.HoldsETFRenderState;
import traben.entity_texture_features.utils.ETFEntity;

@Mixin(value={EntityRenderer.class})
public abstract class MixinEntityRenderer<T extends Entity, S extends EntityRenderState> {
    private static final String RENDER = "submit";

    @Inject(method={"extractRenderState"}, at={@At(value="TAIL")})
    private void etf$createRenderState(CallbackInfo ci, @Local(argsOnly=true) Entity entity, @Local(argsOnly=true) S state) {
        ((HoldsETFRenderState)state).etf$initState((ETFEntity)entity);
    }

    @ModifyReturnValue(method={"getPackedLightCoords"}, at={@At(value="RETURN")})
    private int etf$vanillaLightOverrideCancel(int light, @Local(argsOnly=true) T entity, @Local(argsOnly=true) float tickDelta) {
        return ETF.config().getConfig().getLightOverride((Entity)entity, tickDelta, light);
    }

    @Inject(method={"submit"}, at={@At(value="HEAD")})
    private void etf$protectPostRenderersLikeNametag(CallbackInfo ci) {
        ETFState.pushRenderLayerModifyState(false);
    }

    @Inject(method={"submit"}, at={@At(value="TAIL")})
    private void etf$revertForRenderersThatCallSuperFirst(CallbackInfo ci) {
        ETFState.popRenderLayerModifyState();
    }
}

