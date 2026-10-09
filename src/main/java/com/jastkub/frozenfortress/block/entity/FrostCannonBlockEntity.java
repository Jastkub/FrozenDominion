package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.entity.projectile.IceBombEntity;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * A FROST MAW in a trap room's wall (the Rift): its heart (FrostHeartBlockEntity) wakes it at whoever it can see;
 * the crystal over its brow fills for a second - its tell, seen and heard - and it spits a bomb of ice, the
 * Monstrosity's own (IceBombEntity), at where they stood. Out of the heart, it is stone.
 */
public class FrostCannonBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.frost_cannon.idle");
    private static final RawAnimation CHARGE = RawAnimation.begin().thenPlay("animation.frost_cannon.charge");
    private static final RawAnimation FIRE = RawAnimation.begin().thenPlay("animation.frost_cannon.fire");
    private static final int TELL = 20;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int charging = -1;
    private UUID mark;
    private Vec3 aim;

    public FrostCannonBlockEntity(BlockPos pos, BlockState state) {
        super(FFBlockEntities.FROST_CANNON.get(), pos, state);
    }

    public boolean busy() {
        return charging >= 0;
    }

    private Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(HorizontalDirectionalBlock.FACING) ? s.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    /** Where the bomb leaves it: a block out from the wall, at its throat. */
    public Vec3 muzzle() {
        Direction f = facing();
        return Vec3.atCenterOf(worldPosition).add(f.getStepX() * 1.2D, 0.0D, f.getStepZ() * 1.2D);
    }

    public void charge(Player target) {
        if (busy() || level == null) {
            return;
        }
        charging = TELL;
        mark = target.getUUID();
        aim = target.position();
        triggerAnim("maw", "charge");
        level.playSound(null, worldPosition, FFSounds.FROST_CHARGE.get(), SoundSource.HOSTILE, 1.6F, 1.3F);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FrostCannonBlockEntity maw) {
        if (maw.charging < 0) {
            return;
        }
        Player p = maw.mark != null ? level.getPlayerByUUID(maw.mark) : null;
        if (p != null && p.isAlive() && maw.charging > 6) {
            maw.aim = p.position();                     // it tracks them until the last moment of its tell
        }
        if (--maw.charging <= 0) {
            maw.charging = -1;
            if (maw.aim != null) {
                Vec3 from = maw.muzzle();
                int flight = Mth.clamp((int) (from.distanceTo(maw.aim) * 1.3D), 12, 32);
                level.addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.MawBombEntity(level, from, maw.aim,
                        flight));                     // (its own: harmless but for the hearths - 07.10.2026)
                maw.triggerAnim("maw", "fire");
                level.playSound(null, pos, FFSounds.ICE_IMPACT.get(), SoundSource.HOSTILE, 1.8F, 1.1F);
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5D);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "maw", 0, s -> s.setAndContinue(IDLE))
                .triggerableAnim("charge", CHARGE).triggerableAnim("fire", FIRE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
