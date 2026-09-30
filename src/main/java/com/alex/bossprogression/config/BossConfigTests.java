package com.alex.bossprogression.config;

import com.alex.bossprogression.boss.BossDefinition;
import com.alex.bossprogression.boss.BossRegistry;
import com.alex.bossprogression.network.SyncJournalDefinitions;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Filesystem tests only touch their own temp directory; never alter a user's config files. */
@GameTestHolder("bossprogression")
@PrefixGameTestTemplate(false)
public final class BossConfigTests {
    private static void delete(Path root) throws Exception {
        try (var stream = Files.walk(root)) {
            for (var path : stream.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
    @GameTest(template = "empty_test")
    public static void seedCommentsAndKeepUserEdits(GameTestHelper helper) throws Exception {
        var root = Files.createTempDirectory("bossprogression-config-test-");
        try {
            BossConfigFiles.initialize(root);
            helper.assertTrue(Files.isDirectory(root.resolve("structures")) && Files.exists(root.resolve("README.txt")), "Config tree and help must be created");
            var file = root.resolve("bosses/evoker.jsonc");
            var text = Files.readString(file, StandardCharsets.UTF_8);
            helper.assertTrue(text.contains("//") && text.contains("extra_armor") && text.contains("ruin_max_height") && text.contains("SNBT"), "Hints must live inside the config");
            var generated = List.of(text, Files.readString(root.resolve("settings.jsonc")), Files.readString(root.resolve("README.txt")),
                    Files.readString(root.resolve("bosses/guardian.jsonc")), Files.readString(root.resolve("bosses/wither.jsonc")));
            helper.assertTrue(generated.stream().flatMapToInt(String::codePoints)
                    .noneMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.CYRILLIC), "All generated config hints must be English");
            var edited = text.replace("\"max_health\": 100", "\"max_health\": 123");
            helper.assertTrue(!edited.equals(text), "Fixture must find bundled health setting");
            Files.writeString(file, edited, StandardCharsets.UTF_8);
            BossConfigFiles.initialize(root);
            helper.assertTrue(Files.readString(file).equals(edited), "Startup must not overwrite user edits");
            var loaded = BossConfigFiles.readOverrides(root);
            helper.assertTrue(loaded.size() == 3, "All three commented default files must decode");
            var evoker = loaded.stream().filter(v -> v.definition().id().getPath().equals("evoker")).findFirst().orElseThrow();
            helper.assertTrue(evoker.definition().mob().maxHealth().orElseThrow() == 123, "Disk values must be authoritative overrides");
            var pack = new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation, BossDefinition>();
            pack.put(evoker.definition().id(), BossRegistry.get(evoker.definition().id()));
            BossConfigFiles.applyOverrides(pack, loaded);
            helper.assertTrue(pack.get(evoker.definition().id()) == evoker.definition(), "Disk config must replace the matching pack definition");
            Files.writeString(file, edited.replace("\"max_health\": 123", "\"max_health\": 124"));
            helper.assertTrue(BossConfigFiles.readOverrides(root).stream().filter(v -> v.definition().id().getPath().equals("evoker"))
                    .findFirst().orElseThrow().definition().mob().maxHealth().orElseThrow() == 124, "Reload must reread, not cache old files");
            helper.succeed();
        } finally { delete(root); }
    }
    @GameTest(template = "empty_test")
    public static void switchesDuplicatesAndBadFiles(GameTestHelper helper) throws Exception {
        var root = Files.createTempDirectory("bossprogression-config-invalid-test-");
        try {
            BossConfigFiles.initialize(root);
            var file = root.resolve("bosses/evoker.jsonc");
            var original = Files.readString(file);
            Files.writeString(file, original.replace("\"enabled\": true", "\"enabled\": false"));
            var disabled = BossConfigFiles.readOverrides(root);
            var evokerId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("bossprogression", "evoker");
            var packValue = BossRegistry.get(evokerId);
            var pack = new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation, BossDefinition>();
            pack.put(evokerId, packValue);
            BossConfigFiles.applyOverrides(pack, disabled);
            helper.assertTrue(!pack.containsKey(evokerId), "Per-boss enabled=false must remove the pack definition too");
            Files.writeString(root.resolve("settings.jsonc"), "// disable all disk overrides\n{\"enable_boss_configs\":false}");
            var ignored = BossConfigFiles.readOverrides(root);
            helper.assertTrue(ignored.isEmpty(), "Global switch must leave only pack definitions");
            pack.put(evokerId, packValue);
            BossConfigFiles.applyOverrides(pack, ignored);
            helper.assertTrue(pack.get(evokerId) == packValue, "Global off must preserve pack rules");
            Files.writeString(root.resolve("settings.jsonc"), "{\"enable_boss_configs\":true}");
            Files.writeString(file, "{broken"); // expected error log; other configs must remain available
            var valid = BossConfigFiles.readOverrides(root);
            helper.assertTrue(valid.size() == 2, "Invalid file must not abort other bosses");
            BossConfigFiles.applyOverrides(pack, valid);
            helper.assertTrue(pack.get(evokerId) == packValue, "Broken config must preserve the pack fallback");
            Files.writeString(file, " ".repeat(BossConfigFiles.MAX_FILE_BYTES + 1));
            helper.assertTrue(BossConfigFiles.readOverrides(root).size() == 2, "Oversized files must be rejected");
            Files.writeString(file, original.replace("\"nbt\": \"{}\"", "\"nbt\": \"{}\", \"ignored\": " + "[".repeat(70) + "0" + "]".repeat(70)));
            helper.assertTrue(BossConfigFiles.readOverrides(root).size() == 2, "Deep JSON must be rejected before parsing/codec error recursion");
            Files.writeString(file, original);
            Files.writeString(root.resolve("bosses/0-first.json"), original.replace("\"max_health\": 100", "\"max_health\": 222"));
            var overrides = BossConfigFiles.readOverrides(root);
            helper.assertTrue(overrides.size() == 3 && overrides.getFirst().definition().mob().maxHealth().orElseThrow() == 222,
                    "Duplicate IDs must pick the first sorted file deterministically");
            helper.succeed();
        } finally { delete(root); }
    }
    private static SyncJournalDefinitions roundTrip(GameTestHelper helper, SyncJournalDefinitions packet) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SyncJournalDefinitions.STREAM_CODEC.encode(buffer, packet);
            return SyncJournalDefinitions.STREAM_CODEC.decode(buffer);
        } finally { buffer.release(); }
    }
    @GameTest(template = "empty_test")
    public static void journalSyncDoesNotMutateServer(GameTestHelper helper) {
        var fallback = List.copyOf(BossRegistry.clientAll());
        var server = BossRegistry.all().iterator().next();
        try {
            BossRegistry.clearClientSync();
            BossRegistry.replaceClientFallback(List.of(server));
            var custom = new BossDefinition(server.id(), Component.literal("Synced server name"), server.bossEntity(), server.conditions(),
                    server.dungeon(), server.repeatable(), server.bossGlowing(), server.rewards(), server.mob());
            var view = SyncJournalDefinitions.journalView(custom);
            helper.assertTrue(view.dungeon() == null && view.mob().nbt().isEmpty(), "Server placement/entity NBT must not leak into Journal packets");
            roundTrip(helper, new SyncJournalDefinitions(0, Optional.empty())).applyClient();
            roundTrip(helper, new SyncJournalDefinitions(1, Optional.of(view))).applyClient();
            helper.assertTrue(BossRegistry.clientGet(server.id()).displayName().getString().equals(server.displayName().getString()), "Partial sync must not replace visible Journal data");
            roundTrip(helper, new SyncJournalDefinitions(2, Optional.empty())).applyClient();
            helper.assertTrue(BossRegistry.clientGet(server.id()).displayName().getString().equals("Synced server name"), "Journal must use committed server rules");
            helper.assertTrue(BossRegistry.clientGet(server.id()).conditions().size() == server.conditions().size(), "Condition codecs must round-trip over the packet");
            BossRegistry.replaceClientFallback(List.of(server));
            helper.assertTrue(BossRegistry.clientGet(server.id()).displayName().getString().equals("Synced server name"), "Client resource reload must not overwrite server sync");
            helper.assertTrue(BossRegistry.get(server.id()) == server, "Integrated-server rules must not change from client sync/reload");
            BossRegistry.clearClientSync();
            helper.assertTrue(BossRegistry.clientGet(server.id()) == server, "Disconnect must clear stale remote-server data");
            helper.succeed();
        } finally {
            BossRegistry.clearClientSync();
            BossRegistry.replaceClientFallback(fallback);
        }
    }
}
