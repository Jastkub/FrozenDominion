package com.jastkub.frozenfortress;

import com.jastkub.frozenfortress.entity.DrownedLadyEntity;
import com.jastkub.frozenfortress.entity.ForgeOverseerEntity;
import com.jastkub.frozenfortress.entity.IceAurochsEntity;
import com.jastkub.frozenfortress.entity.RimePriestessEntity;
import com.jastkub.frozenfortress.entity.ShadeShepherdEntity;
import com.jastkub.frozenfortress.entity.TurnkeyEntity;
import com.jastkub.frozenfortress.network.BossScenePacket;
import com.jastkub.frozenfortress.network.FFNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * THE BOSSES' SCENES. When a boss wakes it calls {@link #play} with INTRO, and when it dies the death
 * handler calls it with DEATH (DEATH_TICKS says which bosses have one, and for how long).
 *
 * <p>Each player within 40 blocks who has not yet seen that boss's scene of that kind is sent it (BossScenes on his
 * client: the camera on the boss, the letterbox, the name card) and it is written into his persisted data - a death
 * does not make him see it again. While it plays he cannot be hurt, and the hostile things round the boss are held
 * still: a scene is not a trap.
 */
public final class BossCutscenes {

    public static final byte INTRO = 1, DEATH = 2;
    private static final String TAG = "frozen_dominion_scenes";
    /** How long each boss's death scene runs: its death clip, and a breath for the card. (The minibosses' deaths are
     *  films since 08.10.2026 - BossScenes.Film.death - each its clip (DEATH_T, which starts DEATH_LAG into the death:
     *  the animation controller's blend) and twelve ticks more; their bodies lie until the scene goes to black.) */
    private static final Map<String, Integer> DEATH_TICKS = Map.of(
            "frozen_dominion:turnkey", deathScene(TurnkeyEntity.DEATH_T, TurnkeyEntity.DEATH_LAG),
            "frozen_dominion:rime_priestess", deathScene(RimePriestessEntity.DEATH_T, RimePriestessEntity.DEATH_LAG),
            "frozen_dominion:lamplighter", 72,
            "frozen_dominion:drowned_lady", deathScene(DrownedLadyEntity.DEATH_T, DrownedLadyEntity.DEATH_LAG),
            "frozen_dominion:ice_aurochs", deathScene(IceAurochsEntity.DEATH_T, IceAurochsEntity.DEATH_LAG),
            "frozen_dominion:shade_shepherd", deathScene(ShadeShepherdEntity.DEATH_T, ShadeShepherdEntity.DEATH_LAG),
            "frozen_dominion:forge_overseer", deathScene(ForgeOverseerEntity.DEATH_T, ForgeOverseerEntity.DEATH_LAG),
            "frozen_dominion:bone_lord", 70,
            // her death clip, to the shattering
            "frozen_dominion:ice_monstrosity", 124);
    /** Every scene longer than its clip - the card has time to be read, and
     *  the boss's spoken line (BossScenes) time to be said whole. */
    private static final int LINGER = 30;
    /** Where a keeper made whole again keeps the name of the one it took over from (sceneId). */
    public static final String SCENE = "ffScene";
    /** Players a scene is holding, until the game tick given. */
    private static final Map<UUID, Long> HELD = new HashMap<>();

    private BossCutscenes() {
    }

    /** A miniboss's death scene: its clip run out (from DEATH_LAG in) and twelve ticks of it lying there. */
    private static int deathScene(int clip, int lag) {
        return clip + lag + 12;
    }

