package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.ThermalGeneratorBlock;
import com.antaurora.apofirstlight.blockentity.ThermalGeneratorBlockEntity;
import com.antaurora.apofirstlight.energy.ThermalParticleAnchors;
import com.antaurora.apofirstlight.energy.ThermalParticleProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.BlockState;

/** Client BE tick, never a render-frame callback or animateTick burst. Vanilla particles only. */
public final class ThermalGeneratorParticles {
    private static final ThermalParticleProfile.Effect[] EFFECTS = ThermalParticleProfile.Effect.values();
    private ThermalGeneratorParticles() { }

    public static void tick(Level level, BlockPos pos, BlockState state, ThermalGeneratorBlockEntity machine) {
        var mc = Minecraft.getInstance();
        if (!level.isClientSide || mc.isPaused() || mc.player == null
                || mc.player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>32*32
                || machine.getVisualState()!=ThermalGeneratorBlockEntity.VisualState.RUNNING) return;
        var fluid = machine.getLiquidFuel();
        boolean lava = !fluid.isEmpty() && fluid.getFluid().isSame(Fluids.LAVA);
        long tick = level.getGameTime() + Math.floorMod(pos.asLong() ^ (pos.asLong() >>> 16),997);
        var random = level.random;
        for (var effect : EFFECTS) {
            if (!ThermalParticleProfile.due(effect,machine.getVisualState(),machine.getActiveFuelSource(),lava,tick)) continue;
            // Direct vanilla factory calls must still respect the user's particle setting.
            var setting=mc.options.particles().get();
            if(setting==net.minecraft.client.ParticleStatus.MINIMAL
                    && (effect!=ThermalParticleProfile.Effect.EXHAUST || random.nextInt(4)!=0))continue;
            if(setting==net.minecraft.client.ParticleStatus.DECREASED && !random.nextBoolean())continue;
            if ((effect==ThermalParticleProfile.Effect.EMBER || effect==ThermalParticleProfile.Effect.LAVA)
                    && !random.nextBoolean()) continue;
            double[] anchor = switch(effect) {
                case EXHAUST -> ThermalParticleAnchors.EXHAUST;
                case EMBER -> ThermalParticleAnchors.SOLID_EMBER_FX_ANCHOR;
                case LAVA -> ThermalParticleAnchors.LIQUID_FX_ANCHOR;
                default -> ThermalParticleAnchors.SOLID_FLAME_FX_ANCHOR;
            };
            double x=anchor[0],y=anchor[1],z=anchor[2];
            boolean exhaust=effect==ThermalParticleProfile.Effect.EXHAUST;
            x+=(random.nextDouble()-.5)*(exhaust?.035:.10);
            z+=(random.nextDouble()-.5)*(exhaust?.035:.018);
            if(effect==ThermalParticleProfile.Effect.LAVA) {
                var min=ThermalGeneratorRenderGeometry.LIQUID_MIN;
                var max=ThermalGeneratorRenderGeometry.LIQUID_MAX;
                y=min[1]+(max[1]-min[1])*Math.min(1.0,(double)fluid.getAmount()/machine.getTankCapacity());
            } else if(!exhaust) {
                // Above the existing coal pile / coal-block top, still well inside the chamber.
                y=Math.max(y,.39)+(effect==ThermalParticleProfile.Effect.INTERNAL_SMOKE?.045:0)
                        +random.nextDouble()*.008;
            }
            var point=ThermalParticleProfile.rotate(x,y,z,state.getValue(ThermalGeneratorBlock.FACING));
            var type=switch(effect) {
                case FLAME -> ParticleTypes.SMALL_FLAME;
                case INTERNAL_SMOKE -> ParticleTypes.SMOKE;
                case EMBER,LAVA -> ParticleTypes.LAVA;
                case EXHAUST -> ParticleTypes.CAMPFIRE_COSY_SMOKE;
            };
            var particle=mc.particleEngine.createParticle(type,pos.getX()+point.x,pos.getY()+point.y,pos.getZ()+point.z,
                    0,exhaust?.012:.002,0);
            if(particle==null)continue;
            if(exhaust) {
                particle.scale(.28f);
                particle.setLifetime(80);
            } else {
                // Lava's vanilla constructor adds a strong random launch; keep this enclosed effect gentle.
                particle.setParticleSpeed((random.nextDouble()-.5)*.0004,
                        effect==ThermalParticleProfile.Effect.EMBER?.006:.002,
                        (random.nextDouble()-.5)*.0004);
                particle.scale(effect==ThermalParticleProfile.Effect.FLAME?.6f:.18f);
                if(effect==ThermalParticleProfile.Effect.INTERNAL_SMOKE)particle.setLifetime(12);
            }
        }
    }
}
