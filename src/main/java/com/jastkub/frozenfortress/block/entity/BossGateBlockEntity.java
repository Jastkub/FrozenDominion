package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFBlocks;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * THE GATE THAT DROPS BEHIND YOU: a portcullis over the
 * way into a miniboss's room - the Turnkey's hall, the Chapel of Rime. It hangs
 * raised in the wall over the opening (this block is in the lintel) until
 * someone crosses into the room while its keeper lives: then it falls, and
 * stays down until the keeper is dead or nobody is left alive in there.
 *
 * <p>While it is down the opening is a plane of door_barrier blocks; anyone
 * standing in that plane when it falls is put inside, so the doorway cannot be
 * held open by standing in it. Creative and spectating players do not spring
 * it (and are not counted as somebody still in there).
 */
public class BossGateBlockEntity extends BlockEntity implements GeoBlockEntity {

    public static final int OPEN = 0, SLAMMING = 1, SHUT = 2, RAISING = 3;
    private static final int SLAM_TICKS = 12;
    /** How long after the bars are down its keeper is told (GateKeeper): the sound of the gate, a second, the scene. */
    private static final int TELL_AFTER = 20;
    private static final int RAISE_TICKS = 60;

    private static final RawAnimation OPEN_ANIM = RawAnimation.begin().thenLoop("animation.boss_gate.open");
    private static final RawAnimation SHUT_ANIM = RawAnimation.begin().thenLoop("animation.boss_gate.closed");
    private static final RawAnimation SLAM_ANIM = RawAnimation.begin().thenPlayAndHold("animation.boss_gate.slam");
    private static final RawAnimation RAISE_ANIM = RawAnimation.begin().thenPlayAndHold("animation.boss_gate.raise");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private Direction facing = Direction.NORTH;
    private float shift;
    /** The opening, from this block: x0 y0 z0 x1 y1 z1, inclusive. */
    private int[] barrierBox = {0, 0, 0, 0, 0, 0};
    /** The room it shuts, from this block: x0 y0 z0 x1 y1 z1, the far corner exclusive. */
    private int[] roomBox = {0, 0, 0, 0, 0, 0};
    /** Its keeper's entity type. */
    private String keeper = "";
    /** Drawn this much wider and taller than the 4x5 model (the prison's gate is 12x14). */
    private float scaleX = 1.0F, scaleY = 1.0F;
    /** A prison heart in its room, from this block: while its statue wakes, the keeper is as good as there. */
    private int[] heart;
    /** A place's scene its coming down shows, a second after (BossCutscenes.placeScene: the Rift's), anchored at its
     *  heart, its frame turned SceneYaw; empty - none. */
    private String scene = "";
    private float sceneYaw;
    private int state = OPEN;
    private int clock;
    /** Ticks somebody has been inside (it falls a moment after they cross, not on the threshold). */
    private int insideFor;
    /** A way OUT only (06.10.2026: the cisterns' north side): down from the start, up when the keeper has fallen. */
    private boolean exit;
    /** Its keeper is dead (told by keeperFell): an exit gate rises for good. */
    private boolean fallen;
    /** Seconds someone has been in its hall with no keeper of its kind alive there (an exit gate: exitTick). */
    private int missing;
    /** Who it shut in: it stays down until the keeper is dead or none of them is left alive near. */
    private final java.util.Set<java.util.UUID> shutIn = new java.util.HashSet<>();

    public BossGateBlockEntity(BlockPos pos, BlockState blockState) {
        super(FFBlockEntities.BOSS_GATE.get(), pos, blockState);
    }

    public Direction facing() {
        return facing;
    }

    public float shift() {
        return shift;
    }

    public float scaleX() {
        return scaleX;
    }

    public float scaleY() {
        return scaleY;
    }

    public int gateState() {
        return state;
    }

