package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.network.FFNetwork;
import com.jastkub.frozenfortress.network.RollPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * THE ROLL'S KEY (07.10.2026): X unless the player has moved it (Controls, "Frozen Dominion"). Pressed on the ground,
 * it sends the way the movement keys point - turned into the world by where the player faces - and the server makes
 * them untouchable for its length (FFRoll).
 *
 * <p>THE MOVE ITSELF IS DRIVEN HERE. A player's own position is the
 * client's to compute; the server's one push used to arrive a packet late and be eaten by the floor's friction in two
 * or three blocks. Now the speed is set every tick of the roll, before the player moves (ClientTickEvent START), along
 * {@link #SPEED}: six blocks and more, quick out of the blocks and easing at the end. The checks the server
 * makes before it rolls anyone are made here too, so the client never rolls when the server would not.
 */
public final class ClientRoll {

    public static final KeyMapping ROLL = new KeyMapping("key.frozen_dominion.roll", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.frozen_dominion");

    /** Blocks per tick through the roll (its sum, plus the walk the keys add, is how far it goes). */
    private static final double[] SPEED = {1.0D, 1.0D, 0.95D, 0.85D, 0.72D, 0.58D, 0.45D, 0.32D, 0.2D};   // (+1, "ociupinke za krotki")

    /** The roll under way: its tick (-1: none), its way, and the game time the next one may start. */
    private static int rollTick = -1;
    private static double rollX, rollZ;
    private static long nextRoll;

    private ClientRoll() {
    }

    @Mod.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(ROLL);
        }
    }

    @Mod.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
    public static final class Ticks {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (event.phase == TickEvent.Phase.START) {
                drive(p);
                return;
            }
            com.jastkub.frozenfortress.client.anim.WeaponAnimClient.alignRollers();
            while (ROLL.consumeClick()) {
                if (p == null || mc.screen != null || !p.onGround() || p.isPassenger() || p.isFallFlying()
                        || p.isSleeping() || rollTick >= 0 || p.level().getGameTime() < nextRoll
                        || p.getFoodData().getFoodLevel() <= 6 && !p.getAbilities().instabuild) {
                    continue;
                }
                float fwd = p.input.forwardImpulse, side = p.input.leftImpulse;
                double yaw = Math.toRadians(p.getYRot());
                double sin = Math.sin(yaw), cos = Math.cos(yaw);
                // the movement keys, turned to the world: forward is -sin, cos; left is cos, sin
                float dx = (float) (-sin * fwd + cos * side);
                float dz = (float) (cos * fwd + sin * side);
                FFNetwork.CHANNEL.sendToServer(new RollPacket(dx, dz));
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len < 1.0E-4D) {                    // standing still: the way they face
                    dx = (float) -sin;
                    dz = (float) cos;
                    len = 1.0D;
                }
                rollX = dx / len;
                rollZ = dz / len;
                rollTick = 0;
                nextRoll = p.level().getGameTime() + com.jastkub.frozenfortress.event.FFRoll.COOLDOWN;
            }
        }

        /** Before the player moves this tick: the roll's speed for this tick of it. */
        private static void drive(LocalPlayer p) {
            if (rollTick < 0) {
                return;
            }
            if (p == null || !p.isAlive() || p.isPassenger() || rollTick >= SPEED.length) {
                rollTick = -1;
                return;
            }
            double v = SPEED[rollTick++];
            p.setDeltaMovement(rollX * v, Math.min(0.0D, p.getDeltaMovement().y), rollZ * v);
        }
    }
}
