package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.registry.AflFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/** Visual parameters and camera-volume predicate; no client classes or fluid behaviour changes. */
public final class IndustrialWasteFog {
    // Rounded still-texture mean (100,101,77), darkened to 70%: low-saturation grey/olive.
    public static final float RED = 70.0F / 255.0F;
    public static final float GREEN = 71.0F / 255.0F;
    public static final float BLUE = 54.0F / 255.0F;
    public static final float START = 1.5F;
    public static final float END = 10.0F;

    private IndustrialWasteFog() {
    }

    public static boolean isCameraSubmerged(BlockGetter level, Vec3 cameraPosition) {
        BlockPos pos = BlockPos.containing(cameraPosition);
        FluidState fluid = level.getFluidState(pos);
        if (fluid.isEmpty() || fluid.getFluidType() != AflFluids.INDUSTRIAL_WASTE_TYPE.get()) return false;
        // Same strict surface-height comparison as ForgeHooksClient; covers source and flowing levels.
        return cameraPosition.y < (double) pos.getY() + fluid.getHeight(level, pos);
    }
}
