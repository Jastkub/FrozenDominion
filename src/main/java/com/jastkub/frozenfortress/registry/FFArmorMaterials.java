package com.jastkub.frozenfortress.registry;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

/**
 * The mod's armour materials (the 1.21.1 port: a material is a registry entry now, not an anonymous class). Same
 * numbers as the 1.20.1 classes - defence per piece, enchantability, equip sound, repair item, toughness, knockback
 * resistance - and the same texture name (textures/models/armor/&lt;name&gt;_layer_1/2). What a material no longer
 * holds is durability: each piece gets its own through Item.Properties (DURABILITY below, per piece as it was).
 */
public final class FFArmorMaterials {

    public static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, FrozenFortress.MODID);

    /** Everfrost plate: helmet, chest, legs, boots. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> EVERFROST = MATERIALS.register("everfrost",
            () -> new ArmorMaterial(defense(3, 8, 6, 3), 16, SoundEvents.ARMOR_EQUIP_DIAMOND,
                    () -> Ingredient.of(FFItems.EVERFROST_INGOT.get()),
                    List.of(new ArmorMaterial.Layer(FrozenFortress.id("everfrost"))), 2.5F, 0.05F));

    /** Kingsrime: 22 in all. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> KINGSRIME = MATERIALS.register("kingsrime",
            () -> new ArmorMaterial(defense(3, 9, 7, 3), 18, SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.of(FFItems.KINGSRIME_INGOT.get()),
                    List.of(new ArmorMaterial.Layer(FrozenFortress.id("kingsrime"))), 4.0F, 0.15F));

    /** The Crown of the Hollow King (a helmet; 4 for any piece, as before). */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> HOLLOW_CROWN = MATERIALS.register("hollow_crown",
            () -> new ArmorMaterial(defense(4, 4, 4, 4), 20, SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.of(FFItems.EVERFROST_SHARD.get()),
                    List.of(new ArmorMaterial.Layer(FrozenFortress.id("hollow_crown"))), 3.0F, 0.1F));

    /** Durability per piece, by ArmorItem.Type ordinal (helmet, chest, legs, boots), as 1.20.1's materials gave it. */
    public static final int[] EVERFROST_DURABILITY = {11 * 34, 16 * 34, 15 * 34, 13 * 34};   // helmet, chest, legs, boots (08.10.2026)
    public static final int[] KINGSRIME_DURABILITY = {13 * 40, 18 * 40, 17 * 40, 15 * 40};   // helmet, chest, legs, boots (08.10.2026)
    public static final int HOLLOW_CROWN_DURABILITY = 592;

    private FFArmorMaterials() {
    }

    private static EnumMap<ArmorItem.Type, Integer> defense(int helmet, int chest, int legs, int boots) {
        return Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
            m.put(ArmorItem.Type.HELMET, helmet);
            m.put(ArmorItem.Type.CHESTPLATE, chest);
            m.put(ArmorItem.Type.LEGGINGS, legs);
            m.put(ArmorItem.Type.BOOTS, boots);
        });
    }
}
