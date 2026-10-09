package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.VelkharSmithEntity;
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
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.HashMap;
import java.util.Map;

/**
 * The king's smith (tools/gen_smith.py): his chains to the floor shown only while he is chained.
 *
 * <p>HIS ANKLE'S CHAIN LIES LIKE A CHAIN. As the Turnkey's (TurnkeyRenderer), its links are top-level bones laid every
 * frame along a small rope in the world - but this one is pinned at both ends: the shackle's eye at his left ankle
 * (found through the leg's bones as this frame's animation has them) and the staple's eye in the wall behind him. It
 * is a little longer than the way between them, so it falls from the ankle, lies along the floor and climbs to the
 * wall. It is let settle before it is first drawn, and moves when the leg does.
 */
public class VelkharSmithRenderer extends GeoEntityRenderer<VelkharSmithEntity> {

    /** The shackle's eye on his left shin and the staple's eye in the wall, in the model's bind space (blocks; GeckoLib
     *  mirrors x): gen_smith.py. */
    private static final Vector3f SHACKLE = new Vector3f(6.4F / 16.0F, 4.0F / 16.0F, 0.0F);
    private static final Vector3f RING_EYE = new Vector3f(7.45F / 16.0F, 3.1F / 16.0F, 21.6F / 16.0F);
    /** gen_smith.ANKLE_LINKS and LINK_PITCH: so many links, each this far on from the last. */
    private static final int LINKS = 14;
    private static final float PITCH = 2.0F / 16.0F;
    /** How high its line rests over the floor: half the width of a link on edge. */
    private static final float REST = 1.5F / 16.0F;
    private static final float GRAVITY = 0.045F;   // blocks a tick squared
    private static final float KEEP = 0.86F;       // of its motion a point keeps each tick
    private static final float SCRAPE = 0.4F;      // of its sideways motion a point on the floor keeps
    private static final int ITERATIONS = 12;

    private static final Map<Integer, Rope> ROPES = new HashMap<>();
    /** Set for each frame drawn (not a redraw): the rope is stepped once, before the first bone. */
    private boolean due;
    private GeoBone shin;

