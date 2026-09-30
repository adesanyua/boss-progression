package com.alex.bossprogression.condition;

import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Registry of {@link BossCondition} codec types, keyed by discriminator id (e.g.
 * {@code bossprogression:kill_entity}).
 *
 * <p>This is what makes the JSON content extensible: a new condition kind is added by registering a
 * codec here, without touching the loader or the boss schema. Unknown condition types are surfaced as
 * a decode error (see {@link BossCondition#CODEC}) so a malformed/foreign boss file is skipped with a
 * warning instead of crashing the whole datapack load.
 */
public final class ConditionRegistry {
    private static final Map<ResourceLocation, MapCodec<? extends BossCondition>> CODECS = new HashMap<>();

    private ConditionRegistry() {
    }

    /** Registers a condition codec under its discriminator. Called once for each built-in type. */
    public static void register(ResourceLocation type, MapCodec<? extends BossCondition> codec) {
        CODECS.put(type, codec);
    }

    /**
     * Resolves the codec for a discriminator string. Throws on unknown type so the enclosing
     * {@code DataResult} turns it into a per-file decode failure the loader can log and skip.
     */
    public static MapCodec<? extends BossCondition> byDiscriminator(String discriminator) {
        ResourceLocation type = ResourceLocation.tryParse(discriminator);
        MapCodec<? extends BossCondition> codec = type == null ? null : CODECS.get(type);
        if (codec == null) {
            throw new IllegalStateException("Unknown boss condition type: " + discriminator);
        }
        return codec;
    }

    /** Registers the shipped condition types. Must run before any boss JSON is decoded. */
    public static void registerBuiltIns() {
        register(KillCondition.TYPE, KillCondition.CODEC);
        register(KillTagCondition.TYPE, KillTagCondition.CODEC);
        register(ChestLootedCondition.TYPE, ChestLootedCondition.CODEC);
        for (ActivityCondition.Kind kind : ActivityCondition.Kind.values()) register(kind.type(), ActivityCondition.codec(kind));
    }
}
