package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.registry.FFItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * THE HOLLOW CROWN ON A HEAD, as the thing it is.
 * It was a helmet's texture - the circlet painted round the upper half of the face, where on most skins the eyes
 * are, and nothing of it could rise above the head. Now its armour layer is clear (CrownOfTheHollowKingItem) and
 * this draws its own 3D item model on the head, the way a carved pumpkin is drawn: the circlet round the crown of
 * the head, the shards standing up over it. Where it sits is the model's "head" display transform
 * (models/item/crown_of_the_hollow_king.json).
 */
public class CrownHeadLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    public CrownHeadLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack stack = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (!stack.is(FFItems.CROWN_OF_THE_HOLLOW_KING.get()) || entity.isInvisible()) {
            return;
        }
        poses.pushPose();
        if (entity.isBaby()) {
            poses.translate(0.0F, 0.75F, 0.0F);
            poses.scale(0.7F, 0.7F, 0.7F);
        }
        getParentModel().getHead().translateAndRotate(poses);
        // what CustomHeadLayer does for a block or an item worn on the head
        poses.translate(0.0F, -0.25F, 0.0F);
        poses.mulPose(Axis.YP.rotationDegrees(180.0F));
        poses.scale(0.625F, -0.625F, -0.625F);
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer()
                .renderItem(entity, stack, ItemDisplayContext.HEAD, false, poses, buffers, light);
        poses.popPose();
    }
}
