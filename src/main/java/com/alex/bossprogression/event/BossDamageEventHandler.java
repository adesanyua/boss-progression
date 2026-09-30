package com.alex.bossprogression.event;

import com.alex.bossprogression.boss.BossMobCustomizer;
import com.alex.bossprogression.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Flat damage bonus before victim armor/resistance. Includes spells/projectiles attributed to the boss. */
public final class BossDamageEventHandler {
    @SubscribeEvent
    public void damage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Mob boss) || !(boss.level() instanceof ServerLevel level)) return;
        double extra = boss.getPersistentData().getDouble(BossMobCustomizer.DAMAGE_BONUS);
        if (!Double.isFinite(extra) || extra <= 0 || event.getAmount() <= 0) return;
        var instance = level.getData(ModAttachments.DUNGEON_STORE.get()).byBossUuid(boss.getUUID());
        if (instance == null || !instance.activated() || instance.defeated()) return;
        event.setAmount((float) Math.min(Float.MAX_VALUE, event.getAmount() + extra));
    }
}