    /** The gates loaded on this client: the music asks them whether a fight is on. */
    public static final java.util.Set<BossGateBlockEntity> CLIENT =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            CLIENT.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT.remove(this);
    }

    /** Is it down with this point inside its room - a fight on, with whoever stands there in it? */
    public boolean shutOn(Vec3 p) {
        return !exit && (state == SLAMMING || state == SHUT) && room().contains(p);   // (an exit gate is down all along)
    }

    /** Its keeper's entity type, e.g. frozen_dominion:turnkey. */
    public String keeper() {
        return keeper;
    }

    /** Its hall and its opening, in the world (the client's frost veil - FrostVeil). */
    public AABB roomBox() {
        return room();
    }

    public AABB openingBox() {
        return opening();
    }

    public boolean isExit() {
        return exit;
    }

    /** Its keeper is a prisoner still held - the Ice Prison's heart not yet spent (client or server alike: the heart
     *  syncs its state). Before he is loose there is no keeper's body in the hall to look for. */
    public boolean prisonerHeld() {
        return heart != null && heart.length == 3 && level != null
                && level.getBlockEntity(worldPosition.offset(heart[0], heart[1], heart[2])) instanceof PrisonHeartBlockEntity h
                && !h.isDone();
    }

    private AABB room() {
        return new AABB(worldPosition.offset(roomBox[0], roomBox[1], roomBox[2]),
                worldPosition.offset(roomBox[3], roomBox[4], roomBox[5]));
    }

    private AABB opening() {
        return new AABB(worldPosition.offset(barrierBox[0], barrierBox[1], barrierBox[2]),
                worldPosition.offset(barrierBox[3] + 1, barrierBox[4] + 1, barrierBox[5] + 1));
    }

    private static boolean counts(Player p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator();
    }

    private boolean keeperAlive(ServerLevel level) {
        // THE PRISON'S KEEPER IS ITS HEART until the guardian is out: asleep or waking, the fight is
        // still to come, and the gate falls behind whoever walks in; dead, it is over
        // A TRAP ROOM'S GATES (06.10.2026): their keeper is the room's heart - down while it beats
        if (heart != null && heart.length == 3
                && level.getBlockEntity(worldPosition.offset(heart[0], heart[1], heart[2])) instanceof FrostHeartBlockEntity f) {
            // (and while the room's keeper lives, if it has one: the Ice Aurochs of the Heart's hall, 06.10.2026)
            return f.active() || (!keeper.isEmpty() && keeperLives(level));
        }
        // THE TREASURY'S AMBUSH (07.10.2026): down while the guards it woke are waking or alive
        if (heart != null && heart.length == 3
                && level.getBlockEntity(worldPosition.offset(heart[0], heart[1], heart[2])) instanceof AmbushBlockEntity a) {
            return a.holding();
        }
        if (heart != null && heart.length == 3
                && level.getBlockEntity(worldPosition.offset(heart[0], heart[1], heart[2])) instanceof PrisonHeartBlockEntity h) {
            if (h.isDone()) {
                return false;
            }
            if (!h.isLoose()) {
                return true;
            }
        }
        return keeperLives(level);
    }

    /** Is a living one of the keeper's kind in (or about) the room? */
    private boolean keeperLives(ServerLevel level) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(keeper));
        if (type == null) {
            return false;
        }
        for (Entity e : level.getEntities((Entity) null, room().inflate(3.0D), e -> e.getType() == type)) {
            if (e instanceof LivingEntity l && l.isAlive()) {
                return true;
            }
        }
        return false;
    }

    private boolean someoneInside(ServerLevel level) {
        AABB room = room();
        for (Player p : level.players()) {
            if (counts(p) && room.contains(p.position())) {
                return true;
            }
        }
        return false;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState bs, BossGateBlockEntity gate) {
        if (!(level instanceof ServerLevel s)) {
            return;
        }
        gate.clock++;
        if (gate.exit) {
            gate.exitTick(s);
            return;
        }
        switch (gate.state) {
            case OPEN -> {
                gate.insideFor = gate.someoneInside(s) ? gate.insideFor + 1 : 0;
                if (gate.insideFor >= 20 && gate.clock % 2 == 0 && gate.keeperAlive(s)) {
                    gate.slam(s);
                }
            }
            case SLAMMING -> {
                if (gate.clock == 1) {
                    // THE BARS COMING DOWN, heard: one
                    // sound from the chain letting go to the bars ringing in their frame - its clang lands at 0.35 s,
                    // the 8th tick, with the shards below. From the opening's middle, so both sides hear it whole.
                    Vec3 mid = gate.opening().getCenter();
                    s.playSound(null, mid.x, mid.y, mid.z, FFSounds.BOSS_GATE_DROP.get(), SoundSource.BLOCKS,
                            3.0F, 0.96F + s.random.nextFloat() * 0.08F);
                }
                if (gate.clock == 8) {                              // and down
                    Vec3 foot = gate.opening().getCenter().subtract(0.0D, gate.opening().getYsize() / 2.0D - 0.2D, 0.0D);
                    s.sendParticles(FFParticles.ICE_SHARD.get(), foot.x, foot.y, foot.z, 30,
                            gate.opening().getXsize() / 2.5D, 0.1D, gate.opening().getZsize() / 2.5D, 0.12D);
                }
                if (gate.clock >= SLAM_TICKS) {
                    gate.set(SHUT);
                }
            }
            case SHUT -> {
                if (gate.clock == TELL_AFTER) {
                    gate.tellKeepers(s);                            // a second after the bars came down
                    gate.showScene(s);
                }
                // DOWN FOR GOOD - until the keeper
                // is dead, or every one of those it shut in is dead or gone
                if (gate.clock % 10 == 0 && (!gate.keeperAlive(s) || !gate.anyoneShutIn(s))) {
                    gate.shutIn.clear();
                    gate.raise(s);
                }
            }
            case RAISING -> {
                if (gate.clock % 6 == 3) {                          // the ratchet, a notch at a time
                    s.playSound(null, pos, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 1.6F, 0.7F);
                    s.playSound(null, pos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 1.2F, 0.5F);
                }
                if (gate.clock == RAISE_TICKS * 2 / 3) {            // high enough to pass under
                    gate.barriers(s, false);
                }
                if (gate.clock >= RAISE_TICKS) {
                    gate.barriers(s, false);
                    s.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.8F, 0.5F);
                    gate.set(OPEN);
                }
            }
            default -> gate.set(OPEN);
        }
    }

    /** An exit gate: down until its keeper has fallen, then up for good - its raising the ordinary one. */
    private void exitTick(ServerLevel s) {
        switch (state) {
            case OPEN -> {
                if (!fallen && clock % 10 == 0) {                   // (put back down, should it be up while she lives)
                    barriers(s, true);
                    set(SHUT);
                }
            }
            case SHUT -> {
                // ITS KEEPER NOWHERE: someone is in its hall and for ten seconds nothing of its keeper's kind lives
                // there - it has fallen, whether or not the word came
                if (!fallen && clock % 20 == 0) {
                    boolean seen = false;
                    for (Player p : s.getEntitiesOfClass(Player.class, room(), BossGateBlockEntity::counts)) {
                        seen = true;
                        break;
                    }
                    missing = seen && !keeperLives(s) ? missing + 1 : 0;
                    if (missing >= 10) {
                        fallen = true;
                        setChanged();
                    }
                }
                if (fallen && clock > 20) {
                    raise(s);
                }
            }
            case RAISING -> {
                if (clock % 6 == 3) {
                    s.playSound(null, worldPosition, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 1.6F, 0.7F);
                }
                if (clock >= RAISE_TICKS * 2 / 3) {
                    barriers(s, false);
                }
                if (clock >= RAISE_TICKS) {
                    set(OPEN);
                }
            }
            default -> set(SHUT);
        }
    }

    /** The hall a keeper of this kind keeps near `near` - its (way-in) gate's Room; null if no such gate is loaded. */
    @javax.annotation.Nullable
    public static AABB hallOf(ServerLevel s, LivingEntity keeper, BlockPos near, int chunks) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(keeper.getType());
        int cx = near.getX() >> 4, cz = near.getZ() >> 4;
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                if (!s.hasChunk(cx + dx, cz + dz)) {
                    continue;
                }
                for (BlockEntity be : s.getChunk(cx + dx, cz + dz).getBlockEntities().values()) {
                    if (be instanceof BossGateBlockEntity g && !g.exit && String.valueOf(id).equals(g.keeper)
                            && g.room().contains(Vec3.atCenterOf(near))) {
                        return g.room();
                    }
                }
            }
        }
        return null;
    }

    /** A keeper has died: every exit gate of its kind about it may rise. */
    public static void keeperFell(Level level, LivingEntity keeper) {
        keeperFell(level, keeper, 3);
    }

    /** ...sought within `chunks` chunks of where it fell (a hall longer than three: the Bone Lord's chasm). */
    public static void keeperFell(Level level, LivingEntity keeper, int chunks) {
        if (!(level instanceof ServerLevel s)) {
            return;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(keeper.getType());
        int cx = keeper.blockPosition().getX() >> 4, cz = keeper.blockPosition().getZ() >> 4;
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                if (!s.hasChunk(cx + dx, cz + dz)) {
                    continue;
                }
                for (BlockEntity be : s.getChunk(cx + dx, cz + dz).getBlockEntities().values()) {
                    if (be instanceof BossGateBlockEntity g && g.exit && String.valueOf(id).equals(g.keeper)
                            && g.room().inflate(4.0D).contains(keeper.position())) {
                        g.fallen = true;
                        g.setChanged();
                    }
                }
            }
        }
    }

    /**
     * HELD BACK BY ITS GATE: a
     * keeper whose hall has a gate does not wake when it sees you - first its gate comes down behind you, and a second
     * after the bars land it is told (GateKeeper.gateShut) and wakes, its scene with it; nothing of the gate's own
     * sound under its words. True while such a gate is up, or has not been down that second. (A blow still wakes it.)
     */
    public static boolean holdsBack(LivingEntity keeper) {
        if (!(keeper.level() instanceof ServerLevel s)) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(keeper.getType());
        int cx = keeper.blockPosition().getX() >> 4, cz = keeper.blockPosition().getZ() >> 4;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (!s.hasChunk(cx + dx, cz + dz)) {
                    continue;
                }
                for (BlockEntity be : s.getChunk(cx + dx, cz + dz).getBlockEntities().values()) {
                    if (be instanceof BossGateBlockEntity g && !g.exit && String.valueOf(id).equals(g.keeper)
                            && g.room().inflate(3.0D).contains(keeper.position())) {
                        return !(g.state == SHUT && g.clock >= TELL_AFTER);
                    }
                }
            }
        }
        return false;
    }

    /**
     * IN THE KEEPER'S OWN HALL: is `p` inside the Room of the gate that keeps `keeper`?
     * Whoever its gate has shut in with it sees its entrance whatever stands between them (BossCutscenes.send) - the
     * sight lines are for those outside the hall, under it or over it.
     */
    public static boolean inHallOf(LivingEntity keeper, Player p) {
        if (!(keeper.level() instanceof ServerLevel s)) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(keeper.getType());
        int cx = keeper.blockPosition().getX() >> 4, cz = keeper.blockPosition().getZ() >> 4;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (!s.hasChunk(cx + dx, cz + dz)) {
                    continue;
                }
                for (BlockEntity be : s.getChunk(cx + dx, cz + dz).getBlockEntities().values()) {
                    if (be instanceof BossGateBlockEntity g && !g.exit && String.valueOf(id).equals(g.keeper)
                            && g.room().inflate(3.0D).contains(keeper.position())) {
                        return g.room().inflate(1.0D).contains(p.position());
                    }
                }
            }
        }
        return false;
    }

    /** Down: its place's scene, if it has one, to whoever it has shut in (once each). */
    private void showScene(ServerLevel level) {
        if (scene.isEmpty()) {
            return;
        }
        BlockPos at = heart != null && heart.length == 3 ? worldPosition.offset(heart[0], heart[1], heart[2])
                : BlockPos.containing(room().getCenter());
        AABB in = room().inflate(1.0D);
        com.jastkub.frozenfortress.BossCutscenes.placeScene(level, scene, Vec3.atBottomCenterOf(at), sceneYaw,
                p -> in.contains(p.position()));
    }

    /** Down: a keeper that waits for it (GateKeeper - the Lamplighter's entrance) is told so. */
    private void tellKeepers(ServerLevel level) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(keeper));
        if (type == null) {
            return;
        }
        for (Entity e : level.getEntities((Entity) null, room().inflate(3.0D), e -> e.getType() == type)) {
            if (e instanceof com.jastkub.frozenfortress.entity.GateKeeper k && e.isAlive()) {
                k.gateShut();
            }
        }
    }

    private boolean anyoneShutIn(ServerLevel s) {
        Vec3 c = room().getCenter();
        for (java.util.UUID id : shutIn) {
            Player p = s.getPlayerByUUID(id);
            if (p != null && p.isAlive() && !p.isSpectator() && p.distanceToSqr(c) < 80.0D * 80.0D) {
                return true;
            }
        }
        return false;
    }

    private void slam(ServerLevel s) {
        shutIn.clear();
        AABB inside = room().inflate(2.0D);
        for (Player p : s.players()) {
            if (counts(p) && inside.contains(p.position())) {
                shutIn.add(p.getUUID());
            }
        }
        // whoever stands in the doorway is put inside: it cannot be held open with a body
        AABB plane = opening();
        Vec3 in = room().getCenter().subtract(plane.getCenter());
        in = Math.abs(in.x) > Math.abs(in.z) ? new Vec3(Math.signum(in.x), 0.0D, 0.0D) : new Vec3(0.0D, 0.0D, Math.signum(in.z));
        for (LivingEntity e : s.getEntitiesOfClass(LivingEntity.class, plane.inflate(0.3D, 0.0D, 0.3D))) {
            Vec3 p = e.position();
            double along = in.x != 0.0D ? plane.getCenter().x - p.x : plane.getCenter().z - p.z;
            double push = (in.x != 0.0D ? in.x : in.z) * along + 1.2D;
            e.teleportTo(p.x + in.x * push, p.y, p.z + in.z * push);
        }
        barriers(s, true);
        set(SLAMMING);
    }

    private void raise(ServerLevel s) {
        set(RAISING);
        s.playSound(null, worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2.0F, 0.45F);
    }

    private void barriers(ServerLevel s, boolean shut) {
        BlockPos a = worldPosition.offset(barrierBox[0], barrierBox[1], barrierBox[2]);
        BlockPos b = worldPosition.offset(barrierBox[3], barrierBox[4], barrierBox[5]);
        for (BlockPos p : BlockPos.betweenClosed(a, b)) {
            BlockState here = s.getBlockState(p);
            if (shut && (here.isAir() || here.canBeReplaced())) {
                s.setBlock(p, FFBlocks.DOOR_BARRIER.get().defaultBlockState(), 3);
            } else if (!shut && here.is(FFBlocks.DOOR_BARRIER.get())) {
                s.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private void set(int st) {
        state = st;
        clock = 0;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        double w = 4.0D * Math.max(1.0F, scaleX), h = 6.0D * Math.max(1.0F, scaleY);
        return new AABB(worldPosition).inflate(w, 0.0D, w).expandTowards(0.0D, -h, 0.0D)
                .expandTowards(0.0D, h, 0.0D);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "gate", 0, s -> s.setAndContinue(switch (state) {
            case SLAMMING -> SLAM_ANIM;
            case SHUT -> SHUT_ANIM;
            case RAISING -> RAISE_ANIM;
            default -> OPEN_ANIM;
        })));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        Direction d = Direction.byName(tag.getString("Facing"));
        facing = d != null ? d : Direction.NORTH;
        shift = tag.getFloat("Shift");
        if (tag.contains("BarrierBox")) barrierBox = tag.getIntArray("BarrierBox");
        if (tag.contains("RoomBox")) roomBox = tag.getIntArray("RoomBox");
        keeper = tag.getString("Keeper");
        scaleX = tag.contains("ScaleX") ? tag.getFloat("ScaleX") : 1.0F;
        scaleY = tag.contains("ScaleY") ? tag.getFloat("ScaleY") : 1.0F;
        heart = tag.contains("Heart") ? tag.getIntArray("Heart") : null;
        scene = tag.getString("Scene");
        sceneYaw = tag.getFloat("SceneYaw");
        state = tag.getInt("State");
        exit = tag.getBoolean("Exit");
        fallen = tag.getBoolean("Fallen");
        shutIn.clear();
        for (net.minecraft.nbt.Tag t : tag.getList("ShutIn", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            shutIn.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
        }
        clock = tag.getInt("Clock");
        // placed with a turned structure (the watchtower is a jigsaw piece): turn what it keeps with it
        if (!tag.getBoolean("Turned")) {
            int k = com.jastkub.frozenfortress.block.TemplateTurn.quarters(getBlockState());
            if (k != 0) {
                facing = com.jastkub.frozenfortress.block.TemplateTurn.turn(facing, k);
                barrierBox = com.jastkub.frozenfortress.block.TemplateTurn.box(barrierBox, k, false);
                roomBox = com.jastkub.frozenfortress.block.TemplateTurn.box(roomBox, k, true);
                if (heart != null && heart.length == 3) {
                    int[] h = com.jastkub.frozenfortress.block.TemplateTurn.turn(heart[0], heart[2], k);
                    heart = new int[]{h[0], heart[1], h[1]};
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Facing", facing.getName());
        tag.putFloat("Shift", shift);
        tag.putIntArray("BarrierBox", barrierBox);
        tag.putIntArray("RoomBox", roomBox);
        tag.putString("Keeper", keeper);
        tag.putFloat("ScaleX", scaleX);
        tag.putFloat("ScaleY", scaleY);
        if (heart != null) {
            tag.putIntArray("Heart", heart);
        }
        if (!scene.isEmpty()) {
            tag.putString("Scene", scene);
            tag.putFloat("SceneYaw", sceneYaw);
        }
        tag.putInt("State", state);
        tag.putBoolean("Exit", exit);
        tag.putBoolean("Fallen", fallen);
        net.minecraft.nbt.ListTag ids = new net.minecraft.nbt.ListTag();
        for (java.util.UUID id : shutIn) {
            ids.add(net.minecraft.nbt.NbtUtils.createUUID(id));
        }
        tag.put("ShutIn", ids);
        tag.putInt("Clock", clock);
        tag.putBoolean("Turned", true);
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
