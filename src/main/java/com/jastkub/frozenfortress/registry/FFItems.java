package com.jastkub.frozenfortress.registry;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.item.CrownOfTheHollowKingItem;
import com.jastkub.frozenfortress.item.EverfrostArmorItem;
import com.jastkub.frozenfortress.item.HeartOfWinterItem;
import com.jastkub.frozenfortress.item.SovereignsLamentItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class FFItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.ITEM, FrozenFortress.MODID);

    // ================= BOSS LOOT =================

    public static final DeferredHolder<Item, Item> SOVEREIGNS_LAMENT =
            ITEMS.register("sovereigns_lament", SovereignsLamentItem::new);

    public static final DeferredHolder<Item, Item> CROWN_OF_THE_HOLLOW_KING =
            ITEMS.register("crown_of_the_hollow_king", CrownOfTheHollowKingItem::new);

    public static final DeferredHolder<Item, Item> HEART_OF_THE_SILENT_WINTER =
            ITEMS.register("heart_of_the_silent_winter", HeartOfWinterItem::new);

    /** The disc's song (data/frozen_dominion/jukebox_song/silent_winter.json: comparator 14, 180 s - 1.20.1's
     *  RecordItem(14, ..., 3600 ticks)). */
    public static final ResourceKey<JukeboxSong> SILENT_WINTER_SONG =
            ResourceKey.create(Registries.JUKEBOX_SONG, FrozenFortress.id("silent_winter"));

    public static final DeferredHolder<Item, Item> MUSIC_DISC_SILENT_WINTER =
            ITEMS.register("music_disc_silent_winter", () -> new Item(
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).jukeboxPlayable(SILENT_WINTER_SONG)));

    // ================= MATERIALS =================

    public static final DeferredHolder<Item, Item> EVERFROST_SHARD =
            ITEMS.register("everfrost_shard", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    /** Shards fused into a workable bar - the stock for Everfrost plate. */
    /** Nine shards fused (07.10.2026): four of them and four of gold forge an ingot on the Frost Anvil. */
    public static final DeferredHolder<Item, Item> EVERFROST_CRYSTAL =
            ITEMS.register("everfrost_crystal", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final DeferredHolder<Item, Item> EVERFROST_INGOT =
            ITEMS.register("everfrost_ingot", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    // ================= GARRISON TROPHIES =================
    // One per servant, each feeding its own shortcut recipe into the tool
    // that servant is thematically tied to - a reason to hunt a specific
    // mob rather than just whichever is standing closest.

    /** ZMORA TRONU, the first legendary (tools/gen_throne_bane.py; 07.10.2026). */
    public static final DeferredHolder<Item, Item> THRONE_BANE =
            ITEMS.register("throne_bane", com.jastkub.frozenfortress.item.ThroneBaneItem::new);

    /** KOSTUR PUSTEGO KROLA, the second legendary (tools/gen_hollow_staff.py; 07.10.2026). */
    public static final DeferredHolder<Item, Item> HOLLOW_KINGS_STAFF =
            ITEMS.register("hollow_kings_staff", com.jastkub.frozenfortress.item.HollowStaffItem::new);

    /** The Bone Lord's thighbone - its trophy (one for each who fought it), and what the smith shoes in everfrost. */
    public static final DeferredHolder<Item, Item> BONE_LORD_FEMUR =
            ITEMS.register("bone_lord_femur", () -> new Item(new Item.Properties().rarity(Rarity.EPIC).stacksTo(16)));
    /** SWIECA LATARNIKA - the Lamplighter's Candle, a charm: the frost in keepers' doorways thinned (08.10.2026). */
    public static final DeferredHolder<Item, Item> LAMPLIGHTER_CANDLE =
            ITEMS.register("lamplighter_candle", com.jastkub.frozenfortress.item.LamplighterCandleItem::new);
    /** GNAT POGONI - the Femur of the Chase, from the smith (08.10.2026). */
    public static final DeferredHolder<Item, Item> CHASE_FEMUR =
            ITEMS.register("chase_femur", com.jastkub.frozenfortress.item.ChaseFemurItem::new);

    /** The Wand of the Dead (07.10.2026): forged from the smith's fitting, an Everfrost ingot and a Frost Vertebra. */
    public static final DeferredHolder<Item, Item> BONE_WAND =
            ITEMS.register("bone_wand", com.jastkub.frozenfortress.item.BoneWandItem::new);
    /** 1% of a Frost Skeleton beaten for good: the wand's grip. */
    public static final DeferredHolder<Item, Item> FROST_VERTEBRA = ITEMS.register("frost_vertebra",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.RARE, 16, false));

    /** LUK OSTATNIEJ STRAZY, the third legendary (tools/gen_last_watch.py; 07.10.2026). */
    public static final DeferredHolder<Item, Item> LAST_WATCH_BOW =
            ITEMS.register("last_watch_bow", com.jastkub.frozenfortress.item.LastWatchBowItem::new);

    // THE COURT'S PARTS (crafting plan, phase 3; 07.10.2026): one from each of the seven, always - what the three
    // legendary weapons are forged from, with a Crown Shard and four Kingsrime ingots
    public static final DeferredHolder<Item, Item> AUROCHS_HORN =
            ITEMS.register("aurochs_horn", () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredHolder<Item, Item> OVERSEER_HAMMERHEAD =
            ITEMS.register("overseer_hammerhead", () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredHolder<Item, Item> PRIESTESS_CRYSTAL =
            ITEMS.register("priestess_crystal", () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredHolder<Item, Item> SHEPHERD_BELL =
            ITEMS.register("shepherd_bell", () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredHolder<Item, Item> DROWNED_BRAID =
            ITEMS.register("drowned_braid", () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredHolder<Item, Item> LANTERN_LENS =
            ITEMS.register("lantern_lens", () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredHolder<Item, Item> TURNKEY_CHAIN =
            ITEMS.register("turnkey_chain", () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    public static final DeferredHolder<Item, Item> SENTINEL_RIVET =
            ITEMS.register("sentinel_rivet", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredHolder<Item, Item> RIMEWEAVER_PRISM =
            ITEMS.register("rimeweaver_prism", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredHolder<Item, Item> STILLBOW_FLETCHING =
            ITEMS.register("stillbow_fletching", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredHolder<Item, Item> FROSTMAW_FANG =
            ITEMS.register("frostmaw_fang", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredHolder<Item, Item> WARDEN_CORE =
            ITEMS.register("warden_core", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    // ================= WORKED COMPONENTS =================
    // The trophy above plus a Sentinel's Rivet (the one rivet every player
    // can farm regardless of luck) plus a base material, worked into the
    // one component each tool actually needs - the step between "killed the
    // right thing" and "own the weapon."

    public static final DeferredHolder<Item, Item> ATTUNED_PRISM =
            ITEMS.register("attuned_prism", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredHolder<Item, Item> FROZEN_BOWSTAVE =
            ITEMS.register("frozen_bowstave", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    // ================= VAULT-EXCLUSIVE RARITIES =================

    public static final DeferredHolder<Item, Item> FROSTHEART_TOTEM =
            ITEMS.register("frostheart_totem", com.jastkub.frozenfortress.item.FrostheartTotemItem::new);
    public static final DeferredHolder<Item, Item> WARDENS_LODESTONE =
            ITEMS.register("wardens_lodestone", com.jastkub.frozenfortress.item.WardensLodestoneItem::new);

    // ================= EVERFROST ARMOR =================

    // worn in 3D (GeckoLib, geo/item/armor/everfrost_armor); still EverfrostArmorItems, so the set bonus holds
    public static final DeferredHolder<Item, Item> EVERFROST_HELMET =
            ITEMS.register("everfrost_helmet",
                    () -> new com.jastkub.frozenfortress.item.EverfrostGeoArmorItem(ArmorItem.Type.HELMET));
    public static final DeferredHolder<Item, Item> EVERFROST_CHESTPLATE =
            ITEMS.register("everfrost_chestplate",
                    () -> new com.jastkub.frozenfortress.item.EverfrostGeoArmorItem(ArmorItem.Type.CHESTPLATE));
    public static final DeferredHolder<Item, Item> EVERFROST_LEGGINGS =
            ITEMS.register("everfrost_leggings",
                    () -> new com.jastkub.frozenfortress.item.EverfrostGeoArmorItem(ArmorItem.Type.LEGGINGS));
    public static final DeferredHolder<Item, Item> EVERFROST_BOOTS =
            ITEMS.register("everfrost_boots",
                    () -> new com.jastkub.frozenfortress.item.EverfrostGeoArmorItem(ArmorItem.Type.BOOTS));

    // ================= EVERFROST: THE SWORD AND SHIELD, AND KINGSRIME =================

    public static final DeferredHolder<Item, Item> EVERFROST_SWORD =
            ITEMS.register("everfrost_sword", com.jastkub.frozenfortress.item.KingsrimeItems.EverfrostSword::new);
    public static final DeferredHolder<Item, Item> EVERFROST_SHIELD =
            ITEMS.register("everfrost_shield",
                    () -> new com.jastkub.frozenfortress.item.KingsrimeItems.FrostShield("everfrost_shield", false));
    public static final DeferredHolder<Item, Item> KINGSRIME_HELMET =
            ITEMS.register("kingsrime_helmet",
                    () -> new com.jastkub.frozenfortress.item.KingsrimeItems.KingsrimeArmor(ArmorItem.Type.HELMET));
    public static final DeferredHolder<Item, Item> KINGSRIME_CHESTPLATE =
            ITEMS.register("kingsrime_chestplate",
                    () -> new com.jastkub.frozenfortress.item.KingsrimeItems.KingsrimeArmor(ArmorItem.Type.CHESTPLATE));
    public static final DeferredHolder<Item, Item> KINGSRIME_LEGGINGS =
            ITEMS.register("kingsrime_leggings",
                    () -> new com.jastkub.frozenfortress.item.KingsrimeItems.KingsrimeArmor(ArmorItem.Type.LEGGINGS));
    public static final DeferredHolder<Item, Item> KINGSRIME_BOOTS =
            ITEMS.register("kingsrime_boots",
                    () -> new com.jastkub.frozenfortress.item.KingsrimeItems.KingsrimeArmor(ArmorItem.Type.BOOTS));
    public static final DeferredHolder<Item, Item> KINGSRIME_SWORD =
            ITEMS.register("kingsrime_sword", com.jastkub.frozenfortress.item.KingsrimeSwordItem::new);   // (skills: 07.10.2026)
    public static final DeferredHolder<Item, Item> KINGSRIME_SHIELD =
            ITEMS.register("kingsrime_shield",
                    () -> new com.jastkub.frozenfortress.item.KingsrimeItems.FrostShield("kingsrime_shield", true));
    public static final DeferredHolder<Item, Item> KINGSRIME_BOW =
            ITEMS.register("kingsrime_bow", com.jastkub.frozenfortress.item.KingsrimeItems.KingsrimeBow::new);
    public static final DeferredHolder<Item, Item> KINGSRIME_INGOT =
            ITEMS.register("kingsrime_ingot", () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredHolder<Item, Item> CROWN_SHARD =
            ITEMS.register("crown_shard", () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.EPIC, 16, true));
    /** THE KINGSRIME SEAL (07.10.2026; the netherite template's way gone): in the Frost Anvil's core it remakes an
     *  everfrost piece as the Kingsrime. Two from the Ice Monstrosity; six ingots and one forge another. */
    public static final DeferredHolder<Item, Item> KINGSRIME_SEAL =
            ITEMS.register("kingsrime_seal", () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.EPIC, 64, true));

    // ================= THE COURT'S TOOLS =================

    public static final DeferredHolder<Item, Item> RIMEBOUND_FOCUS =
            ITEMS.register("rimebound_focus", com.jastkub.frozenfortress.item.FFTools.RimeboundFocus::new);
    public static final DeferredHolder<Item, Item> EVERFROST_BOW =
            ITEMS.register("everfrost_bow", com.jastkub.frozenfortress.item.FFTools.EverfrostBow::new);
    public static final DeferredHolder<Item, Item> WINTERS_HORN =
            ITEMS.register("winters_horn", com.jastkub.frozenfortress.item.FFTools.WintersHorn::new);
    public static final DeferredHolder<Item, Item> SOVEREIGNS_SIGNET =
            ITEMS.register("sovereigns_signet", com.jastkub.frozenfortress.item.FFTools.SovereignsSignet::new);
    /** Velkhar's own beam. Only he drops it, and only once. */
    public static final DeferredHolder<Item, Item> HOLLOW_BREATH =
            ITEMS.register("hollow_breath", com.jastkub.frozenfortress.item.FFTools.HollowBreath::new);

    /** Found in watchtowers; the only thing that will break the Stormcrown. */
    public static final DeferredHolder<Item, Item> CROWNBREAKER =
            ITEMS.register("crownbreaker", com.jastkub.frozenfortress.item.CrownbreakerItem::new);

    /** A hearth's coal in brass, from the watchtower: the one ward against the Stormcrown's Chill. */
    public static final DeferredHolder<Item, Item> HEARTH_AMULET =
            ITEMS.register("hearth_amulet", com.jastkub.frozenfortress.item.HearthAmuletItem::new);
    /** Worn at the wrist, nothing slows its wearer (07.10.2026); found in the citadel's chests. */
    public static final DeferredHolder<Item, Item> FROSTWALKER_BAND =
            ITEMS.register("frostwalker_band", com.jastkub.frozenfortress.item.FrostwalkerBandItem::new);
    /** The Glacier Ring: nothing moves its wearer (09.10.2026). */
    public static final DeferredHolder<Item, Item> GLACIER_RING =
            ITEMS.register("glacier_ring", com.jastkub.frozenfortress.item.GlacierRingItem::new);
    /** A Gale Feather: one fall into the Eye of the Storm turned back, spent (08.10.2026). */
    public static final DeferredHolder<Item, Item> GALE_FEATHER =
            ITEMS.register("gale_feather", com.jastkub.frozenfortress.item.GaleFeatherItem::new);
    /** The Potion of Warmth: the expedition's fire in a brass-bound flask, a minute of Warmth (08.10.2026). */
    public static final DeferredHolder<Item, Item> WARMTH_POTION =
            ITEMS.register("warmth_potion", com.jastkub.frozenfortress.item.WarmthPotionItem::new);

    /** Dropped by the Vault Warden; unlocks the sealed throne cathedral. */
    public static final DeferredHolder<Item, Item> FROZEN_SIGIL =
            ITEMS.register("frozen_sigil", () -> new Item(new Item.Properties().rarity(Rarity.EPIC)) {
                @Override
                public boolean isFoil(net.minecraft.world.item.ItemStack stack) {
                    return true;
                }

                @Override
                public void appendHoverText(net.minecraft.world.item.ItemStack stack,
                                            Item.TooltipContext context,
                                            java.util.List<net.minecraft.network.chat.Component> tooltip,
                                            net.minecraft.world.item.TooltipFlag flag) {
                    tooltip.add(net.minecraft.network.chat.Component.translatable("item.frozen_dominion.frozen_sigil.desc"));
                }
            });

    // ================= BLOCK ITEMS =================

    public static final DeferredHolder<Item, Item> UNMELTING_ICE =
            blockItem("unmelting_ice", FFBlocks.UNMELTING_ICE);
    public static final DeferredHolder<Item, Item> SOVEREIGN_ICE =
            blockItem("sovereign_ice", FFBlocks.SOVEREIGN_ICE);
    public static final DeferredHolder<Item, Item> EVERFROST_BLOCK =
            blockItem("everfrost_block", FFBlocks.EVERFROST_BLOCK);
    public static final DeferredHolder<Item, Item> STORMCROWN_BEACON =
            blockItem("stormcrown_beacon", FFBlocks.STORMCROWN_BEACON);
    public static final DeferredHolder<Item, Item> GLACIAL_GLASS =
            blockItem("glacial_glass", FFBlocks.GLACIAL_GLASS);
    public static final DeferredHolder<Item, Item> GLACIAL_GLASS_PANE =
            blockItem("glacial_glass_pane", FFBlocks.GLACIAL_GLASS_PANE);
    public static final DeferredHolder<Item, Item> FROST_LANTERN =
            blockItem("frost_lantern", FFBlocks.FROST_LANTERN);
    public static final DeferredHolder<Item, Item> ICICLE_CLUSTER =
            blockItem("icicle_cluster", FFBlocks.ICICLE_CLUSTER);
    public static final DeferredHolder<Item, Item> FROST_CANDELABRA =
            blockItem("frost_candelabra", FFBlocks.FROST_CANDELABRA);
    public static final DeferredHolder<Item, Item> FROST_CANDLESTICK =
            blockItem("frost_candlestick", FFBlocks.FROST_CANDLESTICK);
    public static final DeferredHolder<Item, Item> FLUTED_STONE_PILLAR =
            blockItem("fluted_stone_pillar", FFBlocks.FLUTED_STONE_PILLAR);
    public static final DeferredHolder<Item, Item> FROST_CHANDELIER =
            blockItem("frost_chandelier", FFBlocks.FROST_CHANDELIER);

    // ================= THE CITADEL'S KEYS =================

    public static final DeferredHolder<Item, Item> DESCENT_KEY = ITEMS.register("descent_key",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.RARE, 1, false));
    public static final DeferredHolder<Item, Item> GUARD_KEY = ITEMS.register("guard_key",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.RARE, 1, false));
    public static final DeferredHolder<Item, Item> CRYPT_KEY = ITEMS.register("crypt_key",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.RARE, 1, false));
    public static final DeferredHolder<Item, Item> CELL_KEY = ITEMS.register("cell_key",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.RARE, 1, false));
    public static final DeferredHolder<Item, Item> COURT_KEY = ITEMS.register("court_key",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.RARE, 1, false));
    public static final DeferredHolder<Item, Item> CHAMBER_KEY = ITEMS.register("chamber_key",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.RARE, 1, false));
    public static final DeferredHolder<Item, Item> ARCHIVE_KEY = ITEMS.register("archive_key",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.RARE, 1, false));
    public static final DeferredHolder<Item, Item> STORM_KEY = ITEMS.register("storm_key",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.RARE, 1, false));
    public static final DeferredHolder<Item, Item> RUNE_PRISONS = ITEMS.register("rune_prisons",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.RARE, 16, true));
    public static final DeferredHolder<Item, Item> RUNE_CRYPTS = ITEMS.register("rune_crypts",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.RARE, 16, true));
    public static final DeferredHolder<Item, Item> RUNE_STORM = ITEMS.register("rune_storm",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.RARE, 16, true));
    public static final DeferredHolder<Item, Item> RUNE_DEPTHS = ITEMS.register("rune_depths",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.RARE, 16, true));
    // (06.10.2026: nothing interesting skipped) the court's rune, and the keys of the wings that were left out
    public static final DeferredHolder<Item, Item> RUNE_COURT = ITEMS.register("rune_court",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.RARE, 16, true));
    public static final DeferredHolder<Item, Item> ANCESTORS_SIGNET = ITEMS.register("ancestors_signet",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.RARE, 1, true));
    /** The Frost Anvil (FrostAnvilBlock): the Seal Core and everfrost ingots are struck on it. */
    public static final DeferredHolder<Item, Item> FROST_ANVIL = blockItem("frost_anvil", FFBlocks.FROST_ANVIL);
    public static final DeferredHolder<Item, Item> SEAL_CORE = ITEMS.register("seal_core",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.EPIC, 1, true));
    public static final DeferredHolder<Item, Item> WARDEN_SHARD = ITEMS.register("warden_shard",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.UNCOMMON, 16, false));
    public static final DeferredHolder<Item, Item> THRONE_KEY = ITEMS.register("throne_key",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.EPIC, 1, true));

    // ================= THE CHAINED COFFERS =================
    // a Nest Shard from every nest the Crownbreaker splits; with an iron ingot over it and an
    // Everfrost Crystal under it, a Shackle Key; a Shackle Key opens one Chained Coffer
    public static final DeferredHolder<Item, Item> SHACKLE_KEY = ITEMS.register("shackle_key",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.RARE, 16, false));
    public static final DeferredHolder<Item, Item> NEST_SHARD = ITEMS.register("nest_shard",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.UNCOMMON, 64, false));
    public static final DeferredHolder<Item, Item> CHAINED_CHEST = blockItem("chained_chest", FFBlocks.CHAINED_CHEST);
    public static final DeferredHolder<Item, Item> GREAT_LANTERN = blockItem("great_lantern", FFBlocks.GREAT_LANTERN);

    public static final DeferredHolder<Item, Item> CITADEL_GATE = blockItem("citadel_gate", FFBlocks.CITADEL_GATE);
    public static final DeferredHolder<Item, Item> CITADEL_LOCK = blockItem("citadel_lock", FFBlocks.CITADEL_LOCK);
    public static final DeferredHolder<Item, Item> CITADEL_SPAWNER = blockItem("citadel_spawner", FFBlocks.CITADEL_SPAWNER);
    public static final DeferredHolder<Item, Item> PRISON_HEART = blockItem("prison_heart", FFBlocks.PRISON_HEART);
    public static final DeferredHolder<Item, Item> SEALED_STONE_BRICKS = blockItem("sealed_stone_bricks", FFBlocks.SEALED_STONE_BRICKS);
    public static final DeferredHolder<Item, Item> SEALED_DEEPSLATE_TILES = blockItem("sealed_deepslate_tiles", FFBlocks.SEALED_DEEPSLATE_TILES);
    public static final DeferredHolder<Item, Item> SEALED_ICE = blockItem("sealed_ice", FFBlocks.SEALED_ICE);
    public static final DeferredHolder<Item, Item> SEALED_CRACKED_STONE_BRICKS = blockItem("sealed_cracked_stone_bricks", FFBlocks.SEALED_CRACKED_STONE_BRICKS);
    public static final DeferredHolder<Item, Item> SEALED_CHISELED_STONE_BRICKS = blockItem("sealed_chiseled_stone_bricks", FFBlocks.SEALED_CHISELED_STONE_BRICKS);
    public static final DeferredHolder<Item, Item> SEALED_FROSTED_STONE_BRICKS = blockItem("sealed_frosted_stone_bricks", FFBlocks.SEALED_FROSTED_STONE_BRICKS);
    public static final DeferredHolder<Item, Item> SEALED_POLISHED_DEEPSLATE = blockItem("sealed_polished_deepslate", FFBlocks.SEALED_POLISHED_DEEPSLATE);
    public static final DeferredHolder<Item, Item> SEALED_POLISHED_ANDESITE = blockItem("sealed_polished_andesite", FFBlocks.SEALED_POLISHED_ANDESITE);
    public static final DeferredHolder<Item, Item> SEALED_SMOOTH_STONE = blockItem("sealed_smooth_stone", FFBlocks.SEALED_SMOOTH_STONE);
    public static final DeferredHolder<Item, Item> SEALED_FLUTED_STONE_PILLAR = blockItem("sealed_fluted_stone_pillar", FFBlocks.SEALED_FLUTED_STONE_PILLAR);
    public static final DeferredHolder<Item, Item> FALSE_WALL = blockItem("false_wall", FFBlocks.FALSE_WALL);
    public static final DeferredHolder<Item, Item> FROSTED_STONE_BRICKS = blockItem("frosted_stone_bricks", FFBlocks.FROSTED_STONE_BRICKS);
    public static final DeferredHolder<Item, Item> FROST_ICICLE = blockItem("frost_icicle", FFBlocks.FROST_ICICLE);
    public static final DeferredHolder<Item, Item> FROST_SCONCE = blockItem("frost_sconce", FFBlocks.FROST_SCONCE);
    public static final DeferredHolder<Item, Item> GREAT_BANNER_ROYAL = blockItem("great_banner_royal", FFBlocks.GREAT_BANNER_ROYAL);
    public static final DeferredHolder<Item, Item> GREAT_BANNER_STORM = blockItem("great_banner_storm", FFBlocks.GREAT_BANNER_STORM);
    public static final DeferredHolder<Item, Item> GREAT_BANNER_MOURNING = blockItem("great_banner_mourning", FFBlocks.GREAT_BANNER_MOURNING);
    public static final DeferredHolder<Item, Item> GREAT_BANNER_FROST = blockItem("great_banner_frost", FFBlocks.GREAT_BANNER_FROST);
    public static final DeferredHolder<Item, Item> GREAT_BANNER_PRISONS = blockItem("great_banner_prisons", FFBlocks.GREAT_BANNER_PRISONS);
    public static final DeferredHolder<Item, Item> GREAT_BANNER_ANCESTORS = blockItem("great_banner_ancestors", FFBlocks.GREAT_BANNER_ANCESTORS);
    public static final DeferredHolder<Item, Item> GREAT_BANNER_DEPTHS = blockItem("great_banner_depths", FFBlocks.GREAT_BANNER_DEPTHS);
    public static final DeferredHolder<Item, Item> GREAT_BANNER_THRONE = blockItem("great_banner_throne", FFBlocks.GREAT_BANNER_THRONE);
    public static final DeferredHolder<Item, Item> BLACK_IRON_FENCE = blockItem("black_iron_fence", FFBlocks.BLACK_IRON_FENCE);
    public static final DeferredHolder<Item, Item> SNOWY_STONE_BRICKS = blockItem("snowy_stone_bricks", FFBlocks.SNOWY_STONE_BRICKS);
    public static final DeferredHolder<Item, Item> ICEBOUND_STONE_BRICKS = blockItem("icebound_stone_bricks", FFBlocks.ICEBOUND_STONE_BRICKS);

    // ================= SPAWN EGGS =================

    public static final DeferredHolder<Item, Item> VELKHAR_SPAWN_EGG =
            ITEMS.register("velkhar_spawn_egg", () -> new DeferredSpawnEggItem(FFEntities.VELKHAR,
                    0x1B2A4A, 0x7FDBFF, new Item.Properties()));
    public static final DeferredHolder<Item, Item> FROSTBOUND_SENTINEL_SPAWN_EGG =
            ITEMS.register("frostbound_sentinel_spawn_egg", () -> new DeferredSpawnEggItem(FFEntities.FROSTBOUND_SENTINEL,
                    0x3A4A6B, 0xA8D8F0, new Item.Properties()));
    public static final DeferredHolder<Item, Item> RIMEWEAVER_SPAWN_EGG =
            ITEMS.register("rimeweaver_spawn_egg", () -> new DeferredSpawnEggItem(FFEntities.RIMEWEAVER,
                    0x4A3A6B, 0xC8E8FF, new Item.Properties()));
    public static final DeferredHolder<Item, Item> STILLBOW_SPAWN_EGG =
            ITEMS.register("stillbow_spawn_egg", () -> new DeferredSpawnEggItem(FFEntities.STILLBOW,
                    0x2A3A4A, 0x9FC8E0, new Item.Properties()));
    public static final DeferredHolder<Item, Item> FROST_SKELETON_SPAWN_EGG =
            ITEMS.register("frost_skeleton_spawn_egg", () -> new DeferredSpawnEggItem(FFEntities.FROST_SKELETON,
                    0xDDD6C0, 0x9FDCFF, new Item.Properties()));
    public static final DeferredHolder<Item, Item> FROSTMAW_SPAWN_EGG =
            ITEMS.register("frostmaw_spawn_egg", () -> new DeferredSpawnEggItem(FFEntities.FROSTMAW,
                    0xD8E8F0, 0x4A6A8B, new Item.Properties()));
    /** The king's smith's trades (VelkharSmithEntity): the Monstrosity's saddle, the skeleton wand's fitting. */
    public static final DeferredHolder<Item, Item> MONSTROSITY_SADDLE = ITEMS.register("monstrosity_saddle",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.EPIC, 1, true));
    public static final DeferredHolder<Item, Item> WAND_FITTING = ITEMS.register("wand_fitting",
            () -> new com.jastkub.frozenfortress.item.CitadelKeyItem(Rarity.RARE, 1, false));
    public static final DeferredHolder<Item, Item> FORGE_OVERSEER_SPAWN_EGG = ITEMS.register("forge_overseer_spawn_egg",
            () -> new DeferredSpawnEggItem(FFEntities.FORGE_OVERSEER, 0x2A2C33, 0x9FE6FF, new Item.Properties()));
    public static final DeferredHolder<Item, Item> SHADE_SHEPHERD_SPAWN_EGG = ITEMS.register("shade_shepherd_spawn_egg",
            () -> new DeferredSpawnEggItem(FFEntities.SHADE_SHEPHERD, 0x24242F, 0xA8A294, new Item.Properties()));
    public static final DeferredHolder<Item, Item> SHADE_SPAWN_EGG = ITEMS.register("shade_spawn_egg",
            () -> new DeferredSpawnEggItem(FFEntities.SHADE, 0x2A2838, 0x9EF0FF, new Item.Properties()));
    public static final DeferredHolder<Item, Item> ICE_AUROCHS_SPAWN_EGG =
            ITEMS.register("ice_aurochs_spawn_egg", () -> new DeferredSpawnEggItem(FFEntities.ICE_AUROCHS,
                    0x2E3A4E, 0xB2DEF4, new Item.Properties()));
    public static final DeferredHolder<Item, Item> VELKHAR_SMITH_SPAWN_EGG =
            ITEMS.register("velkhar_smith_spawn_egg", () -> new DeferredSpawnEggItem(FFEntities.VELKHAR_SMITH,
                    0x5A4636, 0xBFC9D2, new Item.Properties()));
    public static final DeferredHolder<Item, Item> DROWNED_LADY_SPAWN_EGG =
            ITEMS.register("drowned_lady_spawn_egg", () -> new DeferredSpawnEggItem(FFEntities.DROWNED_LADY,
                    0x1E3844, 0x9FE0F0, new Item.Properties()));
    public static final DeferredHolder<Item, Item> RIME_PRIESTESS_SPAWN_EGG =
            ITEMS.register("rime_priestess_spawn_egg", () -> new DeferredSpawnEggItem(FFEntities.RIME_PRIESTESS,
                    0x2E3A5A, 0xBFE6FF, new Item.Properties()));
    public static final DeferredHolder<Item, Item> TURNKEY_LOCK = blockItem("turnkey_lock", FFBlocks.TURNKEY_LOCK);
    public static final DeferredHolder<Item, Item> TURNKEY_SPAWN_EGG =
            ITEMS.register("turnkey_spawn_egg", () -> new DeferredSpawnEggItem(FFEntities.TURNKEY,
                    0x3A2E26, 0x8FE6FF, new Item.Properties()));
    public static final DeferredHolder<Item, Item> LAMPLIGHTER_SPAWN_EGG =
            ITEMS.register("lamplighter_spawn_egg", () -> new DeferredSpawnEggItem(FFEntities.LAMPLIGHTER,
                    0x2F3138, 0xF2D489, new Item.Properties()));
    public static final DeferredHolder<Item, Item> VAULT_WARDEN_SPAWN_EGG =
            ITEMS.register("vault_warden_spawn_egg", () -> new DeferredSpawnEggItem(FFEntities.VAULT_WARDEN,
                    0x2B3B5B, 0x5FEFEF, new Item.Properties()));

    public static final DeferredHolder<Item, Item> MONSTROSITY_SKULL =
            ITEMS.register("monstrosity_skull", () -> new BlockItem(FFBlocks.MONSTROSITY_SKULL.get(),
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC).fireResistant()));

    private static DeferredHolder<Item, Item> blockItem(String name, DeferredHolder<net.minecraft.world.level.block.Block, ? extends net.minecraft.world.level.block.Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private FFItems() {
    }
}
