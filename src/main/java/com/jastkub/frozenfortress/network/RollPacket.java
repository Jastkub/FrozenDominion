package com.jastkub.frozenfortress.network;

import com.jastkub.frozenfortress.event.FFRoll;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** The roll key pressed (07.10.2026): which way its player's movement keys point, in the world (FFRoll#roll). */
public final class RollPacket {

    final float dx, dz;

    public RollPacket(float dx, float dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public static void encode(RollPacket p, FriendlyByteBuf buf) {
        buf.writeFloat(p.dx);
        buf.writeFloat(p.dz);
    }

    public static RollPacket decode(FriendlyByteBuf buf) {
        return new RollPacket(buf.readFloat(), buf.readFloat());
    }

    public static void handle(RollPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer who = ctx.get().getSender();
        if (who != null && Float.isFinite(p.dx) && Float.isFinite(p.dz)) {
            FFRoll.roll(who, Math.max(-1.0F, Math.min(1.0F, p.dx)), Math.max(-1.0F, Math.min(1.0F, p.dz)));
        }
    }
}
