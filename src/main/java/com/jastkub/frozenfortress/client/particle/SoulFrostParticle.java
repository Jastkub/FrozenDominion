package com.jastkub.frozenfortress.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Dark-cyan wisps of the Hollow Winter; they rise and fade like breath. */
public class SoulFrostParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected SoulFrostParticle(ClientLevel level, double x, double y, double z,
                                double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        this.lifetime = 30 + random.nextInt(24);
        this.gravity = -0.04F;
        this.friction = 0.92F;
        this.xd = dx;
        this.yd = dy + 0.06D;
        this.zd = dz;
        this.quadSize = 0.14F + random.nextFloat() * 0.12F;
        this.alpha = 0.9F;
        setColor(0.35F + random.nextFloat() * 0.1F, 0.75F, 0.85F);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        alpha = 0.9F * (1.0F - (float) age / lifetime);
        quadSize *= 0.985F;
        setSpriteFromAge(sprites);
    }

    @Override
    public int getLightColor(float partialTick) {
        // Self-lit: the Hollow Winter glows from within.
        return 0xF000F0;
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
            return new SoulFrostParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
