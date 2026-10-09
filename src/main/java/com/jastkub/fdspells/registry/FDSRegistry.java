package com.jastkub.fdspells.registry;

import com.jastkub.fdspells.FDSpells;
import com.jastkub.fdspells.entity.AvalancheWaveEntity;
import com.jastkub.fdspells.entity.FallingIcicleEntity;
import com.jastkub.fdspells.entity.FrostHeartEntity;
import com.jastkub.fdspells.entity.FrostJavelinEntity;
import com.jastkub.fdspells.entity.FrostRingEntity;
import com.jastkub.fdspells.entity.FrostShacklesEntity;
import com.jastkub.fdspells.entity.FrostShellEntity;
import com.jastkub.fdspells.entity.IcePillarEntity;
import com.jastkub.fdspells.entity.IceSentinelEntity;
import com.jastkub.fdspells.entity.IceShardEntity;
import com.jastkub.fdspells.entity.IcicleRainEntity;
import com.jastkub.fdspells.entity.IcicleShadowEntity;
import com.jastkub.fdspells.entity.LitanyRuneEntity;
import com.jastkub.fdspells.spells.AvalancheSpell;
import com.jastkub.fdspells.spells.FrostHeartSpell;
import com.jastkub.fdspells.spells.FrostJavelinSpell;
import com.jastkub.fdspells.spells.FrostShacklesSpell;
import com.jastkub.fdspells.spells.FrostShellSpell;
import com.jastkub.fdspells.spells.IceSentinelSpell;
import com.jastkub.fdspells.spells.IcicleRainSpell;
import com.jastkub.fdspells.spells.RuneLitanySpell;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.compat.Curios;
import io.redspace.ironsspellbooks.item.SpellBook;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Every spell, entity and sound of the mod. */
public final class FDSRegistry {

