package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.block.CitadelDoorBlock;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFBlocks;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A door of the citadel: two leaves, the lock on their seam, keys it asks
 * for. Put every key in and it opens - the key turns, the bar lifts, the
 * leaves swing in on their hinges - and it stays open for good.
 *
 * <p>What stops you while it is shut is a plane of {@code door_barrier}
 * blocks across the opening (BarrierBox, from this block); they go when the
 * leaves have swung.
 */
public class CitadelDoorBlockEntity extends BlockEntity implements GeoBlockEntity {

    public static final int CLOSED = 0, UNLOCKING = 1, OPENING = 2, OPEN = 3;
    /** Key in, turned, bar up. */
    private static final int UNLOCK_TICKS = 34;
    /** Then the swing. */
    private static final int SWING_TICKS = 46;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private String model = "door_6x6";
    /** The face it wears (textures/block/door/<model>_<theme>.png); empty for the plain one. */
    private String theme = "";
    private Direction facing = Direction.NORTH;
    private float shift;
    private final List<ResourceLocation> keys = new ArrayList<>();
    private final Set<ResourceLocation> inserted = new LinkedHashSet<>();
    private int[] barrierBox = {0, 0, 0, 0, 0, 0};
    private int state = CLOSED;
    private int clock;

    public CitadelDoorBlockEntity(BlockPos pos, BlockState blockState) {
        super(FFBlockEntities.CITADEL_DOOR.get(), pos, blockState);
    }

    public String model() {
        return model;
    }

    public String theme() {
        return theme;
    }

    public Direction facing() {
        return facing;
    }

    public float shift() {
        return shift;
    }

    public int doorState() {
        return state;
    }

    /** Does this door's opening hold that cell? (a barrier asks who it belongs to) */
    public boolean covers(BlockPos p) {
        BlockPos a = worldPosition.offset(barrierBox[0], barrierBox[1], barrierBox[2]);
        BlockPos b = worldPosition.offset(barrierBox[3], barrierBox[4], barrierBox[5]);
        return p.getX() >= Math.min(a.getX(), b.getX()) && p.getX() <= Math.max(a.getX(), b.getX())
                && p.getY() >= Math.min(a.getY(), b.getY()) && p.getY() <= Math.max(a.getY(), b.getY())
                && p.getZ() >= Math.min(a.getZ(), b.getZ()) && p.getZ() <= Math.max(a.getZ(), b.getZ());
    }

