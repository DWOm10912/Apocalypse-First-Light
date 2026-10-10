package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.DieselGeneratorBlock;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The diesel generator's fuel supply connection (docs/machines/diesel_standby_generator_v1.md): the standard fluid port on
 * the radiator end, the end face of its third column's bottom cell (c2r0, block/DieselGeneratorBlock#hasFluidPort). Holds
 * nothing: a pipe on that face reaches the master's sub-base tank, fill only (DieselGeneratorBlockEntity#pipeInlet).
 */
public class DieselGeneratorPortBlockEntity extends BlockEntity {
    private LazyOptional<IFluidHandler> inlet = LazyOptional.of(this::masterInlet);

    public DieselGeneratorPortBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.DIESEL_GENERATOR_PORT.get(), pos, state);
    }

    /** The master's inlet, looked up each time (it may not be loaded yet); an inert handler meanwhile. */
    private IFluidHandler masterInlet() {
        return new IFluidHandler() {
            @Nullable
            private IFluidHandler target() {
                BlockState state = getBlockState();
                if (level == null || !(state.getBlock() instanceof DieselGeneratorBlock block)) return null;
                BlockPos master = block.masterPosition(worldPosition, state);
                return level.isLoaded(master) && level.getBlockEntity(master) instanceof DieselGeneratorBlockEntity generator ? generator.pipeInlet() : null;
            }

            @Override public int getTanks() { IFluidHandler t = target(); return t == null ? 0 : t.getTanks(); }
            @Override public @NotNull net.minecraftforge.fluids.FluidStack getFluidInTank(int tank) { IFluidHandler t = target(); return t == null ? net.minecraftforge.fluids.FluidStack.EMPTY : t.getFluidInTank(tank); }
            @Override public int getTankCapacity(int tank) { IFluidHandler t = target(); return t == null ? 0 : t.getTankCapacity(tank); }
            @Override public boolean isFluidValid(int tank, @NotNull net.minecraftforge.fluids.FluidStack stack) { IFluidHandler t = target(); return t != null && t.isFluidValid(tank, stack); }
            @Override public int fill(net.minecraftforge.fluids.FluidStack resource, FluidAction action) { IFluidHandler t = target(); return t == null ? 0 : t.fill(resource, action); }
            @Override public @NotNull net.minecraftforge.fluids.FluidStack drain(net.minecraftforge.fluids.FluidStack resource, FluidAction action) { return net.minecraftforge.fluids.FluidStack.EMPTY; }
            @Override public @NotNull net.minecraftforge.fluids.FluidStack drain(int maxDrain, FluidAction action) { return net.minecraftforge.fluids.FluidStack.EMPTY; }
        };
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        BlockState state = getBlockState();
        if (capability == ForgeCapabilities.FLUID_HANDLER && side != null && state.getBlock() instanceof DieselGeneratorBlock block && block.hasFluidPort(state, side))
            return inlet.cast();
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inlet.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        inlet = LazyOptional.of(this::masterInlet);
    }
}
