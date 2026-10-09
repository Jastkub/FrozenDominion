package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.registry.FFSounds;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

/**
 * THE SOUND OF OUR BLADES SWUNG. With Better Combat the
 * swords' attacks carry it themselves (weapon_attributes' swing_sound); without it - and for the Crownbreaker, which
 * Better Combat does not wield - the item plays it as the arm goes over. Once a swing, not once a tick: digging with
 * a blade swings it every tick, and that is not a fight.
 */
public final class FFSwing {

    private static final Map<UUID, Long> LAST = new WeakHashMap<>();
    private static final int GAP = 8;
    private static Boolean betterCombat;

    private FFSwing() {
    }

    /** From Item#onEntitySwing. `combat`: Better Combat has attacks for this item, and plays the swing itself. */
    public static void swing(LivingEntity who, SoundEvent sound, boolean combat) {
        if (who.level().isClientSide) {
            return;
        }
        if (combat && betterCombat()) {
            return;
        }
        long now = who.level().getGameTime();
        Long last = LAST.get(who.getUUID());
        if (last != null && now - last < GAP && now >= last) {
            return;
        }
        LAST.put(who.getUUID(), now);
        who.level().playSound(null, who.getX(), who.getEyeY() - 0.4D, who.getZ(), sound, who.getSoundSource(),
                0.85F, 0.92F + who.getRandom().nextFloat() * 0.16F);
    }

    /** Without Better Combat's combo to say which blow this is: one of the sword's strokes, by chance. */
    public static SoundEvent blade() {
        int k = java.util.concurrent.ThreadLocalRandom.current().nextInt(3);
        return (k == 0 ? FFSounds.BLADE_SWING : k == 1 ? FFSounds.BLADE_BACKHAND : FFSounds.BLADE_THRUST).get();
    }

    public static SoundEvent great() {
        int k = java.util.concurrent.ThreadLocalRandom.current().nextInt(4);
        return (k == 0 ? FFSounds.GREAT_SWING : k == 1 ? FFSounds.GREAT_BACKHAND
                : k == 2 ? FFSounds.GREAT_THRUST : FFSounds.GREAT_SLAM).get();
    }

    private static boolean betterCombat() {
        if (betterCombat == null) {
            betterCombat = ModList.get().isLoaded("bettercombat");
        }
        return betterCombat;
    }
}
