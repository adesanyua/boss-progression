package com.alex.bossprogression.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The real data payload of an {@link InvitationItem}, stored in a stack {@code Data Component}
 * (never in the display name or lore, which are cosmetic and client-editable).
 *
 * @param bossId     the encounter this invitation belongs to
 * @param dimension  dimension where the dungeon was generated
 * @param pos        dungeon origin coordinates
 */
public record InvitationData(ResourceLocation bossId, ResourceKey<Level> dimension, BlockPos pos) {
    public static final Codec<InvitationData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.fieldOf("boss_id").forGetter(InvitationData::bossId),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(InvitationData::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(InvitationData::pos)
    ).apply(inst, InvitationData::new));

    public static final StreamCodec<ByteBuf, InvitationData> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);
}
