package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The armour breaking up as he loses.
 *
 * <p>WHY FOUR SHEETS AND NOT ONE FADED UP. A single crack texture brought in
 * on alpha is a bruise: the whole network is there from the first hit and
 * merely darkens, which reads as the plate getting dirty. Damage ARRIVES - a
 * split opens where a moment ago there was none. So there are four sheets,
 * each carrying everything the previous one carried plus new fractures, and
 * this picks between them. Nothing ever un-cracks, because each stage is a
 * superset of the last.
 *
 * <p>WHY IT IS DRAWN ON TOP RATHER THAN PAINTED IN. The base sheet has to stay
 * the same for the whole fight - it is one texture and GeckoLib binds it once
 * - so damage cannot be painted into it at runtime. Re-rendering the model
 * with a transparent overlay costs one more pass and gets the cracks onto
 * every plate, the shield included, with no extra geometry and no second
 * model.
 *
 * <p>The alpha inside a stage still ramps, so a split fades in over the
 * seconds after the threshold instead of appearing between two frames.
 */
public class BattleDamageLayer extends GeoRenderLayer<VelkharEntity> {

    private static final ResourceLocation[] STAGES = {
            FrozenFortress.id("textures/entity/velkhar_cracks1.png"),
            FrozenFortress.id("textures/entity/velkhar_cracks2.png"),
            FrozenFortress.id("textures/entity/velkhar_cracks3.png"),
            FrozenFortress.id("textures/entity/velkhar_cracks4.png"),
    };

    /**
     * Health left when each stage takes over. He is untouched above the first.
     *
     * <p>Spread across the whole bar rather than bunched at the end, because
     * the point of this is to be a READOUT - the player should be able to
     * glance at him and know roughly how the fight is going without looking
     * at the bar at all.
     */
    private static final float[] AT = {0.88F, 0.70F, 0.48F, 0.26F};

    public BattleDamageLayer(GeoRenderer<VelkharEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, VelkharEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        if (animatable.isDeadOrDying()) {
            return;
        }
        // THE STAGE IS TOLD TO US, NOT WORKED OUT HERE.
        //
        // This used to read the health fraction and pick a sheet from it, every
        // frame - so a new network of splits appeared on him the instant a
        // number was crossed, silently, with nothing to mark it. Armour does
        // not quietly acquire cracks. The entity fires a break now - a report,
        // a burst of shards, a flash through the splits already on him - and
        // only then advances this. The client draws what it is given, which
        // means the picture cannot change without something happening first.
        int stage = animatable.damageStage();
        if (stage < 0 || stage >= STAGES.length) {
            return;                       // still unmarked
        }
        float alpha = 1.0F;

        RenderType cracks = RenderType.entityTranslucent(STAGES[stage]);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, cracks,
                bufferSource.getBuffer(cracks), partialTick,
                packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, alpha);
    }
}
