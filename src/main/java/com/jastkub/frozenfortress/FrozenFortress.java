package com.jastkub.frozenfortress;

import com.jastkub.frozenfortress.entity.FrostboundSentinelEntity;
import com.jastkub.frozenfortress.entity.FrostmawEntity;
import com.jastkub.frozenfortress.entity.RimeweaverEntity;
import com.jastkub.frozenfortress.entity.StillbowEntity;
import com.jastkub.frozenfortress.entity.VaultWardenEntity;
import com.jastkub.frozenfortress.entity.boss.VelkharCloneEntity;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.jastkub.frozenfortress.registry.FFBlocks;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFItems;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import com.jastkub.frozenfortress.registry.FFTabs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(FrozenFortress.MODID)
public class FrozenFortress {

    public static final String MODID = "frozen_dominion";

    public FrozenFortress(IEventBus modBus, net.neoforged.fml.ModContainer container) {

        FFSounds.SOUND_EVENTS.register(modBus);
        FFBlocks.BLOCKS.register(modBus);
        com.jastkub.frozenfortress.registry.FFBlockEntities.BLOCK_ENTITIES.register(modBus);
        com.jastkub.frozenfortress.event.LegacyIds.register();          // (old ids: the reliquary is a station now)
        com.jastkub.frozenfortress.registry.FFArmorMaterials.MATERIALS.register(modBus); // (1.21.1: armour materials are a registry)
        FFItems.ITEMS.register(modBus);
        FFEntities.ENTITY_TYPES.register(modBus);
        FFParticles.PARTICLE_TYPES.register(modBus);
        FFEffects.MOB_EFFECTS.register(modBus);
        com.jastkub.frozenfortress.registry.FFEnchantments.ENTITY_EFFECTS.register(modBus); // enchantments are data now; this is Frostbite Edge's blow
        com.jastkub.frozenfortress.registry.FFLootModifiers.SERIALIZERS.register(modBus);
        FFTabs.CREATIVE_MODE_TABS.register(modBus);
        // the Frost Anvil's forging: its recipes and its window (07.10.2026)
        com.jastkub.frozenfortress.registry.FFRecipes.RECIPE_TYPES.register(modBus);
        com.jastkub.frozenfortress.registry.FFRecipes.SERIALIZERS.register(modBus);
        com.jastkub.frozenfortress.registry.FFRecipes.MENUS.register(modBus);
        // the citadel's own structure type: its tiles go back where they were cut
        com.jastkub.frozenfortress.registry.FFStructures.STRUCTURE_TYPES.register(modBus);
        com.jastkub.frozenfortress.registry.FFStructures.PIECE_TYPES.register(modBus);

        // ---- CONFIG FIRST. Registering the specs is what creates the files;
        //      the listener below is what makes an edit to them take effect
        //      without a restart.
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON,
                com.jastkub.frozenfortress.config.FFConfig.COMMON_SPEC);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,
                com.jastkub.frozenfortress.config.FFConfig.CLIENT_SPEC);
        modBus.addListener(this::onConfig);

        modBus.addListener(this::onAttributeCreation);
        modBus.addListener(this::onSpawnPlacements);
        modBus.addListener(this::onCommonSetup);

        // (NeoForge refuses to register an object with no @SubscribeEvent methods; this class has none)
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /**
     * Re-reads the damage multipliers whenever the file is loaded or edited.
     *
     * <p>They are baked into static fields rather than read per hit, because
     * a config lookup is a map lookup and these sit in the path of every blow
     * of a boss that can have forty projectiles in the air. Fires on LOADING
     * and RELOADING both, so editing the file in-game is enough.
     */
    private void onConfig(net.neoforged.fml.event.config.ModConfigEvent event) {
        if (event.getConfig().getSpec()
                == com.jastkub.frozenfortress.config.FFConfig.COMMON_SPEC) {
            com.jastkub.frozenfortress.config.FFConfig.loaded = true;
            com.jastkub.frozenfortress.entity.boss.VelkharEntity.reloadDamage();
        }
    }

    private void onCommonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        // Deferred: registerCurio touches the item registry, which is only
        // safe once every DeferredRegister above has actually fired.
        event.enqueueWork(com.jastkub.frozenfortress.integration.curios.CuriosHooks::register);
        // (the network registers itself: FFNetwork listens for RegisterPayloadHandlersEvent)
    }

    private void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(FFEntities.VELKHAR.get(), VelkharEntity.createAttributes().build());
        event.put(FFEntities.STORM_EYE_ANCHOR.get(),
                com.jastkub.frozenfortress.entity.boss.StormEyeAnchorEntity.createAttributes().build());
        event.put(FFEntities.STORM_EYE_MIRROR.get(),
                com.jastkub.frozenfortress.entity.boss.StormEyeMirrorEntity.createAttributes().build());
        event.put(FFEntities.BONE_WAND_SKELETON.get(),
                com.jastkub.frozenfortress.entity.BoneWandSkeletonEntity.createAttributes().build());
        event.put(FFEntities.HOLLOW_STAFF_SHADE.get(),
                com.jastkub.frozenfortress.entity.HollowStaffShadeEntity.createAttributes().build());
        event.put(FFEntities.VELKHAR_CLONE.get(), VelkharCloneEntity.createAttributes().build());
        event.put(FFEntities.FROSTBOUND_SENTINEL.get(), FrostboundSentinelEntity.createAttributes().build());
        event.put(FFEntities.RIMEWEAVER.get(), RimeweaverEntity.createAttributes().build());
        event.put(FFEntities.STILLBOW.get(), StillbowEntity.createAttributes().build());
        event.put(FFEntities.FROSTMAW.get(), FrostmawEntity.createAttributes().build());
        event.put(FFEntities.FROST_RIDER.get(), com.jastkub.frozenfortress.entity.FrostRiderEntity.createAttributes().build());
        event.put(FFEntities.FROST_SKELETON.get(),
                com.jastkub.frozenfortress.entity.FrostSkeletonEntity.createAttributes().build());
        event.put(FFEntities.HOLLOW_GOLEM.get(),
                com.jastkub.frozenfortress.entity.HollowGolemEntity.createAttributes().build());
        event.put(FFEntities.VAULT_WARDEN.get(), VaultWardenEntity.createAttributes().build());
        event.put(FFEntities.TURNKEY.get(), com.jastkub.frozenfortress.entity.TurnkeyEntity.createAttributes().build());
        event.put(FFEntities.BONE_LORD.get(), com.jastkub.frozenfortress.entity.BoneLordEntity.createAttributes().build());
        event.put(FFEntities.LAMPLIGHTER.get(), com.jastkub.frozenfortress.entity.LamplighterEntity.createAttributes().build());
        event.put(FFEntities.TURNKEY_KEY.get(), com.jastkub.frozenfortress.entity.TurnkeyKeyEntity.createAttributes().build());
        event.put(FFEntities.FROST_CANDLE.get(), com.jastkub.frozenfortress.entity.FrostCandleEntity.createAttributes().build());
        event.put(FFEntities.RIME_PRIESTESS.get(), com.jastkub.frozenfortress.entity.RimePriestessEntity.createAttributes().build());
        event.put(FFEntities.DROWNED_LADY.get(), com.jastkub.frozenfortress.entity.DrownedLadyEntity.createAttributes().build());
        event.put(FFEntities.VELKHAR_SMITH.get(), com.jastkub.frozenfortress.entity.VelkharSmithEntity.createAttributes().build());
        event.put(FFEntities.ICE_AUROCHS.get(), com.jastkub.frozenfortress.entity.IceAurochsEntity.createAttributes().build());
        event.put(FFEntities.FORGE_OVERSEER.get(), com.jastkub.frozenfortress.entity.ForgeOverseerEntity.createAttributes().build());
        event.put(FFEntities.SHADE_SHEPHERD.get(), com.jastkub.frozenfortress.entity.ShadeShepherdEntity.createAttributes().build());
        event.put(FFEntities.SHADE.get(), com.jastkub.frozenfortress.entity.ShadeEntity.createAttributes().build());
        event.put(FFEntities.SHADE_SHEPHERD_DECOY.get(), com.jastkub.frozenfortress.entity.ShadeShepherdDecoyEntity.createAttributes().build());
        event.put(FFEntities.DROWNED_HANDS.get(), com.jastkub.frozenfortress.entity.DrownedHandsEntity.createAttributes().build());
        event.put(FFEntities.FROST_PAGE.get(), com.jastkub.frozenfortress.entity.FrostPageEntity.createAttributes().build());
        event.put(FFEntities.PRIESTESS_MIRROR.get(), com.jastkub.frozenfortress.entity.PriestessMirrorEntity.createAttributes().build());
    }

    private void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        registerMonsterPlacement(event, FFEntities.FROSTBOUND_SENTINEL.get());
        registerMonsterPlacement(event, FFEntities.RIMEWEAVER.get());
        registerMonsterPlacement(event, FFEntities.STILLBOW.get());
        registerMonsterPlacement(event, FFEntities.FROSTMAW.get());
    }

    private static <T extends Monster> void registerMonsterPlacement(RegisterSpawnPlacementsEvent event, EntityType<T> type) {
        event.register(type, net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkAnyLightMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR);
    }
}
