package com.antaurora.apofirstlight.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public final class ContaminationMoteParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float initialAlpha;

    private ContaminationMoteParticle(ClientLevel level, double x, double y, double z,
                                      double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0.0F;
        this.friction = 0.98F;
        this.quadSize = 0.30F + this.random.nextFloat() * 0.25F;
        this.lifetime = 50 + this.random.nextInt(41);
        this.initialAlpha = 1.0F;
        this.setAlpha(initialAlpha);
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.age > this.lifetime * 0.72F) {
            float remaining = (this.lifetime - this.age) / (this.lifetime * 0.28F);
            this.setAlpha(Math.max(0.0F, Math.min(initialAlpha, remaining)));
        }
        this.xd += (this.random.nextDouble() - 0.5D) * 0.001D;
        this.zd += (this.random.nextDouble() - 0.5D) * 0.001D;
        this.xd = Math.max(-0.01D, Math.min(0.01D, this.xd));
        this.zd = Math.max(-0.01D, Math.min(0.01D, this.zd));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public net.minecraft.client.particle.Particle createParticle(SimpleParticleType type, ClientLevel level,
                                                                        double x, double y, double z,
                                                                        double xd, double yd, double zd) {
            return new ContaminationMoteParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
