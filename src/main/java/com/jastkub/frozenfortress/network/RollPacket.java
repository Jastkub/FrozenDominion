package com.jastkub.frozenfortress.network;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.event.FFRoll;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The roll key pressed: which way its player's movement keys point, in the world (FFRoll#roll). */
public record RollPacket(float dx, float dz) implements CustomPacketPayload {

    public static final Type<RollPacket> TYPE = new Type<>(FrozenFortress.id("roll"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RollPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, RollPacket::dx, ByteBufCodecs.FLOAT, RollPacket::dz, RollPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RollPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer who && Float.isFinite(p.dx) && Float.isFinite(p.dz)) {
            FFRoll.roll(who, Math.max(-1.0F, Math.min(1.0F, p.dx)), Math.max(-1.0F, Math.min(1.0F, p.dz)));
        }
    }
}
