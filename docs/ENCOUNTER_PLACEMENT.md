# Encounter location configuration

Edit the generated, commented boss definition in `config/bossprogression/bosses/<boss>.jsonc`, then run `/reload` (see `CONFIG_FILES.md`). You can also use a datapack at `data/<namespace>/bosses/<boss>.json`; enabled disk configs override matching IDs. Replace its `dungeon` block with one of the following configurations. This is server-owned configuration, NOT a client setting. NBT files and definitions must be installed on the server (or the integrated server for single-player).

Old definitions without `mode` retain their procedural arenas. New release definitions use existing world structures: Evoker -> mansion, Guardian -> monument, Wither -> Nether fortress. Existing saved encounters retain their saved location and spawn point; config edits apply to newly unlocked cycles.

## 1. World structure

```json
"dungeon": {
  "mode": "structure",
  "dimension": "minecraft:overworld",
  "structure": "minecraft:mansion",
  "search_radius_chunks": 128,
  "activation_radius": 24
}
```

The `structure` value is a registered **worldgen structure ID**, like the argument of `/locate structure`, NOT an NBT template name. Modded structure IDs work when that mod is installed and the structure generates in the chosen dimension. Other examples: `minecraft:monument`, `minecraft:fortress` (use `minecraft:the_nether`).

The search uses Minecraft's structure locator and may generate chunks. The mod never replaces the found structure with its own room. A collision-free, supported land position (or water position for vanilla Guardians) is searched inside the structure's bounding box, near its centre. Existing native mobs are not deleted, nor adopted as the owned boss: the encounter adds one separate boss entity.

Each physical structure start is reserved for one encounter, including completed/abandoned ones. Two players or two cycles cannot claim that same site. Up to eight bounded searches try alternative anchors; failure leaves the boss READY with no invitation rather than spawning at an unrelated location. Large radius values can produce noticeable server-tick pauses; default is 64 chunks, allowed range 1–128. The radius applies around each search anchor, not as a strict owner-to-site distance. `min_distance`/`max_distance` are not used for structure-mode placement.

Optional `spawn_offset: [x,y,z]` shifts the preferred location relative to the bounding-box centre; if that point is unsafe, the local scan looks for a safe nearby spot. The offset is not a guarantee of a specific room in arbitrary worldgen structures.

## 2. Own NBT building

```json
"dungeon": {
  "mode": "nbt",
  "dimension": "minecraft:overworld",
  "nbt": "arenas/evoker_chamber.nbt",
  "min_distance": 128,
  "max_distance": 512,
  "spawn_offset": [8, 1, 8],
  "activation_radius": 24
}
```

Place the file at:

```
<server-directory>/config/bossprogression/structures/arenas/evoker_chamber.nbt
```

For this development project the directory is `run/config/bossprogression/structures/`. It is created at mod startup. Do not put templates in the `mods/` folder or edit the mod JAR.

### Saving a building

1. Use a vanilla structure block in SAVE mode, select the complete building, and save it.
2. The vanilla file is normally in `<world>/generated/<namespace>/structures/<name>.nbt`.
3. Copy that compressed structure-block `.nbt` to the folder above.
4. Specify its relative file name in `nbt`. It can be in a subfolder; absolute paths and `..` are rejected. Symlinks escaping the folder are rejected as well.
5. `spawn_offset` is the boss's feet position **relative to the saved template's minimum corner**, not the original structure block. With a floor at local Y=0, a typical spawn has Y=1. It must be inside the saved dimensions; reserve enough empty space for the entire boss. A Guardian needs water.

The template is placed once on a located surface. Saved entities are deliberately ignored: do not include the encounter boss in the file. Blocks/block entities (including loot containers) are placed normally. Templates can overwrite terrain/buildings at the chosen location; back up your world and use sensible placement distances. Surface placement does not level terrain or guarantee an accessible entrance.

A successfully placed template is recorded even when its configured spawn spot is blocked. The log reports that spot; clearing it allows normal activation without generating another copy. File changes affect future placements; they do not rebuild already placed encounters.

Limits: compressed file <= 8 MiB; NBT accounting quota 32 MiB; each dimension <=128 blocks; bounding volume <=262144 blocks. Bad/missing files are logged, never replaced silently by a procedural room.

## 3. Biome without a building

```json
"dungeon": {
  "mode": "biome",
  "dimension": "minecraft:the_nether",
  "biome": "minecraft:soul_sand_valley",
  "min_distance": 128,
  "max_distance": 2048,
  "activation_radius": 24
}
```

Searches the specified biome and finds a safe local spawn. No structure or blocks are placed. Underground dimensions use the located biome height rather than the Nether roof. Vanilla Guardians use water positions. The target biome is checked at the final spawn point. Search is bounded and can fail if no suitable safe ground/water is found. `max_distance` is the search radius around each attempted anchor; it is not a strict total owner-to-site radius.

## Activation / persistence / recovery

After requirements are completed, the mode resolves a site, records the encounter UUID, spawn position, radius and reservation key, and issues the invitation. The owner approaching the saved spawn position activates exactly one boss. Spawn collision, water suitability and accepted entity insertion are rechecked at activation.

Reset clears player progression but never triggers deletion or removes reservation records. Optional boss-death cleanup can delete captured dungeon blocks gradually; see `DUNGEON_DESTRUCTION.md`. Old instances cannot activate again for a new cycle. Rewards, offline reconciliation and owner checks remain unchanged. The mod does not party-share a structure reservation.

If resolution fails, fix the config/file and retry as an OP:

```
/bossprogress generate bossprogression:evoker
```

The owner must already be unlocked. No per-tick expensive structure search is performed. Without OP access, an owner can request help; an automatic retry GUI is not currently implemented.

## Checks

Build and dedicated-server startup are checked during development. In-game checks still required:

- Locate/activate a mansion, monument and fortress; verify boss is inside the structure, no building is generated and no suffocation/lava spawn occurs.
- Two owners / repeat cycle: same structure start must not be reused.
- Missing mod/structure/biome IDs, structures disabled, no safe spot: stay READY, no false dungeon flag/invitation.
- Valid local NBT, missing file, corrupt NBT, oversized file/dimensions, `../` and symlink escape.
- Confirm structure-block chest loot/block entities load, saved boss entities do not.
- NBT spawn_offset: interior valid point, outside bounds, blocked point; no repeat placement after a successfully placed template.
- Restart/chunk reload/relog: saved spawn point/radius/site key and once-only activation survive.
- Biome mode: Overworld land, ocean Guardian, Nether caves; no terrain modification.
- Existing pre-mode definitions and old saved instances retain compatibility.
