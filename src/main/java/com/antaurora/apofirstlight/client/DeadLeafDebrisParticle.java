package com.antaurora.apofirstlight.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;

/** A light, client-only piece of dry vegetation carried by a small gust. */
public final class DeadLeafDebrisParticle extends TextureSheetParticle {
    private final float baseSize;
    private final float initialRollVelocity;
    private final double flutterPhase;
    private final double flutterStrength;
    private boolean counted = true;

    private DeadLeafDebrisParticle(ClientLevel level, double x, double y, double z,
                                   double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0.0F;
        this.friction = 0.99F;
        this.hasPhysics = false;
        this.baseSize = 0.22F + this.random.nextFloat() * 0.13F;
        this.quadSize = baseSize * (0.5F + this.random.nextFloat() * 0.2F);
        this.lifetime = 80 + this.random.nextInt(81);
        this.flutterPhase = this.random.nextDouble() * Math.PI * 2.0D;
        this.flutterStrength = 0.002D + this.random.nextDouble() * 0.002D;
        this.roll = this.random.nextFloat() * ((float) Math.PI * 2.0F);
        this.oRoll = this.roll;
        this.initialRollVelocity = (this.random.nextBoolean() ? 1.0F : -1.0F)
                * (0.01745F + this.random.nextFloat() * 0.03491F);
        this.setAlpha(0.0F);
        this.pickSprite(sprites);
        EnvironmentalParticleController.onParticleCreated();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) return;

        if (!this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).isEmpty()) {
            this.lifetime = Math.min(this.lifetime, this.age + 10);
        }

        double flutter = Math.sin(this.age * 0.19D + flutterPhase) * flutterStrength;
        this.xd += flutter;
        this.zd += Math.cos(this.age * 0.17D + flutterPhase) * flutterStrength;
        this.xd = clamp(this.xd, -0.03D, 0.03D);
        this.zd = clamp(this.zd, -0.03D, 0.03D);
        this.oRoll = this.roll;
        this.roll += initialRollVelocity * (float) Math.pow(0.99D, this.age);

        float alpha = 1.0F;
        if (this.age < 8) alpha = this.age / 8.0F;
        if (this.age > this.lifetime - 24) alpha = Math.min(alpha, (this.lifetime - this.age) / 24.0F);
        this.setAlpha(Math.max(0.0F, alpha));

        float size = baseSize;
        if (this.age < 8) size *= 0.5F + 0.5F * this.age / 8.0F;
        if (this.age > this.lifetime - 24) size *= 0.65F + 0.35F * (this.lifetime - this.age) / 24.0F;
        this.quadSize = size;
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new DeadLeafDebrisParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
