package com.alex.bossprogression.item;

import com.alex.bossprogression.registry.ModComponents;
import com.alex.bossprogression.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The invitation handed out after a boss dungeon is generated. Its real payload
 * ({@link InvitationData}) lives in a data component; the tooltip is derived from that component and
 * is therefore purely a view, never the source of truth. Right-clicking echoes the coordinates to
 * chat (server-side). It never teleports the player.
 */
public final class InvitationItem extends Item {
    public InvitationItem(Properties properties) {
        super(properties);
    }

    /** Builds an invitation stack whose authoritative data is stored in the data component. */
    public static ItemStack create(InvitationData data) {
        ItemStack stack = new ItemStack(ModItems.INVITATION.get());
        stack.set(ModComponents.INVITATION.get(), data);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        InvitationData data = stack.get(ModComponents.INVITATION.get());
        if (data == null) {
            tooltip.add(Component.translatable("item.bossprogression.invitation.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.bossprogression.invitation.flavor", data.bossId().getPath())
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("item.bossprogression.invitation.dungeon",
                        data.pos().getX(), data.pos().getY(), data.pos().getZ())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.bossprogression.invitation.dimension",
                        data.dimension().location().toString())
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        InvitationData data = stack.get(ModComponents.INVITATION.get());
        if (data != null && !level.isClientSide()) {
            BlockPos pos = data.pos();
            player.sendSystemMessage(Component.translatable("chat.bossprogression.invitation.coords",
                    data.bossId().getPath(), data.dimension().location().toString(),
                    pos.getX(), pos.getY(), pos.getZ()));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
