package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * The executioner's blade.
 *
 * A greatsword the size of a house is pulled out of the air above the target,
 * hangs there just long enough to be understood, then drops point-first. The
 * king pins the player with Slowness III before it appears, so the window is
 * about reading the telegraph and spending a movement cooldown, not about
 * strolling out of the circle.
 *
 * It is deliberately NOT a projectile: it does not lead, arc or home. It falls
 * exactly where it was placed, which is what makes the slow worth applying.
 */
public class DoomBladeEntity extends Entity implements GeoEntity {

    /** Ticks it hangs before dropping - the whole telegraph. */
    private static final int HANG_TICKS = 32;
    /** Hard stop, so a blade over a void never lives forever. */
    private static final int MAX_TICKS = 160;
    private static final float DAMAGE = 46.0F;
    private static final double RADIUS = 3.6D;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private int age;
    private int impactAge;
    private boolean impacted;
    private double fallSpeed;
    @Nullable
    private LivingEntity owner;

    public DoomBladeEntity(EntityType<? extends DoomBladeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public DoomBladeEntity(Level level, LivingEntity owner, Vec3 where) {
        this(FFEntities.DOOM_BLADE.get(), level);
        this.owner = owner;
        // SPAWNED AS HIGH AS THE ROOM ALLOWS, and not one block higher.
        //
        // Eleven blocks up was unconditional, and the fight happens INDOORS.
        // Under any ceiling lower than that the blade appeared inside the
        // stonework - and a blade inside a block is standing on a floor as far
        // as groundBelow() is concerned, so it landed on its very first
        // falling tick: the screen cracked, the damage went out, and the thing
        // the player was told to watch for never came down at all. That is the
        // "it shakes before the sword hits" and the "sword just vanishes" in
        // one line of code.
        //
        // So the headroom is measured and the drop takes what it can get. Four
        // blocks is the floor: below that it stops reading as a fall, and if a
        // room is that tight the attack is better off looking short than
        // looking broken.
        double rise = 11.0D;
        net.minecraft.core.BlockPos head = net.minecraft.core.BlockPos.containing(where);
        for (int i = 1; i <= 11; i++) {
            net.minecraft.core.BlockPos above = head.above(i);
            if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
                rise = Math.max(4.0D, i - 1.2D);
                break;
            }
        }
        setPos(where.x, where.y + rise, where.z);
    }

