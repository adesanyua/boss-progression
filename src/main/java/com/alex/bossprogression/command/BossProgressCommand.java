package com.alex.bossprogression.command;

import com.alex.bossprogression.BossProgressionMod;
import com.alex.bossprogression.boss.BossDefinition;
import com.alex.bossprogression.boss.BossProgressManager;
import com.alex.bossprogression.boss.BossRegistry;
import com.alex.bossprogression.boss.BossState;
import com.alex.bossprogression.condition.BossCondition;
import com.alex.bossprogression.dungeon.DungeonManager;
import com.alex.bossprogression.item.InvitationData;
import com.alex.bossprogression.item.InvitationItem;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.SharedSuggestionProvider;
import com.mojang.brigadier.context.CommandContext;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * {@code /bossprogress} developer/testing command (Stage 12). Everything runs server-side against the
 * executing player's own authoritative data; the whole progression pipeline can be driven from here
 * without a client grind: inspect ({@code list}/{@code progress}), force ({@code unlock}), reset
 * ({@code reset}), regenerate dungeon ({@code generate}) and re-issue the invitation
 * ({@code invitation}). Datapack content reload is handled by vanilla {@code /reload}, which already
 * re-runs {@link com.alex.bossprogression.data.BossDefinitionLoader}.
 *
 * <p>Gated at permission level 2 so a random player cannot force-unlock their own encounter.
 */
public final class BossProgressCommand {

    private BossProgressCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bossprogress")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(BossProgressCommand::list))
                .then(Commands.literal("progress").then(bossArgument().executes(BossProgressCommand::progress)))
                .then(Commands.literal("unlock").then(bossArgument().executes(BossProgressCommand::unlock)))
                .then(Commands.literal("generate").then(bossArgument().executes(BossProgressCommand::generate)))
                .then(Commands.literal("invitation").then(bossArgument().executes(BossProgressCommand::invitation)))
                .then(Commands.literal("reset")
                        .then(Commands.literal("all").executes(BossProgressCommand::resetAll))
                        .then(bossArgument().executes(BossProgressCommand::resetOne))));
    }

    /** Resolves the executing player, or sends a failure and returns {@code null} for console sources. */
    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("This command must be run by a player."));
        }
        return player;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> bossArgument() {
        return Commands.argument("boss", ResourceLocationArgument.id()).suggests((context, builder) ->
                SharedSuggestionProvider.suggestResource(BossRegistry.all().stream().map(BossDefinition::id), builder));
    }

    private static ResourceLocation bossId(CommandContext<CommandSourceStack> ctx) {
        return ResourceLocationArgument.getId(ctx, "boss");
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        if (BossRegistry.all().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No boss definitions loaded.").withStyle(ChatFormatting.RED), true);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Boss encounters:").withStyle(ChatFormatting.GOLD), true);
        for (BossDefinition definition : BossRegistry.all()) {
            BossState state = player.getData(com.alex.bossprogression.registry.ModAttachments.BOSS_PROGRESS.get())
                    .get(definition.id());
            final String line = " - " + definition.id() + ": " + statusOf(state);
            ctx.getSource().sendSuccess(() -> Component.literal(line), true);
        }
        return BossRegistry.all().size();
    }

    private static String statusOf(BossState state) {
        if (state == null) {
            return "LOCKED";
        }
        if (state.defeated()) {
            return "DEFEATED";
        }
        if (state.unlocked()) {
            return state.dungeonGenerated() ? "DUNGEON_DISCOVERED" : "READY";
        }
        return "LOCKED";
    }

    private static int progress(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        ResourceLocation id = bossId(ctx);
        String raw = id.toString();
        BossDefinition definition = id == null ? null : BossRegistry.get(id);
        if (definition == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown boss: " + raw));
            return 0;
        }
        Optional<BossState> stateOpt = BossProgressManager.state(player, id);
        BossState state = stateOpt.orElseGet(BossState::new);
        ctx.getSource().sendSuccess(() -> Component.literal(definition.id() + " [" + statusOf(state) + "]")
                .withStyle(ChatFormatting.GOLD), true);
        for (BossCondition condition : definition.conditions()) {
            int cur = state.progress(condition.id());
            int req = condition.requiredCount();
            ctx.getSource().sendSuccess(() -> Component.literal("   " + condition.id() + ": " + cur + "/" + req), true);
        }
        return 1;
    }

    private static int unlock(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        ResourceLocation id = bossId(ctx);
        if (id == null || BossRegistry.get(id) == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown boss."));
            return 0;
        }
        boolean changed = BossProgressManager.forceUnlock(player, id);
        ctx.getSource().sendSuccess(() -> Component.literal(
                (changed ? "Unlocked " : "Already unlocked ") + id), true);
        return 1;
    }

    private static int generate(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        ResourceLocation id = bossId(ctx);
        BossDefinition definition = id == null ? null : BossRegistry.get(id);
        if (definition == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown boss."));
            return 0;
        }
        if (!BossProgressManager.isUnlocked(player, id)) {
            ctx.getSource().sendFailure(Component.literal("Boss must be unlocked before generating a dungeon."));
            return 0;
        }
        DungeonManager.onUnlocked(player, definition);
        ctx.getSource().sendSuccess(() -> Component.literal("Dungeon generation requested for " + id
                + " (see server log; skipped if already generated)."), true);
        return 1;
    }

    private static int invitation(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        ResourceLocation id = bossId(ctx);
        BossDefinition definition = id == null ? null : BossRegistry.get(id);
        if (definition == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown boss."));
            return 0;
        }
        BossState state = player.getData(com.alex.bossprogression.registry.ModAttachments.BOSS_PROGRESS.get()).get(id);
        if (state == null || !state.dungeonGenerated()) {
            ctx.getSource().sendFailure(Component.literal("No generated dungeon to invite to. Use 'generate' first."));
            return 0;
        }
        ResourceKey<Level> dim = state.dungeonDimension();
        BlockPos pos = state.dungeonPos();
        ItemStack stack = InvitationItem.create(new InvitationData(id, dim, pos));
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Gave invitation for " + id
                + " at " + pos.toShortString() + " in " + dim.location()), true);
        return 1;
    }

    private static int resetOne(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        ResourceLocation id = bossId(ctx);
        if (id == null) {
            ctx.getSource().sendFailure(Component.literal("Bad boss id."));
            return 0;
        }
        BossProgressManager.reset(player, id);
        BossProgressionMod.LOGGER.info("Admin {} force-reset boss {} for player {}", ctx.getSource().getTextName(), id, player.getGameProfile().getName());
        ctx.getSource().sendSuccess(() -> Component.literal("Reset progress for " + id), true);
        return 1;
    }

    private static int resetAll(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        BossProgressManager.resetAll(player);
        BossProgressionMod.LOGGER.info("Admin {} force-reset all bosses for player {}", ctx.getSource().getTextName(), player.getGameProfile().getName());
        ctx.getSource().sendSuccess(() -> Component.literal("Reset all boss progress."), true);
        return 1;
    }
}
