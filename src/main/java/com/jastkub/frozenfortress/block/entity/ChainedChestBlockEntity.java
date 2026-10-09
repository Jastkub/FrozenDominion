package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFItems;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The coffer's lock and what it keeps. Its LootTable is written in by the
 * structure; a Shackle Key turns the lock (and breaks in it), the chains come
 * off in an animation, and the block becomes a vanilla chest carrying that
 * table - so what is inside is rolled the moment someone opens it, like any
 * chest of the citadel.
 */
public class ChainedChestBlockEntity extends BlockEntity implements GeoBlockEntity {

    public static final int LOCKED = 0, UNCHAINING = 1;
    /** As long as the "unchain" animation: the lozenge, the padlock, the chains. */
    private static final int UNCHAIN_TICKS = 28;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.chained_chest.idle");
    private static final RawAnimation UNCHAIN = RawAnimation.begin().thenPlayAndHold("animation.chained_chest.unchain");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private ResourceLocation lootTable;
    private long lootSeed;
    /**
     * A coffer GUARDED as well as locked (the watchtower's, with the Crownbreaker):
     * the creature that holds its chains. No key turns them while it lives; once there is none of
     * it left near, it is a chained coffer like any other - the key is on its keeper.
     */
    private String keeper = "";
    /** How far round it the keeper is looked for. */
    private static final double KEEPER_REACH = 24.0D;
    private int state = LOCKED;
    private int clock;
    /** Ticks until the chains come off by themselves (releaseAfter), or 0 for a coffer that waits for its key. */
    private int releaseIn;
    /** What it holds itself, not a table to roll (releaseWith): put in the chest it becomes. */
    private final java.util.List<ItemStack> kept = new java.util.ArrayList<>();

    public ChainedChestBlockEntity(BlockPos pos, BlockState blockState) {
        super(FFBlockEntities.CHAINED_CHEST.get(), pos, blockState);
    }

