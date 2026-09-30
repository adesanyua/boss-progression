package com.alex.bossprogression.data;

import com.alex.bossprogression.BossProgressionMod;
import com.alex.bossprogression.boss.BossDefinition;
import com.alex.bossprogression.boss.BossRegistry;
import com.alex.bossprogression.condition.ConditionRegistry;
import com.alex.bossprogression.config.BossConfigFiles;
import com.alex.bossprogression.reward.RewardRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Pack definitions followed by server disk overrides. Client resource reloads never mutate server rules. */
public final class BossDefinitionLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final boolean server;
    public BossDefinitionLoader() { this(true); }
    public BossDefinitionLoader(boolean server) {
        super(GSON, "bosses");
        this.server = server;
        ConditionRegistry.registerBuiltIns();
        RewardRegistry.registerBuiltIns();
    }
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        var definitions = new LinkedHashMap<ResourceLocation, BossDefinition>();
        for (var entry : files.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            try {
                var parsed = BossDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue());
                if (parsed.result().isEmpty()) {
                    parsed.error().ifPresent(error -> BossProgressionMod.LOGGER.error("Skipping boss file {}: {}", entry.getKey(), error));
                    continue;
                }
                var definition = parsed.result().get();
                if (definitions.put(definition.id(), definition) != null)
                    BossProgressionMod.LOGGER.warn("Duplicate datapack boss id {} in {}", definition.id(), entry.getKey());
            } catch (RuntimeException e) {
                BossProgressionMod.LOGGER.error("Skipping boss file {}: {}", entry.getKey(), e.toString());
            }
        }
        int overrides = 0;
        if (server) {
            var disk = BossConfigFiles.readOverrides(BossConfigFiles.directory());
            BossConfigFiles.applyOverrides(definitions, disk);
            overrides = disk.size();
        }
        for (var definition : definitions.values()) {
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(definition.bossEntity())) BossProgressionMod.LOGGER.warn(
                    "Boss {} references unknown entity {} (mod missing?); registering as unavailable", definition.id(), definition.bossEntity());
        }
        if (server) BossRegistry.replaceServer(definitions.values());
        else BossRegistry.replaceClientFallback(definitions.values());
        BossProgressionMod.LOGGER.info("Loaded {} boss definition(s) from datapack + {} config override(s) ({})",
                definitions.size(), overrides, server ? "server" : "client fallback");
    }
}
