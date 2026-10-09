package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.IcePrisonEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.util.Color;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The block, and it shows how close it is to coming apart.
 *
 * <p>It goes cloudier and dimmer with every hit rather than swapping to a
 * cracked texture: the shell is translucent, so what a player actually reads
 * is how clearly they can see the person inside it. Three hits and they are
 * almost out.
 */
public class IcePrisonRenderer extends GeoEntityRenderer<IcePrisonEntity> {

    public IcePrisonRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("ice_prison"), false));
        this.shadowRadius = 0.6F;
    }

    @Override
    public void preRender(com.mojang.blaze3d.vertex.PoseStack poseStack,
                          IcePrisonEntity animatable,
                          software.bernie.geckolib.cache.object.BakedGeoModel model,
                          MultiBufferSource bufferSource,
                          com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay,
                          int colour) {
        // IT IS CUT TO THE PRISONER. Drawn for a player and dropped on an iron
        // golem it came up to the knees, which reads as a golem standing in a
        // bucket. Width and height scale separately so a tall thin thing does
        // not get a shell as wide as it is high.
        poseStack.scale(animatable.fitWidth(), animatable.fitHeight(), animatable.fitWidth());
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }

    @Override
    public Color getRenderColor(IcePrisonEntity animatable, float partialTick, int packedLight) {
        float gone = animatable.shellDamage();
        return Color.ofRGBA(0.72F, 0.88F, 1.0F, 0.88F - 0.45F * gone);
    }

    @Override
    public RenderType getRenderType(IcePrisonEntity animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }
}
