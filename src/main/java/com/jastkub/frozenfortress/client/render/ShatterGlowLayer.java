package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The light coming out of him while the armour gives way.
 *
 * <p>WHY THIS IS NOT {@link BattleDamageLayer} WITH A BRIGHTER TEXTURE. That
 * layer draws with {@code entityTranslucent}, which means its cracks are lit
 * by the room like everything else - so in the fortress, which is dark, the
 * brightest they can be is "slightly paler grey". What the phase break needs
 * is the opposite relationship: the cracks have to be brighter than the plate
 * around them regardless of what the room is doing, because the claim is that
 * the light is coming from INSIDE and the steel is merely what is in its way.
 * That is an emissive pass, and an emissive pass cannot be a parameter on a
 * translucent one.
 *
 * <p>It reuses the final damage sheet as its mask. That is deliberate rather
 * than lazy: the fractures the player has been watching spread across the
 * whole first phase are the same fractures that fail here, so the break reads
 * as the end of something they were already reading, instead of a new pattern
 * appearing at the last moment.
 *
 * <p>The layer draws NOTHING outside the transition. There is no fade in and
 * no fade out here at all - the entity ramps the value and then cuts it to
 * zero in the tick the gate lets go, and a glow that fades after the burst
 * would say the light was decoration on top of the armour rather than the
 * thing that was holding it together.
 */
public class ShatterGlowLayer extends GeoRenderLayer<VelkharEntity> {

    private static final ResourceLocation CRACKS =
            FrozenFortress.id("textures/entity/velkhar_cracks_lit.png");
    /**
     * THE SAME NETWORK, RUNNING.
     * Lit all at once and turned up, the cracks read as a lamp. In the phase break they RUN instead: eight frames
     * of the same sheet (tools/gen_velkhar_crack_crawl.py), each split starting at a point and travelling along
     * itself, the later stages forking off the earlier ones, the newest texels of each frame white-hot - the
     * frame the build has reached drawn whole and the next one fading in over it.
     */
    private static final ResourceLocation[] CRAWL = new ResourceLocation[8];

    static {
        for (int i = 0; i < CRAWL.length; i++) {
            CRAWL[i] = FrozenFortress.id("textures/entity/velkhar_cracks_crawl" + (i + 1) + ".png");
        }
    }

    public ShatterGlowLayer(GeoRenderer<VelkharEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, VelkharEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        // THE LOWER HALF OF THE VALUE IS OURS.
        //
        // The entity ramps one float through two stages: 0 to 0.5 is the
        // cracks taking fire, 0.5 to 1 is the light getting out of them. This
        // layer is the first stage, so it reaches full at the halfway mark and
        // then simply HOLDS while the beams do their half.
        //
        // Holding rather than continuing to climb is the important part. If
        // the cracks kept brightening under the beams they would wash out the
        // beams' roots, and the effect would go back to being one undivided
        // blaze - which is exactly the note this split was made to answer.
        float glow = Math.min(1.0F, animatable.shatterGlow() / 0.5F);
        if (glow <= 0.001F) {
            return;
        }

        // A flicker, and it gets faster as the pressure rises. Held steady the
        // glow reads as a lamp being turned up - something under control.
        // What this is meant to be is a seam failing, and failing things are
        // not smooth: the closer it gets to letting go the more unstable the
        // light behind it becomes.
        float time = animatable.tickCount + partialTick;
        float rate = 0.35F + glow * 1.9F;
        float flicker = 0.80F + 0.20F * Mth.sin(time * rate)
                              * Mth.sin(time * rate * 0.37F + 1.1F);

        float alpha = Mth.clamp(glow * flicker, 0.0F, 1.0F);
        // and the colour whitens as it goes. Ice blue while it is only cracks;
        // by the end the light is too bright to have a colour, which is the
        // difference between "glowing" and "about to give".
        float warm = glow * glow;
        float r = 0.36F + 0.60F * warm;
        float g = 0.78F + 0.21F * warm;

        // ---- THE PHASE BREAK: the network runs, and every split flares as it goes
        //      (VelkharEntity.P2_CRACKS - the same ticks it is heard and
        //      flinched at). Off the scene clock; a king first seen mid-scene
        //      gets the whole network, as every other state does.
        float clock = animatable.sceneClock(partialTick);
        if (animatable.getAttackState() == VelkharEntity.P2_TRANSITION && clock >= 0.0F) {
            float flare = 0.0F;
            for (int beat : VelkharEntity.P2_CRACKS) {
                float since = clock - beat;
                if (since >= 0.0F && since < 6.0F) {
                    flare = Math.max(flare, (1.0F - since / 6.0F) * (1.0F - since / 6.0F));
                }
            }
            // bright from the first split on: here the GROWTH says how far it has got, so a crack that has
            // appeared burns rather than waiting for the ramp to make it visible
            float a = Mth.clamp((0.6F + 0.4F * glow) * flicker * (0.85F + 0.6F * flare), 0.0F, 1.0F);
            float w = Math.min(1.0F, 0.25F + flare);
            float fr = Mth.lerp(w, r, 1.0F), fg = Mth.lerp(w, g, 1.0F);
            float run = glow * CRAWL.length;
            int k = Math.min(CRAWL.length - 1, (int) run);
            float into = run - (int) run;
            if (run < 1.0F) {
                // the first frame fades in as the first splits start
                drawCracks(poseStack, animatable, bakedModel, bufferSource, partialTick, packedOverlay,
                        CRAWL[0], fr, fg, a * into);
            } else {
                drawCracks(poseStack, animatable, bakedModel, bufferSource, partialTick, packedOverlay,
                        CRAWL[k], fr, fg, a);
                if (k + 1 < CRAWL.length && into > 0.02F) {
                    drawCracks(poseStack, animatable, bakedModel, bufferSource, partialTick, packedOverlay,
                            CRAWL[k + 1], fr, fg, a * into);
                }
            }
            return;
        }
        drawCracks(poseStack, animatable, bakedModel, bufferSource, partialTick, packedOverlay, CRACKS, r, g, alpha);
    }

    private void drawCracks(PoseStack poseStack, VelkharEntity animatable, BakedGeoModel bakedModel,
                            MultiBufferSource bufferSource, float partialTick, int packedOverlay,
                            ResourceLocation sheet, float r, float g, float alpha) {
        RenderType type = RenderType.entityTranslucentEmissive(sheet);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type,
                bufferSource.getBuffer(type), partialTick,
                LightTexture.FULL_BRIGHT, packedOverlay, r, g, 1.0F, alpha);
    }
}
