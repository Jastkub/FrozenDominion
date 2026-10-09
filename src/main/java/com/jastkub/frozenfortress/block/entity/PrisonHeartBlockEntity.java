package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFBlocks;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * The prison's heart, in the floor under the statue of the Monstrosity.
 *
 * <p>Asleep until a player comes within reach. Then the statue SHUDDERS - a
 * few seconds of cracking ice, splinters raining off it - and breaks, and
 * what was inside it is standing where it stood, roaring. Its death raises
 * the reliquary with the Key of the Throne.
 */
public class PrisonHeartBlockEntity extends BlockEntity {

    /** The Monstrosity as the prison's guardian, not as the king's summon. */
    private static final double GUARDIAN_HEALTH = 600.0D;     // (06.10.2026: 800 behind armour 30 was not a fight)
    /** How long the statue shakes before it breaks. */
    private static final int WAKE_TICKS = 70;

    private static final int ASLEEP = 0, WAKING = 1, LOOSE = 2, DONE = 3;

    private int stage = ASLEEP;
    private int wakeClock;
    private int radius = 26;
    private int[] statue = {-7, 1, -4, 7, 11, 4};       // the core blocks' box, from here
    private int[] statueAt = {0, 1, 0};                  // the statue block
    private int[] spawn = {0, 1, 0};
    private int[] reliquary = {0, 1, 30};
    private float yaw = 180.0F;
    private String lootTable = "frozen_dominion:chests/citadel/reliquary";

    public PrisonHeartBlockEntity(BlockPos pos, BlockState state) {
        super(FFBlockEntities.PRISON_HEART.get(), pos, state);
    }

