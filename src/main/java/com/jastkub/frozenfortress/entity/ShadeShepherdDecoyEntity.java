package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * ZWODNIK - THE SHEPHERD'S DECOY: a shade torn off him in his second half, wearing his shape (his own geometry and
 * clips, the sheet shade_shepherd_decoy). It walks like him, it sounds like him when it walks, and it swings his hook -
 * weaker, and with no breath and no herd. It has its tells for whoever looks: in the dark its mask has no light in its
 * eyes and it carries no herd-lights round its shoulders; in a fire's light it goes see-through, the shade it is
 * (ShadeShepherdFxRenderers.Decoy). A few blows and it comes apart.
 *
 * <p>It is not the keeper: the chambers' door waits on him, not on this.
 */
public class ShadeShepherdDecoyEntity extends FrostServantEntity {

    public static final int HOOK = ShadeShepherdEntity.HOOK;
    static final float HOOK_DMG = 5.0F;
    static final double HOOK_REACH = 3.1D;
    /** It does not outlast this (ticks). */
    static final int LIFE = 1200;

    private static final String P = "animation.shade_shepherd.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation HOOK_ANIM = RawAnimation.begin().thenPlayAndHold(P + "hook");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay(P + "hurt");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold(P + "split");

    @Nullable
    private UUID ownerId;
    private int age;
    private final List<LivingEntity> hooked = new ArrayList<>();
    private boolean inLight;
    /** Client: how much the light shows it for what it is (0 - 1), and last tick's. */
    private float sight, sightO;

    public ShadeShepherdDecoyEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    /** Torn off `shepherd`, standing at `at`, after whoever he hunts. */
    public static ShadeShepherdDecoyEntity tear(ServerLevel level, ShadeShepherdEntity shepherd, Vec3 at) {
        ShadeShepherdDecoyEntity d = new ShadeShepherdDecoyEntity(FFEntities.SHADE_SHEPHERD_DECOY.get(), level);
        d.moveTo(at.x, at.y, at.z, shepherd.getYRot(), 0.0F);
        d.setYBodyRot(shepherd.getYRot());
        d.setYHeadRot(shepherd.getYRot());
        d.ownerId = shepherd.getUUID();
        if (shepherd.getTarget() != null) {
            d.setTarget(shepherd.getTarget());
        }
        level.addFreshEntity(d);
        return d;
    }

    public boolean ownedBy(LivingEntity e) {
        return ownerId != null && ownerId.equals(e.getUUID());
    }

