/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.ModifyExpressionValue
 *  com.llamalad7.mixinextras.sugar.Local
 *  net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
 *  net.minecraft.client.renderer.feature.ModelFeatureRenderer$CrumblingOverlay
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  org.jetbrains.annotations.NotNull
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package traben.entity_texture_features.mixin.mixins.entity.misc;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_texture_features.ETF;
import traben.entity_texture_features.features.state.ETFEntityRenderState;
import traben.entity_texture_features.features.state.HoldsETFRenderState;
import traben.entity_texture_features.utils.ETFEntity;

@Mixin(value={BlockEntityRenderState.class})
public class MixinBlockEntityRenderState
implements HoldsETFRenderState {
    @Unique
    private ETFEntityRenderState etf$state = null;

    @Override
    @NotNull
    public ETFEntityRenderState etf$getState() {
        assert (this.etf$state != null);
        return this.etf$state;
    }

    @Override
    public void etf$initState(@NotNull ETFEntity entity) {
        this.etf$state = ETFEntityRenderState.forEntity(entity);
        this.etf$state.setVanillaBlockState((BlockEntityRenderState)this);
    }

    @Inject(method={"extractBase"}, at={@At(value="HEAD")})
    private static void etf$createRenderState(BlockEntity blockEntity, BlockEntityRenderState blockEntityRenderState, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, CallbackInfo ci) {
        HoldsETFRenderState holder = (HoldsETFRenderState)blockEntityRenderState;
        holder.etf$initState((ETFEntity)blockEntity);
    }

    @ModifyExpressionValue(method={"extractBase"}, at={@At(value="INVOKE", target="Lnet/minecraft/util/LightCoordsUtil;getLightCoords(Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/core/BlockPos;)I")})
    private static int etf$vanillaLightOverride(int light, @Local(argsOnly=true) BlockEntityRenderState blockEntityRenderState) {
        return ETF.config().getConfig().getLightOverrideBE(light, ((HoldsETFRenderState)blockEntityRenderState).etf$getState());
    }
}

