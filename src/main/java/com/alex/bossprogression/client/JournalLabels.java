package com.alex.bossprogression.client;

import com.alex.bossprogression.boss.BossRegistry;
import com.alex.bossprogression.condition.*;
import com.alex.bossprogression.reward.*;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Client-only display names; never show registry IDs as player-facing titles. */
final class JournalLabels {
    private JournalLabels() {}
    static Component name(String category, ResourceLocation id) {
        if (category.equals("item") && BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.get(id).getDescription();
        if (category.equals("block") && BuiltInRegistries.BLOCK.containsKey(id)) return BuiltInRegistries.BLOCK.get(id).getName();
        if (category.equals("entity") && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return BuiltInRegistries.ENTITY_TYPE.get(id).getDescription();
        if (category.equals("boss") && BossRegistry.clientGet(id) != null) return BossRegistry.clientGet(id).displayName();
        if (category.equals("advancement") && net.minecraft.client.Minecraft.getInstance().getConnection() != null) {
            var node = net.minecraft.client.Minecraft.getInstance().getConnection().getAdvancements().getTree().get(id);
            if (node != null && node.advancement().display().isPresent()) return node.advancement().display().get().getTitle();
        }
        String key = category + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(readable(id.getPath()));
    }
    static String readable(String value) {
        StringBuilder result = new StringBuilder();
        for (String word : value.replace('_', ' ').replace('/', ' ').split(" +")) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
    static Component condition(BossCondition condition) {
        if (condition instanceof KillCondition kill) return Component.translatable("journal.bossprogression.kill", name("entity", kill.targetType()));
        if (condition instanceof KillTagCondition tag) return Component.translatable("journal.bossprogression.kill", name("tag", tag.tagId()));
        if (condition instanceof ChestLootedCondition) return Component.translatable("journal.bossprogression.loot_chest");
        if (condition instanceof ActivityCondition activity) {
            String category = switch (activity.kind()) {
                case MINE_BLOCK -> "block";
                case VISIT_BIOME -> "biome";
                case VISIT_DIMENSION -> "dimension";
                case ADVANCEMENT -> "advancement";
                case DEFEAT_BOSS -> "boss";
                default -> "item";
            };
            Component target = activity.target().map(id -> name(category, id)).orElse(Component.translatable("journal.bossprogression.any_item"));
            return Component.translatable("journal.bossprogression." + activity.kind().name().toLowerCase(java.util.Locale.ROOT), target);
        }
        return Component.literal(readable(condition.id()));
    }
    static Component reward(BossReward reward) {
        if (reward instanceof ItemReward item) return Component.translatable("journal.bossprogression.item_reward", item.count(), name("item", item.item()));
        if (reward instanceof ExperienceReward xp) return Component.translatable("journal.bossprogression.xp_reward", xp.amount());
        return Component.literal(reward.label());
    }
    static Component filters(KillFilters filters) {
        var text = Component.empty();
        filters.dimension().ifPresent(id -> text.append(Component.translatable("journal.bossprogression.dimension", name("dimension", id))).append("; "));
        filters.biome().ifPresent(id -> text.append(Component.translatable("journal.bossprogression.biome", name("biome", id))).append("; "));
        filters.time().ifPresent(time -> text.append(Component.translatable("journal.bossprogression.time", Component.translatable("journal.bossprogression." + time))).append("; "));
        filters.weaponTag().ifPresent(id -> text.append(Component.translatable("journal.bossprogression.weapon", name("tag", id))));
        return text;
    }
}
