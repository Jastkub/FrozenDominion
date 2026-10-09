package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/**
 * A KEEPER IS WHOLE AGAIN WHEN NOBODY IS LEFT TO FIGHT IT. The court's keepers and the watchtower's Lamplighter - not the Ice
 * Monstrosity, not the king - go back to how the citadel left them once whoever fought them is dead or gone: full
 * health, first phase, asleep at their post. Die to the Turnkey at his last ring and he is the whole Turnkey again
 * when you come back down - the soulslike rule.
 *
 * <p>HOW: the first time one is seen, where it stands and which way it looks are written on it - the citadel puts
 * them there asleep, so that is their post. Once it has been in a fight (hurt, or after somebody) and no player who
 * can be in one (alive, not creative, not a spectator) has been within {@link #REACH} of its post for
 * {@link #AWAY} ticks, it is taken away - discarded, nothing dropped - and a fresh one of its kind is put at its post,
 * asleep (Dormant, as the citadel's templates write them). A fresh one is reset in everything at once, whatever its
 * class keeps: no phase flag or charge count of the last fight survives.
 *
 * <p>What the fight did to the hall stays as it was: hearths lit, statues that have risen, cracked floors. Only the
 * keeper is whole.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KeeperReset {

    /** Who resets. (The Ice Monstrosity and Velkhar keep their wounds) */
    public static final Set<String> KEEPERS = Set.of(
            "frozen_dominion:turnkey", "frozen_dominion:shade_shepherd", "frozen_dominion:rime_priestess",
            "frozen_dominion:forge_overseer", "frozen_dominion:ice_aurochs", "frozen_dominion:drowned_lady",
            "frozen_dominion:lamplighter", "frozen_dominion:bone_lord");
    /** How near its post a fighter has to be for it not to be alone, horizontally and up or down. */
    private static final double REACH = 40.0D, REACH_Y = 24.0D;
    /** How long it has to be alone before it is whole again (after a death the screen alone takes longer). */
    private static final int AWAY = 100;

    /** Which way it faced at its post (read by BossCutscenes.postYaw: its entrance is played facing that way). */
    public static final String POST_YAW = "ffPostYaw";
    private static final String HOME_X = "ffPostX", HOME_Y = "ffPostY", HOME_Z = "ffPostZ", HOME_YAW = POST_YAW;
    private static final String FOUGHT = "ffFought", ALONE = "ffAlone";
    /** The citadel's own words on a keeper, carried to the fresh one. */
    private static final Set<String> CARRY = Set.of("Arena");

    private KeeperReset() {
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.tickCount % 20 != 0 || !(e.level() instanceof ServerLevel s)) {
            return;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (id == null || !KEEPERS.contains(id.toString())) {
            return;
        }
        CompoundTag d = e.getPersistentData();
        if (!d.contains(HOME_X)) {                       // its post: where the citadel set it down
            d.putDouble(HOME_X, e.getX());
            d.putDouble(HOME_Y, e.getY());
            d.putDouble(HOME_Z, e.getZ());
            d.putFloat(HOME_YAW, e.getYRot());
        }
        if (e.isDeadOrDying()) {
            return;
        }
        Vec3 post = new Vec3(d.getDouble(HOME_X), d.getDouble(HOME_Y), d.getDouble(HOME_Z));
        // A KEEPER WITH A HALL OF ITS OWN - the Bone Lord's chasm, a hundred and twenty long - is alone only when nobody
        // is in that hall
        net.minecraft.world.phys.AABB hall = e instanceof com.jastkub.frozenfortress.entity.BoneLordEntity lord
                ? lord.arenaBox() : null;
        boolean someone = false;
        for (ServerPlayer p : s.players()) {
            double dx = p.getX() - post.x, dz = p.getZ() - post.z;
            if (VelkharEntity.inTheFight(p) && (hall != null ? hall.inflate(4.0D).contains(p.position())
                    : dx * dx + dz * dz <= REACH * REACH && Math.abs(p.getY() - post.y) <= REACH_Y)) {
                someone = true;
                break;
            }
        }
        if (someone) {
            if (e.getHealth() < e.getMaxHealth() || e instanceof Mob m && m.getTarget() != null) {
                d.putBoolean(FOUGHT, true);
            }
            d.putInt(ALONE, 0);
            return;
        }
        if (!d.getBoolean(FOUGHT)) {
            return;
        }
        int alone = d.getInt(ALONE) + 20;
        d.putInt(ALONE, alone);
        if (alone >= AWAY) {
            renew(s, e, id, post, d.getFloat(HOME_YAW));
        }
    }

    /** Take it away and put a fresh one of its kind at its post, asleep. */
    private static void renew(ServerLevel s, LivingEntity old, ResourceLocation id, Vec3 post, float yaw) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id.toString());
        tag.putBoolean("PersistenceRequired", true);
        tag.putBoolean("Dormant", true);
        // what the citadel wrote on it besides (the Bone Lord's Arena, relative to his post - without it the fresh one
        // knew no hall: no fog over the chasm, nobody in it to wake for)
        CompoundTag was = old.saveWithoutId(new CompoundTag());
        for (String k : CARRY) {
            if (was.contains(k)) {
                tag.put(k, was.get(k).copy());
            }
        }
        Entity fresh = EntityType.loadEntityRecursive(tag, s, e -> {
            e.moveTo(post.x, post.y, post.z, yaw, 0.0F);
            return e;
        });
        if (fresh == null) {
            return;
        }
        if (fresh instanceof LivingEntity l) {
            l.setYHeadRot(yaw);
            l.yBodyRot = yaw;
        }
        CompoundTag d = fresh.getPersistentData();
        d.putDouble(HOME_X, post.x);
        d.putDouble(HOME_Y, post.y);
        d.putDouble(HOME_Z, post.z);
        d.putFloat(HOME_YAW, yaw);
        // the same keeper to its scenes: its entrance is not shown again (BossCutscenes.sceneId)
        d.putUUID(com.jastkub.frozenfortress.BossCutscenes.SCENE, com.jastkub.frozenfortress.BossCutscenes.sceneId(old));
        old.discard();
        s.addFreshEntity(fresh);
        com.mojang.logging.LogUtils.getLogger().info("[keeper] {} whole again at its post {}", id, post);
    }
}
