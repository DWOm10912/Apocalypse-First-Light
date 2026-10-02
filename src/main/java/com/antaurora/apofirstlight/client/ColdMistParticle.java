package com.antaurora.apofirstlight.client;

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

/**
 * Cold air from a powered chest freezer (spawned by {@link ColdMist}): a soft pale puff (cold_mist_0..3, from
 * tools/build-cold-mist-sprites.mjs) that fades in, grows, sinks slowly and drifts, stops at block collision shapes (so a
 * closed lid keeps it in the well), and spreads a little where it lands. Its physics point is a 0.02 box at the bottom
 * of the puff: the quad is drawn lifted by half its half-size, so mist resting on a floor lies on it instead of half
 * through it. Not frustum-culled: the box is far smaller than the quad, and there are few of these.
 */
public final class ColdMistParticle extends TextureSheetParticle {
    private static final float LIFT = .5F;
    private float startSize = .18F, growth = 1.5F, peakAlpha = .12F;
    private final float spin;
    private double gravity = .0004, drag = .95, swirl = .001;
    private boolean landed;

    private ColdMistParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                             SpriteSet sprites) {
        super(level, x, y, z);
        setSize(.02F, .02F);
        xd = vx;
        yd = vy;
        zd = vz;
        lifetime = 60;
        quadSize = startSize;
        alpha = 0;
        roll = oRoll = random.nextFloat() * Mth.TWO_PI;
        spin = (random.nextFloat() - .5F) * .02F;
        float tint = .94F + random.nextFloat() * .06F;
        setColor(.88F * tint, .94F * tint, tint);
        pickSprite(sprites);
    }

    /**
     * life: ticks; size: starting quad half-size, blocks; growth: final / starting size; alpha: at its fullest. Per tick:
     * gravity (blocks/tick, downward), drag (velocity factor), swirl (random velocity kick, blocks/tick).
     */
    public void configure(int life, float size, float growth, float alpha, double gravity, double drag, double swirl) {
        lifetime = life;
        quadSize = startSize = size;
        this.growth = growth;
        peakAlpha = alpha;
        this.gravity = gravity;
        this.drag = drag;
        this.swirl = swirl;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        xd += (random.nextDouble() - .5) * swirl;
        zd += (random.nextDouble() - .5) * swirl;
        yd += (random.nextDouble() - .5) * swirl * .5 - gravity;
        double falling = -yd;
        move(xd, yd, zd);
        if (onGround) {
            // cold air spreads over what it lands on
            if (!landed && falling > 0) {
                landed = true;
                double across = Math.sqrt(xd * xd + zd * zd), push = falling * .8;
                if (across < 1e-4) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    xd += Math.cos(angle) * push;
                    zd += Math.sin(angle) * push;
                } else {
                    xd += xd / across * push;
                    zd += zd / across * push;
                }
            }
            yd = 0;
        }
        xd *= drag;
        yd *= drag;
        zd *= drag;
        roll += spin;
        float t = (float) age / lifetime, out = Mth.clamp((t - .45F) / .55F, 0, 1);
        alpha = peakAlpha * Math.min(1, t / .2F) * (1 - out * out * (3 - 2 * out));
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = Mth.clamp((age + partialTick) / lifetime, 0, 1);
        return startSize * (1 + (growth - 1) * (1 - (1 - t) * (1 - t)));
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        double lift = getQuadSize(partialTick) * LIFT;
        y += lift;
        yo += lift;
        try {
            super.render(buffer, camera, partialTick);
        } finally {
            y -= lift;
            yo -= lift;
        }
    }

    @Override
    public boolean shouldCull() {
        return false;
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
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new ColdMistParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
