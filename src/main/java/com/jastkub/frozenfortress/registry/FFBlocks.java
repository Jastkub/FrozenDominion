package com.jastkub.frozenfortress.registry;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class FFBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK, FrozenFortress.MODID);


    /**
     * The crust a colossus's bomb leaves, and the fuel it drains back.
     *
     * <p>Unlike FLOOR_CRACK below it, this one is BREAKABLE and it persists:
     * it is a resource in the fight rather than a decal on it. See
     * {@link com.jastkub.frozenfortress.block.RimeSheetBlock}.
     */
    public static final DeferredHolder<Block, Block> RIME_SHEET =
            BLOCKS.register("rime_sheet",
                    com.jastkub.frozenfortress.block.RimeSheetBlock::new);

    /** Temporary split in the floor left by a heavy blow. Never dropped, never crafted. */
    public static final DeferredHolder<Block, Block> FLOOR_CRACK =
            BLOCKS.register("floor_crack",
                    com.jastkub.frozenfortress.block.FloorCrackBlock::new);

    /**
     * The seat itself. Fortress-only: unbreakable, no loot table, no item, no
     * creative tab - it cannot leave the throne room by any route.
     */
    /** A swinging axe's anchor over the Rift's way (07.10.2026; PendulumAxeBlockEntity). */
    public static final DeferredHolder<Block, Block> PENDULUM_AXE =
            BLOCKS.register("pendulum_axe", com.jastkub.frozenfortress.block.PendulumAxeBlock::new);

    /** The Monstrosity's skull: its drop, the statue's head in the waking ritual (07.10.2026). */
    public static final DeferredHolder<Block, Block> MONSTROSITY_SKULL =
            BLOCKS.register("monstrosity_skull", com.jastkub.frozenfortress.block.MonstrositySkullBlock::new);

    public static final DeferredHolder<Block, Block> FROZEN_THRONE =
            BLOCKS.register("frozen_throne",
                    com.jastkub.frozenfortress.block.FrozenThroneBlock::new);

    /**
     * The citadel's own ice: looks as ice does, but
     * never melts and leaves no water when broken.
     */
    public static final DeferredHolder<Block, Block> UNMELTING_ICE =
            BLOCKS.register("unmelting_ice", () -> new net.minecraft.world.level.block.HalfTransparentBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.ICE).friction(0.98F).strength(0.5F)
                            .sound(SoundType.GLASS).noOcclusion()
                            .isValidSpawn((s, l, p, e) -> false)
                            .isRedstoneConductor((s, l, p) -> false)));

    /** The Frost Anvil beside the imprisoned smith: the Seal Core and everfrost ingots (FrostAnvilBlock). */
    public static final DeferredHolder<Block, Block> FROST_ANVIL =
            BLOCKS.register("frost_anvil", com.jastkub.frozenfortress.block.FrostAnvilBlock::new);

    /** Ancient never-melting ice, glows faintly from within. */
    public static final DeferredHolder<Block, Block> SOVEREIGN_ICE =
            BLOCKS.register("sovereign_ice", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(1.8F, 6.0F)
                    .friction(0.98F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 10)));

    /** Unbreakable glowing windows of the sealed cathedral. */
    public static final DeferredHolder<Block, Block> SEALED_SOVEREIGN_ICE =
            BLOCKS.register("sealed_sovereign_ice", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 10)
                    .noLootTable()));

    // ================= THE CITADEL: gates, locks, nests, the prison =================

    /** A block of a sealed gate; its lock removes the whole gate. */
    public static final DeferredHolder<Block, Block> CITADEL_GATE =
            BLOCKS.register("citadel_gate", com.jastkub.frozenfortress.block.CitadelGateBlock::new);
    /** The lock of a sealed gate: the keys it wants are set by the structure. */
    public static final DeferredHolder<Block, Block> CITADEL_LOCK =
            BLOCKS.register("citadel_lock", com.jastkub.frozenfortress.block.CitadelLockBlock::new);
    /** A Frost Nest: the citadel's spawner, broken only by the Crownbreaker. */
    public static final DeferredHolder<Block, Block> CITADEL_SPAWNER =
            BLOCKS.register("citadel_spawner", com.jastkub.frozenfortress.block.CitadelSpawnerBlock::new);
    /** Under the Ice Prison's floor: wakes the Monstrosity, raises the reliquary. */
    public static final DeferredHolder<Block, Block> PRISON_HEART =
            BLOCKS.register("prison_heart", com.jastkub.frozenfortress.block.PrisonHeartBlock::new);
    /** The trap rooms (06.10.2026): the heart of the room's cold, the frost maw, the crumbling ice. */
    public static final DeferredHolder<Block, Block> FROST_HEART =
            BLOCKS.register("frost_heart", com.jastkub.frozenfortress.block.TrapBlocks.FrostHeart::new);
    public static final DeferredHolder<Block, Block> FROST_CANNON =
            BLOCKS.register("frost_cannon", com.jastkub.frozenfortress.block.TrapBlocks.FrostCannon::new);
    public static final DeferredHolder<Block, Block> CRUMBLING_ICE =
            BLOCKS.register("crumbling_ice", com.jastkub.frozenfortress.block.TrapBlocks.CrumblingIce::new);
    /** The shells of the throne room and the prison: they look like the
     *  fortress around them and nothing breaks them. */
    public static final DeferredHolder<Block, Block> SEALED_STONE_BRICKS =
            BLOCKS.register("sealed_stone_bricks", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F)
                    .sound(SoundType.STONE).noLootTable()));
    public static final DeferredHolder<Block, Block> SEALED_DEEPSLATE_TILES =
            BLOCKS.register("sealed_deepslate_tiles", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE).strength(-1.0F, 3600000.0F)
                    .sound(SoundType.DEEPSLATE_TILES).noLootTable()));
    public static final DeferredHolder<Block, Block> SEALED_ICE =
            BLOCKS.register("sealed_ice", () -> new net.minecraft.world.level.block.HalfTransparentBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(-1.0F, 3600000.0F)
                            .sound(SoundType.GLASS).noOcclusion().lightLevel(state -> 4).noLootTable()));

    // ================= THE LAMPLIGHTER'S DOME: its shell, which nothing breaks =================
    // -
    // each the block it stands in for, drawn with that block's own models
    public static final DeferredHolder<Block, Block> SEALED_CRACKED_STONE_BRICKS = sealed("sealed_cracked_stone_bricks", MapColor.STONE, SoundType.STONE);
    public static final DeferredHolder<Block, Block> SEALED_CHISELED_STONE_BRICKS = sealed("sealed_chiseled_stone_bricks", MapColor.STONE, SoundType.STONE);
    public static final DeferredHolder<Block, Block> SEALED_FROSTED_STONE_BRICKS = sealed("sealed_frosted_stone_bricks", MapColor.STONE, SoundType.STONE);
    public static final DeferredHolder<Block, Block> SEALED_POLISHED_DEEPSLATE = sealed("sealed_polished_deepslate", MapColor.DEEPSLATE, SoundType.POLISHED_DEEPSLATE);
    public static final DeferredHolder<Block, Block> SEALED_POLISHED_ANDESITE = sealed("sealed_polished_andesite", MapColor.STONE, SoundType.STONE);
    public static final DeferredHolder<Block, Block> SEALED_SMOOTH_STONE = sealed("sealed_smooth_stone", MapColor.STONE, SoundType.STONE);
    /** The watchtower's great lantern: its lens turns to the Lamplighter's beam and the beam leaves it. */
    public static final DeferredHolder<Block, Block> GREAT_LANTERN =
            BLOCKS.register("great_lantern", com.jastkub.frozenfortress.block.GreatLanternBlock::new);
    /** The citadel's checkpoints: they stir when the hall's keeper falls, and kindled they take your return. */
    public static final DeferredHolder<Block, Block> FROST_SHRINE =
            BLOCKS.register("frost_shrine", com.jastkub.frozenfortress.block.FrostShrineBlock::new);
    public static final DeferredHolder<Block, Block> SEALED_FLUTED_STONE_PILLAR =
            BLOCKS.register("sealed_fluted_stone_pillar", () -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).sound(SoundType.STONE).noLootTable()));

    private static DeferredHolder<Block, Block> sealed(String name, MapColor color, SoundType sound) {
        return BLOCKS.register(name, () -> new Block(BlockBehaviour.Properties.of()
                .mapColor(color).strength(-1.0F, 3600000.0F).sound(sound).noLootTable()));
    }

    /** A false wall: it looks like the cracked masonry round it and is not
     *  there. Walked through, never broken - so a secret does not depend on
     *  the Mining Fatigue being gone. */
    public static final DeferredHolder<Block, Block> FALSE_WALL =
            BLOCKS.register("false_wall", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).noCollission()
                    .sound(SoundType.STONE).noLootTable()));

    /** A statue: one block that draws a whole model in ice (see CitadelStatueBlock). */
    public static final DeferredHolder<Block, Block> CITADEL_STATUE =
            BLOCKS.register("citadel_statue", com.jastkub.frozenfortress.block.CitadelStatueBlock::new);

    /** The invisible body of a statue - what you actually bump into. */
    public static final DeferredHolder<Block, Block> STATUE_CORE =
            BLOCKS.register("statue_core", com.jastkub.frozenfortress.block.StatueCoreBlock::new);

    /** Stone bricks with the frost grown down over their top edge. */
    public static final DeferredHolder<Block, Block> FROSTED_STONE_BRICKS =
            BLOCKS.register("frosted_stone_bricks", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));

    /** Stone bricks with snow lying on them: the citadel's wall tops and ledges. */
    public static final DeferredHolder<Block, Block> SNOWY_STONE_BRICKS =
            BLOCKS.register("snowy_stone_bricks", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW).strength(1.5F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));

    /** Stone bricks under a skin of ice, where the glacier has grown over the masonry. */
    public static final DeferredHolder<Block, Block> ICEBOUND_STONE_BRICKS =
            BLOCKS.register("icebound_stone_bricks", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE).strength(1.8F, 6.0F).requiresCorrectToolForDrops()
                    .friction(0.98F).sound(SoundType.GLASS)));

    /** An icicle one to three blocks long, built in sections like a stalactite. */
    public static final DeferredHolder<Block, Block> FROST_ICICLE =
            BLOCKS.register("frost_icicle", com.jastkub.frozenfortress.block.FrostIcicleBlock::new);

    /** A frost lantern on an iron bracket, fixed to a wall. */
    public static final DeferredHolder<Block, Block> FROST_SCONCE =
            BLOCKS.register("frost_sconce", com.jastkub.frozenfortress.block.FrostSconceBlock::new);

    /** The block that owns a door of the citadel: its model, keys and opening. */
    public static final DeferredHolder<Block, Block> CITADEL_DOOR =
            BLOCKS.register("citadel_door", com.jastkub.frozenfortress.block.CitadelDoorBlock::new);

    /** A lock in a pillar of the Turnkey's hall: his flung keys fly to these. */
    public static final DeferredHolder<Block, Block> TURNKEY_LOCK =
            BLOCKS.register("turnkey_lock", com.jastkub.frozenfortress.block.TurnkeyLockBlock::new);

    /** A chest bound in chains: a Shackle Key opens it, and it becomes a chest of that stage's treasure. */
    public static final DeferredHolder<Block, Block> CHAINED_CHEST =
            BLOCKS.register("chained_chest", com.jastkub.frozenfortress.block.ChainedChestBlock::new);

    /** Under the Frozen Cisterns' stair: it sends the stair down into the water for the fight (06.10.2026). */
    public static final DeferredHolder<Block, Block> STAIR_WARD =
            BLOCKS.register("stair_ward", com.jastkub.frozenfortress.block.StairWardBlock::new);

    // ---- THE TRAPS
    public static final DeferredHolder<Block, Block> ICICLE_TRAP =
            BLOCKS.register("icicle_trap", com.jastkub.frozenfortress.block.IcicleTrapBlock::new);
    public static final DeferredHolder<Block, Block> ARROW_SLIT =
            BLOCKS.register("arrow_slit", com.jastkub.frozenfortress.block.ArrowSlitBlock::new);
    public static final DeferredHolder<Block, Block> AMBUSH =
            BLOCKS.register("ambush", com.jastkub.frozenfortress.block.AmbushBlock::new);

    /** The lintel block a boss gate hangs from: the portcullis that drops behind you into a miniboss's room. */
    public static final DeferredHolder<Block, Block> BOSS_GATE =
            BLOCKS.register("boss_gate", com.jastkub.frozenfortress.block.BossGateBlock::new);

    /** The shut door's invisible plane across the opening. */
    public static final DeferredHolder<Block, Block> DOOR_BARRIER =
            BLOCKS.register("door_barrier", com.jastkub.frozenfortress.block.DoorBarrierBlock::new);

    /** A secret's catch, disguised as carved stone. */
    public static final DeferredHolder<Block, Block> SECRET_SWITCH =
            BLOCKS.register("secret_switch", com.jastkub.frozenfortress.block.SecretSwitchBlock::new);

    /** False masonry a secret switch moves aside. Looks like the wall it is in. */
    public static final DeferredHolder<Block, Block> SECRET_STONE_BRICKS =
            BLOCKS.register("secret_stone_bricks", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).sound(SoundType.STONE).noLootTable()));
    public static final DeferredHolder<Block, Block> SECRET_DEEPSLATE_BRICKS =
            BLOCKS.register("secret_deepslate_bricks", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE).strength(-1.0F, 3600000.0F).sound(SoundType.DEEPSLATE_BRICKS).noLootTable()));

    /** The court's great banners: two wide, three tall, on an iron rod. */
    public static final DeferredHolder<Block, Block> GREAT_BANNER_ROYAL =
            BLOCKS.register("great_banner_royal", com.jastkub.frozenfortress.block.GreatBannerBlock::new);
    public static final DeferredHolder<Block, Block> GREAT_BANNER_STORM =
            BLOCKS.register("great_banner_storm", com.jastkub.frozenfortress.block.GreatBannerBlock::new);
    public static final DeferredHolder<Block, Block> GREAT_BANNER_MOURNING =
            BLOCKS.register("great_banner_mourning", com.jastkub.frozenfortress.block.GreatBannerBlock::new);
    public static final DeferredHolder<Block, Block> GREAT_BANNER_FROST =
            BLOCKS.register("great_banner_frost", com.jastkub.frozenfortress.block.GreatBannerBlock::new);
    public static final DeferredHolder<Block, Block> GREAT_BANNER_PRISONS =
            BLOCKS.register("great_banner_prisons", com.jastkub.frozenfortress.block.GreatBannerBlock::new);
    public static final DeferredHolder<Block, Block> GREAT_BANNER_ANCESTORS =
            BLOCKS.register("great_banner_ancestors", com.jastkub.frozenfortress.block.GreatBannerBlock::new);
    public static final DeferredHolder<Block, Block> GREAT_BANNER_DEPTHS =
            BLOCKS.register("great_banner_depths", com.jastkub.frozenfortress.block.GreatBannerBlock::new);
    public static final DeferredHolder<Block, Block> GREAT_BANNER_THRONE =
            BLOCKS.register("great_banner_throne", com.jastkub.frozenfortress.block.GreatBannerBlock::new);

    /** Railings of blackened iron. */
    public static final DeferredHolder<Block, Block> BLACK_IRON_FENCE =
            BLOCKS.register("black_iron_fence", () -> new net.minecraft.world.level.block.FenceBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.0F, 6.0F)
                            .requiresCorrectToolForDrops().sound(SoundType.CHAIN).noOcclusion()));

    /** The keyhole for the Sigil of the Hollow King. */
    public static final DeferredHolder<Block, Block> SIGIL_LOCK =
            BLOCKS.register("sigil_lock", com.jastkub.frozenfortress.block.SigilLockBlock::new);

    // ================= COURT FURNISHINGS =================

    /** Cathedral glass, deep blue and lit from within. */
    public static final DeferredHolder<Block, Block> GLACIAL_GLASS =
            BLOCKS.register("glacial_glass", com.jastkub.frozenfortress.block.FFDecoBlocks.GlacialGlass::new);
    public static final DeferredHolder<Block, Block> GLACIAL_GLASS_PANE =
            BLOCKS.register("glacial_glass_pane", com.jastkub.frozenfortress.block.FFDecoBlocks.GlacialGlassPane::new);
    /** Lantern of cold fire; stands or hangs. */
    public static final DeferredHolder<Block, Block> FROST_LANTERN =
            BLOCKS.register("frost_lantern", com.jastkub.frozenfortress.block.FFDecoBlocks.FrostLantern::new);
    /** Icicles that never fall. */
    public static final DeferredHolder<Block, Block> ICICLE_CLUSTER =
            BLOCKS.register("icicle_cluster", com.jastkub.frozenfortress.block.FFDecoBlocks.IcicleCluster::new);
    /** An iron candelabra two blocks high, five candles in cold flame. */
    public static final DeferredHolder<Block, Block> FROST_CANDELABRA =
            BLOCKS.register("frost_candelabra", com.jastkub.frozenfortress.block.FFDecoBlocks.FrostCandelabra::new);
    /** Three candles on a short stand, for a table or an altar. */
    public static final DeferredHolder<Block, Block> FROST_CANDLESTICK =
            BLOCKS.register("frost_candlestick", com.jastkub.frozenfortress.block.FFDecoBlocks.FrostCandlestick::new);
    /** A column of stone in flutes. */
    public static final DeferredHolder<Block, Block> FLUTED_STONE_PILLAR =
            BLOCKS.register("fluted_stone_pillar", () -> new net.minecraft.world.level.block.RotatedPillarBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops().sound(SoundType.STONE)));
    /** Hanging chandelier of frozen light. */
    public static final DeferredHolder<Block, Block> FROST_CHANDELIER =
            BLOCKS.register("frost_chandelier", com.jastkub.frozenfortress.block.FFDecoBlocks.FrostChandelier::new);

    /** The Stormcrown: a frozen beacon that empowers the fortress garrison. */
    public static final DeferredHolder<Block, Block> STORMCROWN_BEACON =
            BLOCKS.register("stormcrown_beacon",
                    com.jastkub.frozenfortress.block.StormcrownBeaconBlock::new);

    /** Storage / crafting block for Everfrost Shards. */
    public static final DeferredHolder<Block, Block> EVERFROST_BLOCK =
            BLOCKS.register("everfrost_block", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(3.0F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 7)));

    private FFBlocks() {
    }

    /** A floe of the Eye of the Storm (Velkhar's last phase): StormEyeArena builds and melts them. No item. */
    public static final DeferredHolder<Block, Block> STORM_EYE_FLOE =
            BLOCKS.register("storm_eye_floe", com.jastkub.frozenfortress.block.StormEyeFloeBlock::new);
}
