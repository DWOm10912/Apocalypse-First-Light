package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.UndergroundFuelTankBlock;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Underground Fuel Tank V1, on the port cell (the master): one {@link #CAPACITY_MB} mB tank of the block's fuel only
 * (UndergroundFuelTankBlock#fuel), reached through the AFL fluid port on the cell's top face. Passive storage: pipes fill
 * it and draw from it; nothing here pushes or pumps (the pump is the next step, docs/models/underground_fuel_tank_v1.md).
 */
public class UndergroundFuelTankBlockEntity extends BlockEntity {
    /** About the tank's volume (2.4 m across, ~6.4 m long): tools/build-underground-fuel-tank-v1.mjs CAPACITY_MB. */
    public static final int CAPACITY_MB = 30_000;
    private static final String TANK_KEY = "Tank";

    private final FluidTank tank = new FluidTank(CAPACITY_MB, this::accepts) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private LazyOptional<IFluidHandler> capability = LazyOptional.of(() -> tank);

    public UndergroundFuelTankBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.UNDERGROUND_FUEL_TANK.get(), position, state);
    }

    private boolean accepts(FluidStack stack) {
        return getBlockState().getBlock() instanceof UndergroundFuelTankBlock block && stack.getFluid().isSame(block.fuel());
    }

    public FluidTank tank() {
        return tank;
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> type, @Nullable Direction side) {
        if (type == ForgeCapabilities.FLUID_HANDLER && (side == null || side == Direction.UP)) return capability.cast();
        return super.getCapability(type, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        capability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        capability = LazyOptional.of(() -> tank);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tank.readFromNBT(tag.getCompound(TANK_KEY));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TANK_KEY, tank.writeToNBT(new CompoundTag()));
    }
}
