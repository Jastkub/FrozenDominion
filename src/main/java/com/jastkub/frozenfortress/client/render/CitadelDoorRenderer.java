package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.CitadelDoorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Draws a door: the model its block entity names, turned so its face looks
 * the way the block entity says, its runes glowing.
 */
public class CitadelDoorRenderer extends GeoBlockRenderer<CitadelDoorBlockEntity> {

    /** (1.21) the box it is drawn in is the renderer's to say; the block entity knows it. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(com.jastkub.frozenfortress.block.entity.CitadelDoorBlockEntity be) {
        return be.renderBox();
    }

    public CitadelDoorRenderer() {
        super(new DoorModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        CitadelDoorBlockEntity door = this.animatable;
        if (door == null) {
            return;
        }
        // the models are drawn with their face to the north
        float yaw = switch (door.facing()) {
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            case EAST -> 270.0F;
            default -> 0.0F;
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        if (door.shift() != 0.0F) {
            poseStack.translate(door.shift(), 0.0F, 0.0F);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(CitadelDoorBlockEntity door) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    static class DoorModel extends GeoModel<CitadelDoorBlockEntity> {
        @Override
        public ResourceLocation getModelResource(CitadelDoorBlockEntity door) {
            return FrozenFortress.id("geo/block/" + door.model() + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(CitadelDoorBlockEntity door) {
            String face = door.theme().isEmpty() ? door.model() : door.model() + "_" + door.theme();
            return FrozenFortress.id("textures/block/door/" + face + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(CitadelDoorBlockEntity door) {
            return FrozenFortress.id("animations/block/" + door.model() + ".animation.json");
        }
    }
}
