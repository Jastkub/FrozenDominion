package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharCloneEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** The mirror-image renders as translucent blizzard-light. */
public class VelkharCloneRenderer extends FrostGeoRenderer<VelkharCloneEntity> {

    public VelkharCloneRenderer(EntityRendererProvider.Context context) {
        super(context, new CloneModel());
        this.shadowRadius = 0.0F;
    }

    /**
     * A SHADE IS BARELY THERE, and it was nearly black instead.
     *
     * <p>"A shadowy model" was read as dark, and it should have been read as
     * INSUBSTANTIAL: what is wanted while they peel out of him is pale, cold
     * blue and see-through, not a silhouette. A near-black figure on a snow
     * arena is the highest-contrast thing on screen - the exact opposite of
     * something that has not finished existing.
     *
     * <p>So the shade draws translucent-emissive and tinted: lit from inside,
     * fading up as it emerges. The solid copy keeps the ordinary translucent
     * pass it always had.
     */
    @Override
    public software.bernie.geckolib.util.Color getRenderColor(
            VelkharCloneEntity animatable, float partialTick, int packedLight) {
        if (animatable.isStormRush()) {
            // A WHITEOUT'S COPY SHIMMERS (07.10.2026): a little see-through, a little cold, breathing - seen only close
            // to, through the storm, and only by whoever looks; the ears had the first word (its feet are air)
            float t = animatable.tickCount + partialTick;
            float a = 0.80F + 0.14F * net.minecraft.util.Mth.sin(t * 0.9F) + 0.06F * net.minecraft.util.Mth.sin(t * 2.3F);
            return software.bernie.geckolib.util.Color.ofRGBA(0.90F, 0.96F, 1.0F, a);
        }
        if (!animatable.isShade()) {
            return software.bernie.geckolib.util.Color.ofRGBA(1.0F, 1.0F, 1.0F, 1.0F);
        }
        // it thickens as it comes out, so the emergence is visible ON the
        // figure rather than only in the way it moves
        float out = animatable.shadeForm();
        return software.bernie.geckolib.util.Color.ofRGBA(
                0.62F, 0.84F, 1.0F, 0.26F + 0.34F * out);
    }

    @Override
    public RenderType getRenderType(VelkharCloneEntity animatable, ResourceLocation texture,
                                    net.minecraft.client.renderer.MultiBufferSource bufferSource, float partialTick) {
        return animatable.isShade()
                ? RenderType.entityTranslucentEmissive(texture)
                : RenderType.entityTranslucent(texture);
    }

    /**
     * THE COPY CARRIES THE ICE GREATSWORD TOO - and only while he does.
     *
     * <p>This renderer had no preRender at all, which is not the same as
     * "nothing to do": the clone is drawn from Velkhar's own geometry, and
     * that model contains a hundred-unit ghost blade sitting on the root. With
     * nobody hiding it, every copy has been walking around holding a full-size
     * sword permanently, because the only code that ever hid that bone lives
     * in HIS renderer. A shared model needs the same rules applied on both
     * sides or the second one shows the raw bind pose.
     *
     * <p>Scaled off the same tick window his blade is grown on, so the three
     * of them form and lose their swords on the same frame. The Y scale is
     * what makes it extend out of the fist; the girth comes up behind it so it
     * thickens into a blade instead of ballooning from a toy.
     */
    @Override
    public void preRender(com.mojang.blaze3d.vertex.PoseStack poseStack,
                          VelkharCloneEntity animatable,
                          software.bernie.geckolib.cache.object.BakedGeoModel model,
                          net.minecraft.client.renderer.MultiBufferSource bufferSource,
                          com.mojang.blaze3d.vertex.VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, int colour) {
        // EVERY HAND BONE, EVERY FRAME - and that matters more here than it
        // does on him. The copies share his cached BakedGeoModel, so anything
        // this renderer leaves hidden stays hidden on the KING as well, and
        // whichever of them drew last decided whether he had a weapon. That is
        // the sword that "sometimes is not there". A copy carries the conjured
        // blade and nothing else: no greatsword, no twins, no staff.
        // A COPY IS A COPY, GREATSWORD AND ALL.
        //
        // This used to force the sword hidden, and that was wrong twice over.
        // It is wrong for the look - a mirror woven out of him should carry
        // what he carries, which is what "they jump in their base model"
        // means. And it is wrong mechanically: the copies share his cached
        // BakedGeoModel, so this was the one piece of code outside his own
        // renderer that could hide HIS weapon. With it gone, exactly one place
        // in the mod decides whether that bone is visible.
        //
        // The twins and the staff still go: those belong to reforges the
        // copies were never part of.
        // The paired blades belong to his second-phase reforge; a copy woven
        // out of him has no business holding either half.
        //
        // BOTH of them. twin_r was added to the model later, on the same hand
        // the greatsword lives in, and only its partner was ever hidden here -
        // so every copy has been walking round with an unexplained second
        // sword grown out of its sword fist, permanently, whatever it was
        // doing. A shared model needs the same rules on both sides, and a rule
        // written for one bone does not cover the bone added next to it.
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }

    /**
     * A COPY CARRIES THE GREATSWORD AND NOTHING ELSE.
     *
     * <p>Decided at draw time, for the same reason his own renderer does it
     * that way: the copies share the king's cached model, so hiding a bone
     * here used to hide it on HIM. Skipping the draw touches nothing shared.
     */
    @Override
    public void renderRecursively(com.mojang.blaze3d.vertex.PoseStack poseStack,
                                  VelkharCloneEntity animatable,
                                  software.bernie.geckolib.cache.object.GeoBone bone,
                                  RenderType renderType,
                                  net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                  com.mojang.blaze3d.vertex.VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight,
                                  int packedOverlay, int colour) {
        String name = bone.getName();
        // AND THE SHIELD. This list had the twins and the staff on it and not
        // the gate, so every copy he has ever woven has been carrying a shield
        // - including the ones summoned by the MAGE, who threw his away at the
        // second-phase break and has not owned one since. A mirror made out of
        // a man cannot be holding something the man does not have.
        // AND THE CROSSBOW. It was added to the model months after this list
        // was written and never added to it, so every copy has been carrying
        // one permanently - at full size, because the king's renderer only
        // ever writes that bone's scale on the one attack that uses it and
        // resets it to 1.0 the rest of the time.
        //
        // It is the same rule as the twins and the staff, and it is the
        // strictest case of it: the crossbow exists for about eight seconds
        // of one fight, forged out of his own sword on top of a tower. A
        // mirror woven out of him during a completely different attack cannot
        // be holding the thing he has not made yet.
        if ("twin".equals(name) || "twin_r".equals(name) || "ice_staff".equals(name)
                || "shield".equals(name) || "crossbow".equals(name)
                || name.startsWith("bow_")) {
            return;
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    static class CloneModel extends DefaultedEntityGeoModel<VelkharCloneEntity> {
        CloneModel() {
            super(FrozenFortress.id("velkhar"), true);
        }

        @Override
        public ResourceLocation getTextureResource(VelkharCloneEntity entity) {
            // A SHADOW UNTIL IT LANDS. See VelkharCloneEntity.isShade(): they
            // peel out of him as near-black silhouettes and only become the
            // pale mirror on the tick they come down on somebody.
            return entity.isShade()
                    ? FrozenFortress.id("textures/entity/velkhar_shade.png")
                    : FrozenFortress.id("textures/entity/velkhar_clone.png");
        }
    }
}
