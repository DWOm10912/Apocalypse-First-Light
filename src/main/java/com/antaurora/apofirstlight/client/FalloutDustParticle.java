package com.antaurora.apofirstlight.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Fine, pale fallout dust with a slow downward drift. */
public final class FalloutDustParticle extends TextureSheetParticle {
    private final double phase;
    private boolean counted = true;

    private FalloutDustParticle(ClientLevel level, double x, double y, double z,
                                double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        this.gravity = 0.0F;
        this.friction = 0.995F;
        this.hasPhysics = false;
        this.quadSize = 0.035F + this.random.nextFloat() * 0.035F;
        this.lifetime = 100 + this.random.nextInt(81);
        this.phase = this.random.nextDouble() * Math.PI * 2.0D;
        this.setAlpha(0.0F);
        this.pickSprite(sprites);
        EnvironmentalParticleController.onParticleCreated();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) return;
        this.xd += Math.sin(this.age * 0.11D + phase) * 0.00035D;
        this.zd += Math.cos(this.age * 0.09D + phase) * 0.00035D;
        this.xd = Math.max(-0.018D, Math.min(0.018D, this.xd));
        this.zd = Math.max(-0.018D, Math.min(0.018D, this.zd));
        float alpha = 0.0F;
        if (this.age < 12) alpha = this.age / 12.0F;
        else if (this.age < this.lifetime - 20) alpha = 0.62F;
        else alpha = Math.max(0.0F, (this.lifetime - this.age) / 20.0F) * 0.62F;
        this.setAlpha(alpha);
    }

    @Override
    public void remove() {
        super.remove();
        if (counted) {
            counted = false;
            EnvironmentalParticleController.onParticleRemoved();
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new FalloutDustParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
