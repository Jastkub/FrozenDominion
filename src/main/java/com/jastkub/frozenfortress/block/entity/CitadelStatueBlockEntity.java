package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * What a statue is: which model, which hide, which pose, how big and which way
 * it faces - all from the structure's NBT, all sent to the client with the
 * chunk. It holds one pose for ever, unless something wakes it: then it
 * shudders for a while before whatever holds it lets go.
 */
public class CitadelStatueBlockEntity extends BlockEntity implements GeoBlockEntity {

    /** Block event: tremble for `param` ticks, worse as they run out. */
    public static final int EVENT_SHAKE = 1;
    /** Block event: shudder hard for `param` ticks - it is breaking. */
    public static final int EVENT_BREAK = 2;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private String model = "velkhar_statue";
    private String texture = "velkhar_f1";
    private String pose = "f1";
    private float scale = 2.0F;
    private float yaw = 180.0F;
    private final Set<String> hidden = new HashSet<>();
    /** Client: the game time the shudder ends, and how long it lasts in all. */
    private long shakeUntil;
    private int shakeLength = 1;
    private boolean shakeHard;
    /** What is inside it, if anything: a statue of a creature can break and let it out. */
    private String awaken = "";
    /** Kept for old statues' tags; a statue with a creature in it now always wakes. */
    private float chance;
    private int radius = 4;
    /** Added to every statue's Radius. */
    private static final double WAKE_EXTRA = 1.5D;
    private int breaking = -1;
    /** Ticks somebody has stood within Radius (it falls away again when nobody does). */
    private int near;
    /** How long it bears that before it breaks: a few seconds, its own. */
    private int wait;
    /** Pickaxe blows it has taken. */
    private int hits;
    private boolean warned;
    /** What the dead one still wears (FrostSkeletonEntity.VARIANT_BONES): its gear shown, and handed on as it rises. */
    private int variant;

    /** How long it shakes before it lets go - the last chance to smash it. */
    private static final int BREAK_TICKS = 40;
    /** Blows that shatter it, empty, before it breaks of itself. */
    public static final int HITS_TO_SHATTER = 3;

    public CitadelStatueBlockEntity(BlockPos pos, BlockState state) {
        super(FFBlockEntities.CITADEL_STATUE.get(), pos, state);
    }

    public String model() {
        return model;
    }

    public String texture() {
        return texture;
    }

    public float scale() {
        return scale;
    }

    public float yaw() {
        return yaw;
    }

    public boolean isHidden(String bone) {
        // the gear of the dead: only this one's own variant (and none at all on a statue written before there were
        // variants - those lie as the bare bones they were)
        if (bone.startsWith("v_")) {
            return !com.jastkub.frozenfortress.entity.FrostSkeletonEntity.wears(isRemains() ? variant : 0, bone);
        }
        return hidden.contains(bone);
    }

    /** One of the dead lying about the citadel, not a statue of ice. */
    public boolean isRemains() {
        return model.startsWith("remains");
    }

    /**
     * A DRIFT, not a statue (the expedition camp's ambush, user 06.10.2026: "zasadzka stada frostmawow"): a
     * frostmaw lying low under the snow, its shape a drift in a snow hide (textures/block/statue/snow_*). It
     * stirs at once, wakes the rest of its pack lying round it, and bursts out of the snow - no splinters of
     * ice, it was never ice - leaping at whoever woke it.
     */
    public boolean isDrift() {
        return texture.startsWith("snow");
    }

    /** How long a remains takes to get up once it has stirred, and how long it shakes before it does. */
    private int breakTicks() {
        return isRemains() ? 11 : isDrift() ? 5 : BREAK_TICKS * 4 / 5;      // (07.10.2026: a quarter faster)
    }

    /**
     * Another of the dead got up near it: it stirs too, after its own few
     * seconds. (Not those that answer only the Priestess's requiem.)
     */
    public void stir(int delay) {
        if (awaken.isEmpty() || warned || breaking >= 0 || radius <= 0 || isRemoved() || level == null) {
            return;
        }
        warned = true;
        near = 0;
        wait = delay;
        setChanged();
        startShaking(wait);
    }

