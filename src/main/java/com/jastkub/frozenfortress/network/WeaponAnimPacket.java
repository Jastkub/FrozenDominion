package com.jastkub.frozenfortress.network;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * A player's body animation for a legendary weapon's skill (07.10.2026), sent to them and everyone who can see them
 * (FFNetwork#playerAnim). A null animation fades back to their own movement.
 */
public final class WeaponAnimPacket {

    final int entity;
    @Nullable
    final ResourceLocation anim;
    final int fade;

    public WeaponAnimPacket(int entity, @Nullable ResourceLocation anim, int fade) {
        this.entity = entity;
        this.anim = anim;
        this.fade = fade;
    }

    public static void encode(WeaponAnimPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entity);
        buf.writeBoolean(p.anim != null);
        if (p.anim != null) {
            buf.writeResourceLocation(p.anim);
        }
        buf.writeVarInt(p.fade);
    }

    public static WeaponAnimPacket decode(FriendlyByteBuf buf) {
        int e = buf.readVarInt();
        ResourceLocation a = buf.readBoolean() ? buf.readResourceLocation() : null;
        return new WeaponAnimPacket(e, a, buf.readVarInt());
    }

    public static void handle(WeaponAnimPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.jastkub.frozenfortress.client.anim.WeaponAnimClient.play(p.entity, p.anim, p.fade));
        ctx.get().setPacketHandled(true);
    }
}
