package com.jastkub.frozenfortress.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Base renderer for everything in the fortress that dies with a hand-authored
 * death animation. Vanilla flips a corpse onto its side, which would hide the
 * animation entirely - here the body stays upright and does its own falling.
 */
public class FrostGeoRenderer<T extends LivingEntity & GeoAnimatable> extends GeoEntityRenderer<T> {

    public FrostGeoRenderer(EntityRendererProvider.Context context, GeoModel<T> model) {
        super(context, model);
    }

    @Override
    protected float getDeathMaxRotation(T animatable) {
        return 0.0F;
    }
}
