package com.jastkub.fdspells.entity;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One beat of the Frost Heart: a ring of ice shards running out over the ground to its reach. Whatever the ring
 * passes over is hurt and slowed - once a ring; a jump over it as it comes is the answer.
 */
public class FrostRingEntity extends FxEntity {

    public static final int GROW = 12;
    private AbstractSpell spell;
    private final Set<UUID> struck = new HashSet<>();

    public FrostRingEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    public void arm(AbstractSpell spell, float radius) {
        this.spell = spell;
        setParam(radius);
    }

    @Override
    public String kind() {
        return "frost_ring";
    }

    /** Its reach this frame: out from nothing to the full ring, eased. */
    public float radius(float pt) {
        float f = Math.min(1.0F, (tickCount + pt) / GROW);
        return param() * (1.0F - (1.0F - f) * (1.0F - f));
    }

    @Override
    public float spread(float pt) {
        return Math.max(0.05F, radius(pt));
    }

    @Override
    public float size(float pt) {
        float t = tickCount + pt;
        return t > GROW ? Math.max(0.0F, 1.0F - (t - GROW) / 4.0F) : 1.0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || spell == null || tickCount > GROW) {
            return;
        }
        float r = radius(0.0F), r0 = Math.max(0.0F, r - 1.0F);
        AABB box = getBoundingBox().inflate(r + 1.0D, 1.0D, r + 1.0D);
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, box, this::foe)) {
            double d = Math.sqrt(v.distanceToSqr(getX(), v.getY(), getZ()));
            if (d < r0 - 0.4D || d > r + 0.6D || v.getY() > getY() + 0.9D || !struck.add(v.getUUID())) {
                continue;
            }
            if (strike(v, damage, spell)) {
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1), owner());
            }
        }
    }
}
