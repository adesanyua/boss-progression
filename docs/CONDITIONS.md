# Boss conditions / release content

Player-editable definitions are generated in `config/bossprogression/bosses/*.jsonc` with inline comments; see `CONFIG_FILES.md`. They override matching datapack IDs when enabled. Definitions can also live at `data/<namespace>/bosses/<name>.json` in a datapack. Built-ins are in `src/main/resources/data/bossprogression/bosses/`. Use `/reload` after editing. All conditions are ANDed. `id` must be unique within a boss. Counts must be positive. New activity conditions default `required_count` to 1.

## Supported types

Every discriminator below has the prefix `bossprogression:`.

| Type | Target field | Semantics |
| --- | --- | --- |
| kill_entity | entity | Kills credited to the attacking player, including projectiles when no weapon filter is used |
| kill_entity_tag | tag | As above, using an entity-type tag |
| loot_chest | optional loot_table | First actual opening of a chest/barrel with an ungenerated loot table; double chests count once |
| obtain_item | item | Possess the entire required quantity at the same time; latched once achieved; does not consume items |
| submit_item | item | Journal's Submit Items button donates outstanding items from inventory; partial submissions persist |
| craft_item | item | Number of output items crafted by the player (not smelting) |
| mine_block | block | Number of blocks broken in survival; player-placed blocks are also eligible |
| visit_biome | biome | Stand in the specified biome; required_count must be 1 |
| visit_dimension | dimension | Enter specified dimension; required_count must be 1 |
| advancement | advancement | Complete the specified advancement; required_count must be 1 |
| defeat_boss | boss | Owner's specified Boss Progression encounter is DEFEATED; required_count must be 1 |
| trade | optional item | Number of villager transactions; optional item filters the purchased output |
| fish | optional item | Number of items caught with a fishing rod; optional item filters drops |

Inventory, location, advancement and prerequisite conditions are checked once a second. Once satisfied, they remain complete for the cycle. On reset, counters and submission progress clear. Existing advancements can satisfy the next cycle immediately; standing in the required biome/dimension or possessing the required items can also satisfy the next cycle. Craft, mining, trading, fishing, submission and kill counters must be earned again. Reset does NOT revoke vanilla advancements.

## Examples

```json
{"type":"bossprogression:obtain_item","id":"pearls","item":"minecraft:ender_pearl","required_count":16}
```
```json
{"type":"bossprogression:submit_item","id":"tribute","item":"minecraft:emerald","required_count":32}
```
```json
{"type":"bossprogression:craft_item","id":"apples","item":"minecraft:golden_apple","required_count":3}
```
```json
{"type":"bossprogression:mine_block","id":"obsidian","block":"minecraft:obsidian","required_count":64}
```
```json
{"type":"bossprogression:visit_biome","id":"forest","biome":"minecraft:dark_forest"}
```
```json
{"type":"bossprogression:visit_dimension","id":"nether","dimension":"minecraft:the_nether"}
```
```json
{"type":"bossprogression:advancement","id":"enter_nether","advancement":"minecraft:story/enter_the_nether"}
```
```json
{"type":"bossprogression:defeat_boss","id":"previous_boss","boss":"bossprogression:evoker"}
```
```json
{"type":"bossprogression:loot_chest","id":"shipwrecks","required_count":10,"loot_table":"minecraft:chests/shipwreck_treasure"}
```
```json
{"type":"bossprogression:trade","id":"trades","required_count":15}
```
```json
{"type":"bossprogression:fish","id":"cod","item":"minecraft:cod","required_count":20}
```

## Kill filters

Both kill types accept optional `filters`. Absent filters preserve the old schema. All specified filters must match at the victim's position when it dies.

```json
{
  "type": "bossprogression:kill_entity",
  "id": "night_skeletons",
  "entity": "minecraft:skeleton",
  "required_count": 20,
  "filters": {
    "dimension": "minecraft:overworld",
    "biome": "minecraft:dark_forest",
    "time": "night",
    "weapon_tag": "minecraft:swords"
  }
}
```

`time` supports `day` and `night`. Night is world day-time ticks 13000–22999, including dimensions with no visible sky (e.g. Nether). `weapon_tag` is a melee-only filter: projectiles are excluded rather than checking an unrelated item currently held by the shooter. Bow-at-launch attribution is not implemented.

## Release bosses

- Evoker: 200 illager kills and 100 fresh loot containers; illagers use `bossprogression:illagers` rather than the broader raiders tag. Rewards: 32 emeralds, 1 totem, 500 XP.
- Guardian (`bossprogression:guardian`, entity `minecraft:elder_guardian`): defeat Evoker, visit deep ocean, kill 32 guardians in Overworld, fish 16 cod, donate 32 prismarine shards. Rewards: 8 sponge, 8 diamonds, 1000 XP. Encounter is placed in an existing ocean monument.
- Wither: defeat Guardian, visit Nether, kill 40 wither skeletons there with swords, mine 32 soul sand, craft 8 golden apples, donate 3 wither skulls. Rewards: 16 diamonds, enchanted golden apple, 2000 XP. Encounter is placed in an existing Nether fortress; ordinary Wither behavior can destroy it.

All three are manually repeatable and glowing. Natural mob drops remain in addition to definition rewards. No test boss is shipped. Existing player saves are not erased; a removed test boss's saved state may remain inert.

Release encounters now use existing worldgen structures. Local NBT placement and biome-only spawning are also configurable; see `ENCOUNTER_PLACEMENT.md`. Old definitions without placement mode retain their procedural arenas. These definitions are a release baseline, not a claim of completed gameplay balancing. Server config/datapack Journal definitions are synchronized at login and after reload; matching client config files are not required.

## Manual verification

- Old definitions without filters/new conditions load unchanged. Unknown types, missing targets, invalid time and invalid counts are rejected by the codec.
- Test each kill filter separately and combined; test projectile with weapon_tag does NOT count. Check day/night boundary and Nether world-clock behavior.
- Inventory condition: below threshold, full threshold, remove items afterward; reset and reacquire as needed.
- Submission: partial donation, duplicate packet, unknown boss, fully completed condition, relog, independent player inventories. No extra items are consumed after completion.
- Craft normal and shift-click; canceled block break/fishing, survival vs creative mining; trade with and without target; fishing item filtering.
- Visit dimension/biome and complete advancement; preexisting advancement, missing advancement ID, prerequisite boss owner's victory, reset/repeat cycle.
- Loot-table-filtered single and double containers; wrong table must not consume deduplication slot.
- Open Journal: activity labels, filters, pagination, Submit Items visibility and server attachment refresh.
- Unlock all three with OP commands and enter arenas; verify monument water for Guardian, fortress placement for Wither and standard mob AI/glow.
