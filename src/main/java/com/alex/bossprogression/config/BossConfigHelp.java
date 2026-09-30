package com.alex.bossprogression.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

/** Inline help stays in user-facing JSONC rather than relying on external documentation alone. */
final class BossConfigHelp {
    private static final Pattern KEY = Pattern.compile("^(\\s*)\"([^\"]+)\"\\s*:");
    private static final Map<String, String> HELP = Map.ofEntries(
            Map.entry("id", "Boss ID: namespace:name. Condition ID: a unique string within conditions. Avoid changing an existing boss ID."),
            Map.entry("display_name", "Name in the Journal, invitation and prerequisite labels. The entity name is configured separately in mob.name."),
            Map.entry("boss_entity", "Entity type ID, e.g. minecraft:evoker, minecraft:elder_guardian, minecraft:wither or an installed mod's entity."),
            Map.entry("conditions", "All requirements must be completed (AND). Supported types, examples and optional filters are listed above."),
            Map.entry("type", "Type prefixed with bossprogression:. In conditions: requirement type. In rewards: item or experience."),
            Map.entry("tag", "Entity type tag ID, e.g. bossprogression:illagers. This is not the ID of a single entity."),
            Map.entry("entity", "Entity ID for kill_entity, e.g. minecraft:skeleton."),
            Map.entry("required_count", "Required number of actions; a positive integer. Visits, advancement and defeat_boss require exactly 1."),
            Map.entry("boss", "Boss ID the encounter owner must defeat, e.g. bossprogression:evoker."),
            Map.entry("item", "Item ID for a requirement or reward, e.g. minecraft:emerald. Optional for trade/fish."),
            Map.entry("block", "Block ID for mine_block, e.g. minecraft:obsidian."),
            Map.entry("biome", "Biome ID for visit_biome or dungeon.mode=biome placement."),
            Map.entry("advancement", "Advancement ID, e.g. minecraft:story/enter_the_nether."),
            Map.entry("filters", "Optional kill filters: dimension, biome, time (day/night), weapon_tag (melee only)."),
            Map.entry("loot_table", "Optional loot_chest filter: loot table ID, e.g. minecraft:chests/shipwreck_treasure."),
            Map.entry("dungeon", "Encounter placement: structure, nbt or biome. Optional parameters/examples are listed above."),
            Map.entry("mode", "structure = existing structure; nbt = custom file; biome = open-world site. procedural is the legacy arena mode."),
            Map.entry("dimension", "Dimension ID, e.g. minecraft:overworld, minecraft:the_nether or minecraft:the_end."),
            Map.entry("structure", "Worldgen structure ID: minecraft:mansion, minecraft:monument, minecraft:fortress, etc. Only for mode=structure."),
            Map.entry("search_radius_chunks", "Structure/biome search radius in chunks: 1-128. One chunk is 16 blocks wide."),
            Map.entry("activation_radius", "Boss activation distance from the encounter point: 4-128 blocks."),
            Map.entry("nbt", "In mob: entity SNBT string. In dungeon: relative file path under config/bossprogression/structures/. Examples above."),
            Map.entry("spawn_offset", "NBT spawn point offset from the template corner: [x,y,z]."),
            Map.entry("min_distance", "Minimum NBT/biome/procedural distance: 0-8192 blocks; must be less than max_distance."),
            Map.entry("max_distance", "Maximum distance: 1-8192 blocks; must be greater than min_distance."),
            Map.entry("destruction", "Gradual collapse after victory, structure/NBT only. Terrain, foundation and player edits are protected."),
            Map.entry("destroy_after_defeat", "true enables collapse after victory; false leaves the building intact."),
            Map.entry("delay_ticks", "Collapse delay: 0-72000 ticks; 20 ticks is approximately 1 second."),
            Map.entry("blocks_per_tick", "Collapse processing rate: 1-128 blocks/tick (the shared server budget may slow it down)."),
            Map.entry("structure_block_tag", "Allowed structure-material block tag; default bossprogression:structure_cleanup."),
            Map.entry("protected_block_tag", "Protected terrain/material block tag; default bossprogression:protected_terrain. Protection overrides permission."),
            Map.entry("preserve_bottom_layers", "Bottom building layers that must not be removed: 1-16. Default 1."),
            Map.entry("ruin_chance", "Chance to retain a low-wall cluster: 0-1. Default 0.35; 0 leaves only protected remnants/foundation. Not an exact block percentage."),
            Map.entry("ruin_max_height", "Maximum ruin height above the preserved foundation: 1-12 blocks. Default 5."),
            Map.entry("repeatable", "true allows a manual full-cycle reset after victory and rewards; false allows one completion."),
            Map.entry("boss_glowing", "true enables the boss outline; false disables it. Overrides raw SNBT Glowing."),
            Map.entry("rewards", "Rewards go to the encounter owner, not the last-hit player. Empty array [] means no extra rewards."),
            Map.entry("count", "Reward item quantity: 1-2304. Excess items drop nearby if the inventory is full."),
            Map.entry("amount", "Reward XP points, not experience levels."),
            Map.entry("mob", "Entity name, base attributes, bonuses and SNBT. Changes apply to future spawns, not existing bosses."),
            Map.entry("name", "In-world entity name. Remove this field to use SNBT CustomName or display_name."),
            Map.entry("name_visible", "true always shows the name; false uses normal Minecraft display rules, such as targeting the entity."),
            Map.entry("max_health", "Base maximum health: 1-1024 HP. 2 HP equals 1 heart. The boss spawns at full health."),
            Map.entry("extra_armor", "Additional armor points: 0-30. Final armor is capped by Minecraft's rules."),
            Map.entry("extra_damage", "Additional damage: 0-2048 HP per hit/projectile attributed to this boss, before the victim's armor."),
            Map.entry("attributes", "Attribute ID -> base number, e.g. minecraft:generic.movement_speed: 0.35. Unknown/unsupported attributes are skipped.")
    );
    private BossConfigHelp() {}
    static String annotate(String json) throws IOException {
        String guide;
        try (var stream = BossConfigHelp.class.getResourceAsStream("/config_templates/bossprogression/boss_help.txt")) {
            if (stream == null) throw new IOException("Missing boss config help template");
            guide = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        StringBuilder result = new StringBuilder(guide).append('\n');
        boolean root = true;
        for (String line : json.lines().toList()) {
            if (root && line.trim().equals("{")) {
                result.append("{\n  // false disables this boss ID, including its datapack definition.\n  \"enabled\": true,\n");
                root = false;
                continue;
            }
            var match = KEY.matcher(line);
            if (match.find() && HELP.containsKey(match.group(2))) result.append(match.group(1)).append("// ").append(HELP.get(match.group(2))).append('\n');
            result.append(line).append('\n');
        }
        return result.toString();
    }
}
