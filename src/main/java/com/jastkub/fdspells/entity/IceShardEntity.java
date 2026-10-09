package com.jastkub.fdspells.entity;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A shard of a burst Frost Shell, flying out a few blocks: it cuts what it passes through, once each. */
public class IceShardEntity extends FxEntity {

    private static final double SPEED = 0.55D;
    private AbstractSpell spell;
    private double dx, dz;
    private final Set<UUID> struck = new HashSet<>();

    public IceShardEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    public void fly(AbstractSpell spell, double dx, double dz) {
        this.spell = spell;
        this.dx = dx;
        this.dz = dz;
    }

    @Override
    public String kind() {
        return "ice_shard";
    }

    @Override
    public boolean faces() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        setPos(getX() + dx * SPEED, getY() - 0.02D * tickCount, getZ() + dz * SPEED);
        if (spell == null) {
            return;
        }
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.3D),
                this::foe)) {
            if (struck.add(v.getUUID())) {
                strike(v, damage, spell);
            }
        }
    }
}