    /** The hearts loaded on this client: the awakening's cutscene watches them. */
    public static final java.util.Set<PrisonHeartBlockEntity> CLIENT =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            CLIENT.add(this);
        } else if (level != null) {
            SERVER.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT.remove(this);
        SERVER.remove(this);
    }

    /** The statue shaking, about to break. */
    public boolean isWaking() {
        return stage == WAKING;
    }

    /** The guardian dead: the prison's fight is over. */
    public boolean isDone() {
        return stage == DONE;
    }

    /** The hearts loaded on the server: nothing may be built in their arenas. */
    public static final java.util.Set<PrisonHeartBlockEntity> SERVER =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /** Is that point inside this heart's arena (the ring round it)? */
    public boolean inArena(BlockPos p) {
        double dx = p.getX() + 0.5D - (worldPosition.getX() + 0.5D);
        double dz = p.getZ() + 0.5D - (worldPosition.getZ() + 0.5D);
        int dy = p.getY() - worldPosition.getY();
        return dx * dx + dz * dz <= 35.0D * 35.0D && dy >= -2 && dy <= 42;
    }

    /** The guardian out (and not yet dead). */
    public boolean isLoose() {
        return stage == LOOSE;
    }

    /** The middle of the statue, at its chest: what the awakening's camera looks at. */
    public net.minecraft.world.phys.Vec3 statueCentre() {
        return new net.minecraft.world.phys.Vec3(worldPosition.getX() + (statue[0] + statue[3]) / 2.0D + 0.5D,
                worldPosition.getY() + statue[1] + (statue[4] - statue[1]) * 0.6D,
                worldPosition.getZ() + (statue[2] + statue[5]) / 2.0D + 0.5D);
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PrisonHeartBlockEntity heart) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (heart.stage == WAKING) {
            heart.tickWaking(serverLevel);
            return;
        }
        if (heart.stage != ASLEEP || level.getGameTime() % 10 != 0) {
            return;
        }
        for (Player p : serverLevel.players()) {
            if (p.isSpectator() || p.isCreative()) {
                continue;
            }
            double dx = p.getX() - (pos.getX() + 0.5D);
            double dz = p.getZ() - (pos.getZ() + 0.5D);
            double dy = p.getY() - pos.getY();
            if (dx * dx + dz * dz <= heart.radius * heart.radius && dy > -2 && dy < 40) {
                heart.beginWaking(serverLevel);
                return;
            }
        }
    }

    private BlockPos at(int[] off) {
        return worldPosition.offset(off[0], off[1], off[2]);
    }

    private void beginWaking(ServerLevel serverLevel) {
        stage = WAKING;
        wakeClock = WAKE_TICKS;
        sync();
        if (serverLevel.getBlockEntity(at(statueAt)) instanceof CitadelStatueBlockEntity s) {
            s.startShaking(WAKE_TICKS);
        }
        // a bass impact to turn every head, then the ice pouring off it as it shakes
        serverLevel.playSound(null, at(statueAt), FFSounds.MONSTROSITY_STATUE_WAKE.get(), SoundSource.HOSTILE, 4.0F, 1.0F);
    }

    private void tickWaking(ServerLevel serverLevel) {
        int done = WAKE_TICKS - wakeClock;
        BlockPos base = at(statueAt);
        if (--wakeClock > 0) {
            setChanged();
            return;
        }
        shatter(serverLevel);
    }

    /** The ice lets go: the statue and its core are gone, the guardian is out. */
    private void shatter(ServerLevel serverLevel) {
        stage = LOOSE;
        sync();
        BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
        BlockPos a = at(new int[] {statue[0], statue[1], statue[2]});
        BlockPos b = at(new int[] {statue[3], statue[4], statue[5]});
        int burst = 0;
        for (BlockPos p : BlockPos.betweenClosed(a, b)) {
            BlockState bs = serverLevel.getBlockState(p);
            if (bs.is(FFBlocks.STATUE_CORE.get()) || bs.is(FFBlocks.CITADEL_STATUE.get())) {
                serverLevel.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        BlockPos base = at(statueAt);
        if (serverLevel.getBlockState(base).is(FFBlocks.CITADEL_STATUE.get())) {
            serverLevel.setBlock(base, Blocks.AIR.defaultBlockState(), 2);
        }
        BlockPos where = at(spawn);
        HollowGolemEntity golem = FFEntities.HOLLOW_GOLEM.get().create(serverLevel);
        if (golem != null) {
            golem.moveTo(where.getX() + 0.5D, where.getY(), where.getZ() + 0.5D, yaw, 0.0F);
            golem.setYBodyRot(yaw);
            golem.setYHeadRot(yaw);
            var health = golem.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(GUARDIAN_HEALTH);
            }
            golem.setHealth((float) GUARDIAN_HEALTH);
            golem.setPersistenceRequired();
            golem.setPrisonHeart(worldPosition);
            ForgeEventFactory.onFinalizeSpawn(golem, serverLevel, serverLevel.getCurrentDifficultyAt(where),
                    MobSpawnType.STRUCTURE, null, null);
            golem.wakeFromStatue();
            serverLevel.addFreshEntity(golem);
        }
        serverLevel.playSound(null, where, FFSounds.ICE_SHATTER.get(), SoundSource.HOSTILE, 5.0F, 0.4F);
        serverLevel.playSound(null, where, FFSounds.SHOCKWAVE.get(), SoundSource.HOSTILE, 4.0F, 0.6F);
        serverLevel.playSound(null, where, net.minecraft.sounds.SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 4.0F, 0.5F);
        // the shell bursts: the one spray of ice of its waking, and splinters flying that hurt
        serverLevel.sendParticles(FFParticles.ICE_SHARD.get(), where.getX() + 0.5D, where.getY() + 5.0D,
                where.getZ() + 0.5D, 400, 4.0D, 5.0D, 3.0D, 0.45D);
        CitadelStatueBlockEntity.splinters(serverLevel, where, 2.6F, 28, 4.0F);
    }

    /** Called by the guardian as it dies. */
    public void onGuardianDeath(ServerLevel serverLevel) {
        if (stage == DONE) {
            return;
        }
        stage = DONE;
        sync();
        BlockPos at = at(reliquary);
        serverLevel.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 3);
        if (serverLevel.getBlockEntity(at) instanceof ChestBlockEntity chest) {
            ResourceLocation rl = ResourceLocation.tryParse(lootTable);
            if (rl != null) {
                chest.setLootTable(rl, serverLevel.random.nextLong());
            }
        }
        serverLevel.playSound(null, at, FFSounds.VELKHAR_WHISPER.get(), SoundSource.BLOCKS, 2.0F, 0.8F);
        serverLevel.sendParticles(FFParticles.SOUL_FROST.get(), at.getX() + 0.5D, at.getY() + 1.0D, at.getZ() + 0.5D,
                60, 0.6D, 1.0D, 0.6D, 0.06D);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stage = tag.getInt("Stage");
        wakeClock = tag.getInt("WakeClock");
        if (tag.contains("Radius")) radius = tag.getInt("Radius");
        if (tag.contains("Statue")) statue = tag.getIntArray("Statue");
        if (tag.contains("StatueAt")) statueAt = tag.getIntArray("StatueAt");
        if (tag.contains("Spawn")) spawn = tag.getIntArray("Spawn");
        if (tag.contains("Reliquary")) reliquary = tag.getIntArray("Reliquary");
        if (tag.contains("Yaw")) yaw = tag.getFloat("Yaw");
        if (tag.contains("LootTable")) lootTable = tag.getString("LootTable");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Stage", stage);
        tag.putInt("WakeClock", wakeClock);
        tag.putInt("Radius", radius);
        tag.putIntArray("Statue", statue);
        tag.putIntArray("StatueAt", statueAt);
        tag.putIntArray("Spawn", spawn);
        tag.putIntArray("Reliquary", reliquary);
        tag.putFloat("Yaw", yaw);
        tag.putString("LootTable", lootTable);
    }
}
