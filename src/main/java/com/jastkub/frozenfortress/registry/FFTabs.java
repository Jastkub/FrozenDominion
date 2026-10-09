package com.jastkub.frozenfortress.registry;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class FFTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FrozenFortress.MODID);

    public static final RegistryObject<CreativeModeTab> FROZEN_FORTRESS_TAB =
            CREATIVE_MODE_TABS.register("frozen_citadel", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.frozen_dominion"))
                    .icon(() -> new ItemStack(FFItems.SOVEREIGNS_LAMENT.get()))
                    .displayItems((params, output) -> {
                        output.accept(FFItems.SOVEREIGNS_LAMENT.get());
                        output.accept(FFItems.RIMEBOUND_FOCUS.get());
                        output.accept(FFItems.EVERFROST_BOW.get());
                        output.accept(FFItems.EVERFROST_SWORD.get());
                        output.accept(FFItems.EVERFROST_SHIELD.get());
                        output.accept(FFItems.KINGSRIME_SEAL.get());
                        output.accept(FFItems.KINGSRIME_INGOT.get());
                        output.accept(FFItems.CROWN_SHARD.get());
                        output.accept(FFItems.KINGSRIME_HELMET.get());
                        output.accept(FFItems.KINGSRIME_CHESTPLATE.get());
                        output.accept(FFItems.KINGSRIME_LEGGINGS.get());
                        output.accept(FFItems.KINGSRIME_BOOTS.get());
                        output.accept(FFItems.KINGSRIME_SWORD.get());
                        output.accept(FFItems.KINGSRIME_SHIELD.get());
                        output.accept(FFItems.KINGSRIME_BOW.get());
                        output.accept(FFItems.WINTERS_HORN.get());
                        output.accept(FFItems.SOVEREIGNS_SIGNET.get());
                        output.accept(FFItems.HOLLOW_BREATH.get());
                        output.accept(FFItems.CROWN_OF_THE_HOLLOW_KING.get());
                        output.accept(FFItems.HEART_OF_THE_SILENT_WINTER.get());
                        output.accept(FFItems.MONSTROSITY_SKULL.get());
                        output.accept(FFItems.THRONE_BANE.get());
                        output.accept(FFItems.HOLLOW_KINGS_STAFF.get());
                        output.accept(FFItems.BONE_LORD_FEMUR.get());
                        output.accept(FFItems.CHASE_FEMUR.get());
                        output.accept(FFItems.LAMPLIGHTER_CANDLE.get());
                        output.accept(FFItems.LAST_WATCH_BOW.get());
                        output.accept(FFItems.BONE_WAND.get());
                        output.accept(FFItems.FROST_VERTEBRA.get());
                        output.accept(FFItems.AUROCHS_HORN.get());
                        output.accept(FFItems.OVERSEER_HAMMERHEAD.get());
                        output.accept(FFItems.PRIESTESS_CRYSTAL.get());
                        output.accept(FFItems.SHEPHERD_BELL.get());
                        output.accept(FFItems.DROWNED_BRAID.get());
                        output.accept(FFItems.LANTERN_LENS.get());
                        output.accept(FFItems.TURNKEY_CHAIN.get());
                        output.accept(FFItems.MUSIC_DISC_SILENT_WINTER.get());
                        output.accept(FFItems.EVERFROST_SHARD.get());
                        output.accept(FFItems.EVERFROST_CRYSTAL.get());
                        output.accept(FFItems.FROSTWALKER_BAND.get());
                        output.accept(FFItems.GLACIER_RING.get());
                        output.accept(FFItems.GALE_FEATHER.get());
                        output.accept(FFItems.WARMTH_POTION.get());
                        output.accept(FFItems.EVERFROST_INGOT.get());
                        output.accept(FFItems.SENTINEL_RIVET.get());
                        output.accept(FFItems.RIMEWEAVER_PRISM.get());
                        output.accept(FFItems.STILLBOW_FLETCHING.get());
                        output.accept(FFItems.FROSTMAW_FANG.get());
                        output.accept(FFItems.WARDEN_CORE.get());
                        output.accept(FFItems.ATTUNED_PRISM.get());
                        output.accept(FFItems.FROZEN_BOWSTAVE.get());
                        output.accept(FFItems.FROSTHEART_TOTEM.get());
                        output.accept(FFItems.WARDENS_LODESTONE.get());
                        output.accept(FFItems.EVERFROST_HELMET.get());
                        output.accept(FFItems.EVERFROST_CHESTPLATE.get());
                        output.accept(FFItems.EVERFROST_LEGGINGS.get());
                        output.accept(FFItems.EVERFROST_BOOTS.get());
                        output.accept(FFItems.FROZEN_SIGIL.get());
                        output.accept(FFItems.DESCENT_KEY.get());
                        output.accept(FFItems.GUARD_KEY.get());
                        output.accept(FFItems.CRYPT_KEY.get());
                        output.accept(FFItems.CELL_KEY.get());
                        output.accept(FFItems.COURT_KEY.get());
                        output.accept(FFItems.CHAMBER_KEY.get());
                        output.accept(FFItems.ARCHIVE_KEY.get());
                        output.accept(FFItems.STORM_KEY.get());
                        output.accept(FFItems.WARDEN_SHARD.get());
                        output.accept(FFItems.SEAL_CORE.get());
                        output.accept(FFItems.THRONE_KEY.get());
                        output.accept(FFItems.SHACKLE_KEY.get());
                        output.accept(FFItems.NEST_SHARD.get());
                        output.accept(FFItems.CHAINED_CHEST.get());
                        output.accept(FFItems.TURNKEY_LOCK.get());
                        output.accept(FFItems.CITADEL_GATE.get());
                        output.accept(FFItems.CITADEL_LOCK.get());
                        output.accept(FFItems.CITADEL_SPAWNER.get());
                        output.accept(FFItems.PRISON_HEART.get());
                        output.accept(FFItems.SEALED_STONE_BRICKS.get());
                        output.accept(FFItems.SEALED_DEEPSLATE_TILES.get());
                        output.accept(FFItems.SEALED_ICE.get());
                        output.accept(FFItems.FALSE_WALL.get());
                        output.accept(FFItems.RUNE_PRISONS.get());
                        output.accept(FFItems.RUNE_CRYPTS.get());
                        output.accept(FFItems.RUNE_STORM.get());
                        output.accept(FFItems.RUNE_DEPTHS.get());
                        output.accept(FFItems.RUNE_COURT.get());
                        output.accept(FFItems.ANCESTORS_SIGNET.get());
                        output.accept(FFItems.FROST_ANVIL.get());
                        output.accept(FFItems.FROSTED_STONE_BRICKS.get());
                        output.accept(FFItems.FROST_ICICLE.get());
                        output.accept(FFItems.FROST_SCONCE.get());
                        output.accept(FFItems.GREAT_BANNER_ROYAL.get());
                        output.accept(FFItems.GREAT_BANNER_STORM.get());
                        output.accept(FFItems.GREAT_BANNER_MOURNING.get());
                        output.accept(FFItems.GREAT_BANNER_FROST.get());
                        output.accept(FFItems.GREAT_BANNER_PRISONS.get());
                        output.accept(FFItems.GREAT_BANNER_ANCESTORS.get());
                        output.accept(FFItems.GREAT_BANNER_DEPTHS.get());
                        output.accept(FFItems.GREAT_BANNER_THRONE.get());
                        output.accept(FFItems.BLACK_IRON_FENCE.get());
                        output.accept(FFItems.SNOWY_STONE_BRICKS.get());
                        output.accept(FFItems.ICEBOUND_STONE_BRICKS.get());
                        output.accept(FFItems.CROWNBREAKER.get());
                        output.accept(FFItems.HEARTH_AMULET.get());
                        output.accept(FFItems.SOVEREIGN_ICE.get());
                        output.accept(FFItems.EVERFROST_BLOCK.get());
                        output.accept(FFItems.STORMCROWN_BEACON.get());
                        output.accept(FFItems.GLACIAL_GLASS.get());
                        output.accept(FFItems.GLACIAL_GLASS_PANE.get());
                        output.accept(FFItems.FROST_LANTERN.get());
                        output.accept(FFItems.ICICLE_CLUSTER.get());
                        output.accept(FFItems.FROST_CHANDELIER.get());
                        output.accept(FFItems.FROST_CANDELABRA.get());
                        output.accept(FFItems.FROST_CANDLESTICK.get());
                        output.accept(FFItems.FLUTED_STONE_PILLAR.get());
                        output.accept(FFItems.VELKHAR_SPAWN_EGG.get());
                        output.accept(FFItems.FROSTBOUND_SENTINEL_SPAWN_EGG.get());
                        output.accept(FFItems.RIMEWEAVER_SPAWN_EGG.get());
                        output.accept(FFItems.STILLBOW_SPAWN_EGG.get());
                        output.accept(FFItems.FROSTMAW_SPAWN_EGG.get());
                        output.accept(FFItems.FROST_SKELETON_SPAWN_EGG.get());
                        output.accept(FFItems.VAULT_WARDEN_SPAWN_EGG.get());
                        output.accept(FFItems.TURNKEY_SPAWN_EGG.get());
                        output.accept(FFItems.LAMPLIGHTER_SPAWN_EGG.get());
                        output.accept(FFItems.RIME_PRIESTESS_SPAWN_EGG.get());
                        output.accept(FFItems.DROWNED_LADY_SPAWN_EGG.get());
                        output.accept(FFItems.ICE_AUROCHS_SPAWN_EGG.get());
                        output.accept(FFItems.FORGE_OVERSEER_SPAWN_EGG.get());
                        output.accept(FFItems.SHADE_SHEPHERD_SPAWN_EGG.get());
                        output.accept(FFItems.SHADE_SPAWN_EGG.get());
                        output.accept(FFItems.VELKHAR_SMITH_SPAWN_EGG.get());
                        output.accept(FFItems.MONSTROSITY_SADDLE.get());
                        output.accept(FFItems.WAND_FITTING.get());
                    })
                    .build());

    private FFTabs() {
    }
}
