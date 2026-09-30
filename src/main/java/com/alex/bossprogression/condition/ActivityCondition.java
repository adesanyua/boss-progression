package com.alex.bossprogression.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Shared counter schema; each activity has its own discriminator and target field. */
public final class ActivityCondition extends BossCondition {
    public enum Kind {
        OBTAIN_ITEM("item"), SUBMIT_ITEM("item"), CRAFT_ITEM("item"), MINE_BLOCK("block"),
        VISIT_BIOME("biome"), VISIT_DIMENSION("dimension"), ADVANCEMENT("advancement"), DEFEAT_BOSS("boss"),
        TRADE("item"), FISH("item");
        final String field;
        Kind(String field) { this.field = field; }
        public ResourceLocation type() { return ResourceLocation.fromNamespaceAndPath("bossprogression", name().toLowerCase(java.util.Locale.ROOT)); }
    }
    private final Kind kind;
    private final Optional<ResourceLocation> target;
    public ActivityCondition(String id, int count, Kind kind, Optional<ResourceLocation> target) {
        super(id, count); this.kind = kind; this.target = target;
    }
    public Kind kind() { return kind; }
    public Optional<ResourceLocation> target() { return target; }
    public boolean matches(ResourceLocation id) { return target.isEmpty() || target.get().equals(id); }
    @Override public ResourceLocation type() { return kind.type(); }
    public String label() { return kind.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + target.map(t -> ": " + t).orElse(""); }
    public static MapCodec<ActivityCondition> codec(Kind kind) {
        boolean single = kind == Kind.VISIT_BIOME || kind == Kind.VISIT_DIMENSION || kind == Kind.ADVANCEMENT || kind == Kind.DEFEAT_BOSS;
        return RecordCodecBuilder.<ActivityCondition>mapCodec(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(BossCondition::id),
                Codec.intRange(1, single ? 1 : Integer.MAX_VALUE).optionalFieldOf("required_count", 1).forGetter(BossCondition::requiredCount),
                ResourceLocation.CODEC.optionalFieldOf(kind.field).forGetter(ActivityCondition::target)
        ).apply(i, (id, count, target) -> new ActivityCondition(id, count, kind, target))).validate(c ->
                c.target.isPresent() || kind == Kind.TRADE || kind == Kind.FISH
                        ? com.mojang.serialization.DataResult.success(c)
                        : com.mojang.serialization.DataResult.error(() -> "Missing " + kind.field));
    }
}
