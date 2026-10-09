package com.jastkub.frozenfortress.registry;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.FrostboundSentinelEntity;
import com.jastkub.frozenfortress.entity.FrostmawEntity;
import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import com.jastkub.frozenfortress.entity.RimeweaverEntity;
import com.jastkub.frozenfortress.entity.StillbowEntity;
import com.jastkub.frozenfortress.entity.VaultWardenEntity;
import com.jastkub.frozenfortress.entity.boss.FrozenTornadoEntity;
import com.jastkub.frozenfortress.entity.boss.ThrownBladeEntity;
import com.jastkub.frozenfortress.entity.boss.VelkharCloneEntity;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.jastkub.frozenfortress.entity.projectile.FrostBoltEntity;
import com.jastkub.frozenfortress.entity.projectile.IceArrowEntity;
import com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity;
import com.jastkub.frozenfortress.entity.projectile.SovereignBeamEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class FFEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, FrozenFortress.MODID);

    // ================= BOSS =================

    public static final DeferredHolder<EntityType<?>, EntityType<VelkharEntity>> VELKHAR =
            ENTITY_TYPES.register("velkhar", () -> EntityType.Builder.of(VelkharEntity::new, MobCategory.MONSTER)
                    .sized(1.6F, 4.2F)
                    .clientTrackingRange(12)
                    .fireImmune()
                    .build("velkhar"));

    /**
     * A mark burned onto the floor: the executioner's sigil, and the rift.
     *
     * <p>updateInterval 1 because the rift GROWS while it is being opened,
     * and a radius that only reaches the client every few ticks arrives as a
     * series of jumps rather than as something widening.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.effect.FloorSigilEntity>> FLOOR_SIGIL =
            ENTITY_TYPES.register("floor_sigil",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.effect.FloorSigilEntity>of(
                                    com.jastkub.frozenfortress.entity.effect.FloorSigilEntity::new, MobCategory.MISC)
                            .sized(0.2F, 0.2F).clientTrackingRange(16).updateInterval(1)
                            .fireImmune().build("floor_sigil"));

    /** A slab of floor torn loose and hurled. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.IceBoulderEntity>> ICE_BOULDER =
            ENTITY_TYPES.register("ice_boulder",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.IceBoulderEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.IceBoulderEntity::new, MobCategory.MISC)
                            .sized(1.8F, 1.8F).clientTrackingRange(12).updateInterval(1)
                            .fireImmune().build("ice_boulder"));

    /** A ward that has to be broken before he can be touched. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.IceWardPillarEntity>> ICE_WARD_PILLAR =
            ENTITY_TYPES.register("ice_ward_pillar",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.IceWardPillarEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.IceWardPillarEntity::new, MobCategory.MISC)
                            .sized(1.1F, 3.2F).clientTrackingRange(16).updateInterval(2)
                            .fireImmune().build("ice_ward_pillar"));

    /** One of the three wards turning round him in the last phase. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.SpiritWardEntity>> SPIRIT_WARD =
            ENTITY_TYPES.register("spirit_ward",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.SpiritWardEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.SpiritWardEntity::new, MobCategory.MISC)
                            // updateInterval 1: it is on a circle, and at 2 the
                            // orbit arrives as a stutter rather than a turn.
                            .sized(1.3F, 1.8F).clientTrackingRange(16).updateInterval(1)
                            .fireImmune().build("spirit_ward"));

    /** A marked circle with a column of cold coming up through it. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.FrostStrikeEntity>> FROST_STRIKE =
            ENTITY_TYPES.register("frost_strike",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.FrostStrikeEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.FrostStrikeEntity::new, MobCategory.MISC)
                            .sized(0.2F, 0.2F).clientTrackingRange(16).updateInterval(20)
                            .fireImmune().build("frost_strike"));

    /** One orb of the ring he circles himself with. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.IceOrbEntity>> ICE_ORB =
            ENTITY_TYPES.register("ice_orb",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.IceOrbEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.IceOrbEntity::new, MobCategory.MISC)
                            .sized(0.75F, 0.75F).clientTrackingRange(12).updateInterval(1)
                            .fireImmune().build("ice_orb"));

    /** One of the three seekers that chase a player down and freeze them. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.HunterOrbEntity>> HUNTER_ORB =
            ENTITY_TYPES.register("hunter_orb",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.HunterOrbEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.HunterOrbEntity::new, MobCategory.MISC)
                            .sized(0.8F, 0.8F).clientTrackingRange(16).updateInterval(1)
                            .fireImmune().build("hunter_orb"));

    /** The breakable block of ice a seeker leaves its victim standing in. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.IcePrisonEntity>> ICE_PRISON =
            ENTITY_TYPES.register("ice_prison",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.IcePrisonEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.IcePrisonEntity::new, MobCategory.MISC)
                            .sized(1.2F, 2.2F).clientTrackingRange(16).updateInterval(1)
                            .fireImmune().build("ice_prison"));

    /** One pulse of mending on its way from a ward to the king. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.WardSignalEntity>> WARD_SIGNAL =
            ENTITY_TYPES.register("ward_signal",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.WardSignalEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.WardSignalEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F).clientTrackingRange(20).updateInterval(1)
                            .fireImmune().build("ward_signal"));

    /** The fog on the floor before the stones come out of it. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.GroundMistEntity>> GROUND_MIST =
            ENTITY_TYPES.register("ground_mist",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.GroundMistEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.GroundMistEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F).clientTrackingRange(24).updateInterval(20)
                            .fireImmune().build("ground_mist"));

    /** The charge running through one patch of that fog. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.SpotArcEntity>> SPOT_ARC =
            ENTITY_TYPES.register("spot_arc",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.SpotArcEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.SpotArcEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F).clientTrackingRange(20).updateInterval(2)
                            .fireImmune().build("spot_arc"));

    /** The spire he rides up when the greatsword comes apart. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.IceTowerEntity>> ICE_TOWER =
            ENTITY_TYPES.register("ice_tower",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.IceTowerEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.IceTowerEntity::new, MobCategory.MISC)
                            .sized(2.6F, 9.0F).clientTrackingRange(32).updateInterval(2)
                            .fireImmune().build("ice_tower"));

    /** Decorative floor debris thrown up by heavy impacts. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.effect.FallingDebrisEntity>> FALLING_DEBRIS =
            ENTITY_TYPES.register("falling_debris",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.effect.FallingDebrisEntity>of(
                                    com.jastkub.frozenfortress.entity.effect.FallingDebrisEntity::new,
                                    MobCategory.MISC)
                            .sized(0.85F, 0.85F)
                            .clientTrackingRange(10)
                            .updateInterval(20)
                            .fireImmune()
                            .build("falling_debris"));

    public static final DeferredHolder<EntityType<?>, EntityType<VelkharCloneEntity>> VELKHAR_CLONE =
            ENTITY_TYPES.register("velkhar_clone", () -> EntityType.Builder.of(VelkharCloneEntity::new, MobCategory.MONSTER)
                    .sized(1.6F, 4.2F)
                    .clientTrackingRange(12)
                    .fireImmune()
                    .build("velkhar_clone"));

    public static final DeferredHolder<EntityType<?>, EntityType<FrozenTornadoEntity>> FROZEN_TORNADO =
            ENTITY_TYPES.register("frozen_tornado", () -> EntityType.Builder.<FrozenTornadoEntity>of(FrozenTornadoEntity::new, MobCategory.MISC)
                    .sized(2.4F, 5.0F)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build("frozen_tornado"));

    /** The executioner's greatsword that falls on a slowed target. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.DoomBladeEntity>> DOOM_BLADE =
            ENTITY_TYPES.register("doom_blade",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.DoomBladeEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.DoomBladeEntity::new, MobCategory.MISC)
                            .sized(1.6F, 4.2F)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .fireImmune()
                            .noSummon()
                            .build("doom_blade"));

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownBladeEntity>> THROWN_BLADE =
            ENTITY_TYPES.register("thrown_blade", () -> EntityType.Builder.<ThrownBladeEntity>of(ThrownBladeEntity::new, MobCategory.MISC)
                    .sized(1.4F, 0.5F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .fireImmune()
                    .build("thrown_blade"));

    // ================= SERVANTS =================

    public static final DeferredHolder<EntityType<?>, EntityType<FrostboundSentinelEntity>> FROSTBOUND_SENTINEL =
            ENTITY_TYPES.register("frostbound_sentinel", () -> EntityType.Builder.of(FrostboundSentinelEntity::new, MobCategory.MONSTER)
                    .sized(0.85F, 2.4F)
                    .clientTrackingRange(10)
                    .build("frostbound_sentinel"));

    public static final DeferredHolder<EntityType<?>, EntityType<RimeweaverEntity>> RIMEWEAVER =
            ENTITY_TYPES.register("rimeweaver", () -> EntityType.Builder.of(RimeweaverEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.2F)
                    .clientTrackingRange(10)
                    .build("rimeweaver"));

    public static final DeferredHolder<EntityType<?>, EntityType<StillbowEntity>> STILLBOW =
            ENTITY_TYPES.register("stillbow", () -> EntityType.Builder.of(StillbowEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.1F)
                    .clientTrackingRange(10)
                    .build("stillbow"));

    /** The dead of the citadel, got up again: weak, clumsy, never alone. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.FrostSkeletonEntity>> FROST_SKELETON =
            ENTITY_TYPES.register("frost_skeleton", () -> EntityType.Builder.of(
                            com.jastkub.frozenfortress.entity.FrostSkeletonEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("frost_skeleton"));

    public static final DeferredHolder<EntityType<?>, EntityType<FrostmawEntity>> FROSTMAW =
            ENTITY_TYPES.register("frostmaw", () -> EntityType.Builder.of(FrostmawEntity::new, MobCategory.MONSTER)
                    .sized(1.5F, 1.6F)
                    .clientTrackingRange(10)
                    .build("frostmaw"));

    /** The Frost Rider: one of the guard's dead in a Frostmaw's saddle, throwing snowballs. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.FrostRiderEntity>> FROST_RIDER =
            ENTITY_TYPES.register("frost_rider", () -> EntityType.Builder.of(
                            com.jastkub.frozenfortress.entity.FrostRiderEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("frost_rider"));

    /** Its snowball: packed hard, a thing with a body (FrostSnowballRenderer). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.projectile.FrostSnowballEntity>> FROST_SNOWBALL =
            ENTITY_TYPES.register("frost_snowball", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.projectile.FrostSnowballEntity>of(
                            com.jastkub.frozenfortress.entity.projectile.FrostSnowballEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(10).build("frost_snowball"));

    /** A flask of warmth in flight (thrown, 08.10.2026): drawn as the flask itself (ThrownItemRenderer). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.projectile.ThrownWarmthEntity>> THROWN_WARMTH =
            ENTITY_TYPES.register("thrown_warmth", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.projectile.ThrownWarmthEntity>of(
                            com.jastkub.frozenfortress.entity.projectile.ThrownWarmthEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build("thrown_warmth"));

    /** Its burst, only to be seen (WarmthSplashRenderer). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.WarmthSplashEntity>> WARMTH_SPLASH =
            ENTITY_TYPES.register("warmth_splash", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.WarmthSplashEntity>of(
                            com.jastkub.frozenfortress.entity.WarmthSplashEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(20).fireImmune().build("warmth_splash"));

    /**
     * Hrimthar, the Buried Colossus. Six blocks across and eight tall, which
     * is what the geometry measures in the pose it actually stands in - a
     * hitbox smaller than the silhouette is the classic way a big thing
     * starts feeling like a hologram.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<HollowGolemEntity>> HOLLOW_GOLEM =
            ENTITY_TYPES.register("ice_monstrosity", () -> EntityType.Builder.of(HollowGolemEntity::new, MobCategory.MONSTER)
                    // Re-measured after the re-proportioning: 7.75 blocks tall
                    // and 9.3 across, because this animal is WIDER THAN IT IS
                    // TALL - short legs, slab chest, arms hung far outboard.
                    // The box stays well inside that span on purpose. A hitbox
                    // as wide as the arms would be mostly full of the daylight
                    // between limb and body, and it still has to walk through
                    // its own fortress.
                    // RE-MEASURED after the shapes were rebuilt off the
                    // reference scan: 6.25 blocks to the top of the canted
                    // fins and 9.38 across. The box stops at 6.0 because it
                    // has a second job - the colossus has to walk through its
                    // own fortress - and noCulling covers the quarter block of
                    // fin that pokes out; see HollowGolemEntity's constructor.
                    .sized(5.6F, 6.0F)
                    .clientTrackingRange(16)
                    .fireImmune()
                    // NO noSummon(). It sets canSummon=false, which is the flag
                    // the /summon command itself checks - so the colossus could
                    // not be spawned by hand at all, and there was no way to
                    // look at it outside a live fight at fifteen percent health.
                    .build("ice_monstrosity"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.TurnkeyEntity>> TURNKEY =
            ENTITY_TYPES.register("turnkey", () -> EntityType.Builder.of(com.jastkub.frozenfortress.entity.TurnkeyEntity::new,
                            MobCategory.MONSTER)
                    .sized(1.3F, 3.1F)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build("turnkey"));

    /** WLADCA KOSCI, the Chasm of Bones' miniboss (08.10.2026): the Frost Skeleton's rig two and a half times over. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.BoneLordEntity>> BONE_LORD =
            ENTITY_TYPES.register("bone_lord", () -> EntityType.Builder.of(com.jastkub.frozenfortress.entity.BoneLordEntity::new,
                            MobCategory.MONSTER)
                    .sized(2.4F, 12.0F)
                    .clientTrackingRange(12)
                    .fireImmune()
                    .build("bone_lord"));

    /** THE LAMPLIGHTER: keeper of the watchtower's great lantern (LamplighterEntity). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.LamplighterEntity>> LAMPLIGHTER =
            ENTITY_TYPES.register("lamplighter", () -> EntityType.Builder.of(com.jastkub.frozenfortress.entity.LamplighterEntity::new,
                            MobCategory.MONSTER)
                    .sized(0.9F, 2.9F)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build("lamplighter"));

    /** A key off the Turnkey's ring, flung to a lock in his hall. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.TurnkeyKeyEntity>> TURNKEY_KEY =
            ENTITY_TYPES.register("turnkey_key", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.TurnkeyKeyEntity>of(
                            com.jastkub.frozenfortress.entity.TurnkeyKeyEntity::new, MobCategory.MISC)
                    .sized(0.7F, 0.7F).clientTrackingRange(10).fireImmune().build("turnkey_key"));

    /** A grate dropped out of the vault: four of them make a cell. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.PortcullisEntity>> PORTCULLIS =
            ENTITY_TYPES.register("portcullis", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.PortcullisEntity>of(
                            com.jastkub.frozenfortress.entity.PortcullisEntity::new, MobCategory.MISC)
                    .sized(3.0F, 4.0F).clientTrackingRange(10).fireImmune().build("portcullis"));

    /** The Turnkey's manacle on its chain. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.projectile.TurnkeyManacleEntity>> TURNKEY_MANACLE =
            ENTITY_TYPES.register("turnkey_manacle", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.projectile.TurnkeyManacleEntity>of(
                            com.jastkub.frozenfortress.entity.projectile.TurnkeyManacleEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(1).fireImmune().build("turnkey_manacle"));

    /** A candle of ice the Priestess raises: her ward while it burns. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.FrostCandleEntity>> FROST_CANDLE =
            ENTITY_TYPES.register("frost_candle", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.FrostCandleEntity>of(
                            com.jastkub.frozenfortress.entity.FrostCandleEntity::new, MobCategory.MISC)
                    .sized(0.7F, 1.9F).clientTrackingRange(10).fireImmune().build("frost_candle"));

    /** KAPLANKA SZRONU: keeper of the Chapel of Rime and of the Archive's key. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.RimePriestessEntity>> RIME_PRIESTESS =
            ENTITY_TYPES.register("rime_priestess", () -> EntityType.Builder.of(
                            com.jastkub.frozenfortress.entity.RimePriestessEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 3.0F).clientTrackingRange(10).fireImmune().build("rime_priestess"));

    /** A rune of the Priestess's Litany, three blocks of the chapel's floor. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.LitanyRuneEntity>> LITANY_RUNE =
            ENTITY_TYPES.register("litany_rune", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.LitanyRuneEntity>of(
                            com.jastkub.frozenfortress.entity.LitanyRuneEntity::new, MobCategory.MISC)
                    .sized(3.0F, 0.1F).clientTrackingRange(10).updateInterval(20).fireImmune().build("litany_rune"));

    /** A leaf torn from the Priestess's book. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.FrostPageEntity>> FROST_PAGE =
            ENTITY_TYPES.register("frost_page", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.FrostPageEntity>of(
                            com.jastkub.frozenfortress.entity.FrostPageEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.4F).clientTrackingRange(10).updateInterval(1).fireImmune().build("frost_page"));

    /** A wave of frost running out over the floor in a ring (drawn as geometry). */
    /** A block of the cisterns' stair going down into the water (StairWardBlockEntity). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.StairDebrisEntity>> STAIR_DEBRIS =
            ENTITY_TYPES.register("stair_debris", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.StairDebrisEntity>of(
                            com.jastkub.frozenfortress.entity.StairDebrisEntity::new, MobCategory.MISC)
                    .sized(0.98F, 0.98F).clientTrackingRange(10).updateInterval(20).build("stair_debris"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.FrostWaveEntity>> FROST_WAVE =
            ENTITY_TYPES.register("frost_wave", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.FrostWaveEntity>of(
                            com.jastkub.frozenfortress.entity.FrostWaveEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(20).fireImmune().build("frost_wave"));

    /** The Monstrosity's glacier javelin: a missile, then a pillar its charge breaks on. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.projectile.IceJavelinEntity>> ICE_JAVELIN =
            ENTITY_TYPES.register("ice_javelin", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.projectile.IceJavelinEntity>of(
                            com.jastkub.frozenfortress.entity.projectile.IceJavelinEntity::new, MobCategory.MISC)
                    .sized(1.4F, 3.2F).clientTrackingRange(10).updateInterval(1).fireImmune().build("ice_javelin"));

    /** The Monstrosity's bomb: a crystal of ice in its own light, thrown in an arc. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.projectile.IceBombEntity>> ICE_BOMB =
            ENTITY_TYPES.register("ice_bomb", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.projectile.IceBombEntity>of(
                            com.jastkub.frozenfortress.entity.projectile.IceBombEntity::new, MobCategory.MISC)
                    .sized(0.9F, 0.9F).clientTrackingRange(10).updateInterval(1).fireImmune().build("ice_bomb"));

    /** A frost maw's bomb (07.10.2026): the Monstrosity's to look at, harmless but for the hearths it douses. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.projectile.MawBombEntity>> MAW_BOMB =
            ENTITY_TYPES.register("maw_bomb", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.projectile.MawBombEntity>of(
                            com.jastkub.frozenfortress.entity.projectile.MawBombEntity::new, MobCategory.MISC)
                    .sized(0.9F, 0.9F).clientTrackingRange(10).updateInterval(1).fireImmune().build("maw_bomb"));

    /** The Monstrosity's avalanche: a wall of snow and ice rolling along the floor (AvalancheEntity). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.projectile.AvalancheEntity>> AVALANCHE =
            ENTITY_TYPES.register("avalanche", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.projectile.AvalancheEntity>of(
                            com.jastkub.frozenfortress.entity.projectile.AvalancheEntity::new, MobCategory.MISC)
                    .sized(2.0F, 2.6F).clientTrackingRange(10).updateInterval(2).fireImmune().noSave().build("avalanche"));

    /** A gout of the Monstrosity's trough, thrown up by its geyser: a frozen pool where it lands. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.projectile.FrostGlobEntity>> FROST_GLOB =
            ENTITY_TYPES.register("frost_glob", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.projectile.FrostGlobEntity>of(
                            com.jastkub.frozenfortress.entity.projectile.FrostGlobEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.8F).clientTrackingRange(10).updateInterval(1).fireImmune().build("frost_glob"));

    /** A shard of a burst bomb's shell: a frozen pool where it lands. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.projectile.IceBombEntity.IceFragmentEntity>> ICE_FRAGMENT =
            ENTITY_TYPES.register("ice_fragment", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.projectile.IceBombEntity.IceFragmentEntity>of(
                            com.jastkub.frozenfortress.entity.projectile.IceBombEntity.IceFragmentEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(1).fireImmune().build("ice_fragment"));

    /** An icicle of the avalanche, falling on its shadow. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.projectile.FallingIcicleEntity>> FALLING_ICICLE =
            ENTITY_TYPES.register("falling_icicle", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.projectile.FallingIcicleEntity>of(
                            com.jastkub.frozenfortress.entity.projectile.FallingIcicleEntity::new, MobCategory.MISC)
                    .sized(1.0F, 3.0F).clientTrackingRange(10).updateInterval(1).fireImmune().build("falling_icicle"));

    /** An icicle of a trap's ceiling (07.10.2026): the avalanche's to look at, a trap's hurt and nothing more. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.projectile.TrapIcicleEntity>> TRAP_ICICLE =
            ENTITY_TYPES.register("trap_icicle", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.projectile.TrapIcicleEntity>of(
                            com.jastkub.frozenfortress.entity.projectile.TrapIcicleEntity::new, MobCategory.MISC)
                    .sized(1.0F, 3.0F).clientTrackingRange(10).updateInterval(1).fireImmune().build("trap_icicle"));

    /** An arrow slit's bolt (07.10.2026): the Stillbow's arrow to look at, a little hurt and nothing more. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.projectile.TrapArrowEntity>> TRAP_ARROW =
            ENTITY_TYPES.register("trap_arrow", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.projectile.TrapArrowEntity>of(
                            com.jastkub.frozenfortress.entity.projectile.TrapArrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(1).build("trap_arrow"));

    /** A frozen pool of the geyser's water. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.FrostPuddleEntity>> FROST_PUDDLE =
            ENTITY_TYPES.register("frost_puddle", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.FrostPuddleEntity>of(
                            com.jastkub.frozenfortress.entity.FrostPuddleEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.2F).clientTrackingRange(10).updateInterval(20).fireImmune().build("frost_puddle"));

    /** The bell of penance. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.PenanceBellEntity>> PENANCE_BELL =
            ENTITY_TYPES.register("penance_bell", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.PenanceBellEntity>of(
                            com.jastkub.frozenfortress.entity.PenanceBellEntity::new, MobCategory.MISC)
                    .sized(1.4F, 2.0F).clientTrackingRange(10).updateInterval(1).fireImmune().build("penance_bell"));

    /** A reflection of the Priestess, out of her veil. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.PriestessMirrorEntity>> PRIESTESS_MIRROR =
            ENTITY_TYPES.register("priestess_mirror", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.PriestessMirrorEntity>of(
                            com.jastkub.frozenfortress.entity.PriestessMirrorEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 3.0F).clientTrackingRange(10).fireImmune().build("priestess_mirror"));

    public static final DeferredHolder<EntityType<?>, EntityType<VaultWardenEntity>> VAULT_WARDEN =
            ENTITY_TYPES.register("vault_warden", () -> EntityType.Builder.of(VaultWardenEntity::new, MobCategory.MONSTER)
                    .sized(1.4F, 3.2F)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build("vault_warden"));

    // ================= PROJECTILES =================

    public static final DeferredHolder<EntityType<?>, EntityType<FrostBoltEntity>> FROST_BOLT =
            ENTITY_TYPES.register("frost_bolt", () -> EntityType.Builder.<FrostBoltEntity>of(FrostBoltEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(6)
                    .updateInterval(2)
                    .build("frost_bolt"));

    public static final DeferredHolder<EntityType<?>, EntityType<IceArrowEntity>> ICE_ARROW =
            ENTITY_TYPES.register("ice_arrow", () -> EntityType.Builder.<IceArrowEntity>of(IceArrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(6)
                    .updateInterval(2)
                    .build("ice_arrow"));

    public static final DeferredHolder<EntityType<?>, EntityType<IceSpikeEntity>> ICE_SPIKE =
            ENTITY_TYPES.register("ice_spike", () -> EntityType.Builder.<IceSpikeEntity>of(IceSpikeEntity::new, MobCategory.MISC)
                    .sized(1.0F, 2.2F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .fireImmune()
                    .build("ice_spike"));

    /** The channelled beam. Updated every tick because it tracks a live view. */
    public static final DeferredHolder<EntityType<?>, EntityType<SovereignBeamEntity>> SOVEREIGN_BEAM =
            ENTITY_TYPES.register("sovereign_beam", () -> EntityType.Builder.<SovereignBeamEntity>of(SovereignBeamEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .noSummon()
                    .fireImmune()
                    .build("sovereign_beam"));

    /** A shard out of his open chest, trailing its own shadow. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.ShadowShardEntity>> SHADOW_SHARD =
            ENTITY_TYPES.register("shadow_shard",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.ShadowShardEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.ShadowShardEntity::new, MobCategory.MISC)
                            .sized(0.45F, 0.45F).clientTrackingRange(12).updateInterval(1)
                            .fireImmune().build("shadow_shard"));

    /**
     * The gate's shadow riding the shield wave. Cosmetic only - no damage, no
     * collision; the ring in VelkharEntity is still what hurts.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.ShieldEchoEntity>> SHIELD_ECHO =
            ENTITY_TYPES.register("shield_echo",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.ShieldEchoEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.ShieldEchoEntity::new, MobCategory.MISC)
                            // tracking range is generous because it ends up
                            // fourteen blocks from where it was spawned
                            .sized(0.6F, 0.6F).clientTrackingRange(32).updateInterval(1)
                            .fireImmune().build("shield_echo"));

    /**
     * The blade that comes up out of the floor where the player was standing.
     * It carries its own damage - it outlives the tick it was spawned on.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.GraveBladeEntity>> GRAVE_BLADE =
            ENTITY_TYPES.register("grave_blade",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.GraveBladeEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.GraveBladeEntity::new, MobCategory.MISC)
                            .sized(1.0F, 3.2F).clientTrackingRange(48).updateInterval(1)
                            .fireImmune().noSummon().build("grave_blade"));

    /** A piece of his plate, loose in the room after the armour lets go. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.ArmourShardEntity>> ARMOUR_SHARD =
            ENTITY_TYPES.register("armour_shard",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.ArmourShardEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.ArmourShardEntity::new, MobCategory.MISC)
                            .sized(0.4F, 0.3F).clientTrackingRange(24).updateInterval(2)
                            .fireImmune().build("armour_shard"));

    /** One spear out of the cloud. Origin is the tip; the shaft stands above it. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.IceSpearEntity>> ICE_SPEAR =
            ENTITY_TYPES.register("ice_spear",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.IceSpearEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.IceSpearEntity::new, MobCategory.MISC)
                            .sized(0.4F, 1.25F).clientTrackingRange(16).updateInterval(1)
                            .fireImmune().build("ice_spear"));

    /** The silhouette he leaves behind when he blinks. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.VelkharAfterimageEntity>> VELKHAR_AFTERIMAGE =
            ENTITY_TYPES.register("velkhar_afterimage",
                    () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.VelkharAfterimageEntity>of(
                                    com.jastkub.frozenfortress.entity.boss.VelkharAfterimageEntity::new,
                                    MobCategory.MISC)
                            .sized(1.6F, 4.2F).clientTrackingRange(16).updateInterval(20)
                            .noSummon().fireImmune().build("velkhar_afterimage"));

    /** An attack's picture with a body of its own - a ring, a ward, a mark, a burst (AttackFxEntity, by kind). */
    /** The air bent by the Monstrosity's roar (RoarWarpEntity; drawn by client.RoarWarpFx). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.RoarWarpEntity>> ROAR_WARP =
            ENTITY_TYPES.register("roar_warp", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.RoarWarpEntity>of(
                            com.jastkub.frozenfortress.entity.RoarWarpEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(4).fireImmune().noSave().noSummon()
                    .build("roar_warp"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.AttackFxEntity>> ATTACK_FX =
            ENTITY_TYPES.register("attack_fx", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.AttackFxEntity>of(
                            com.jastkub.frozenfortress.entity.AttackFxEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(2).fireImmune().noSave().noSummon()
                    .build("attack_fx"));

    /** The Rimeweaver's bolt: a shuttle of ice. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.projectile.RimeShuttleEntity>> RIME_SHUTTLE =
            ENTITY_TYPES.register("rime_shuttle", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.projectile.RimeShuttleEntity>of(
                            com.jastkub.frozenfortress.entity.projectile.RimeShuttleEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(1).fireImmune().build("rime_shuttle"));

    /** The Rimeweaver's thorns, out of the floor. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.projectile.RimeThornEntity>> RIME_THORN =
            ENTITY_TYPES.register("rime_thorn", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.projectile.RimeThornEntity>of(
                            com.jastkub.frozenfortress.entity.projectile.RimeThornEntity::new, MobCategory.MISC)
                    .sized(0.8F, 1.0F).clientTrackingRange(10).updateInterval(20).fireImmune().build("rime_thorn"));

    /** Woven into rime: what the Rimeweaver's circle does to whoever stays in it. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.RimeBindEntity>> RIME_BIND =
            ENTITY_TYPES.register("rime_bind", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.RimeBindEntity>of(
                            com.jastkub.frozenfortress.entity.boss.RimeBindEntity::new, MobCategory.MISC)
                    .sized(1.2F, 2.2F).clientTrackingRange(16).updateInterval(1).fireImmune().build("rime_bind"));

    /** A grate of the Vault Warden's lock: eight of them close the vault on you. */
    // ---- THE DROWNED LADY (Topielica) of the Frozen Cisterns, and the things of her fight (06.10.2026)

    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.DrownedLadyEntity>> DROWNED_LADY =
            ENTITY_TYPES.register("drowned_lady", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.DrownedLadyEntity>of(
                            com.jastkub.frozenfortress.entity.DrownedLadyEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.6F).clientTrackingRange(10).fireImmune()
                    .build("drowned_lady"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.DrownedIcePlateEntity>> DROWNED_ICE_PLATE =
            ENTITY_TYPES.register("drowned_ice_plate", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.DrownedIcePlateEntity>of(
                            com.jastkub.frozenfortress.entity.DrownedIcePlateEntity::new, MobCategory.MISC)
                    .sized(3.0F, 0.25F).clientTrackingRange(10).updateInterval(20).fireImmune().noSummon()
                    .build("drowned_ice_plate"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.DrownedShadowEntity>> DROWNED_SHADOW =
            ENTITY_TYPES.register("drowned_shadow", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.DrownedShadowEntity>of(
                            com.jastkub.frozenfortress.entity.DrownedShadowEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.2F).clientTrackingRange(10).updateInterval(1).fireImmune().noSave().noSummon()
                    .build("drowned_shadow"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.DrownedHandsEntity>> DROWNED_HANDS =
            ENTITY_TYPES.register("drowned_hands", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.DrownedHandsEntity>of(
                            com.jastkub.frozenfortress.entity.DrownedHandsEntity::new, MobCategory.MISC)
                    .sized(1.2F, 1.8F).clientTrackingRange(10).updateInterval(2).fireImmune().noSave().noSummon()
                    .build("drowned_hands"));

    /** The lane of the Monstrosity's charge, drawn on the floor before it (07.10.2026). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.ChargeLaneEntity>> CHARGE_LANE =
            ENTITY_TYPES.register("charge_lane", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.ChargeLaneEntity>of(
                            com.jastkub.frozenfortress.entity.ChargeLaneEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.1F).clientTrackingRange(10).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("charge_lane"));
    /** PULS MROKU: the Shade Shepherd's ring of shadow (07.10.2026). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.ShadePulseEntity>> SHADE_PULSE =
            ENTITY_TYPES.register("shade_pulse", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.ShadePulseEntity>of(
                            com.jastkub.frozenfortress.entity.ShadePulseEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("shade_pulse"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.DrownedTideEntity>> DROWNED_TIDE =
            ENTITY_TYPES.register("drowned_tide", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.DrownedTideEntity>of(
                            com.jastkub.frozenfortress.entity.DrownedTideEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("drowned_tide"));

    /** THE ICE AUROCHS (Lodowy Tur) of the Frost Heart's hall, and the ring of ice his stomp sends out. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.IceAurochsEntity>> ICE_AUROCHS =
            ENTITY_TYPES.register("ice_aurochs", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.IceAurochsEntity>of(
                            com.jastkub.frozenfortress.entity.IceAurochsEntity::new, MobCategory.MONSTER)
                    .sized(2.8F, 2.9F).clientTrackingRange(10).fireImmune()
                    .build("ice_aurochs"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.IceAurochsRingEntity>> ICE_AUROCHS_RING =
            ENTITY_TYPES.register("ice_aurochs_ring", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.IceAurochsRingEntity>of(
                            com.jastkub.frozenfortress.entity.IceAurochsRingEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("ice_aurochs_ring"));

    /** THE SHADE SHEPHERD (Pasterz Cieni) of the Lightless Chambers, his herd, his decoy, his breath. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.ShadeShepherdEntity>> SHADE_SHEPHERD =
            ENTITY_TYPES.register("shade_shepherd", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.ShadeShepherdEntity>of(
                            com.jastkub.frozenfortress.entity.ShadeShepherdEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.9F).clientTrackingRange(10).updateInterval(1).fireImmune().build("shade_shepherd"));   // (every tick: his dash)
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.ShadeEntity>> SHADE =
            ENTITY_TYPES.register("shade", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.ShadeEntity>of(
                            com.jastkub.frozenfortress.entity.ShadeEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 1.1F).clientTrackingRange(10).fireImmune().build("shade"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.ShadeShepherdDecoyEntity>> SHADE_SHEPHERD_DECOY =
            ENTITY_TYPES.register("shade_shepherd_decoy", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.ShadeShepherdDecoyEntity>of(
                            com.jastkub.frozenfortress.entity.ShadeShepherdDecoyEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.9F).clientTrackingRange(10).fireImmune().noSave().noSummon().build("shade_shepherd_decoy"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.ShadeShepherdGustEntity>> SHADE_SHEPHERD_GUST =
            ENTITY_TYPES.register("shade_shepherd_gust", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.ShadeShepherdGustEntity>of(
                            com.jastkub.frozenfortress.entity.ShadeShepherdGustEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.8F).clientTrackingRange(10).updateInterval(1).fireImmune().noSave().noSummon().build("shade_shepherd_gust"));
    /** The shadows the Shepherd leaves behind him in his dash (08.10.2026). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.ShadeAfterimageEntity>> SHADE_AFTERIMAGE =
            ENTITY_TYPES.register("shade_afterimage", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.ShadeAfterimageEntity>of(
                            com.jastkub.frozenfortress.entity.ShadeAfterimageEntity::new, MobCategory.MISC)
                    .sized(0.9F, 2.9F).clientTrackingRange(8).updateInterval(20).fireImmune().noSave().noSummon().build("shade_afterimage"));

    /** THE FORGE OVERSEER (Nadzorca Kuzni), and the things of his fight. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.ForgeOverseerEntity>> FORGE_OVERSEER =
            ENTITY_TYPES.register("forge_overseer", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.ForgeOverseerEntity>of(
                            com.jastkub.frozenfortress.entity.ForgeOverseerEntity::new, MobCategory.MONSTER)
                    .sized(1.6F, 3.6F).clientTrackingRange(10).fireImmune().build("forge_overseer"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.ForgeOverseerPlateEntity>> FORGE_OVERSEER_PLATE =
            ENTITY_TYPES.register("forge_overseer_plate", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.ForgeOverseerPlateEntity>of(
                            com.jastkub.frozenfortress.entity.ForgeOverseerPlateEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.3F).clientTrackingRange(10).updateInterval(2).fireImmune().noSave().noSummon().build("forge_overseer_plate"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.ForgeOverseerSlagEntity>> FORGE_OVERSEER_SLAG =
            ENTITY_TYPES.register("forge_overseer_slag", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.ForgeOverseerSlagEntity>of(
                            com.jastkub.frozenfortress.entity.ForgeOverseerSlagEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(1).fireImmune().noSave().build("forge_overseer_slag"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.ForgeOverseerSteamEntity>> FORGE_OVERSEER_STEAM =
            ENTITY_TYPES.register("forge_overseer_steam", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.ForgeOverseerSteamEntity>of(
                            com.jastkub.frozenfortress.entity.ForgeOverseerSteamEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(20).fireImmune().noSave().noSummon().build("forge_overseer_steam"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.ForgeOverseerShockwaveEntity>> FORGE_OVERSEER_SHOCKWAVE =
            ENTITY_TYPES.register("forge_overseer_shockwave", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.ForgeOverseerShockwaveEntity>of(
                            com.jastkub.frozenfortress.entity.ForgeOverseerShockwaveEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(20).fireImmune().noSave().noSummon().build("forge_overseer_shockwave"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.ForgeOverseerRimeEntity>> FORGE_OVERSEER_RIME =
            ENTITY_TYPES.register("forge_overseer_rime", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.ForgeOverseerRimeEntity>of(
                            com.jastkub.frozenfortress.entity.ForgeOverseerRimeEntity::new, MobCategory.MISC)
                    .sized(1.0F, 2.0F).clientTrackingRange(10).updateInterval(1).fireImmune().noSave().noSummon().build("forge_overseer_rime"));

    /** The king's smith, chained in his cell off the forge (VelkharSmithEntity). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.VelkharSmithEntity>> VELKHAR_SMITH =
            ENTITY_TYPES.register("velkhar_smith", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.VelkharSmithEntity>of(
                            com.jastkub.frozenfortress.entity.VelkharSmithEntity::new, MobCategory.MISC)
                    .sized(0.9F, 2.2F).clientTrackingRange(10).fireImmune().build("velkhar_smith"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.VaultBarEntity>> VAULT_BAR =
            ENTITY_TYPES.register("vault_bar", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.VaultBarEntity>of(
                            com.jastkub.frozenfortress.entity.VaultBarEntity::new, MobCategory.MISC)
                    .sized(1.0F, 3.5F).clientTrackingRange(10).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("vault_bar"));

    /** Zmora Tronu's crescent and Throne-slayer slash (ThroneBaneWaveEntity). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.ThroneBaneWaveEntity>> THRONE_BANE_WAVE =
            ENTITY_TYPES.register("fx_throne_bane_wave", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.ThroneBaneWaveEntity>of(
                            com.jastkub.frozenfortress.entity.ThroneBaneWaveEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(8).updateInterval(2).fireImmune().noSave().noSummon()
                    .build("fx_throne_bane_wave"));

    // the Staff of the Hollow King's spells and its risen shades (07.10.2026)
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.HollowStaffRuneEntity>> HOLLOW_STAFF_RUNE =
            ENTITY_TYPES.register("fx_hollow_staff_rune", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.HollowStaffRuneEntity>of(
                            com.jastkub.frozenfortress.entity.HollowStaffRuneEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(1).fireImmune().noSave().noSummon()
                    .build("fx_hollow_staff_rune"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.HollowStaffBellEntity>> HOLLOW_STAFF_BELL =
            ENTITY_TYPES.register("fx_hollow_staff_bell", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.HollowStaffBellEntity>of(
                            com.jastkub.frozenfortress.entity.HollowStaffBellEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(12).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("fx_hollow_staff_bell"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.HollowStaffTideEntity>> HOLLOW_STAFF_TIDE =
            ENTITY_TYPES.register("fx_hollow_staff_tide", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.HollowStaffTideEntity>of(
                            com.jastkub.frozenfortress.entity.HollowStaffTideEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(12).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("fx_hollow_staff_tide"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.HollowStaffFxEntity>> HOLLOW_STAFF_FX =
            ENTITY_TYPES.register("fx_hollow_staff_fx", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.HollowStaffFxEntity>of(
                            com.jastkub.frozenfortress.entity.HollowStaffFxEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(2).fireImmune().noSave().noSummon()
                    .build("fx_hollow_staff_fx"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.HollowStaffShadeEntity>> HOLLOW_STAFF_SHADE =
            ENTITY_TYPES.register("hollow_staff_shade", () -> EntityType.Builder.of(
                            com.jastkub.frozenfortress.entity.HollowStaffShadeEntity::new, MobCategory.MISC)
                    .sized(0.7F, 1.9F).clientTrackingRange(10).fireImmune().noSave().build("hollow_staff_shade"));

    /** The Kingsrime sword's skills (KingsrimeSwordItem, 07.10.2026). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.effect.KingsrimeSwordCrescentEntity>> KINGSRIME_CRESCENT =
            ENTITY_TYPES.register("fx_kingsrime_crescent", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.effect.KingsrimeSwordCrescentEntity>of(
                            com.jastkub.frozenfortress.entity.effect.KingsrimeSwordCrescentEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("fx_kingsrime_crescent"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.effect.KingsrimeSwordBladesEntity>> KINGSRIME_BLADES =
            ENTITY_TYPES.register("fx_kingsrime_blades", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.effect.KingsrimeSwordBladesEntity>of(
                            com.jastkub.frozenfortress.entity.effect.KingsrimeSwordBladesEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("fx_kingsrime_blades"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.effect.KingsrimeSwordCrownEntity>> KINGSRIME_CROWN =
            ENTITY_TYPES.register("fx_kingsrime_crown", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.effect.KingsrimeSwordCrownEntity>of(
                            com.jastkub.frozenfortress.entity.effect.KingsrimeSwordCrownEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.5F).clientTrackingRange(8).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("fx_kingsrime_crown"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.effect.KingsrimeSwordShackleEntity>> KINGSRIME_SHACKLE =
            ENTITY_TYPES.register("fx_kingsrime_shackle", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.effect.KingsrimeSwordShackleEntity>of(
                            com.jastkub.frozenfortress.entity.effect.KingsrimeSwordShackleEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("fx_kingsrime_shackle"));

    /** The Bow of the Last Watch (LastWatchBowItem, 07.10.2026). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.LastWatchBeamEntity>> LAST_WATCH_BEAM =
            ENTITY_TYPES.register("fx_last_watch_beam", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.LastWatchBeamEntity>of(com.jastkub.frozenfortress.entity.LastWatchBeamEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(16).updateInterval(20).fireImmune().noSave().noSummon().build("fx_last_watch_beam"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.LastWatchMarkEntity>> LAST_WATCH_MARK =
            ENTITY_TYPES.register("fx_last_watch_mark", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.LastWatchMarkEntity>of(com.jastkub.frozenfortress.entity.LastWatchMarkEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).clientTrackingRange(10).updateInterval(2).fireImmune().noSave().noSummon().build("fx_last_watch_mark"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.LastWatchChainsEntity>> LAST_WATCH_CHAINS =
            ENTITY_TYPES.register("fx_last_watch_chains", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.LastWatchChainsEntity>of(com.jastkub.frozenfortress.entity.LastWatchChainsEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(2).fireImmune().noSave().noSummon().build("fx_last_watch_chains"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.LastWatchSentinelEntity>> LAST_WATCH_SENTINEL =
            ENTITY_TYPES.register("fx_last_watch_sentinel", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.LastWatchSentinelEntity>of(com.jastkub.frozenfortress.entity.LastWatchSentinelEntity::new, MobCategory.MISC)
                    .sized(1.0F, 2.3F).clientTrackingRange(10).updateInterval(1).fireImmune().noSave().noSummon().build("fx_last_watch_sentinel"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.projectile.LastWatchArrowEntity>> LAST_WATCH_ARROW =
            ENTITY_TYPES.register("fx_last_watch_arrow", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.projectile.LastWatchArrowEntity>of(com.jastkub.frozenfortress.entity.projectile.LastWatchArrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(20).build("fx_last_watch_arrow"));

    // the Wand of the Dead (07.10.2026): its servants, its grave's ring, its pictures
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.BoneWandSkeletonEntity>> BONE_WAND_SKELETON =
            ENTITY_TYPES.register("bone_wand_skeleton", () -> EntityType.Builder.of(
                            com.jastkub.frozenfortress.entity.BoneWandSkeletonEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).clientTrackingRange(10).noSave().build("bone_wand_skeleton"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.BoneWandRingEntity>> BONE_WAND_RING =
            ENTITY_TYPES.register("fx_bone_wand_ring", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.BoneWandRingEntity>of(
                            com.jastkub.frozenfortress.entity.BoneWandRingEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("fx_bone_wand_ring"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.BoneWandFxEntity>> BONE_WAND_FX =
            ENTITY_TYPES.register("fx_bone_wand_fx", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.BoneWandFxEntity>of(
                            com.jastkub.frozenfortress.entity.BoneWandFxEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(2).fireImmune().noSave().noSummon()
                    .build("fx_bone_wand_fx"));

    // ================= OKO BURZY - THE EYE OF THE STORM (Velkhar's last phase, 07.10.2026) =================
    /** The vortex under the floes, and the column the ascent tears up through the throne room. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.StormEyeVortexEntity>> STORM_EYE_VORTEX =
            ENTITY_TYPES.register("storm_eye_vortex", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.StormEyeVortexEntity>of(
                            com.jastkub.frozenfortress.entity.boss.StormEyeVortexEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(16).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("storm_eye_vortex"));
    /** The ring of electrified cloud round the arena. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.StormEyeWallEntity>> STORM_EYE_WALL =
            ENTITY_TYPES.register("storm_eye_wall", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.StormEyeWallEntity>of(
                            com.jastkub.frozenfortress.entity.boss.StormEyeWallEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(16).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("storm_eye_wall"));
    /** A Storm Anchor: the pylons that hold him up (a living body, so arrows hit it; its attributes are registered too). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.StormEyeAnchorEntity>> STORM_EYE_ANCHOR =
            ENTITY_TYPES.register("storm_eye_anchor", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.StormEyeAnchorEntity>of(
                            com.jastkub.frozenfortress.entity.boss.StormEyeAnchorEntity::new, MobCategory.MISC)
                    .sized(1.4F, 3.0F).clientTrackingRange(10).updateInterval(3).fireImmune().noSave().noSummon()
                    .build("storm_eye_anchor"));
    /** Znak Gromu: the thunder rune on a floe, and its bolt. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.StormEyeRuneEntity>> STORM_EYE_RUNE =
            ENTITY_TYPES.register("storm_eye_rune", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.StormEyeRuneEntity>of(
                            com.jastkub.frozenfortress.entity.boss.StormEyeRuneEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.2F).clientTrackingRange(10).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("storm_eye_rune"));
    /** Wichura: the wall of wind with one gap. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.StormEyeGaleEntity>> STORM_EYE_GALE =
            ENTITY_TYPES.register("storm_eye_gale", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.StormEyeGaleEntity>of(
                            com.jastkub.frozenfortress.entity.boss.StormEyeGaleEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("storm_eye_gale"));
    /** Kula Burzy: a slow orb of caged lightning. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.StormEyeOrbEntity>> STORM_EYE_ORB =
            ENTITY_TYPES.register("storm_eye_orb", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.StormEyeOrbEntity>of(
                            com.jastkub.frozenfortress.entity.boss.StormEyeOrbEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(1).fireImmune().noSave().noSummon()
                    .build("storm_eye_orb"));
    /** One arc of lightning between two points, for a few ticks. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.StormEyeBoltEntity>> STORM_EYE_BOLT =
            ENTITY_TYPES.register("storm_eye_bolt", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.StormEyeBoltEntity>of(
                            com.jastkub.frozenfortress.entity.boss.StormEyeBoltEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("storm_eye_bolt"));
    /** The rescue updraft and the mirrors' squall. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.StormEyeGustEntity>> STORM_EYE_GUST =
            ENTITY_TYPES.register("storm_eye_gust", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.StormEyeGustEntity>of(
                            com.jastkub.frozenfortress.entity.boss.StormEyeGustEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(1).fireImmune().noSave().noSummon()
                    .build("storm_eye_gust"));
    /** Zamiec Lustrzana: a mirror of the Hollow Magus (a living body, one hit breaks it; its attributes are registered too). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.StormEyeMirrorEntity>> STORM_EYE_MIRROR =
            ENTITY_TYPES.register("storm_eye_mirror", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.StormEyeMirrorEntity>of(
                            com.jastkub.frozenfortress.entity.boss.StormEyeMirrorEntity::new, MobCategory.MISC)
                    .sized(1.6F, 4.2F).clientTrackingRange(10).updateInterval(1).fireImmune().noSave().noSummon()
                    .build("storm_eye_mirror"));
    /**
     * A floe of the rings: moving ice (the central floe is blocks). Where it is is computed from the clock on both
     * sides, so its position packets are few and ignored; its synced data carries the motion and the look.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.StormEyeFloeEntity>> STORM_EYE_FLOE =
            ENTITY_TYPES.register("storm_eye_floe", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.StormEyeFloeEntity>of(
                            com.jastkub.frozenfortress.entity.boss.StormEyeFloeEntity::new, MobCategory.MISC)
                    .sized(7.0F, 2.3F).clientTrackingRange(10).updateInterval(40).fireImmune().noSave().noSummon()
                    .build("storm_eye_floe"));

    // Velkhar's last additions (07.10.2026): the roof's rubble on the ascent, Lodowy Sad, Rzut Wrotami
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.StormEyeRubbleEntity>> STORM_EYE_RUBBLE =
            ENTITY_TYPES.register("storm_eye_rubble", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.StormEyeRubbleEntity>of(
                            com.jastkub.frozenfortress.entity.boss.StormEyeRubbleEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).clientTrackingRange(8).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("storm_eye_rubble"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.VelkharJudgmentSwordEntity>> VELKHAR_JUDGMENT_SWORD =
            ENTITY_TYPES.register("velkhar_judgment_sword", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.VelkharJudgmentSwordEntity>of(
                            com.jastkub.frozenfortress.entity.boss.VelkharJudgmentSwordEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.8F).clientTrackingRange(8).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("velkhar_judgment_sword"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.VelkharJudgmentMarkEntity>> VELKHAR_JUDGMENT_MARK =
            ENTITY_TYPES.register("velkhar_judgment_mark", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.VelkharJudgmentMarkEntity>of(
                            com.jastkub.frozenfortress.entity.boss.VelkharJudgmentMarkEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.2F).clientTrackingRange(8).updateInterval(20).fireImmune().noSave().noSummon()
                    .build("velkhar_judgment_mark"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.jastkub.frozenfortress.entity.boss.VelkharGateThrowEntity>> VELKHAR_GATE_THROW =
            ENTITY_TYPES.register("velkhar_gate_throw", () -> EntityType.Builder.<com.jastkub.frozenfortress.entity.boss.VelkharGateThrowEntity>of(
                            com.jastkub.frozenfortress.entity.boss.VelkharGateThrowEntity::new, MobCategory.MISC)
                    .sized(1.6F, 0.6F).clientTrackingRange(8).updateInterval(1).fireImmune().noSave().noSummon()
                    .build("velkhar_gate_throw"));

    private FFEntities() {
    }
}
