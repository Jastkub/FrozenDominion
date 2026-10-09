package com.jastkub.frozenfortress.network;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The attack key on a saddled Monstrosity's back: its arm sweeps (HollowGolemEntity.riderSweep). */
public record RiderStrikePacket() implements CustomPacketPayload {

    public static final Type<RiderStrikePacket> TYPE = new Type<>(FrozenFortress.id("rider_strike"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RiderStrikePacket> STREAM_CODEC =
            StreamCodec.unit(new RiderStrikePacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RiderStrikePacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer who && who.getVehicle() instanceof HollowGolemEntity golem) {
            golem.riderSweep(who);
        }
    }
}
