package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.entity.effect.KingsrimeSwordShackleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The frost that holds (KingsrimeSwordShackleEntity) as GEOMETRY: six crystals of ice grown up round the feet of what
 * it holds in two ticks, leaning in on it, sized to it, and a patch of frost on the floor under them. When it lets go
 * they are thrown outward and fade.
 */
public class KingsrimeSwordShackleRenderer extends EntityRenderer<KingsrimeSwordShackleEntity> {

    static final int CRYSTALS = 6;

    public KingsrimeSwordShackleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(KingsrimeSwordShackleEntity e) {
        return KingsrimeSwordMesh.CRYSTAL;
    }

    @Override
    public void render(KingsrimeSwordShackleEntity e, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        float t = e.tickCount + partialTick;
        poseStack.pushPose();
        LivingEntity h = e.holder();
        if (h != null) {
            poseStack.translate(Mth.lerp(partialTick, h.xo, h.getX()) - Mth.lerp(partialTick, e.xo, e.getX()),
                    Mth.lerp(partialTick, h.yo, h.getY()) - Mth.lerp(partialTick, e.yo, e.getY()),
                    Mth.lerp(partialTick, h.zo, h.getZ()) - Mth.lerp(partialTick, e.zo, e.getZ()));
        }
        Matrix4f m = poseStack.last().pose();
        Matrix3f n = poseStack.last().normal();
        int hold = e.life() - KingsrimeSwordShackleEntity.BREAK;
        float free = Mth.clamp((t - hold) / KingsrimeSwordShackleEntity.BREAK, 0.0F, 1.0F);
        float w = e.holderWidth(), hh = e.holderHeight();
        VertexConsumer rc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(KingsrimeSwordMesh.RING));
        disc(rc, m, n, w * 0.8F + 0.35F, 0.75F * (1.0F - free) * Mth.clamp(t / 2.0F, 0.0F, 1.0F));
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(KingsrimeSwordMesh.CRYSTAL));
        crystals(vc, m, n, t, free, w, hh, e.getId());
        poseStack.popPose();
        super.render(e, yaw, partialTick, poseStack, bufferSource, packedLight);
    }

    static void crystals(VertexConsumer vc, Matrix4f m, Matrix3f n, float t, float free, float w, float hh, int id) {
        float grow = Mth.clamp(t / 2.0F, 0.0F, 1.0F);
        float size = Mth.clamp(w / 0.6F, 0.8F, 2.6F);
        float len0 = Mth.clamp(hh * 0.45F, 0.45F, 1.7F);
        for (int k = 0; k < CRYSTALS; k++) {
            float hk = KingsrimeSwordMesh.hash(k, id), hk2 = KingsrimeSwordMesh.hash(k, id + 5);
            float a = Mth.TWO_PI * (k + 0.3F * hk) / CRYSTALS;
            float c = Mth.cos(a), s = Mth.sin(a);
            float r = w * 0.5F + 0.1F + 0.08F * hk2;
            float[] base = {c * r + c * 0.9F * free, 0.3F * free, s * r + s * 0.9F * free};
            float lean = 0.55F - 0.9F * free;
            float[] axis = KingsrimeSwordMesh.norm(-c * lean, 1.0F, -s * lean);
            float len = len0 * (0.8F + 0.4F * hk) * grow;
            KingsrimeSwordMesh.blade(vc, m, n, base, axis, new float[]{-s, 0.0F, c}, len, 0.11F * size, 0.07F * size, 0.3F,
                    0.86F, 0.95F, 1.0F, 0.9F * (1.0F - free * free));
        }
    }

    /** Frost on the floor: the ring sheet's last band, its rim at the edge and its middle at the centre. */
    static void disc(VertexConsumer vc, Matrix4f m, Matrix3f n, float r, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        int seg = 20;
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float u0 = 4.0F * i / seg, u1 = 4.0F * (i + 1) / seg;
            KingsrimeSwordMesh.put(vc, m, n, 0.0F, 0.03F, 0.0F, (u0 + u1) * 0.5F, KingsrimeSwordMesh.DISC_V1 - 0.01F,
                    1.0F, 1.0F, 1.0F, alpha);
            KingsrimeSwordMesh.put(vc, m, n, Mth.cos(a0) * r, 0.03F, Mth.sin(a0) * r, u0, KingsrimeSwordMesh.DISC_V0 + 0.01F,
                    1.0F, 1.0F, 1.0F, alpha);
            KingsrimeSwordMesh.put(vc, m, n, Mth.cos(a1) * r, 0.03F, Mth.sin(a1) * r, u1, KingsrimeSwordMesh.DISC_V0 + 0.01F,
                    1.0F, 1.0F, 1.0F, alpha);
            KingsrimeSwordMesh.put(vc, m, n, 0.0F, 0.03F, 0.0F, (u0 + u1) * 0.5F, KingsrimeSwordMesh.DISC_V1 - 0.01F,
                    1.0F, 1.0F, 1.0F, alpha);
        }
    }
}
