package com.jastkub.frozenfortress.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A boss's scene for this player: its entrance or its death (BossCutscenes on the server, BossScenes on the client).
 * With where the boss stood and which way it faced as it began - the frame its shots are laid out in, there on this
 * client even before the boss itself is.
 */
public record BossScenePacket(int entityId, byte kind, short ticks, String type, double x, double y, double z,
                              float yaw) {

    public static void encode(BossScenePacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeByte(p.kind);
        buf.writeShort(p.ticks);
        buf.writeUtf(p.type, 128);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeFloat(p.yaw);
    }

    public static BossScenePacket decode(FriendlyByteBuf buf) {
        return new BossScenePacket(buf.readVarInt(), buf.readByte(), buf.readShort(), buf.readUtf(128), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readFloat());
    }

    public static void handle(BossScenePacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.jastkub.frozenfortress.client.BossScenes.start(p.entityId, p.kind, p.ticks, p.type, p.x, p.y, p.z,
                        p.yaw));
        ctx.get().setPacketHandled(true);
    }
}
