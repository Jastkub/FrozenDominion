package com.jastkub.frozenfortress.network;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The mod's packets, as NeoForge 1.21.1's payloads: each a record with its type and stream codec, registered here on
 * the mod bus. Handlers run on the main thread (the registrar's default). The old channel's calls are kept as thin
 * forwards (CHANNEL.sendToServer, send, playerAnim) so the rest of the mod did not have to change.
 */
@EventBusSubscriber(modid = FrozenFortress.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class FFNetwork {

    private static final String VERSION = "1";

    /** What the 1.20.1 code called the channel: only what it was used for. */
    public static final Channel CHANNEL = new Channel();

    public static final class Channel {
        private Channel() {
        }

        public void sendToServer(CustomPacketPayload payload) {
            PacketDistributor.sendToServer(payload);
        }
    }

    private FFNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(VERSION);
        r.playToClient(BossScenePacket.TYPE, BossScenePacket.STREAM_CODEC, BossScenePacket::handle);
        r.playToClient(HeartColdPacket.TYPE, HeartColdPacket.STREAM_CODEC, HeartColdPacket::handle);
        r.playToServer(RiderStrikePacket.TYPE, RiderStrikePacket.STREAM_CODEC, RiderStrikePacket::handle);
        r.playToServer(RollPacket.TYPE, RollPacket.STREAM_CODEC, RollPacket::handle);
        r.playToClient(WeaponAnimPacket.TYPE, WeaponAnimPacket.STREAM_CODEC, WeaponAnimPacket::handle);
    }

    public static void playerAnim(net.minecraft.world.entity.player.Player who,
                                  @javax.annotation.Nullable net.minecraft.resources.ResourceLocation anim, int fade) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(who, new WeaponAnimPacket(who.getId(), anim, fade));
    }

    public static void send(ServerPlayer player, CustomPacketPayload message) {
        PacketDistributor.sendToPlayer(player, message);
    }
}