    /** The death scene's length for this boss, or 0 for one that has none. */
    public static int deathTicks(LivingEntity boss) {
        if (boss instanceof com.jastkub.frozenfortress.entity.HollowGolemEntity g && g.isTamed()) {
            return 0;                                    // a tamed one's end is no boss's
        }
        Integer t = DEATH_TICKS.get(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(boss.getType())));
        return t == null ? 0 : t;
    }

    /**
     * DOES `p` SEE IT:
     * nothing solid between them - no floor, no vault, no wall. Four sight lines (its head and its middle to their eyes
     * and their feet), so a pillar between them in its own hall does not hide it; any one clear is enough. The reach is
     * each boss's own and untouched - this only asks that the one in reach is in its hall and not under it.
     */
    public static boolean witness(LivingEntity boss, Player p) {
        // IN ITS HALL IS ENOUGH: a column between the Aurochs and whoever came in, a pillar between the Priestess and her guest -
        // and no scene. Whoever is in its own hall sees it, whatever stands between; the sight lines below are only
        // for those outside it (under it in the caves, over it)
        if (com.jastkub.frozenfortress.block.entity.BossGateBlockEntity.inHallOf(boss, p)) {
            return true;
        }
        net.minecraft.world.phys.Vec3 head = boss.position().add(0.0D, Math.max(1.0D, boss.getBbHeight() * 0.8D), 0.0D);
        net.minecraft.world.phys.Vec3 mid = boss.position().add(0.0D, 0.6D, 0.0D);
        net.minecraft.world.phys.Vec3[] to = {p.getEyePosition(), p.position().add(0.0D, 0.3D, 0.0D)};
        for (net.minecraft.world.phys.Vec3 f : new net.minecraft.world.phys.Vec3[]{head, mid}) {
            for (net.minecraft.world.phys.Vec3 t : to) {
                if (boss.level().clip(new net.minecraft.world.level.ClipContext(f, t,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, boss)).getType()
                        == net.minecraft.world.phys.HitResult.Type.MISS) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * THE NAME ITS SCENES ARE KEPT UNDER: its own - or, for a keeper made whole again after a wipe (KeeperReset: a fresh one put at its post), the
     * name of the first one at that post, carried over each time. So its entrance is seen once, at the first meeting,
     * however many times it is fought again.
     */
    public static java.util.UUID sceneId(LivingEntity boss) {
        CompoundTag d = boss.getPersistentData();
        return d.hasUUID(SCENE) ? d.getUUID(SCENE) : boss.getUUID();
    }

    public static void play(LivingEntity boss, byte kind, int ticks) {
        if (ticks > 0) {
            send(boss, kind, ticks + LINGER);
        }
    }

    /**
     * AN ENTRANCE SHOT AS A FILM: exactly `ticks` long - the keeper's
     * own "intro" clip runs the whole of it, so nothing lingers after it - and true if anybody was shown it. When
     * nobody was (all in reach have seen this keeper's entrance), the keeper wakes as it always did, short.
     * BossScenes on each client shoots it: its shots, cuts and card for that keeper.
     */
    public static boolean intro(LivingEntity boss, int ticks) {
        return ticks > 0 && send(boss, INTRO, ticks);
    }

    /** Which way a keeper faces at its post (KeeperReset wrote it down the first time it was seen), or its own yaw. */
    public static float postYaw(LivingEntity boss) {
        CompoundTag d = boss.getPersistentData();
        return d.contains(com.jastkub.frozenfortress.event.KeeperReset.POST_YAW)
                ? d.getFloat(com.jastkub.frozenfortress.event.KeeperReset.POST_YAW) : boss.getYRot();
    }

    /**
     * THROUGH ITS ENTRANCE A KEEPER STANDS AT ITS POST, turned the way its clip was made for (the Overseer's bench
     * before him), its head straight on its shoulders: nothing of the game's walking, looking or turning moves it.
     * Called as the scene starts (so the frame BossScenes shoots in is the clip's) and every tick of it.
     */
    public static void holdPose(Mob boss, float yaw) {
        boss.getNavigation().stop();
        boss.setDeltaMovement(0.0D, boss.getDeltaMovement().y, 0.0D);
        boss.setYRot(yaw);
        boss.yRotO = yaw;
        boss.setYBodyRot(yaw);
        boss.yBodyRotO = yaw;
        boss.setYHeadRot(yaw);
        boss.yHeadRotO = yaw;
        boss.setXRot(0.0F);
    }

    /**
     * A PLACE'S SCENES: films with no keeper in
     * them, their length here and their shots in BossScenes (Film.of: laid out about where they are anchored).
     */
    public static final Map<String, Integer> PLACE_TICKS = Map.of("stormcrown_taken", 220, "rift", 230);

    /**
     * A place's scene, once for each player `to` takes who has not seen it here: anchored at `at`, its frame turned
     * `yaw` (the way its shots were laid out), the player held through it and the court's creatures about it stilled,
     * as for a keeper's entrance.
     */
    public static void placeScene(ServerLevel s, String path, net.minecraft.world.phys.Vec3 at, float yaw,
                                  java.util.function.Predicate<ServerPlayer> to) {
        Integer ticks = PLACE_TICKS.get(path);
        if (ticks == null) {
            return;
        }
        String key = "intro:" + FrozenFortress.MODID + ":" + path + "@" + net.minecraft.core.BlockPos.containing(at).asLong();
        long until = s.getGameTime() + ticks;
        boolean any = false;
        for (ServerPlayer p : s.players()) {
            if (p.isSpectator() || !p.isAlive() || !to.test(p)) {
                continue;
            }
            CompoundTag kept = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            CompoundTag seen = kept.getCompound(TAG);
            if (seen.getBoolean(key)) {
                continue;
            }
            seen.putBoolean(key, true);
            kept.put(TAG, seen);
            p.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
            HELD.put(p.getUUID(), until);
            FFNetwork.send(p, new BossScenePacket(-1, INTRO, (short) (int) ticks, FrozenFortress.MODID + ":" + path,
                    at.x, at.y, at.z, yaw));
            any = true;
        }
        if (any) {
            for (Mob m : s.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(at, at).inflate(48.0D),
                    m -> m instanceof Enemy && m.isAlive())) {
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false));
                m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 9, false, false));
            }
        }
    }

    /** The scene to everyone in reach who has not seen it; true if anybody was sent it. */
    private static boolean send(LivingEntity boss, byte kind, int ticks) {
        if (!(boss.level() instanceof ServerLevel s) || ticks <= 0) {
            return false;
        }
        String type = String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(boss.getType()));
        // once for each player and each boss - not each KIND of boss
        String key = (kind == INTRO ? "intro:" : "death:") + type + "@" + sceneId(boss);
        long until = s.getGameTime() + ticks;
        boolean any = false;
        for (ServerPlayer p : s.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(40.0D))) {
            if (p.isSpectator() || !witness(boss, p)) {
                continue;                                  // (in reach, but not where it can be seen: under it, over it)
            }
            CompoundTag kept = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            CompoundTag seen = kept.getCompound(TAG);
            if (seen.getBoolean(key)) {
                continue;
            }
            seen.putBoolean(key, true);
            kept.put(TAG, seen);
            p.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
            HELD.put(p.getUUID(), until);
            // (where it stands and which way it faces: the frame its shots are laid out in - BossScenes)
            FFNetwork.send(p, new BossScenePacket(boss.getId(), kind, (short) ticks, type, boss.getX(), boss.getY(),
                    boss.getZ(), boss.yBodyRot));
            any = true;
        }
        if (any) {
            for (Mob m : s.getEntitiesOfClass(Mob.class, boss.getBoundingBox().inflate(32.0D),
                    m -> m != boss && m instanceof Enemy && m.isAlive())) {
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false));
                m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 9, false, false));
            }
        }
        return any;
    }

    /** Until when (game time) a scene holds this player; 0 if none. */
    public static long heldUntil(Player p) {
        Long until = HELD.get(p.getUUID());
        return until == null ? 0L : until;
    }

    /** Is a scene holding this player (and so nothing may hurt him)? */
    public static boolean held(Player p) {
        Long until = HELD.get(p.getUUID());
        if (until == null) {
            return false;
        }
        if (p.level().getGameTime() > until) {
            HELD.remove(p.getUUID());
            return false;
        }
        return true;
    }
}
