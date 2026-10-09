package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.client.particle.BlizzardFlakeParticle;
import com.jastkub.frozenfortress.client.particle.FrostSwirlParticle;
import com.jastkub.frozenfortress.client.particle.IceShardParticle;
import com.jastkub.frozenfortress.client.particle.ShockwaveParticle;
import com.jastkub.frozenfortress.client.particle.SoulFrostParticle;
import com.jastkub.frozenfortress.client.render.FrostmawRenderer;
import com.jastkub.frozenfortress.client.render.FrostboundSentinelRenderer;
import com.jastkub.frozenfortress.client.render.IceSpikeRenderer;
import com.jastkub.frozenfortress.client.render.IceArrowRenderer;
import com.jastkub.frozenfortress.client.render.NothingRenderer;
import com.jastkub.frozenfortress.client.render.RimeweaverRenderer;
import com.jastkub.frozenfortress.client.render.StillbowRenderer;
import com.jastkub.frozenfortress.client.render.ThrownBladeRenderer;
import com.jastkub.frozenfortress.client.render.VaultWardenRenderer;
import com.jastkub.frozenfortress.client.render.VelkharCloneRenderer;
import com.jastkub.frozenfortress.client.render.VelkharRenderer;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    /** The Hollow Crown drawn as its 3D model on players' and armour stands' heads (CrownHeadLayer). */
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addLayers(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.client.resources.PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer r) {
                r.addLayer(new com.jastkub.frozenfortress.client.render.CrownHeadLayer<>(r));
            }
        }
        net.minecraft.client.renderer.entity.EntityRenderer<?> standRenderer =
                event.getRenderer(net.minecraft.world.entity.EntityType.ARMOR_STAND);
        if (standRenderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer stand) {
            stand.addLayer(new com.jastkub.frozenfortress.client.render.CrownHeadLayer(stand));
        }
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FFEntities.ROAR_WARP.get(),
                com.jastkub.frozenfortress.client.render.RoarWarpRenderer::new);
        event.registerEntityRenderer(FFEntities.VELKHAR.get(), VelkharRenderer::new);
        // the cisterns' stair going down: drawn as the falling blocks they are
        event.registerEntityRenderer(FFEntities.STAIR_DEBRIS.get(), ctx -> {
            @SuppressWarnings({"unchecked", "rawtypes"})
            net.minecraft.client.renderer.entity.EntityRenderer<com.jastkub.frozenfortress.entity.StairDebrisEntity> r =
                    (net.minecraft.client.renderer.entity.EntityRenderer)
                            new net.minecraft.client.renderer.entity.FallingBlockRenderer(ctx);
            return r;
        });
        event.registerEntityRenderer(FFEntities.VELKHAR_CLONE.get(), VelkharCloneRenderer::new);
        event.registerEntityRenderer(FFEntities.FROSTBOUND_SENTINEL.get(), FrostboundSentinelRenderer::new);
        event.registerEntityRenderer(FFEntities.RIMEWEAVER.get(), RimeweaverRenderer::new);
        event.registerEntityRenderer(FFEntities.STILLBOW.get(), StillbowRenderer::new);
        event.registerEntityRenderer(FFEntities.FROSTMAW.get(), FrostmawRenderer::new);
        // the defenders' attacks, each a thing with a body (tools/gen_defender_fx.py)
        event.registerEntityRenderer(FFEntities.ATTACK_FX.get(), com.jastkub.frozenfortress.client.render.AttackFxRenderer::new);
        event.registerEntityRenderer(FFEntities.RIME_SHUTTLE.get(),
                com.jastkub.frozenfortress.client.render.RimeweaverFxRenderers.Shuttle::new);
        event.registerEntityRenderer(FFEntities.RIME_THORN.get(),
                com.jastkub.frozenfortress.client.render.RimeweaverFxRenderers.Thorn::new);
        event.registerEntityRenderer(FFEntities.VAULT_BAR.get(), com.jastkub.frozenfortress.client.render.VaultBarRenderer::new);
        event.registerEntityRenderer(FFEntities.RIME_BIND.get(),
                com.jastkub.frozenfortress.client.render.RimeweaverFxRenderers.Bind::new);
        event.registerEntityRenderer(FFEntities.FROST_SKELETON.get(),
                com.jastkub.frozenfortress.client.render.FrostSkeletonRenderer::new);
        event.registerEntityRenderer(FFEntities.FROST_RIDER.get(),
                com.jastkub.frozenfortress.client.render.FrostSkeletonRenderer::new);
        event.registerEntityRenderer(FFEntities.FROST_SNOWBALL.get(),
                com.jastkub.frozenfortress.client.render.FrostSnowballRenderer::new);
        event.registerEntityRenderer(FFEntities.THROWN_WARMTH.get(),
                net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(FFEntities.WARMTH_SPLASH.get(),
                com.jastkub.frozenfortress.client.render.WarmthSplashRenderer::new);
        event.registerEntityRenderer(FFEntities.HOLLOW_GOLEM.get(),
                com.jastkub.frozenfortress.client.render.HollowGolemRenderer::new);
        event.registerEntityRenderer(FFEntities.VAULT_WARDEN.get(), VaultWardenRenderer::new);
        event.registerEntityRenderer(FFEntities.TURNKEY.get(), com.jastkub.frozenfortress.client.render.TurnkeyRenderer::new);
        event.registerEntityRenderer(FFEntities.BONE_LORD.get(), com.jastkub.frozenfortress.client.render.BoneLordRenderer::new);
        event.registerEntityRenderer(FFEntities.LAMPLIGHTER.get(), com.jastkub.frozenfortress.client.render.LamplighterRenderer::new);
        event.registerEntityRenderer(FFEntities.TURNKEY_KEY.get(), com.jastkub.frozenfortress.client.render.CourtFoesRenderers.Key::new);
        event.registerEntityRenderer(FFEntities.PORTCULLIS.get(), com.jastkub.frozenfortress.client.render.CourtFoesRenderers.Grate::new);
        event.registerEntityRenderer(FFEntities.TURNKEY_MANACLE.get(), com.jastkub.frozenfortress.client.render.CourtFoesRenderers.Manacle::new);
        event.registerEntityRenderer(FFEntities.FROST_CANDLE.get(), com.jastkub.frozenfortress.client.render.CourtFoesRenderers.Candle::new);
        event.registerEntityRenderer(FFEntities.RIME_PRIESTESS.get(), com.jastkub.frozenfortress.client.render.CourtFoesRenderers.Priestess::new);
        event.registerEntityRenderer(FFEntities.DROWNED_LADY.get(), com.jastkub.frozenfortress.client.render.DrownedLadyRenderer::new);
        event.registerEntityRenderer(FFEntities.VELKHAR_SMITH.get(), com.jastkub.frozenfortress.client.render.VelkharSmithRenderer::new);
        event.registerEntityRenderer(FFEntities.ICE_AUROCHS.get(), com.jastkub.frozenfortress.client.render.IceAurochsRenderer::new);
        event.registerEntityRenderer(FFEntities.FORGE_OVERSEER.get(), com.jastkub.frozenfortress.client.render.ForgeOverseerRenderer::new);
        event.registerEntityRenderer(FFEntities.FORGE_OVERSEER_PLATE.get(), com.jastkub.frozenfortress.client.render.ForgeOverseerFxRenderers.Plate::new);
        event.registerEntityRenderer(FFEntities.FORGE_OVERSEER_SLAG.get(), com.jastkub.frozenfortress.client.render.ForgeOverseerFxRenderers.Slag::new);
        event.registerEntityRenderer(FFEntities.FORGE_OVERSEER_STEAM.get(), com.jastkub.frozenfortress.client.render.ForgeOverseerFxRenderers.Steam::new);
        event.registerEntityRenderer(FFEntities.FORGE_OVERSEER_SHOCKWAVE.get(), com.jastkub.frozenfortress.client.render.ForgeOverseerFxRenderers.Shockwave::new);
        event.registerEntityRenderer(FFEntities.FORGE_OVERSEER_RIME.get(), com.jastkub.frozenfortress.client.render.ForgeOverseerFxRenderers.Rime::new);
        event.registerEntityRenderer(FFEntities.SHADE_SHEPHERD.get(), com.jastkub.frozenfortress.client.render.ShadeShepherdRenderer::new);
        event.registerEntityRenderer(FFEntities.SHADE.get(), com.jastkub.frozenfortress.client.render.ShadeRenderer::new);
        event.registerEntityRenderer(FFEntities.SHADE_SHEPHERD_DECOY.get(), com.jastkub.frozenfortress.client.render.ShadeShepherdFxRenderers.Decoy::new);
        event.registerEntityRenderer(FFEntities.SHADE_SHEPHERD_GUST.get(), com.jastkub.frozenfortress.client.render.ShadeShepherdFxRenderers.Gust::new);
        event.registerEntityRenderer(FFEntities.SHADE_AFTERIMAGE.get(), com.jastkub.frozenfortress.client.render.ShadeShepherdFxRenderers.Afterimage::new);
        event.registerEntityRenderer(FFEntities.ICE_AUROCHS_RING.get(), com.jastkub.frozenfortress.client.render.IceAurochsRingRenderer::new);
        event.registerEntityRenderer(FFEntities.DROWNED_ICE_PLATE.get(), com.jastkub.frozenfortress.client.render.DrownedFxRenderers.Plate::new);
        event.registerEntityRenderer(FFEntities.DROWNED_SHADOW.get(), com.jastkub.frozenfortress.client.render.DrownedFxRenderers.Shadow::new);
        event.registerEntityRenderer(FFEntities.DROWNED_HANDS.get(), com.jastkub.frozenfortress.client.render.DrownedFxRenderers.Hands::new);
        event.registerEntityRenderer(FFEntities.DROWNED_TIDE.get(), com.jastkub.frozenfortress.client.render.DrownedFxRenderers.Tide::new);
        event.registerEntityRenderer(FFEntities.SHADE_PULSE.get(), com.jastkub.frozenfortress.client.render.ShadePulseRenderer::new);
        event.registerEntityRenderer(FFEntities.CHARGE_LANE.get(), com.jastkub.frozenfortress.client.render.ChargeLaneRenderer::new);
        event.registerEntityRenderer(FFEntities.PRIESTESS_MIRROR.get(), com.jastkub.frozenfortress.client.render.CourtFoesRenderers.Mirror::new);
        event.registerEntityRenderer(FFEntities.FROST_PAGE.get(), com.jastkub.frozenfortress.client.render.CourtFoesRenderers.Page::new);
        event.registerEntityRenderer(FFEntities.PENANCE_BELL.get(), com.jastkub.frozenfortress.client.render.CourtFoesRenderers.Bell::new);
        event.registerEntityRenderer(FFEntities.FROST_WAVE.get(), com.jastkub.frozenfortress.client.render.FrostWaveRenderer::new);
        event.registerEntityRenderer(FFEntities.ICE_JAVELIN.get(), com.jastkub.frozenfortress.client.render.GolemHazardRenderers.Javelin::new);
        event.registerEntityRenderer(FFEntities.ICE_BOMB.get(), com.jastkub.frozenfortress.client.render.GolemHazardRenderers.Bomb::new);
        event.registerEntityRenderer(FFEntities.MAW_BOMB.get(), com.jastkub.frozenfortress.client.render.GolemHazardRenderers.Bomb::new);
        event.registerEntityRenderer(FFEntities.FROST_GLOB.get(), com.jastkub.frozenfortress.client.render.GolemHazardRenderers.Glob::new);
        event.registerEntityRenderer(FFEntities.AVALANCHE.get(), com.jastkub.frozenfortress.client.render.AvalancheRenderer::new);
        event.registerEntityRenderer(FFEntities.ICE_FRAGMENT.get(), com.jastkub.frozenfortress.client.render.GolemHazardRenderers.Fragment::new);
        event.registerEntityRenderer(FFEntities.FALLING_ICICLE.get(), com.jastkub.frozenfortress.client.render.GolemHazardRenderers.Icicle::new);
        event.registerEntityRenderer(FFEntities.TRAP_ICICLE.get(), com.jastkub.frozenfortress.client.render.GolemHazardRenderers.Icicle::new);
        event.registerEntityRenderer(FFEntities.TRAP_ARROW.get(), IceArrowRenderer::new);
        event.registerEntityRenderer(FFEntities.FROST_PUDDLE.get(), com.jastkub.frozenfortress.client.render.GolemHazardRenderers.Puddle::new);
        event.registerEntityRenderer(FFEntities.LITANY_RUNE.get(), com.jastkub.frozenfortress.client.render.CourtFoesRenderers.Rune::new);
        event.registerEntityRenderer(FFEntities.ICE_SPIKE.get(), IceSpikeRenderer::new);
        event.registerEntityRenderer(FFEntities.FLOOR_SIGIL.get(),
                com.jastkub.frozenfortress.client.render.FloorSigilRenderer::new);
        event.registerEntityRenderer(FFEntities.FALLING_DEBRIS.get(),
                com.jastkub.frozenfortress.client.render.FallingDebrisRenderer::new);
        event.registerEntityRenderer(FFEntities.ICE_BOULDER.get(),
                com.jastkub.frozenfortress.client.render.IceBoulderRenderer::new);
        event.registerEntityRenderer(FFEntities.ICE_WARD_PILLAR.get(),
                com.jastkub.frozenfortress.client.render.IceWardPillarRenderer::new);
        event.registerEntityRenderer(FFEntities.SPIRIT_WARD.get(),
                com.jastkub.frozenfortress.client.render.SpiritWardRenderer::new);
        event.registerEntityRenderer(FFEntities.ICE_ORB.get(),
                com.jastkub.frozenfortress.client.render.IceOrbRenderer::new);
        event.registerEntityRenderer(FFEntities.ICE_TOWER.get(),
                com.jastkub.frozenfortress.client.render.IceTowerRenderer::new);
        event.registerEntityRenderer(FFEntities.GROUND_MIST.get(),
                com.jastkub.frozenfortress.client.render.GroundMistRenderer::new);
        event.registerEntityRenderer(FFEntities.SPOT_ARC.get(),
                com.jastkub.frozenfortress.client.render.SpotArcRenderer::new);
        event.registerEntityRenderer(FFEntities.WARD_SIGNAL.get(),
                com.jastkub.frozenfortress.client.render.WardSignalRenderer::new);
        event.registerEntityRenderer(FFEntities.HUNTER_ORB.get(),
                com.jastkub.frozenfortress.client.render.HunterOrbRenderer::new);
        event.registerEntityRenderer(FFEntities.ICE_PRISON.get(),
                com.jastkub.frozenfortress.client.render.IcePrisonRenderer::new);
        event.registerEntityRenderer(FFEntities.FROST_STRIKE.get(),
                com.jastkub.frozenfortress.client.render.FrostStrikeRenderer::new);
        event.registerEntityRenderer(FFEntities.SOVEREIGN_BEAM.get(),
                com.jastkub.frozenfortress.client.render.SovereignBeamRenderer::new);
        event.registerEntityRenderer(FFEntities.THROWN_BLADE.get(), ThrownBladeRenderer::new);
        event.registerEntityRenderer(FFEntities.DOOM_BLADE.get(),
                com.jastkub.frozenfortress.client.render.DoomBladeRenderer::new);
        event.registerEntityRenderer(FFEntities.ICE_ARROW.get(), IceArrowRenderer::new);
        // WAS NothingRenderer, and it drew exactly that - see IceCrystalRenderer.
        event.registerEntityRenderer(FFEntities.FROST_BOLT.get(),
                com.jastkub.frozenfortress.client.render.IceCrystalRenderer::new);
        event.registerEntityRenderer(FFEntities.FROZEN_TORNADO.get(),
                com.jastkub.frozenfortress.client.render.FrozenTornadoRenderer::new);
        event.registerEntityRenderer(FFEntities.SHADOW_SHARD.get(),
                com.jastkub.frozenfortress.client.render.ShadowShardRenderer::new);
        event.registerEntityRenderer(FFEntities.GRAVE_BLADE.get(),
                com.jastkub.frozenfortress.client.render.GraveBladeRenderer::new);
        event.registerEntityRenderer(FFEntities.SHIELD_ECHO.get(),
                com.jastkub.frozenfortress.client.render.ShieldEchoRenderer::new);
        event.registerEntityRenderer(FFEntities.ARMOUR_SHARD.get(),
                com.jastkub.frozenfortress.client.render.ArmourShardRenderer::new);
        event.registerEntityRenderer(FFEntities.ICE_SPEAR.get(),
                com.jastkub.frozenfortress.client.render.IceSpearRenderer::new);
        event.registerEntityRenderer(FFEntities.VELKHAR_AFTERIMAGE.get(),
                com.jastkub.frozenfortress.client.render.VelkharAfterimageRenderer::new);
        event.registerBlockEntityRenderer(
                com.jastkub.frozenfortress.registry.FFBlockEntities.STORMCROWN_BEACON.get(),
                com.jastkub.frozenfortress.client.render.StormcrownBeaconRenderer::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.STORM_EYE_VORTEX.get(), com.jastkub.frozenfortress.client.render.StormEyeRenderers.Vortex::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.STORM_EYE_WALL.get(), com.jastkub.frozenfortress.client.render.StormEyeRenderers.Wall::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.STORM_EYE_ANCHOR.get(), com.jastkub.frozenfortress.client.render.StormEyeRenderers.Anchor::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.STORM_EYE_RUNE.get(), com.jastkub.frozenfortress.client.render.StormEyeRenderers.Rune::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.STORM_EYE_GALE.get(), com.jastkub.frozenfortress.client.render.StormEyeRenderers.Gale::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.STORM_EYE_ORB.get(), com.jastkub.frozenfortress.client.render.StormEyeRenderers.Orb::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.STORM_EYE_BOLT.get(), com.jastkub.frozenfortress.client.render.StormEyeRenderers.Bolt::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.STORM_EYE_GUST.get(), com.jastkub.frozenfortress.client.render.StormEyeRenderers.Gust::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.STORM_EYE_MIRROR.get(), com.jastkub.frozenfortress.client.render.StormEyeMirrorRenderer::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.STORM_EYE_FLOE.get(), com.jastkub.frozenfortress.client.render.StormEyeFloeRenderer::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.STORM_EYE_RUBBLE.get(), com.jastkub.frozenfortress.client.render.StormEyeRenderers.Rubble::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.VELKHAR_JUDGMENT_SWORD.get(), com.jastkub.frozenfortress.client.render.VelkharJudgmentRenderers.Sword::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.VELKHAR_JUDGMENT_MARK.get(), com.jastkub.frozenfortress.client.render.VelkharJudgmentRenderers.Mark::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.VELKHAR_GATE_THROW.get(), com.jastkub.frozenfortress.client.render.VelkharGateThrowRenderer::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.BONE_WAND_SKELETON.get(),
                com.jastkub.frozenfortress.client.render.BoneWandRenderers.Skeleton::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.BONE_WAND_RING.get(),
                com.jastkub.frozenfortress.client.render.BoneWandRenderers.Ring::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.BONE_WAND_FX.get(),
                com.jastkub.frozenfortress.client.render.BoneWandRenderers.Fx::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.HOLLOW_STAFF_RUNE.get(),
                com.jastkub.frozenfortress.client.render.HollowStaffRenderers.Rune::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.HOLLOW_STAFF_BELL.get(),
                com.jastkub.frozenfortress.client.render.HollowStaffRenderers.Bell::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.HOLLOW_STAFF_TIDE.get(),
                com.jastkub.frozenfortress.client.render.HollowStaffRenderers.Tide::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.HOLLOW_STAFF_FX.get(),
                com.jastkub.frozenfortress.client.render.HollowStaffRenderers.Fx::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.HOLLOW_STAFF_SHADE.get(),
                com.jastkub.frozenfortress.client.render.HollowStaffRenderers.Shade::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.KINGSRIME_CRESCENT.get(),
                com.jastkub.frozenfortress.client.render.KingsrimeSwordCrescentRenderer::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.KINGSRIME_BLADES.get(),
                com.jastkub.frozenfortress.client.render.KingsrimeSwordBladesRenderer::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.KINGSRIME_CROWN.get(),
                com.jastkub.frozenfortress.client.render.KingsrimeSwordCrownRenderer::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.KINGSRIME_SHACKLE.get(),
                com.jastkub.frozenfortress.client.render.KingsrimeSwordShackleRenderer::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.LAST_WATCH_BEAM.get(),
                com.jastkub.frozenfortress.client.render.LastWatchRenderers.Beam::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.LAST_WATCH_MARK.get(),
                com.jastkub.frozenfortress.client.render.LastWatchRenderers.Mark::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.LAST_WATCH_CHAINS.get(),
                com.jastkub.frozenfortress.client.render.LastWatchRenderers.Chains::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.LAST_WATCH_SENTINEL.get(),
                com.jastkub.frozenfortress.client.render.LastWatchRenderers.Sentinel::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.LAST_WATCH_ARROW.get(),
                com.jastkub.frozenfortress.client.render.LastWatchRenderers.Arrow::new);
        event.registerEntityRenderer(com.jastkub.frozenfortress.registry.FFEntities.THRONE_BANE_WAVE.get(),
                com.jastkub.frozenfortress.client.render.ThroneBaneWaveRenderer::new);
        event.registerBlockEntityRenderer(
                com.jastkub.frozenfortress.registry.FFBlockEntities.PENDULUM_AXE.get(),
                com.jastkub.frozenfortress.client.render.PendulumAxeRenderer::new);
        event.registerBlockEntityRenderer(
                com.jastkub.frozenfortress.registry.FFBlockEntities.MONSTROSITY_SKULL.get(),
                ctx -> new com.jastkub.frozenfortress.client.render.MonstrositySkullRenderer());
        event.registerBlockEntityRenderer(
                com.jastkub.frozenfortress.registry.FFBlockEntities.CITADEL_STATUE.get(),
                ctx -> new com.jastkub.frozenfortress.client.render.CitadelStatueRenderer());
        event.registerBlockEntityRenderer(
                com.jastkub.frozenfortress.registry.FFBlockEntities.CITADEL_DOOR.get(),
                ctx -> new com.jastkub.frozenfortress.client.render.CitadelDoorRenderer());
        event.registerBlockEntityRenderer(
                com.jastkub.frozenfortress.registry.FFBlockEntities.BOSS_GATE.get(),
                ctx -> new com.jastkub.frozenfortress.client.render.BossGateRenderer());
        event.registerBlockEntityRenderer(
                com.jastkub.frozenfortress.registry.FFBlockEntities.FROST_HEART.get(),
                ctx -> new com.jastkub.frozenfortress.client.render.TrapBlockRenderers.Heart());
        event.registerBlockEntityRenderer(
                com.jastkub.frozenfortress.registry.FFBlockEntities.FROST_CANNON.get(),
                ctx -> new com.jastkub.frozenfortress.client.render.TrapBlockRenderers.Cannon());
        event.registerBlockEntityRenderer(
                com.jastkub.frozenfortress.registry.FFBlockEntities.CITADEL_SPAWNER.get(),
                ctx -> new com.jastkub.frozenfortress.client.render.TrapBlockRenderers.Nest());
        event.registerBlockEntityRenderer(
                com.jastkub.frozenfortress.registry.FFBlockEntities.FROZEN_THRONE.get(),
                ctx -> new com.jastkub.frozenfortress.client.render.FrozenThroneRenderer());
        event.registerBlockEntityRenderer(
                com.jastkub.frozenfortress.registry.FFBlockEntities.CHAINED_CHEST.get(),
                ctx -> new com.jastkub.frozenfortress.client.render.ChainedChestRenderer());
        event.registerBlockEntityRenderer(
                com.jastkub.frozenfortress.registry.FFBlockEntities.GREAT_LANTERN.get(),
                ctx -> new com.jastkub.frozenfortress.client.render.GreatLanternRenderer());
        event.registerBlockEntityRenderer(
                com.jastkub.frozenfortress.registry.FFBlockEntities.FROST_SHRINE.get(),
                ctx -> new com.jastkub.frozenfortress.client.render.FrostShrineRenderer());
    }

    /** The court's glass and icicles need to draw as translucent, not solid. */
    @SubscribeEvent
    public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            com.jastkub.frozenfortress.client.anim.WeaponAnimClient.init();     // (PlayerAnimator, if it is there)
            net.minecraft.client.renderer.item.ItemProperties.register(
                    com.jastkub.frozenfortress.registry.FFItems.THRONE_BANE.get(),
                    com.jastkub.frozenfortress.FrozenFortress.id("blaze"),
                    com.jastkub.frozenfortress.client.ThroneBaneClient::blaze);
            net.minecraft.world.item.Item lastWatch = com.jastkub.frozenfortress.registry.FFItems.LAST_WATCH_BOW.get();
            net.minecraft.client.renderer.item.ItemProperties.register(lastWatch,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("pull"),
                    (s, l, e, i) -> com.jastkub.frozenfortress.item.LastWatchBowItem.pull(s, e));
            net.minecraft.client.renderer.item.ItemProperties.register(lastWatch,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("pulling"),
                    (s, l, e, i) -> e != null && e.isUsingItem() && e.getUseItem() == s ? 1.0F : 0.0F);
            net.minecraft.client.renderer.item.ItemProperties.register(lastWatch,
                    com.jastkub.frozenfortress.FrozenFortress.id("vigil"),
                    (s, l, e, i) -> com.jastkub.frozenfortress.item.LastWatchBowItem.vigilFull(s));
            com.jastkub.frozenfortress.client.BoneWandClient.registerItemProperties(
                    com.jastkub.frozenfortress.registry.FFItems.BONE_WAND.get());
            com.jastkub.frozenfortress.client.HollowStaffClient.registerItemProperties(
                    com.jastkub.frozenfortress.registry.FFItems.HOLLOW_KINGS_STAFF.get());
            // (the forge's window: onMenuScreens, 1.21 has its own event for it)
            // kingsrime_bow_props: the bows draw (the Everfrost one too since its frames were drawn, 07.10.2026), the
            // shields raise
            for (net.minecraft.world.item.Item bowItem : new net.minecraft.world.item.Item[]{
                    com.jastkub.frozenfortress.registry.FFItems.KINGSRIME_BOW.get(),
                    com.jastkub.frozenfortress.registry.FFItems.EVERFROST_BOW.get()}) {
                net.minecraft.client.renderer.item.ItemProperties.register(
                        bowItem, net.minecraft.resources.ResourceLocation.withDefaultNamespace("pull"),
                        (stack, level, entity, seed) -> entity == null || entity.getUseItem() != stack ? 0.0F
                                : (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0F);
                net.minecraft.client.renderer.item.ItemProperties.register(
                        bowItem, net.minecraft.resources.ResourceLocation.withDefaultNamespace("pulling"),
                        (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
            }
            for (net.minecraft.world.item.Item shieldItem : new net.minecraft.world.item.Item[]{
                    com.jastkub.frozenfortress.registry.FFItems.EVERFROST_SHIELD.get(),
                    com.jastkub.frozenfortress.registry.FFItems.KINGSRIME_SHIELD.get()}) {
                net.minecraft.client.renderer.item.ItemProperties.register(shieldItem,
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("blocking"),
                        (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
            }
            var translucent = net.minecraft.client.renderer.RenderType.translucent();
            var cutout = net.minecraft.client.renderer.RenderType.cutout();
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.jastkub.frozenfortress.registry.FFBlocks.GLACIAL_GLASS.get(), translucent);
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.jastkub.frozenfortress.registry.FFBlocks.GLACIAL_GLASS_PANE.get(), translucent);
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.jastkub.frozenfortress.registry.FFBlocks.ICICLE_CLUSTER.get(), translucent);
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.jastkub.frozenfortress.registry.FFBlocks.FLOOR_CRACK.get(), translucent);
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.jastkub.frozenfortress.registry.FFBlocks.FROST_LANTERN.get(), cutout);
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.jastkub.frozenfortress.registry.FFBlocks.FROST_CHANDELIER.get(), cutout);
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.jastkub.frozenfortress.registry.FFBlocks.FROST_CANDELABRA.get(), cutout);
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.jastkub.frozenfortress.registry.FFBlocks.FROST_CANDLESTICK.get(), cutout);
        });
    }

    /** The frost anvil's window (FrostForgeScreen). */
    @SubscribeEvent
    public static void onMenuScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.jastkub.frozenfortress.registry.FFRecipes.FROST_FORGE.get(),
                com.jastkub.frozenfortress.client.gui.FrostForgeScreen::new);
    }

    /**
     * (1.21) The items' client sides, which used to be Item#initializeClient (EverfrostGeoArmorItem,
     * KingsrimeItems.KingsrimeArmor, KingsrimeItems.FrostShield): the armour worn as its GeckoLib model, the shields
     * drawn by their GeckoLib item renderer. One extension - one renderer - per item, as each item had its own.
     *
     * <p>NeoForge 21.1 still honours a deprecated initializeClient (before this event) and refuses a second extension
     * for the same item, so an item that still declares its own is left to it.
     */
    @SubscribeEvent
    public static void onClientExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        for (net.minecraft.world.item.Item piece : new net.minecraft.world.item.Item[]{
                com.jastkub.frozenfortress.registry.FFItems.EVERFROST_HELMET.get(),
                com.jastkub.frozenfortress.registry.FFItems.EVERFROST_CHESTPLATE.get(),
                com.jastkub.frozenfortress.registry.FFItems.EVERFROST_LEGGINGS.get(),
                com.jastkub.frozenfortress.registry.FFItems.EVERFROST_BOOTS.get()}) {
            registerOnce(event, armour(com.jastkub.frozenfortress.client.render.EverfrostArmorRenderer::new), piece);
        }
        for (net.minecraft.world.item.Item piece : new net.minecraft.world.item.Item[]{
                com.jastkub.frozenfortress.registry.FFItems.KINGSRIME_HELMET.get(),
                com.jastkub.frozenfortress.registry.FFItems.KINGSRIME_CHESTPLATE.get(),
                com.jastkub.frozenfortress.registry.FFItems.KINGSRIME_LEGGINGS.get(),
                com.jastkub.frozenfortress.registry.FFItems.KINGSRIME_BOOTS.get()}) {
            registerOnce(event, armour(com.jastkub.frozenfortress.client.render.KingsrimeArmorRenderer::new), piece);
        }
        // the model names and the kingsrime flag are what FFItems builds the two FrostShields with
        registerOnce(event, shield("everfrost_shield", false), com.jastkub.frozenfortress.registry.FFItems.EVERFROST_SHIELD.get());
        registerOnce(event, shield("kingsrime_shield", true), com.jastkub.frozenfortress.registry.FFItems.KINGSRIME_SHIELD.get());
    }

    private static void registerOnce(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event,
                                     net.neoforged.neoforge.client.extensions.common.IClientItemExtensions ext,
                                     net.minecraft.world.item.Item item) {
        if (!event.isItemRegistered(item)) {
            event.registerItem(ext, item);
        }
    }

    private static net.neoforged.neoforge.client.extensions.common.IClientItemExtensions armour(
            java.util.function.Supplier<? extends software.bernie.geckolib.renderer.GeoArmorRenderer<?>> make) {
        return new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private software.bernie.geckolib.renderer.GeoArmorRenderer<?> renderer;

            @Override
            public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(
                    net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.item.ItemStack stack,
                    net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.client.model.HumanoidModel<?> original) {
                if (renderer == null) {
                    renderer = make.get();
                }
                renderer.prepForRender(entity, stack, slot, original);
                return renderer;
            }
        };
    }

    private static net.neoforged.neoforge.client.extensions.common.IClientItemExtensions shield(String model, boolean kingsrime) {
        return new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new com.jastkub.frozenfortress.client.render.FrostShieldRenderer(model, kingsrime);
                }
                return renderer;
            }
        };
    }

    /** The roar's bent air (RoarWarpFx): the frame, seen displaced through its shells and stream. */
    @SubscribeEvent
    public static void onRegisterShaders(net.neoforged.neoforge.client.event.RegisterShadersEvent event)
            throws java.io.IOException {
        event.registerShader(new net.minecraft.client.renderer.ShaderInstance(event.getResourceProvider(),
                        FrozenFortress.id("roar_warp"),
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),
                com.jastkub.frozenfortress.client.RoarWarpFx::setShader);
    }

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(FFParticles.FROST_SWIRL.get(), FrostSwirlParticle.Provider::new);
        event.registerSpriteSet(FFParticles.ICE_SHARD.get(), IceShardParticle.Provider::new);
        event.registerSpriteSet(FFParticles.BLIZZARD_FLAKE.get(), BlizzardFlakeParticle.Provider::new);
        event.registerSpriteSet(FFParticles.SOUL_FROST.get(), SoulFrostParticle.Provider::new);
        event.registerSpriteSet(FFParticles.SHOCKWAVE.get(), ShockwaveParticle.Provider::new);
    }
}
