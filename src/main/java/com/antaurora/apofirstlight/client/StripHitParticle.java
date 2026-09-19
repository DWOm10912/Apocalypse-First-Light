package com.antaurora.apofirstlight.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/** One shared billboard implementation for eight-frame horizontal hit-effect strips. */
public final class StripHitParticle extends TextureSheetParticle {
    private final float spin;
    private StripHitParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,SpriteSet sprites) {
        super(level,x,y,z);
        xd=vx;yd=vy;zd=vz;gravity=0;friction=.96F;hasPhysics=false;
        quadSize=.10F+random.nextFloat()*.08F;
        lifetime=8+random.nextInt(5); // At least eight ticks: do not skip authored frames.
        roll=random.nextFloat()*(float)(Math.PI*2);oRoll=roll;
        spin=(random.nextFloat()-.5F)*.12F;
        pickSprite(sprites);setAlpha(1);
    }
    @Override protected float getU0(){return sprite.getU(HitParticleAnimation.frame(age,lifetime)*2);}
    @Override protected float getU1(){return sprite.getU((HitParticleAnimation.frame(age,lifetime)+1)*2);}
    @Override public void tick(){
        super.tick();
        if(removed)return;
        oRoll=roll;roll+=spin;setAlpha(HitParticleAnimation.alpha(age,lifetime));
    }
    @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites){this.sprites=sprites;}
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel level,double x,double y,double z,double vx,double vy,double vz){
            return new StripHitParticle(level,x,y,z,vx,vy,vz,sprites);
        }
    }
}
