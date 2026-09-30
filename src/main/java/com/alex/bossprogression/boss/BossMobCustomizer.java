package com.alex.bossprogression.boss;

import com.alex.bossprogression.BossProgressionMod;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Applied once before entity insertion. Vanilla entity saving persists names, attributes and equipment. */
public final class BossMobCustomizer {
    public static final String DAMAGE_BONUS = "bossprogression:extra_damage";
    private static final ResourceLocation ARMOR_BONUS = ResourceLocation.fromNamespaceAndPath("bossprogression", "extra_armor");
    private static final Set<String> WARNED = new HashSet<>();
    private BossMobCustomizer() {}

    public static boolean apply(Mob mob, BossDefinition definition) {
        return apply(mob, definition.mob(), definition.displayName(), definition.id(), definition.bossGlowing());
    }
    public static boolean apply(Mob mob, BossMobConfig config, Component displayName, ResourceLocation bossId, boolean glowing) {
        var uuid = mob.getUUID();
        var position = mob.position();
        try {
            if (!config.nbt().isEmpty()) {
                CompoundTag merged = mob.saveWithoutId(new CompoundTag());
                merged.merge(config.nbt().copy());
                mob.load(merged);
            }
            // Even custom entity readAdditionalSaveData cannot relocate or replace the encounter identity.
            mob.setUUID(uuid);
            mob.setPos(position.x, position.y, position.z);
            for (var entry : config.attributes().entrySet()) {
                var holder = BuiltInRegistries.ATTRIBUTE.getHolder(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.ATTRIBUTE, entry.getKey()));
                if (holder.isEmpty()) { warn(bossId, "Unknown attribute " + entry.getKey()); continue; }
                setBase(mob, holder.get(), entry.getValue(), bossId);
            }
            config.maxHealth().ifPresent(value -> setBase(mob, Attributes.MAX_HEALTH, value, bossId));
            var armor = mob.getAttribute(Attributes.ARMOR);
            if (armor != null) {
                armor.removeModifier(ARMOR_BONUS);
                if (config.extraArmor() > 0) armor.addPermanentModifier(new AttributeModifier(
                        ARMOR_BONUS, config.extraArmor(), AttributeModifier.Operation.ADD_VALUE));
            } else if (config.extraArmor() > 0) warn(bossId, "Mob does not support armor");
            Component name = config.name().<Component>map(Component::literal).orElseGet(() ->
                    mob.getCustomName() != null ? mob.getCustomName() : displayName);
            mob.setCustomName(name);
            mob.setCustomNameVisible(config.nameVisible());
            mob.setGlowingTag(glowing); // boss_glowing is authoritative even if raw NBT includes Glowing
            mob.setPersistenceRequired();
            mob.getPersistentData().putDouble(DAMAGE_BONUS, config.extraDamage());
            float health = config.maxHealth().isPresent() || !config.nbt().contains("Health") ? mob.getMaxHealth()
                    : Math.min(config.nbt().getFloat("Health"), mob.getMaxHealth());
            mob.setHealth(health);
            mob.refreshDimensions();
            mob.setPos(position.x, position.y, position.z);
            if (!Float.isFinite(mob.getHealth()) || mob.getHealth() <= 0) throw new IllegalArgumentException("Mob tuning produced invalid health");
            WARNED.remove(bossId + ":spawn");
            return true;
        } catch (RuntimeException e) {
            mob.setUUID(uuid);
            mob.setPos(position.x, position.y, position.z);
            if (WARNED.add(bossId + ":spawn")) BossProgressionMod.LOGGER.warn(
                    "Cannot apply mob settings to boss {}; encounter remains unactivated: {}", bossId, e.toString());
            return false;
        }
    }
    private static void setBase(Mob mob, Holder<Attribute> type, double value, ResourceLocation bossId) {
        var attribute = mob.getAttribute(type);
        if (attribute == null) { warn(bossId, "Mob does not support attribute " + type.getRegisteredName()); return; }
        double sanitized = type.value().sanitizeValue(value);
        if (sanitized != value) warn(bossId, "Clamped attribute " + type.getRegisteredName() + " from " + value + " to " + sanitized);
        attribute.setBaseValue(sanitized);
    }
    private static void warn(ResourceLocation id, String message) {
        if (WARNED.add(id + ":" + message)) BossProgressionMod.LOGGER.warn("Boss {}: {}", id, message);
    }
}
