package com.jastkub.frozenfortress.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Soft frost mist that spirals gently upward. */
public class FrostSwirlParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private final double swirlRadius;
    private final double swirlSpeed;
    private final double startAngle;

    protected FrostSwirlParticle(ClientLevel level, double x, double y, double z,
                                 double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        this.lifetime = 24 + random.nextInt(16);
        this.gravity = -0.02F;
        this.friction = 0.94F;
        this.xd = dx;
        this.yd = dy + 0.03D;
        this.zd = dz;
        this.quadSize = 0.12F + random.nextFloat() * 0.1F;
        this.swirlRadius = 0.02D + random.nextDouble() * 0.03D;
        this.swirlSpeed = 0.3D + random.nextDouble() * 0.4D;
        this.startAngle = random.nextDouble() * Math.PI * 2.0D;
        this.alpha = 0.85F;
        float shade = 0.85F + random.nextFloat() * 0.15F;
        setColor(shade * 0.75F, shade * 0.92F, shade);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        double angle = startAngle + age * swirlSpeed;
        xd += Math.cos(angle) * swirlRadius * 0.2D;
        zd += Math.sin(angle) * swirlRadius * 0.2D;
        alpha = 0.85F * (1.0F - (float) age / lifetime);
        setSpriteFromAge(sprites);
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
            return new FrostSwirlParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
