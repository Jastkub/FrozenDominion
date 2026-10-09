package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.registry.FFEnchantments;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * What 1.21.1 took out of an arrow's public face, for the mod's bows (the 1.21.1 port). An arrow's piercing and its
 * knockback now come only from the enchantments of the weapon it was fired from: the piercing setter went private and
 * setKnockback is gone. The Kingsrime bow's full-draw arrow still pierces two, and the Last Watch bow's loosed arrow
 * still carries its Punch, so both are set here as before. (1.21.1 runs on Mojang's names, so the members are found by
 * their own names; were one ever not, the arrow simply goes without and the log says so once.)
 */
public final class FFArrows {

    private static final Logger LOG = LogUtils.getLogger();
    private static Method setPierce;
    private static boolean failed;

    private FFArrows() {
    }

    public static void setPierceLevel(AbstractArrow arrow, byte level) {
        if (failed) {
            return;
        }
        try {
            if (setPierce == null) {
                setPierce = AbstractArrow.class.getDeclaredMethod("setPierceLevel", byte.class);
                setPierce.setAccessible(true);
            }
            setPierce.invoke(arrow, level);
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            LOG.error("Frozen Dominion: cannot set an arrow's piercing - the Kingsrime bow's arrows will not pierce", e);
        }
    }

    private static Field weapon;
    private static boolean weaponFailed;

    /**
     * Punch on an arrow loosed by hand (the Last Watch bow's partial draw; 1.20.1's setKnockback). 1.21.1 knocks an
     * arrow's target back by the Punch of the weapon the arrow was fired from, and nothing else; so the arrow is given,
     * as that weapon, a plain bow bearing only this Punch - it adds no damage and no other effect, only the push
     * (the same 0.6 a level, against knockback resistance, along the arrow's flight).
     */
    public static void setPunch(AbstractArrow arrow, int punch) {
        if (punch <= 0 || weaponFailed) {
            return;
        }
        Holder<Enchantment> h = FFEnchantments.holder(Enchantments.PUNCH, arrow.level());
        if (h == null) {
            return;
        }
        ItemStack bow = new ItemStack(Items.BOW);
        bow.enchant(h, punch);
        try {
            if (weapon == null) {
                weapon = AbstractArrow.class.getDeclaredField("firedFromWeapon");
                weapon.setAccessible(true);
            }
            weapon.set(arrow, bow);
        } catch (ReflectiveOperationException | RuntimeException e) {
            weaponFailed = true;
            LOG.error("Frozen Dominion: cannot give an arrow its Punch - the Last Watch bow's arrows will not push", e);
        }
    }
}
