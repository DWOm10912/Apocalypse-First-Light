package com.antaurora.apofirstlight.energy;

import com.antaurora.apofirstlight.blockentity.ThermalGeneratorBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Fixed normal-operation profile; future soot can select a different smoke interval. */
public final class ThermalParticleProfile {
    public enum Effect { FLAME, INTERNAL_SMOKE, EMBER, EXHAUST, LAVA }
    public static final int SOLID_EXHAUST_INTERVAL = 4;
    public static final int LIQUID_EXHAUST_INTERVAL = 6;
    private ThermalParticleProfile() { }

    public static boolean due(Effect effect, ThermalGeneratorBlockEntity.VisualState state,
                              ThermalGeneratorBlockEntity.FuelSource source,
                              boolean lavaPresent, long tick) {
        if (state != ThermalGeneratorBlockEntity.VisualState.RUNNING) return false;
        boolean solid = source == ThermalGeneratorBlockEntity.FuelSource.SOLID;
        boolean liquid = source == ThermalGeneratorBlockEntity.FuelSource.LIQUID;
        return switch (effect) {
            case FLAME -> solid && Math.floorMod(tick,3)==0;
            case INTERNAL_SMOKE -> solid && Math.floorMod(tick,9)==1;
            case EMBER -> solid && Math.floorMod(tick,16)==2;
            case EXHAUST -> (solid || liquid) && Math.floorMod(tick,
                    solid ? SOLID_EXHAUST_INTERVAL : LIQUID_EXHAUST_INTERVAL)==0;
            case LAVA -> liquid && lavaPresent && Math.floorMod(tick,12)==3;
        };
    }

    /** Same NORTH-local rotation as the baked model and BER, about the block center. */
    public static Vec3 rotate(double x, double y, double z, Direction facing) {
        return switch (facing) {
            case EAST -> new Vec3(1-z,y,x);
            case SOUTH -> new Vec3(1-x,y,1-z);
            case WEST -> new Vec3(z,y,1-x);
            default -> new Vec3(x,y,z);
        };
    }
}
