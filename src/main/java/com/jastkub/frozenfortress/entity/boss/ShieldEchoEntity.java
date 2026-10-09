package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The gate's shadow, thrown ahead of him: an image of the shield itself,
 * swelling as it crosses the room.
 *
 * <p>WHY THIS IS AN ENTITY AND NOT PARTICLES. The wave used to be drawn by
 * stamping several dozen particles along the outline of a heater shield every
 * other tick. That fails for a reason no amount of tuning fixes: a particle is
 * a fixed-size billboard, so the "outline" is a dotted line whose dots stay
 * the same size while the shape they describe grows - by the far edge of the
 * ring the gaps are wider than the marks and the silhouette has dissolved into
 * a scatter of sparks. It read as a generic magic puff, which is exactly what
 * it should not be: the whole point of the attack is that the thing bearing
 * down on you is recognisably the slab strapped to his arm.
 *
 * <p>A textured quad has none of that problem. The shield is drawn once, as a
 * picture, and scaling it scales the image - so it is still a shield at
 * fourteen blocks, just a bigger one.
 *
 * <p>PURELY COSMETIC. It carries no damage and no collision. The wave that
 * hurts is still the expanding ring test in {@code VelkharEntity}, unchanged,
 * so this could be deleted tomorrow without touching the fight. That is
 * deliberate: swapping how something looks should never be able to change how
 * it plays.
 */
public class ShieldEchoEntity extends Entity {

    /**
     * How long it lives, in ticks.
     *
     * <p>Matched to the ring in {@code VelkharEntity}: the wave steps outward
     * from tick 20 to tick 52, so the shadow gets the same 32 ticks and dies
     * with it rather than hanging in the air after the danger has passed.
     */
    public static final int LIFETIME = 32;

    /** Blocks per tick. The ring widens 0.78 every two ticks; this matches it. */
    private static final double SPEED = 0.39D;

    private static final EntityDataAccessor<Float> HEADING =
            SynchedEntityData.defineId(ShieldEchoEntity.class, EntityDataSerializers.FLOAT);
    /**
     * 0 = the travelling wave of the pulse; above 0 = a FLASH, at that power.
     *
     * <p>The same shape does two jobs. As a wave it crosses the room with the
     * ring that hurts. As a flash it is what every shield BLOW throws off -
     * born big, gone in a third of a second, barely moving. Both are "the gate
     * did something", which is why they should look related; the difference is
     * entirely in how long they last and how fast they leave, so it is one
     * entity with one number rather than two classes.
     */
    private static final EntityDataAccessor<Float> FLASH =
            SynchedEntityData.defineId(ShieldEchoEntity.class, EntityDataSerializers.FLOAT);

    public ShieldEchoEntity(EntityType<? extends ShieldEchoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public ShieldEchoEntity(Level level, Vec3 from, float headingDeg) {
        this(FFEntities.SHIELD_ECHO.get(), level);
        setPos(from.x, from.y, from.z);
        this.entityData.set(HEADING, headingDeg);
    }

    /** Which way it is travelling, in degrees. Synced so the client can face it. */
    public float heading() {
        return this.entityData.get(HEADING);
    }

    /** Turn this echo into a short, hard flash of the given power (0..1). */
    public void asFlash(float power) {
        this.entityData.set(FLASH, Math.max(0.05F, Math.min(1.0F, power)));
    }

    /** Above zero when this is a blow's flash rather than the pulse's wave. */
    public float flash() {
        return this.entityData.get(FLASH);
    }

    /** How long this one lives, in ticks. A flash is over almost at once. */
    public int lifetime() {
        return flash() > 0.0F ? 7 : LIFETIME;
    }

    /** 0 at birth, 1 at the end of its life. Drives scale and fade. */
    public float progress(float partialTick) {
        return Math.min(1.0F, (this.tickCount + partialTick) / (float) lifetime());
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(HEADING, 0.0F);
        builder.define(FLASH, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount > lifetime()) {
            discard();
            return;
        }
        // Straight out along the heading it was born with. It does NOT track
        // the boss - the wave has left him, and a shadow that kept turning to
        // follow its thrower would read as attached to him rather than as
        // something he let go of.
        //
        // A flash barely travels: it is the gate ringing where it struck, not
        // something sent anywhere, so it drifts just enough to separate itself
        // from the shield rather than crossing the room.
        double speed = flash() > 0.0F ? SPEED * 0.28D : SPEED;
        double rad = Math.toRadians(this.entityData.get(HEADING));
        setPos(getX() - Math.sin(rad) * speed,
               getY(),
               getZ() + Math.cos(rad) * speed);
    }

    /**
     * IT IS NEVER SAVED, and that is not an optimisation - it is a bug fix.
     *
     * <p>As written first, this entity persisted. It dies by comparing
     * {@code tickCount} against {@link #LIFETIME}, and {@code tickCount}
     * restarts at zero on load - so any echo caught mid-flight by a chunk
     * unload came back on the next load, flew its full thirty-two ticks
     * again, and was saved again if it was still airborne when the chunk went
     * out. A cosmetic flash a third of a second long could become a glowing
     * shape that reappears next to him forever.
     *
     * <p>Nothing here is worth persisting in any case: it carries no damage,
     * no owner and no state a reload could not throw away.
     */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(HEADING, tag.getFloat("Heading"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Heading", this.entityData.get(HEADING));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 4096.0D;      // it gets large; do not cull it early
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
            getAddEntityPacket(net.minecraft.server.level.ServerEntity serverEntity) {
        return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(this, serverEntity);
    }
}
