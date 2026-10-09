package com.jastkub.frozenfortress.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.PartEntity;

import javax.annotation.Nullable;

/**
 * A part of the Bone Lord that can be struck where it is: his ribcage or his skull (BoneLordEntity carries them where
 * his clip has them, out of BoneLordFrames). His own hitbox is a column narrow enough for the bridges; these are what
 * an arrow at his head, or at his body stretched out along a bridge after a fall, actually hits. Blows to them are
 * blows to him. Never saved, never sent on its own (it lives and dies with him).
 */
public class BoneLordPart extends PartEntity<BoneLordEntity> {

    public final String name;
    private final EntityDimensions size;

    public BoneLordPart(BoneLordEntity parent, String name, float width, float height) {
        super(parent);
        this.name = name;
        this.size = EntityDimensions.scalable(width, height);
        this.refreshDimensions();
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return getParent().partsLive();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && getParent().partsLive() && getParent().hurtByPart(this, source, amount);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
