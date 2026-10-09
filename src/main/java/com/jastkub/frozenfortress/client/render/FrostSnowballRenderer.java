package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.entity.projectile.FrostSnowballEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Frost Rider's snowball in flight, drawn the way the game draws its own thrown snowball - only larger, so it can
 * be followed across the camp (with FrostSnowballEntity's frost trail behind it).
 *
 * <p>NOT ITS OWN BOXES ANY MORE: the three crossed blocks of
 * packed snow this drew never reached the screen - the entity was there (its hitbox, its shadow, its hits) and the
 * renderer was called every frame, but none of its quads were ever seen, in either version. The game's own item
 * drawing is what every thrown thing uses, and it is seen.
 */
public class FrostSnowballRenderer extends EntityRenderer<FrostSnowballEntity> {

    private static final ItemStack BALL = new ItemStack(Items.SNOWBALL);
    /** Against the vanilla snowball's one: this one is packed hard and thrown hard. */
    private static final float SCALE = 1.6F;

    private final ItemRenderer items;

    public FrostSnowballRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0.18F;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(FrostSnowballEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(FrostSnowballEntity ball, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers,
                       int light) {
        float age = ball.tickCount + partialTick;
        poses.pushPose();
        poses.translate(0.0F, 0.15F, 0.0F);
        poses.scale(SCALE, SCALE, SCALE);
        // turned to the eye, as the game's own; and tumbling, as a thrown thing does
        poses.mulPose(entityRenderDispatcher.cameraOrientation());
        poses.mulPose(Axis.YP.rotationDegrees(180.0F));
        poses.mulPose(Axis.ZP.rotationDegrees(ball.getId() * 47.0F + age * 23.0F));
        items.renderStatic(BALL, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, poses, buffers,
                ball.level(), ball.getId());
        poses.popPose();
        super.render(ball, yaw, partialTick, poses, buffers, light);
    }
}
