package com.jastkub.frozenfortress.block.entity;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import com.jastkub.frozenfortress.entity.FrostServantEntity;
import com.jastkub.frozenfortress.integration.curios.CuriosHooks;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFBlocks;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFItems;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The Stormcrown, burning over the citadel - and, once the Crownbreaker is
 * driven into it, burning for whoever drove it.
 *
 * <p>HOSTILE, its blessing is the court's: every servant within reach fights
 * harder (Strength, Speed, Resistance), the stone refuses the pick (Mining
 * Fatigue), and its CHILL lies on every player without the Hearth Amulet -
 * frostbite and a bite every few seconds that nothing but the amulet keeps off.
 *
 * <p>TAKEN, its beam turns from glacier blue to hearth gold, the servants lose
 * it, the Storm Seal breaks, the Chill and the fatigue lift - and it pulses
 * Strength and Resistance over every player within reach instead: the whole
 * citadel, the Monstrosity's prison and the throne included.
 *
 * <p>Effects are short and re-applied on a cycle, so they lapse on their own.
 */
public class StormcrownBeaconBlockEntity extends BlockEntity implements GeoBlockEntity {

    /** How far the crown's influence reaches - the whole citadel, surface to prison. */
    // A HUNDRED AND TWENTY-EIGHT: the whole citadel, surface to prison. At
    // sixty-four the fatigue ended short of the Descent Gate, and the stone
    // round the only way down could simply be dug through.
    public static final int RADIUS = 128;

    private static final int PULSE_INTERVAL = 60;
    private static final int EFFECT_DURATION = 160;
    /** How long the crown takes to yield once the wedge is driven in. */
    private static final int BREAK_TICKS = 70;

    /** 0 = untouched; anything higher counts up to BREAK_TICKS, and then it is taken. */
    private int breaking;
    /** Taken by the players: its light is theirs. */
    private boolean captured;
    /**
     * THE STONE HOLDS UNTIL THE KING IS DEAD: a taken crown still keeps its hold on the pick; only
     * Velkhar's death lets it go (kingFell, from VelkharEntity.die).
     */
    private boolean kingFallen;

