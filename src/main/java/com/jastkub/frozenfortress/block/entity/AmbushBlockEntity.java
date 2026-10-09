package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * THE TREASURY'S AMBUSH: hidden under its floor, it watches the chest of the
 * treasure. Open it (or break it) and the guards that stood as statues round the room - statues that answer nobody's
 * coming, only this - shake, and a second and a half later break out; the gate on the way in (a BossGateBlockEntity
 * whose heart this is) drops while any of them lives.
 *
 * <p>NBT (from the citadel's generator): Chest - the chest's cell relative to this block; Room - the room, a box
 * relative to this block (far corner exclusive); Item - optional, an item id: then it is sprung not by the lid lifting
 * but by THAT being taken out of the chest.
 */
public class AmbushBlockEntity extends BlockEntity {

    private static final int ARMED = 0, WAKING = 1, HOLDING = 2, DONE = 3;
    /** How long the guards shake before they break out. */
    private static final int WAKE_TICKS = 30;

    private int[] chest = {0, 1, 0};
    private int[] room = {0, 0, 0, 1, 1, 1};
    /** What, taken from the chest, springs it ("" - the lid lifting does). */
    private String item = "";
    private int state = ARMED;
    private int timer;
    /** Has it seen its chest where it watches (chestCell)? */
    private boolean bound;
    private final List<UUID> guards = new ArrayList<>();

    public AmbushBlockEntity(BlockPos pos, BlockState state) {
        super(FFBlockEntities.AMBUSH.get(), pos, state);
    }

    /** Does it hold its gate down: its guards are waking, or one of them still lives. */
    public boolean holding() {
        return state == WAKING || state == HOLDING;
    }

    private AABB room() {
        return new AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(worldPosition.offset(room[0], room[1], room[2])), net.minecraft.world.phys.Vec3.atLowerCornerOf(worldPosition.offset(room[3], room[4], room[5])));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState bs, AmbushBlockEntity ambush) {
        if (!(level instanceof ServerLevel s) || ambush.state == DONE || level.getGameTime() % 5 != 0) {
            return;
        }
        switch (ambush.state) {
            case ARMED -> {
                BlockPos c = ambush.chestCell(s);
                boolean gone = !(s.getBlockState(c).getBlock() instanceof ChestBlock);
                boolean sprung = ambush.item.isEmpty()
                        ? gone || ChestBlockEntity.getOpenCount(s, c) > 0
                        : gone || !ambush.chestHolds(s, c);
                if (sprung) {
                    ambush.spring(s);
                }
            }
            case WAKING -> {
                ambush.timer -= 5;
                if (ambush.timer <= 0) {
                    ambush.release(s);
                }
            }
            case HOLDING -> {
                boolean any = false;
                for (UUID id : ambush.guards) {
                    Entity e = s.getEntity(id);
                    if (e != null && e.isAlive()) {
                        any = true;
                        break;
                    }
                }
                if (!any) {
                    ambush.state = DONE;
                    ambush.setChanged();
                }
            }
            default -> {
            }
        }
    }

    /**
     * Its chest's cell. A citadel laid before 08.10.2026 had it watching the cell its chest was lifted out of (onto its
     * pedestal, a block up): until it has once seen its chest, it looks for it a
     * block or two over or under, and keeps to it from then on.
     */
    private BlockPos chestCell(ServerLevel s) {
        BlockPos c = worldPosition.offset(chest[0], chest[1], chest[2]);
        if (!bound) {
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                if (s.getBlockState(c.above(dy)).getBlock() instanceof ChestBlock) {
                    chest[1] += dy;
                    bound = true;
                    setChanged();
                    return c.above(dy);
                }
            }
        }
        return c;
    }

    /** Does the chest still hold its thing? (A chest whose loot table has not been rolled yet - nobody has opened it -
     *  holds it still: the table is what puts it there.) */
    private boolean chestHolds(ServerLevel s, BlockPos c) {
        if (!(s.getBlockEntity(c) instanceof ChestBlockEntity box)) {
            return false;
        }
        if (box.getLootTable() != null) {
            return true;
        }
        net.minecraft.resources.ResourceLocation want = net.minecraft.resources.ResourceLocation.tryParse(item);
        for (int i = 0; i < box.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack st = box.getItem(i);
            if (!st.isEmpty() && want != null
                    && want.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()))) {
                return true;
            }
        }
        return false;
    }

    /** The chest is open (or its thing taken): the guards stir. */
    private void spring(ServerLevel s) {
        int n = 0;
        for (CitadelStatueBlockEntity statue : guardStatues(s)) {
            statue.startShaking(WAKE_TICKS);
            n++;
        }
        if (n == 0) {
            state = DONE;                                   // (nobody left to stand guard: it was sprung before)
            setChanged();
            return;
        }
        BlockPos c = worldPosition.offset(chest[0], chest[1], chest[2]);
        s.playSound(null, c, FFSounds.ICE_CRACK.get(), SoundSource.HOSTILE, 2.0F, 0.5F);
        s.playSound(null, c, FFSounds.BOSS_GATE_DROP.get(), SoundSource.HOSTILE, 0.6F, 1.4F);
        state = WAKING;
        timer = WAKE_TICKS;
        setChanged();
    }

    /** And out they come: each one counted, the gate held while any lives. */
    private void release(ServerLevel s) {
        for (CitadelStatueBlockEntity statue : guardStatues(s)) {
            BlockPos at = statue.getBlockPos();
            if (statue.awakenNow(s)) {
                for (Mob m : s.getEntitiesOfClass(Mob.class, new AABB(at).inflate(2.0D),
                        m -> m.isAlive() && m.tickCount < 5 && !guards.contains(m.getUUID()))) {
                    guards.add(m.getUUID());
                    Player p = s.getNearestPlayer(m, 32.0D);
                    if (p != null && !p.isCreative() && !p.isSpectator()) {
                        m.setTarget(p);
                    }
                }
            }
        }
        state = guards.isEmpty() ? DONE : HOLDING;
        setChanged();
    }

    /** The statues of its room that wake only when called: its guards. */
    private List<CitadelStatueBlockEntity> guardStatues(ServerLevel s) {
        List<CitadelStatueBlockEntity> out = new ArrayList<>();
        AABB r = room();
        for (BlockPos q : BlockPos.betweenClosed((int) r.minX, (int) r.minY, (int) r.minZ,
                (int) r.maxX - 1, (int) r.maxY - 1, (int) r.maxZ - 1)) {
            if (s.getBlockEntity(q) instanceof CitadelStatueBlockEntity statue && statue.answersOnlyACall()) {
                out.add(statue);
            }
        }
        return out;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Chest")) chest = tag.getIntArray("Chest");
        if (tag.contains("Room")) room = tag.getIntArray("Room");
        item = tag.getString("Item");
        state = tag.getInt("State");
        timer = tag.getInt("Timer");
        bound = tag.getBoolean("Bound");
        guards.clear();
        for (Tag t : tag.getList("Guards", Tag.TAG_INT_ARRAY)) {
            guards.add(NbtUtils.loadUUID(t));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putIntArray("Chest", chest);
        tag.putIntArray("Room", room);
        tag.putString("Item", item);
        tag.putInt("State", state);
        tag.putInt("Timer", timer);
        tag.putBoolean("Bound", bound);
        ListTag list = new ListTag();
        for (UUID id : guards) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("Guards", list);
    }
}
