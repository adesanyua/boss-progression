package com.alex.bossprogression.dungeon;

import com.alex.bossprogression.boss.BossDefinition;
import com.alex.bossprogression.boss.BossMobConfig;
import com.alex.bossprogression.boss.BossMobCustomizer;
import com.alex.bossprogression.event.BossDamageEventHandler;
import com.alex.bossprogression.registry.ModAttachments;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("bossprogression")
@PrefixGameTestTemplate(false)
public final class BossMobTests {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("bossprogression", "mob_test");
    private static BossMobConfig config(String json) {
        return BossMobConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }
    @GameTest(template = "empty_test")
    public static void attributesAndNbtPersist(GameTestHelper helper) {
        Mob mob = EntityType.ZOMBIE.create(helper.getLevel());
        mob.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getCenter());
        var uuid = mob.getUUID();
        var pos = mob.position();
        double baseArmor = mob.getAttributeValue(Attributes.ARMOR);
        var settings = config("""
                {"name":"Crypt Warden","max_health":240,"extra_armor":8,"extra_damage":4,
                 "attributes":{"minecraft:generic.armor_toughness":6,"minecraft:generic.movement_speed":0.3},
                 "nbt":"{NoAI:1b,Silent:1b,Glowing:0b}"}
                """);
        helper.assertTrue(BossMobCustomizer.apply(mob, settings, Component.literal("Fallback"), ID, true), "Mob customization should succeed");
        helper.assertTrue(mob.getUUID().equals(uuid) && mob.position().equals(pos), "Mob identity/location must stay unchanged");
        helper.assertTrue(mob.getMaxHealth() == 240 && mob.getHealth() == 240, "Configured HP must start full");
        helper.assertTrue(mob.getAttributeValue(Attributes.ARMOR) == baseArmor + 8, "Armor bonus must be additive");
        helper.assertTrue(mob.isNoAi() && mob.isSilent() && mob.isCurrentlyGlowing() && mob.isPersistenceRequired(), "NBT and lifecycle flags must apply");
        helper.assertTrue(mob.getCustomName().getString().equals("Crypt Warden") && mob.isCustomNameVisible(), "Custom name must apply");
        helper.assertTrue(BossMobCustomizer.apply(mob, settings, Component.literal("Fallback"), ID, true), "Repeated application must succeed");
        helper.assertTrue(mob.getAttributeValue(Attributes.ARMOR) == baseArmor + 8, "Armor modifier must not duplicate");
        mob.setHealth(80);
        var saved = mob.saveWithoutId(new CompoundTag());
        Mob loaded = EntityType.ZOMBIE.create(helper.getLevel());
        loaded.load(saved);
        helper.assertTrue(loaded.getMaxHealth() == 240 && loaded.getHealth() == 80, "Reload must preserve HP without healing");
        helper.assertTrue(loaded.getAttributeValue(Attributes.ARMOR) == baseArmor + 8, "Armor must persist");
        helper.assertTrue(loaded.getPersistentData().getDouble(BossMobCustomizer.DAMAGE_BONUS) == 4, "Damage bonus must persist");
        helper.assertTrue(loaded.getCustomName().getString().equals("Crypt Warden"), "Name must persist");
        helper.succeed();
    }
    @GameTest(template = "empty_test")
    public static void rawEquipmentNameAndHealth(GameTestHelper helper) {
        var json = new com.google.gson.JsonObject();
        json.addProperty("nbt", """
                {attributes:[{id:'minecraft:generic.max_health',base:200d}],Health:150f,
                 ArmorItems:[{},{},{},{id:'minecraft:diamond_helmet',count:1}],
                 CustomName:'{"text":"Raw Warden","color":"gold"}'}
                """);
        Mob mob = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(BossMobCustomizer.apply(mob, BossMobConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(),
                Component.literal("Fallback"), ID, false), "Raw entity NBT must apply");
        helper.assertTrue(mob.getMaxHealth() == 200 && mob.getHealth() == 150, "Raw attributes/Health must use 1.21.1 formats");
        helper.assertTrue(mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.DIAMOND_HELMET), "Raw equipment must load");
        helper.assertTrue(mob.getCustomName().getString().equals("Raw Warden") && mob.getCustomName().getStyle().getColor() != null, "Styled raw name must survive");
        helper.succeed();
    }
    @GameTest(template = "empty_test")
    public static void validationAndLegacyDefaults(GameTestHelper helper) {
        var legacy = BossDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"id":"bossprogression:legacy","display_name":"Legacy","boss_entity":"minecraft:zombie","conditions":[]}
                """)).getOrThrow();
        helper.assertTrue(legacy.mob().maxHealth().isEmpty() && legacy.mob().extraArmor() == 0
                && legacy.mob().extraDamage() == 0 && legacy.mob().nbt().isEmpty(), "Old JSON must keep vanilla attributes");
        for (String bad : new String[] {"{UUID:[I;1,2,3,4]}", "{Pos:[0d,0d,0d]}", "{NeoForgeData:{}}", "{Passengers:[]}", "{Health:0f}", "{broken"})
            helper.assertTrue(BossMobConfig.NBT_CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonPrimitive(bad)).error().isPresent(), "Unsafe/invalid NBT must be rejected: " + bad);
        helper.assertTrue(BossMobConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"max_health\":-1}")).error().isPresent(), "Invalid HP must be rejected");
        Mob mob = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(BossMobCustomizer.apply(mob, config("{\"attributes\":{\"minecraft:unknown_test_attribute\":2}}"),
                Component.literal("Fallback"), ID, false), "Unknown attribute must not crash or cancel valid spawn");
        helper.succeed();
    }
    @GameTest(template = "empty_test")
    public static void bonusDamageRequiresOwnedEncounter(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob boss = EntityType.EVOKER.create(level);
        BossMobCustomizer.apply(boss, config("{\"extra_damage\":4}"), Component.literal("Evoker"), ID, false);
        var store = level.getData(ModAttachments.DUNGEON_STORE.get());
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var instance = new DungeonInstance(UUID.randomUUID(), ID, level.dimension(), pos, UUID.randomUUID(), pos, 24, "mob-test:" + pos, null);
        instance.setActivated(true);
        instance.setBossUuid(boss.getUUID());
        store.put(instance);
        level.setData(ModAttachments.DUNGEON_STORE.get(), store);
        try {
            var victim = EntityType.ZOMBIE.create(level);
            var handler = new BossDamageEventHandler();
            var melee = new LivingIncomingDamageEvent(victim, new DamageContainer(level.damageSources().mobAttack(boss), 3));
            handler.damage(melee);
            helper.assertTrue(melee.getAmount() == 7, "Melee bonus must apply once before armor");
            var projectile = EntityType.ARROW.create(level);
            var spell = new LivingIncomingDamageEvent(victim, new DamageContainer(level.damageSources().mobProjectile(projectile, boss), 3));
            handler.damage(spell);
            helper.assertTrue(spell.getAmount() == 7, "Owned projectile bonus must apply");
            var other = EntityType.ZOMBIE.create(level);
            other.getPersistentData().putDouble(BossMobCustomizer.DAMAGE_BONUS, 4);
            var ordinary = new LivingIncomingDamageEvent(victim, new DamageContainer(level.damageSources().mobAttack(other), 3));
            handler.damage(ordinary);
            helper.assertTrue(ordinary.getAmount() == 3, "Untracked mob must not gain boss damage");
            instance.setDefeated(true);
            var deadBoss = new LivingIncomingDamageEvent(victim, new DamageContainer(level.damageSources().mobAttack(boss), 3));
            handler.damage(deadBoss);
            helper.assertTrue(deadBoss.getAmount() == 3, "Completed encounter must not add damage");
            helper.succeed();
        } finally {
            store.instances().remove(instance.key());
            level.setData(ModAttachments.DUNGEON_STORE.get(), store);
        }
    }
}
