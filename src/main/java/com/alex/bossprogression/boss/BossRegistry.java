package com.alex.bossprogression.boss;

import com.alex.bossprogression.BossProgressionMod;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Server rules and client Journal caches are separate, including on an integrated server. */
public final class BossRegistry {
    private static volatile Map<ResourceLocation, BossDefinition> definitions = Map.of();
    private static volatile Map<ResourceLocation, BossDefinition> clientFallback = Map.of();
    private static volatile Map<ResourceLocation, BossDefinition> clientSynced;
    private static Map<ResourceLocation, BossDefinition> clientPending;
    private BossRegistry() {}

    public static void register(BossDefinition definition) {
        var next = new LinkedHashMap<>(definitions);
        if (next.put(definition.id(), definition) != null)
            BossProgressionMod.LOGGER.warn("Boss definition {} was overwritten (duplicate id)", definition.id());
        definitions = Collections.unmodifiableMap(next);
    }
    public static void clear() { definitions = Map.of(); }
    public static void replaceServer(Collection<BossDefinition> bosses) { definitions = table(bosses); }
    public static void replaceClientFallback(Collection<BossDefinition> bosses) { clientFallback = table(bosses); }
    private static Map<ResourceLocation, BossDefinition> table(Collection<BossDefinition> bosses) {
        var result = new LinkedHashMap<ResourceLocation, BossDefinition>();
        bosses.forEach(b -> result.put(b.id(), b));
        return Collections.unmodifiableMap(result);
    }
    @Nullable public static BossDefinition get(ResourceLocation id) { return definitions.get(id); }
    public static Collection<BossDefinition> all() { return definitions.values(); }
    @Nullable public static BossDefinition clientGet(ResourceLocation id) { return clientTable().get(id); }
    public static Collection<BossDefinition> clientAll() { return clientTable().values(); }
    private static Map<ResourceLocation, BossDefinition> clientTable() {
        var synced = clientSynced;
        return synced == null ? clientFallback : synced;
    }
    public static void beginClientSync() { clientPending = new LinkedHashMap<>(); }
    public static void addClientSync(BossDefinition boss) {
        if (clientPending != null) clientPending.put(boss.id(), boss);
    }
    public static void finishClientSync() {
        if (clientPending == null) return;
        clientSynced = Collections.unmodifiableMap(new LinkedHashMap<>(clientPending));
        clientPending = null;
    }
    public static void clearClientSync() { clientPending = null; clientSynced = null; }
}