    public void tryUnlock(Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel) || state != LOCKED) {
            return;
        }
        if (!keeper.isEmpty() && keeperNear(serverLevel)) {
            player.displayClientMessage(Component.translatable("block.frozen_dominion.chained_chest.guarded"), true);
            serverLevel.playSound(null, worldPosition, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 1.0F, 0.6F);
            return;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(FFItems.SHACKLE_KEY.get())) {
            player.displayClientMessage(Component.translatable("block.frozen_dominion.chained_chest.locked"), true);
            serverLevel.playSound(null, worldPosition, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 1.0F, 0.7F);
            return;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);                                     // the key breaks in the lock
        }
        state = UNCHAINING;
        clock = 0;
        sync();
        player.displayClientMessage(Component.translatable("block.frozen_dominion.chained_chest.unlock"), true);
        serverLevel.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.4F, 0.6F);
    }

    /**
     * A REWARD, NOT A LOCK (Velkhar's hoard): this coffer holds `table` (rolled with `seed` when it
     * is opened) and lets its chains go by themselves `delay` ticks from now - the same animation and sounds as a key,
     * no key and no keeper.
     */
    public void releaseAfter(ResourceLocation table, long seed, int delay) {
        lootTable = table;
        lootSeed = seed;
        keeper = "";
        releaseIn = Math.max(1, delay);
        sync();
    }

    /**
     * A KEEPER'S SPOILS IN CHAINS: this coffer holds these very things (BoneLordSpoils: what he dropped - the Bone Lord's and the
     * Monstrosity's), and it is a coffer like every other: a Shackle Key opens it and breaks in the lock. Its
     * chains used to come off by themselves - after four seconds, then as somebody walked up - and nobody saw them.
     */
    public void releaseWith(java.util.List<ItemStack> items) {
        kept.clear();
        for (ItemStack st : items) {
            if (!st.isEmpty()) {
                kept.add(st.copy());
            }
        }
        lootTable = null;
        keeper = "";
        releaseIn = 0;
        sync();
    }

    private void unchain(ServerLevel serverLevel) {
        state = UNCHAINING;
        clock = 0;
        sync();
        serverLevel.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.4F, 0.6F);
    }

    /** Is its keeper alive, anywhere near? (An unknown keeper - not in this game - is none.) */
    private boolean keeperNear(ServerLevel serverLevel) {
        return net.minecraft.world.entity.EntityType.byString(keeper).map(type -> !serverLevel.getEntities(type,
                new net.minecraft.world.phys.AABB(worldPosition).inflate(KEEPER_REACH),
                e -> e.isAlive()).isEmpty()).orElse(false);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState bs, ChainedChestBlockEntity coffer) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (coffer.state == LOCKED && coffer.releaseIn > 0 && --coffer.releaseIn == 0) {
            coffer.unchain(serverLevel);                // it opens itself (releaseAfter)
        }
        if (coffer.state != UNCHAINING) {
            return;
        }
        coffer.clock++;
        double x = pos.getX() + 0.5D, y = pos.getY() + 0.5D, z = pos.getZ() + 0.5D;
        if (coffer.clock == 4) {
            serverLevel.playSound(null, pos, FFSounds.ICE_SHATTER.get(), SoundSource.BLOCKS, 1.4F, 1.3F);
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(), x, y, z, 14, 0.25D, 0.25D, 0.25D, 0.08D);
        }
        if (coffer.clock == 10) {
            serverLevel.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.6F, 0.7F);
        }
        if (coffer.clock == 16 || coffer.clock == 22) {
            serverLevel.playSound(null, pos, SoundEvents.CHAIN_FALL, SoundSource.BLOCKS, 1.3F, 0.8F);
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CHAIN.defaultBlockState()),
                    x, pos.getY() + 0.2D, z, 16, 0.5D, 0.1D, 0.5D, 0.05D);
        }
        if (coffer.clock >= UNCHAIN_TICKS) {
            coffer.becomeChest(serverLevel);
        }
    }

    /** The chains are off: an ordinary chest stands there now, with the treasure's table in it. */
    private void becomeChest(ServerLevel serverLevel) {
        Direction facing = getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? getBlockState().getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        ResourceLocation table = lootTable;
        long seed = lootSeed;
        BlockPos pos = worldPosition;
        serverLevel.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 3);
        if (table != null && serverLevel.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, table), seed);
        }
        if (!kept.isEmpty() && serverLevel.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            int slot = 0;
            for (ItemStack st : kept) {
                if (slot < chest.getContainerSize()) {
                    chest.setItem(slot++, st);
                } else {                                        // (what the chest cannot hold: beside it)
                    serverLevel.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(serverLevel,
                            pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, st));
                }
            }
            chest.setChanged();
        }
        serverLevel.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0F, 0.8F);
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "chains", 0,
                s -> s.setAndContinue(state == UNCHAINING ? UNCHAIN : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lootTable = tag.contains("LootTable") ? ResourceLocation.tryParse(tag.getString("LootTable")) : null;
        lootSeed = tag.getLong("LootTableSeed");
        state = tag.getInt("State");
        clock = tag.getInt("Clock");
        keeper = tag.getString("Keeper");
        releaseIn = tag.getInt("ReleaseIn");
        kept.clear();
        net.minecraft.nbt.ListTag items = tag.getList("Kept", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            ItemStack st = ItemStack.parseOptional(registries, items.getCompound(i));
            if (!st.isEmpty()) {
                kept.add(st);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (lootTable != null) {
            tag.putString("LootTable", lootTable.toString());
        }
        tag.putLong("LootTableSeed", lootSeed);
        tag.putInt("State", state);
        tag.putInt("Clock", clock);
        if (!keeper.isEmpty()) {
            tag.putString("Keeper", keeper);
        }
        if (releaseIn > 0) {
            tag.putInt("ReleaseIn", releaseIn);
        }
        if (!kept.isEmpty()) {
            net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
            for (ItemStack st : kept) {
                items.add(st.save(registries));
            }
            tag.put("Kept", items);
        }
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
