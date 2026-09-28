package com.antaurora.apofirstlight.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/** One short, translucent gas puff per confirmed suppressed shot. Input velocity is the muzzle axis. */
public final class SuppressorMuzzleSmokeParticle extends TextureSheetParticle {
    private static final float SIZE = .045F, OPACITY = .20F;

    private SuppressorMuzzleSmokeParticle(ClientLevel level, double x, double y, double z,
                                         double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        xd = vx; yd = vy; zd = vz;
        hasPhysics = false;
        lifetime = 4;
        quadSize = SIZE;
        alpha = OPACITY;
        roll = oRoll = 0;
        setColor(.86F, .85F, .83F);
        pickSprite(sprites);
    }

    @Override public void tick() {
        xo = x; yo = y; zo = z; oRoll = roll;
        if (++age >= lifetime) { remove(); return; }
        move(xd, yd, zd);
        xd *= .78; zd *= .78;
        yd = yd * .78 + (age >= 2 ? .001 : 0);
        float remaining = 1F - (float)age / lifetime;
        alpha = OPACITY * remaining * remaining;
    }

    @Override public float getQuadSize(float partial) {
        return SIZE * (1F + .25F * Math.min(1F, (age + partial) / lifetime));
    }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            return new SuppressorMuzzleSmokeParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
