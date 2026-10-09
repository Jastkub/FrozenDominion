package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * One of the three wards turning round the king in his last phase.
 *
 * <p>WHY THIS IS AN ENTITY. It was particles first - three columns of cold
 * drawn server-side - and particles cannot be a shield. A particle is a
 * fixed-size billboard with no silhouette of its own, so three of them
 * describe a place rather than an object, and the player cannot tell by
 * looking whether there are three left or one. The whole mechanic rests on
 * being able to count them at a glance.
 *
 * <p>PURELY COSMETIC, like {@link ShieldEchoEntity}. It carries no damage, no
 * collision and no damage gate: the refusal lives in VelkharEntity.hurt(),
 * which asks how many of these are standing and breaks the nearest one. So
 * this could be deleted tomorrow and the fight would play identically, just
 * illegibly - which is the right way round for something whose job is to be
 * looked at.
 *
 * <p>The orbit is recomputed from the king's position every tick rather than
 * integrated, so a ward cannot drift out of station over a long phase, and a
 * teleporting king takes his wards with him on the same tick instead of
 * dragging them across the room.
 */
public class SpiritWardEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Integer> OWNER =
            SynchedEntityData.defineId(SpiritWardEntity.class, EntityDataSerializers.INT);
    /** Which of the three it is, so the trio stays evenly spaced. */
    private static final EntityDataAccessor<Integer> SLOT =
            SynchedEntityData.defineId(SpiritWardEntity.class, EntityDataSerializers.INT);

    /** How far out they ride, how high they sit, and how fast they come round. */
    private static final double ORBIT_R = 1.95D;
    private static final double ORBIT_Y = 1.05D;
    /** Degrees a tick - a lap in five seconds, slow enough to count. */
    private static final float SPIN = 3.6F;

    /**
     * A ward with no king left does not hang around, and neither does one
     * whose king somehow outlives the phase that raised it. Two minutes is
     * well past any honest use; this only exists so a ward can never be the
     * thing that is still in the world an hour later.
     */
    private static final int LIFETIME = 2400;

    public SpiritWardEntity(EntityType<? extends SpiritWardEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    public SpiritWardEntity(Level level, Entity owner, int slot) {
        this(FFEntities.SPIRIT_WARD.get(), level);
        setOwnerId(owner.getId());
        setSlot(slot);
        station(owner, 0.0F);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER, -1);
        entityData.define(SLOT, 0);
    }

    public int getOwnerId() {
        return entityData.get(OWNER);
    }

    public void setOwnerId(int id) {
        entityData.set(OWNER, id);
    }

    public int getSlot() {
        return entityData.get(SLOT);
    }

    public void setSlot(int slot) {
        entityData.set(SLOT, slot);
    }

    /** True while this ward still belongs to that king. */
    public boolean guards(Entity king) {
        return king != null && getOwnerId() == king.getId();
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > LIFETIME) {
            discard();
            return;
        }
        Entity owner = level().getEntity(getOwnerId());
        boolean gone = !(owner instanceof VelkharEntity king)
                || !king.isAlive() || king.getPhase() < 3;
        if (gone) {
            if (!level().isClientSide) {
                discard();
            }
            return;
        }
        // ---- THE RENDERER LERPS FROM THE OLD POSITION, AND IT HAS TO BE
        //      GIVEN ONE. setPos does not touch xo/yo/zo, so without this the
        //      previous frame's value is whatever it happened to be and the
        //      orbit arrives as a stutter rather than as motion. This is what
        //      "move more smoothly" was actually asking for; the tick rate was
        //      never the problem.
        this.xo = getX();
        this.yo = getY();
        this.zo = getZ();
        this.xOld = getX();
        this.yOld = getY();
        this.zOld = getZ();
        this.yRotO = getYRot();
        station(owner, angleAt(level().getGameTime()));
    }

    /**
     * Where the ring has turned to, off the world clock.
     *
     * <p>OFF THE CLOCK, NOT OFF tickCount. tickCount starts at zero whenever
     * the entity appears, and the client's copy appears a tick or two after
     * the server's - so the two sides computed different angles and the
     * position packets spent the whole phase dragging the client's guess back.
     * Game time is the same number on both, so both arrive at the same answer
     * and there is nothing left to correct.
     */
    private float angleAt(long gameTime) {
        return (gameTime % 3600L) * SPIN;
    }

    /** Put it on its station around the king, square to him. */
    private void station(Entity owner, float spin) {
        double ang = Math.toRadians(spin + getSlot() * 120.0D);
        setPos(owner.getX() + Math.cos(ang) * ORBIT_R,
               owner.getY() + ORBIT_Y,
               owner.getZ() + Math.sin(ang) * ORBIT_R);
        // SQUARE TO HIM, NOT TO THE CIRCLE. They faced outward first, which
        // means each one showed its edge from two thirds of the angles you
        // can stand at - three shields of which one is legible is not three
        // shields. Carrying his own facing keeps all three broadside to
        // whoever he is facing, which is whoever they are there to stop.
        setYRot(owner.getYRot());
    }

    /**
     * It ate a blow, and comes apart doing it.
     *
     * <p>Loud and bright on purpose: a hit that simply does nothing reads as
     * the boss being broken, while a hit that visibly destroys something reads
     * as the boss spending one, and the player can count what is left.
     */
    public void shatter() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY() + 0.8D, getZ(), 48, 0.30D, 0.5D, 0.30D, 0.38D);
            serverLevel.sendParticles(FFParticles.SOUL_FROST.get(),
                    getX(), getY() + 0.8D, getZ(), 36, 0.34D, 0.55D, 0.34D, 0.16D);
        }
        playSound(FFSounds.ICE_SHATTER.get(), 3.0F, 1.35F);
        playSound(FFSounds.VELKHAR_SHIELD_HIT.get(), 2.4F, 1.5F);
        discard();
    }

    // ---- it is scenery: nothing touches it, and it touches nothing --------

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 16384.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setOwnerId(tag.getInt("Owner"));
        setSlot(tag.getInt("Slot"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Owner", getOwnerId());
        tag.putInt("Slot", getSlot());
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.still");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, this::idleAnim));
    }

    private <E extends GeoEntity> PlayState idleAnim(AnimationState<E> state) {
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
