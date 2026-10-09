package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.TurnkeyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import java.util.HashMap;
import java.util.Map;

/**
 * The Turnkey: the keyhole in his mask and his lantern light themselves.
 *
 * <p>His ring of keys hangs flat at his hip and never leaves it - only its keys do, when he flings them (the animation).
 *
 * <p>HIS CHAIN IS A CHAIN. Its three links and the manacle's cuff are top-level bones,
 * and every frame they are hung from his right fist by a small rope: four
 * points, the first held at the fist, the rest falling under their own weight,
 * carried on by what they were doing (so a swing of the arm swings them, a step
 * sets them swaying, a turn leaves them behind), damped, kept a link's length
 * from one another - and never through the floor: the cuff stays clear of it.
 * The rope lives in the world (that is what gives it its inertia) and is
 * brought back into the model to place the bones.
 *
 * <p>The fist is found by composing the arm's bones exactly as GeckoLib does,
 * after this frame's animation and before any bone is drawn - so the chain is
 * where the hand is this frame, not where it was in the last one.
 */
public class TurnkeyRenderer extends FrostGeoRenderer<TurnkeyEntity> {

    /** The bottom of his right fist, in the model's bind space (blocks; GeckoLib mirrors x). */
    private static final Vector3f FIST = new Vector3f(-10.8F / 16.0F, 10.9F / 16.0F, 0.0F);
    /** From one link's top to the next. */
    private static final float LINK = 2.2F / 16.0F;
    /** The cuff, from the top it hangs by to its bottom (it must stay clear of the floor). */
    private static final float CUFF = 4.4F / 16.0F;
    private static final float GRAVITY = 0.045F;   // blocks a tick squared
    private static final float KEEP = 0.86F;       // of its motion a point keeps each tick
    private static final String[] CHAIN = {"link_0", "link_1", "link_2", "cuff"};

    /** Shared with the thrown manacle's renderer, whose chain starts at the same fist. */
    private static final Map<Integer, Rope> ROPES = new HashMap<>();
    /** Set for each frame drawn (not the glow's redraw): the rope is stepped once, before the first bone. */
    private boolean due;
    private GeoBone hand;

