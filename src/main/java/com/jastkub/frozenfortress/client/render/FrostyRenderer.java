package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.FrostyEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/** FROSTY: the hound's rig (tools/gen_frosty.py) drawn 2.2 times its size, his ice and eyes lit, his breath long. */
public class FrostyRenderer extends FrostGeoRenderer<FrostyEntity> {

    public FrostyRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("frosty"), false));
        withScale(2.2F);
        this.shadowRadius = 1.8F;
        addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));
        // (in his model space, drawn 2.2 times over: 3.8 of it is the eight blocks his breath bites to)
        addRenderLayer(new FrostmawBreathLayer<>(this, 3.8F));
    }
}
