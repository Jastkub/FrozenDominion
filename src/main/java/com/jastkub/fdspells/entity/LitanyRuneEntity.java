package com.jastkub.fdspells.entity;

import com.jastkub.fdspells.registry.FDSRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * LITANIA RUN: one rune of ice circling its caster (FDSEvents sends it off). Circling, it keeps its place in the ring;
 * sent, it flies at the one its caster hurt, and on reaching it freezes it - chilled, slowed hard, frost on it.
 */
public class LitanyRuneEntity extends FxEntity {

    private static final EntityDataAccessor<Integer> SEEK = SynchedEntityData.defineId(LitanyRuneEntity.class,
            EntityDataSerializers.INT);
    private static final double RING = 1.5D, SPEED = 0.9D;
    private AbstractSpell spell;
    private int slot, slots = 1;

    public LitanyRuneEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    public void orbit(AbstractSpell spell, int slot, int slots) {
        this.spell = spell;
        this.slot = slot;
        this.slots = slots;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SEEK, -1);
    }

    @Override
    public String kind() {
        return "litany_rune";
    }

    @Override
    public boolean faces() {
        return true;
    }

    public boolean orbiting() {
        return entityData.get(SEEK) < 0;
    }

    /** Off at `target` (one of the ring at a time - FDSEvents picks it). */
    public void seek(LivingEntity target) {
        entityData.set(SEEK, target.getId());
        life = tickCount + 60;
        sound(FDSRegistry.RUNE_FIRE.get(), 0.9F, 1.1F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        LivingEntity o = owner();
        if (o == null || !o.isAlive()) {
            discard();
            return;
        }
        if (tickCount == 1 && slot == 0) {
            sound(FDSRegistry.RUNE_FORM.get(), 1.0F, 1.0F);
        }
        int seek = entityData.get(SEEK);
        if (seek < 0) {
            double a = (tickCount * 0.09D) + slot * (Math.PI * 2.0D / slots);
            double bob = Math.sin(tickCount * 0.12D + slot) * 0.15D;
            setPos(o.getX() + Math.cos(a) * RING, o.getY() + o.getBbHeight() * 0.62D + bob, o.getZ() + Math.sin(a) * RING);
            setYRot((float) Math.toDegrees(-a));
            return;
        }
        Entity t = level().getEntity(seek);
        if (!(t instanceof LivingEntity v) || !v.isAlive()) {
            discard();
            return;
        }
        Vec3 to = v.position().add(0.0D, v.getBbHeight() * 0.55D, 0.0D).subtract(position());
        double d = to.length();
        if (d <= SPEED + 0.4D) {
            if (spell != null && strike(v, damage, spell)) {
                v.addEffect(new MobEffectInstance(MobEffectRegistry.CHILLED, 100, 1), o);
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3), o);
                v.setTicksFrozen(Math.max(v.getTicksFrozen(), v.getTicksRequiredToFreeze() + 80));
            }
            sound(FDSRegistry.RUNE_HIT.get(), 1.0F, 1.0F);
            discard();
            return;
        }
        Vec3 step = to.scale(SPEED / d);
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
        setYRot((float) Math.toDegrees(Math.atan2(-step.x, step.z)));
    }
}