    public TurnkeyRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("turnkey"), true));
        this.shadowRadius = 1.0F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        addRenderLayer(new TurnkeyWardLayer(this));
    }

    /** The rope of one Turnkey: points 0 (the fist) to 3 (the cuff's top), in the world. */
    static final class Rope {
        final Vector3f[] p = new Vector3f[4];
        final Vector3f[] prev = new Vector3f[4];
        /** From the world into the model's space, as of this frame. */
        final Matrix4f toModel = new Matrix4f();
        float lastTime = Float.NaN;
        boolean live;
    }

    @Override
    public void preRender(PoseStack poseStack, TurnkeyEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        int st = animatable.getAttackState();
        // the chain is in the air while the manacle is: no second one at his fist
        boolean thrown = st == TurnkeyEntity.SHACKLE;
        for (String n : CHAIN) {
            model.getBone(n).ifPresent(b -> b.setHidden(thrown));
        }
        if (!isReRender) {
            hand = model.getBone("hand_r").orElse(null);
            due = true;
            if (ROPES.size() > 64) {
                ROPES.keySet().removeIf(id -> animatable.level().getEntity(id) == null);
            }
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, TurnkeyEntity animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        if (due && !isReRender) {
            due = false;
            if (hand != null) {
                step(ROPES.computeIfAbsent(animatable.getId(), k -> new Rope()), animatable, hand, partialTick);
            }
        }
        String n = bone.getName();
        if (n.startsWith("link_") || n.equals("cuff")) {
            Rope r = ROPES.get(animatable.getId());
            if (r != null && r.live) {
                hang(r, bone, n.equals("cuff") ? 3 : n.charAt(5) - '0');
            }
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /** Where his fist was drawn in this frame (the thrown manacle's chain starts there), or null. */
    public static Vec3 fist(TurnkeyEntity e, float partialTick) {
        Rope r = ROPES.get(e.getId());
        if (r == null || !r.live || Math.abs(e.tickCount + partialTick - r.lastTime) > 1.5F) {
            return null;
        }
        return new Vec3(r.p[0].x, r.p[0].y, r.p[0].z);
    }

    /** A bone's own transform, as GeckoLib applies it (RenderUtils: position, pivot, Z-Y-X, scale, back). The
     *  smith's ankle chain is found by it too (VelkharSmithRenderer). */
    static Matrix4f local(GeoBone b) {
        return new Matrix4f()
                .translate(-b.getPosX() / 16.0F, b.getPosY() / 16.0F, b.getPosZ() / 16.0F)
                .translate(b.getPivotX() / 16.0F, b.getPivotY() / 16.0F, b.getPivotZ() / 16.0F)
                .rotateZ(b.getRotZ()).rotateY(b.getRotY()).rotateX(b.getRotX())
                .scale(b.getScaleX(), b.getScaleY(), b.getScaleZ())
                .translate(-b.getPivotX() / 16.0F, -b.getPivotY() / 16.0F, -b.getPivotZ() / 16.0F);
    }

    /** One frame of the rope: the fist moved to where this frame's pose has it, the rest following. */
    private void step(Rope r, TurnkeyEntity e, GeoBone hand, float partialTick) {
        Matrix4f arm = new Matrix4f();
        for (GeoBone b = hand; b != null; b = b.getParent()) {
            arm.mulLocal(local(b));                          // root first, the hand last
        }
        // model space -> world: where the entity is drawn this frame, its turn and its scale
        Vec3 at = e.getPosition(partialTick);
        Matrix4f toWorld = new Matrix4f().translate((float) at.x, (float) at.y, (float) at.z)
                .mul(new Matrix4f(this.entityRenderTranslations).invert())
                .mul(this.modelRenderTranslations);
        r.toModel.set(toWorld).invert();
        Vector3f anchor = toWorld.transformPosition(arm.transformPosition(new Vector3f(FIST)));

        float now = e.tickCount + partialTick;
        if (!r.live || Float.isNaN(r.lastTime) || Math.abs(now - r.lastTime) > 20.0F) {
            for (int i = 0; i < 4; i++) {
                r.p[i] = new Vector3f(anchor.x, anchor.y - i * LINK, anchor.z);
                r.prev[i] = new Vector3f(r.p[i]);
            }
            r.live = true;
            r.lastTime = now;
            return;
        }
        float dt = Math.min(3.0F, Math.max(0.0F, now - r.lastTime));
        r.lastTime = now;
        float floor = (float) at.y + CUFF + 0.02F;
        int sub = Math.max(1, (int) Math.ceil(dt / 0.34F));
        float h = dt / sub;
        float keep = (float) Math.pow(KEEP, h);
        Vector3f from = new Vector3f(r.p[0]);
        for (int s = 0; s < sub; s++) {
            // the fist goes from where it was to where it is, in even strides
            Vector3f fist = new Vector3f(from).lerp(anchor, (s + 1) / (float) sub);
            r.p[0].set(fist);
            r.prev[0].set(fist);
            for (int i = 1; i < 4; i++) {
                Vector3f v = new Vector3f(r.p[i]).sub(r.prev[i]).mul(keep);
                r.prev[i].set(r.p[i]);
                r.p[i].add(v).add(0.0F, -GRAVITY * h * h, 0.0F);
            }
            for (int it = 0; it < 4; it++) {
                for (int i = 1; i < 4; i++) {
                    Vector3f a = r.p[i - 1], b = r.p[i];
                    Vector3f d = new Vector3f(b).sub(a);
                    float len = d.length();
                    if (len < 1.0E-5F) {
                        d.set(0.0F, -1.0F, 0.0F);
                        len = 1.0F;
                    }
                    Vector3f fix = d.mul((len - LINK) / len);
                    if (i == 1) {
                        b.sub(fix);                          // the fist does not give
                    } else {
                        a.add(new Vector3f(fix).mul(0.5F));
                        b.sub(fix.mul(0.5F));
                    }
                }
                for (int i = 1; i < 4; i++) {
                    if (r.p[i].y < floor) {                  // the floor: nothing goes through it
                        r.p[i].y = floor;
                        r.prev[i].y = Math.max(r.prev[i].y, floor);
                    }
                }
            }
        }
    }

    /** Puts a link (j 0..2) or the cuff (j 3) where the rope has it, along the rope. */
    private static void hang(Rope r, GeoBone bone, int j) {
        Vector3f at = r.toModel.transformPosition(new Vector3f(r.p[j]));
        Vector3f d;
        if (j < 3) {
            d = r.toModel.transformPosition(new Vector3f(r.p[j + 1])).sub(at);
        } else {
            // the cuff hangs on from the last link's line, its weight pulling it upright
            d = new Vector3f(at).sub(r.toModel.transformPosition(new Vector3f(r.p[2])));
            if (d.lengthSquared() > 1.0E-8F) {
                d.normalize();
            }
            d.add(0.0F, -1.0F, 0.0F);
        }
        if (d.lengthSquared() < 1.0E-8F) {
            d.set(0.0F, -1.0F, 0.0F);
        }
        d.normalize();
        // the pivot to where the rope says (a top-level bone: model space is its parent's), and the
        // link turned from hanging straight down (its bind) to along the rope: d = Rz(c)·Rx(a)·(0,-1,0)
        bone.setPosX(-(at.x - bone.getPivotX() / 16.0F) * 16.0F);
        bone.setPosY((at.y - bone.getPivotY() / 16.0F) * 16.0F);
        bone.setPosZ((at.z - bone.getPivotZ() / 16.0F) * 16.0F);
        bone.setRotX((float) -Math.asin(Math.max(-1.0F, Math.min(1.0F, d.z))));
        bone.setRotY(0.0F);
        bone.setRotZ((float) Math.atan2(d.x, -d.y));
    }
}
