package com.jastkub.fdspells.entity;

import com.jastkub.fdspells.registry.FDSRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * OKOWY: four chains of ice up out of the ground to a band round the foe. While they hold it does not walk, jump or
 * get pushed off; they snap when their time is up or it dies. Sized to the foe it holds (the band at its middle).
 */
public class FrostShacklesEntity extends FxEntity {

    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(FrostShacklesEntity.class,
            EntityDataSerializers.INT);
    private AbstractSpell spell;

    public FrostShacklesEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    public void bind(AbstractSpell spell, LivingEntity target) {
        this.spell = spell;
        entityData.set(TARGET, target.getId());
        setParam(Math.max(0.6F, target.getBbHeight() * 0.5F));
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TARGET, -1);
    }

    @Override
    public String kind() {
        return "frost_shackles";
    }

    /** Chains up to the band: the model is a block tall; scaled to half the foe's height, and as wide as the foe. */
    @Override
    public float size(float pt) {
        float t = tickCount + pt;
        return Math.min(1.0F, t / 5.0F);
    }

    @Override
    public float spread(float pt) {
        Entity e = level().getEntity(entityData.get(TARGET));
        return e != null ? Math.max(0.7F, e.getBbWidth() * 1.4F) : 1.0F;
    }

    public float height() {
        return param();
    }

    @Override
    public void tick() {
        super.tick();
        Entity e = level().getEntity(entityData.get(TARGET));
        if (e != null) {
            setPos(e.getX(), e.getY(), e.getZ());
        }
        if (level().isClientSide) {
            return;
        }
        if (!(e instanceof LivingEntity v) || !v.isAlive()) {
            expire();
            return;
        }
        if (tickCount == 1) {
            sound(FDSRegistry.CHAINS.get(), 1.2F, 0.8F);
            if (spell != null) {
                strike(v, damage, spell);
            }
        }
        Vec3 m = v.getDeltaMovement();
        v.setDeltaMovement(0.0D, Math.min(m.y, 0.0D), 0.0D);
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 9, false, false, false), owner());
        v.addEffect(new MobEffectInstance(MobEffects.JUMP, 5, 250, false, false, false), owner());
        if (v instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        if (v instanceof ServerPlayer sp) {
            v.hurtMarked = true;
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        }
    }

    @Override
    protected void expire() {
        sound(FDSRegistry.CHAINS_BREAK.get(), 1.1F, 1.0F);
        discard();
    }
}
