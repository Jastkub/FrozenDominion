package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.FrostShrineBlock;
import com.jastkub.frozenfortress.entity.boss.StormEyeArena;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A Frost Shrine (FrostShrineBlock): the station before a boss's door - where you come back to life, and where what
 * you carried waits for you when the boss's hall was the death of you.
 *
 * <p>WHAT IT GIVES BACK: who dies in its
 * Room rises before it, everything they carried back on them, each thing in its own slot - armour, the pack, the off
 * hand, every Curios slot (the Hearth Amulet at the throat before the cold can reach them). Their experience is lost
 * as death loses it. Taken off at the moment of death
 * into the player's own persisted record (PENDING), put back as they respawn (onRespawn).
 *
 * <p>ITS CASKET is what is left over: a thing another mod dropped late, a stack that found its slot taken - and
 * what shrines of an older build kept. Opened like a chest: each of the fallen sees only their
 * own, takes out what they want, and what they leave in it stays kept for them. Filled from deaths in its Room - the boss's hall behind the door, a box relative to it from the citadel's
 * generator - and, for the station before the throne hall, from deaths in the Eye of the Storm (the one nearest the
 * king's throne). Everything is taken off the dead at the moment of death, before anything falls, so nothing reaches
 * the floor or a gravestone mod; Curios too.
 *
 * <p>NBT: Room - the boss's hall, relative to this block (far corner exclusive); Facing - the way it faced as the
 * generator wrote it (the template may turn it: see load); Fallen - what it keeps.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.GAME)
