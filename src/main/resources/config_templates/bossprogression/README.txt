Boss Progression - config files (UTF-8)

These files are created automatically when Minecraft/the server first starts with the mod.
Copying the JAR into mods alone does not create them.

settings.jsonc      - enable/disable local boss config priority.
bosses/evoker.jsonc - The highest evoker: requirements, name, HP, armor, rewards, placement and collapse.
bosses/guardian.jsonc and bosses/wither.jsonc - the other bosses.
structures/        - your custom .nbt building templates for dungeon.mode = "nbt".

Open .jsonc files in Notepad/VS Code. Hints and examples are included directly in the files.
Save as UTF-8, then run /reload on the server (OP) or restart the world.
Existing files are never overwritten during startup or updates.
Missing standard files are restored from the installed mod version on the next mod startup.
To reset a config, back it up first, then delete the corresponding standard file.
Do not rename standard files to disable a boss: use enabled or the global switch.
Existing comments from older versions are not automatically replaced; edited values are preserved.

Priority: built-in definitions < datapacks < config/bossprogression/bosses/.
Definitions match by root id, not filename. enabled:false disables that ID even if a datapack supplies it.
Disabling an earlier boss may make dependent defeat_boss requirements impossible to complete.
To add a boss, copy an example to a new .jsonc file and change the root id and settings.
Condition IDs must be unique within each boss. Duplicate boss IDs: the first sorted file wins.
Subfolders are supported (search depth 5), up to 256 files, each no larger than 256 KiB.
JSON nesting is limited to 64 levels (comments and quoted string contents do not count).
File symlinks are not read. Invalid files are skipped with an error in logs/latest.log;
the datapack definition remains as fallback. Invalid settings do not disable the other configs.

Existing bosses do not receive new health/equipment. Saved encounter placement and collapse jobs
retain their original settings. Existing progress counters/unlocks are not automatically reset.
NBT templates are loaded from structures/, not mods. Native block/item/entity tags, structures,
advancements and loot tables are still supplied by Minecraft/mods/datapacks.
In multiplayer, edit the server's configs: local client configs cannot change server rules.
The Journal synchronizes server definitions on login and /reload.
