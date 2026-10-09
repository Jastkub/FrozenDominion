package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.entity.boss.IcePrisonEntity;
import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.RawAnimation;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * THE QUENCH'S FROST: what a blow of the Forge Overseer's quenched hammer does to whoever it lands on (the slam, its
 * ring, the sweep, the blow on whoever his tongs hold) - shards of quench-ice driven up out of the floor round them and
 * the quench's breath about their waist (geo/entity/fx_overseer_rime, its clip freeze). It holds them by the ice prison's
 * rules, only shorter: a second and a half at most, and three blows (theirs or a friend's) break it sooner.
 */
public class ForgeOverseerRimeEntity extends IcePrisonEntity {

    public static final int HOLD = 30;
    private static final RawAnimation FREEZE = RawAnimation.begin().thenPlayAndHold("animation.fx_overseer_rime.freeze");

    @Nullable
    private UUID heldId;

    public ForgeOverseerRimeEntity(EntityType<? extends ForgeOverseerRimeEntity> type, Level level) {
        super(type, level);
    }

    public ForgeOverseerRimeEntity(Level level, @Nullable LivingEntity owner, LivingEntity held) {
        super(FFEntities.FORGE_OVERSEER_RIME.get(), level, owner, held);
        this.heldId = held.getUUID();
    }

    /** Is this the frost on `e`? (one at a time on anybody) */
    public boolean holds(LivingEntity e) {
        return heldId != null && heldId.equals(e.getUUID());
    }

    @Override
    protected int maxTicks() {
        return HOLD;
    }

    @Override
    protected RawAnimation idleAnimation() {
        return FREEZE;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        heldId = tag.hasUUID("Held") ? tag.getUUID("Held") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (heldId != null) {
            tag.putUUID("Held", heldId);
        }
    }
}
