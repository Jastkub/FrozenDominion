package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.DrownedLadyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * TOPIELICA (tools/gen_drowned_lady.py). Her eyes and the points of her crown of rime light themselves - the
 * cisterns are dark. No shadow on the floor: she is half in it.
 *
 * <p>While she is under the ice - asleep, or gone down after you - she is not drawn at all: what is seen of her then
 * is her shape (DrownedShadowEntity), and her own model, pushed down under the floor, would show through every
 * block of clear ice.
 */
public class DrownedLadyRenderer extends FrostGeoRenderer<DrownedLadyEntity> {

    public DrownedLadyRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("drowned_lady"), true));
        this.shadowRadius = 0.0F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(DrownedLadyEntity lady, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (lady.hidden() && !lady.isDeadOrDying()) {
            return;
        }
        super.render(lady, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
