package com.jastkub.fdspells.entity;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * An icicle out of the air, point down: it hangs a moment, then falls ever faster onto its shadow and bursts - what
 * stands within a block and a half of it is hurt and chilled.
 */
public class FallingIcicleEntity extends FxEntity {

    public static final double HEIGHT = 10.0D;
    /** Fall so it lands as its shadow ends: HEIGHT = a/2 * t^2 over the last 14 of WARN ticks. */
    private static final int HANG = 4;
    private static final double ACCEL = 2.0D * HEIGHT / Math.pow(IcicleRainEntity.WARN - HANG, 2);
    private AbstractSpell spell;
    private double ground;
    private double speed;

    public FallingIcicleEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    public void fall(AbstractSpell spell, double ground) {
        this.spell = spell;
        this.ground = ground;
    }

    @Override
    public String kind() {
        return "falling_icicle";
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || spell == null || tickCount <= HANG) {
            return;
        }
        speed += ACCEL;
        double y = getY() - speed;
        if (y <= ground) {
            setPos(getX(), ground, getZ());
            AABB box = new AABB(getX() - 1.5D, ground - 0.5D, getZ() - 1.5D, getX() + 1.5D, ground + 2.5D,
                    getZ() + 1.5D);
            for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, box, this::foe)) {
                if (v.distanceToSqr(getX(), v.getY(), getZ()) <= 2.25D && strike(v, damage, spell)) {
                    v.addEffect(new MobEffectInstance(MobEffectRegistry.CHILLED.get(), 80, 0), owner());
                }
            }
            shatterSound();
            discard();
            return;
        }
        setPos(getX(), y, getZ());
    }
}
