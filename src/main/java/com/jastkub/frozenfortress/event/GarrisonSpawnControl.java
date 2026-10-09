package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.registry.FFTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Keeps the garrison a garrison instead of a crowd.
 *
 * <p>The fortress declares its own spawn list through the structure's
 * {@code spawn_overrides}, which tells the game <em>what</em> may appear but
 * nothing at all about <em>how many</em>. Left alone, the ordinary monster
 * spawn cycle keeps topping the halls up until they are shoulder to shoulder.
 *
 * <p>So the population is capped here instead: a room-sized bubble may hold
 * only a handful, and a hall-sized one only so many more. The hand-placed
 * garrison counts towards both, so a fresh fortress spawns nothing extra at
 * all - it only refills once the player has actually thinned it out.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class GarrisonSpawnControl {

    /** Roughly one chamber. */
    private static final double ROOM_RADIUS = 16.0D;
    private static final int ROOM_CAP = 3;

    /** Roughly one wing of the fortress. */
    private static final double WING_RADIUS = 40.0D;
    private static final int WING_CAP = 10;

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION
                && type != MobSpawnType.STRUCTURE) {
            return;   // spawn eggs, commands and the boss's own summons are exempt
        }

        Mob mob = event.getEntity();
        if (!mob.getType().is(FFTags.GARRISON)) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        if (crowded(level, event, ROOM_RADIUS, ROOM_CAP)
                || crowded(level, event, WING_RADIUS, WING_CAP)) {
            event.setSpawnCancelled(true);
        }
    }

    private static boolean crowded(ServerLevel level, MobSpawnEvent event, double radius, int cap) {
        AABB box = new AABB(event.getX(), event.getY(), event.getZ(),
                event.getX(), event.getY(), event.getZ()).inflate(radius);
        List<Monster> nearby = level.getEntitiesOfClass(Monster.class, box,
                other -> other.isAlive() && other.getType().is(FFTags.GARRISON));
        return nearby.size() >= cap;
    }

    private GarrisonSpawnControl() {
    }
}
