package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * SERCE MROZU - the heart of a trap room.
 *
 * <p>While it beats, its room is its own:
 * <ul>
 *   <li>THE COLD: whoever is in it and not by a burning hearth freezes - the game's own freezing, the frost
 *       creeping over the screen - and, frozen through, is hurt. A hearth (a campfire, lit with the flint and
 *       steel left at the door) is the only warmth; a Potion of Warmth is no use against this cold.</li>
 *   <li>NOTHING IS BUILT in it (CommonEvents.onPlaceInArena), and the room's portcullises (BossGateBlockEntity,
 *       with this as its heart) stay down behind whoever came in, until it is out - or nobody is left alive in
 *       there; dying opens the gates, and the hearths and what lives there are as they were.</li>
 *   <li>THE RIFT'S (mode "hand"): ice spikes on its floor hurt whoever is down on them; frost maws in its walls
 *       spit bombs of ice (FrostCannonBlockEntity) - and at the end of the way over, the Crownbreaker's wedge splits
 *       the heart.</li>
 *   <li>THE HEART'S HALL'S (mode "fires"): its four hearths, all burning, lay it open - its shell parts - and
 *       then it can be struck to pieces (six blows).</li>
 *   <li>A KEEPER'S (mode "keeper"): on its keeper's altar, beating for
 *       him - nothing puts it out but his death ({@link #keeperFell}).</li>
 * </ul>
 */
public class FrostHeartBlockEntity extends BlockEntity implements GeoBlockEntity {

    public static final Set<FrostHeartBlockEntity> SERVER = Collections.newSetFromMap(new WeakHashMap<>());
    /** The client's: where its cold is on you (ColdOverlay's mist and heartbeat). */
    private static final Set<FrostHeartBlockEntity> CLIENT = Collections.newSetFromMap(new WeakHashMap<>());
    public static final String MODE_HAND = "hand", MODE_FIRES = "fires", MODE_KEEPER = "keeper";
    private static final int NO_PIT = -999;
    /** Ticks between the maws' shots as the room is entered... */
    private static final int MAW_EVERY = 90;
    /** ...and quickening: MAW_STEP ticks off every MAW_RAMP ticks somebody is still in it, down to MAW_FASTEST. */
    private static final int MAW_RAMP = 200, MAW_STEP = 8, MAW_FASTEST = 30;

    private static final RawAnimation BEAT = RawAnimation.begin().thenLoop("animation.frost_heart.beat");
    private static final RawAnimation EXPOSED = RawAnimation.begin().thenLoop("animation.frost_heart.exposed");
    private static final RawAnimation SHATTER = RawAnimation.begin().thenPlayAndHold("animation.frost_heart.shatter");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private String mode = MODE_HAND;
    private int[] roomBox = {-8, -2, -8, 8, 8, 8};
    /** The spikes' floor, relative to the heart (the Rift), or NO_PIT. */
    private int pit = NO_PIT;
    private int hitsToBreak = 6;
    /**
     * (An old world's heart) how fast its cold crept, in tenths: 10 the Tur's hall, 3 the Shepherd's chamber, 2 the
     * Rift. Read only to give such a heart its time ({@link #freezeTicks}).
     */
    private int chill = 10;
    /**
     * Ticks out of a hearth's warmth before one is FROZEN THROUGH and its bite starts: the Tur's hall 140, the Shepherd's chamber 160, the Rift 180 ("Freeze",
     * from the citadel's generator). 0: not set - an old world's heart, given its time by its mode and its chill.
     */
    private int freeze = 0;
    private int hits;
    /** The wedge in it (the Rift's): ticks it has been splitting, or -1. */
    private int splitting = -1;
    private static final int SPLIT_TICKS = 30;
    /** The advancement its end earns those near ("Feat", from the citadel's generator: the Heart's hall's). */
    private String feat = "";
    private boolean done;
    private boolean exposed;
    private long lastHit;
    private int clock;
    /** How long somebody has been in its room without a break, and the ticks to the maws' next shot. */
    private int occupied, mawWait = MAW_EVERY;
    private boolean scanned;
    private final List<BlockPos> hearths = new ArrayList<>();
    private final List<BlockPos> cannons = new ArrayList<>();
    /** How long each one in its room has been frozen through, in ticks (its bite grows with it: {@link #bite}). */
    private final Map<UUID, Integer> frozenFor = new HashMap<>();
    /**
     * How deep its cold is in each one in its room, in ticks of frost - ITS OWN: not the game's freezing, which the
     * creatures' blows and frostbite fill and the frost-warding gear empties; neither touches this, nor this them.
     */
    private final Map<UUID, Integer> creep = new HashMap<>();
    /** Ticks of its frost at which one is frozen through, unless it says otherwise ({@link #freezeTicks}). */
    public static final int FROZEN_THROUGH = 140;
    /** Seconds frozen through in which its bite doubles, and its first bite (health). */
    private static final float BITE_DOUBLES = 2.5F, BITE_FIRST = 0.5F;
    /** Seconds frozen through from which its name in the effect list says it will kill (level III). */
    private static final int BITE_DEADLY = 6;

    public FrostHeartBlockEntity(BlockPos pos, BlockState state) {
        super(FFBlockEntities.FROST_HEART.get(), pos, state);
    }

    /** Its time to freeze one through, in ticks (see {@link #freeze}). */
    public int freezeTicks() {
        if (freeze > 0) {
            return freeze;
        }
        if (MODE_HAND.equals(mode)) {
            return 180;                                                  // the Rift
        }
        return MODE_KEEPER.equals(mode) && chill < 10 ? 160 : FROZEN_THROUGH;   // the Shepherd's; the Tur's
    }

    /** The room of the keeper's heart that holds `at` (the Shade Shepherd's chamber), or null. */
    @javax.annotation.Nullable
    public static AABB keeperRoom(Level level, BlockPos at) {
        Vec3 c = Vec3.atCenterOf(at);
        for (FrostHeartBlockEntity h : new ArrayList<>(SERVER)) {
            if (!h.isRemoved() && h.level == level && MODE_KEEPER.equals(h.mode) && h.room().contains(c)) {
                return h.room();
            }
        }
        return null;
    }

    /** Does it still hold its room - its cold, its gates, its ban on building? */
    public boolean active() {
        return !done;
    }

    public boolean exposed() {
        return exposed;
    }

    public AABB room() {
        BlockPos p = worldPosition;
        return new AABB(p.getX() + roomBox[0], p.getY() + roomBox[1], p.getZ() + roomBox[2],
                p.getX() + roomBox[3], p.getY() + roomBox[4], p.getZ() + roomBox[5]);
    }

    public boolean inRoom(BlockPos pos) {
        return room().contains(Vec3.atCenterOf(pos));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            SERVER.add(this);
        } else if (level != null) {
            CLIENT.add(this);
        }
    }

    /** (Client) Is a beating heart's cold on whoever stands at `at`? */
    public static boolean coldAt(Level level, Vec3 at) {
        for (FrostHeartBlockEntity h : CLIENT) {
            if (!h.isRemoved() && !h.done && h.level == level && h.room().contains(at)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setRemoved() {
        SERVER.remove(this);
        CLIENT.remove(this);
        super.setRemoved();
    }

    /** Its room's hearths and maws, found once. */
    private void scan(Level level) {
        scanned = true;
        hearths.clear();
        cannons.clear();
        // (the maws sit IN the walls, just outside the room's box)
        AABB r = room().inflate(2.0D);
        for (BlockPos q : BlockPos.betweenClosed((int) Math.floor(r.minX), (int) Math.floor(r.minY), (int) Math.floor(r.minZ),
                (int) Math.floor(r.maxX) - 1, (int) Math.floor(r.maxY) - 1, (int) Math.floor(r.maxZ) - 1)) {
            BlockState s = level.getBlockState(q);
            if (s.getBlock() instanceof CampfireBlock) {
                hearths.add(q.immutable());
            } else if (level.getBlockEntity(q) instanceof FrostCannonBlockEntity) {
                cannons.add(q.immutable());
            }
        }
    }

    private boolean lit(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT);
    }

    private static boolean counts(Player p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FrostHeartBlockEntity heart) {
        if (!(level instanceof ServerLevel s) || heart.done) {
            return;
        }
        SERVER.add(heart);
        if (!heart.scanned) {
            heart.scan(level);
        }
        heart.clock++;
        if (heart.splitting >= 0) {                           // the wedge going in
            heart.splitting++;
            if (heart.splitting % 8 == 0) {
                s.playSound(null, pos, FFSounds.ICE_CRACK.get(), SoundSource.BLOCKS, 1.6F,
                        0.6F + heart.splitting / (float) SPLIT_TICKS);
                s.sendParticles(com.jastkub.frozenfortress.registry.FFParticles.ICE_SHARD.get(), pos.getX() + 0.5D,
                        pos.getY() + 0.8D, pos.getZ() + 0.5D, 6 + heart.splitting / 4, 0.3D, 0.4D, 0.3D, 0.12D);
            }
            if (heart.splitting >= SPLIT_TICKS) {
                heart.splitting = -1;
                heart.end();
                return;
            }
        }
        AABB room = heart.room();
        List<Player> inside = new ArrayList<>();
        for (Player p : s.players()) {
            if (counts(p) && room.contains(p.position())) {
                inside.add(p);
            }
        }
        if (inside.isEmpty()) {
            heart.occupied = 0;                          // an empty room: the maws are back at their slowest
            heart.mawWait = MAW_EVERY;
        } else {
            heart.occupied++;
        }
        for (Player p : inside) {
            // THE COLD: its own frost in you, a tick of it a tick, to its time (freezeTicks); a hearth thaws it two a
            // tick
            boolean warm = false;
            for (BlockPos h : heart.hearths) {
                if (p.distanceToSqr(Vec3.atCenterOf(h)) < 4.5D * 4.5D && heart.lit(level, h)) {
                    warm = true;
                    break;
                }
            }
            UUID id = p.getUUID();
            int deep = heart.creep.getOrDefault(id, 0);
            if (warm) {
                p.removeEffect(com.jastkub.frozenfortress.registry.FFEffects.HEART_FROST);
                heart.frozenFor.remove(id);
                heart.creep.put(id, Math.max(0, deep - 2));
            } else {
                // (ZAR OGNISKA: 15% a level of the ticks the cold does not get in)
                int ember = com.jastkub.frozenfortress.registry.FFEnchantments.level(
                        com.jastkub.frozenfortress.registry.FFEnchantments.HEARTH_EMBER, p);
                deep = Math.min(heart.freezeTicks() + 60, deep + ((heart.clock % 20) < 3 * ember ? 0 : 1));
                heart.creep.put(id, deep);
                // THE BITE GROWS: nothing while
                // the frost creeps; frozen through, a bite a second that doubles every two and a half - half a
                // heart, then a heart, two, four... some nine seconds of it kill a man in no armour. A hearth (or
                // leaving) ends it, and it starts again from nothing.
                int frozen = 0;
                boolean through = deep >= heart.freezeTicks();
                if (through) {
                    frozen = heart.frozenFor.merge(id, 1, Integer::sum);
                    if (frozen % 20 == 0) {
                        p.hurt(com.jastkub.frozenfortress.FFDamage.heartFrost(level), bite(frozen / 20));
                    }
                } else {
                    heart.frozenFor.remove(id);
                }
                // its name in the effect list: I while it creeps, II once frozen through, III once it kills
                // (HeartFrostEffect)
                int stage = !through ? 0 : frozen / 20 >= BITE_DEADLY ? 2 : 1;
                p.addEffect(new MobEffectInstance(com.jastkub.frozenfortress.registry.FFEffects.HEART_FROST, 40,
                        stage, false, false, true));
            }
            // and how deep it is, for the frost at the rim of his screen (ColdOverlay)
            if (heart.clock % 2 == 0 && p instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.jastkub.frozenfortress.network.FFNetwork.send(sp, new com.jastkub.frozenfortress.network.HeartColdPacket(
                        Math.min(1.0F, heart.creep.getOrDefault(id, 0) / (float) heart.freezeTicks())));
            }
            // THE SPIKES (the Rift): whoever is down on its floor
            // (a heart a second: the fall onto their points is the real hurt - this is to get you up the ladder)
            if (heart.pit != NO_PIT && p.getY() < pos.getY() + heart.pit + 1.3D && heart.clock % 20 == 0) {
                p.hurt(s.damageSources().stalagmite(), 2.0F);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
            }
        }
        // whoever has left its room starts from nothing next time - and his screen clears
        heart.frozenFor.keySet().removeIf(id -> inside.stream().noneMatch(p -> p.getUUID().equals(id)));
        heart.creep.keySet().removeIf(id -> {
            if (inside.stream().anyMatch(p -> p.getUUID().equals(id))) {
                return false;
            }
            heart.release(s, id);
            return true;
        });
        if (MODE_FIRES.equals(heart.mode)) {
            boolean all = !heart.hearths.isEmpty();
            for (BlockPos h : heart.hearths) {
                all &= heart.lit(level, h);
            }
            if (all != heart.exposed) {
                heart.exposed = all;
                heart.sync();
                s.playSound(null, pos, all ? FFSounds.FROST_RELEASE.get() : FFSounds.FROST_CHARGE.get(),
                        SoundSource.BLOCKS, 2.0F, all ? 0.7F : 1.2F);
            }
        } else if (!inside.isEmpty() && --heart.mawWait <= 0 && !heart.cannons.isEmpty()) {
            heart.mawWait = Math.max(MAW_FASTEST, MAW_EVERY - MAW_STEP * (heart.occupied / MAW_RAMP));
            // A MAW SPITS: one that can see somebody, at him
            Player mark = inside.get(s.random.nextInt(inside.size()));
            List<BlockPos> order = new ArrayList<>(heart.cannons);
            Collections.shuffle(order, new java.util.Random(s.random.nextLong()));
            for (BlockPos c : order) {
                if (level.getBlockEntity(c) instanceof FrostCannonBlockEntity maw && !maw.busy()) {
                    Vec3 from = maw.muzzle();
                    HitResult hit = level.clip(new ClipContext(from, mark.getEyePosition(), ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, mark));
                    if (hit.getType() == HitResult.Type.MISS) {
                        maw.charge(mark);
                        break;
                    }
                }
            }
        }
        if (heart.clock % 30 == 0) {
            s.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 0.5F);   // it beats
            // AND IT BREATHES: on every third beat a breath of cold goes up off it - the hearths' frost-blue puff, a thing
            // with a body (ForgeOverseerSteamEntity.puff), rising and thinning - with a soft hiss; only for whoever is near
            if (heart.clock % 90 == 0 && s.getNearestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    24.0D, false) != null) {
                net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atBottomCenterOf(pos).add(0.0D, 0.9D, 0.0D);
                s.addFreshEntity(new com.jastkub.frozenfortress.entity.ForgeOverseerSteamEntity(s, at).puff());
                s.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25F,
                        1.5F + s.random.nextFloat() * 0.2F);
            }
        }
    }

    /** (A keeper's) Its keeper is dead where `at` is: the heart that beat for him in that room breaks. */
    public static void keeperFell(Level level, Vec3 at) {
        if (level.isClientSide) {
            return;
        }
        for (FrostHeartBlockEntity h : new ArrayList<>(SERVER)) {
            if (!h.isRemoved() && !h.done && MODE_KEEPER.equals(h.mode) && h.level == level && h.room().contains(at)) {
                h.end();
                if (!h.feat.isEmpty() && level instanceof ServerLevel sl) {
                    com.jastkub.frozenfortress.FFAdvancements.grantNearby(sl, h.worldPosition, 40.0D, h.feat, "smashed");
                }
            }
        }
    }

    /** Its bite, `seconds` frozen through: half a heart at first, doubling every two and a half seconds. */
    static float bite(int seconds) {
        return (float) Math.min(1000.0D, BITE_FIRST * Math.pow(2.0D, seconds / BITE_DOUBLES));
    }

    /** (The Rift's) A hand laid on it no longer puts it out: only the wedge splits it. */
    public void putOut(Player by) {
        if (done || level == null || level.isClientSide) {
            return;
        }
        by.displayClientMessage(Component.translatable(MODE_HAND.equals(mode)
                ? "message.frozen_dominion.heart_wedge" : "message.frozen_dominion.heart_keeper"), true);
    }

    /**
     * (The Rift's) The Crownbreaker driven into it: it splits over two seconds and goes out. True if the wedge took -
     * a keeper's heart and the Heart's hall's only die with their keeper.
     */
    public boolean wedge(Player by) {
        if (done || splitting >= 0 || level == null || level.isClientSide) {
            return false;
        }
        if (!MODE_HAND.equals(mode)) {
            by.displayClientMessage(Component.translatable("message.frozen_dominion.heart_keeper"), true);
            return false;
        }
        splitting = 0;
        level.playSound(null, worldPosition, FFSounds.ICE_GRIND.get(), SoundSource.BLOCKS, 2.0F, 0.8F);
        by.displayClientMessage(Component.translatable("message.frozen_dominion.heart_out"), true);
        if (by instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.jastkub.frozenfortress.FFAdvancements.grant(sp, "rift", "heart_out");
        }
        setChanged();
        return true;
    }

    /** (The Heart's hall's) A blow: only once it lies open - the hearths all burning. */
    public void strike(Player by) {
        if (done || !MODE_FIRES.equals(mode) || level == null || level.isClientSide) {
            return;
        }
        if (!exposed) {
            level.playSound(null, worldPosition, SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 1.0F, 0.6F);
            by.displayClientMessage(Component.translatable("message.frozen_dominion.heart_shut"), true);
            return;
        }
        if (level.getGameTime() - lastHit < 8) {
            return;
        }
        lastHit = level.getGameTime();
        hits++;
        level.playSound(null, worldPosition, FFSounds.ICE_CRACK.get(), SoundSource.BLOCKS, 1.6F, 0.8F + hits * 0.1F);
        if (hits >= hitsToBreak) {
            end();
            if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                com.jastkub.frozenfortress.FFAdvancements.grantNearby(sl, worldPosition, 40.0D, "heart_hall", "smashed");
            }
        }
        setChanged();
    }

    private void end() {
        done = true;
        exposed = false;
        level.playSound(null, worldPosition, FFSounds.ICE_SHATTER.get(), SoundSource.BLOCKS, 2.5F, 0.6F);
        level.playSound(null, worldPosition, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 2.0F, 0.5F);
        // and its cold lets go of everyone in the room at once
        for (Player p : level.players()) {
            if (room().contains(p.position())) {
                p.removeEffect(com.jastkub.frozenfortress.registry.FFEffects.HEART_FROST);
            }
        }
        if (level instanceof ServerLevel s) {
            for (UUID id : creep.keySet()) {
                release(s, id);
            }
        }
        creep.clear();
        frozenFor.clear();
        sync();
    }

    /** Its cold is out of this one: his screen clears (ColdOverlay). */
    private void release(ServerLevel s, UUID id) {
        if (s.getPlayerByUUID(id) instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.jastkub.frozenfortress.network.FFNetwork.send(sp, new com.jastkub.frozenfortress.network.HeartColdPacket(0.0F));
        }
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Mode")) mode = tag.getString("Mode");
        if (tag.contains("RoomBox")) roomBox = tag.getIntArray("RoomBox");
        pit = tag.contains("Pit") ? tag.getInt("Pit") : NO_PIT;
        if (tag.contains("Hits")) hitsToBreak = tag.getInt("Hits");
        if (tag.contains("Chill")) chill = tag.getInt("Chill");
        freeze = tag.getInt("Freeze");
        feat = tag.getString("Feat");
        splitting = tag.contains("Splitting") ? tag.getInt("Splitting") : -1;
        hits = tag.getInt("Struck");
        done = tag.getBoolean("Done");
        exposed = tag.getBoolean("Exposed");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Mode", mode);
        tag.putIntArray("RoomBox", roomBox);
        if (pit != NO_PIT) {
            tag.putInt("Pit", pit);
        }
        tag.putInt("Hits", hitsToBreak);
        tag.putInt("Chill", chill);
        tag.putInt("Freeze", freeze);
        tag.putString("Feat", feat);
        tag.putInt("Splitting", splitting);
        tag.putInt("Struck", hits);
        tag.putBoolean("Done", done);
        tag.putBoolean("Exposed", exposed);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** (1.21) the renderer asks for this: its getRenderBoundingBox(be). */
    public AABB renderBox() {
        return new AABB(worldPosition).inflate(1.0D, 1.0D, 1.0D).expandTowards(0.0D, 1.5D, 0.0D);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "heart", 4,
                s -> s.setAndContinue(done ? SHATTER : exposed ? EXPOSED : BEAT)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
