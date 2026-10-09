package com.jastkub.fdspells.entity;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Where the javelin went into the ground: a pillar of ice three blocks high. It is solid - cover, a wall to put
 * between you and an archer - and a creature that runs into it is stunned for two seconds (once each).
 */
public class IcePillarEntity extends FxEntity {

    private AbstractSpell spell;
    private final Set<UUID> stunned = new HashSet<>();

    public IcePillarEntity(EntityType<?> type, Level world) {
        super(type, world);
        noPhysics = false;
    }

    public void arm(AbstractSpell spell) {
        this.spell = spell;
    }

    @Override
    public String kind() {
        return "ice_pillar";
    }

    /** It grows up out of the ground in a few ticks and sinks back at the end. */
    @Override
    public float size(float pt) {
        float t = tickCount + pt;
        return Math.max(0.05F, Math.min(1.0F, Math.min(t / 6.0F, (lifeTicks() - t) / 10.0F)));
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(0.0D, 0.0D, 0.0D);
        if (level().isClientSide || spell == null) {
            return;
        }
        if (tickCount == 1) {
            sound(com.jastkub.fdspells.registry.FDSRegistry.ICE_GRIND.get(), 1.2F, 0.7F);
        }
        for (Mob m : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(0.25D, 0.0D, 0.25D), this::foe)) {
            double speed = m.getDeltaMovement().horizontalDistanceSqr();
            if ((speed > 0.004D || m.getNavigation().isInProgress()) && stunned.add(m.getUUID())) {
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 9), owner());
                m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 2), owner());
                m.getNavigation().stop();
                strike(m, damage, spell);
                sound(com.jastkub.fdspells.registry.FDSRegistry.ICE_IMPACT.get(), 1.0F, 0.7F);
            }
        }
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return true;
    }

    @Override
    protected void expire() {
        shatterSound();
        discard();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }
}
