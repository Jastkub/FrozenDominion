package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * THE ONLY LIGHT IN THE LIGHTLESS CHAMBERS (Komory Bez Swiatla) is a lit campfire, and everything in the Shade
 * Shepherd's fight is decided by it: a shade is drawn only within a lit fire's light (ShadeRenderer), it keeps out of
 * that light and takes harder blows in it (ShadeEntity), and the Shepherd's breath goes out to put fires out
 * (ShadeShepherdGustEntity). Both sides ask the same question here, the same way.
 *
 * <p>FOUND THROUGH THE CHUNKS' BLOCK ENTITIES, not by scanning blocks: a campfire always carries one (its cooking
 * slots), the client has them too, and a chunk holds a handful - cheap enough for every shade to ask every few ticks.
 * Whether it burns is its block state's {@link CampfireBlock#LIT}, as the Frost Heart reads its hearths.
 */
public final class ShadeLight {

    /**
     * A fire the Shepherd put out SMOULDERS a while and will not take a spark: flint struck on it only hisses until then (CommonEvents.onRelight). Server side; by position.
     */
    private static final java.util.Map<Long, Long> SMOULDER = new java.util.HashMap<>();
    public static final int SMOULDER_TICKS = 80;

    /** Is the fire at `p` still smouldering from his breath? */
    public static boolean smouldering(Level level, BlockPos p) {
        Long until = SMOULDER.get(p.asLong());
        if (until == null) {
            return false;
        }
        if (level.getGameTime() >= until || level.getGameTime() < until - SMOULDER_TICKS - 20) {
            SMOULDER.remove(p.asLong());
            return false;
        }
        return true;
    }

    /** How far a lit campfire's light shows a shade (blocks, from the fire's centre). */
    public static final double SIGHT = 6.0D;

    private ShadeLight() {
    }

    public static boolean isLit(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT);
    }

    /** Is a lit campfire within `r` blocks of `at`? */
    public static boolean litNear(Level level, Vec3 at, double r) {
        return nearestLit(level, at, r) != null;
    }

    /** The nearest lit campfire within `r` blocks of `at`, or null. */
    @Nullable
    public static BlockPos nearestLit(Level level, Vec3 at, double r) {
        BlockPos best = null;
        double bestD = r * r;
        for (BlockPos p : campfires(level, at, r)) {
            double d = p.distToCenterSqr(at.x, at.y, at.z);
            if (d < bestD && isLit(level, p)) {
                best = p;
                bestD = d;
            }
        }
        return best;
    }

    /** Every campfire, lit or not, within `r` blocks of `at` (by the block entities of the chunks it touches). */
    public static List<BlockPos> campfires(Level level, Vec3 at, double r) {
        List<BlockPos> out = new ArrayList<>();
        int cx0 = SectionPos.blockToSectionCoord(Mth.floor(at.x - r));
        int cx1 = SectionPos.blockToSectionCoord(Mth.floor(at.x + r));
        int cz0 = SectionPos.blockToSectionCoord(Mth.floor(at.z - r));
        int cz1 = SectionPos.blockToSectionCoord(Mth.floor(at.z + r));
        double r2 = r * r;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof CampfireBlockEntity && !be.isRemoved()) {
                        BlockPos p = be.getBlockPos();
                        if (p.distToCenterSqr(at.x, at.y, at.z) <= r2) {
                            out.add(p.immutable());
                        }
                    }
                }
            }
        }
        return out;
    }

    /** The lit ones among `fires`. */
    public static List<BlockPos> lit(Level level, List<BlockPos> fires) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : fires) {
            if (isLit(level, p)) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * A fire PUT OUT: its LIT set false (what was cooking on it dropped, as water does it), a hiss, and the picture
     * of it going out - the dark wrapping the logs, the embers going cold, the smoke (fx_shade_snuff, a model of its
     * own; the smoke particles only ride along). False if it was not burning.
     */
    public static boolean snuff(ServerLevel level, BlockPos p, @Nullable Entity by) {
        BlockState s = level.getBlockState(p);
        if (!(s.getBlock() instanceof CampfireBlock) || !s.getValue(CampfireBlock.LIT)) {
            return false;
        }
        SMOULDER.put(p.asLong(), level.getGameTime() + SMOULDER_TICKS);
        CampfireBlock.dowse(by, level, p, s);
        level.setBlock(p, s.setValue(CampfireBlock.LIT, false), 11);
        level.playSound(null, p, FFSounds.SHADE_SHEPHERD_SNUFF.get(), SoundSource.HOSTILE, 1.8F,
                0.9F + level.random.nextFloat() * 0.2F);
        AttackFxEntity.spawn(level, "shade_snuff", Vec3.atBottomCenterOf(p), 0.0F, 1.0F, 30, null);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX() + 0.5D, p.getY() + 0.6D, p.getZ() + 0.5D,
                4, 0.15D, 0.1D, 0.15D, 0.01D);
        return true;
    }
}
