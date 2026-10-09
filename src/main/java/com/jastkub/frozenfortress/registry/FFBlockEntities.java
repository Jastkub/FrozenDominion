package com.jastkub.frozenfortress.registry;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.FrozenThroneBlockEntity;
import com.jastkub.frozenfortress.block.entity.StormcrownBeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class FFBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, FrozenFortress.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StormcrownBeaconBlockEntity>> STORMCROWN_BEACON =
            BLOCK_ENTITIES.register("stormcrown_beacon", () -> BlockEntityType.Builder
                    .of(StormcrownBeaconBlockEntity::new, FFBlocks.STORMCROWN_BEACON.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FrozenThroneBlockEntity>> FROZEN_THRONE =
            BLOCK_ENTITIES.register("frozen_throne", () -> BlockEntityType.Builder
                    .of(FrozenThroneBlockEntity::new, FFBlocks.FROZEN_THRONE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jastkub.frozenfortress.block.entity.CitadelLockBlockEntity>> CITADEL_LOCK =
            BLOCK_ENTITIES.register("citadel_lock", () -> BlockEntityType.Builder
                    .of(com.jastkub.frozenfortress.block.entity.CitadelLockBlockEntity::new, FFBlocks.CITADEL_LOCK.get(),
                            FFBlocks.SECRET_SWITCH.get())
                    .build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jastkub.frozenfortress.block.entity.CitadelSpawnerBlockEntity>> CITADEL_SPAWNER =
            BLOCK_ENTITIES.register("citadel_spawner", () -> BlockEntityType.Builder
                    .of(com.jastkub.frozenfortress.block.entity.CitadelSpawnerBlockEntity::new, FFBlocks.CITADEL_SPAWNER.get())
                    .build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jastkub.frozenfortress.block.entity.PrisonHeartBlockEntity>> PRISON_HEART =
            BLOCK_ENTITIES.register("prison_heart", () -> BlockEntityType.Builder
                    .of(com.jastkub.frozenfortress.block.entity.PrisonHeartBlockEntity::new, FFBlocks.PRISON_HEART.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jastkub.frozenfortress.block.entity.CitadelStatueBlockEntity>> CITADEL_STATUE =
            BLOCK_ENTITIES.register("citadel_statue", () -> BlockEntityType.Builder
                    .of(com.jastkub.frozenfortress.block.entity.CitadelStatueBlockEntity::new, FFBlocks.CITADEL_STATUE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jastkub.frozenfortress.block.entity.CitadelDoorBlockEntity>> CITADEL_DOOR =
            BLOCK_ENTITIES.register("citadel_door", () -> BlockEntityType.Builder
                    .of(com.jastkub.frozenfortress.block.entity.CitadelDoorBlockEntity::new, FFBlocks.CITADEL_DOOR.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jastkub.frozenfortress.block.entity.FrostHeartBlockEntity>> FROST_HEART =
            BLOCK_ENTITIES.register("frost_heart", () -> BlockEntityType.Builder
                    .of(com.jastkub.frozenfortress.block.entity.FrostHeartBlockEntity::new, FFBlocks.FROST_HEART.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jastkub.frozenfortress.block.entity.FrostCannonBlockEntity>> FROST_CANNON =
            BLOCK_ENTITIES.register("frost_cannon", () -> BlockEntityType.Builder
                    .of(com.jastkub.frozenfortress.block.entity.FrostCannonBlockEntity::new, FFBlocks.FROST_CANNON.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jastkub.frozenfortress.block.entity.StairWardBlockEntity>> STAIR_WARD =
            BLOCK_ENTITIES.register("stair_ward", () -> BlockEntityType.Builder
                    .of(com.jastkub.frozenfortress.block.entity.StairWardBlockEntity::new, FFBlocks.STAIR_WARD.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jastkub.frozenfortress.block.entity.IcicleTrapBlockEntity>> ICICLE_TRAP =
            BLOCK_ENTITIES.register("icicle_trap", () -> BlockEntityType.Builder
                    .of(com.jastkub.frozenfortress.block.entity.IcicleTrapBlockEntity::new, FFBlocks.ICICLE_TRAP.get())
                    .build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jastkub.frozenfortress.block.entity.ArrowSlitBlockEntity>> ARROW_SLIT =
            BLOCK_ENTITIES.register("arrow_slit", () -> BlockEntityType.Builder
                    .of(com.jastkub.frozenfortress.block.entity.ArrowSlitBlockEntity::new, FFBlocks.ARROW_SLIT.get())
                    .build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jastkub.frozenfortress.block.entity.AmbushBlockEntity>> AMBUSH =
            BLOCK_ENTITIES.register("ambush", () -> BlockEntityType.Builder
                    .of(com.jastkub.frozenfortress.block.entity.AmbushBlockEntity::new, FFBlocks.AMBUSH.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jastkub.frozenfortress.block.entity.BossGateBlockEntity>> BOSS_GATE =
            BLOCK_ENTITIES.register("boss_gate", () -> BlockEntityType.Builder
                    .of(com.jastkub.frozenfortress.block.entity.BossGateBlockEntity::new, FFBlocks.BOSS_GATE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jastkub.frozenfortress.block.entity.ChainedChestBlockEntity>> CHAINED_CHEST =
            BLOCK_ENTITIES.register("chained_chest", () -> BlockEntityType.Builder
                    .of(com.jastkub.frozenfortress.block.entity.ChainedChestBlockEntity::new, FFBlocks.CHAINED_CHEST.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jastkub.frozenfortress.block.entity.FrostShrineBlockEntity>> FROST_SHRINE =
            BLOCK_ENTITIES.register("frost_shrine", () -> BlockEntityType.Builder
                    .of(com.jastkub.frozenfortress.block.entity.FrostShrineBlockEntity::new, FFBlocks.FROST_SHRINE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jastkub.frozenfortress.block.entity.GreatLanternBlockEntity>> GREAT_LANTERN =
            BLOCK_ENTITIES.register("great_lantern", () -> BlockEntityType.Builder
                    .of(com.jastkub.frozenfortress.block.entity.GreatLanternBlockEntity::new, FFBlocks.GREAT_LANTERN.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jastkub.frozenfortress.block.entity.MonstrositySkullBlockEntity>> MONSTROSITY_SKULL =
            BLOCK_ENTITIES.register("monstrosity_skull", () -> BlockEntityType.Builder
                    .of(com.jastkub.frozenfortress.block.entity.MonstrositySkullBlockEntity::new, FFBlocks.MONSTROSITY_SKULL.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jastkub.frozenfortress.block.entity.PendulumAxeBlockEntity>> PENDULUM_AXE =
            BLOCK_ENTITIES.register("pendulum_axe", () -> BlockEntityType.Builder
                    .of(com.jastkub.frozenfortress.block.entity.PendulumAxeBlockEntity::new, FFBlocks.PENDULUM_AXE.get())
                    .build(null));

    private FFBlockEntities() {
    }
}
