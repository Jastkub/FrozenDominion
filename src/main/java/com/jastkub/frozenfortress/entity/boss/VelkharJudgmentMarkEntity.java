package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * LODOWY SAD's CIRCLE ON THE FLOOR: where a spectral sword will land. A ring of runes the size of the blow, the sword
 * drawn in its middle point-down, and a second ring closing in from its rim to its heart as the fall comes - when the
 * two meet, it lands (VelkharJudgmentRenderers.Mark). Geometry, not particles; it does nothing itself.
 */
public class VelkharJudgmentMarkEntity extends StormEyeFxEntity {

    /** Ticks it lasts after the impact (the flash). */
    public static final int AFTER = 8;

    private static final EntityDataAccessor<Integer> START =
            SynchedEntityData.defineId(VelkharJudgmentMarkEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> IMPACT =
            SynchedEntityData.defineId(VelkharJudgmentMarkEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(VelkharJudgmentMarkEntity.class, EntityDataSerializers.FLOAT);

    public VelkharJudgmentMarkEntity(EntityType<? extends VelkharJudgmentMarkEntity> type, Level level) {
        super(type, level);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    static EntityType<VelkharJudgmentMarkEntity> type() {
        ResourceLocation id = FrozenFortress.id("velkhar_judgment_mark");
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                ? (EntityType<VelkharJudgmentMarkEntity>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(id) : null;
    }

    /** A circle at `floor`, from game time `start`, landing `impact` ticks after it. */
    @Nullable
    static VelkharJudgmentMarkEntity under(ServerLevel level, Vec3 floor, int start, int impact, float radius) {
        EntityType<VelkharJudgmentMarkEntity> type = type();
        if (type == null) {
            return null;
        }
        VelkharJudgmentMarkEntity m = new VelkharJudgmentMarkEntity(type, level);
        m.setPos(floor.x, floor.y, floor.z);
        m.entityData.set(START, start);
        m.entityData.set(IMPACT, impact);
        m.entityData.set(RADIUS, radius);
        level.addFreshEntity(m);
        return m;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(START, 0);
        builder.define(IMPACT, 24);
        builder.define(RADIUS, 2.6F);
    }

    public float age(float partialTick) {
        return (float) (level().getGameTime() - (long) entityData.get(START)) + partialTick;
    }

    public int impact() {
        return entityData.get(IMPACT);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (age(0.0F) > impact() + AFTER || tickCount > 200)) {
            discard();
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
