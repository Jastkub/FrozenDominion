package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.BoneLordEntity;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * THE BONE LORD'S WEIGHT, on this client: every footfall of his run thuds and knocks the camera for whoever is within
 * twenty-odd blocks, and every blow that lands (the stomp, the slam, his own fall - BoneLordEntity.impactPacked) shakes
 * the whole hall (ScreenShake).
 *
 * <p>The footfalls ride the clip's own foot-plants (its sound keyframes, read off the entity), so the thud is on the
 * frame the foot lands at any pace. A clip only runs while he is drawn, though - and a giant running at your back is
 * exactly when you should feel him - so when the clip has gone quiet while he is plainly running, a clock of the clip's
 * own stride stands in for it.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class BoneLordClient {

    /** One footfall's shake: degrees at his foot, how far it carries, how long it lasts. */
    private static final float STEP = 0.8F, SPRINT_STEP = 1.0F, STEP_REACH = 26.0F;
    private static final int STEP_TICKS = 8;
    /** A blow's: degrees at full strength, how far, how long. */
    private static final float BLOW = 3.2F, BLOW_REACH = 40.0F;
    private static final int BLOW_TICKS = 16;
    /** Half a stride of the clips (gen_bone_lord RUN_LEN 1.84 s, SPRINT_LEN 1.57 s), in ticks - the stand-in clock. */
    private static final int RUN_HALF = 18, SPRINT_HALF = 16;
    /** How far a footfall is heard: the event's volume (its range is sixteen blocks a unit - its loudness is in the
     *  sample itself, tools/gen_bone_lord_step.py, the game holding a sound's gain at full). */
    private static final float STEP_VOLUME = 4.0F;

    /** In his fist last tick (the game's "Shift to dismount" put away - Shift does not, BoneLordGrip). */
    private static boolean held;

    private static final Map<BoneLordEntity, Integer> SEEN_IMPACT = new WeakHashMap<>();
    private static final Map<BoneLordEntity, Integer> CLOCK = new WeakHashMap<>();

    private BoneLordClient() {
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        boolean inFist = mc.player.getVehicle() instanceof BoneLordEntity;
        if (inFist && !held) {
            mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.empty(), false);
        }
        held = inFist;
        for (BoneLordEntity lord : level.getEntitiesOfClass(BoneLordEntity.class, mc.player.getBoundingBox().inflate(80.0D))) {
            if (!lord.isAlive() || lord.isDormant()) {
                SEEN_IMPACT.put(lord, lord.impactPacked());
                continue;
            }
            // ---- a blow that landed
            int packed = lord.impactPacked();
            Integer seen = SEEN_IMPACT.put(lord, packed);
            if (seen != null && seen != packed) {
                float power = (packed & 0xFF) / 255.0F;
                Vec3 at = lord.position().add(Vec3.directionFromRotation(0.0F, lord.attackYaw()).scale(3.0D));
                ScreenShake.shake(at, BLOW * power, BLOW_REACH, BLOW_TICKS);
            }
            // ---- his footfalls
            int foot = lord.takeStep();
            boolean running = lord.getAttackState() == 0 && lord.onGround()
                    && (lord.getX() - lord.xo) * (lord.getX() - lord.xo) + (lord.getZ() - lord.zo) * (lord.getZ() - lord.zo)
                    > 0.0025D;
            if (foot == 0 && running && lord.ticksSinceClipStep() > 30) {
                int clock = CLOCK.merge(lord, 1, Integer::sum);
                int half = lord.isBursting() ? SPRINT_HALF : RUN_HALF;
                if (clock % half == 0) {
                    foot = (clock / half) % 2 == 0 ? 1 : 2;
                }
            } else if (!running) {
                CLOCK.put(lord, 0);
            }
            if (foot != 0) {
                footfall(level, lord, foot);
            }
        }
    }

    private static void footfall(ClientLevel level, BoneLordEntity lord, int foot) {
        // the foot: a block forward of him and a block to its side
        float yaw = lord.yBodyRot;
        Vec3 fwd = Vec3.directionFromRotation(0.0F, yaw);
        Vec3 right = new Vec3(-fwd.z, 0.0D, fwd.x);
        Vec3 at = lord.position().add(fwd.scale(1.0D)).add(right.scale(foot == 1 ? -1.0D : 1.0D));
        boolean sprint = lord.isBursting();
        ScreenShake.shake(at, sprint ? SPRINT_STEP : STEP, STEP_REACH, STEP_TICKS);
        // LOUD: his own footfall, made to be heard on
        // any speakers - the Monstrosity's step he borrowed sat in the bass, quiet - and the rattle of bone over it
        float pitch = (sprint ? 0.98F : 0.9F) + level.random.nextFloat() * 0.1F;
        level.playLocalSound(at.x, at.y, at.z, FFSounds.BONE_LORD_STEP.get(), SoundSource.HOSTILE, STEP_VOLUME, pitch, false);
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.SKELETON_STEP, SoundSource.HOSTILE, 2.0F, 0.45F, false);
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.BONE_BLOCK_STEP, SoundSource.HOSTILE, 1.6F, 0.5F, false);
    }
}
