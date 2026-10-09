package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.IceWardPillarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The ward, and the line it feeds him down.
 *
 * <p>The wards already made him untouchable and already healed him, but both
 * of those were invisible: health went up, and nothing on screen said why. A
 * player with no reason to look around the room has no reason to leave him,
 * which turned the one mechanic that is supposed to move them into a pause.
 *
 * <p>So the link is drawn. Two ribbons, an opaque core inside a wider
 * translucent shell, both camera-facing - a beam is a round thing and two
 * flat quads at different widths read as round far more cheaply than a tube
 * does. The V scrolls toward him rather than away, because which way it flows
 * is the entire message.
 */
public class IceWardPillarRenderer extends GeoEntityRenderer<IceWardPillarEntity> {

    private static final ResourceLocation CORE =
            FrozenFortress.id("textures/entity/velkhar_beam_inner.png");
    private static final ResourceLocation SHELL =
            FrozenFortress.id("textures/entity/velkhar_beam_outer.png");

    public IceWardPillarRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("ice_ward_pillar"), false));
        this.shadowRadius = 0.0F;
    }


    /**
     * NO TETHER. This used to draw two camera-facing ribbons from the pillar
     * to the king, permanently, for as long as it stood.
     *
     * <p>A link that is always on says "these are connected" once and then
     * never says anything again - and because the healing was simultaneous
     * with it, there was nothing to watch and nothing to react to. The mending
     * is sent as discrete signals now (VelkharEntity.tickWardHealing): each
     * one leaves, crosses the room and lands, and the health only moves when
     * it arrives. The pillar just stands there being a pillar.
     */
    @Override
    public void render(IceWardPillarEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
