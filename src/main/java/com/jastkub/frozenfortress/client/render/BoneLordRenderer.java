package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.BoneLordEntity;
import com.jastkub.frozenfortress.entity.BoneLordFrames;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The Bone Lord: a colossus of his own (tools/gen_bone_lord.py), drawn twice his geometry - thirteen blocks of him -
 * the cold in his sockets, the heart in his ribcage, the crystals of his mantle and the lozenge on his ushanka lit.
 * The scale is the generator's (BoneLordFrames.SCALE), so what he holds is in his fist.
 */
public class BoneLordRenderer extends FrostGeoRenderer<BoneLordEntity> {

    public static final float SCALE = BoneLordFrames.SCALE;

    public BoneLordRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("bone_lord"), false));
        withScale(SCALE);
        shadowRadius = 2.4F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
