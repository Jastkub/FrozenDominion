package com.jastkub.frozenfortress.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * THE STAFF OF THE HOLLOW KING's rules, in one place (07.10.2026): who its spells strike, the King's MARK, the Tide's
 * HOLD and the toll's KNOCK-DOWN.
 *
 * <p>All three states live in the target's persistent data as a game time they end at, so any spell can ask "is this
 * one marked / held / knocked down" without a capability; each also has its picture (HollowStaffFxEntity kinds
 * "mark", "hold", "stun"), and the hold and the knock-down are enforced by that picture's tick ({@link #root}).
 *
 * <p><b>Who is struck.</b> {@link #isFoe}: never the caster, his shades, his tamed beasts, his team, a creative or
 * spectating player - and another player only where PvP is on. The areas (the Bell, the Tide) are pickier
 * ({@link #inTheFight}): what is hostile, or already in the fight with him (he struck it, it struck him, it hunts
 * him, it carries his mark) - so a bell dropped in a village does not flatten the villagers.
 */
public final class HollowStaffMagic {

    public static final String MARK = "ff_hollow_mark", MARK_BY = "ff_hollow_mark_by", MARK_FX = "ff_hollow_mark_fx";
    public static final String HELD = "ff_hollow_held", STUN = "ff_hollow_stun";

    private HollowStaffMagic() {
    }

    // ------------------------------------------------------------------------------------------------ sides
    public static boolean isFoe(@Nullable LivingEntity caster, Entity e) {
        if (!(e instanceof LivingEntity v) || !v.isAlive() || v == caster || v instanceof ArmorStand) {
            return false;
        }
        if (v instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return false;
        }
        if (caster == null) {
            return true;
        }
        if (v instanceof HollowStaffShadeEntity s && s.ownedBy(caster)) {
            return false;
        }
        if (v instanceof OwnableEntity o && caster.getUUID().equals(o.getOwnerUUID())) {
            return false;
        }
        if (v.isAlliedTo(caster) || caster.isAlliedTo(v)) {
            return false;
        }
        if (v instanceof Player p) {
            return caster instanceof Player cp && cp.canHarmPlayer(p) && v.level().getServer() != null
                    && v.level().getServer().isPvpAllowed();
        }
        return true;
    }

    /** For the areas: a foe that is hostile, or already in this fight. */
    public static boolean inTheFight(@Nullable LivingEntity caster, Entity e) {
        if (!isFoe(caster, e)) {
            return false;
        }
        LivingEntity v = (LivingEntity) e;
        if (v instanceof Enemy || v instanceof Player || caster == null) {
            return true;
        }
        if (v instanceof Mob m && m.getTarget() == caster) {
            return true;
        }
        if (caster.getLastHurtMob() == v || caster.getLastHurtByMob() == v || v.getLastHurtByMob() == caster) {
            return true;
        }
        return marked(v) && caster.getUUID().equals(markedBy(v));
    }

    public static DamageSource magic(Entity direct, @Nullable LivingEntity owner) {
        return owner != null ? direct.damageSources().indirectMagic(direct, owner) : direct.damageSources().magic();
    }

    /** A spell's blow: past the last blow's immunity (the runes come three ticks apart), and the caster's shades
     *  are told what he struck. */
    public static boolean strike(LivingEntity v, Entity direct, @Nullable LivingEntity owner, float dmg) {
        v.invulnerableTime = 0;
        boolean hurt = v.hurt(magic(direct, owner), dmg);
        if (hurt && owner != null) {
            owner.setLastHurtMob(v);
        }
        return hurt;
    }

    // ------------------------------------------------------------------------------------------------ the mark
    public static void mark(LivingEntity v, LivingEntity owner, int ticks) {
        long until = v.level().getGameTime() + ticks;
        CompoundTag d = v.getPersistentData();
        boolean fresh = !marked(v);
        d.putLong(MARK, Math.max(until, d.getLong(MARK)));
        d.putUUID(MARK_BY, owner.getUUID());
        Entity fx = v.level().getEntity(d.getInt(MARK_FX));
        if (fx instanceof HollowStaffFxEntity f && f.isAlive() && HollowStaffFxEntity.MARK_KIND.equals(f.kind())
                && f.followed() == v.getId()) {
            f.extend(ticks);
        } else if (v.level() instanceof ServerLevel) {
            HollowStaffFxEntity f = HollowStaffFxEntity.spawn(v.level(), HollowStaffFxEntity.MARK_KIND, v.position(), 0.0F,
                    markSize(v), ticks).follow(v, v.getBbHeight() + 0.45F);
            d.putInt(MARK_FX, f.getId());
        }
        if (fresh && owner.level() instanceof ServerLevel s) {
            s.playSound(null, v.getX(), v.getY() + v.getBbHeight(), v.getZ(),
                    com.jastkub.frozenfortress.registry.FFSounds.HOLLOW_STAFF_MARK.get(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.9F + v.getRandom().nextFloat() * 0.2F);
        }
    }

    static float markSize(LivingEntity v) {
        return Math.max(0.8F, Math.min(2.6F, v.getBbWidth() / 0.6F));
    }

    public static boolean marked(LivingEntity v) {
        return v.getPersistentData().getLong(MARK) > v.level().getGameTime();
    }

    @Nullable
    public static UUID markedBy(LivingEntity v) {
        CompoundTag d = v.getPersistentData();
        return d.hasUUID(MARK_BY) ? d.getUUID(MARK_BY) : null;
    }

    // ------------------------------------------------------------------------------------------------ held, knocked down
    /** The Black Tide holds a marked one where it left them. */
    public static void hold(LivingEntity v, int ticks) {
        v.getPersistentData().putLong(HELD, v.level().getGameTime() + ticks);
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false, true));
        HollowStaffFxEntity.spawn(v.level(), HollowStaffFxEntity.HOLD, v.position(), 0.0F, markSize(v) * 0.9F, ticks)
                .follow(v, 0.0F);
    }

    public static boolean held(LivingEntity v) {
        return v.getPersistentData().getLong(HELD) > v.level().getGameTime();
    }

    /** The toll knocks a marked one down: rooted, its blows nothing, for `ticks`. */
    public static void stun(LivingEntity v, int ticks) {
        v.getPersistentData().putLong(STUN, v.level().getGameTime() + ticks);
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false, true));
        v.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 9, false, false, true));
        v.setDeltaMovement(v.getDeltaMovement().multiply(0.0D, 1.0D, 0.0D).add(0.0D, -0.4D, 0.0D));
        v.hurtMarked = true;
        HollowStaffFxEntity.spawn(v.level(), HollowStaffFxEntity.STUN, v.position(), 0.0F, markSize(v), ticks)
                .follow(v, v.getBbHeight() + 0.2F);
    }

    public static boolean stunned(LivingEntity v) {
        return v.getPersistentData().getLong(STUN) > v.level().getGameTime();
    }

    /** Every tick of a hold or a knock-down: it goes nowhere (a player is held by the slowness; his own client moves
     *  him). */
    public static void root(Entity e) {
        if (!(e instanceof LivingEntity v) || v instanceof Player) {
            return;
        }
        Vec3 m = v.getDeltaMovement();
        v.setDeltaMovement(0.0D, Math.min(0.0D, m.y), 0.0D);
        if (v instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }
}
