/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap
 *  net.fabricmc.fabric.api.tag.client.v1.ClientTags
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.core.Holder
 *  net.minecraft.core.Holder$Reference
 *  net.minecraft.core.MappedRegistry
 *  net.minecraft.core.Registry
 *  net.minecraft.core.RegistryAccess
 *  net.minecraft.tags.TagKey
 *  net.minecraft.world.level.biome.Biome
 */
package com.thedeathlycow.immersive.storms.util;

import com.thedeathlycow.immersive.storms.config.section.BiomeConfig;
import com.thedeathlycow.immersive.storms.util.WeatherEffectType;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Optional;
import net.fabricmc.fabric.api.tag.client.v1.ClientTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

public final class WeatherEffectsClient {
    private static final VarHandle ALL_TAGS = WeatherEffectsClient.findAllTagsHandle();
    private static final Reference2ObjectOpenHashMap<TagKey<?>, Reference2ObjectOpenHashMap<Holder<?>, Boolean>> TAG_CACHE = new Reference2ObjectOpenHashMap();
    private static ClientLevel cachedLevel;
    private static RegistryAccess cachedRegistryAccess;
    private static Registry<?> cachedRegistry;
    private static Object cachedTagSet;

    public static boolean typeAffectsBiome(WeatherEffectType type, Holder<Biome> biome) {
        return type.getBiomeTag() != null && !BiomeConfig.getConfig().isBiomeExcluded(biome) && (WeatherEffectsClient.isInWithLocalFallbackCached(type.getBiomeTag(), biome) || BiomeConfig.getConfig().isIncluded(type, biome));
    }

    static boolean isInWithLocalFallbackCached(TagKey<Biome> tag, Holder<Biome> biome) {
        ClientLevel level;
        Minecraft minecraft;
        if (ALL_TAGS != null && biome instanceof Holder.Reference && (minecraft = Minecraft.getInstance()) != null && minecraft.isSameThread() && (level = minecraft.level) != null && WeatherEffectsClient.validateCache(level, tag)) {
            Boolean cached;
            Reference2ObjectOpenHashMap perTag = (Reference2ObjectOpenHashMap)TAG_CACHE.get(tag);
            if (perTag == null) {
                perTag = new Reference2ObjectOpenHashMap();
                TAG_CACHE.put(tag, perTag);
            }
            if ((cached = (Boolean)perTag.get(biome)) != null) {
                return cached;
            }
            boolean result = ClientTags.isInWithLocalFallback(tag, biome);
            perTag.put(biome, (Object)result);
            return result;
        }
        return ClientTags.isInWithLocalFallback(tag, biome);
    }

    private static boolean validateCache(ClientLevel level, TagKey<Biome> tag) {
        RegistryAccess registryAccess = level.registryAccess();
        if (registryAccess == null) {
            WeatherEffectsClient.invalidate(null, null, null, null);
            return false;
        }
        if (level != cachedLevel || registryAccess != cachedRegistryAccess) {
            Optional registry = registryAccess.lookup(tag.registry());
            Registry resolved = registry.isPresent() && registry.get() instanceof MappedRegistry ? (Registry)registry.get() : null;
            WeatherEffectsClient.invalidate(level, registryAccess, resolved, resolved != null ? ALL_TAGS.get((MappedRegistry)resolved) : null);
        }
        if (cachedRegistry == null || cachedRegistry.key() != tag.registry()) {
            return false;
        }
        Object tagSet = ALL_TAGS.get((MappedRegistry)cachedRegistry);
        if (tagSet != cachedTagSet) {
            TAG_CACHE.clear();
            cachedTagSet = tagSet;
        }
        return true;
    }

    private static void invalidate(ClientLevel level, RegistryAccess registryAccess, Registry<?> registry, Object tagSet) {
        TAG_CACHE.clear();
        cachedLevel = level;
        cachedRegistryAccess = registryAccess;
        cachedRegistry = registry;
        cachedTagSet = tagSet;
    }

    private static VarHandle findAllTagsHandle() {
        try {
            Class<?> tagSetType = Class.forName("net.minecraft.core.MappedRegistry$TagSet", false, MappedRegistry.class.getClassLoader());
            return MethodHandles.privateLookupIn(MappedRegistry.class, MethodHandles.lookup()).findVarHandle(MappedRegistry.class, "allTags", tagSetType);
        }
        catch (Throwable t) {
            return null;
        }
    }

    private WeatherEffectsClient() {
    }
}

