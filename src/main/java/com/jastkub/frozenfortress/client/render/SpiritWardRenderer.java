package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.SpiritWardEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The ward, drawn translucent.
 *
 * <p>GeoEntityRenderer's default is entityCutoutNoCull, which is a BINARY
 * alpha: a texel is drawn at full strength or not at all. The ward's sheet is
 * almost entirely part-transparent, so under the default it would have come
 * out either a solid pale slab or nothing whatever - and "faded and spiritual"
 * is the one thing it is asked to be.
 *
 * <p>entityTranslucent honours the alpha, at the usual cost: translucent
 * geometry does not write depth the way solid geometry does, so two wards
 * overlapping can sort oddly for a frame. Three thin shields turning on a
 * wide circle rarely overlap, and a sorting flicker is a cheaper price than
 * losing the look entirely.
 */
public class SpiritWardRenderer extends GeoEntityRenderer<SpiritWardEntity> {

    public SpiritWardRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("spirit_ward"), false));
        this.shadowRadius = 0.0F;
    }

    @Override
    public RenderType getRenderType(SpiritWardEntity animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(getTextureLocation(animatable));
    }
}
