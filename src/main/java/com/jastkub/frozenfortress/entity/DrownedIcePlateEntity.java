package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ONE PLATE OF THE CISTERNS' FLOOR - three blocks square - and the Drowned Lady's weapon. Two kinds, drawn from two models (tools/gen_drowned_lady.py):
 *
 * <ul>
 *   <li>A PLATE SHE BREAKS. It CRACKS first - thirty ticks, three stages, each heard and each a new set of lit lines
 *   across it, the pieces rocking more and more - which is the whole of its warning: step off. Then it BREAKS: the
 *   ice of the floor under it really is gone (the blocks are water while the hole is open), whoever was on it goes
 *   into the freezing water - slowed, frostbitten, the cold biting every second until they climb out - and eleven
 *   seconds later it skins over again and the floor's own ice is put back exactly as it was.</li>
 *   <li>A RUNE PLATE: thick old ice with a rune cut in it. It NEVER breaks, her tide parts round it, her hands cannot
 *   come up through it - the ground the fight teaches you to find. It flares when her tide passes over it.</li>
 * </ul>
 *
 * <p>Particles carry none of this ("totalne gowno"): the cracks, the hole, the floes and the new ice are all geometry.
 *
 * <p>It is SAVED (unlike the other pieces of her fight): a hole open when the chunk unloads must still be able to put
 * the floor back, so the blocks it took are written down with it.
 */
public class DrownedIcePlateEntity extends Entity implements GeoEntity {

    public static final int CRACK = 0, BROKEN = 1, FREEZE = 2, RUNE = 3, RUNE_APPEAR = 4, RUNE_FADE = 5;

    /** The beats of its clips (tools/gen_drowned_lady.py: PLATE_CRACK, PLATE_FREEZE). */
    public static final int CRACK_T = 30;
    static final int HOLE_T = 220;
    static final int FREEZE_T = 20;
    static final int APPEAR_T = 20;
    static final int FADE_T = 24;
    /** The ice under you giving way. */
    static final float BREAK_DMG = 5.0F;
    /** The water's bite, every second you are in it. */
    // three, from one; and it drags you down (chill)
    static final float COLD_DMG = 5.0F;
    /** Half its width, in blocks. */
    public static final double HALF = 1.5D;

    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(DrownedIcePlateEntity.class, EntityDataSerializers.INT);
    /** Counted up each time the tide parts round a rune: the client flares it on the change. */
    private static final EntityDataAccessor<Integer> WARD =
            SynchedEntityData.defineId(DrownedIcePlateEntity.class, EntityDataSerializers.INT);

    private static final String P = "animation.drowned_ice_plate.";
    private static final String R = "animation.drowned_rune_plate.";
    private static final RawAnimation CRACK_ANIM = RawAnimation.begin().thenPlayAndHold(P + "crack");
    private static final RawAnimation BREAK_ANIM = RawAnimation.begin().thenPlay(P + "break").thenLoop(P + "hole");
    private static final RawAnimation FREEZE_ANIM = RawAnimation.begin().thenPlayAndHold(P + "freeze");
    private static final RawAnimation APPEAR_ANIM = RawAnimation.begin().thenPlayAndHold(R + "appear");
    private static final RawAnimation RUNE_IDLE = RawAnimation.begin().thenLoop(R + "idle");
    private static final RawAnimation WARD_ANIM = RawAnimation.begin().thenPlay(R + "ward");
    private static final RawAnimation FADE_ANIM = RawAnimation.begin().thenPlayAndHold(R + "fade");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private int age;
    /** How long its lady has been nowhere to be found (a rune plate fades if she is gone for good). */
    private int orphaned;
    /** The floor's blocks it took to make the hole, and what they were - put back, exactly, when it freezes. */
    private final List<BlockPos> cells = new ArrayList<>();
    private final List<BlockState> originals = new ArrayList<>();
    /** Who was in its water last tick: a splash for each one that falls in. */
    private final Set<UUID> inside = new HashSet<>();
    /** Client: when the last flare began. */
    private int wardStart = -100;

    public DrownedIcePlateEntity(EntityType<? extends DrownedIcePlateEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;                  // three blocks across from a small box
    }

    private DrownedIcePlateEntity(Level level, @Nullable Entity owner, BlockPos center, int phase) {
        this(FFEntities.DROWNED_ICE_PLATE.get(), level);
        this.ownerId = owner == null ? null : owner.getUUID();
        moveTo(center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D, 0.0F, 0.0F);
        entityData.set(PHASE, phase);
    }

