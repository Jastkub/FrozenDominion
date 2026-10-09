package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.StormcrownBeaconBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Draws the Stormcrown: its body (tools/gen_nest_beacon.py - the crystal held up by copper claws, the crown of ice
 * turning round it; gold once it is taken) and its beam, a pale glacial column from the crystal's top to the sky -
 * gold too once it is taken.
 */
public class StormcrownBeaconRenderer extends GeoBlockRenderer<StormcrownBeaconBlockEntity> {

    /** (1.21) the box it is drawn in is the renderer's to say; the block entity knows it. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(com.jastkub.frozenfortress.block.entity.StormcrownBeaconBlockEntity be) {
        return be.renderBox();
    }

    /** The storm's own violet while it burns for the court... */
    private static final int BEAM_COLOR = com.jastkub.frozenfortress.util.FFColor.argb(0.62F, 0.5F, 0.95F, 1.0F);
    /** ...drawn in the vortex's own streaks (client.StormcrownSpiral's arms), wider, rising into its eye. */
    private static final ResourceLocation STORM_BEAM = FrozenFortress.id("textures/environment/storm_arms.png");
    /** ...and hearth gold once it is taken. */
    private static final int TAKEN_COLOR = com.jastkub.frozenfortress.util.FFColor.argb(1.0F, 0.74F, 0.30F, 1.0F);
    private static final ResourceLocation TAKEN_TEXTURE = FrozenFortress.id("textures/block/stormcrown_taken.png");
    /** The beam leaves from inside the crystal's point, a block up. */
    private static final int BEAM_FROM = 1;

    public StormcrownBeaconRenderer(BlockEntityRendererProvider.Context context) {
        super(new DefaultedBlockGeoModel<>(FrozenFortress.id("stormcrown")) {
            @Override
            public ResourceLocation getTextureResource(StormcrownBeaconBlockEntity beacon) {
                return beacon.isCaptured() ? TAKEN_TEXTURE : super.getTextureResource(beacon);
            }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    /** The beam, once the body is drawn (GeckoLib's last hook: the pose is back at the block's corner). */
    @Override
    public void renderFinal(PoseStack poseStack, StormcrownBeaconBlockEntity beacon,
                            software.bernie.geckolib.cache.object.BakedGeoModel model, MultiBufferSource bufferSource,
                            com.mojang.blaze3d.vertex.VertexConsumer buffer, float partialTick, int packedLight,
                            int packedOverlay, int colour) {
        super.renderFinal(poseStack, beacon, model, bufferSource, buffer, partialTick, packedLight, packedOverlay,
                colour);
        long gameTime = beacon.getLevel() != null ? beacon.getLevel().getGameTime() : 0L;
        // (while the king's storm hangs over it the beam ends in the storm's eye - client.StormcrownSpiral - and
        // climbs back to the sky as the storm unwinds once it is taken)
        int height = com.jastkub.frozenfortress.client.StormcrownSpiral.beamHeight(beacon.getBlockPos(), BEAM_FROM, 320);
        if (beacon.isCaptured()) {
            BeaconRenderer.renderBeaconBeam(poseStack, bufferSource, BeaconRenderer.BEAM_LOCATION,
                    partialTick, 1.0F, gameTime, BEAM_FROM, height, TAKEN_COLOR, 0.24F, 0.36F);
        } else {
            BeaconRenderer.renderBeaconBeam(poseStack, bufferSource, STORM_BEAM,
                    partialTick, 0.35F, gameTime, BEAM_FROM, height, BEAM_COLOR, 0.5F, 0.85F);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(StormcrownBeaconBlockEntity beacon) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 320;
    }

    @Override
    public boolean shouldRender(StormcrownBeaconBlockEntity beacon, net.minecraft.world.phys.Vec3 cameraPos) {
        return true;
    }
}
