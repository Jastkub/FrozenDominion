package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.network.FFNetwork;
import com.jastkub.frozenfortress.registry.FFSounds;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * THE ROLL: a
 * dodge roll on its own key (X by default, ClientRoll). Some five blocks along the way you are moving - or the way
 * you face, standing still - low and quick, and for the moment of it nothing that strikes you lands: blows, arrows, the
 * bosses' things (the Monstrosity's avalanche passes over whoever rolls through its front). Not the fall, the void,
 * drowning or the cold - only what is aimed. A breath between two (COOLDOWN), and it costs a little hunger.
 *
 * <p>The move is the client's (ClientRoll drives it tick by tick - a player's position is theirs to compute); here are
 * the untouchable ticks, the cooldown, the hunger, the sound, and the body: one of four clips by the way the roll goes
 * against the way they face (tools/gen_roll_anims.py - forward and back over the head, left and right over the
 * shoulder).
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class FFRoll {

    /** Its length in ticks, the untouchable part of it, and the wait before the next. */
    public static final int ROLL_T = 10, IFRAMES = 8, COOLDOWN = 30;
    private static final float HUNGER = 0.6F;

    private static final Map<UUID, Long> SAFE_UNTIL = new WeakHashMap<>();
    private static final Map<UUID, Long> NEXT = new WeakHashMap<>();

    private FFRoll() {
    }

    /** From RollPacket: `dx`, `dz` the way their keys point (both 0: the way they face). */
    public static void roll(ServerPlayer p, float dx, float dz) {
        long now = p.level().getGameTime();
        Long next = NEXT.get(p.getUUID());
        if ((next != null && now < next) || !p.isAlive() || p.isPassenger() || p.isSleeping() || p.isFallFlying()
                || p.isSpectator() || !p.onGround() || p.getFoodData().getFoodLevel() <= 6 && !p.isCreative()) {
            return;
        }
        Vec3 dir = new Vec3(dx, 0.0D, dz);
        if (dir.lengthSqr() < 1.0E-4D) {
            dir = new Vec3(p.getLookAngle().x, 0.0D, p.getLookAngle().z);
        }
        if (dir.lengthSqr() < 1.0E-4D) {
            return;
        }
        dir = dir.normalize();
        NEXT.put(p.getUUID(), now + COOLDOWN);
        SAFE_UNTIL.put(p.getUUID(), now + IFRAMES);
        p.causeFoodExhaustion(HUNGER);
        p.level().playSound(null, p.getX(), p.getY() + 0.5D, p.getZ(), FFSounds.BLADE_SWING.get(), SoundSource.PLAYERS,
                0.7F, 0.62F + p.getRandom().nextFloat() * 0.08F);
        // which way against the way they face: forward is -sin, cos; left is cos, sin
        double yaw = Math.toRadians(p.getYRot());
        double fwd = dir.x * -Math.sin(yaw) + dir.z * Math.cos(yaw);
        double left = dir.x * Math.cos(yaw) + dir.z * Math.sin(yaw);
        String clip = Math.abs(fwd) >= Math.abs(left) ? (fwd >= 0 ? "roll_forward" : "roll_back")
                : (left > 0 ? "roll_left" : "roll_right");
        FFNetwork.playerAnim(p, FrozenFortress.id(clip), 1);
    }

    /** In the untouchable part of a roll. */
    public static boolean rolling(LivingEntity e) {
        Long until = e instanceof Player ? SAFE_UNTIL.get(e.getUUID()) : null;
        return until != null && e.level().getGameTime() < until;
    }

    @SubscribeEvent
    public static void onRolledThrough(LivingAttackEvent event) {
        LivingEntity v = event.getEntity();
        if (v.level().isClientSide || !rolling(v)) {
            return;
        }
        var s = event.getSource();
        // only what is aimed at you: the world's own harms still find you mid-roll
        if (s.is(DamageTypes.FALL) || s.is(DamageTypes.FELL_OUT_OF_WORLD) || s.is(DamageTypes.DROWN)
                || s.is(DamageTypes.FREEZE) || s.is(DamageTypes.STARVE) || s.is(DamageTypes.IN_WALL)
                || s.is(DamageTypes.LAVA) || s.is(DamageTypes.IN_FIRE) || s.is(DamageTypes.ON_FIRE)
                || s.is(DamageTypes.GENERIC_KILL)) {
            return;
        }
        event.setCanceled(true);
    }
}
