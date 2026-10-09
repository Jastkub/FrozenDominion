package com.jastkub.fdspells.entity;

import com.jastkub.fdspells.registry.FDSRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * SERCE MROZU: a heart of ice on the ground, beating. Every beat (a second and a half) a ring of frost runs out over
 * the ground from it. Anything but its caster can break it: twelve points of blows.
 */
public class FrostHeartEntity extends FxEntity {

    public static final int BEAT = 30;
    private AbstractSpell spell;
    private float radius;
    private float health = 12.0F;

    public FrostHeartEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    public void arm(AbstractSpell spell, float radius) {
        this.spell = spell;
        this.radius = radius;
    }

    @Override
    public String kind() {
        return "frost_heart";
    }

    /** It swells on every beat. */
    @Override
    public float size(float pt) {
        float t = tickCount + pt;
        float in = Math.min(1.0F, t / 8.0F);
        float beat = (t % BEAT) / BEAT;
        float pulse = beat < 0.15F ? 1.0F + 0.18F * (beat / 0.15F) : 1.18F - 0.18F * Math.min(1.0F, (beat - 0.15F) / 0.4F);
        return in * pulse;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || spell == null) {
            return;
        }
        if (tickCount % BEAT == 6) {
            FrostRingEntity ring = new FrostRingEntity(FDSRegistry.FROST_RING.get(), level());
            ring.moveTo(getX(), getY() + 0.05D, getZ());
            if (owner() != null) {
                ring.setup(owner(), damage, level, FrostRingEntity.GROW + 4);
            }
            ring.arm(spell, radius);
            level().addFreshEntity(ring);
            sound(FDSRegistry.CRYSTAL_CHIME.get(), 1.0F, 0.6F);
            sound(FDSRegistry.FROST_RELEASE.get(), 0.7F, 1.2F);
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity by = source.getEntity();
        if (level().isClientSide || isRemoved() || (by != null && ownedBy(by))) {
            return false;
        }
        health -= amount;
        sound(FDSRegistry.ICE_IMPACT.get(), 0.8F, 1.3F);
        if (health <= 0.0F) {
            expire();
        }
        return true;
    }

    @Override
    protected void expire() {
        shatterSound();
        discard();
    }
}
