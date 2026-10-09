package com.jastkub.fdspells.entity;

import com.jastkub.fdspells.registry.FDSRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * OSZCZEP MROZU in flight: a javelin of ice on a long, slightly falling line. Into a foe: hurt and chilled. Into the
 * ground or a wall: an ice pillar stands up on the floor where it struck.
 */
public class FrostJavelinEntity extends Projectile implements GeoEntity, Fx {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private AbstractSpell spell;
    private float damage;
    private int spellLevel = 1;

    public FrostJavelinEntity(EntityType<? extends Projectile> type, Level world) {
        super(type, world);
    }

    public void arm(LivingEntity caster, AbstractSpell spell, float damage, int level, Vec3 velocity) {
        setOwner(caster);
        this.spell = spell;
        this.damage = damage;
        this.spellLevel = level;
        setDeltaMovement(velocity);
        face(velocity);
        yRotO = getYRot();
        xRotO = getXRot();
    }

    /** Yaw as the game counts it (0 = +z), pitch positive nose-down. */
    private void face(Vec3 v) {
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        setYRot((float) (Mth.atan2(-v.x, v.z) * Mth.RAD_TO_DEG));
        setXRot((float) (-Mth.atan2(v.y, h) * Mth.RAD_TO_DEG));
    }

    @Override
    public String kind() {
        return "frost_javelin";
    }

    @Override
    public boolean faces() {
        return true;
    }

    @Override
    public boolean pitched() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                if (isRemoved()) {
                    return;
                }
            }
            if (tickCount > 80) {
                discard();
                return;
            }
        }
        yRotO = getYRot();
        xRotO = getXRot();
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        v = v.scale(0.99D).add(0.0D, -0.025D, 0.0D);
        setDeltaMovement(v);
        face(v);
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && FxEntity.isFoe(getOwner() instanceof LivingEntity le ? le : null, e);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (spell != null && hit.getEntity() instanceof LivingEntity v) {
            Entity o = getOwner();
            if (io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(v, damage,
                    spell.getDamageSource(this, o != null ? o : this))) {
                v.addEffect(new MobEffectInstance(MobEffectRegistry.CHILLED, 100, 0),
                        o instanceof LivingEntity le ? le : null);
            }
        }
        level().playSound(null, getX(), getY(), getZ(), FDSRegistry.ICE_IMPACT.get(), SoundSource.PLAYERS, 1.0F, 1.1F);
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        Vec3 at = hit.getLocation();
        BlockPos p = hit.getBlockPos().relative(hit.getDirection());
        // stand it on the floor under where it struck
        int down = 0;
        while (down < 6 && level().getBlockState(p.below()).getCollisionShape(level(), p.below()).isEmpty()) {
            p = p.below();
            down++;
        }
        if (getOwner() instanceof LivingEntity caster && spell != null
                && level().getBlockState(p).getCollisionShape(level(), p).isEmpty()) {
            IcePillarEntity pillar = new IcePillarEntity(FDSRegistry.ICE_PILLAR.get(), level());
            pillar.moveTo(at.x, p.getY(), at.z, getYRot(), 0.0F);
            pillar.setup(caster, damage * 0.4F, spellLevel, 160 + 20 * spellLevel);
            pillar.arm(spell);
            level().addFreshEntity(pillar);
        }
        level().playSound(null, getX(), getY(), getZ(), FDSRegistry.ICE_IMPACT.get(), SoundSource.PLAYERS, 1.2F, 0.8F);
        discard();
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 64.0D * 64.0D;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation loop = RawAnimation.begin().thenLoop("animation." + kind() + ".loop");
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(loop)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
