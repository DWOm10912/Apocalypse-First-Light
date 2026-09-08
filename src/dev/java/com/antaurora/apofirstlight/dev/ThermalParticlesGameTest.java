package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.blockentity.ThermalGeneratorBlockEntity.FuelSource;
import com.antaurora.apofirstlight.blockentity.ThermalGeneratorBlockEntity.VisualState;
import com.antaurora.apofirstlight.energy.ThermalParticleProfile;
import com.antaurora.apofirstlight.energy.ThermalParticleAnchors;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;

public final class ThermalParticlesGameTest {
    public static void profile(GameTestHelper h) {
        for(var state:VisualState.values())for(var source:FuelSource.values()) {
            int[] counts=new int[5];
            for(int tick=0;tick<144;tick++)for(var fx:ThermalParticleProfile.Effect.values()) {
                boolean due=ThermalParticleProfile.due(fx,state,source,true,tick);
                if(due)counts[fx.ordinal()]++;
                if(state!=VisualState.RUNNING || source==FuelSource.NONE)h.assertTrue(!due,"inactive particles");
                if(source==FuelSource.LIQUID && fx==ThermalParticleProfile.Effect.FLAME)h.assertTrue(!due,"liquid coal flame");
            }
            if(state==VisualState.RUNNING && source==FuelSource.SOLID)
                h.assertTrue(counts[0]==48 && counts[1]==16 && counts[2]==9 && counts[3]==36 && counts[4]==0,"solid bounded cadence");
            if(state==VisualState.RUNNING && source==FuelSource.LIQUID)
                h.assertTrue(counts[0]==0 && counts[1]==0 && counts[2]==0 && counts[3]==24 && counts[4]==12,"liquid bounded cadence");
        }
        h.assertTrue(!ThermalParticleProfile.due(ThermalParticleProfile.Effect.LAVA,VisualState.RUNNING,FuelSource.LIQUID,false,3),"nonlava no lava particles");
        for(double[] a:new double[][]{ThermalParticleAnchors.SOLID_FLAME_FX_ANCHOR,ThermalParticleAnchors.SOLID_EMBER_FX_ANCHOR,
                ThermalParticleAnchors.LIQUID_FX_ANCHOR,ThermalParticleAnchors.EXHAUST}) {
            var north=ThermalParticleProfile.rotate(a[0],a[1],a[2],Direction.NORTH);
            var east=ThermalParticleProfile.rotate(a[0],a[1],a[2],Direction.EAST);
            var south=ThermalParticleProfile.rotate(a[0],a[1],a[2],Direction.SOUTH);
            var west=ThermalParticleProfile.rotate(a[0],a[1],a[2],Direction.WEST);
            h.assertTrue(Math.abs(east.x-(1-north.z))<1e-8 && east.z==north.x,"east transform");
            h.assertTrue(Math.abs(south.x+north.x-1)<1e-8 && Math.abs(south.z+north.z-1)<1e-8,"south transform");
            h.assertTrue(west.x==north.z && Math.abs(west.z+north.x-1)<1e-8,"west transform");
        }
        h.succeed();
    }
}
