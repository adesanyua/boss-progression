package com.alex.bossprogression.dungeon;

import com.alex.bossprogression.BossProgressionMod;
import com.alex.bossprogression.boss.BossDefinition;
import com.alex.bossprogression.boss.BossProgressManager;
import com.alex.bossprogression.boss.BossRegistry;
import com.alex.bossprogression.boss.BossMobCustomizer;
import java.util.Objects;
import com.alex.bossprogression.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server-authoritative boss lifecycle for generated dungeons (MVP5).
 *
 * <p>Two responsibilities, both driven off the persisted {@link DungeonInstanceStore} so they survive
 * relog/restart and can never fire twice:
 * <ul>
 *   <li><b>Spawn</b> — when the owner walks into a dungeon that has not been activated yet, the boss
 *       entity (the definition's {@code bossEntity}) is summoned at the room centre exactly once; the
 *       instance is marked {@code activated} and remembers the boss {@code UUID}.</li>
 *   <li><b>Defeat</b> — when a dying entity's {@code UUID} matches a dungeon's stored boss, the
 *       instance is marked {@code defeated} and the owner's progress is flagged {@code DEFEATED}.</li>
 * </ul>
 * There is no client-side logic; nothing trusts the client.
 */
public final class BossSpawnManager {

    /**
     * Fires on the server only (guarded by {@link PlayerTickEvent.Post#getEntity()}'s level). Kept cheap:
     * it returns immediately unless this dimension actually holds dungeon instances.
     */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        DungeonInstanceStore store = level.getData(ModAttachments.DUNGEON_STORE.get());
        if (store.instances().isEmpty()) {
            return;
        }
        BlockPos here = player.blockPosition();
        for (DungeonInstance instance : store.instances().values()) {
            if (!instance.owner().equals(player.getUUID()) || instance.activated() || instance.defeated()
                    || instance.destruction() != null && instance.destruction().capturing()) {
                continue;
            }
            BossDefinition definition = BossRegistry.get(instance.bossId());
            if (definition == null || definition.dungeon() == null
                    || BossProgressManager.state(player, instance.bossId()).map(s ->
                        s.defeated() || !Objects.equals(s.encounterId(), instance.encounterId())
                        || !Objects.equals(s.dungeonPos(), instance.pos())).orElse(true)) {
                continue;
            }
            double radius = instance.activationRadius();
            if (here.closerThan(instance.spawnPos(), radius)) {
                spawnBoss(level, instance, definition, store);
            }
        }
    }

    private static void spawnBoss(ServerLevel level, DungeonInstance instance,
                                  BossDefinition definition, DungeonInstanceStore store) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(definition.bossEntity());
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(definition.bossEntity())) {
            BossProgressionMod.LOGGER.warn("Boss entity {} for {} is unavailable; not spawning (dungeon stays retryable)",
                    definition.bossEntity(), definition.id());
            return;
        }
        BlockPos spawnAt = instance.spawnPos();
        if (!EncounterPlacement.safe(level, spawnAt, definition)) return; // terrain changed or NBT spawn is blocked
        Entity spawned = type.create(level);
        if (!(spawned instanceof Mob mob)) {
            BossProgressionMod.LOGGER.warn("Boss entity {} is not a Mob; not spawning", definition.bossEntity());
            return;
        }
        mob.setPos(spawnAt.getX() + 0.5, spawnAt.getY(), spawnAt.getZ() + 0.5);
        if (!BossMobCustomizer.apply(mob, definition)) return;
        if (!level.noCollision(mob, mob.getBoundingBox())) return; // configured scale/equipment may change geometry
        if (!level.addFreshEntity(mob)) return; // canceled spawn must remain retryable

        instance.setActivated(true);
        instance.setBossUuid(mob.getUUID());
        level.setData(ModAttachments.DUNGEON_STORE.get(), store);
        BossProgressionMod.LOGGER.info("Boss {} ({}) spawned at {} for {}",
                definition.id(), definition.bossEntity(), spawnAt.toShortString(),
                playerOrNull(level.getServer(), instance.owner()));
    }

    /** Marks the owning encounter DEFEATED when its stored boss dies. Idempotent per instance. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Level level = dead.level();
        if (level.isClientSide()) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        DungeonInstanceStore store = serverLevel.getData(ModAttachments.DUNGEON_STORE.get());
        DungeonInstance instance = store.byBossUuid(dead.getUUID());
        if (instance == null || instance.defeated()) {
            return;
        }
        instance.setDefeated(true);
        DungeonDestructionManager.onDefeated(serverLevel, instance);
        serverLevel.setData(ModAttachments.DUNGEON_STORE.get(), store);

        MinecraftServer server = serverLevel.getServer();
        ServerPlayer owner = server.getPlayerList().getPlayer(instance.owner());
        if (owner != null) {
            BossProgressManager.markDefeated(owner, instance);
        }
        BossProgressionMod.LOGGER.info("Boss {} defeated at {} (owner online: {})",
                instance.bossId(), instance.pos().toShortString(), owner != null);
    }

    private static String playerOrNull(MinecraftServer server, java.util.UUID owner) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        return player != null ? player.getGameProfile().getName() : owner.toString();
    }
}