public class FrostShrineBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final RawAnimation STIRRING = RawAnimation.begin().thenLoop("animation.frost_shrine.stirring");
    private static final RawAnimation KINDLE = RawAnimation.begin().thenPlay("animation.frost_shrine.kindle")
            .thenLoop("animation.frost_shrine.burning");
    private static final RawAnimation BURNING = RawAnimation.begin().thenLoop("animation.frost_shrine.burning");
    private static final RawAnimation CASKET_EMPTY = RawAnimation.begin().thenLoop("animation.frost_shrine.casket_empty");
    private static final RawAnimation CASKET_FULL = RawAnimation.begin().thenLoop("animation.frost_shrine.casket_full");
    /** A hand laid on it once it burns: it takes you in (FrostShrineBlock.kindle - triggered, its own controller). */
    private static final RawAnimation REMEMBER = RawAnimation.begin().thenPlay("animation.frost_shrine.remember");
    /** How far round the throne to look for the throne hall's station, in chunks. */
    private static final int SEARCH_CHUNKS = 6;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<UUID, List<ItemStack>> kept = new HashMap<>();
    /** The boss's hall, relative to it (far corner exclusive); null - it keeps nothing (one placed by hand). */
    @Nullable
    private int[] room;
    /** Its Room is the whole of the underground, not a keeper's hall (Wide: the station at the head of the descent). */
    private boolean wide;
    /** Its Room already turned to match its template's turn (or there was none to make). */
    private boolean turned;
    /** Client: the stage last drawn, so a shrine seen catching fire is seen catching fire (and one already burning
     *  when it comes into view just burns). */
    private int seenStage = -1;
    /** Client: the clip it burns with - chosen once, as it catches or as it is first seen, and held (the predicate
     *  runs every frame, and a clip handed over anew each time would cut the kindling off after one). */
    private RawAnimation burnClip;

    public FrostShrineBlockEntity(BlockPos pos, BlockState state) {
        super(FFBlockEntities.FROST_SHRINE.get(), pos, state);
    }

    private AABB hall() {
        return new AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(worldPosition.offset(room[0], room[1], room[2])), net.minecraft.world.phys.Vec3.atLowerCornerOf(worldPosition.offset(room[3], room[4], room[5])));
    }

    /**
     * Is a death here this station's? Its hall and two blocks round it - but the station of the underground ("Wide",
     * the whole of it up to the ground floor) only below its top: a death on the ground floor above is nobody's
     * (08.10.2026: a player killed by a Sentinel on the ground floor rose down in the depths).
     */
    private boolean catches(Vec3 at) {
        AABB h = hall();
        AABB zone = wide ? h.inflate(2.0D, 0.0D, 2.0D).setMaxY(h.maxY - 1.0D) : h.inflate(2.0D);
        return zone.contains(at);
    }

    // ------------------------------------------------------------------------------------------------ keeping
    /** Everything of a death's record (its slots, its Curios, the loose) into the casket for `who`. */
    private void keepAll(UUID who, CompoundTag rec) {
        List<ItemStack> all = new ArrayList<>();
        for (String list : new String[]{"Slots", "Curios"}) {
            ListTag l = rec.getList(list, Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                ItemStack st = ItemStack.parseOptional(level.registryAccess(), l.getCompound(i).getCompound("Item"));
                if (!st.isEmpty()) {
                    all.add(st);
                }
            }
        }
        ListTag loose = rec.getList("Loose", Tag.TAG_COMPOUND);
        for (int i = 0; i < loose.size(); i++) {
            ItemStack st = ItemStack.parseOptional(level.registryAccess(), loose.getCompound(i));
            if (!st.isEmpty()) {
                all.add(st);
            }
        }
        keep(who, all);
        setChanged();
    }

    private void keep(UUID who, List<ItemStack> stacks) {
        if (!stacks.isEmpty()) {
            kept.computeIfAbsent(who, k -> new ArrayList<>()).addAll(stacks);
        }
        setChanged();
        showFull();
    }

    /** A hand laid on it: its casket opened on their things. True if there was anything. */
    public boolean giveBack(ServerPlayer p) {
        sweep(p.getUUID());
        List<ItemStack> stacks = kept.get(p.getUUID());
        if (stacks == null) {
            return false;
        }
        {
            int rows = Math.max(1, Math.min(6, (stacks.size() + 8) / 9));
            MenuType<ChestMenu> type = switch (rows) {
                case 1 -> MenuType.GENERIC_9x1;
                case 2 -> MenuType.GENERIC_9x2;
                case 3 -> MenuType.GENERIC_9x3;
                case 4 -> MenuType.GENERIC_9x4;
                case 5 -> MenuType.GENERIC_9x5;
                default -> MenuType.GENERIC_9x6;
            };
            Casket casket = new Casket(this, p.getUUID(), rows * 9);
            p.openMenu(new SimpleMenuProvider((id, inv, who) -> new ChestMenu(type, id, inv, casket, rows),
                    Component.translatable("container.frozen_dominion.frost_shrine")));
        }
        if (stacks != null) {
            p.displayClientMessage(Component.translatable("message.frozen_dominion.shrine_returned"), true);
        }
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.6F, 0.6F);
            level.playSound(null, worldPosition, SoundEvents.ENDER_CHEST_OPEN, SoundSource.BLOCKS, 0.8F, 0.7F);
        }
        return true;
    }

    /** Their list without the places emptied (as the casket closes, and before it opens); none left, no list. */
    private void sweep(UUID who) {
        List<ItemStack> l = kept.get(who);
        if (l != null) {
            l.removeIf(ItemStack::isEmpty);
            if (l.isEmpty()) {
                kept.remove(who);
            }
        }
        setChanged();
        showFull();
    }

    /** The casket in its plinth lit while it keeps anybody's things. */
    private void showFull() {
        if (level == null) {
            return;
        }
        boolean full = kept.values().stream().anyMatch(l -> l.stream().anyMatch(s -> !s.isEmpty()));
        BlockState st = getBlockState();
        if (st.getBlock() instanceof FrostShrineBlock && st.getValue(FrostShrineBlock.FULL) != full) {
            level.setBlock(worldPosition, st.setValue(FrostShrineBlock.FULL, full), Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------------------------------------ the fallen
    /**
     * WHICH STATION THIS DEATH BELONGS TO. In the Eye of the Storm: the one nearest the king's throne (the arena's
     * home) - the station before the throne hall. Anywhere else: the one whose boss's hall (its Room) the dead lay in.
     * Neither: none, and death is as it always was.
     */
    @Nullable
    public static FrostShrineBlockEntity forDeath(ServerLevel s, Vec3 at) {
        StormEyeArena arena = StormEyeArena.at(s, at);
        if (arena != null) {
            return near(s, arena.home);
        }
        // the one whose Room is the smallest of those it lies in: a keeper's hall before the whole of the underground
        // (whose station keeps what is lost down there anywhere else); the nearer of two the same
        List<FrostShrineBlockEntity> near = new ArrayList<>();
        int cx = BlockPos.containing(at).getX() >> 4, cz = BlockPos.containing(at).getZ() >> 4;
        for (int dx = -SEARCH_CHUNKS - 4; dx <= SEARCH_CHUNKS + 4; dx++) {
            for (int dz = -SEARCH_CHUNKS - 4; dz <= SEARCH_CHUNKS + 4; dz++) {
                if (!s.hasChunk(cx + dx, cz + dz)) {
                    continue;
                }
                for (BlockEntity be : s.getChunk(cx + dx, cz + dz).getBlockEntities().values()) {
                    if (be instanceof FrostShrineBlockEntity r && r.room != null) {
                        near.add(r);
                    }
                }
            }
        }
        FrostShrineBlockEntity best = smallestCatching(near, at);
        // FALLEN THROUGH ITS FLOOR (08.10.2026: the Chasm of Bones goes down to the bedrock now - CitadelShaftPiece):
        // dead at the foot of an open drop under a hall, it is that hall's death - up the open column it fell down
        BlockPos.MutableBlockPos up = BlockPos.containing(at).mutable();
        for (int k = 0; best == null && k < 96; k++) {
            up.move(net.minecraft.core.Direction.UP);
            if (s.getBlockState(up).isSolidRender(s, up)) {
                break;                                          // (a roof over it: it did not fall from anywhere)
            }
            best = smallestCatching(near, Vec3.atCenterOf(up));
        }
        return best;
    }

    @Nullable
    private static FrostShrineBlockEntity smallestCatching(List<FrostShrineBlockEntity> near, Vec3 at) {
        FrostShrineBlockEntity best = null;
        double bv = Double.MAX_VALUE, bd = Double.MAX_VALUE;
        for (FrostShrineBlockEntity r : near) {
            if (r.catches(at)) {
                AABB h = r.hall();
                double v = h.getXsize() * h.getYsize() * h.getZsize();
                double d = Vec3.atCenterOf(r.worldPosition).distanceToSqr(at);
                if (v < bv - 1.0D || (v < bv + 1.0D && d < bd)) {
                    bv = v;
                    bd = d;
                    best = r;
                }
            }
        }
        return best;
    }

    @Nullable
    private static FrostShrineBlockEntity near(ServerLevel s, BlockPos throne) {
        FrostShrineBlockEntity best = null;
        double bd = Double.MAX_VALUE;
        int cx = throne.getX() >> 4, cz = throne.getZ() >> 4;
        for (int dx = -SEARCH_CHUNKS; dx <= SEARCH_CHUNKS; dx++) {
            for (int dz = -SEARCH_CHUNKS; dz <= SEARCH_CHUNKS; dz++) {
                if (!s.hasChunk(cx + dx, cz + dz)) {
                    continue;
                }
                for (BlockEntity be : s.getChunk(cx + dx, cz + dz).getBlockEntities().values()) {
                    if (be instanceof FrostShrineBlockEntity r && r.room != null) {
                        double d = r.worldPosition.distSqr(throne);
                        if (d < bd) {
                            bd = d;
                            best = r;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static boolean keepsInventory(ServerLevel s) {
        return s.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
    }

    private static String keptMessage(ServerLevel s, Vec3 at, FrostShrineBlockEntity r, boolean theirs) {
        String k = StormEyeArena.at(s, at) != null ? "message.frozen_dominion.shrine_kept_throne"
                : r.wide ? "message.frozen_dominion.shrine_kept_below" : "message.frozen_dominion.shrine_kept";
        return theirs ? k : k.replace("shrine_kept", "shrine_wait");
    }

    /** How far from the shrine that is their return a death is still its own (horizontally, in blocks): the whole
     *  citadel and the land round it, not the world. */
    private static final double OWN_REACH = 256.0D;

    /**
     * THE SHRINE THAT IS THEIR RETURN, if one is and they fell within its reach: a hand laid on a shrine and a
     * death anywhere in the citadel - out of every keeper's hall too - is a rising before it with everything.
     */
    @Nullable
    private static FrostShrineBlockEntity own(ServerPlayer p, ServerLevel s) {
        BlockPos stand = p.getRespawnPosition();
        if (stand == null || !s.dimension().equals(p.getRespawnDimension())
                || Math.abs(stand.getX() + 0.5D - p.getX()) > OWN_REACH || Math.abs(stand.getZ() + 0.5D - p.getZ()) > OWN_REACH) {
            return null;
        }
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos at = stand.relative(d);
            BlockState st = s.getBlockState(at);
            if (st.getBlock() instanceof FrostShrineBlock && at.relative(st.getValue(FrostShrineBlock.FACING)).equals(stand)
                    && s.getBlockEntity(at) instanceof FrostShrineBlockEntity r) {
                return r;
            }
        }
        return null;
    }

    /** Whose this death is: the shrine that is their return (within its reach), else the one whose hall it was in. */
    @Nullable
    private static FrostShrineBlockEntity forPlayer(ServerPlayer p, ServerLevel s) {
        FrostShrineBlockEntity r = own(p, s);
        return r != null ? r : forDeath(s, p.position());
    }

    /** Is this shrine their return - laid a hand on, so they rise before it (their respawn is its standing cell)? */
    private static boolean theirs(ServerPlayer p, ServerLevel s, BlockPos shrine) {
        BlockState st = s.getBlockState(shrine);
        if (!(st.getBlock() instanceof FrostShrineBlock)) {
            return false;
        }
        BlockPos stand = shrine.relative(st.getValue(FrostShrineBlock.FACING));
        return s.dimension().equals(p.getRespawnDimension()) && stand.equals(p.getRespawnPosition());
    }

    /**
     * AT THE MOMENT OF DEATH, before anything falls: everything the dead carried - pack, hotbar, armour, off hand, every Curios
     * slot - is taken off them into the station, so when the game comes to drop their things there is nothing left:
     * not for the floor, not for a gravestone mod listening for the drops. At the lowest priority, so a mod that
     * cancels the death (a revive) has had its say first. The curse of vanishing is left on them to do its work; with
     * keepInventory nothing is touched.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel s)
                || keepsInventory(s)) {
            return;
        }
        FrostShrineBlockEntity r = forPlayer(p, s);
        if (r == null) {
            return;
        }
        CompoundTag rec = new CompoundTag();
        rec.putString("Dim", s.dimension().location().toString());
        rec.putLong("Pos", r.getBlockPos().asLong());
        ListTag slots = new ListTag();
        net.minecraft.world.entity.player.Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && !net.minecraft.world.item.enchantment.EnchantmentHelper.has(st, net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                CompoundTag one = new CompoundTag();
                one.putInt("Slot", i);
                one.put("Item", st.save(p.registryAccess()));
                slots.add(one);
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        ListTag curios = new ListTag();
        com.jastkub.frozenfortress.integration.curios.CuriosHooks.takeAll(p, curios);
        rec.put("Slots", slots);
        rec.put("Curios", curios);
        CompoundTag kept = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        // the Gale Feathers eaten in the fight that killed them: back with the rest (StormEyeArena.FEATHERS_SPENT)
        ListTag loose = new ListTag();
        int spent = kept.getInt(com.jastkub.frozenfortress.entity.boss.StormEyeArena.FEATHERS_SPENT);
        if (spent > 0) {
            loose.add(new ItemStack(com.jastkub.frozenfortress.registry.FFItems.GALE_FEATHER.get(), spent)
                    .save(p.registryAccess()));
            kept.remove(com.jastkub.frozenfortress.entity.boss.StormEyeArena.FEATHERS_SPENT);
        }
        rec.put("Loose", loose);
        kept.put(PENDING, rec);
        p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, kept);
        boolean theirs = theirs(p, s, r.getBlockPos());
        p.sendSystemMessage(Component.translatable(theirs && forDeath(s, p.position()) != r
                ? "message.frozen_dominion.shrine_kept_own" : keptMessage(s, p.position(), r, theirs)));
    }

    /** The record of what a fallen player carried, in their persisted data until they rise (onRespawn). */
    private static final String PENDING = "ffShrineReturn";

    /**
     * RISEN: before the shrine that kept their things, with everything back in its own slot; what finds its slot taken
     * goes anywhere in the pack, what will not fit into the shrine's casket. Their return stays where it was - only a
     * hand laid on a shrine makes it theirs (rising here once set it for good).
     *
     * <p>AND ONLY BEFORE ONE THAT IS THEIRS: whoever died in a shrine's hall - the shrine of the underground's hall is ALL of the underground - was
     * carried to it on rising, laid a hand on it or not. Now a shrine they have not kindled for themselves keeps their
     * things in its casket and they rise where their own return is; a hand on it gives everything back.
     */
    @SubscribeEvent
    public static void onRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        CompoundTag kept = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        if (!kept.contains(PENDING)) {
            return;
        }
        CompoundTag rec = kept.getCompound(PENDING);
        kept.remove(PENDING);
        p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, kept);
        ServerLevel at = p.server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(rec.getString("Dim"))));
        BlockPos pos = BlockPos.of(rec.getLong("Pos"));
        FrostShrineBlockEntity shrine = null;
        if (at != null) {
            at.getChunk(pos);                                       // (loaded, if it was not)
            BlockState st = at.getBlockState(pos);
            if (st.getBlock() instanceof FrostShrineBlock && at.getBlockEntity(pos) instanceof FrostShrineBlockEntity b) {
                shrine = b;
                if (!theirs(p, at, pos)) {
                    b.keepAll(p.getUUID(), rec);                    // not theirs: it holds it all for them
                    return;
                }
                net.minecraft.core.Direction facing = st.getValue(FrostShrineBlock.FACING);
                BlockPos stand = pos.relative(facing);
                p.teleportTo(at, stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D, facing.toYRot(), 0.0F);
            }
        }
        List<ItemStack> over = new ArrayList<>();
        net.minecraft.world.entity.player.Inventory inv = p.getInventory();
        ListTag slots = rec.getList("Slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < slots.size(); i++) {
            CompoundTag one = slots.getCompound(i);
            ItemStack st = ItemStack.parseOptional(p.registryAccess(), one.getCompound("Item"));
            int slot = one.getInt("Slot");
            if (slot >= 0 && slot < inv.getContainerSize() && inv.getItem(slot).isEmpty()) {
                inv.setItem(slot, st);
            } else {
                over.add(st);
            }
        }
        ListTag curios = rec.getList("Curios", Tag.TAG_COMPOUND);
        for (int i = 0; i < curios.size(); i++) {
            CompoundTag one = curios.getCompound(i);
            ItemStack st = ItemStack.parseOptional(p.registryAccess(), one.getCompound("Item"));
            if (!com.jastkub.frozenfortress.integration.curios.CuriosHooks.putBack(p, one, st)) {
                over.add(st);
            }
        }
        ListTag loose = rec.getList("Loose", Tag.TAG_COMPOUND);
        for (int i = 0; i < loose.size(); i++) {
            over.add(ItemStack.parseOptional(p.registryAccess(), loose.getCompound(i)));
        }
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack st : over) {
            if (st.isEmpty()) {
                continue;
            }
            // armour (and a shield) comes back ON, wherever it came back from: its own slot taken by nothing, it is
            // put on 
            net.minecraft.world.entity.EquipmentSlot wear = p.getEquipmentSlotForItem(st);
            if ((wear.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR
                    || wear == net.minecraft.world.entity.EquipmentSlot.OFFHAND)
                    && p.getItemBySlot(wear).isEmpty()) {
                p.setItemSlot(wear, st);
                continue;
            }
            if (!inv.add(st)) {
                left.add(st);
            }
        }
        if (!left.isEmpty()) {
            if (shrine != null) {
                shrine.keep(p.getUUID(), left);                     // (what will not fit: its casket)
            } else {
                for (ItemStack st : left) {
                    p.drop(st, false);
                }
            }
        }
        inv.setChanged();
        p.displayClientMessage(Component.translatable("message.frozen_dominion.shrine_returned"), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.6F, 0.6F);
    }

    /** Whatever still came to be dropped (another mod's own pocket), swept in after it - first and last of all. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDropsFirst(LivingDropsEvent event) {
        sweep(event, false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDropsLast(LivingDropsEvent event) {
        sweep(event, true);
    }

    private static void sweep(LivingDropsEvent event, boolean last) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel s)
                || event.getDrops().isEmpty()) {
            return;
        }
        FrostShrineBlockEntity r = forPlayer(p, s);
        if (r != null) {
            CompoundTag kept = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            CompoundTag rec = kept.getCompound(PENDING);
            if (!kept.contains(PENDING)) {
                // (no record - the death was not ours to take: into the casket, as before)
                List<ItemStack> stacks = new ArrayList<>();
                for (ItemEntity e : event.getDrops()) {
                    if (!e.getItem().isEmpty()) {
                        stacks.add(e.getItem().copy());
                    }
                }
                event.getDrops().clear();
                r.keep(p.getUUID(), stacks);
                return;
            }
            ListTag loose = rec.getList("Loose", Tag.TAG_COMPOUND);
            for (ItemEntity e : event.getDrops()) {
                if (!e.getItem().isEmpty()) {
                    loose.add(e.getItem().save(p.registryAccess()));
                }
            }
            event.getDrops().clear();
            rec.put("Loose", loose);
            kept.put(PENDING, rec);
            p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, kept);
            return;
        }
        StormEyeArena arena = last ? StormEyeArena.at(s, p.position()) : null;
        if (arena == null) {
            return;
        }
        // (a storm over a citadel built before the stations: the hall's floor at the column's foot, and they do not
        // despawn there)
        Vec3 floor = arena.hallFloor();
        for (ItemEntity e : event.getDrops()) {
            e.setPos(floor.x, floor.y + 0.5D, floor.z);
            e.setDeltaMovement(Vec3.ZERO);
            e.setUnlimitedLifetime();
        }
        p.sendSystemMessage(Component.translatable("message.frozen_dominion.shrine_floor"));
    }

    // ------------------------------------------------------------------------------------------------ animation
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "shrine", 4, state -> {
            int stage = getBlockState().getBlock() instanceof FrostShrineBlock
                    ? getBlockState().getValue(FrostShrineBlock.STAGE) : FrostShrineBlock.STIRRING;
            int before = seenStage;
            seenStage = stage;
            if (stage == FrostShrineBlock.KINDLED) {
                // caught as it catches: the kindling first; already burning when first seen: burning
                if (burnClip == null || before != FrostShrineBlock.KINDLED && before >= 0) {
                    burnClip = before >= 0 && before != FrostShrineBlock.KINDLED ? KINDLE : BURNING;
                }
                return state.setAndContinue(burnClip);
            }
            burnClip = null;
            return state.setAndContinue(STIRRING);
        }));
        controllers.add(new AnimationController<>(this, "remember", 3,
                state -> software.bernie.geckolib.animation.PlayState.STOP).triggerableAnim("remember", REMEMBER));
        controllers.add(new AnimationController<>(this, "casket", 6, state -> state.setAndContinue(
                getBlockState().getBlock() instanceof FrostShrineBlock && getBlockState().getValue(FrostShrineBlock.FULL)
                        ? CASKET_FULL : CASKET_EMPTY)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** The crystal flame stands a block over it. (1.21) the renderer asks for this: its getRenderBoundingBox(be). */
    public AABB renderBox() {
        return new AABB(worldPosition).inflate(0.5D, 1.5D, 0.5D);
    }

    /**
     * ITS CASKET, OPENED FOR ONE OF THE FALLEN: a chest's window on their list in it, live - what is taken out is gone
     * from the list then and there (nothing to lose if the game stops with it open). Nothing can be put in: it keeps
     * what death left in it, it is not a chest to store things in. Places emptied are swept out as it closes; more than
     * six rows' worth, and the rest is there the next time it is opened.
     */
    private static final class Casket implements Container {
        private final FrostShrineBlockEntity shrine;
        private final UUID who;
        private final int size;

        Casket(FrostShrineBlockEntity shrine, UUID who, int size) {
            this.shrine = shrine;
            this.who = who;
            this.size = size;
        }

        private List<ItemStack> list() {
            return shrine.kept.getOrDefault(who, List.of());
        }

        @Override
        public int getContainerSize() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return list().stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            List<ItemStack> l = list();
            return slot < l.size() ? l.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            List<ItemStack> l = list();
            ItemStack out = slot < l.size() ? ContainerHelper.removeItem(l, slot, count) : ItemStack.EMPTY;
            if (!out.isEmpty()) {
                setChanged();
            }
            return out;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            List<ItemStack> l = list();
            return slot < l.size() ? ContainerHelper.takeItem(l, slot) : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            List<ItemStack> l = shrine.kept.get(who);
            if (l == null) {
                if (stack.isEmpty()) {
                    return;
                }
                l = new ArrayList<>();
                shrine.kept.put(who, l);
            }
            while (l.size() <= slot) {
                l.add(ItemStack.EMPTY);
            }
            l.set(slot, stack);
            setChanged();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return false;
        }

        @Override
        public void setChanged() {
            shrine.setChanged();
            shrine.showFull();
        }

        @Override
        public boolean stillValid(Player player) {
            return !shrine.isRemoved() && Container.stillValidBlockEntity(shrine, player);
        }

        @Override
        public void stopOpen(Player player) {
            shrine.sweep(who);
        }

        @Override
        public void clearContent() {
            List<ItemStack> l = shrine.kept.get(who);
            if (l != null) {
                l.clear();
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ saving
    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        kept.clear();
        room = tag.contains("Room") ? tag.getIntArray("Room") : null;
        if (room != null && room.length != 6) {
            room = null;
        }
        // TURNED WITH ITS TEMPLATE (the watchtower: a jigsaw piece, placed any of four ways): its block's front was
        // turned with it, its Room was not - by as many quarters as lie between the front the generator wrote and the
        // one it has now. Once (jigsaw-obrot-nbt-pulapka).
        turned = tag.getBoolean("Turned");
        wide = tag.getBoolean("Wide");
        if (!turned && room != null && tag.contains("Facing")
                && getBlockState().getBlock() instanceof FrostShrineBlock) {
            net.minecraft.core.Direction was = net.minecraft.core.Direction.byName(tag.getString("Facing"));
            net.minecraft.core.Direction now = getBlockState().getValue(FrostShrineBlock.FACING);
            if (was != null && was.getAxis().isHorizontal()) {
                int k = 0;
                while (k < 4 && com.jastkub.frozenfortress.block.TemplateTurn.turn(was, k) != now) {
                    k++;
                }
                room = com.jastkub.frozenfortress.block.TemplateTurn.box(room, k & 3, true);
            }
            turned = true;
        }
        ListTag all = tag.getList("Fallen", Tag.TAG_COMPOUND);
        for (int i = 0; i < all.size(); i++) {
            CompoundTag one = all.getCompound(i);
            if (!one.hasUUID("Who")) {
                continue;
            }
            UUID who = one.getUUID("Who");
            ListTag items = one.getList("Items", Tag.TAG_COMPOUND);
            List<ItemStack> stacks = new ArrayList<>();
            for (int k = 0; k < items.size(); k++) {
                ItemStack st = ItemStack.parseOptional(registries, items.getCompound(k));
                if (!st.isEmpty()) {
                    stacks.add(st);
                }
            }
            if (!stacks.isEmpty()) {
                kept.put(who, stacks);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag all = new ListTag();
        for (UUID id : kept.keySet()) {
            CompoundTag one = new CompoundTag();
            one.putUUID("Who", id);
            ListTag items = new ListTag();
            for (ItemStack st : kept.getOrDefault(id, List.of())) {
                if (!st.isEmpty()) {
                    items.add(st.save(registries));
                }
            }
            one.put("Items", items);
            all.add(one);
        }
        tag.put("Fallen", all);
        if (room != null) {
            tag.putIntArray("Room", room);
        }
        tag.putBoolean("Turned", turned);
        if (wide) {
            tag.putBoolean("Wide", true);
        }
    }
}
