package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.ForgeOverseerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * NADZORCA KUZNI (tools/gen_forge_overseer.py). The cold fire behind the grates of his helm and his chest, the runes in
 * his hammer's cheeks and - while it is quenched - the rime on its head light themselves: the forge is dark.
 *
 * <p>WHAT LIVES HERE AND NOT IN HIS CLIPS (Blockbench shows every bone at once): the plates he has lost are hidden
 * (ForgeOverseerEntity.PLATE_BONES, their own entities on the floor now), and the hammer's rime ("hammer_rime") is shown
 * only while the hammer is quenched. The baked model is shared by every overseer, so both are set on every frame.
 * Nothing here moves the pose stack, so the glow layer's second pass through preRender changes nothing.
 */
public class ForgeOverseerRenderer extends FrostGeoRenderer<ForgeOverseerEntity> {

    public ForgeOverseerRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("forge_overseer"), true));
        this.shadowRadius = 1.1F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void preRender(PoseStack poseStack, ForgeOverseerEntity overseer, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                          int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        for (int i = 0; i < ForgeOverseerEntity.PLATE_BONES.length; i++) {
            boolean gone = overseer.plateLost(i);
            model.getBone(ForgeOverseerEntity.PLATE_BONES[i]).ifPresent(b -> b.setHidden(gone));
        }
        boolean rimed = overseer.isQuenched();
        model.getBone("hammer_rime").ifPresent(b -> b.setHidden(!rimed));
        super.preRender(poseStack, overseer, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, red, green, blue, alpha);
    }
}
