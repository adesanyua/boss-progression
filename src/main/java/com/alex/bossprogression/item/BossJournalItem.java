package com.alex.bossprogression.item;

import com.alex.bossprogression.client.BossJournalScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Boss Journal. Right-clicking opens {@link BossJournalScreen}, which is populated entirely from
 * the server-synced progression data (never from client state).
 *
 * <p>The client-only screen call is guarded by {@code level.isClientSide()}: on a dedicated server that
 * branch never executes, so the client classes are never loaded there.
 */
public final class BossJournalItem extends Item {
    public BossJournalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            BossJournalScreen.open();
        }
        // Consume the interaction on both sides; all displayed content comes from synced server data.
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