    public void tryUse(Player player, ItemStack held) {
        if (!(level instanceof ServerLevel serverLevel) || state != CLOSED) {
            return;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (id != null && keys.contains(id) && !inserted.contains(id)) {
            inserted.add(id);
            setChanged();
            if (!player.getAbilities().instabuild) {
                held.shrink(1);                                 // the key stays in its lock
            }
            serverLevel.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.6F, 0.5F);
            if (inserted.containsAll(keys)) {
                begin(serverLevel);
            } else {
                player.displayClientMessage(Component.translatable("block.frozen_dominion.citadel_lock.fits",
                        inserted.size(), keys.size()), true);
            }
            return;
        }
        MutableComponent missing = Component.empty();
        boolean first = true;
        for (ResourceLocation key : keys) {
            if (inserted.contains(key)) {
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(key);
            if (!first) {
                missing.append(", ");
            }
            missing.append(item != null ? item.getDescription() : Component.literal(key.toString()));
            first = false;
        }
        player.displayClientMessage(Component.translatable("block.frozen_dominion.citadel_lock.locked", missing), true);
    }

    private void begin(ServerLevel serverLevel) {
        state = UNLOCKING;
        clock = 0;
        sync();
        serverLevel.playSound(null, worldPosition, FFSounds.VELKHAR_SWORD_PULL.get(), SoundSource.BLOCKS, 2.2F, 0.45F);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState bs, CitadelDoorBlockEntity door) {
        if (!(level instanceof ServerLevel serverLevel) || (door.state != UNLOCKING && door.state != OPENING)) {
            return;
        }
        door.clock++;
        if (door.state == UNLOCKING) {
            if (door.clock == 12) {
                serverLevel.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2.0F, 0.5F);
            }
            if (door.clock == 24) {
                serverLevel.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 2.0F, 0.6F);
            }
            if (door.clock >= UNLOCK_TICKS) {
                door.state = OPENING;
                door.clock = 0;
                door.sync();
                serverLevel.playSound(null, pos, FFSounds.ICE_GRIND.get(), SoundSource.BLOCKS, 2.6F, 0.5F);
            }
            return;
        }
        if (door.clock % 9 == 0) {
            serverLevel.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.2F, 0.4F);
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(), pos.getX() + 0.5D, pos.getY() + 2.0D,
                    pos.getZ() + 0.5D, 8, 1.5D, 1.5D, 1.5D, 0.05D);
        }
        if (door.clock >= SWING_TICKS) {
            door.finish(serverLevel);
        }
    }

    /** The leaves are against the walls: the way is open, for good. */
    private void finish(ServerLevel serverLevel) {
        state = OPEN;
        BlockPos a = worldPosition.offset(barrierBox[0], barrierBox[1], barrierBox[2]);
        BlockPos b = worldPosition.offset(barrierBox[3], barrierBox[4], barrierBox[5]);
        for (BlockPos p : BlockPos.betweenClosed(a, b)) {
            if (serverLevel.getBlockState(p).is(FFBlocks.DOOR_BARRIER.get())) {
                serverLevel.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        BlockState own = serverLevel.getBlockState(worldPosition);
        if (own.is(FFBlocks.CITADEL_DOOR.get())) {
            serverLevel.setBlock(worldPosition, own.setValue(CitadelDoorBlock.OPEN, true), 3);
        }
        sync();
        serverLevel.playSound(null, worldPosition, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2.4F, 0.45F);
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(9.0D, 0.0D, 9.0D).expandTowards(0.0D, 16.0D, 0.0D);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "door", 0, s -> {
            String base = "animation." + model + ".";
            return switch (state) {
                case UNLOCKING, OPENING -> s.setAndContinue(RawAnimation.begin().thenPlayAndHold(base + "opening"));
                case OPEN -> s.setAndContinue(RawAnimation.begin().thenLoop(base + "open"));
                default -> s.setAndContinue(RawAnimation.begin().thenLoop(base + "closed"));
            };
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Model")) model = tag.getString("Model");
        theme = tag.getString("Theme");
        if (tag.contains("Facing")) {
            Direction d = Direction.byName(tag.getString("Facing"));
            facing = d != null ? d : Direction.NORTH;
        }
        shift = tag.getFloat("Shift");
        keys.clear();
        for (Tag t : tag.getList("Keys", Tag.TAG_STRING)) {
            ResourceLocation rl = ResourceLocation.tryParse(t.getAsString());
            if (rl != null) keys.add(rl);
        }
        inserted.clear();
        for (Tag t : tag.getList("Inserted", Tag.TAG_STRING)) {
            ResourceLocation rl = ResourceLocation.tryParse(t.getAsString());
            if (rl != null) inserted.add(rl);
        }
        if (tag.contains("BarrierBox")) barrierBox = tag.getIntArray("BarrierBox");
        state = tag.getInt("State");
        clock = tag.getInt("Clock");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Model", model);
        tag.putString("Theme", theme);
        tag.putString("Facing", facing.getName());
        tag.putFloat("Shift", shift);
        ListTag k = new ListTag();
        for (ResourceLocation rl : keys) k.add(StringTag.valueOf(rl.toString()));
        tag.put("Keys", k);
        ListTag in = new ListTag();
        for (ResourceLocation rl : inserted) in.add(StringTag.valueOf(rl.toString()));
        tag.put("Inserted", in);
        tag.putIntArray("BarrierBox", barrierBox);
        tag.putInt("State", state);
        tag.putInt("Clock", clock);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
