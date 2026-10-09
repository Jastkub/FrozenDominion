package com.jastkub.frozenfortress.network;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A boss's scene for this player: its entrance or its death (BossCutscenes on the server, BossScenes on the client).
 * With where the boss stood and which way it faced as it began - the frame its shots are laid out in, there on this
 * client even before the boss itself is. (bossType - not "type", which is the payload's own type().)
 */
public record BossScenePacket(int entityId, byte kind, short ticks, String bossType, double x, double y, double z,
                              float yaw) implements CustomPacketPayload {

    public static final Type<BossScenePacket> TYPE = new Type<>(FrozenFortress.id("boss_scene"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossScenePacket> STREAM_CODEC =
            StreamCodec.of(BossScenePacket::encode, BossScenePacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, BossScenePacket p) {
        buf.writeVarInt(p.entityId);
        buf.writeByte(p.kind);
        buf.writeShort(p.ticks);
        buf.writeUtf(p.bossType, 128);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeFloat(p.yaw);
    }

    private static BossScenePacket decode(RegistryFriendlyByteBuf buf) {
        return new BossScenePacket(buf.readVarInt(), buf.readByte(), buf.readShort(), buf.readUtf(128), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readFloat());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BossScenePacket p, IPayloadContext ctx) {
        com.jastkub.frozenfortress.client.BossScenes.start(p.entityId, p.kind, p.ticks, p.bossType, p.x, p.y, p.z, p.yaw);
    }
}
