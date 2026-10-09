package com.jastkub.fdspells.entity;

import com.jastkub.fdspells.registry.FDSRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * SOPLE ZE SKLEPIENIA, the spell's hand: unseen at the middle of the rain, it marks a shadow and drops an icicle on
 * it every few ticks - the first ones on whoever stands there, the rest scattered round the ring.
 */
public class IcicleRainEntity extends FxEntity {

    /** Ticks between one shadow and its icicle landing (IcicleShadowEntity and FallingIcicleEntity share it). */
    public static final int WARN = 18;
    private static final int EVERY = 4;
    private AbstractSpell spell;
    private int count;
    private float radius;
    private int dropped;

    public IcicleRainEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    public void start(AbstractSpell spell, int count, float radius) {
        this.spell = spell;
        this.count = count;
        this.radius = radius;
    }

    @Override
    public String kind() {
        return "icicle_rain";
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || spell == null || dropped >= count || tickCount % EVERY != 0) {
            return;
        }
        Vec3 at;
        LivingEntity aim = level().getNearestEntity(LivingEntity.class,
                net.minecraft.world.entity.ai.targeting.TargetingConditions.forCombat().range(radius + 1.0D),
                owner(), getX(), getY(), getZ(), getBoundingBox().inflate(radius + 1.0D, 3.0D, radius + 1.0D));
        if (aim != null && foe(aim) && dropped % 2 == 0) {
            at = aim.position();                                   // on whoever stands there, every other one
        } else {
            double a = random.nextDouble() * Math.PI * 2.0D, d = Math.sqrt(random.nextDouble()) * radius;
            at = position().add(Math.cos(a) * d, 0.0D, Math.sin(a) * d);
        }
        at = Utils.moveToRelativeGroundLevel(level(), at, 4);
        IcicleShadowEntity shadow = new IcicleShadowEntity(FDSRegistry.ICICLE_SHADOW.get(), level());
        shadow.moveTo(at.x, at.y + 0.02D, at.z, random.nextFloat() * 360.0F, 0.0F);
        LivingEntity o = owner();
        if (o != null) {
            shadow.setup(o, 0.0F, level, WARN + 2);
        }
        level().addFreshEntity(shadow);
        FallingIcicleEntity ice = new FallingIcicleEntity(FDSRegistry.FALLING_ICICLE.get(), level());
        ice.moveTo(at.x, at.y + FallingIcicleEntity.HEIGHT, at.z, random.nextFloat() * 360.0F, 0.0F);
        if (o != null) {
            ice.setup(o, damage, level, WARN + 10);
        }
        ice.fall(spell, at.y);
        level().addFreshEntity(ice);
        dropped++;
    }
}
