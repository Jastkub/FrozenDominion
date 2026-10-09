package com.jastkub.frozenfortress.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Heavy wind-driven snow of the arena blizzard. */
public class BlizzardFlakeParticle extends TextureSheetParticle {

    protected BlizzardFlakeParticle(ClientLevel level, double x, double y, double z,
                                    double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.lifetime = 40 + random.nextInt(30);
        this.gravity = 0.12F;
        this.friction = 0.99F;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.quadSize = 0.08F + random.nextFloat() * 0.08F;
        this.alpha = 0.9F;
        setColor(0.92F, 0.96F, 1.0F);
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        // Gusting: sideways pushes with per-flake phase.
        double gust = Math.sin((age + Math.abs(hashCode() % 40)) * 0.15D) * 0.004D;
        xd += gust;
        zd += gust * 0.6D;
        if (age > lifetime - 10) {
            alpha = 0.9F * (lifetime - age) / 10.0F;
        }
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
            return new BlizzardFlakeParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
