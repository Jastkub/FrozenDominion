package com.jastkub.frozenfortress.network;

import com.jastkub.frozenfortress.FrozenFortress;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A player's body animation (PlayerAnimator, client/anim): its id, or none to fade back out over `fade` ticks. */
public record WeaponAnimPacket(int entity, Optional<ResourceLocation> animation, int fade) implements CustomPacketPayload {

    public static final Type<WeaponAnimPacket> TYPE = new Type<>(FrozenFortress.id("weapon_anim"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WeaponAnimPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WeaponAnimPacket::entity,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), WeaponAnimPacket::animation,
            ByteBufCodecs.VAR_INT, WeaponAnimPacket::fade, WeaponAnimPacket::new);

    public WeaponAnimPacket(int entity, @Nullable ResourceLocation anim, int fade) {
        this(entity, Optional.ofNullable(anim), fade);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WeaponAnimPacket p, IPayloadContext ctx) {
        com.jastkub.frozenfortress.client.anim.WeaponAnimClient.play(p.entity, p.animation.orElse(null), p.fade);
    }
}
