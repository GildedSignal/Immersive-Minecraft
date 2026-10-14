/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.network.chat.Component
 *  net.minecraft.util.Mth
 *  net.minecraft.util.ProblemReporter
 *  net.minecraft.world.Nameable
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.Pose
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.entity.BlockEntityType
 *  net.minecraft.world.level.storage.TagValueOutput
 *  net.minecraft.world.level.storage.ValueOutput
 *  net.minecraft.world.phys.Vec3
 *  net.minecraft.world.scores.Team
 *  org.jetbrains.annotations.Nullable
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 */
package traben.entity_texture_features.mixin.mixins.entity.misc;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import traben.entity_texture_features.ETFApi;
import traben.entity_texture_features.features.property_reading.properties.optifine_properties.NBTProperty;
import traben.entity_texture_features.features.state.ETFEntityRenderState;
import traben.entity_texture_features.features.state.ETFFrameNbtCache;
import traben.entity_texture_features.features.state.ETFState;
import traben.entity_texture_features.utils.ETFEntity;
import traben.entity_texture_features.utils.ETFUtils2;

@Mixin(value={BlockEntity.class})
public abstract class MixinBlockEntity
implements ETFEntity {
    @Unique
    private UUID etf$id = null;

    @Shadow
    public abstract BlockEntityType<?> getType();

    @Shadow
    public abstract BlockPos getBlockPos();

    @Shadow
    @Nullable
    public abstract Level getLevel();

    @Shadow
    protected abstract void saveMetadata(ValueOutput var1);

    @Override
    public EntityType<?> etf$getType() {
        return null;
    }

    @Override
    public UUID etf$getUuid() {
        if (this.etf$id == null) {
            this.etf$id = ETFApi.getUUIDForBlockEntity((BlockEntity)(Object)this);
        }
        return this.etf$id;
    }

    @Override
    public int etf$getOptifineId() {
        BlockPos pos = this.etf$getBlockPos();
        int hash = ETFUtils2.optifineHashing(37);
        hash = ETFUtils2.optifineHashing(hash + pos.getX());
        hash = ETFUtils2.optifineHashing(hash + pos.getZ());
        return ETFUtils2.optifineHashing(hash + pos.getY());
    }

    @Override
    public int etf$getOptifineVehicleId() {
        return this.etf$getOptifineId();
    }

    @Override
    public Level etf$getWorld() {
        return this.getLevel();
    }

    @Override
    public BlockPos etf$getBlockPos() {
        return this.getBlockPos();
    }

    @Override
    public int etf$getBlockY() {
        return this.getBlockPos().getY();
    }

    @Override
    public CompoundTag etf$getNbt() {
        ETFEntityRenderState state = ETFState.state();
        if (state == null) {
            return NBTProperty.INTENTIONAL_FAILURE;
        }
        return ETFFrameNbtCache.cache(state, this.etf$getUuid(), () -> {
            TagValueOutput compound = TagValueOutput.createWithoutContext((ProblemReporter)ProblemReporter.DISCARDING);
            this.saveMetadata((ValueOutput)compound);
            return compound.buildResult();
        }, this);
    }

    @Override
    public boolean etf$hasCustomName() {
        Nameable name;
        MixinBlockEntity mixinBlockEntity = this;
        return mixinBlockEntity instanceof Nameable && (name = (Nameable)mixinBlockEntity).hasCustomName();
    }

    @Override
    public Component etf$getCustomName() {
        Nameable name;
        MixinBlockEntity mixinBlockEntity = this;
        return mixinBlockEntity instanceof Nameable && (name = (Nameable)mixinBlockEntity).hasCustomName() ? name.getCustomName() : Component.nullToEmpty((String)"null");
    }

    @Override
    public Team etf$getScoreboardTeam() {
        return null;
    }

    @Override
    public Iterable<ItemStack> etf$getItemsEquipped() {
        return null;
    }

    @Override
    public Iterable<ItemStack> etf$getHandItems() {
        return null;
    }

    @Override
    public Iterable<ItemStack> etf$getArmorItems() {
        return null;
    }

    @Override
    public float etf$distanceTo(Entity entity) {
        BlockPos pos = this.getBlockPos();
        float f = (float)((double)pos.getX() - entity.getX());
        float g = (float)((double)pos.getY() - entity.getY());
        float h = (float)((double)pos.getZ() - entity.getZ());
        return Mth.sqrt((float)(f * f + g * g + h * h));
    }

    @Override
    public Vec3 etf$getVelocity() {
        return Vec3.ZERO;
    }

    @Override
    public Pose etf$getPose() {
        return Pose.STANDING;
    }

    @Override
    public boolean etf$canBeBright() {
        return false;
    }

    @Override
    public boolean etf$isBlockEntity() {
        return true;
    }

    @Override
    public String etf$getEntityKey() {
        return ETFApi.getBlockEntityTypeToTranslationKey(this.getType());
    }
}

