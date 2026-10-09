package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * HER HANDS, UP THROUGH THE ICE - the strike of the Drowned Lady's GRASP FROM BELOW. Where her shape under the ice
 * stopped, the ice heaves and cracks (the tell: twelve ticks, seen and heard), and two long drowned arms burst up out
 * of it. Whoever is over them is taken round the waist and DRAGGED DOWN: the ice under them gives (a hole opens, a
 * DrownedIcePlateEntity) and they are held in the black water, squeezed, frostbitten, the light going - three seconds
 * at the most.
 *
 * <p>A HOLD THAT CAN BE BROKEN, as the ice prison is: three blows on her hands - theirs or a friend's, an arrow will do - and the hands burst into ice,
 * she takes the wound of it, and she comes up out of the floor reeling (DrownedLadyEntity.STUNNED). Nobody over
 * them when they close - they claw at nothing and slide back down.
 *
 * <p>A living thing with no AI (like the Turnkey's keys): other mods' projectiles cast whatever they hit to
 * LivingEntity, and her hands are exactly what gets shot at.
 */
public class DrownedHandsEntity extends Mob implements GeoEntity {

    public static final int BURST = 0, HOLD = 1, WHIFF = 2, RELEASE = 3, SHATTER = 4;
    /** The ice heaves for this long, then they close (tools/gen_drowned_lady.py: HANDS_BURST). */
    public static final int BURST_T = 12;
    static final int HOLD_MAX = 60, WHIFF_T = 20, RELEASE_T = 14, SHATTER_T = 10;
    /** Blows that break them off whoever they hold. */
    static final int HITS = 3;
    /** How near the middle you must be to be taken, and how near the floor. */
    // from 1.3 and 1.6
    static final double GRAB_R = 2.4D, GRAB_H = 2.4D;
    static final float GRAB_DMG = 4.0F, SQUEEZE_DMG = 2.0F;

    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(DrownedHandsEntity.class, EntityDataSerializers.INT);

    private static final String P = "animation.drowned_hands.";
    private static final RawAnimation BURST_ANIM = RawAnimation.begin().thenPlayAndHold(P + "burst");
    private static final RawAnimation HOLD_ANIM = RawAnimation.begin().thenLoop(P + "hold");
    private static final RawAnimation WHIFF_ANIM = RawAnimation.begin().thenPlayAndHold(P + "whiff");
    private static final RawAnimation RELEASE_ANIM = RawAnimation.begin().thenPlayAndHold(P + "release");
    private static final RawAnimation SHATTER_ANIM = RawAnimation.begin().thenPlayAndHold(P + "shatter");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    @Nullable
    private UUID heldId;
    /** Was a hole opened under whoever they took (then they are pulled down into it). */
    private boolean dragging;
    private int age;
    private int hits;
    private boolean reported;
    @Nullable
    private Entity breaker;

    public DrownedHandsEntity(EntityType<? extends DrownedHandsEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        this.noPhysics = true;
        this.xpReward = 0;
    }

    /** Her hands, coming up through the ice at `at` (the floor's face). */
    public static DrownedHandsEntity spawn(Level level, DrownedLadyEntity owner, Vec3 at, float yaw) {
        DrownedHandsEntity h = new DrownedHandsEntity(FFEntities.DROWNED_HANDS.get(), level);
        h.ownerId = owner.getUUID();
        h.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        h.setYBodyRot(yaw);
        h.setYHeadRot(yaw);
        level.addFreshEntity(h);
        return h;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, BURST);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    private void setPhase(int p) {
        entityData.set(PHASE, p);
        age = 0;
    }

    @Override
    protected void registerGoals() {
        // nothing: they come up where they come up
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        DrownedLadyEntity owner = owner();
        if (owner == null || !owner.isAlive()) {
            letGo();
            discard();
            return;
        }
        age++;
        switch (phase()) {
            case BURST -> {
                if (age == 6) {                                       // the ice heaving over them, cracking
                    playSound(FFSounds.DROWNED_LADY_ICE_CRACK.get(), 1.5F, 0.75F);
                }
                if (age >= BURST_T) {
                    grab(owner);
                }
            }
            case HOLD -> hold(owner);
            case WHIFF -> {
                if (age >= WHIFF_T) {
                    finish(owner, false);
                }
            }
            case RELEASE -> {
                if (age >= RELEASE_T) {
                    finish(owner, false);
                }
            }
            case SHATTER -> {
                if (age >= SHATTER_T) {
                    finish(owner, true);
                }
            }
            default -> finish(owner, false);
        }
    }

    /** They close: on whoever is over them - or on nothing. */
    private void grab(DrownedLadyEntity owner) {
        LivingEntity victim = null;
        double best = GRAB_R * GRAB_R;
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(GRAB_R, GRAB_H, GRAB_R),
                this::foe)) {
            double d = v.distanceToSqr(getX(), v.getY(), getZ());
            // the rune plates hold: nothing comes up through them
            if (d <= best && Math.abs(v.getY() - getY()) < GRAB_H && DrownedIcePlateEntity.runeUnder(v) == null) {
                best = d;
                victim = v;
            }
        }
        playSound(FFSounds.DROWNED_LADY_GRASP.get(), 1.8F, 1.0F);
        if (victim == null) {
            setPhase(WHIFF);
            return;
        }
        heldId = victim.getUUID();
        setPhase(HOLD);
        victim.hurt(damageSources().indirectMagic(this, owner), GRAB_DMG);
        // the ice under them gives: a hole there already, or one opened now (a plate still cracking goes at once)
        DrownedIcePlateEntity hole = DrownedIcePlateEntity.holeAt(level(), victim.getX(), getY(), victim.getZ());
        if (hole != null) {
            if (hole.phase() == DrownedIcePlateEntity.CRACK) {
                hole.breakOpen(true);
            }
            dragging = true;
        } else {
            BlockPos c = BlockPos.containing(victim.getX(), getY() - 0.5D, victim.getZ());
            if (DrownedIcePlateEntity.canPlate(level(), c)) {
                DrownedIcePlateEntity.open(level(), owner, c);
                playSound(FFSounds.DROWNED_LADY_ICE_BREAK.get(), 1.4F, 0.9F);
                dragging = true;
            }
        }
    }

    /** Held: pulled in to the middle and down into the water, squeezed, the cold and the dark coming. */
    private void hold(DrownedLadyEntity owner) {
        LivingEntity v = held();
        if (v == null || age > HOLD_MAX) {
            letGo();
            setPhase(RELEASE);
            return;
        }
        double down = dragging ? getY() - 0.95D : getY();
        Vec3 pull = new Vec3((getX() - v.getX()) * 0.35D, v.getY() > down ? -0.18D : 0.0D, (getZ() - v.getZ()) * 0.35D);
        v.setDeltaMovement(pull);
        v.hurtMarked = true;
        if (age % 10 == 1) {
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 6));
            v.addEffect(new MobEffectInstance(MobEffects.JUMP, 20, 128));          // no jumping out of their grip
            v.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0));         // dragged under: the light going
            v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 1), owner);
        }
        if (age % 10 == 0) {
            v.hurt(damageSources().indirectMagic(this, owner), SQUEEZE_DMG);
        }
    }

    /** Whoever they held is free: the grip's own slowing goes with them (the frostbite and the dark stay a while). */
    private void letGo() {
        LivingEntity v = held();
        if (v != null) {
            v.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            v.removeEffect(MobEffects.JUMP);
        }
        heldId = null;
    }

    @Nullable
    private LivingEntity held() {
        if (heldId != null && level() instanceof ServerLevel s && s.getEntity(heldId) instanceof LivingEntity le
                && le.isAlive()) {
            return le;
        }
        return null;
    }

    /** Done - she comes up out of the floor here (reeling, if they were broken off). */
    private void finish(DrownedLadyEntity owner, boolean broken) {
        if (!reported) {
            reported = true;
            owner.handsDone(new Vec3(getX(), getY(), getZ()), broken, breaker);
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            letGo();
            discard();
            return true;
        }
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        int ph = phase();
        if (!(ph == HOLD || (ph == BURST && age >= 8))) {
            return false;
        }
        Entity by = source.getEntity();
        if (by instanceof FrostServantEntity || (by != null && ownerId != null && ownerId.equals(by.getUUID()))) {
            return false;
        }
        if (!(by instanceof LivingEntity) && !(source.getDirectEntity() instanceof Projectile)) {
            return false;                                       // the cold of her own water does not free anyone
        }
        hits++;
        playSound(FFSounds.DROWNED_LADY_ICE_CRACK.get(), 1.4F, 0.9F + hits * 0.2F);
        if (hits >= HITS) {
            breaker = by;
            letGo();
            setPhase(SHATTER);
            playSound(FFSounds.DROWNED_LADY_HANDS_BREAK.get(), 1.8F, 1.0F);
        }
        return true;
    }

    private boolean foe(LivingEntity e) {
        return e.isAlive() && !(e instanceof FrostServantEntity) && !(e instanceof DrownedHandsEntity)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Nullable
    private DrownedLadyEntity owner() {
        if (ownerId != null && level() instanceof ServerLevel s && s.getEntity(ownerId) instanceof DrownedLadyEntity d) {
            return d;
        }
        return null;
    }

    @Override
    public boolean isPickable() {
        int ph = phase();
        return ph == BURST || ph == HOLD;
    }

    @Override
    public void travel(Vec3 input) {
        // they move with nothing but her
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {   // (1.21: canBreatheUnderwater is final)
        return type != net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value() && super.canDrownInFluidType(type);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "hands", 0, state -> state.setAndContinue(switch (phase()) {
            case HOLD -> HOLD_ANIM;
            case WHIFF -> WHIFF_ANIM;
            case RELEASE -> RELEASE_ANIM;
            case SHATTER -> SHATTER_ANIM;
            default -> BURST_ANIM;
        })));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