    public static final DeferredRegister<AbstractSpell> SPELLS =
            DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, FDSpells.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FDSpells.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, FDSpells.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FDSpells.MODID);

    /**
     * BREWIARZ SZRONU: the Rime Priestess's own book - ten spell slots and a quarter more power to the Ice school, worn in the
     * spellbook slot. Frozen Dominion's Priestess drops it (loot_modifiers/rime_breviary).
     */
    public static final RegistryObject<Item> RIME_BREVIARY = ITEMS.register("rime_breviary",
            () -> new SpellBook(10, new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant())
                    .withAttributes(Curios.SPELLBOOK_SLOT, new AttributeContainer(AttributeRegistry.ICE_SPELL_POWER,
                            0.25D, AttributeModifier.Operation.MULTIPLY_BASE)));

    // ------------------------------------------------------------------ the spells
    public static final RegistryObject<AbstractSpell> AVALANCHE = SPELLS.register("avalanche", AvalancheSpell::new);
    public static final RegistryObject<AbstractSpell> ICICLE_RAIN = SPELLS.register("icicle_rain", IcicleRainSpell::new);
    public static final RegistryObject<AbstractSpell> FROST_JAVELIN =
            SPELLS.register("frost_javelin", FrostJavelinSpell::new);
    public static final RegistryObject<AbstractSpell> FROST_SHACKLES =
            SPELLS.register("frost_shackles", FrostShacklesSpell::new);
    public static final RegistryObject<AbstractSpell> FROST_HEART = SPELLS.register("frost_heart", FrostHeartSpell::new);
    public static final RegistryObject<AbstractSpell> RUNE_LITANY = SPELLS.register("rune_litany", RuneLitanySpell::new);
    public static final RegistryObject<AbstractSpell> ICE_SENTINEL_SPELL =
            SPELLS.register("ice_sentinel", IceSentinelSpell::new);
    public static final RegistryObject<AbstractSpell> FROST_SHELL = SPELLS.register("frost_shell", FrostShellSpell::new);

    // ------------------------------------------------------------------ what they put in the world
    public static final RegistryObject<EntityType<AvalancheWaveEntity>> AVALANCHE_WAVE =
            fx("avalanche_wave", AvalancheWaveEntity::new, 3.0F, 2.0F, 1);
    public static final RegistryObject<EntityType<IcicleRainEntity>> ICICLE_RAIN_CLOUD =
            fx("icicle_rain", IcicleRainEntity::new, 0.5F, 0.5F, 20);
    public static final RegistryObject<EntityType<IcicleShadowEntity>> ICICLE_SHADOW =
            fx("icicle_shadow", IcicleShadowEntity::new, 1.0F, 0.1F, 20);
    public static final RegistryObject<EntityType<FallingIcicleEntity>> FALLING_ICICLE =
            fx("falling_icicle", FallingIcicleEntity::new, 0.6F, 1.6F, 1);
    public static final RegistryObject<EntityType<FrostJavelinEntity>> FROST_JAVELIN_ENTITY =
            fx("frost_javelin", FrostJavelinEntity::new, 0.4F, 0.4F, 1);
    public static final RegistryObject<EntityType<IcePillarEntity>> ICE_PILLAR =
            fx("ice_pillar", IcePillarEntity::new, 0.9F, 3.0F, 20);
    public static final RegistryObject<EntityType<FrostShacklesEntity>> FROST_SHACKLES_ENTITY =
            fx("frost_shackles", FrostShacklesEntity::new, 1.0F, 1.0F, 1);
    public static final RegistryObject<EntityType<FrostHeartEntity>> FROST_HEART_ENTITY =
            fx("frost_heart", FrostHeartEntity::new, 0.9F, 1.4F, 20);
    public static final RegistryObject<EntityType<FrostRingEntity>> FROST_RING =
            fx("frost_ring", FrostRingEntity::new, 1.0F, 0.4F, 20);
    public static final RegistryObject<EntityType<LitanyRuneEntity>> LITANY_RUNE =
            fx("litany_rune", LitanyRuneEntity::new, 0.5F, 0.5F, 1);
    public static final RegistryObject<EntityType<FrostShellEntity>> FROST_SHELL_ENTITY =
            fx("frost_shell", FrostShellEntity::new, 1.0F, 2.0F, 1);
    public static final RegistryObject<EntityType<IceShardEntity>> ICE_SHARD =
            fx("ice_shard", IceShardEntity::new, 0.4F, 0.4F, 1);
    public static final RegistryObject<EntityType<IceSentinelEntity>> ICE_SENTINEL =
            ENTITIES.register("ice_sentinel", () -> EntityType.Builder.of(IceSentinelEntity::new, MobCategory.MISC)
                    .sized(0.9F, 2.4F).clientTrackingRange(10).build("ice_sentinel"));

    // ------------------------------------------------------------------ their sounds (Frozen Dominion's ice families)
    public static final RegistryObject<SoundEvent> ICE_SHATTER = sound("ice_shatter");
    public static final RegistryObject<SoundEvent> ICE_IMPACT = sound("ice_impact");
    public static final RegistryObject<SoundEvent> FROST_CHARGE = sound("frost_charge");
    public static final RegistryObject<SoundEvent> FROST_RELEASE = sound("frost_release");
    public static final RegistryObject<SoundEvent> ICE_GRIND = sound("ice_grind");
    public static final RegistryObject<SoundEvent> CRYSTAL_CHIME = sound("crystal_chime");
    public static final RegistryObject<SoundEvent> RUNE_FORM = sound("rune_form");
    public static final RegistryObject<SoundEvent> RUNE_FIRE = sound("rune_fire");
    public static final RegistryObject<SoundEvent> RUNE_HIT = sound("rune_hit");
    public static final RegistryObject<SoundEvent> CHAINS = sound("chains");
    public static final RegistryObject<SoundEvent> CHAINS_BREAK = sound("chains_break");
    public static final RegistryObject<SoundEvent> WHOOSH = sound("whoosh");
    /**
     * EACH SPELL'S OWN VOICE: the charge, played as
     * it is cast, and the release, as it goes (tools/gen_cast_sounds.py). By the spell's id.
     */
    public static final java.util.Map<String, RegistryObject<SoundEvent>> CAST_CHARGE = new java.util.HashMap<>();
    public static final java.util.Map<String, RegistryObject<SoundEvent>> CAST_RELEASE = new java.util.HashMap<>();

    static {
        for (String id : new String[]{"avalanche", "icicle_rain", "frost_javelin", "frost_shackles", "frost_heart", "rune_litany", "ice_sentinel", "frost_shell"}) {
            CAST_CHARGE.put(id, sound("cast_" + id + "_charge"));
            CAST_RELEASE.put(id, sound("cast_" + id + "_release"));
        }
    }

    private FDSRegistry() {
    }

    private static <T extends Entity> RegistryObject<EntityType<T>> fx(String name, EntityType.EntityFactory<T> factory,
                                                                      float w, float h, int interval) {
        return ENTITIES.register(name, () -> EntityType.Builder.of(factory, MobCategory.MISC).sized(w, h)
                .clientTrackingRange(8).updateInterval(interval).fireImmune().noSave().noSummon().build(name));
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(FDSpells.id(name)));
    }

    public static void register(IEventBus bus) {
        SPELLS.register(bus);
        ENTITIES.register(bus);
        SOUNDS.register(bus);
        ITEMS.register(bus);
    }
}
