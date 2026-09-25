package com.antaurora.apofirstlight.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/** Detached, unlit-by-emission vapor. Lighting is sampled by the gun once, not per particle. */
public final class ChamberGasParticle extends TextureSheetParticle {
    public enum Stage { JET_CORE, EXPANSION_CLOUD, RESIDUAL }
    private Stage stage = Stage.RESIDUAL;
    private float initialSize = .03F, initialAlpha = .4F, growth = 2.7F;
    private double drag = .38, buoyancy = .002;
    private int birthLight;

    private ChamberGasParticle(ClientLevel level, double x, double y, double z,
                               double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        xd = vx; yd = vy; zd = vz;
        hasPhysics = false;
        lifetime = 8;
        quadSize = initialSize;
        alpha = initialAlpha;
        roll = oRoll = random.nextFloat() * (float)(Math.PI * 2);
        float tint = .80F + random.nextFloat() * .15F;
        setColor(tint, tint, tint);
        pickSprite(sprites);
    }

    public void configure(int life, float size, float opacity, double drag, double buoyancy,
                          float growth, int light, Stage stage) {
        lifetime = life;
        quadSize = initialSize = size;
        alpha = initialAlpha = opacity;
        this.drag = drag;
        this.buoyancy = buoyancy;
        this.growth = growth;
        birthLight = light;
        this.stage = stage;
        if (stage != Stage.RESIDUAL) {
            float tint = (stage == Stage.JET_CORE ? .94F : .86F) + random.nextFloat() * .05F;
            setColor(tint, tint, tint);
        }
    }

    @Override public void tick() {
        xo = x; yo = y; zo = z; oRoll = roll;
        if (++age >= lifetime) { remove(); return; }
        move(xd, yd, zd);
        // Preserve the first axial step; brake on tick 2, then turn into a short-lived cloud.
        double damping = stage == Stage.JET_CORE
                ? (age == 1 ? drag : age == 2 ? .48 : .72)
                : age <= 2 ? drag : Math.max(drag, .82);
        xd *= damping; zd *= damping;
        yd = yd * damping + (stage == Stage.RESIDUAL || age >= 3 ? buoyancy : 0);
        roll += .018F;
        float t = (float)age / lifetime;
        if (stage == Stage.RESIDUAL) {
            alpha = initialAlpha * (1 - t) * (1 - t);
        } else {
            float hold = stage == Stage.JET_CORE ? .25F : .15F;
            float fade = Math.max(0, (t - hold) / (1 - hold));
            alpha = initialAlpha * (1 - fade * fade * (3 - 2 * fade));
        }
    }

    @Override public float getQuadSize(float partial) {
        float t = Math.min(1, (age + partial) / lifetime * 2);
        return initialSize * (1 + (growth - 1) * (1 - (1 - t) * (1 - t)));
    }

    @Override public int getLightColor(float partial) { return birthLight; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            return new ChamberGasParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
