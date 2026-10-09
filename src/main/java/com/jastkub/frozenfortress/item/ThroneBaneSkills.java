package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.AttackFxEntity;
import com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity;
import com.jastkub.frozenfortress.network.FFNetwork;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * ZMORA TRONU'S SKILLS (07.10.2026) - the moves of ThroneBaneItem, run on BOTH sides from the same inputs: the
 * wielder's CLIENT drives the body (a player's movement is the client's; a dash pushed from the server stutters), his
 * SERVER deals the blows, lays the effects (every one a thing with a body - AttackFxEntity's kinds fx_throne_bane_*,
 * ThroneBaneWaveEntity, IceSpikeEntity) and plays the body animations for everyone who sees him (FFNetwork#playerAnim).
 * Both decide from the same things - onGround and the shift key as of the tick before (the client's use() runs before
 * its own movement that tick, and its last move packet carries exactly that state) - so they agree.
 *
 * <ul>
 * <li>SZARZA (tap RMB): a dash of DASH_T ticks along the look, a spectral bull running before it; everyone in its
 *     path is struck, thrown aside and STUNNED (heavy slowness, no jumping).</li>
 * <li>MLOT NADZORCY (sneak + RMB): a leap, the blade up over the head, and a slam down: a ring of ice spikes, a
 *     shockwave, the frozen struck twice as hard. Within COMBO_WINDOW of a Szarza its rings are doubled.</li>
 * <li>UPADEK KORONY (RMB in the air): a plunge straight down; its blow grows with the height fallen (to a cap), and
 *     the fall itself costs nothing.</li>
 * <li>TRONOBOJCA (hold RMB two seconds, let go): ThroneBaneItem winds it (the sigil), this lets it go.</li>
 * </ul>
 * Tick timings mirror tools/gen_throne_bane.py (the clips of the effects and the body).
 */
public final class ThroneBaneSkills {

    // ------------------------------------------------------------------------------------------------ the numbers
    /** Szarza: ticks, blocks a tick (plain / empowered by Gniew Tura). */
    public static final int DASH_T = 10, DASH_T_EMP = 12;
    public static final double DASH_SPEED = 0.85D, DASH_SPEED_EMP = 0.95D;
    public static final float DASH_DMG = 16.0F, DASH_DMG_EMP = 22.0F;
    public static final int STUN_T = 30, STUN_T_EMP = 50;
    /** Mlot: the leap's lift, the forward drift, the tick it is driven down, the fall speed. */
    public static final double LEAP_UP = 1.0D, LEAP_FWD = 0.45D, SLAM_DOWN = -1.9D;
    public static final int LEAP_APEX = 7;
    public static final float SLAM_DMG = 20.0F, SLAM_DMG_EMP = 26.0F, SLAM_SPIKE = 8.0F, SLAM_SPIKE_EMP = 10.0F;
    public static final double SLAM_R = 4.0D, SLAM_R_EMP = 5.0D;
    /** Szarza into Mlot within this many ticks of the dash's end doubles the slam's rings. */
    public static final int COMBO_WINDOW = 30;
    /** Upadek Korony: the plunge speed, the blow (base + per block fallen, capped), its reach. */
    public static final double PLUNGE_SPEED = -2.2D;
    public static final float PLUNGE_BASE = 14.0F, PLUNGE_PER_BLOCK = 2.0F, PLUNGE_CAP = 40.0F, PLUNGE_CAP_EMP = 50.0F;
    /** Tronobojca (ThroneBaneWaveEntity): armour-piercing; half again on the stunned. */
    public static final float SLAYER_DMG = 34.0F, SLAYER_DMG_EMP = 44.0F, STUNNED_BONUS = 1.5F;
    /** The crescent every swing throws in the charged form. */
    public static final float CRESCENT_DMG = 12.0F;
    /** Cooldowns (ticks). */
    public static final int CD_CHARGE = 160, CD_HAMMER = 200, CD_PLUNGE = 100, CD_SLAYER = 400;
    /** The effects' lives (their clips: tools/gen_throne_bane.py). */
    static final int BULL_LIFE = 22, SHOCK_LIFE = 22, IMPACT_LIFE = 30, AWAKEN_LIFE = 26, HIT_LIFE = 10;

    enum Kind { DASH, LEAP, PLUNGE }

    /** One move under way for one player, on one side. */
    static final class State {
        final Kind kind;
        final boolean empowered;
        final Vec3 dir;
        int t;
        boolean combo;
        double startY;
        float fallenBefore;
        Vec3 last;
        final Set<UUID> struck = new HashSet<>();
        boolean fed;

        State(Kind kind, boolean empowered, Vec3 dir) {
            this.kind = kind;
            this.empowered = empowered;
            this.dir = dir;
        }
    }

    /** Separate tables per side: in a single-player game both live in this one JVM. */
    private static final Map<UUID, State> SERVER = new HashMap<>();
    private static final Map<UUID, State> CLIENT = new HashMap<>();
    /** Server: until when a dash's end lets a slam combo off it; until when a landing spares the fall. */
    private static final Map<UUID, Long> COMBO_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> SAFE_UNTIL = new HashMap<>();
    /** Server: the stunned, [from, until] in game time. */
    private static final Map<LivingEntity, long[]> STUNNED = new WeakHashMap<>();

    private ThroneBaneSkills() {
    }

    private static Map<UUID, State> table(Level level) {
        return level.isClientSide ? CLIENT : SERVER;
    }

    /** A move is under way: nothing else starts until it is done. */
    public static boolean busy(Player p) {
        return table(p.level()).containsKey(p.getUUID());
    }

    /** The fall that ends a leap or a plunge (and a moment after) costs nothing. */
    public static boolean protects(Player p) {
        State s = SERVER.get(p.getUUID());
        if (s != null && (s.kind == Kind.LEAP || s.kind == Kind.PLUNGE)) {
            return true;
        }
        Long until = SAFE_UNTIL.get(p.getUUID());
        return until != null && p.level().getGameTime() <= until;
    }

    static Vec3 flatLook(Player p) {
        Vec3 d = Vec3.directionFromRotation(0.0F, p.getYRot());
        return new Vec3(d.x, 0.0D, d.z).normalize();
    }

    // ================================================================================================ starting them
    /** SZARZA - from ThroneBaneItem#releaseUsing on a tap, both sides. */
    public static void startCharge(Player p, ItemStack stack) {
        Level level = p.level();
        boolean emp = ThroneBaneItem.isCharged(stack, level);
        State s = new State(Kind.DASH, emp, flatLook(p));
        s.last = p.position();
        table(level).put(p.getUUID(), s);
        ThroneBaneItem.setCooldown(stack, ThroneBaneItem.CD_KEY_CHARGE, level.getGameTime() + CD_CHARGE);
        if (level.isClientSide) {
            clientShake(0.35F, emp ? DASH_T_EMP : DASH_T);
            return;
        }
        anim(p, "throne_bane_charge", 2);
        sound(p, FFSounds.THRONE_BANE_CHARGE.get(), 1.6F, emp ? 0.9F : 1.0F);
        AttackFxEntity.spawn(level, "throne_bane_bull", p.position(), p.getYRot(), emp ? 1.25F : 1.0F, BULL_LIFE, p)
                .follow(p);
    }

    /** MLOT NADZORCY - from ThroneBaneItem#use, sneaking, on the ground; both sides. */
    public static void startLeap(Player p, ItemStack stack) {
        Level level = p.level();
        State s = new State(Kind.LEAP, ThroneBaneItem.isCharged(stack, level), flatLook(p));
        table(level).put(p.getUUID(), s);
        ThroneBaneItem.setCooldown(stack, ThroneBaneItem.CD_KEY_HAMMER, level.getGameTime() + CD_HAMMER);
        if (level.isClientSide) {
            return;
        }
        Long combo = COMBO_UNTIL.remove(p.getUUID());
        s.combo = combo != null && level.getGameTime() <= combo;
        anim(p, "throne_bane_leap", 2);
        sound(p, FFSounds.THRONE_BANE_LEAP.get(), 1.2F, 1.0F);
        if (s.combo) {
            sound(p, FFSounds.THRONE_BANE_COMBO.get(), 1.0F, 1.0F);
            p.displayClientMessage(Component.translatable("item.frozen_dominion.throne_bane.combo_ring")
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    /** UPADEK KORONY - from ThroneBaneItem#use in the air; both sides. */
    public static void startPlunge(Player p, ItemStack stack) {
        Level level = p.level();
        State s = new State(Kind.PLUNGE, ThroneBaneItem.isCharged(stack, level), Vec3.ZERO);
        s.startY = p.getY();
        s.fallenBefore = p.fallDistance;
        table(level).put(p.getUUID(), s);
        ThroneBaneItem.setCooldown(stack, ThroneBaneItem.CD_KEY_PLUNGE, level.getGameTime() + CD_PLUNGE);
        if (level.isClientSide) {
            return;
        }
        anim(p, "throne_bane_plunge", 2);
        sound(p, FFSounds.THRONE_BANE_PLUNGE.get(), 1.3F, 1.0F);
    }

    // ================================================================================================ every tick
    /** From ThroneBaneEvents: every player, both sides; `start` is the tick's START phase (the body moves then). */
    public static void tick(Player p, boolean start) {
        Level level = p.level();
        if (level.isClientSide) {
            if (!p.isLocalPlayer()) {
                return;
            }
            State s = CLIENT.get(p.getUUID());
            if (s != null && start) {
                clientMove(p, s);
            }
            return;
        }
        if (start) {
            return;
        }
        State s = SERVER.get(p.getUUID());
        if (s == null) {
            return;
        }
        if (!p.isAlive() || p.isSpectator()) {
            SERVER.remove(p.getUUID());
            return;
        }
        switch (s.kind) {
            case DASH -> serverDash(p, s);
            case LEAP -> serverLeap(p, s);
            case PLUNGE -> serverPlunge(p, s);
        }
        s.t++;
    }

    // ------------------------------------------------------------------------------------------------ the client: the body
    private static void clientMove(Player p, State s) {
        Vec3 dm = p.getDeltaMovement();
        switch (s.kind) {
            case DASH -> {
                int len = s.empowered ? DASH_T_EMP : DASH_T;
                double v = s.empowered ? DASH_SPEED_EMP : DASH_SPEED;
                if (s.t < len) {
                    p.setDeltaMovement(s.dir.x * v, Math.min(dm.y, 0.1D), s.dir.z * v);
                } else {
                    p.setDeltaMovement(s.dir.x * 0.25D, dm.y, s.dir.z * 0.25D);
                    CLIENT.remove(p.getUUID());
                }
            }
            case LEAP -> {
                if (s.t == 0) {
                    p.setDeltaMovement(s.dir.x * LEAP_FWD, LEAP_UP, s.dir.z * LEAP_FWD);
                } else if (s.t >= 3 && p.onGround()) {
                    clientShake(s.empowered ? 1.3F : 1.0F, 12);
                    CLIENT.remove(p.getUUID());
                } else if (s.t >= LEAP_APEX) {
                    p.setDeltaMovement(s.dir.x * LEAP_FWD, Math.min(dm.y, SLAM_DOWN), s.dir.z * LEAP_FWD);
                } else {
                    p.setDeltaMovement(s.dir.x * LEAP_FWD, dm.y, s.dir.z * LEAP_FWD);
                }
                if (s.t > 60 || p.isInWater()) {
                    CLIENT.remove(p.getUUID());
                }
            }
            case PLUNGE -> {
                if (s.t >= 1 && p.onGround()) {
                    float fallen = (float) Math.max(0.0D, s.startY - p.getY()) + s.fallenBefore;
                    clientShake(Mth.clamp(0.5F + fallen * 0.06F, 0.5F, 1.4F), 12);
                    CLIENT.remove(p.getUUID());
                } else {
                    p.setDeltaMovement(dm.x * 0.3D, PLUNGE_SPEED, dm.z * 0.3D);
                }
                if (s.t > 100 || p.isInWater()) {
                    CLIENT.remove(p.getUUID());
                }
            }
        }
        p.fallDistance = 0.0F;
        s.t++;
    }

    // ------------------------------------------------------------------------------------------------ the server: the blows
    private static void serverDash(Player p, State s) {
        int len = s.empowered ? DASH_T_EMP : DASH_T;
        Vec3 now = p.position();
        AABB sweep = new AABB(s.last, now).inflate(1.3D, 0.0D, 1.3D).expandTowards(s.dir.scale(1.2D))
                .setMinY(Math.min(s.last.y, now.y) - 0.5D)
                .setMaxY(Math.max(s.last.y, now.y) + p.getBbHeight() + 0.3D);
        s.last = now;
        ServerLevel level = (ServerLevel) p.level();
        int stunT = s.empowered ? STUN_T_EMP : STUN_T;
        for (LivingEntity v : foes(p, sweep)) {
            if (!s.struck.add(v.getUUID())) {
                continue;
            }
            Vec3 rel = v.position().subtract(now);
            Vec3 side = new Vec3(-s.dir.z, 0.0D, s.dir.x);
            double sign = rel.dot(side) >= 0.0D ? 1.0D : -1.0D;
            Vec3 push = side.scale(1.1D * sign).add(s.dir.scale(0.45D)).add(0.0D, 0.38D, 0.0D);
            if (strike(p, v, level.damageSources().playerAttack(p), s.empowered ? DASH_DMG_EMP : DASH_DMG, push)) {
                stun(v, stunT, p);
                sound(v, FFSounds.THRONE_BANE_CHARGE_HIT.get(), 1.3F, 0.9F + level.random.nextFloat() * 0.2F);
                if (!s.fed) {
                    s.fed = true;
                    ThroneBaneItem.feed(p);
                }
            }
        }
        if (s.t >= len) {
            SERVER.remove(p.getUUID());
            COMBO_UNTIL.put(p.getUUID(), level.getGameTime() + COMBO_WINDOW);
        }
    }

    private static void serverLeap(Player p, State s) {
        if (s.t >= 3 && p.onGround()) {
            SERVER.remove(p.getUUID());
            SAFE_UNTIL.put(p.getUUID(), p.level().getGameTime() + 10);
            slam(p, s);
            return;
        }
        if (s.t > 60 || p.isInWater()) {
            SERVER.remove(p.getUUID());
            anim(p, null, 4);
        }
    }

    private static void serverPlunge(Player p, State s) {
        if (s.t >= 1 && p.onGround()) {
            SERVER.remove(p.getUUID());
            SAFE_UNTIL.put(p.getUUID(), p.level().getGameTime() + 10);
            crownfall(p, s);
            return;
        }
        if (s.t > 100 || p.isInWater()) {
            SERVER.remove(p.getUUID());
            anim(p, null, 4);
        }
    }

    /** MLOT NADZORCY lands. */
    private static void slam(Player p, State s) {
        ServerLevel level = (ServerLevel) p.level();
        Vec3 at = p.position();
        anim(p, "throne_bane_slam", 1);
        sound(p, FFSounds.THRONE_BANE_SLAM.get(), 2.2F, s.empowered ? 0.9F : 1.0F);
        AttackFxEntity.spawn(level, "throne_bane_shockwave", at, p.getYRot(), s.empowered ? 1.25F : 1.0F, SHOCK_LIFE, p);
        if (s.combo) {
            AttackFxEntity.spawn(level, "throne_bane_shockwave", at, p.getYRot() + 11.25F, 1.75F, SHOCK_LIFE, p);
        }
        double r = s.empowered ? SLAM_R_EMP : SLAM_R;
        float dmg = s.empowered ? SLAM_DMG_EMP : SLAM_DMG;
        boolean any = false;
        for (LivingEntity v : foes(p, new AABB(at, at).inflate(r, 1.5D, r).expandTowards(0.0D, 1.5D, 0.0D))) {
            Vec3 rel = v.position().subtract(at);
            double d = Math.hypot(rel.x, rel.z);
            if (d > r) {
                continue;
            }
            boolean frozen = isFrozen(v);
            Vec3 out = d < 1.0E-3D ? Vec3.ZERO : new Vec3(rel.x / d, 0.0D, rel.z / d);
            Vec3 push = out.scale(0.5D).add(0.0D, 0.7D * (1.0D - d / (r + 1.0D)) + 0.2D, 0.0D);
            if (strike(p, v, level.damageSources().playerAttack(p), frozen ? dmg * 2.0F : dmg, push)) {
                any = true;
                if (frozen) {
                    level.playSound(null, v.getX(), v.getY(), v.getZ(), FFSounds.ICE_SHATTER.get(), SoundSource.PLAYERS,
                            1.2F, 0.8F);
                }
            }
        }
        // the rings of spikes: two (four on a combo), the outer ones later
        float spike = s.empowered ? SLAM_SPIKE_EMP : SLAM_SPIKE;
        double[][] rings = s.combo
                ? new double[][]{{2.5D, 8, 3}, {4.3D, 12, 6}, {5.9D, 15, 9}, {7.4D, 18, 12}}
                : new double[][]{{2.5D, 8, 3}, {4.3D, 12, 6}};
        for (double[] ring : rings) {
            int n = (int) ring[1];
            double off = level.random.nextDouble() * Math.PI * 2.0D;
            for (int i = 0; i < n; i++) {
                double a = off + Math.PI * 2.0D * i / n;
                level.addFreshEntity(new IceSpikeEntity(level, p, at.x + Math.cos(a) * ring[0], at.y,
                        at.z + Math.sin(a) * ring[0], spike, (int) ring[2], ring[0] > 5.0D ? 1.25F : 1.0F));
            }
        }
        if (any) {
            ThroneBaneItem.feed(p);
        }
    }

    /** UPADEK KORONY lands: the higher the fall, the harder. */
    private static void crownfall(Player p, State s) {
        ServerLevel level = (ServerLevel) p.level();
        Vec3 at = p.position();
        float fallen = (float) Math.max(0.0D, s.startY - at.y) + s.fallenBefore;
        float dmg = Math.min(s.empowered ? PLUNGE_CAP_EMP : PLUNGE_CAP,
                (PLUNGE_BASE + PLUNGE_PER_BLOCK * fallen) * (s.empowered ? 1.25F : 1.0F));
        double r = Math.min(5.0D, 2.8D + 0.12D * fallen);
        anim(p, "throne_bane_plunge_land", 1);
        sound(p, FFSounds.THRONE_BANE_IMPACT.get(), 2.0F, Mth.clamp(1.1F - fallen * 0.015F, 0.8F, 1.1F));
        AttackFxEntity.spawn(level, "throne_bane_impact", at, p.getYRot(),
                Mth.clamp(0.8F + 0.035F * fallen, 0.8F, 1.5F), IMPACT_LIFE, p);
        boolean any = false;
        for (LivingEntity v : foes(p, new AABB(at, at).inflate(r, 1.5D, r).expandTowards(0.0D, 1.5D, 0.0D))) {
            Vec3 rel = v.position().subtract(at);
            double d = Math.hypot(rel.x, rel.z);
            if (d > r) {
                continue;
            }
            Vec3 out = d < 1.0E-3D ? Vec3.ZERO : new Vec3(rel.x / d, 0.0D, rel.z / d);
            if (strike(p, v, level.damageSources().playerAttack(p), dmg, out.scale(0.6D).add(0.0D, 0.55D, 0.0D))) {
                any = true;
            }
        }
        if (s.empowered) {
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * 2.0D * i / 10.0D;
                level.addFreshEntity(new IceSpikeEntity(level, p, at.x + Math.cos(a) * 3.0D, at.y,
                        at.z + Math.sin(a) * 3.0D, SLAM_SPIKE_EMP, 4));
            }
        }
        if (any) {
            ThroneBaneItem.feed(p);
        }
    }

    // ================================================================================================ the shared parts
    /** Everyone a skill of `p` may strike in `box`: not him, not a player, not his own or his allies, not a stand. */
    public static List<LivingEntity> foes(Entity p, AABB box) {
        return p.level().getEntitiesOfClass(LivingEntity.class, box, e -> isFoe(p, e));
    }

    public static boolean isFoe(@Nullable Entity owner, LivingEntity e) {
        if (!e.isAlive() || e == owner || e instanceof ArmorStand || !e.isAttackable()) {
            return false;
        }
        if (e instanceof Player pl && (owner instanceof Player || pl.isCreative() || pl.isSpectator())) {
            return false;
        }
        if (owner instanceof LivingEntity o) {
            if (e.isAlliedTo(o) || (e instanceof TamableAnimal t && t.isOwnedBy(o))) {
                return false;
            }
        }
        return true;
    }

    /** A skill's blow: no invulnerability frames in its way, the hit-stop (a moment's dead weight), a flash of ice
     *  burst out of the struck, frostbite, and its push (less what the struck shrugs off). */
    public static boolean strike(Entity by, LivingEntity v, DamageSource src, float dmg, Vec3 push) {
        v.invulnerableTime = 0;
        if (!v.hurt(src, dmg)) {
            return false;
        }
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 4, false, false, false));
        v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 80, 0), by);
        double keep = 1.0D - Mth.clamp(v.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0D, 1.0D);
        if (keep > 0.0D && push.lengthSqr() > 0.0D) {
            v.setDeltaMovement(v.getDeltaMovement().scale(0.3D).add(push.scale(keep)));
            v.hurtMarked = true;
        }
        if (v.level() instanceof ServerLevel level) {
            AttackFxEntity.spawn(level, "throne_bane_hit", v.position().add(0.0D, v.getBbHeight() * 0.55D, 0.0D),
                    by.getYRot(), Mth.clamp(v.getBbWidth() * 1.3F, 0.8F, 2.2F), HIT_LIFE,
                    by instanceof LivingEntity le ? le : null);
        }
        return true;
    }

    /** Frozen: frostbitten, or frozen through by powder snow. */
    public static boolean isFrozen(LivingEntity v) {
        return v.hasEffect(FFEffects.FROSTBITE.get()) || v.isFullyFrozen();
    }

    // ------------------------------------------------------------------------------------------------ the stun
    /** STUNNED: the heaviest slowness, no jumping, standing where it is - the crown of ice wheeling over its head.
     *  A great one (over 150 health: a boss) only staggers, a third as long. */
    public static void stun(LivingEntity v, int ticks, @Nullable LivingEntity by) {
        if (!(v.level() instanceof ServerLevel level)) {
            return;
        }
        int t = v.getMaxHealth() > 150.0F ? Math.max(8, ticks / 3) : ticks;
        long now = level.getGameTime();
        STUNNED.put(v, new long[]{now, now + t});
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t, 5, false, false, true), by);
        float size = Mth.clamp(v.getBbHeight() / 1.95F, 0.5F, 2.5F);
        AttackFxEntity.spawn(level, "throne_bane_stun", v.position(), 0.0F, size, t, by).follow(v);
        level.playSound(null, v.getX(), v.getEyeY(), v.getZ(), FFSounds.THRONE_BANE_STUN.get(), SoundSource.PLAYERS,
                1.0F, 1.0F);
    }

    public static boolean isStunned(LivingEntity v) {
        long[] s = STUNNED.get(v);
        return s != null && v.level().getGameTime() <= s[1];
    }

    /** Tronobojca took it: the stun is spent. */
    public static void breakStun(LivingEntity v) {
        STUNNED.remove(v);
        v.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    /** Server, every tick: the stunned stand (once the blow's own push has spent itself), the stun runs out. */
    public static void tickStuns() {
        Iterator<Map.Entry<LivingEntity, long[]>> it = STUNNED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, long[]> e = it.next();
            LivingEntity v = e.getKey();
            long now = v.level().getGameTime();
            if (!v.isAlive() || now > e.getValue()[1]) {
                it.remove();
                continue;
            }
            if (v instanceof Mob m) {
                m.getNavigation().stop();
                if (now - e.getValue()[0] > 6 && m.onGround()) {
                    Vec3 dm = m.getDeltaMovement();
                    m.setDeltaMovement(0.0D, Math.min(dm.y, 0.0D), 0.0D);
                }
            }
        }
    }

    public static void forget(Player p) {
        SERVER.remove(p.getUUID());
        CLIENT.remove(p.getUUID());
        COMBO_UNTIL.remove(p.getUUID());
        SAFE_UNTIL.remove(p.getUUID());
    }

    // ------------------------------------------------------------------------------------------------ small things
    static void anim(Player p, @Nullable String name, int fade) {
        if (!p.level().isClientSide) {
            FFNetwork.playerAnim(p, name == null ? null : FrozenFortress.id(name), fade);
        }
    }

    static void sound(Entity at, SoundEvent sound, float volume, float pitch) {
        at.level().playSound(null, at.getX(), at.getY() + at.getBbHeight() * 0.5D, at.getZ(), sound,
                SoundSource.PLAYERS, volume, pitch);
    }

    /** The wielder's own camera shakes (client only; nothing for anyone else). */
    static void clientShake(float amplitude, int ticks) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.jastkub.frozenfortress.client.ThroneBaneClient.shake(amplitude, ticks));
    }
}