    @Override
    public void tick() {
        super.tick();
        age++;

        if (level().isClientSide) {
            if (age < HANG_TICKS && age % 2 == 0) {
                // Shards streaming inwards as it forms.
                double a = age * 0.5D;
                level().addParticle(FFParticles.ICE_SHARD.get(),
                        getX() + Math.cos(a) * 2.4D, getY() + 1.5D + (age % 8) * 0.2D,
                        getZ() + Math.sin(a) * 2.4D, 0.0D, -0.05D, 0.0D);
            }
            return;
        }

        ServerLevel server = (ServerLevel) level();

        if (age < HANG_TICKS) {
            // Hanging. Rotate slowly so it catches the light, and mark the
            // ground underneath so the target can read where it lands.
            setYRot(getYRot() + 6.0F);
            server.sendParticles(FFParticles.FROST_SWIRL.get(),
                    getX(), getY() + 2.0D, getZ(), 6, 1.2D, 2.0D, 1.2D, 0.02D);
            double floor = groundBelow();
            server.sendParticles(FFParticles.SOUL_FROST.get(),
                    getX(), floor + 0.1D, getZ(), 14, RADIUS * 0.6D, 0.05D, RADIUS * 0.6D, 0.01D);
            if (age == 1) {
                playSoundAt(FFSounds.VELKHAR_SWORD_PULL.get(), 3.2F, 0.7F);
            }
            if (age == HANG_TICKS - 6) {
                playSoundAt(FFSounds.VELKHAR_AIRCUT.get(), 3.4F, 0.6F);
            }
            return;
        }

        if (!impacted) {
            fallSpeed = Math.min(2.6D, fallSpeed + 0.28D);
            setPos(getX(), getY() - fallSpeed, getZ());
            server.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY() + 1.0D, getZ(), 5, 0.5D, 1.0D, 0.5D, 0.03D);
            // IT HAS TO HAVE FALLEN BEFORE IT CAN LAND. A second guard behind
            // the headroom fix: whatever the geometry above turns out to be,
            // nothing may count as an impact on the first two ticks of the
            // drop, because an impact there is by definition not the floor.
            boolean falling = age > HANG_TICKS + 1;
            if ((falling && getY() <= groundBelow() + 0.2D) || age > MAX_TICKS) {
                impact(server);
            }
        } else {
            impactAge++;
            // ---- IT BREAKS. It used to be buried for twenty-four ticks and
            //      then simply cease to exist, which undid the whole weight of
            //      the landing: the heaviest thing in the fight left by being
            //      switched off. Now the blade comes apart where it stands -
            //      see DoomBladeRenderer, which throws each piece of the model
            //      off on its own arc. The entity only has to say WHEN.
            if (impactAge == SHATTER_AT) {
                playSoundAt(FFSounds.ICE_SHATTER.get(), 4.2F, 0.5F);
                playSoundAt(FFSounds.VELKHAR_SHIELD_SLAM.get(), 3.4F, 0.62F);
                server.sendParticles(FFParticles.ICE_SHARD.get(),
                        getX(), getY() + 3.0D, getZ(), 160, 1.2D, 3.2D, 1.2D, 0.5D);
                server.sendParticles(FFParticles.FROST_SWIRL.get(),
                        getX(), getY() + 1.6D, getZ(), 60, 1.6D, 2.0D, 1.6D, 0.25D);
            }
            if (impactAge > SHATTER_AT + SHATTER_LEN) {
                discard();
            }
        }
    }

    /** Ticks after the landing before it breaks, and how long breaking takes. */
    public static final int SHATTER_AT = 14;
    public static final int SHATTER_LEN = 26;

    /** 0 before it breaks, ramping to 1 as the pieces scatter. */
    public float shatter(float partialTick) {
        if (!impacted) {
            return 0.0F;
        }
        float f = (impactAge + partialTick - SHATTER_AT) / SHATTER_LEN;
        return Math.max(0.0F, Math.min(1.0F, f));
    }

    private void impact(ServerLevel server) {
        impacted = true;
        setPos(getX(), groundBelow(), getZ());

        playSoundAt(FFSounds.VELKHAR_IMPACT.get(), 4.0F, 0.55F);
        playSoundAt(FFSounds.VELKHAR_SLAM.get(), 3.6F, 0.7F);

        // AND THE SCREEN TAKES IT. The camera shake is driven off the king's
        // own impact counter (see ClientEvents) - the blade is a separate
        // entity and has no way to move anybody's camera itself, so it asks
        // him. Full strength: this is the heaviest single thing in the fight
        // and the one moment where a crack rather than a rumble is right.
        if (owner instanceof VelkharEntity king) {
            king.thump(1.0F);
        }

        server.sendParticles(FFParticles.SHOCKWAVE.get(),
                getX(), getY() + 0.2D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        server.sendParticles(FFParticles.FROST_SWIRL.get(),
                getX(), getY() + 0.6D, getZ(), 90, RADIUS, 0.8D, RADIUS, 0.35D);
        server.sendParticles(FFParticles.ICE_SHARD.get(),
                getX(), getY() + 0.4D, getZ(), 70, RADIUS * 0.8D, 0.4D, RADIUS * 0.8D, 0.4D);

        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                new AABB(getX() - RADIUS, getY() - 2.0D, getZ() - RADIUS,
                        getX() + RADIUS, getY() + 4.0D, getZ() + RADIUS),
                e -> e.isAlive() && e != owner && !(e instanceof VelkharEntity
                        && !com.jastkub.frozenfortress.entity.FFAllies.spares(owner, e)))) {
            double d = victim.position().distanceTo(position());
            if (d > RADIUS) {
                continue;
            }
            // Full force under the blade, less at the rim - standing on the
            // edge of the circle should hurt, not delete.
            float scaled = (float) (DAMAGE * com.jastkub.frozenfortress.config.FFConfig.mul(com.jastkub.frozenfortress.config.FFConfig.COMMON.doomBlade)
                    * (1.0D - 0.45D * (d / RADIUS)));
            victim.hurt(owner != null
                    ? damageSources().mobAttack(owner)
                    : damageSources().magic(), scaled);
            victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 120, 1));
            Vec3 away = victim.position().subtract(position());
            if (away.lengthSqr() < 1.0E-4D) {
                away = new Vec3(0.0D, 0.0D, 1.0D);
            }
            away = away.normalize();
            victim.setDeltaMovement(away.x * 0.8D, 0.55D, away.z * 0.8D);
            victim.hurtMarked = true;
            if (victim instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.connection.send(new net.minecraft.network.protocol.game
                        .ClientboundSetEntityMotionPacket(sp));
            }
        }
    }

    /** First solid surface under the blade, so it lands on the floor not in it. */
    private double groundBelow() {
        net.minecraft.core.BlockPos p = blockPosition();
        for (int i = 0; i < 24; i++) {
            net.minecraft.core.BlockPos below = p.below(i);
            if (!level().getBlockState(below).getCollisionShape(level(), below).isEmpty()) {
                return below.getY() + 1.0D;
            }
        }
        return getY() - 24.0D;
    }

    private void playSoundAt(net.minecraft.sounds.SoundEvent sound, float vol, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, vol, pitch);
    }

    public boolean hasImpacted() {
        return impacted;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        impacted = tag.getBoolean("Impacted");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putBoolean("Impacted", impacted);
    }


    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Static geometry - the drama is in the movement, not a rig.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
