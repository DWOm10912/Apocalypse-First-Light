package com.antaurora.apofirstlight.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraftforge.fluids.ForgeFlowingFluid;

/** Gasoline / diesel (FuelFluidType): never makes new sources; no ambient particles or drips. */
public abstract class FuelFluid extends ForgeFlowingFluid {
    protected FuelFluid(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canConvertToSource(FluidState state, Level level, BlockPos pos) {
        return false;
    }

    public static final class Source extends FuelFluid {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }

    public static final class Flowing extends FuelFluid {
        public Flowing(Properties properties) {
            super(properties);
            registerDefaultState(getStateDefinition().any().setValue(LEVEL, 7));
        }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }
}
