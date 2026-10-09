package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.RimeweaverEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class RimeweaverRenderer extends FrostGeoRenderer<RimeweaverEntity> {

    public RimeweaverRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("rimeweaver"), true));
        this.shadowRadius = 0.5F;
        addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));   // its ice and its eyes light themselves
    }
}
