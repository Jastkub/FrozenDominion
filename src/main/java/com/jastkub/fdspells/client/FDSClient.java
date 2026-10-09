package com.jastkub.fdspells.client;

import com.jastkub.fdspells.FDSpells;
import com.jastkub.fdspells.entity.IceSentinelEntity;
import com.jastkub.fdspells.registry.FDSRegistry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import javax.annotation.Nullable;

/** The renderers: every spell's thing drawn from its own model; the rain's unseen hand not at all. */
@Mod.EventBusSubscriber(modid = FDSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FDSClient {

    private FDSClient() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(FDSRegistry.AVALANCHE_WAVE.get(), c -> new FxRenderer<>(c, "avalanche_wave"));
        e.registerEntityRenderer(FDSRegistry.ICICLE_RAIN_CLOUD.get(), NoopRenderer::new);
        e.registerEntityRenderer(FDSRegistry.ICICLE_SHADOW.get(), c -> new FxRenderer<>(c, "icicle_shadow"));
        e.registerEntityRenderer(FDSRegistry.FALLING_ICICLE.get(), c -> new FxRenderer<>(c, "falling_icicle"));
        e.registerEntityRenderer(FDSRegistry.FROST_JAVELIN_ENTITY.get(), c -> new FxRenderer<>(c, "frost_javelin"));
        e.registerEntityRenderer(FDSRegistry.ICE_PILLAR.get(), c -> new FxRenderer<>(c, "ice_pillar"));
        e.registerEntityRenderer(FDSRegistry.FROST_SHACKLES_ENTITY.get(), c -> new FxRenderer<>(c, "frost_shackles"));
        e.registerEntityRenderer(FDSRegistry.FROST_HEART_ENTITY.get(), c -> new FxRenderer<>(c, "frost_heart"));
        e.registerEntityRenderer(FDSRegistry.FROST_RING.get(), c -> new FxRenderer<>(c, "frost_ring"));
        e.registerEntityRenderer(FDSRegistry.LITANY_RUNE.get(), c -> new FxRenderer<>(c, "litany_rune"));
        e.registerEntityRenderer(FDSRegistry.FROST_SHELL_ENTITY.get(), c -> new FxRenderer<>(c, "frost_shell"));
        e.registerEntityRenderer(FDSRegistry.ICE_SHARD.get(), c -> new FxRenderer<>(c, "ice_shard"));
        e.registerEntityRenderer(FDSRegistry.ICE_SENTINEL.get(), SentinelRenderer::new);
    }

    /** The sentinel: its own model, translucent ice, its runes lit. */
    static final class SentinelRenderer extends GeoEntityRenderer<IceSentinelEntity> {
        SentinelRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FDSpells.id("ice_sentinel")));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            shadowRadius = 0.6F;
        }

        @Override
        public RenderType getRenderType(IceSentinelEntity animatable, ResourceLocation texture,
                                        @Nullable net.minecraft.client.renderer.MultiBufferSource buffers,
                                        float partialTick) {
            return RenderType.entityTranslucent(texture);
        }
    }
}
