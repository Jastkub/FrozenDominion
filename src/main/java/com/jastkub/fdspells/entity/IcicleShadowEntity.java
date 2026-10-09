package com.jastkub.fdspells.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Where an icicle will land: a ring of frost on the ground that tightens as it comes. */
public class IcicleShadowEntity extends FxEntity {

    public IcicleShadowEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Override
    public String kind() {
        return "icicle_shadow";
    }

    /** It closes in from wide to the icicle's own width as the icicle falls. */
    @Override
    public float spread(float pt) {
        float f = Math.min(1.0F, (tickCount + pt) / IcicleRainEntity.WARN);
        return 1.8F - 0.9F * f;
    }
}