    /** 0 when still, rising to 1 as the shudder runs out. Client only. */
    public float shake(float partialTick) {
        if (level == null) {
            return 0.0F;
        }
        float left = shakeUntil - (level.getGameTime() + partialTick);
        if (left <= 0.0F) {
            return 0.0F;
        }
        return 1.0F - left / Math.max(1, shakeLength);
    }

    /** Client: is it breaking (the hard shudder), not just trembling. */
    public boolean shakingHard() {
        return shakeHard;
    }

    /** Server: tell every client watching to shake it. Only the statues shake - the dead lie still until they get up;
     *  their rattle is heard, not seen. */
    public void startShaking(int ticks) {
        if (isRemains()) {
            return;
        }
        if (level != null && !level.isClientSide) {
            level.blockEvent(worldPosition, getBlockState().getBlock(), breaking >= 0 ? EVENT_BREAK : EVENT_SHAKE, ticks);
        }
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_SHAKE || id == EVENT_BREAK) {
            if (level != null) {
                shakeLength = Math.max(1, param);
                shakeUntil = level.getGameTime() + param;
                shakeHard = id == EVENT_BREAK;
            }
            return true;
        }
        return super.triggerEvent(id, param);
    }

    /**
     * Nothing comes off it while it shakes.
     */
    public static void clientTick(Level level, BlockPos pos, CitadelStatueBlockEntity statue) {
    }

    /**
     * Server: come near it once and it has noticed you - for good. From then on
     * it trembles, worse and worse, for a few seconds of its own (whether you
     * stay or run), cracks, shudders two seconds more and breaks, and what was
     * in it steps out. Until it breaks, a pickaxe can still finish it empty.
     */
    public static void serverTick(Level level, BlockPos pos, CitadelStatueBlockEntity statue) {
        if (statue.awaken.isEmpty() || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        boolean bones = statue.isRemains();
        if (statue.breaking >= 0) {
            if (++statue.breaking >= statue.breakTicks()) {
                statue.shatter(serverLevel, true);
            }
            return;
        }
        if (statue.warned) {
            // it has noticed somebody: it no longer matters where they are
            if (++statue.near >= statue.wait) {
                statue.breaking = 0;
                statue.setChanged();
                statue.startShaking(statue.breakTicks());
                serverLevel.playSound(null, pos, bones ? com.jastkub.frozenfortress.registry.FFSounds.FROST_SKELETON_HURT.get()
                                : statue.isDrift() ? com.jastkub.frozenfortress.registry.FFSounds.FROSTMAW_GROWL.get()
                                : com.jastkub.frozenfortress.registry.FFSounds.ICE_CRACK.get(),
                        net.minecraft.sounds.SoundSource.HOSTILE, bones ? 1.0F : 1.8F, statue.isDrift() ? 0.9F : 0.7F);
            } else if (statue.isDrift()) {
                // (a drift is quiet until it goes: the snow only shivers)
            } else if (statue.near % (bones ? 12 : 20) == 0) {
                // the dead rattle where the ice cracks
                serverLevel.playSound(null, pos, bones ? com.jastkub.frozenfortress.registry.FFSounds.FROST_SKELETON_IDLE.get()
                                : com.jastkub.frozenfortress.registry.FFSounds.ICE_CRACK.get(),
                        net.minecraft.sounds.SoundSource.HOSTILE, 0.7F, 1.3F + 0.3F * statue.near / statue.wait);
            }
            return;
        }
        if (level.getGameTime() % 5 != 0 || statue.radius <= 0) {
            return;                              // (radius 0: it answers only the Priestess's requiem)
        }
        for (net.minecraft.world.entity.player.Player p : serverLevel.players()) {
            if (p.isSpectator() || p.isCreative()) {
                continue;
            }
            double dx = p.getX() - (pos.getX() + 0.5D), dz = p.getZ() - (pos.getZ() + 0.5D);
            //
            double reach = statue.radius + WAKE_EXTRA;
            // - only one that can SEE you: no floor between
            if (dx * dx + dz * dz <= reach * reach && Math.abs(p.getY() - pos.getY()) < 6.0D && statue.sees(serverLevel, p)) {
                statue.warned = true;
                statue.near = 0;
                // 2 to 4 seconds of trembling (the dead: 1 to 2 - they are up before you are past them; a drift:
                // under a second, and the rest of the pack round it a beat behind)
                //
                statue.wait = bones ? 16 + serverLevel.random.nextInt(17)
                        : statue.isDrift() ? 5 + serverLevel.random.nextInt(6) : 32 + serverLevel.random.nextInt(33);
                statue.setChanged();
                statue.startShaking(statue.wait);
                if (statue.isDrift()) {
                    for (BlockPos q : BlockPos.betweenClosed(pos.offset(-16, -4, -16), pos.offset(16, 4, 16))) {
                        if (!q.equals(pos) && serverLevel.getBlockEntity(q) instanceof CitadelStatueBlockEntity other
                                && other.isDrift()) {
                            other.stir(8 + serverLevel.random.nextInt(16));
                        }
                    }
                }
                serverLevel.playSound(null, pos, bones ? com.jastkub.frozenfortress.registry.FFSounds.FROST_SKELETON_IDLE.get()
                                : com.jastkub.frozenfortress.registry.FFSounds.ICE_CRACK.get(),
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, bones ? 1.1F : 1.5F);
                return;
            }
        }
    }

    /**
     * A pickaxe blow (the statue's cells take no damage - the citadel's Mining
     * Fatigue would make that hopeless - they count blows). Three before it
     * breaks of itself and it shatters with nothing in it.
     */
    public void strike(net.minecraft.server.level.ServerLevel serverLevel, BlockPos at) {
        // the king's statues, the Monstrosity's and the great guards of the halls (scaled up) are monuments:
        // they do not break. Any other creature's statue breaks under a pick, empty or not
        // - a great one with a creature
        // in it is no monument: what wakes can be broken first, whatever its size. And the other way round (the same
        // day - the Aurochs among the king's
        // servants broke under a pick): WHAT DOES NOT WAKE DOES NOT BREAK. Every figure of the garrison holds its
        // creature (the scenes' too); what holds none is the citadel's stone.
        // A GUARD THAT ANSWERS ONLY A CALL DOES NOT CRACK: the treasury's statues wait for their ambush and nothing else - a pick rings off them like a
        // monument's until the call comes
        boolean monument = model.startsWith("velkhar") || model.startsWith("monstrosity") || awaken.isEmpty()
                || answersOnlyACall();
        if (monument) {
            serverLevel.playSound(null, at, isRemains() ? com.jastkub.frozenfortress.registry.FFSounds.FROST_SKELETON_STEP.get()
                            : net.minecraft.sounds.SoundEvents.GLASS_HIT,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.6F);
            return;
        }
        // THE DEAD THAT GET UP ARE NOT BROKEN BEFOREHAND: a blow wakes them - it gets up at once, and while it is
        // getting up nothing touches it (FrostSkeletonEntity, ST_RISE)
        if (isRemains() && !awaken.isEmpty()) {
            serverLevel.playSound(null, at, com.jastkub.frozenfortress.registry.FFSounds.FROST_SKELETON_HURT.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.1F, 0.8F);
            shatter(serverLevel, true);
            return;
        }
        if (isDrift() && !awaken.isEmpty()) {                 // poke a drift and what is under it comes out
            shatter(serverLevel, true);
            return;
        }
        hits++;
        setChanged();
        startShaking(warned ? Math.max(6, wait - near) : 6);   // a blow does not still its trembling
        serverLevel.playSound(null, at, isRemains() ? com.jastkub.frozenfortress.registry.FFSounds.FROST_SKELETON_HURT.get()
                        : com.jastkub.frozenfortress.registry.FFSounds.ICE_CRACK.get(),
                net.minecraft.sounds.SoundSource.BLOCKS, 1.1F, 1.0F + hits * 0.3F);
        // the dead are only bone: two blows break them up before they can get up
        if (hits >= (isRemains() ? 2 : HITS_TO_SHATTER)) {
            shatter(serverLevel, false);
        }
    }

    /**
     * Called awake (the Priestess of Rime's requiem): a statue with a creature
     * in it breaks and lets it out at once. The king's statues and the empty
     * ones do not answer. True if it woke.
     */
    public boolean awakenNow(net.minecraft.server.level.ServerLevel serverLevel) {
        return awakenNowInner(serverLevel);
    }

    /**
     * Is `p` in its sight - nothing between its head and his eyes but its own body, and what can be seen through
     *?
     */
    private boolean sees(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.player.Player p) {
        net.minecraft.world.phys.Vec3 head = new net.minecraft.world.phys.Vec3(worldPosition.getX() + 0.5D,
                worldPosition.getY() + Math.min(1.5D, 1.2D * scale), worldPosition.getZ() + 0.5D);
        net.minecraft.world.phys.Vec3 from = p.getEyePosition();
        net.minecraft.world.phys.Vec3 dir = head.subtract(from).normalize();
        for (int pass = 0; pass < 6; pass++) {
            net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(
                    from, head, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, p));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(worldPosition)
                    || owner(level, hit.getBlockPos()) == this) {
                return true;
            }
            if (!seenThrough(level.getBlockState(hit.getBlockPos()))) {
                return false;
            }
            // on, past the bars (out of their cell)
            BlockPos cell = hit.getBlockPos();
            net.minecraft.world.phys.Vec3 at = hit.getLocation();
            for (int k = 0; k < 20 && BlockPos.containing(at).equals(cell); k++) {
                at = at.add(dir.scale(0.1D));
            }
            from = at;
        }
        return false;
    }

    /** Bars, grilles, chains, fences, panes and glass: in the way of a body, not of an eye. */
    private static boolean seenThrough(net.minecraft.world.level.block.state.BlockState s) {
        net.minecraft.world.level.block.Block b = s.getBlock();
        return b instanceof net.minecraft.world.level.block.IronBarsBlock || b instanceof net.minecraft.world.level.block.ChainBlock
                || b instanceof net.minecraft.world.level.block.FenceBlock || b instanceof net.minecraft.world.level.block.FenceGateBlock
                || b instanceof net.minecraft.world.level.block.AbstractGlassBlock
                || net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(b).getPath().contains("bars");
    }

    /** A creature in it that answers nobody's coming, only a call (the treasury's guards: AmbushBlockEntity). */
    public boolean answersOnlyACall() {
        return !awaken.isEmpty() && radius <= 0 && !isRemains() && !isDrift() && !isRemoved();
    }

    private boolean awakenNowInner(net.minecraft.server.level.ServerLevel serverLevel) {
        if (awaken.isEmpty() || isRemoved()) {
            return false;
        }
        shatter(serverLevel, true);
        return true;
    }

    /** The statue a cell of a figure belongs to: through its core, to the block at its feet. */
    @javax.annotation.Nullable
    public static CitadelStatueBlockEntity owner(Level level, BlockPos from) {
        if (level.getBlockEntity(from) instanceof CitadelStatueBlockEntity s) {
            return s;
        }
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        queue.add(from);
        seen.add(from);
        int n = 0;
        while (!queue.isEmpty() && n++ < 1500) {
            BlockPos p = queue.poll();
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos q = p.relative(d);
                if (!seen.add(q)) {
                    continue;
                }
                net.minecraft.world.level.block.state.BlockState st = level.getBlockState(q);
                if (st.is(com.jastkub.frozenfortress.registry.FFBlocks.CITADEL_STATUE.get())
                        && level.getBlockEntity(q) instanceof CitadelStatueBlockEntity s) {
                    return s;
                }
                if (st.is(com.jastkub.frozenfortress.registry.FFBlocks.STATUE_CORE.get())) {
                    queue.add(q);
                }
            }
        }
        // A LIMB APART (05.10.2026: a raised arm, a weapon - its cores do not touch the body's, and a blow
        // on it found no statue): the nearest statue whose figure reaches this far
        CitadelStatueBlockEntity best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.betweenClosed(from.offset(-8, -12, -8), from.offset(8, 2, 8))) {
            if (level.getBlockEntity(q) instanceof CitadelStatueBlockEntity s
                    && s.getRenderBoundingBox().inflate(0.5D).contains(net.minecraft.world.phys.Vec3.atCenterOf(from))) {
                double d = q.distSqr(from);
                if (d < bd) {
                    bd = d;
                    best = s;
                }
            }
        }
        return best;
    }

    /** The ice lets go: its body comes down - and, if release, what was in it steps out. */
    private void shatter(net.minecraft.server.level.ServerLevel serverLevel, boolean release) {
        if (isRemains()) {
            getUp(serverLevel, release);
            return;
        }
        net.minecraft.world.level.block.state.BlockState ice = net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        queue.add(worldPosition);
        int n = 0;
        while (!queue.isEmpty() && n < 900) {
            BlockPos p = queue.poll();
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos q = p.relative(d);
                if (seen.add(q) && serverLevel.getBlockState(q).is(com.jastkub.frozenfortress.registry.FFBlocks.STATUE_CORE.get())) {
                    n++;
                    serverLevel.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                    queue.add(q);
                }
            }
        }
        BlockPos at = worldPosition;
        String id = awaken;
        float facing = yaw;
        serverLevel.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        if (isDrift()) {
            burstDrift(serverLevel, at, id, facing, release);
            return;
        }
        serverLevel.playSound(null, at, net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.6F);
        // THE BREAK, and only the break: the shell bursts - a spray of ice, and its
        // splinters flying off, hurting and frostbiting whoever they hit
        serverLevel.sendParticles(FFParticles.ICE_SHARD.get(), at.getX() + 0.5D, at.getY() + 1.5D * scale, at.getZ() + 0.5D,
                120, 0.8D * scale, 1.4D * scale, 0.8D * scale, 0.25D);
        if (!release) {
            return;
        }
        splinters(serverLevel, at, scale, 10 + (int) (scale * 4), 3.0F);
        burstCell(serverLevel, at);
        net.minecraft.world.entity.EntityType.byString(id).ifPresent(type -> {
            net.minecraft.world.entity.Entity e = type.create(serverLevel);
            if (e instanceof net.minecraft.world.entity.Mob mob) {
                mob.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, facing, 0.0F);
                mob.setYBodyRot(facing);
                mob.setYHeadRot(facing);
                // TRIGGERED, not STRUCTURE: GarrisonSpawnControl caps STRUCTURE spawns at three of the garrison a room - meant
                // for the game topping the halls up, it was quietly cancelling the creature of every statue that
                // woke where three already stood. What a statue holds always comes out of it, and stays.
                net.minecraftforge.event.ForgeEventFactory.onFinalizeSpawn(mob, serverLevel,
                        serverLevel.getCurrentDifficultyAt(at), net.minecraft.world.entity.MobSpawnType.TRIGGERED, null, null);
                mob.setSpawnCancelled(false);
                mob.setPersistenceRequired();
                serverLevel.addFreshEntityWithPassengers(mob);        // (and its rider, if one came with it)
            }
        });
    }

    /** A drift bursts: snow thrown up, and the frostmaw under it leaps out at the nearest of them. */
    private void burstDrift(net.minecraft.server.level.ServerLevel serverLevel, BlockPos at, String id, float facing,
                            boolean release) {
        serverLevel.playSound(null, at, net.minecraft.sounds.SoundEvents.SNOW_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.7F);
        // the drift thrown off: clods of it flying (a thing with a body - AttackFxEntity), a little powder with them
        com.jastkub.frozenfortress.entity.AttackFxEntity.spawn(serverLevel,
                com.jastkub.frozenfortress.entity.AttackFxEntity.SNOW_BURST, net.minecraft.world.phys.Vec3.atBottomCenterOf(at),
                facing, 1.2F, 20, null);
        serverLevel.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.SNOW_BLOCK.defaultBlockState()),
                at.getX() + 0.5D, at.getY() + 0.6D, at.getZ() + 0.5D, 30, 0.9D, 0.5D, 0.9D, 0.15D);
        if (!release) {
            return;
        }
        net.minecraft.world.entity.EntityType.byString(id).ifPresent(type -> {
            net.minecraft.world.entity.Entity e = type.create(serverLevel);
            if (e instanceof net.minecraft.world.entity.Mob mob) {
                mob.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, facing, 0.0F);
                mob.setYBodyRot(facing);
                mob.setYHeadRot(facing);
                net.minecraftforge.event.ForgeEventFactory.onFinalizeSpawn(mob, serverLevel,
                        serverLevel.getCurrentDifficultyAt(at), net.minecraft.world.entity.MobSpawnType.TRIGGERED, null, null);
                mob.setSpawnCancelled(false);
                mob.setPersistenceRequired();
                net.minecraft.world.entity.player.Player p = serverLevel.getNearestPlayer(mob, 24.0D);
                if (p != null && !p.isCreative() && !p.isSpectator()) {
                    net.minecraft.world.phys.Vec3 to = p.position().subtract(mob.position());
                    double len = Math.max(0.001D, to.horizontalDistance());
                    mob.setDeltaMovement(to.x / len * 0.55D, 0.42D, to.z / len * 0.55D);
                    float face = (float) (net.minecraft.util.Mth.atan2(to.z, to.x) * (180.0D / Math.PI)) - 90.0F;
                    mob.setYRot(face);
                    mob.setYBodyRot(face);
                    mob.setYHeadRot(face);
                    mob.setTarget(p);
                }
                serverLevel.addFreshEntityWithPassengers(mob);
            }
        });
    }

    /**
     * One of the dead gets up (Frost Skeleton): not out of
     * a shell of ice but out of its own pose, the creature's clip starting on
     * the statue's very frame. Its band claws up out of the floor round it, one
     * or two more, and the dead lying near hear them and stir in their turn.
     * Broken up first (release false), it is only bones scattering.
     */
    private void getUp(net.minecraft.server.level.ServerLevel serverLevel, boolean release) {
        BlockPos at = worldPosition;
        String id = awaken;
        float facing = yaw;
        int how = java.util.Arrays.asList(com.jastkub.frozenfortress.entity.FrostSkeletonEntity.RISES).indexOf(pose);
        serverLevel.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        net.minecraft.core.particles.ItemParticleOption bone = new net.minecraft.core.particles.ItemParticleOption(
                net.minecraft.core.particles.ParticleTypes.ITEM, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE));
        if (!release) {
            serverLevel.playSound(null, at, com.jastkub.frozenfortress.registry.FFSounds.FROST_SKELETON_COLLAPSE.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            return;
        }
        if (!"frozen_dominion:frost_skeleton".equals(id)) {
            net.minecraft.world.entity.EntityType.byString(id).ifPresent(type -> {
                net.minecraft.world.entity.Entity e = type.create(serverLevel);
                if (e != null) {
                    e.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, facing, 0.0F);
                    serverLevel.addFreshEntity(e);
                }
            });
            return;
        }
        com.jastkub.frozenfortress.entity.FrostSkeletonEntity.rise(serverLevel, at.getX() + 0.5D, at.getY(),
                at.getZ() + 0.5D, facing, Math.max(0, how), variant);
        // ONE OF THE DEAD, ONE SKELETON: the band out of the floor is gone; how many of the dead get up
        // is the citadel's own two in three (WAKE_CHANCE), nothing more
        net.minecraft.util.RandomSource r = serverLevel.random;
        // and the dead lying near hear it (those of them that get up at all)
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-10, -3, -10), at.offset(10, 3, 10))) {
            if (serverLevel.getBlockEntity(p) instanceof CitadelStatueBlockEntity other && other.isRemains()) {
                other.stir(30 + r.nextInt(60));
            }
        }
    }

    /** Splinters of a statue's shell flying off as it breaks (IceFragmentEntity, as shrapnel). */
    public static void splinters(net.minecraft.server.level.ServerLevel level, BlockPos at, float scale, int n, float damage) {
        net.minecraft.util.RandomSource r = level.random;
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2.0D;
            double out = 0.35D + r.nextDouble() * 0.35D;
            net.minecraft.world.phys.Vec3 from = new net.minecraft.world.phys.Vec3(at.getX() + 0.5D,
                    at.getY() + (0.6D + r.nextDouble() * 2.4D) * scale, at.getZ() + 0.5D);
            level.addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.IceBombEntity.IceFragmentEntity(level, null,
                    from, new net.minecraft.world.phys.Vec3(Math.cos(a) * out, 0.2D + r.nextDouble() * 0.35D,
                    Math.sin(a) * out)).shrapnel(damage));
        }
    }

    /**
     * A statue that stood in a cell: what steps out of it bursts the cell's
     * bars. The air
     * around the statue is walked out from its feet; if it is a small closed
     * space - a cell, a niche behind a grate - every bar on its edge goes. In
     * an open hall the walk runs out of room first, and nothing is touched.
     */
    private static void burstCell(net.minecraft.server.level.ServerLevel level, BlockPos at) {
        final int limit = 140;                                   // a cell of the prisons is ~80 cells of air
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.List<BlockPos> bars = new java.util.ArrayList<>();
        queue.add(at);
        seen.add(at);
        int open = 0;
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            if (++open > limit) {
                return;                                          // no cell: an open room
            }
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos q = p.relative(d);
                if (Math.abs(q.getY() - at.getY()) > 4 || !seen.add(q)) {
                    continue;
                }
                net.minecraft.world.level.block.state.BlockState st = level.getBlockState(q);
                if (st.is(net.minecraft.world.level.block.Blocks.IRON_BARS)) {
                    bars.add(q);
                } else if (st.isAir() || !st.blocksMotion()) {
                    queue.add(q);
                }
            }
        }
        net.minecraft.world.level.block.state.BlockState iron = net.minecraft.world.level.block.Blocks.IRON_BARS.defaultBlockState();
        for (BlockPos b : bars) {
            level.setBlock(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        if (!bars.isEmpty()) {
            level.playSound(null, at, net.minecraft.sounds.SoundEvents.CHAIN_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.6F);
            level.playSound(null, at, net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.7F);
        }
    }

    /** Culled against the figure, not against the one block at its feet. */
    @Override
    public AABB getRenderBoundingBox() {
        double w = 6.0D * scale;
        return new AABB(worldPosition).inflate(w, 0.0D, w).expandTowards(0.0D, 8.0D * scale, 0.0D);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "pose", 0,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop("animation." + model + "." + pose))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Model")) model = tag.getString("Model");
        variant = tag.getInt("Variant");
        if (tag.contains("Texture")) texture = tag.getString("Texture");
        if (tag.contains("Pose")) pose = tag.getString("Pose");
        if (tag.contains("Scale")) scale = tag.getFloat("Scale");
        if (tag.contains("Yaw")) yaw = tag.getFloat("Yaw");
        hidden.clear();
        for (Tag t : tag.getList("Hidden", Tag.TAG_STRING)) {
            hidden.add(t.getAsString());
        }
        awaken = tag.getString("Awaken");
        chance = tag.getFloat("Chance");
        if (tag.contains("Radius")) radius = tag.getInt("Radius");
        breaking = tag.contains("Breaking") ? tag.getInt("Breaking") : -1;
        near = tag.getInt("Near");
        wait = tag.getInt("Wait");
        hits = tag.getInt("Hits");
        warned = tag.getBoolean("Warned");
        // placed with a turned structure (the watchtower, the camp): the figure turns with its cores
        if (!tag.getBoolean("Turned")) {
            yaw += 90.0F * com.jastkub.frozenfortress.block.TemplateTurn.quarters(getBlockState());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Model", model);
        tag.putInt("Variant", variant);
        tag.putString("Texture", texture);
        tag.putString("Pose", pose);
        tag.putFloat("Scale", scale);
        tag.putFloat("Yaw", yaw);
        ListTag h = new ListTag();
        for (String b : hidden) {
            h.add(StringTag.valueOf(b));
        }
        tag.put("Hidden", h);
        tag.putString("Awaken", awaken);
        tag.putFloat("Chance", chance);
        tag.putInt("Radius", radius);
        tag.putInt("Near", near);
        tag.putInt("Wait", wait);
        tag.putInt("Hits", hits);
        tag.putInt("Breaking", breaking);
        tag.putBoolean("Warned", warned);
        tag.putBoolean("Turned", true);
    }

    /** The client needs all of it to draw anything at all. */
    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
