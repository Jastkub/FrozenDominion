package com.jastkub.frozenfortress.registry;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.effect.FrostbiteEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class FFEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, FrozenFortress.MODID);

    public static final RegistryObject<MobEffect> FROSTBITE =
            MOB_EFFECTS.register("frostbite", FrostbiteEffect::new);

    /** The Stormcrown's cold: only the Hearth Amulet keeps it off. */
    public static final RegistryObject<MobEffect> CHILL =
            MOB_EFFECTS.register("chill", com.jastkub.frozenfortress.effect.ChillEffect::new);

    /** The Potion of Warmth's: frostbite kept off while it lasts (WarmthPotionItem, CommonEvents). */
    public static final RegistryObject<MobEffect> WARMTH =
            MOB_EFFECTS.register("warmth", com.jastkub.frozenfortress.effect.WarmthEffect::new);

    /** A trap room's cold, named and shown (FrostHeartBlockEntity lays it on; only a hearth lifts it). */
    /** The Stormcrown's hold on the stone: no block of the citadel breaks (was Mining Fatigue - 08.10.2026). */
    public static final RegistryObject<MobEffect> CROWN_HOLD =
            MOB_EFFECTS.register("crown_hold", com.jastkub.frozenfortress.effect.CrownHoldEffect::new);
    public static final RegistryObject<MobEffect> HEART_FROST =
            MOB_EFFECTS.register("heart_frost", com.jastkub.frozenfortress.effect.HeartFrostEffect::new);

    private FFEffects() {
    }
}