    /** Client: 1 where a fire's light shows it see-through for what it is. */
    public float sight(float partialTick) {
        return Mth.lerp(partialTick, sightO, sight);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new DecoyGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null));
    }

    @Override
    public void tick() {
        super.tick();
        if ((tickCount + getId()) % 3 == 0) {
            inLight = ShadeLight.litNear(level(), position().add(0.0D, 1.0D, 0.0D), ShadeLight.SIGHT);
        }
        if (level().isClientSide) {
            sightO = sight;
            sight += Mth.clamp((inLight ? 1.0F : 0.0F) - sight, -0.15F, 0.15F);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        age++;
        ShadeShepherdEntity owner = ownerId != null && level() instanceof ServerLevel s
                && s.getEntity(ownerId) instanceof ShadeShepherdEntity sh && sh.isAlive() ? sh : null;
        if (age > LIFE || (owner == null && age > 40)) {
            dissolve();
            return;
        }
        if (getAttackState() == HOOK) {
            int t = attackTicks;
            if (t == 2) {
                playSound(FFSounds.SHADE_SHEPHERD_HOOK_TELL.get(), 1.6F, 1.05F);
            }
            if (t == ShadeShepherdEntity.HOOK_SWING) {
                playSound(FFSounds.SHADE_SHEPHERD_HOOK.get(), 1.8F, 1.05F);
            }
            if (t == ShadeShepherdEntity.HOOK_HIT) {
                hookHit();
            }
            if (t > ShadeShepherdEntity.HOOK_HIT && t <= ShadeShepherdEntity.HOOK_PULL) {
                Vec3 dest = position().add(Vec3.directionFromRotation(0.0F, getYRot()).scale(1.8D));
                for (LivingEntity v : hooked) {
                    Vec3 to = new Vec3(dest.x - v.getX(), 0.0D, dest.z - v.getZ());
                    double d = to.length();
                    if (d > 0.4D) {
                        Vec3 pull = to.scale(Math.min(0.6D, d * 0.3D) / d);
                        v.setDeltaMovement(pull.x, Math.max(v.getDeltaMovement().y, 0.06D), pull.z);
                        v.hurtMarked = true;
                    }
                }
            }
            if (t >= ShadeShepherdEntity.HOOK_T) {
                hooked.clear();
                setAttackState(0);
            }
        }
    }

    private void hookHit() {
        hooked.clear();
        Vec3 f = Vec3.directionFromRotation(0.0F, getYRot());
        double cos = Math.cos(Math.toRadians(ShadeShepherdEntity.HOOK_CONE));
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(HOOK_REACH + 1.0D, 2.0D,
                HOOK_REACH + 1.0D), e -> e != this && e.isAlive() && !FFAllies.ofTheKing(e)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            Vec3 to = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
            double d = to.length();
            if (d > HOOK_REACH + v.getBbWidth() * 0.5D || Math.abs(v.getY() - getY()) > 2.5D
                    || (d > 0.8D && to.scale(1.0D / d).dot(f) < cos)) {
                continue;
            }
            if (v.hurt(damageSources().mobAttack(this), HOOK_DMG)) {
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0), this);
            }
            hooked.add(v);
        }
        if (!hooked.isEmpty()) {
            playSound(FFSounds.SHADE_SHEPHERD_HOOK_HIT.get(), 1.6F, 1.1F);
        }
    }

    /** It comes apart (its shepherd gone, or its time up). */
    public void dissolve() {
        if (isAlive()) {
            hurt(damageSources().genericKill(), 1000.0F);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            // it bursts back into the shadow it was: the same rags of dark that tore it off him
            AttackFxEntity.spawn(level(), "shade_split", position(), 0.0F, 0.8F, 24, this);
            playSound(FFSounds.SHADE_SHEPHERD_SPLIT.get(), 1.8F, 1.25F);
        }
    }

    /** Gone in a moment: the burst is its death (the renderer fades it over these ticks). */
    @Override
    protected int getDeathDuration() {
        return 10;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FFSounds.SHADE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FFSounds.SHADE_DEATH.get();
    }

    /** It walks like him - the same bare feet, the same little bell. */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(FFSounds.SHADE_SHEPHERD_STEP.get(), 0.7F, 0.9F + random.nextFloat() * 0.2F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) {
            tag.putUUID("Shepherd", ownerId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("Shepherd") ? tag.getUUID("Shepherd") : null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            if (isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            if (getAttackState() == HOOK) {
                return state.setAndContinue(HOOK_ANIM);
            }
            return state.setAndContinue(FrostServantEntity.going(state) ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "flinch", 1, state ->
                !isDeadOrDying() && hurtTime > 0 && !isAttacking() ? state.setAndContinue(HURT) : PlayState.STOP));
    }

    /** It walks in on whoever he hunts and swings his hook at them; nothing else. */
    static class DecoyGoal extends Goal {
        private final ShadeShepherdDecoyEntity mob;
        private int cooldown = 30;

        DecoyGoal(ShadeShepherdDecoyEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            if (cooldown > 0) {
                cooldown--;
            }
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (mob.getAttackState() != 0) {
                mob.getNavigation().stop();
                return;
            }
            double d = Math.hypot(target.getX() - mob.getX(), target.getZ() - mob.getZ());
            if (d > 2.6D) {
                mob.getNavigation().moveTo(target, 1.0D);
            } else {
                mob.getNavigation().stop();
            }
            if (cooldown <= 0 && d < HOOK_REACH - 0.1D && Math.abs(target.getY() - mob.getY()) < 2.5D) {
                float yaw = (float) (Mth.atan2(target.getZ() - mob.getZ(), target.getX() - mob.getX()) * (180.0D / Math.PI))
                        - 90.0F;
                mob.setYRot(yaw);
                mob.setYBodyRot(yaw);
                mob.getNavigation().stop();
                mob.setAttackState(HOOK);
                cooldown = 60 + mob.random.nextInt(30);
            }
        }
    }
}
