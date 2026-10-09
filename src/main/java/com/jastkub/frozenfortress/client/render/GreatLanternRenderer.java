package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.GreatLanternBlockEntity;
import com.jastkub.frozenfortress.entity.LamplighterEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import javax.annotation.Nullable;

/**
 * THE GREAT LANTERN: its cage still, its lens turning to the beam, and the beam out of the lens
 * (drawn as LamplighterRenderer draws it - the same sheet, the same rays broken on the piers). The
 * beam is its keeper's: the nearest living Lamplighter whose lantern this is. With him dead the lens
 * stops where it was and no light leaves it.
 */
public class GreatLanternRenderer extends GeoBlockRenderer<GreatLanternBlockEntity> {

    public GreatLanternRenderer() {
        super(new LensModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void actuallyRender(PoseStack poses, GreatLanternBlockEntity lantern, BakedGeoModel model, RenderType type,
                               MultiBufferSource buffers, VertexConsumer buffer, boolean isReRender, float partialTick,
                               int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        LamplighterEntity keeper = isReRender ? null : keeper(lantern);
        boolean lit = keeper != null && keeper.beamMode() != LamplighterEntity.BEAM_OFF;
        if (lit) {                                      // before the model is posed (setCustomAnimations)
            lantern.lensAngle = keeper.beamAngle(partialTick);
            lantern.twoBeams = keeper.beamMode() == LamplighterEntity.BEAM_TWO;
        }
        super.actuallyRender(poses, lantern, model, type, buffers, buffer, isReRender, partialTick, packedLight,
                packedOverlay, red, green, blue, alpha);
        if (lit) {
            // the pose stands at the block's middle, a hair up (GeoBlockRenderer), unturned (no facing)
            LamplighterRenderer.beams(keeper, Vec3.atLowerCornerOf(lantern.getBlockPos()).add(0.5D, 0.01D, 0.5D),
                    partialTick, poses, buffers);
        }
    }

    @Nullable
    static LamplighterEntity keeper(GreatLanternBlockEntity lantern) {
        if (lantern.getLevel() == null) {
            return null;
        }
        Vec3 c = Vec3.atCenterOf(lantern.getBlockPos());
        LamplighterEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LamplighterEntity e : lantern.getLevel().getEntitiesOfClass(LamplighterEntity.class,
                new AABB(lantern.getBlockPos()).inflate(30.0D), e -> e.isAlive() && !e.isDeadOrDying())) {
            if (e.lantern().distanceToSqr(c) > 2.25D) {
                continue;                                   // another lantern's keeper
            }
            double d = e.distanceToSqr(c);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    @Override
    public boolean shouldRenderOffScreen(GreatLanternBlockEntity lantern) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    /** Turns the lens to the beam (its fore opening, the model's -z, along the beam), shows the rear shutter for one. */
    static class LensModel extends DefaultedBlockGeoModel<GreatLanternBlockEntity> {
        LensModel() {
            super(FrozenFortress.id("great_lantern"));
        }

        @Override
        public void setCustomAnimations(GreatLanternBlockEntity lantern, long instanceId,
                                        AnimationState<GreatLanternBlockEntity> state) {
            float a = lantern.lensAngle * Mth.DEG_TO_RAD;
            software.bernie.geckolib.core.animatable.model.CoreGeoBone lens = getAnimationProcessor().getBone("lens");
            if (lens != null) {
                lens.setRotY(Mth.PI - a);
            }
            software.bernie.geckolib.core.animatable.model.CoreGeoBone rear = getAnimationProcessor().getBone("rear");
            if (rear != null) {
                rear.setHidden(lantern.twoBeams);
            }
        }
    }
}
