package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.client.HitParticleAnimation;
import com.antaurora.apofirstlight.registry.AflParticles;
import com.antaurora.apofirstlight.weapon.NativeHitEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Called only by authoritative shot-result packets. No client raycast or held-weapon lookup. */
public final class NativeHitEffects {
    private NativeHitEffects(){}
    public static void play(String preset,Vec3 point){
        if(preset.isEmpty())return;
        var level=Minecraft.getInstance().level;if(level==null)return;
        NativeHitEffect effect;
        try{effect=NativeHitEffect.parse(preset);}catch(IllegalArgumentException ignored){return;}
        switch(effect){
            case NONE -> {}
            case DIZZY_STARS -> {
                var r=level.random;int count=3+r.nextInt(4);
                for(int i=0;i<count;i++){
                    var particle=switch(HitParticleAnimation.variant(r.nextInt(100))){
                        case 0->AflParticles.HIT_YELLOW_STAR.get();
                        case 1->AflParticles.HIT_BLUE_STAR.get();
                        default->AflParticles.HIT_DIZZY.get();
                    };
                    double radius=.05+r.nextDouble()*.07,theta=r.nextDouble()*Math.PI*2,vertical=r.nextDouble()*2-1;
                    double horizontal=Math.sqrt(1-vertical*vertical);
                    level.addParticle(particle,point.x+radius*horizontal*Math.cos(theta),point.y+radius*vertical,
                            point.z+radius*horizontal*Math.sin(theta),
                            (r.nextDouble()-.5)*.035,.012+r.nextDouble()*.023,(r.nextDouble()-.5)*.035);
                }
            }
        }
    }
}
