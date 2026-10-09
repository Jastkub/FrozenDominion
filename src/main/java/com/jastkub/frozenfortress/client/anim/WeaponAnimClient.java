package com.jastkub.frozenfortress.client.anim;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/** The guard in front of WeaponAnims: PlayerAnimator is optional, and this is the only door to it. */
public final class WeaponAnimClient {

    private static Boolean present;

    private WeaponAnimClient() {
    }

    public static boolean present() {
        if (present == null) {
            present = ModList.get().isLoaded("playeranimator");
        }
        return present;
    }

    public static void init() {
        if (present()) {
            WeaponAnims.init();
        }
    }

    /** From WeaponAnimPacket: the entity's animation, or a fade back to nothing when `id` is null. */
    public static void play(int entityId, ResourceLocation id, int fade) {
        if (!present() || Minecraft.getInstance().level == null) {
            return;
        }
        if (Minecraft.getInstance().level.getEntity(entityId) instanceof AbstractClientPlayer p) {
            WeaponAnims.play(p, id, fade);
            if (id != null && id.getPath().startsWith("roll_")) {
                ROLLING.put(entityId, p.level().getGameTime() + ROLL_TICKS);
            }
        }
    }

    /** A roll clip's length (tools/gen_roll_anims.py END), and a tick to spare. */
    private static final int ROLL_TICKS = 13;
    /** Who is in a roll on this client, and until when (game time). */
    private static final java.util.Map<Integer, Long> ROLLING = new java.util.HashMap<>();

    /**
     * THE BODY FACES WHERE THE HEAD DOES THROUGH A ROLL: the game turns a body after the way it moves, and a roll to the side or back had it
     * turned up to seventy-five degrees off the face - the clip turning over that skewed body, and once it was over the
     * body swinging back round. Held to the head's yaw every tick of the roll, there is nothing to swing back.
     */
    public static void alignRollers() {
        Minecraft mc = Minecraft.getInstance();
        if (ROLLING.isEmpty() || mc.level == null) {
            ROLLING.clear();
            return;
        }
        long now = mc.level.getGameTime();
        java.util.Iterator<java.util.Map.Entry<Integer, Long>> it = ROLLING.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<Integer, Long> e = it.next();
            if (now > e.getValue() || !(mc.level.getEntity(e.getKey()) instanceof net.minecraft.world.entity.LivingEntity l)) {
                it.remove();
                continue;
            }
            l.yBodyRot = l.yHeadRot;
            l.yBodyRotO = l.yHeadRotO;
        }
    }
}
