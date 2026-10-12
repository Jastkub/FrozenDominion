package com.jastkub.fdspells.entity;

import com.jastkub.fdspells.registry.FDSRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
 * A SPIRE OF ICE out of the ground - the spikes of Frozen Dominion's swords, made spells: a nub of ice shows where it will come, a moment later it bursts up, cuts
 * whoever stands over it and throws them up a little, and sinks back. Winter's Sorrow raises them in rings, the Rime
 * Path in a line.
 */
public class IceSpireEntity extends FxEntity {

    private static final EntityDataAccessor<Integer> DELAY =
            SynchedEntityData.defineId(IceSpireEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE =
            SynchedEntityData.defineId(IceSpireEntity.class, EntityDataSerializers.FLOAT);
    /** Ticks it stands after it comes up (then sinks over its last eight). */
    public static final int STANDS = 26;

    private AbstractSpell spell;
    private final Set<UUID> struck = new HashSet<>();

    public IceSpireEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    /** One raised for `spell` at the floor nearest (x, y, z): up after `delay` ticks, `scale` times the base spire. */
    public static IceSpireEntity raise(Level world, LivingEntity caster, AbstractSpell spell, double x, double y, double z,
                                       float damage, int spellLevel, int delay, float scale) {
        Vec3 at = Utils.moveToRelativeGroundLevel(world, new Vec3(x, y, z), 3);
        IceSpireEntity s = new IceSpireEntity(FDSRegistry.ICE_SPIRE.get(), world);
        s.moveTo(at.x, at.y, at.z, world.random.nextFloat() * 360.0F, 0.0F);
        s.setup(caster, damage, spellLevel, delay + STANDS);
        s.spell = spell;
        s.entityData.set(DELAY, delay);
        s.entityData.set(SCALE, scale);
        world.addFreshEntity(s);
        return s;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DELAY, 8);
        builder.define(SCALE, 1.0F);
    }

    public int delay() {
        return entityData.get(DELAY);
    }

    @Override
    public String kind() {
        return "ice_spire";
    }

    /** A nub of ice till it comes (the warning), up in three ticks, sinking over its last eight. */
    @Override
    public float size(float pt) {
        float t = tickCount + pt, d = delay();
        float grow = t < d ? 0.18F : Math.min(1.0F, 0.18F + 0.82F * (t - d) / 3.0F);
        float sink = Math.min(1.0F, Math.max(0.0F, (lifeTicks() - t) / 8.0F));
        return Math.max(0.03F, grow * sink * entityData.get(SCALE));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || spell == null) {
            return;
        }
        int d = delay();
        if (tickCount == d) {
            sound(FDSRegistry.ICE_IMPACT.get(), 0.9F, 0.9F + random.nextFloat() * 0.3F);
        }
        if (tickCount >= d && tickCount <= d + 4) {
            float h = 1.6F * entityData.get(SCALE);
            AABB box = new AABB(getX() - 0.7D, getY(), getZ() - 0.7D, getX() + 0.7D, getY() + h, getZ() + 0.7D);
            for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, box, this::foe)) {
                if (struck.add(v.getUUID()) && strike(v, damage, spell)) {
                    v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), owner());
                    v.push(0.0D, 0.35D, 0.0D);
                    v.hurtMarked = true;
                }
            }
        }
    }
}