    /** A plate starts to crack under `center` (the floor block at its middle); it breaks in CRACK_T. */
    public static DrownedIcePlateEntity crack(Level level, @Nullable Entity owner, BlockPos center) {
        DrownedIcePlateEntity p = new DrownedIcePlateEntity(level, owner, center, CRACK);
        level.addFreshEntity(p);
        level.playSound(null, center, FFSounds.DROWNED_LADY_ICE_CRACK.get(), SoundSource.HOSTILE, 1.3F, 1.0F);
        return p;
    }

    /** The ice under `center` gone at once - her hands came up through it. */
    public static DrownedIcePlateEntity open(Level level, @Nullable Entity owner, BlockPos center) {
        DrownedIcePlateEntity p = new DrownedIcePlateEntity(level, owner, center, BROKEN);
        p.breakOpen(false);
        level.addFreshEntity(p);
        return p;
    }

    /** A thick plate with a rune in it, coming up out of the floor's ice. */
    public static DrownedIcePlateEntity rune(Level level, @Nullable Entity owner, BlockPos center) {
        DrownedIcePlateEntity p = new DrownedIcePlateEntity(level, owner, center, RUNE_APPEAR);
        level.addFreshEntity(p);
        return p;
    }

    // ------------------------------------------------------------------------------------------------ the floor
    /** A block of the floor a plate can be cut from: ice with room over it and something solid under it (so the
     *  water the hole leaves cannot run away anywhere). */
    public static boolean iceCell(Level level, BlockPos p) {
        BlockPos below = p.below();
        //  SEALED ICE IS HER ICE TOO. Her arena was
        // sealed that day so no tool goes through it, and the sealing turned her floor to sealed_ice - which this did
        // not count, so no plate could crack anywhere, nor a rune be laid. The plate keeps what it took and puts it
        // back (originals/restore), so the floor stays sealed after every hole.
        return (level.getBlockState(p).is(BlockTags.ICE)
                        || level.getBlockState(p).is(com.jastkub.frozenfortress.registry.FFBlocks.UNMELTING_ICE.get())
                        || level.getBlockState(p).is(com.jastkub.frozenfortress.registry.FFBlocks.SEALED_ICE.get()))
                && level.getBlockState(p.above()).isAir() && level.getBlockState(p.above(2)).isAir()
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /** Can a plate be laid with its middle on `center`: all nine of its blocks floor ice, none under another plate. */
    public static boolean canPlate(Level level, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!iceCell(level, center.offset(dx, 0, dz))) {
                    return false;
                }
            }
        }
        AABB around = new AABB(center).inflate(3.0D, 2.0D, 3.0D);
        for (DrownedIcePlateEntity other : level.getEntitiesOfClass(DrownedIcePlateEntity.class, around)) {
            if (Math.abs(other.getX() - (center.getX() + 0.5D)) < 3.0D
                    && Math.abs(other.getZ() - (center.getZ() + 0.5D)) < 3.0D) {
                return false;
            }
        }
        return true;
    }

    /** The rune plate `e` stands on, or null (feet within a block of its face). */
    @Nullable
    public static DrownedIcePlateEntity runeUnder(Entity e) {
        return runeAt(e.level(), e.getX(), e.getY(), e.getZ(), 0.0D);
    }

    /** The rune plate over (x, z) - `margin` blocks wider all round - near height y, or null. */
    @Nullable
    public static DrownedIcePlateEntity runeAt(Level level, double x, double y, double z, double margin) {
        AABB around = new AABB(x - 2.5D, y - 2.0D, z - 2.5D, x + 2.5D, y + 2.0D, z + 2.5D);
        for (DrownedIcePlateEntity p : level.getEntitiesOfClass(DrownedIcePlateEntity.class, around,
                DrownedIcePlateEntity::isRune)) {
            if (p.covers(x, z, margin) && Math.abs(y - p.getY()) < 1.2D) {
                return p;
            }
        }
        return null;
    }

    /** The open hole over (x, z), or null. */
    @Nullable
    public static DrownedIcePlateEntity holeAt(Level level, double x, double y, double z) {
        AABB around = new AABB(x - 2.5D, y - 2.5D, z - 2.5D, x + 2.5D, y + 2.0D, z + 2.5D);
        for (DrownedIcePlateEntity p : level.getEntitiesOfClass(DrownedIcePlateEntity.class, around)) {
            if (p.covers(x, z, 0.0D) && (p.phase() == BROKEN || p.phase() == CRACK)) {
                return p;
            }
        }
        return null;
    }

    public boolean covers(double x, double z, double margin) {
        return Math.abs(x - getX()) <= HALF + margin && Math.abs(z - getZ()) <= HALF + margin;
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    public boolean isRune() {
        int p = phase();
        return p == RUNE || p == RUNE_APPEAR || p == RUNE_FADE;
    }

    /** Still to break, or broken: it counts against how many holes she may have open at once. */
    public boolean isBreaking() {
        return phase() == CRACK || phase() == BROKEN;
    }

    public boolean ownedBy(Entity e) {
        return ownerId != null && ownerId.equals(e.getUUID());
    }

    /** The middle block of the floor under it. */
    public BlockPos center() {
        return BlockPos.containing(getX(), getY() - 0.5D, getZ());
    }

    // ------------------------------------------------------------------------------------------------ its life
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        age++;
        switch (phase()) {
            case CRACK -> {
                // the second and third stages of the cracking, heard as they are seen (its clip: 0, 10, 20)
                if (age == 10 || age == 20) {
                    playSound(FFSounds.DROWNED_LADY_ICE_CRACK.get(), 1.4F, 0.9F + age * 0.012F);
                }
                if (age >= CRACK_T) {
                    breakOpen(true);
                }
            }
            case BROKEN -> {
                chill();
                if (age >= HOLE_T || ladyGone()) {
                    setPhase(FREEZE);
                    playSound(FFSounds.DROWNED_LADY_ICE_CRACK.get(), 0.8F, 0.6F);
                }
            }
            case FREEZE -> {
                if (age >= FREEZE_T) {
                    restore();
                    discard();
                }
            }
            case RUNE_APPEAR -> {
                if (age >= APPEAR_T) {
                    setPhase(RUNE);
                }
            }
            case RUNE -> {
                if (age % 20 == 0) {
                    orphaned = owner() == null ? orphaned + 20 : 0;
                    if (orphaned >= 600 || ladyGone()) {
                        fade();
                    }
                }
            }
            case RUNE_FADE -> {
                if (age >= FADE_T) {
                    discard();
                }
            }
            default -> discard();
        }
    }

    private void setPhase(int phase) {
        entityData.set(PHASE, phase);
        age = 0;
    }

    /** The plate goes: its blocks become the water under it, and whoever was standing on it goes in. */
    void breakOpen(boolean announce) {
        BlockPos c = center();
        cells.clear();
        originals.clear();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = c.offset(dx, 0, dz);
                if (iceCell(level(), p)) {
                    cells.add(p.immutable());
                    originals.add(level().getBlockState(p));
                    level().setBlock(p, Blocks.WATER.defaultBlockState(), 3);
                }
            }
        }
        entityData.set(PHASE, BROKEN);
        age = 0;
        if (!announce) {
            return;
        }
        level().playSound(null, c, FFSounds.DROWNED_LADY_ICE_BREAK.get(), SoundSource.HOSTILE, 1.6F, 1.0F);
        LivingEntity owner = owner();
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, plateBox(-0.4D, 1.0D), this::foe)) {
            v.hurt(owner != null ? damageSources().indirectMagic(this, owner) : damageSources().magic(), BREAK_DMG);
        }
    }

    /** THE WATER UNDER THE ICE: whoever is in the hole is slowed and frostbitten, and the cold bites every second. */
    private void chill() {
        Set<UUID> now = new HashSet<>();
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, plateBox(-1.2D, -0.15D), this::foe)) {
            if (!v.isInWater()) {
                continue;
            }
            now.add(v.getUUID());
            if (!inside.contains(v.getUUID())) {
                level().playSound(null, v.blockPosition(), FFSounds.DROWNED_LADY_SPLASH.get(), SoundSource.HOSTILE,
                        1.2F, 1.0F + random.nextFloat() * 0.15F);
            }
            // the cold goes
            // deeper: harder slowed and frostbitten, weakened, frozen through at once - and the water drags harder
            if (age % 10 == 0) {
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 3));
                v.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
                v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 160, 2));
                v.setTicksFrozen(Math.min(v.getTicksRequiredToFreeze() + 80, v.getTicksFrozen() + 70));
            }
            // the black water pulls at you: climbing out is a fight, not a hop
            if (v.getDeltaMovement().y > -0.4D) {
                v.setDeltaMovement(v.getDeltaMovement().add(0.0D, -0.075D, 0.0D));
                v.hurtMarked = true;
            }
            if (age % 20 == 0) {
                v.hurt(damageSources().freeze(), COLD_DMG);
            }
        }
        inside.clear();
        inside.addAll(now);
    }

    /** The box over its three blocks, from `lo` to `hi` blocks about its face. */
    private AABB plateBox(double lo, double hi) {
        return new AABB(getX() - HALF, getY() + lo, getZ() - HALF, getX() + HALF, getY() + hi, getZ() + HALF);
    }

    private boolean foe(LivingEntity e) {
        return e.isAlive() && !(e instanceof FrostServantEntity) && !(e instanceof DrownedHandsEntity)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** The floor's own ice back where it was - and nobody left inside it. */
    void restore() {
        if (cells.isEmpty()) {
            return;
        }
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, plateBox(-1.4D, 0.2D))) {
            if (v.getY() < getY()) {
                v.teleportTo(v.getX(), getY() + 0.02D, v.getZ());
            }
        }
        for (int i = 0; i < cells.size(); i++) {
            BlockPos p = cells.get(i);
            // only water is turned back to ice: never anything somebody (or something) has put there since
            if (level().getBlockState(p).is(Blocks.WATER)) {
                level().setBlock(p, originals.get(i), 3);
            }
        }
        cells.clear();
        originals.clear();
    }

    /** Her tide passed over this rune and it held: it flares. */
    public void flare() {
        entityData.set(WARD, entityData.get(WARD) + 1);
    }

    /** She is gone: the holes skin over now and the runes sink back into the ice. */
    public void ladyDied() {
        int p = phase();
        if (p == CRACK) {
            discard();                              // never broke: nothing to put back
        } else if (p == BROKEN) {
            setPhase(FREEZE);
        } else if (p == RUNE || p == RUNE_APPEAR) {
            fade();
        }
    }

    private void fade() {
        setPhase(RUNE_FADE);
    }

    private boolean ladyGone() {
        return ownerId != null && level() instanceof ServerLevel s && s.getEntity(ownerId) instanceof LivingEntity le
                && !le.isAlive();
    }

    @Nullable
    private LivingEntity owner() {
        if (ownerId != null && level() instanceof ServerLevel s && s.getEntity(ownerId) instanceof LivingEntity le) {
            return le;
        }
        return null;
    }

    /** Removed for good with a hole still open (a /kill, a fight reset): the floor goes back first. */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) {
            restore();
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------------------------------------ the rest
    @Override
    protected void defineSynchedData() {
        entityData.define(PHASE, CRACK);
        entityData.define(WARD, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (WARD.equals(key) && level().isClientSide && tickCount > 0) {
            wardStart = tickCount;
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64.0D * 64.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        entityData.set(PHASE, tag.getInt("Phase"));
        age = tag.getInt("Age");
        cells.clear();
        originals.clear();
        ListTag list = tag.getList("Cells", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            cells.add(NbtUtils.readBlockPos(c.getCompound("Pos")));
            originals.add(NbtUtils.readBlockState(level().holderLookup(Registries.BLOCK), c.getCompound("State")));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putInt("Phase", phase());
        tag.putInt("Age", age);
        ListTag list = new ListTag();
        for (int i = 0; i < cells.size(); i++) {
            CompoundTag c = new CompoundTag();
            c.put("Pos", NbtUtils.writeBlockPos(cells.get(i)));
            c.put("State", NbtUtils.writeBlockState(originals.get(i)));
            list.add(c);
        }
        tag.put("Cells", list);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "plate", 0, state -> state.setAndContinue(switch (phase()) {
            case CRACK -> CRACK_ANIM;
            case BROKEN -> BREAK_ANIM;
            case FREEZE -> FREEZE_ANIM;
            case RUNE_APPEAR -> APPEAR_ANIM;
            case RUNE_FADE -> FADE_ANIM;
            default -> tickCount - wardStart < 16 ? WARD_ANIM : RUNE_IDLE;
        })));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
