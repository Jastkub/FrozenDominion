package com.jastkub.fdspells.entity;

import com.jastkub.fdspells.registry.FDSRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * LAWINA'S WAVE: a front of tumbling ice chunks running out along the ground. It climbs a single step and drops down
 * two; a wall or a drop deeper than that ends it. Everything it meets is hurt once, thrown up and on, and slowed.
 */
public class AvalancheWaveEntity extends FxEntity {

    private static final double SPEED = 0.5D;
    private AbstractSpell spell;
    private float distance = 16.0F;
    private float width = 3.0F;
    private double travelled;
    private final Set<UUID> struck = new HashSet<>();

    public AvalancheWaveEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    public void launch(AbstractSpell spell, float distance, float width) {
        this.spell = spell;
        this.distance = distance;
        this.width = width;
        setParam(width);
    }

    @Override
    public String kind() {
        return "avalanche_wave";
    }

    @Override
    public boolean faces() {
        return true;
    }

    /** Wider with the spell's level; it rises out of the ground and sinks back at the end. */
    @Override
    public float spread(float pt) {
        return param() / 3.0F;
    }

    @Override
    public float size(float pt) {
        return Math.max(0.15F, Math.min(1.0F, (tickCount + pt) / 5.0F));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || spell == null) {
            return;
        }
        double r = Math.toRadians(getYRot());
        Vec3 dir = new Vec3(-Math.sin(r), 0.0D, Math.cos(r));
        Vec3 next = position().add(dir.scale(SPEED));
        BlockPos feet = BlockPos.containing(next.x, getY() + 0.1D, next.z);
        double y = getY();
        if (!level().getBlockState(feet).getCollisionShape(level(), feet).isEmpty()) {
            BlockPos up = feet.above();
            if (level().getBlockState(up).getCollisionShape(level(), up).isEmpty()
                    && level().getBlockState(up.above()).getCollisionShape(level(), up.above()).isEmpty()) {
                y = up.getY();                                     // a step up
            } else {
                expire();                                          // a wall
                return;
            }
        } else {
            int drop = 0;
            BlockPos below = feet.below();
            while (drop < 3 && level().getBlockState(below).getCollisionShape(level(), below).isEmpty()) {
                below = below.below();
                drop++;
            }
            if (drop >= 3) {
                expire();                                          // over an edge
                return;
            }
            y = below.getY() + 1.0D;
        }
        setPos(next.x, y, next.z);
        travelled += SPEED;
        if (tickCount % 8 == 0) {
            sound(FDSRegistry.ICE_GRIND.get(), 1.0F, 0.8F + random.nextFloat() * 0.2F);
        }
        Vec3 side = new Vec3(dir.z, 0.0D, -dir.x);
        AABB box = new AABB(getX() - width, getY() - 0.5D, getZ() - width, getX() + width, getY() + 2.4D,
                getZ() + width);
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, box, this::foe)) {
            Vec3 d = v.position().subtract(position());
            double along = d.dot(dir), across = Math.abs(d.dot(side));
            if (along < -1.0D || along > 1.2D || across > width * 0.5D + v.getBbWidth() * 0.5D
                    || !struck.add(v.getUUID())) {
                continue;
            }
            if (strike(v, damage, spell)) {
                v.setDeltaMovement(v.getDeltaMovement().add(dir.scale(0.9D)).add(0.0D, 0.45D, 0.0D));
                v.hurtMarked = true;
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), owner());
                sound(FDSRegistry.ICE_IMPACT.get(), 1.0F, 0.9F);
            }
        }
        if (travelled >= distance) {
            expire();
        }
    }

    @Override
    protected void expire() {
        shatterSound();
        discard();
    }
}
