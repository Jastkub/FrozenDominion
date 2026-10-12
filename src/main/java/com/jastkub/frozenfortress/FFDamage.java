package com.jastkub.frozenfortress;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

/** The mod's own kinds of hurt (data/frozen_dominion/damage_type). */
public final class FFDamage {

    /**
     * A trap room's heart's cold (FrostHeartBlockEntity - Odmrozenie): its own, not the game's freezing, so nothing
     * that wards off freezing or the creatures' frostbite wards it off - only a hearth does. Through armour.
     */
    public static final ResourceKey<DamageType> HEART_FROST =
            ResourceKey.create(Registries.DAMAGE_TYPE, FrozenFortress.id("heart_frost"));

    /**
     * DASHED DOWN IN THE BONE LORD'S FIST (BoneLordEntity's slam): the end of whoever he holds, and nothing of their
     * armour with it. Through armour, enchantments and a
     * shield; a totem still answers it.
     */
    public static final ResourceKey<DamageType> BONE_LORD_FIST =
            ResourceKey.create(Registries.DAMAGE_TYPE, FrozenFortress.id("bone_lord_fist"));

    /**
     * THE CHASM OF BONES' FLOOR: the spikes at the foot of the shaft under the Bone Lord's bridges. A fall into it is
     * the end, whatever is worn - through armour, enchantments, resistance, and no totem answers it.
     */
    public static final ResourceKey<DamageType> CHASM_SPIKES =
            ResourceKey.create(Registries.DAMAGE_TYPE, FrozenFortress.id("chasm_spikes"));

    private FFDamage() {
    }

    public static DamageSource boneLordFist(Level level, net.minecraft.world.entity.Entity lord) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(BONE_LORD_FIST), lord);
    }

    public static DamageSource chasmSpikes(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(CHASM_SPIKES));
    }

    public static DamageSource heartFrost(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(HEART_FROST));
    }
}
