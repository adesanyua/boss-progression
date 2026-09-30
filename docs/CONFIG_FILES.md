# Editable configs in the Minecraft config folder

On the first Minecraft/server startup with the mod, Boss Progression automatically creates the files below. Merely copying the JAR into `mods/` does not create them:

```text
<Minecraft/server>/config/bossprogression/
├── settings.jsonc
├── README.txt
├── bosses/
│   ├── evoker.jsonc
│   ├── guardian.jsonc
│   └── wither.jsonc
└── structures/
```

Development instance: `run/config/bossprogression/`. Isolated GameTest instance: `run/gametest/config/bossprogression/`.

## Editing

The files use UTF-8 **JSONC**: JSON with `//` and `/* ... */` comments. Generated boss files contain English inline hints plus examples/type lists for requirements, mob stats/SNBT, placement, rewards, reset and destruction/ruins. They are not opaque copies without help. Open with Notepad/VS Code and change the actual values below the comments; use double-quoted strings, unquoted true/false and decimal points.

Example inside `bosses/evoker.jsonc`:

```jsonc
"mob": {
  // Base maximum HP; 2 HP = 1 heart. Applies to future spawns.
  "max_health": 100,
  // Additional armor points.
  "extra_armor": 20
}
```

That snippet illustrates fields, not a complete replacement definition. The generated full definition also includes id, display_name, boss_entity, conditions etc. Evoker defaults remain The highest evoker, 100 HP, +20 armor.

Save changes and run `/reload` as an operator, or restart the world/server. Disk files are reread during every server datapack reload. Existing files are never overwritten by startup or a mod update. Missing standard files are recreated on the next mod initialization; delete a backed-up file to restore the installed version's defaults. New optional fields have codec defaults, but existing generated comments are not automatically rewritten during an update.

## Priority and disabling

1. Built-in definitions in the mod.
2. Datapacks replacing/adding definitions.
3. Disk definitions in `config/bossprogression/bosses/` when enabled.

A disk file is a **complete definition**, not a partial patch. Matching uses the root `id`, not its filename. Disk overrides are applied only server-side (including a single-player integrated server).

- `settings.jsonc`: `"enable_boss_configs": false` ignores all disk boss files and restores normal datapack priority without deleting files.
- Boss file: `"enabled": false` removes that boss ID from the active server registry, including its datapack definition. Disabling a prerequisite boss can make later bosses' requirements impossible; configure the chain accordingly.
- Add another `.jsonc` or `.json` file by copying an example, assigning a new root id and adjusting settings. Optional subdirectories are supported (walk depth 5). Duplicate config IDs: first filename in sorted path order wins, with an error log for the duplicate.
- To use a datapack's replacement of the three shipped bosses, turn off disk overrides or update the corresponding config; generated default files otherwise intentionally take priority.

Native entity/item/block tags, structures, advancements and loot tables are still supplied by Minecraft/mods/datapacks. Configs reference their IDs; this feature does not generate new registry objects or custom tags from arbitrary filenames.

## Safety and persistence

- Invalid individual definitions are skipped with the exact path/error in `logs/latest.log`; the datapack definition remains as fallback. Other configs still load.
- Invalid `settings.jsonc` logs an error and continues reading boss configs, rather than silently disabling all overrides.
- Maximum 256 eligible files and 256 KiB per file, decoded as UTF-8; JSON nesting is capped at 64 before parsing (quoted strings/comments do not count). File symlinks are excluded. If file count exceeds the cap, disk overrides are ignored as a group with a logged error.
- Existing schema validation still applies (counts, dungeon targets/paths, finite attributes, reserved entity-NBT fields). Unknown keys follow the existing codec behavior and do not create new functionality.
- No saved progress is erased by a reload. Already spawned mobs do not receive new HP/equipment/name; persisted encounter placement and destruction jobs keep their saved settings. Pending old destruction settings without newer fields still use their codec defaults.
- Server/client and data saves are not a crash-atomic transaction; the existing reward delivery caveat remains.

## Journal synchronization

At login and after `/reload`, `OnDatapackSyncEvent` sends server names, requirements, repeatability and rewards to the Journal. The client does not need identical server config/datapack files. Gameplay remains server-authoritative; this payload is clientbound only.

Journal definitions are a reduced projection: server-only placement settings and raw entity NBT are not sent. Begin/entry/end packets build a pending cache and commit it atomically, so a partial batch does not display a partial registry. Client resource reloads affect only a separate fallback cache; they cannot replace the active synced view or mutate rules of an integrated server. Disconnect clears the remote cache.

This feature requires matching mod/network versions. Mod-added condition/reward codec implementations must exist on both sides. Very large third-party datapack definitions remain subject to Minecraft's network payload limits (disk configs also have the file-size limit above).

## Verification

`BossConfigTests` adds three isolated GameTests (12 required tests total):

- Creation of the folder tree and commented files, English-only generated help, UTF-8 parsing of defaults, user edits surviving reinitialization and subsequent reads seeing changed values.
- Global/per-boss switches, deterministic duplicate selection, malformed/oversized/deeply nested file isolation. Intentional negative cases log expected errors.
- Registry-friendly stream-codec round-trip, atomic Journal-cache commit, resource fallback isolation, disconnect clearing and no mutation of server rules.

Build and dedicated startup were checked; startup reports 3 pack definitions + 3 disk overrides. No interactive multiplayer verification was performed. Manually check client join, `/reload` with the Journal open, single-player F3+T, disconnect/reconnect to another server, and different client/server config folders.
