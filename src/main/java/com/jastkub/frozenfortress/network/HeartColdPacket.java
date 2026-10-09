package com.jastkub.frozenfortress.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** How deep a heart's cold is in this player (0 to 1; FrostHeartBlockEntity), for ColdOverlay to show. */
public record HeartColdPacket(float depth) {

    public static void encode(HeartColdPacket p, FriendlyByteBuf buf) {
        buf.writeFloat(p.depth);
    }

    public static HeartColdPacket decode(FriendlyByteBuf buf) {
        return new HeartColdPacket(buf.readFloat());
    }

    public static void handle(HeartColdPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.jastkub.frozenfortress.client.ColdOverlay.heartCold(p.depth));
        ctx.get().setPacketHandled(true);
    }
}
