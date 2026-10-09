package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.StormEyeFloeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * STANDING ON MOVING ICE (Oko Burzy, 07.10.2026: the floes of the rings turn, breathe and rise - StormEyeFloeEntity).
 * A player's own position is the client's to compute, so carrying them is done here, the way ClientRoll drives the
 * roll:
 *
 * <ol>
 *   <li>AT THE START OF THE TICK (ClientTickEvent START), before anything moves: the floes' clock goes on a tick and
 *       every floe takes its step - its last place kept, its new one taken, its boxes moved under it. Whoever moves
 *       this tick moves against the floes where they now are.</li>
 *   <li>IN THE PLAYER'S OWN TICK, after the level has kept their old place for the drawing and before they move
 *       (EntityTickEvent.Pre): if they were standing on a floe, they are moved with it - along, up or down, and round its
 *       middle as it turns. Done here and not at the start of the tick: the level overwrites the old place with the
 *       current one just before the player's tick, so a carry made at the start would land between two frames, a
 *       jerk twenty times a second; made here, it is drawn smoothly from the old place to the new, with the ice.</li>
 *   <li>EVERY FRAME (RenderTickEvent): the view turns with the floe they stand on. The local player's view is never
 *       drawn between ticks (it is the mouse's, frame by frame), so the turn is paid frame by frame as the mouse's is.</li>
 * </ol>
 * Jumping, flying and riding are not carried (as on a boat).
 */
public final class StormEyeFloeClient {

    /** The floe the last carry stood the player on (null: not on one), and the spin the view was last turned to. */
    @Nullable
    private static StormEyeFloeEntity riding;
    @Nullable
    private static StormEyeFloeEntity turnedWith;
    private static double lastSpin = Double.NaN;

    private StormEyeFloeClient() {
    }

    @net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent
        public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null) {
                riding = null;
                return;
            }
            StormEyeFloeEntity.advanceClientClock(level, mc.isPaused());
            if (mc.isPaused()) {
                return;
            }
            double t = StormEyeFloeEntity.clientClock(level);
            for (Entity e : level.entitiesForRendering()) {
                if (e instanceof StormEyeFloeEntity f && !f.isRemoved()) {
                    f.clientStep(t);
                }
            }
        }

        @SubscribeEvent
        public static void carry(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
            if (!(event.getEntity() instanceof LocalPlayer p) || p != Minecraft.getInstance().player) {
                return;
            }
            riding = null;
            if (!p.onGround() || p.isPassenger() || p.isSpectator() || p.getAbilities().flying || p.isFallFlying()) {
                return;
            }
            StormEyeFloeEntity under = null;
            for (Entity e : ((ClientLevel) p.level()).entitiesForRendering()) {
                if (e instanceof StormEyeFloeEntity f && !f.isRemoved() && f.carries(p)) {
                    under = f;
                    break;
                }
            }
            if (under == null) {
                return;
            }
            Vec3 from = under.stepFrom(), to = under.stepTo();
            double turn = under.stepTurn();
            // their place off the floe's middle, turned with it (Motion.local's sense: + turns from +X to +Z)
            double ox = p.getX() - from.x, oz = p.getZ() - from.z;
            double c = Math.cos(turn), s = Math.sin(turn);
            p.setPos(to.x + ox * c - oz * s, p.getY() + (to.y - from.y), to.z + ox * s + oz * c);
            riding = under;
        }

        /**
         * AND NOBODY GOES THROUGH IT: the floe's boxes are Forge parts, and Canary's faster entity collisions
         * (mixin.entity.collisions.movement) look for bodies section by section, where parts never are - the boxes
         * held nobody. So the floor is not left to the collision at all: at the END of the player's tick, whoever was
         * on or over a floe's disc as the tick began, and has come down to its top or through it within the tick
         * (not jumping), is stood back on its top - before the frame draws them, so no sinking is seen - their fall
         * stopped and on the ground, as a block would have left them.
         */
        @SubscribeEvent
        public static void floor(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof LocalPlayer p)
                    || p != Minecraft.getInstance().player || p.isPassenger() || p.isSpectator()
                    || p.getAbilities().flying || p.isFallFlying() || p.getDeltaMovement().y > 0.05D) {
                return;
            }
            for (Entity e : ((ClientLevel) p.level()).entitiesForRendering()) {
                if (!(e instanceof StormEyeFloeEntity f) || f.isRemoved()) {
                    continue;
                }
                double top = f.floorUnder(p);
                if (Double.isNaN(top) || p.yo < top - 0.6D || p.getY() > top + 1.0E-4D) {
                    continue;
                }
                p.setPos(p.getX(), top, p.getZ());
                p.setDeltaMovement(p.getDeltaMovement().x, 0.0D, p.getDeltaMovement().z);
                p.setOnGround(true);
                p.verticalCollision = true;
                p.verticalCollisionBelow = true;
                p.resetFallDistance();
                return;
            }
        }

        @SubscribeEvent
        public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            StormEyeFloeEntity f = riding;
            if (p == null || f == null || f.isRemoved() || mc.isPaused()) {
                lastSpin = Double.NaN;
                turnedWith = null;
                return;
            }
            double spin = f.renderSpin(event.getPartialTick().getGameTimeDeltaPartialTick(true));
            if (turnedWith == f && !Double.isNaN(lastSpin)) {
                // yaw + turn faces the way the floe has turned the ground under them (Motion.local's sense)
                float d = (float) Math.toDegrees(spin - lastSpin);
                p.setYRot(p.getYRot() + d);
                p.yRotO += d;
            }
            lastSpin = spin;
            turnedWith = f;
        }
    }
}
