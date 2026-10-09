package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * The nest's clock. It only breeds while somebody is near; it keeps the room
 * at a number rather than filling it - its own kind up to its MaxNearby, and
 * nothing at all while {@link #CROWD} hostile things of any kind
 * are about it - the risen dead aside: the
 * citadel's skeletons are its remains got up, not a nest's brood.
 */
public class CitadelSpawnerBlockEntity extends BlockEntity implements GeoBlockEntity {

    /** How long the Crownbreaker takes to split one. */
    public static final int BREAK_TICKS = 40;
    /** Every nest about once a minute: the middle of its own
     *  delay stretched to a minute (the default 240-480 ticks: 1200), give or take a tenth. */
    private static final float NEST_MINUTE = 1200.0F / 360.0F;
    /** Hostile things of any kind within 16 blocks at which a nest holds its brood back. */
    private static final int CROWD = 6;
    /** Ticks it swells before its brood comes out (its spawn clip throws it open at 1.45 s). */
    private static final int WINDUP = 28;
    private int windup;

    private final List<String> entities = new ArrayList<>();
    private int maxNearby = 4;
    private int spawnCount = 2;
    private int minDelay = 240;
    private int maxDelay = 480;
    private int playerRange = 18;
    private int spawnRange = 4;
    private int delay = 40;
    private int breaking;
    /** The rune of the part of the citadel this nest is in, and the chance its brood drops it. */
    private String rune = "";
    private float runeChance;

    /** ITS BODY: a cocoon of ice that breathes while it waits, throws
     *  its shards open as its brood comes out, shudders with the wedge in it (tools/gen_nest_beacon.py). */
    private static final RawAnimation NEST_IDLE = RawAnimation.begin().thenLoop("animation.frost_nest.idle");
    /* (triggered, so played once and let go: held, or run on into the idle, it never ended - the controller kept it, and
     * every brood after the first came out of a nest that did not move. The idle is the controller's own once it ends.) */
    private static final RawAnimation NEST_SPAWN = RawAnimation.begin().then("animation.frost_nest.spawn",
            software.bernie.geckolib.animation.Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation NEST_BREAK = RawAnimation.begin().thenLoop("animation.frost_nest.breaking");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CitadelSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(FFBlockEntities.CITADEL_SPAWNER.get(), pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "nest", 4, s -> s.setAndContinue(NEST_IDLE))
                .triggerableAnim("spawn", NEST_SPAWN).triggerableAnim("breaking", NEST_BREAK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public boolean isBreaking() {
        return breaking > 0;
    }

    public void beginBreaking() {
        if (breaking == 0) {
            breaking = 1;
            setChanged();
            triggerAnim("nest", "breaking");
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CitadelSpawnerBlockEntity nest) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // no snow on a nest: none can settle on it (the snow_layer_cannot_survive_on
        // tag), and what settled before that is shaken off
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 100L) == 0L &&level.getBlockState(pos.above()).is(net.minecraft.world.level.block.Blocks.SNOW)) {
            level.removeBlock(pos.above(), false);
        }
        if (nest.breaking > 0) {
            nest.breaking++;
            float progress = nest.breaking / (float) BREAK_TICKS;
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    2 + (int) (progress * 8), 0.4D, 0.4D, 0.4D, 0.1D + progress * 0.2D);
            if (nest.breaking % 10 == 0) {
                serverLevel.playSound(null, pos, FFSounds.ICE_CRACK.get(), SoundSource.BLOCKS, 1.4F, 0.6F + progress);
            }
            if (nest.breaking >= BREAK_TICKS) {
                serverLevel.playSound(null, pos, FFSounds.SHOCKWAVE.get(), SoundSource.BLOCKS, 1.8F, 1.1F);
                serverLevel.sendParticles(FFParticles.SOUL_FROST.get(), pos.getX() + 0.5D, pos.getY() + 0.5D,
                        pos.getZ() + 0.5D, 40, 0.5D, 0.5D, 0.5D, 0.12D);
                level.destroyBlock(pos, false);
                // what is left of it: a Nest Shard (with an iron ingot and an Everfrost Crystal, a Shackle Key)
                net.minecraft.world.entity.item.ItemEntity shard = new net.minecraft.world.entity.item.ItemEntity(serverLevel,
                        pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        new net.minecraft.world.item.ItemStack(com.jastkub.frozenfortress.registry.FFItems.NEST_SHARD.get(),
                                //
                                serverLevel.random.nextFloat() < 0.2F ? 2 : 1));
                shard.setDefaultPickUpDelay();
                serverLevel.addFreshEntity(shard);
            }
            return;
        }
        if (nest.entities.isEmpty()) {
            return;
        }
        AABB near = new AABB(pos).inflate(nest.playerRange);
        boolean watched = false;
        for (Player p : serverLevel.players()) {
            if (!p.isSpectator() && !p.isCreative() && near.contains(p.position())) {
                watched = true;
                break;
            }
        }
        if (!watched) {
            return;
        }
        boolean breeding = nest.windup > 0;
        if (breeding) {
            if (--nest.windup > 0) {
                return;                                     // still swelling
            }
        } else if (--nest.delay > 0) {
            return;
        }
        if (!breeding) {
            // SLOWER: in
            // code, so the nests already in a world follow too - a default nest now 54 to 66 seconds after its last
            nest.delay = (int) ((nest.minDelay + nest.maxDelay) / 2.0F * NEST_MINUTE
                    * (0.9F + 0.2F * serverLevel.random.nextFloat()));
            nest.setChanged();
        }
        List<EntityType<?>> types = new ArrayList<>();
        for (String id : nest.entities) {
            EntityType.byString(id).ifPresent(types::add);
        }
        if (types.isEmpty()) {
            return;
        }
        AABB room = new AABB(pos).inflate(16);
        int present = serverLevel.getEntities((Entity) null, room, e -> types.contains(e.getType())).size();
        int crowd = serverLevel.getEntities((Entity) null, room,
                e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive()
                        && !(e instanceof com.jastkub.frozenfortress.entity.FrostSkeletonEntity)).size();
        int toSpawn = Math.min(nest.spawnCount, Math.min(nest.maxNearby - present, CROWD - crowd));
        if (!breeding) {
            // it swells first, and only if there is room for its brood
            if (toSpawn > 0) {
                nest.windup = WINDUP;
                nest.triggerAnim("nest", "spawn");
                serverLevel.playSound(null, pos, FFSounds.FROST_CHARGE.get(), SoundSource.HOSTILE, 0.9F, 0.8F);
            }
            return;
        }
        for (int n = 0; n < toSpawn; n++) {
            EntityType<?> type = types.get(serverLevel.random.nextInt(types.size()));
            for (int attempt = 0; attempt < 12; attempt++) {
                double x = pos.getX() + 0.5D + (serverLevel.random.nextDouble() - 0.5D) * 2 * nest.spawnRange;
                double y = pos.getY() + serverLevel.random.nextInt(3) - 1;
                double z = pos.getZ() + 0.5D + (serverLevel.random.nextDouble() - 0.5D) * 2 * nest.spawnRange;
                BlockPos at = BlockPos.containing(x, y, z);
                if (!serverLevel.getBlockState(at.below()).isFaceSturdy(serverLevel, at.below(), net.minecraft.core.Direction.UP)) {
                    continue;
                }
                if (!serverLevel.noCollision(type.getSpawnAABB(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D))) {
                    continue;
                }
                Entity e = type.create(serverLevel);
                if (!(e instanceof Mob mob)) {
                    break;
                }
                mob.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, serverLevel.random.nextFloat() * 360.0F, 0.0F);
                EventHooks.finalizeMobSpawn(mob, serverLevel, serverLevel.getCurrentDifficultyAt(at),
                        MobSpawnType.SPAWNER, null);
                if (mob.isSpawnCancelled()) {
                    break;
                }
                if (!nest.rune.isEmpty()) {
                    mob.getPersistentData().putString(com.jastkub.frozenfortress.event.CitadelEvents.RUNE, nest.rune);
                    mob.getPersistentData().putFloat(com.jastkub.frozenfortress.event.CitadelEvents.RUNE_CHANCE, nest.runeChance);
                }
                serverLevel.addFreshEntityWithPassengers(mob);
                serverLevel.sendParticles(FFParticles.SOUL_FROST.get(), mob.getX(), mob.getY() + 1.0D, mob.getZ(),
                        20, 0.4D, 0.8D, 0.4D, 0.04D);
                serverLevel.playSound(null, at, FFSounds.FROST_RELEASE.get(), SoundSource.HOSTILE, 1.0F, 1.4F);
                break;
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        entities.clear();
        for (Tag t : tag.getList("Entities", Tag.TAG_STRING)) {
            entities.add(t.getAsString());
        }
        if (tag.contains("MaxNearby")) maxNearby = tag.getInt("MaxNearby");
        if (tag.contains("SpawnCount")) spawnCount = tag.getInt("SpawnCount");
        if (tag.contains("MinDelay")) minDelay = tag.getInt("MinDelay");
        if (tag.contains("MaxDelay")) maxDelay = tag.getInt("MaxDelay");
        if (tag.contains("PlayerRange")) playerRange = tag.getInt("PlayerRange");
        if (tag.contains("SpawnRange")) spawnRange = tag.getInt("SpawnRange");
        if (tag.contains("Delay")) delay = tag.getInt("Delay");
        breaking = tag.getInt("Breaking");
        rune = tag.getString("Rune");
        runeChance = tag.getFloat("RuneChance");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (String id : entities) {
            list.add(StringTag.valueOf(id));
        }
        tag.put("Entities", list);
        tag.putInt("MaxNearby", maxNearby);
        tag.putInt("SpawnCount", spawnCount);
        tag.putInt("MinDelay", minDelay);
        tag.putInt("MaxDelay", maxDelay);
        tag.putInt("PlayerRange", playerRange);
        tag.putInt("SpawnRange", spawnRange);
        tag.putInt("Delay", delay);
        tag.putInt("Breaking", breaking);
        tag.putString("Rune", rune);
        tag.putFloat("RuneChance", runeChance);
    }
}