    /** The crowns loaded on the server, so the king's death can find his own. */
    public static final java.util.Set<StormcrownBeaconBlockEntity> SERVER =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            SERVER.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        SERVER.remove(this);
    }

    /** The king of the citadel round `at` is dead: every crown of it lets the stone go, and says so to whoever is in it. */
    public static void kingFell(ServerLevel level, net.minecraft.world.phys.Vec3 at) {
        for (StormcrownBeaconBlockEntity b : new java.util.ArrayList<>(SERVER)) {
            if (b.isRemoved() || b.level != level || b.kingFallen) {
                continue;
            }
            double dx = b.worldPosition.getX() + 0.5D - at.x;
            double dz = b.worldPosition.getZ() + 0.5D - at.z;
            if (dx * dx + dz * dz > 256.0D * 256.0D) {
                continue;
            }
            b.kingFallen = true;
            b.setChanged();
            for (Player player : level.getEntitiesOfClass(Player.class, new AABB(b.worldPosition).inflate(RADIUS))) {
                if (player.hasEffect(FFEffects.CROWN_HOLD)) {
                    player.removeEffect(FFEffects.CROWN_HOLD);
                    player.displayClientMessage(Component.translatable(
                            "block.frozen_dominion.stormcrown_beacon.king_fallen"), true);
                }
            }
        }
    }

    /** ITS BODY: the crown turning, the crystal held up by the claws - shaking while the wedge is
     *  in it, slower and gold once it is taken (tools/gen_nest_beacon.py; the gold is its texture, StormcrownBeaconRenderer). */
    private static final RawAnimation CROWN_IDLE = RawAnimation.begin().thenLoop("animation.stormcrown.idle");
    private static final RawAnimation CROWN_BREAK = RawAnimation.begin().thenLoop("animation.stormcrown.breaking");
    private static final RawAnimation CROWN_TAKEN = RawAnimation.begin().thenLoop("animation.stormcrown.taken");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public StormcrownBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(FFBlockEntities.STORMCROWN_BEACON.get(), pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "crown", 6, s -> s.setAndContinue(
                isCaptured() ? CROWN_TAKEN : isBreaking() ? CROWN_BREAK : CROWN_IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public boolean isBreaking() {
        return breaking > 0;
    }

    public boolean isCaptured() {
        return captured;
    }

    public void beginBreaking() {
        if (breaking == 0 && !captured) {
            breaking = 1;
            sync();
        }
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** The crown yielding: fractures through it, its light stuttering, then it turns. */
    private static boolean tickBreaking(Level level, BlockPos pos, StormcrownBeaconBlockEntity beacon) {
        if (beacon.breaking == 0) {
            return false;
        }
        beacon.breaking++;
        float progress = (float) beacon.breaking / BREAK_TICKS;
        if (level instanceof ServerLevel serverLevel) {
            int count = 2 + (int) (progress * 10.0F);
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    count, 0.45D, 0.45D, 0.45D, 0.12D + progress * 0.2D);
            if (beacon.breaking % 10 == 0) {
                serverLevel.playSound(null, pos, FFSounds.ICE_CRACK.get(), SoundSource.BLOCKS,
                        1.2F, 0.6F + progress * 0.8F);
                serverLevel.sendParticles(FFParticles.SOUL_FROST.get(),
                        pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D,
                        14, 0.5D, 0.6D, 0.5D, 0.08D);
            }
            if (beacon.breaking >= BREAK_TICKS) {
                beacon.take(serverLevel);
            }
        }
        return true;
    }

    /** It is theirs now. */
    private void take(ServerLevel serverLevel) {
        BlockPos pos = worldPosition;
        breaking = 0;
        captured = true;
        sync();
        serverLevel.playSound(null, pos, FFSounds.SHOCKWAVE.get(), SoundSource.BLOCKS, 2.4F, 0.7F);
        serverLevel.playSound(null, pos, FFSounds.VELKHAR_WHISPER.get(), SoundSource.BLOCKS, 2.0F, 0.7F);
        serverLevel.playSound(null, pos, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3.0F, 0.8F);
        serverLevel.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                80, 0.8D, 1.6D, 0.8D, 0.06D);
        AABB range = new AABB(pos).inflate(RADIUS);
        // the storm goes out of the court at once, and the cold off the players
        for (FrostServantEntity servant : serverLevel.getEntitiesOfClass(FrostServantEntity.class, range)) {
            servant.removeEffect(MobEffects.DAMAGE_BOOST);
            servant.removeEffect(MobEffects.MOVEMENT_SPEED);
            servant.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        }
        for (Player player : serverLevel.getEntitiesOfClass(Player.class, range)) {
            player.removeEffect(FFEffects.CHILL);
            player.removeEffect(MobEffects.DIG_SLOWDOWN);              // (a crown of an older build laid this)
            // (the Crown's Hold stays: it goes with the king - see kingFallen)
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D) < 48.0D * 48.0D) {
                player.displayClientMessage(Component.translatable("block.frozen_dominion.stormcrown_beacon.taken"), true);
            }
        }
        breakStormSeal(serverLevel, pos);
        com.jastkub.frozenfortress.FFAdvancements.grantNearby(serverLevel, pos, 48.0D, "beacon", "shattered");
        // AND IT IS SEEN: the crown
        // taken, the beam, then out under the sky as the storm over the citadel unwinds (BossScenes: stormcrown_taken)
        net.minecraft.world.phys.Vec3 here = net.minecraft.world.phys.Vec3.atBottomCenterOf(pos);
        com.jastkub.frozenfortress.BossCutscenes.placeScene(serverLevel, "stormcrown_taken", here, 0.0F,
                p -> p.distanceToSqr(here) < 48.0D * 48.0D);
    }

    /** THE STORM SEAL BREAKS: every block of seal ice the crown held goes - the way to the Depths opens. */
    public static void breakStormSeal(ServerLevel serverLevel, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-64, -24, -64), pos.offset(64, 24, 64))) {
            if (serverLevel.getBlockState(p).is(FFBlocks.SEALED_SOVEREIGN_ICE.get())) {
                serverLevel.levelEvent(2001, p, Block.getId(serverLevel.getBlockState(p)));
                serverLevel.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  StormcrownBeaconBlockEntity beacon) {
        // A crown in the middle of yielding blesses no one.
        if (tickBreaking(level, pos, beacon)) {
            return;
        }
        if (level.getGameTime() % PULSE_INTERVAL != 0 || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        AABB range = new AABB(pos).inflate(RADIUS);
        List<Player> players = level.getEntitiesOfClass(Player.class, range,
                p -> !p.isCreative() && !p.isSpectator() && p.isAlive());

        if (beacon.captured) {
            // its light is the players' now, wherever in the citadel they fight
            for (Player player : players) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, EFFECT_DURATION, 0, true, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, EFFECT_DURATION, 0, true, true, true));
                if (!beacon.kingFallen) {
                    player.addEffect(new MobEffectInstance(FFEffects.CROWN_HOLD,
                            EFFECT_DURATION + PULSE_INTERVAL, 0, true, false, false));
                }
            }
            return;
        }

        for (Player player : players) {
            // The crown is what makes the stone refuse the pick: break its hold and you dig again. (Its own hold, hidden -
            // not Mining Fatigue, which slowed the sword as much as the pick: 08.10.2026)
            player.addEffect(new MobEffectInstance(FFEffects.CROWN_HOLD,
                    EFFECT_DURATION + PULSE_INTERVAL, 0, true, false, false));
            // ...and its Chill, unless a hearth's coal is at the throat
            if (CuriosHooks.isEquipped(player, FFItems.HEARTH_AMULET.get())) {
                serverLevel.sendParticles(ParticleTypes.SMALL_FLAME, player.getX(), player.getY() + 1.3D, player.getZ(),
                        3, 0.2D, 0.15D, 0.2D, 0.005D);
                player.removeEffect(FFEffects.CHILL);
            } else {
                player.addEffect(new MobEffectInstance(FFEffects.CHILL, EFFECT_DURATION, 0, false, true, true));
                noteTheChill(player);
            }
        }

        List<FrostServantEntity> court = level.getEntitiesOfClass(FrostServantEntity.class, range,
                FrostServantEntity::isAlive);
        if (court.isEmpty()) {
            return;
        }
        for (FrostServantEntity servant : court) {
            servant.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, EFFECT_DURATION, 0, true, false, true));
            servant.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_DURATION, 0, true, false, true));
            servant.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, EFFECT_DURATION, 0, true, false, true));
        }
        level.playSound(null, pos, FFSounds.BLIZZARD_LOOP.get(), SoundSource.BLOCKS, 0.6F, 1.4F);
    }

    /** THE JOURNAL: the first time the Chill bites, a line of the expedition's
     *  journal says what it is and where the answer lies. Once per player. */
    private static void noteTheChill(Player player) {
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean("frozen_dominion_chill_noted")) {
            return;
        }
        data.putBoolean("frozen_dominion_chill_noted", true);
        player.sendSystemMessage(Component.translatable("journal.frozen_dominion.chill"));
    }

    // ---- the client's memory of the crown it last saw (client.CitadelMist: its frost mist hangs in its reach while it
    //      burns) - kept when its chunk drops out of view, so the mist does not lift with it
    public static volatile BlockPos clientSeenAt;
    public static volatile boolean clientSeenTaken;
    /** ...and whether the wedge is in it right now (client.StormcrownSpiral: the storm over it thrashes). */
    public static volatile boolean clientSeenBreaking;
    public static volatile net.minecraft.resources.ResourceKey<Level> clientSeenIn;

    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                  StormcrownBeaconBlockEntity beacon) {
        clientSeenAt = pos.immutable();
        clientSeenTaken = beacon.captured;
        clientSeenBreaking = beacon.breaking > 0 && !beacon.captured;
        clientSeenIn = level.dimension();
        // snow spiralling up the beam - or, once it is taken, embers
        if (level.random.nextInt(2) == 0) {
            double angle = level.getGameTime() * 0.18D + level.random.nextDouble();
            double radius = 0.6D + level.random.nextDouble() * 0.5D;
            level.addParticle(beacon.captured ? ParticleTypes.SMALL_FLAME : FFParticles.BLIZZARD_FLAKE.get(),
                    pos.getX() + 0.5D + Math.cos(angle) * radius,
                    pos.getY() + 1.0D + level.random.nextDouble() * 6.0D,
                    pos.getZ() + 0.5D + Math.sin(angle) * radius,
                    -Math.sin(angle) * 0.05D, 0.08D, Math.cos(angle) * 0.05D);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Breaking", breaking);
        tag.putBoolean("Captured", captured);
        tag.putBoolean("KingFallen", kingFallen);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        breaking = tag.getInt("Breaking");
        captured = tag.getBoolean("Captured");
        kingFallen = tag.getBoolean("KingFallen");
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The beam is drawn from here up to the sky, so never cull it. */
    /** (1.21) the renderer asks for this: its getRenderBoundingBox(be). */
    public AABB renderBox() {
        return new AABB(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                worldPosition.getX() + 1, worldPosition.getY() + 320, worldPosition.getZ() + 1);
    }
}
