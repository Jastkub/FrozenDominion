package com.jastkub.frozenfortress.network;

import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** The rider of a saddled Monstrosity swung (07.10.2026): it sweeps its arm across its front (HollowGolemEntity#riderSweep). */
public final class RiderStrikePacket {

    public static void encode(RiderStrikePacket p, FriendlyByteBuf buf) {
    }

    public static RiderStrikePacket decode(FriendlyByteBuf buf) {
        return new RiderStrikePacket();
    }

    public static void handle(RiderStrikePacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer who = ctx.get().getSender();
        if (who != null && who.getVehicle() instanceof HollowGolemEntity golem) {
            golem.riderSweep(who);
        }
    }
}
