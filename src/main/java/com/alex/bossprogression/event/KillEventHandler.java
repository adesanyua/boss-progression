package com.alex.bossprogression.event;

import com.alex.bossprogression.boss.BossDefinition;
import com.alex.bossprogression.boss.BossProgressManager;
import com.alex.bossprogression.boss.BossRegistry;
import com.alex.bossprogression.condition.BossCondition;
import com.alex.bossprogression.condition.KillCondition;
import com.alex.bossprogression.condition.KillTagCondition;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.core.RegistryAccess;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Server-side kill tracking. Resolves the real {@link ServerPlayer} that earned the kill (following
 * the damage source to its owning entity, so projectiles count for the shooter) and forwards matching
 * kills to {@link BossProgressManager}. It never mutates progress itself.
 */
public final class KillEventHandler {

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Level level = dead.level();
        if (level.isClientSide()) {
            return; // authoritative progress is server-side only
        }

        ServerPlayer killer = resolveKiller(event.getSource());
        if (killer == null) {
            return;
        }

        ResourceLocation killedId = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType());
        Holder<EntityType<?>> killedHolder = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(dead.getType());
        RegistryAccess registries = level.registryAccess();
        for (BossDefinition definition : BossRegistry.all()) {
            for (BossCondition condition : definition.conditions()) {
                if (condition instanceof KillCondition kill && kill.matches(killedId) && kill.filters().matches(dead, killer, event.getSource())) {
                    BossProgressManager.increment(killer, definition.id(), kill.id(), 1);
                } else if (condition instanceof KillTagCondition tag) {
                    boolean inTag = registries.registryOrThrow(Registries.ENTITY_TYPE)
                            .getTag(tag.tag())
                            .map(set -> set.contains(killedHolder))
                            .orElse(false);
                    if (inTag && tag.filters().matches(dead, killer, event.getSource())) {
                        BossProgressManager.increment(killer, definition.id(), tag.id(), 1);
                    }
                }
            }
        }
    }

    /**
     * The player who should be credited with the kill. {@link DamageSource#getEntity()} returns the
     * owning player for projectiles, but the direct attacker may itself be a non-player mob; in that
     * case no player is credited.
     */
    private static ServerPlayer resolveKiller(DamageSource source) {
        Entity attacker = source.getEntity();
        return attacker instanceof ServerPlayer player ? player : null;
    }
}
