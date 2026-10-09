package com.jastkub.frozenfortress.network;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** How deep a Frost Heart's cold has got into this player (the frost on their screen, ColdOverlay). */
public record HeartColdPacket(float depth) implements CustomPacketPayload {

    public static final Type<HeartColdPacket> TYPE = new Type<>(FrozenFortress.id("heart_cold"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HeartColdPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.FLOAT, HeartColdPacket::depth, HeartColdPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HeartColdPacket p, IPayloadContext ctx) {
        com.jastkub.frozenfortress.client.ColdOverlay.heartCold(p.depth);
    }
}
