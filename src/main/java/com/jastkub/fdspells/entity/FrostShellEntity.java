package com.jastkub.fdspells.entity;

import com.jastkub.fdspells.registry.FDSRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * PANCERZ SZRONU: a shell of ice plates round its caster, carrying the temporary health it gave. When that health is
 * spent - or its time is up - it bursts: shards fly out and everything within three and a half blocks is cut and
 * slowed. Whatever of its health is left at the end goes with it.
 */
public class FrostShellEntity extends FxEntity {

    private AbstractSpell spell;
    private float granted;

    public FrostShellEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    public void close(AbstractSpell spell, float absorption) {
        this.spell = spell;
        this.granted = absorption;
        LivingEntity o = owner();
        if (o != null) {
            o.setAbsorptionAmount(o.getAbsorptionAmount() + absorption);
            sound(FDSRegistry.CRYSTAL_CHIME.get(), 1.0F, 0.9F);
        }
    }

    @Override
    public String kind() {
        return "frost_shell";
    }

    @Override
    public float size(float pt) {
        return Math.min(1.0F, (tickCount + pt) / 6.0F);
    }

    @Override
    public float spread(float pt) {
        LivingEntity o = owner();
        return o != null ? Math.max(0.8F, o.getBbWidth() / 0.6F) : 1.0F;
    }

    @Override
    public void tick() {
        // it rides on its caster, client and server alike (the owner is known on the client from the server's first
        // position update only, so the client follows the synced position)
        super.tick();
        LivingEntity o = owner();
        if (level().isClientSide) {
            return;
        }
        if (o == null || !o.isAlive()) {
            discard();
            return;
        }
        setPos(o.getX(), o.getY(), o.getZ());
        if (tickCount > 2 && o.getAbsorptionAmount() <= 0.05F) {
            burst(o);
        }
    }

    @Override
    protected void expire() {
        LivingEntity o = owner();
        if (o != null) {
            o.setAbsorptionAmount(Math.max(0.0F, o.getAbsorptionAmount() - granted));
            burst(o);
        } else {
            discard();
        }
    }

    private void burst(LivingEntity o) {
        if (isRemoved()) {
            return;
        }
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2.0D / 10.0D + random.nextDouble() * 0.3D;
            IceShardEntity shard = new IceShardEntity(com.jastkub.fdspells.registry.FDSRegistry.ICE_SHARD.get(), level());
            shard.moveTo(getX(), getY() + 1.0D + random.nextDouble() * 0.6D, getZ(),
                    (float) Math.toDegrees(Math.atan2(-Math.cos(a), Math.sin(a))), 0.0F);
            shard.setup(o, damage * 0.5F, level, 9);
            shard.fly(spell, Math.cos(a), Math.sin(a));
            level().addFreshEntity(shard);
        }
        if (spell != null) {
            for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5D),
                    this::foe)) {
                if (v.distanceToSqr(this) <= 3.5D * 3.5D && strike(v, damage, spell)) {
                    v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), o);
                }
            }
        }
        shatterSound();
        sound(FDSRegistry.ICE_SHATTER.get(), 1.2F, 0.7F);
        discard();
    }
}
