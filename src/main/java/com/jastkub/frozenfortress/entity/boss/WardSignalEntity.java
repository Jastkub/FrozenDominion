package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * One pulse of mending on its way from a ward to the king.
 *
 * <p>WHY THIS IS AN ENTITY AND NOT A HANDFUL OF PARTICLES. It was particles
 * first, and it could not be seen at all - which is the same lesson the
 * projectile trails taught twice: a thing crossing a room at speed cannot be
 * drawn by dropping motes along its path, because at any speed worth having
 * they are further apart than they are wide. Being an entity gets it a model
 * and gets it ProjectileTrail's real ribbon, which is what makes a healing
 * link legible from the far side of an arena.
 *
 * <p>It also FOLLOWS him rather than flying at where he was. This is mending,
 * not a missile: it should never miss, and a player watching should never see
 * one sail past him, because that would say the wards can be dodged and they
 * cannot.
 */
public class WardSignalEntity extends Entity implements GeoEntity {

    private UUID markUUID;
    private float heal;
    private int age;

    /** It never chases forever; a signal to something that left simply fades. */
    private static final int MAX_TICKS = 60;

    public WardSignalEntity(EntityType<? extends WardSignalEntity> type, Level level) {
        super(type, level);
        // DRAWN FAR OUTSIDE ITS OWN HITBOX, so it must not be frustum culled.
        // The renderer paints a beam the length of the arena from an entity whose type
        // declares half a block; the game culls against the declared box, so
        // the effect vanished whenever its centre point left the screen -
        // which, for something lying on the floor, is most of a boss fight.
        this.noCulling = true;
        this.noPhysics = true;
    }

    public WardSignalEntity(Level level, LivingEntity mark, Vec3 from, float heal) {
        this(FFEntities.WARD_SIGNAL.get(), level);
        this.markUUID = mark.getUUID();
        this.heal = heal;
        setPos(from.x, from.y, from.z);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                level().addParticle(FFParticles.SOUL_FROST.get(),
                        getX() + (random.nextDouble() - 0.5D) * 0.3D,
                        getY() + (random.nextDouble() - 0.5D) * 0.3D,
                        getZ() + (random.nextDouble() - 0.5D) * 0.3D, 0.0D, 0.0D, 0.0D);
            }
            return;
        }
        age++;
        LivingEntity mark = markUUID != null && level() instanceof ServerLevel sl
                && sl.getEntity(markUUID) instanceof LivingEntity le && le.isAlive() ? le : null;
        if (mark == null || age > MAX_TICKS) {
            discard();
            return;
        }
        Vec3 at = mark.position().add(0.0D, mark.getBbHeight() * 0.55D, 0.0D);
        Vec3 to = at.subtract(position());
        double gap = to.length();
        if (gap < 0.8D) {
            // never past the top of the stretch of his bar he is in (VelkharEntity.healWithinPhase)
            if (mark instanceof VelkharEntity king) {
                king.healWithinPhase(heal);
            } else {
                mark.heal(heal);
            }
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(FFParticles.SOUL_FROST.get(),
                        at.x, at.y, at.z, 18, 0.55D, 0.8D, 0.55D, 0.06D);
                serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                        at.x, at.y, at.z, 10, 0.4D, 0.6D, 0.4D, 0.12D);
                serverLevel.playSound(null, blockPosition(), FFSounds.ICE_PRISON.get(),
                        SoundSource.HOSTILE, 1.2F, 1.7F);
            }
            discard();
            return;
        }
        // accelerates into him, so it arrives rather than drifting in
        double pace = 0.30D + Math.min(0.55D, age * 0.035D);
        Vec3 step = to.scale(pace / gap);
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 9216.0D;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        heal = tag.getFloat("Heal");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putFloat("Heal", heal);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }

    private static final RawAnimation STILL = RawAnimation.begin().thenLoop("animation.still");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "still", 0, this::still));
    }

    private <E extends GeoEntity> PlayState still(AnimationState<E> state) {
        return state.setAndContinue(STILL);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
