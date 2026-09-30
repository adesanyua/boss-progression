package com.alex.bossprogression.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;

/** Optional server-owned mob tuning; defaults preserve vanilla attributes. */
public record BossMobConfig(Optional<String> name, boolean nameVisible, Optional<Double> maxHealth,
                            double extraArmor, double extraDamage, Map<ResourceLocation, Double> attributes, CompoundTag nbt) {
    public static final BossMobConfig DEFAULT = new BossMobConfig(Optional.empty(), true, Optional.empty(), 0, 0, Map.of(), new CompoundTag());
    private static final Set<String> RESERVED = Set.of("id", "UUID", "UUIDMost", "UUIDLeast", "Pos", "Motion", "Dimension",
            "Passengers", "Riding", "Leash", "Team", "PersistenceRequired", "PortalCooldown", "DeathTime", "HurtTime",
            "HurtByTimestamp", "FallDistance", "OnGround", "NeoForgeData", "ForgeData", "neoforge:attachments");
    private static Codec<Double> number(double min, double max) {
        return Codec.DOUBLE.validate(v -> Double.isFinite(v) && v >= min && v <= max ? DataResult.success(v)
                : DataResult.error(() -> "Value must be finite and between " + min + " and " + max));
    }
    public static final Codec<CompoundTag> NBT_CODEC = Codec.STRING.comapFlatMap(BossMobConfig::parseNbt, CompoundTag::toString);
    public static final Codec<BossMobConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.validate(s -> s.length() <= 256 ? DataResult.success(s) : DataResult.error(() -> "Boss name exceeds 256 characters"))
                    .optionalFieldOf("name").forGetter(BossMobConfig::name),
            Codec.BOOL.optionalFieldOf("name_visible", true).forGetter(BossMobConfig::nameVisible),
            number(1, 1024).optionalFieldOf("max_health").forGetter(BossMobConfig::maxHealth),
            number(0, 30).optionalFieldOf("extra_armor", 0.0).forGetter(BossMobConfig::extraArmor),
            number(0, 2048).optionalFieldOf("extra_damage", 0.0).forGetter(BossMobConfig::extraDamage),
            Codec.unboundedMap(ResourceLocation.CODEC, number(-1048576, 1048576)).optionalFieldOf("attributes", Map.of()).forGetter(BossMobConfig::attributes),
            NBT_CODEC.optionalFieldOf("nbt", new CompoundTag()).forGetter(BossMobConfig::nbt)
    ).apply(i, BossMobConfig::new));

    public BossMobConfig {
        attributes = Map.copyOf(attributes);
        nbt = nbt.copy();
    }
    private static DataResult<CompoundTag> parseNbt(String snbt) {
        if (snbt.length() > 32768) return DataResult.error(() -> "Mob SNBT exceeds 32768 characters");
        if (!withinDepth(snbt)) return DataResult.error(() -> "Mob SNBT nesting exceeds 32");
        try {
            CompoundTag tag = TagParser.parseTag(snbt);
            for (String key : RESERVED) if (tag.contains(key)) return DataResult.error(() -> "Mob NBT field is encounter-managed and forbidden: " + key);
            if (!finite(tag)) return DataResult.error(() -> "Mob NBT contains a non-finite number or an excessive embedded JSON string");
            if (tag.contains("Health") && (!(tag.get("Health") instanceof NumericTag value) || value.getAsDouble() <= 0))
                return DataResult.error(() -> "Health must be a positive finite number");
            return DataResult.success(tag);
        } catch (Exception e) {
            return DataResult.error(() -> "Invalid mob SNBT: " + e.getMessage());
        }
    }
    private static boolean withinDepth(String text) {
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') quote = c;
            else if (c == '{' || c == '[') { if (++depth > 32) return false; }
            else if (c == '}' || c == ']') depth--;
        }
        return true;
    }
    private static boolean finite(Tag tag) {
        if (tag instanceof StringTag value) {
            String text = value.getAsString();
            return text.length() <= 8192 && withinDepth(text);
        }
        if (tag instanceof NumericTag number) return Double.isFinite(number.getAsDouble());
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) if (!finite(compound.get(key))) return false;
        } else if (tag instanceof ListTag list) {
            for (Tag element : list) if (!finite(element)) return false;
        }
        return true;
    }
}
