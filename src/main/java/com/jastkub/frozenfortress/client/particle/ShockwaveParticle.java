package com.jastkub.frozenfortress.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A ring of force expanding flat along the ground. */
public class ShockwaveParticle extends TextureSheetParticle {

    protected ShockwaveParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.lifetime = 14;
        this.gravity = 0.0F;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.quadSize = 1.0F;
        this.alpha = 0.9F;
        setColor(0.8F, 0.93F, 1.0F);
        pickSprite(sprites);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        Vec3 camPos = camera.getPosition();
        float px = (float) (Mth.lerp(partialTick, xo, x) - camPos.x());
        float py = (float) (Mth.lerp(partialTick, yo, y) - camPos.y());
        float pz = (float) (Mth.lerp(partialTick, zo, z) - camPos.z());

        float progress = (age + partialTick) / lifetime;
        float radius = 1.0F + progress * 8.0F;
        float fade = alpha * (1.0F - progress);

        float u0 = getU0();
        float u1 = getU1();
        float v0 = getV0();
        float v1 = getV1();
        int light = getLightColor(partialTick);

        // Flat quad on the ground plane.
        buffer.addVertex(px - radius, py + 0.02F, pz - radius).setUv(u0, v0)
                .setColor(rCol, gCol, bCol, fade).setLight(light);
        buffer.addVertex(px - radius, py + 0.02F, pz + radius).setUv(u0, v1)
                .setColor(rCol, gCol, bCol, fade).setLight(light);
        buffer.addVertex(px + radius, py + 0.02F, pz + radius).setUv(u1, v1)
                .setColor(rCol, gCol, bCol, fade).setLight(light);
        buffer.addVertex(px + radius, py + 0.02F, pz - radius).setUv(u1, v0)
                .setColor(rCol, gCol, bCol, fade).setLight(light);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double dx, double dy, double dz) {
            return new ShockwaveParticle(level, x, y, z, sprites);
        }
    }
}
