# Boss mob attributes and NBT (Minecraft 1.21.1)

Boss JSON has a new optional `mob` object. It is configured server-side in generated `config/bossprogression/bosses/*.jsonc` files (with inline hints; see `CONFIG_FILES.md`) or in the same datapack definition as requirements/rewards/dungeon. Disk configs override matching datapack IDs when enabled. Old JSON without it remains valid. Vanilla attributes remain unchanged unless configured; the default world name is the definition's `display_name` and is visible.

## Example

This is an illustrative strengthened boss, not the stats shipped by default:

```json
"mob": {
  "name": "Arch Evoker",
  "name_visible": true,
  "max_health": 240,
  "extra_armor": 8,
  "extra_damage": 4,
  "attributes": {
    "minecraft:generic.armor_toughness": 6,
    "minecraft:generic.movement_speed": 0.35,
    "minecraft:generic.follow_range": 48,
    "minecraft:generic.knockback_resistance": 0.5
  },
  "nbt": "{NoAI:0b,Silent:0b,CanPickUpLoot:0b}"
}
```

The release JSONs expose this block: Evoker is named «The highest evoker», has 100 base HP and +20 armor; Elder Guardian keeps 80 base HP and Wither 300. Other bonuses are 0. These are health points, not hearts (2 HP = 1 heart).

| Field | Meaning |
| --- | --- |
| name | Optional plain-text entity name; does not replace Journal display_name |
| name_visible | Show custom name; default true |
| max_health | Base maximum HP, 1–1024; spawn at full health when present |
| extra_armor | Additional armor attribute points, 0–30; one persistent additive modifier, not extra equipment |
| extra_damage | Flat bonus per boss-owned damage event, 0–2048, before victim armor/resistance |
| attributes | Map of attribute registry IDs to base values; supports installed mod attributes |
| nbt | SNBT compound **inside a JSON string**, as used in entity commands; default `{}` |

Vanilla clamps final armor/attribute values to its limits; armor above the cap does not add more protection. Unknown or unsupported attributes are logged and skipped rather than crashing. Finite values are required; typed attribute values are sanitized using each attribute's actual range.

`extra_damage` is NOT added again as an attack_damage modifier: it is applied once to melee, projectiles, Evoker fangs etc. only when the damage source attributes the hit to an activated, undefeated encounter boss. It does not amplify attacks from summoned Vexes, unattributed environmental damage or ordinary mobs. Changing `minecraft:generic.attack_damage` separately adjusts attacks that actually use that vanilla attribute; many spells use hardcoded damage instead.

## Raw NBT examples

Equipment (1.21.1 ItemStack uses lowercase `id` and `count`):

```json
"nbt": "{ArmorItems:[{},{},{},{id:\"minecraft:diamond_helmet\",count:1}],ArmorDropChances:[0f,0f,0f,0f]}"
```

Formatted name (omit typed `mob.name` to let this name win):

```json
"nbt": "{CustomName:'{\"text\":\"Ancient Guardian\",\"color\":\"gold\",\"bold\":true}'}"
```

Native entity attributes in raw 1.21.1 SNBT use lowercase `attributes`, `id`, `base`:

```json
"nbt": "{attributes:[{id:\"minecraft:generic.max_health\",base:200d}],Health:200f}"
```

Prefer the typed `max_health`/`attributes` fields for simple tuning. Capitalized old versions' `Attributes`/`Name`/`Base` formats are not silently migrated by this feature. Raw entity data is applied to a freshly created mob, not loaded as a separate entity.

Fields like `NoAI`, `Silent`, `NoGravity`, equipment and entity-specific fields are accepted, but may make a boss unsuitable or impossible to fight. This is trusted administrator/datapack configuration, not a client network request.

## Precedence and durability

1. Merge raw NBT onto the mob's initial vanilla save data.
2. Set typed base `attributes`, then typed `max_health` if present.
3. Add the unique armor modifier and store the flat damage bonus.
4. Apply typed name if present; otherwise keep raw CustomName or use display_name. name_visible and definition boss_glowing are authoritative.
5. Enforce encounter UUID, spawn position, persistence and final health. If max_health is present, health is full. Otherwise explicit raw Health is applied last and clamped to the resulting maximum; without Health it starts full.
6. Refresh dimensions and recheck collision before inserting the entity. A scaled boss needs enough room.

Permanent armor modifier, vanilla attributes, NBT equipment/name and NeoForge persistent damage data survive normal entity save/reload. The customizer does not run on every chunk reload, so wounded bosses are not healed again and bonuses are not stacked. `/reload` changes future spawns, not already spawned mobs.

## Safety/validation

- SNBT limit: 32768 characters, depth <=32; embedded strings <=8192 characters and depth <=32. Non-finite numeric data is rejected.
- Invalid syntax/settings make the boss definition fail decoding with a logged error, instead of crashing the datapack loader.
- Reserved fields are rejected: entity `id`, UUID fields, Pos, Motion, Dimension, passengers/vehicle/leash/team, persistence, portal/death/hurt bookkeeping, Forge/NeoForge persistent data and attachments. These cannot override encounter ownership/lifecycle.
- Raw Health must be positive. The spawn is canceled if applying entity-specific NBT raises a runtime exception; the encounter remains unactivated and logs a warning. Fix the definition and `/reload` before retrying activation.
- Vanilla/mod-specific entity NBT is not guaranteed to support every tag. Very large equipment/effects or invalid entity-specific values are the administrator's responsibility; no new mobs or passenger trees are created from NBT.

## Verification

`BossMobTests` adds four isolated GameTests:

- Attributes/NBT/name/identity/persistence; armor reapplication does not stack; entity round-trip keeps wounded HP and damage bonus.
- Raw 1.21.1 attributes/Health, diamond helmet equipment and styled entity name.
- Legacy definition defaults, malformed/forbidden NBT, invalid health and unknown attribute handling.
- Bonus damage applies to melee and owned projectiles but not ordinary mobs or defeated encounters.

Together with destruction and config/sync tests, all 12 GameTests passed. Build and dedicated-server startup passed. Still check actual name rendering, equipment, combat balance and third-party mob-specific tags manually via runClient.
