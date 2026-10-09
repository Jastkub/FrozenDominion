package com.jastkub.frozenfortress.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Sharp glittering ice fragments with real weight; thrown by impacts. */
public class IceShardParticle extends TextureSheetParticle {

    protected IceShardParticle(ClientLevel level, double x, double y, double z,
                               double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.lifetime = 16 + random.nextInt(14);
        this.gravity = 0.7F;
        this.friction = 0.96F;
        this.xd = dx * (0.8D + random.nextDouble() * 0.4D);
        this.yd = dy + 0.25D + random.nextDouble() * 0.2D;
        this.zd = dz * (0.8D + random.nextDouble() * 0.4D);
        this.quadSize = 0.07F + random.nextFloat() * 0.07F;
        this.roll = random.nextFloat() * (float) Math.PI * 2.0F;
        float shade = 0.9F + random.nextFloat() * 0.1F;
        setColor(shade * 0.8F, shade * 0.95F, shade);
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        oRoll = roll;
        roll += 0.35F;
        super.tick();
        if (onGround) {
            xd *= 0.4D;
            zd *= 0.4D;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double dx, double dy, double dz) {
            return new IceShardParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
