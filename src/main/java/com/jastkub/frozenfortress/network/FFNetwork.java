package com.jastkub.frozenfortress.network;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** The mod's one channel (06.10.2026): what the server has to tell one player only - a boss's scene, for him; how
 *  deep a heart's cold is in him (07.10.2026). */
public final class FFNetwork {

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(FrozenFortress.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private FFNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(BossScenePacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BossScenePacket::encode)
                .decoder(BossScenePacket::decode)
                .consumerMainThread(BossScenePacket::handle)
                .add();
        CHANNEL.messageBuilder(HeartColdPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HeartColdPacket::encode)
                .decoder(HeartColdPacket::decode)
                .consumerMainThread(HeartColdPacket::handle)
                .add();
        CHANNEL.messageBuilder(RiderStrikePacket.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RiderStrikePacket::encode)
                .decoder(RiderStrikePacket::decode)
                .consumerMainThread(RiderStrikePacket::handle)
                .add();
        CHANNEL.messageBuilder(RollPacket.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RollPacket::encode)
                .decoder(RollPacket::decode)
                .consumerMainThread(RollPacket::handle)
                .add();
        CHANNEL.messageBuilder(WeaponAnimPacket.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WeaponAnimPacket::encode)
                .decoder(WeaponAnimPacket::decode)
                .consumerMainThread(WeaponAnimPacket::handle)
                .add();
    }

    /** A legendary weapon's body animation on `who`, for them and everyone who sees them (null: fade back out). */
    public static void playerAnim(net.minecraft.world.entity.player.Player who,
                                  @javax.annotation.Nullable net.minecraft.resources.ResourceLocation anim, int fade) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> who),
                new WeaponAnimPacket(who.getId(), anim, fade));
    }

    public static void send(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
