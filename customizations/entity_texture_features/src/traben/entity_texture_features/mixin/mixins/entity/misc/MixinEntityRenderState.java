/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.entity.state.EntityRenderState
 *  org.jetbrains.annotations.NotNull
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 */
package traben.entity_texture_features.mixin.mixins.entity.misc;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import traben.entity_texture_features.features.state.ETFEntityRenderState;
import traben.entity_texture_features.features.state.HoldsETFRenderState;
import traben.entity_texture_features.utils.ETFEntity;

@Mixin(value={EntityRenderState.class})
public class MixinEntityRenderState
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
        this.etf$state.setVanillaState((EntityRenderState)this);
    }
}

