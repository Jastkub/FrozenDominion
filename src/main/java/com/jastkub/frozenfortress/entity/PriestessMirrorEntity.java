package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A reflection of the Priestess, out of her veil of snow: her shape exactly -
 * but her hands are empty, the book is not with it. It walks, it turns its
 * mask on you, now and then a leaf comes off it. Strike it and it breaks -
 * and A WAVE OF FROST runs out of where it stood: a crest of ice over the floor, out to
 * six and a half blocks, taking whoever it reaches (six damage, slowed,
 * frostbitten, frozen) - a FrostWaveEntity, geometry and not dust: a blade
 * that struck it is caught at once, a bow far enough off is not, and late in
 * its run a jump clears it. When the true one is found, every reflection
 * falls to snow.
 */
public class PriestessMirrorEntity extends FrostServantEntity {

    private static final String P = "animation.rime_priestess.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation PAGES = RawAnimation.begin().thenPlay(P + "pages");
    private static final int LIFE = 260;
    @Nullable
    private UUID ownerId;

    public PriestessMirrorEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public PriestessMirrorEntity(Level level, RimePriestessEntity owner, Vec3 at) {
        this(FFEntities.PRIESTESS_MIRROR.get(), level);
        this.ownerId = owner.getUUID();
        moveTo(at.x, at.y, at.z, owner.getYRot(), 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.6D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (tickCount > LIFE || ownerId == null || !(level() instanceof ServerLevel s)
                || !(s.getEntity(ownerId) instanceof RimePriestessEntity owner) || !owner.isAlive()) {
            fade();
            return;
        }
        LivingEntity t = getTarget() != null ? getTarget() : owner.getTarget();
        if (t != null) {
            getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (tickCount % 70 == 35 && hasLineOfSight(t)) {
                setAttackState(1);
                s.addFreshEntity(new FrostPageEntity(level(), this, t, position().add(0.0D, 2.2D, 0.0D),
                        random.nextDouble() * Math.PI * 2.0D, 2.0F));
                playSound(SoundEvents.BOOK_PAGE_TURN, 1.4F, 0.7F);
            }
        }
        if (isAttacking() && attackTicks > 20) {
            setAttackState(0);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.getEntity() instanceof FrostServantEntity) {
            return false;
        }
        if (source.getEntity() instanceof LivingEntity && level() instanceof ServerLevel s) {
            // the wrong one: it breaks, and a wave of its frost runs out across the floor
            s.addFreshEntity(new FrostWaveEntity(level(), this, getX(), getY(), getZ(), 6.5F, 16, 6.0F));
            s.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 1.4D, getZ(), 40, 0.5D, 1.0D, 0.5D, 0.15D);
            playSound(SoundEvents.GLASS_BREAK, 2.0F, 0.7F);
            playSound(FFSounds.SHOCKWAVE.get(), 1.6F, 1.3F);
            discard();
            return true;
        }
        return false;
    }

    /** Found out (or worn thin): to snow, harmlessly. */
    void fade() {
        if (level() instanceof ServerLevel s) {
            s.sendParticles(FFParticles.BLIZZARD_FLAKE.get(), getX(), getY() + 1.4D, getZ(), 50, 0.5D, 1.2D, 0.5D, 0.05D);
            playSound(FFSounds.VELKHAR_TELEPORT.get(), 0.8F, 1.6F);
        }
        discard();
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        discard();                                              // a reflection does not outlast a reload
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (isAttacking()) {
                return state.setAndContinue(PAGES);
            }
            return state.setAndContinue(FrostServantEntity.going(state) ? WALK : IDLE);
        }));
    }
}
