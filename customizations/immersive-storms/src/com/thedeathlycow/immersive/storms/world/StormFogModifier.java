/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.seibel.distanthorizons.api.DhApi$Delayed
 *  com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig
 *  net.minecraft.client.DeltaTracker
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.client.renderer.fog.FogData
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.BlockPos$MutableBlockPos
 *  net.minecraft.core.Holder
 *  net.minecraft.util.ARGB
 *  net.minecraft.util.Mth
 *  net.minecraft.world.attribute.GaussianSampler
 *  net.minecraft.world.attribute.GaussianSampler$Accumulator
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.biome.Biome
 *  net.minecraft.world.phys.Vec3
 *  org.jetbrains.annotations.Nullable
 *  org.joml.Vector2f
 *  org.joml.Vector2fc
 *  org.joml.Vector3f
 *  org.joml.Vector3fc
 */
package com.thedeathlycow.immersive.storms.world;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig;
import com.thedeathlycow.immersive.storms.ImmersiveStormsClient;
import com.thedeathlycow.immersive.storms.config.section.ImmersiveStormsConfig;
import com.thedeathlycow.immersive.storms.mixin.client.LevelAccessor;
import com.thedeathlycow.immersive.storms.util.WeatherEffectType;
import com.thedeathlycow.immersive.storms.util.WeatherEffects;
import com.thedeathlycow.immersive.storms.util.WeatherEffectsClient;
import com.thedeathlycow.immersive.storms.world.WeightedVector2fAccumulator;
import com.thedeathlycow.immersive.storms.world.WeightedVector3fAccumulator;
import java.util.function.Function;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.GaussianSampler;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class StormFogModifier {
    private static final boolean CACHE = !"false".equalsIgnoreCase(System.getProperty("immersive_storms.fogColorCache", "true"));
    private static final int MAX_SAMPLES = 216;
    private static WeatherEffectType[] cachedTypes;
    private static double[] cachedWeights;
    private static int cachedCount;
    private static long cachedGameTime;
    private static long cachedPosXBits;
    private static long cachedPosYBits;
    private static long cachedPosZBits;

    public static int sampleWeatherFogColor(ClientLevel clientLevel, Vec3 vec3, float f, int n) {
        float f2 = clientLevel.getRainLevel(f);
        float f3 = clientLevel.getThunderLevel(f);
        Vector3f vector3f = ARGB.vector3fFromRGB24((int)n);
        if (!CACHE) {
            return StormFogModifier.sampleUncached(clientLevel, vec3, f2, f3, (Vector3fc)vector3f);
        }
        StormFogModifier.ensureTypeSamples(clientLevel, vec3);
        WeightedVector3fAccumulator weightedVector3fAccumulator = new WeightedVector3fAccumulator();
        for (int i = 0; i < cachedCount; ++i) {
            weightedVector3fAccumulator.accumulate(cachedWeights[i], cachedTypes[i].getFogColor((Vector3fc)vector3f, f2, f3));
        }
        return weightedVector3fAccumulator.getPackedColor();
    }

    static int sampleUncached(ClientLevel clientLevel, Vec3 vec3, float f, float f2, Vector3fc vector3fc) {
        WeightedVector3fAccumulator weightedVector3fAccumulator = new WeightedVector3fAccumulator();
        GaussianSampler.sample((Vec3)vec3.scale(0.25), (n, n2, n3) -> {
            Holder holder2 = clientLevel.getNoiseBiome(n, n2, n3);
            WeatherEffectType weatherEffectType2 = WeatherEffectType.forBiome((Holder<Biome>)holder2, (weatherEffectType, holder) -> ImmersiveStormsClient.getConfig().isEnabled((WeatherEffectType)((Object)((Object)weatherEffectType))) && WeatherEffectsClient.typeAffectsBiome(weatherEffectType, (Holder<Biome>)holder));
            return weatherEffectType2.getFogColor(vector3fc, f, f2);
        }, (GaussianSampler.Accumulator)weightedVector3fAccumulator);
        return weightedVector3fAccumulator.getPackedColor();
    }

    private static void ensureTypeSamples(ClientLevel clientLevel, Vec3 vec3) {
        long l = clientLevel.getGameTime();
        long l2 = Double.doubleToRawLongBits(vec3.x);
        long l3 = Double.doubleToRawLongBits(vec3.y);
        long l4 = Double.doubleToRawLongBits(vec3.z);
        if (cachedTypes != null && cachedCount > 0 && l == cachedGameTime && l2 == cachedPosXBits && l3 == cachedPosYBits && l4 == cachedPosZBits) {
            return;
        }
        if (cachedTypes == null) {
            cachedTypes = new WeatherEffectType[216];
            cachedWeights = new double[216];
        }
        cachedCount = 0;
        GaussianSampler.sample((Vec3)vec3.scale(0.25), (n, n2, n3) -> {
            Holder holder2 = clientLevel.getNoiseBiome(n, n2, n3);
            return WeatherEffectType.forBiome((Holder<Biome>)holder2, (weatherEffectType, holder) -> ImmersiveStormsClient.getConfig().isEnabled((WeatherEffectType)((Object)((Object)weatherEffectType))) && WeatherEffectsClient.typeAffectsBiome(weatherEffectType, (Holder<Biome>)holder));
        }, (d, weatherEffectType) -> {
            StormFogModifier.cachedWeights[StormFogModifier.cachedCount] = d;
            StormFogModifier.cachedTypes[StormFogModifier.cachedCount] = weatherEffectType;
            ++cachedCount;
        });
        cachedGameTime = l;
        cachedPosXBits = l2;
        cachedPosYBits = l3;
        cachedPosZBits = l4;
    }

    static void clearColorCacheForTest() {
        cachedCount = 0;
        cachedGameTime = Long.MIN_VALUE;
    }

    public static void applyStartEndModifier(FogData fogData, Vec3 vec3, ClientLevel clientLevel, DeltaTracker deltaTracker) {
        ImmersiveStormsConfig immersiveStormsConfig = ImmersiveStormsClient.getConfig();
        float f = deltaTracker.getGameTimeDeltaPartialTick(false);
        float f2 = clientLevel.getRainLevel(f);
        float f3 = clientLevel.getThunderLevel(f);
        Vector2f vector2f = new Vector2f(fogData.environmentalStart, fogData.environmentalEnd);
        Vector2fc vector2fc = StormFogModifier.lerpFogDistance(vec3, (Level)clientLevel, (Vector2fc)vector2f, WeatherEffectType::getRainWeatherData);
        Vector2fc vector2fc2 = f3 > 0.0f ? StormFogModifier.lerpFogDistance(vec3, (Level)clientLevel, (Vector2fc)vector2f, WeatherEffectType::getThunderWeatherData) : null;
        StormFogModifier.updateFogRadius(fogData, vector2fc, vector2fc2, f2, f3, immersiveStormsConfig);
    }

    public static boolean shouldApply(Level level) {
        ImmersiveStormsConfig immersiveStormsConfig = ImmersiveStormsClient.getConfig();
        return immersiveStormsConfig.isEnableFogChanges() && level.getRainLevel(1.0f) > 0.0f && ((LevelAccessor)level).invokeCanHaveWeather();
    }

    private static Vector2fc lerpFogDistance(Vec3 vec3, Level level, Vector2fc vector2fc, Function<WeatherEffectType, WeatherEffectType.WeatherData> function) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        WeightedVector2fAccumulator weightedVector2fAccumulator = new WeightedVector2fAccumulator();
        int n = level.getSeaLevel();
        GaussianSampler.sample((Vec3)vec3, (n2, n3, n4) -> {
            mutableBlockPos.set(n2, n3, n4);
            if (n3 < n) {
                return vector2fc;
            }
            WeatherEffectType weatherEffectType2 = WeatherEffects.getCurrentType(level, (BlockPos)mutableBlockPos, false, (weatherEffectType, holder) -> ImmersiveStormsClient.getConfig().isEnabled((WeatherEffectType)((Object)((Object)weatherEffectType))) && WeatherEffectsClient.typeAffectsBiome(weatherEffectType, (Holder<Biome>)holder));
            WeatherEffectType.WeatherData weatherData = (WeatherEffectType.WeatherData)function.apply(weatherEffectType2);
            return weatherData != null ? weatherData.fogDistance() : vector2fc;
        }, (GaussianSampler.Accumulator)weightedVector2fAccumulator);
        return weightedVector2fAccumulator.getAverageVector();
    }

    private static void updateFogRadius(FogData fogData, Vector2fc vector2fc, @Nullable Vector2fc vector2fc2, float f, float f2, ImmersiveStormsConfig immersiveStormsConfig) {
        float f3;
        float f4 = Mth.lerp((float)f, (float)fogData.environmentalEnd, (float)vector2fc.y());
        if (vector2fc2 != null) {
            f4 = Mth.lerp((float)f2, (float)f4, (float)vector2fc2.y());
        }
        if ((f3 = (f4 *= immersiveStormsConfig.getFogDistanceMultiplier()) / fogData.environmentalEnd) < 1.0f) {
            fogData.environmentalEnd = f4;
            fogData.skyEnd *= f3;
            fogData.cloudEnd *= f3;
        }
        if (ImmersiveStormsClient.isDistantHorizonsLoaded()) {
            StormFogModifier.setFogDistanceForDistantHorizons(f3);
        }
    }

    private static void setFogDistanceForDistantHorizons(float f) {
        IDhApiConfig iDhApiConfig = DhApi.Delayed.configs;
        if (iDhApiConfig != null) {
            if (f < 0.99f) {
                iDhApiConfig.graphics().fog().farFog().farFogMinThickness().setValue(Float.valueOf(1.0f - f * 0.1f));
                iDhApiConfig.graphics().fog().farFog().farFogMaxThickness().setValue(Float.valueOf(1.0f));
                iDhApiConfig.graphics().fog().enableVanillaFog().setValue(true);
            } else {
                iDhApiConfig.graphics().fog().farFog().farFogMinThickness().clearValue();
                iDhApiConfig.graphics().fog().farFog().farFogMaxThickness().clearValue();
                iDhApiConfig.graphics().fog().enableVanillaFog().clearValue();
            }
        }
    }

    private StormFogModifier() {
    }

    static {
        cachedGameTime = Long.MIN_VALUE;
    }
}

