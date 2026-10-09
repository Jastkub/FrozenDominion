package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * THE ICE BEATS. The colossus's only glow: its ice drawn again over the hide at full
 * brightness, through the glow mask, at a strength that beats like a heart -
 * two swells close together, then a rest - never below half, so the ice is
 * always seen in the dark of the hall, and at the top of each beat it is all
 * light.
 *
 * <p>One layer, not a constant glow with a swell on top: the hide keeps its ice
 * (lit by the room) and this blends from that toward full light, so the beat
 * shows across the whole range instead of riding on a lit floor.
 *
 * <p>In its fury the heart races: the beat comes twice as often.
 */
public class GolemPulseLayer extends GeoRenderLayer<HollowGolemEntity> {

    private static final ResourceLocation MASK =
            FrozenFortress.id("textures/entity/ice_monstrosity_glowmask.png");
    /** Ticks from one beat to the next: calm, and in its fury. */
    private static final float CALM = 52.0F, FURY = 26.0F;

    public GolemPulseLayer(GeoRenderer<HollowGolemEntity> renderer) {
        super(renderer);
    }

    /** 0..1: the first swell, a smaller second one close behind it, then a rest. */
    static float beat(float t, float period) {
        float ph = (t % period) / period;
        float lub = (float) Math.exp(-Math.pow((ph - 0.08F) / 0.055F, 2));
        float dub = 0.7F * (float) Math.exp(-Math.pow((ph - 0.25F) / 0.065F, 2));
        return Math.max(lub, dub);
    }

    @Override
    public void render(PoseStack poses, HollowGolemEntity golem, BakedGeoModel model,
                       RenderType type, MultiBufferSource buffers, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        float t = golem.tickCount + partialTick;
        float b = beat(t, golem.berserk() ? FURY : CALM);
        float alpha = Mth.clamp(0.5F + 0.5F * b, 0.0F, 1.0F);
        // on its knee, its heart open: the ice blazes, racing - the window, shown
        if (golem.isStaggered()) {
            alpha = Mth.clamp(0.8F + 0.2F * beat(t, 12.0F), 0.0F, 1.0F);
        }
        getRenderer().reRender(model, poses, buffers, golem, RenderType.entityTranslucentEmissive(MASK),
                buffers.getBuffer(RenderType.entityTranslucentEmissive(MASK)),
                partialTick, 0xF000F0, packedOverlay, com.jastkub.frozenfortress.util.FFColor.argb(1.0F, 1.0F, 1.0F, alpha));
    }
}