    public VelkharSmithRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("velkhar_smith"), true));
        this.shadowRadius = 0.5F;
    }

    /** The rope of one smith: points 0 (the ankle) to LINKS (the wall), in the world. */
    static final class Rope {
        final Vector3f[] p = new Vector3f[LINKS + 1];
        final Vector3f[] prev = new Vector3f[LINKS + 1];
        /** From the world into the model's space, as of this frame. */
        final Matrix4f toModel = new Matrix4f();
        float lastTime = Float.NaN;
        boolean live;
    }

    @Override
    public void preRender(PoseStack poseStack, VelkharSmithEntity smith, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                          int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        model.getBone("chains").ifPresent(b -> b.setHidden(!smith.isChained()));
        if (!isReRender) {
            shin = model.getBone("shin_l").orElse(null);
            due = true;
            if (ROPES.size() > 32) {
                ROPES.keySet().removeIf(id -> smith.level().getEntity(id) == null);
            }
        }
        super.preRender(poseStack, smith, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, VelkharSmithEntity smith, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        if (due && !isReRender) {
            due = false;
            if (shin != null) {
                step(ROPES.computeIfAbsent(smith.getId(), k -> new Rope()), smith, shin, partialTick);
            }
        }
        String n = bone.getName();
        if (n.startsWith("alink_")) {
            Rope r = ROPES.get(smith.getId());
            if (r != null && r.live) {
                lay(r, bone, Integer.parseInt(n.substring(6)));
            }
        }
        super.renderRecursively(poseStack, smith, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }

    /** One frame of the rope: the ankle moved to where this frame's pose has it, the wall's eye held, the rest following. */
    private void step(Rope r, VelkharSmithEntity e, GeoBone shin, float partialTick) {
        Matrix4f leg = new Matrix4f();
        for (GeoBone b = shin; b != null; b = b.getParent()) {
            leg.mulLocal(TurnkeyRenderer.local(b));            // root first, the shin last
        }
        // model space -> world: where the entity is drawn this frame, its turn and its scale
        Vec3 at = e.getPosition(partialTick);
        Matrix4f toWorld = new Matrix4f().translate((float) at.x, (float) at.y, (float) at.z)
                .mul(new Matrix4f(this.entityRenderTranslations).invert())
                .mul(this.modelRenderTranslations);
        r.toModel.set(toWorld).invert();
        Vector3f ankle = toWorld.transformPosition(leg.transformPosition(new Vector3f(SHACKLE)));
        Vector3f wall = toWorld.transformPosition(new Vector3f(RING_EYE));
        float floor = (float) at.y + REST;

        float now = e.tickCount + partialTick;
        if (!r.live || Float.isNaN(r.lastTime) || Math.abs(now - r.lastTime) > 20.0F) {
            // laid from the one eye to the other with a sag, and let settle before it is first seen
            for (int i = 0; i <= LINKS; i++) {
                float t = i / (float) LINKS;
                Vector3f q = new Vector3f(ankle).lerp(wall, t);
                q.y = Math.max(floor, q.y - 0.3F * (float) Math.sin(Math.PI * t));
                r.p[i] = q;
                r.prev[i] = new Vector3f(q);
            }
            for (int s = 0; s < 120; s++) {
                stride(r, ankle, wall, floor, 1.0F);
            }
            r.live = true;
            r.lastTime = now;
            return;
        }
        float dt = Math.min(3.0F, Math.max(0.0F, now - r.lastTime));
        r.lastTime = now;
        int sub = Math.max(1, (int) Math.ceil(dt / 0.34F));
        Vector3f from = new Vector3f(r.p[0]);
        for (int s = 0; s < sub; s++) {
            // the ankle goes from where it was to where it is, in even strides
            stride(r, new Vector3f(from).lerp(ankle, (s + 1) / (float) sub), wall, floor, dt / sub);
        }
    }

    /** One stride of h ticks: the free points carried on and pulled down, then kept a link apart and off the floor. */
    private static void stride(Rope r, Vector3f ankle, Vector3f wall, float floor, float h) {
        float keep = (float) Math.pow(KEEP, h);
        r.p[0].set(ankle);
        r.prev[0].set(ankle);
        r.p[LINKS].set(wall);
        r.prev[LINKS].set(wall);
        for (int i = 1; i < LINKS; i++) {
            Vector3f v = new Vector3f(r.p[i]).sub(r.prev[i]).mul(keep);
            if (r.p[i].y <= floor + 1.0E-4F) {
                v.x *= SCRAPE;                                 // iron on stone: it drags, it does not glide
                v.z *= SCRAPE;
            }
            r.prev[i].set(r.p[i]);
            r.p[i].add(v).add(0.0F, -GRAVITY * h * h, 0.0F);
        }
        for (int it = 0; it < ITERATIONS; it++) {
            for (int i = 1; i <= LINKS; i++) {
                Vector3f a = r.p[i - 1], b = r.p[i];
                Vector3f d = new Vector3f(b).sub(a);
                float len = d.length();
                if (len < 1.0E-5F) {
                    d.set(0.0F, -1.0F, 0.0F);
                    len = 1.0F;
                }
                Vector3f fix = d.mul((len - PITCH) / len);
                if (i == 1) {
                    b.sub(fix);                                // the ankle does not give
                } else if (i == LINKS) {
                    a.add(fix);                                // nor does the wall
                } else {
                    a.add(new Vector3f(fix).mul(0.5F));
                    b.sub(fix.mul(0.5F));
                }
            }
            for (int i = 1; i < LINKS; i++) {
                if (r.p[i].y < floor) {                        // the floor: nothing goes through it
                    r.p[i].y = floor;
                    r.prev[i].y = Math.max(r.prev[i].y, floor);
                }
            }
        }
    }

    /** Puts link j where the rope has it: its top at point j, along the rope to point j + 1. */
    private static void lay(Rope r, GeoBone bone, int j) {
        if (j < 0 || j >= LINKS) {
            return;
        }
        Vector3f at = r.toModel.transformPosition(new Vector3f(r.p[j]));
        Vector3f d = r.toModel.transformPosition(new Vector3f(r.p[j + 1])).sub(at);
        if (d.lengthSquared() < 1.0E-8F) {
            d.set(0.0F, -1.0F, 0.0F);
        }
        d.normalize();
        // the pivot to where the rope says (a top-level bone: model space is its parent's), and the link turned from
        // hanging straight down (its bind) to along the rope: d = Rz(c)·Rx(a)·(0,-1,0) - as the Turnkey's
        bone.setPosX(-(at.x - bone.getPivotX() / 16.0F) * 16.0F);
        bone.setPosY((at.y - bone.getPivotY() / 16.0F) * 16.0F);
        bone.setPosZ((at.z - bone.getPivotZ() / 16.0F) * 16.0F);
        bone.setRotX((float) -Math.asin(Math.max(-1.0F, Math.min(1.0F, d.z))));
        bone.setRotY(0.0F);
        bone.setRotZ((float) Math.atan2(d.x, -d.y));
    }
}
