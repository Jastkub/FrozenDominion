package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.MonstrositySkullBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The skull: the Monstrosity's head bones (geo/block/monstrosity_skull.geo.json, copied out of its own model by
 * tools/gen_monstrosity_skull.py) on its own texture and glowmask, brought down to about a block and a half across
 * the horns and sat with the jaw's underside on the block's floor.
 */
public class MonstrositySkullRenderer extends GeoBlockRenderer<MonstrositySkullBlockEntity> {

    /** 62.8 model pixels across the horns to 24 (a block and a half); where the head sits in its own model space. */
    private static final float SCALE = 0.38F;
    private static final float FLOOR = 44.15F, MID_Z = -28.5F;

    public MonstrositySkullRenderer() {
        super(new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(MonstrositySkullBlockEntity a) {
                return FrozenFortress.id("geo/block/monstrosity_skull.geo.json");
            }

            @Override
            public ResourceLocation getTextureResource(MonstrositySkullBlockEntity a) {
                return FrozenFortress.id("textures/entity/ice_monstrosity.png");
            }

            @Override
            public ResourceLocation getAnimationResource(MonstrositySkullBlockEntity a) {
                return FrozenFortress.id("animations/block/monstrosity_skull.animation.json");
            }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    /** Turned to its facing (GeckoLib's own turn), then brought down and seated - once, before the glow redraws it. */
    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        super.rotateBlock(facing, poseStack);
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(0.0F, -FLOOR / 16.0F, -MID_Z / 16.0F);
    }
}
