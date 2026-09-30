package com.alex.bossprogression.config;

import com.alex.bossprogression.BossProgressionMod;
import com.alex.bossprogression.boss.BossDefinition;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.FMLPaths;

/** Commented disk overrides layered over datapacks; all gameplay decoding still uses BossDefinition.CODEC. */
public final class BossConfigFiles {
    public static final int MAX_FILE_BYTES = 262144;
    public static final int MAX_FILES = 256;
    public record Override(BossDefinition definition, boolean enabled, Path source) {}
    private BossConfigFiles() {}
    public static Path directory() { return FMLPaths.CONFIGDIR.get().resolve(BossProgressionMod.MODID); }

    public static void initialize() {
        try { initialize(directory()); }
        catch (IOException e) { BossProgressionMod.LOGGER.error("Cannot initialize Boss Progression config directory {}", directory(), e); }
    }
    /** Public path overload also allows isolated filesystem tests without touching user configs. */
    public static void initialize(Path root) throws IOException {
        Files.createDirectories(root.resolve("bosses"));
        Files.createDirectories(root.resolve("structures"));
        createOnce(root.resolve("settings.jsonc"), resource("/config_templates/bossprogression/settings.jsonc"));
        createOnce(root.resolve("README.txt"), resource("/config_templates/bossprogression/README.txt"));
        for (String boss : List.of("evoker", "guardian", "wither")) {
            Path target = root.resolve("bosses/" + boss + ".jsonc");
            if (Files.exists(target)) continue;
            String json = resource("/data/bossprogression/bosses/" + boss + ".json");
            createOnce(target, BossConfigHelp.annotate(json));
        }
    }
    private static String resource(String path) throws IOException {
        try (var stream = BossConfigFiles.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing bundled config template " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private static void createOnce(Path file, String contents) throws IOException {
        try { Files.writeString(file, contents, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
        catch (FileAlreadyExistsException ignored) { /* User edits always win, including concurrent startup. */ }
    }
    private static JsonElement readJson(Path file) throws IOException {
        byte[] bytes;
        try (var stream = Files.newInputStream(file)) { bytes = stream.readNBytes(MAX_FILE_BYTES + 1); }
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Config exceeds " + MAX_FILE_BYTES + " bytes");
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        checkJsonDepth(text);
        return JsonParser.parseString(text); // Gson accepts // and /* */ comments (JSONC).
    }
    /** Bound recursion before Gson/codec error formatting. Ignore quotes and JSONC comments. */
    private static void checkJsonDepth(String text) throws IOException {
        int depth = 0;
        char quote = 0;
        boolean escaped = false, lineComment = false, blockComment = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i), next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (lineComment) { if (c == '\n' || c == '\r') lineComment = false; continue; }
            if (blockComment) { if (c == '*' && next == '/') { blockComment = false; i++; } continue; }
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
            } else if (c == '/' && next == '/') { lineComment = true; i++; }
            else if (c == '/' && next == '*') { blockComment = true; i++; }
            else if (c == '"' || c == '\'') quote = c;
            else if (c == '[' || c == '{') { if (++depth > 64) throw new IOException("Config JSON nesting exceeds 64"); }
            else if (c == ']' || c == '}') depth--;
        }
    }
    public static void applyOverrides(Map<net.minecraft.resources.ResourceLocation, BossDefinition> definitions, List<Override> overrides) {
        for (var override : overrides) {
            if (override.enabled()) definitions.put(override.definition().id(), override.definition());
            else definitions.remove(override.definition().id());
        }
    }
    public static List<Override> readOverrides(Path root) {
        try {
            Path settings = root.resolve("settings.jsonc");
            if (Files.exists(settings)) {
                var object = readJson(settings).getAsJsonObject();
                boolean enabled = !object.has("enable_boss_configs") || Codec.BOOL.parse(JsonOps.INSTANCE,
                        object.get("enable_boss_configs")).getOrThrow();
                if (!enabled) return List.of();
            }
        } catch (IOException | RuntimeException e) {
            // A broken switch must not silently turn off the boss overrides.
            BossProgressionMod.LOGGER.error("Invalid {}; continuing with boss configs: {}", root.resolve("settings.jsonc"), e.toString());
        }
        Path bosses = root.resolve("bosses");
        if (!Files.isDirectory(bosses)) return List.of();
        List<Path> paths;
        try (var stream = Files.walk(bosses, 5)) {
            paths = stream.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> p.toString().endsWith(".jsonc") || p.toString().endsWith(".json"))
                    .limit(MAX_FILES + 1L).toList();
        } catch (IOException | RuntimeException e) {
            BossProgressionMod.LOGGER.error("Cannot list boss configs {}: {}", bosses, e.toString());
            return List.of();
        }
        if (paths.size() > MAX_FILES) {
            BossProgressionMod.LOGGER.error("Too many boss configs in {} (limit {}); using datapacks only", bosses, MAX_FILES);
            return List.of();
        }
        List<Override> result = new ArrayList<>();
        Set<net.minecraft.resources.ResourceLocation> seen = new HashSet<>();
        for (Path file : paths.stream().sorted(Comparator.comparing(Path::toString)).toList()) {
            try {
                var json = readJson(file);
                var definition = BossDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                boolean enabled = !json.getAsJsonObject().has("enabled") || Codec.BOOL.parse(JsonOps.INSTANCE,
                        json.getAsJsonObject().get("enabled")).getOrThrow();
                if (!seen.add(definition.id())) {
                    BossProgressionMod.LOGGER.error("Skipping duplicate boss id {} in {}; first sorted config wins", definition.id(), file);
                    continue;
                }
                result.add(new Override(definition, enabled, file));
            } catch (IOException | RuntimeException e) {
                BossProgressionMod.LOGGER.error("Skipping invalid boss config {}; datapack fallback remains: {}", file, e.toString());
            }
        }
        return List.copyOf(result);
    }
}
