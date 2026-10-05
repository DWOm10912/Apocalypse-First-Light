package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.FuelDispenserBlock;
import com.antaurora.apofirstlight.block.FuelDispenserSumpBlock;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fuel Dispenser Sump V1, on the two lower (port) cells: holds nothing itself. Its gasoline / diesel ports hand a pipe the
 * dispenser's line of that grade (FuelDispenserBlockEntity#fuelInput: fill only), its power port the dispenser's power
 * input; without a dispenser above, the ports give nothing.
 */
public class FuelDispenserSumpBlockEntity extends BlockEntity {
    public FuelDispenserSumpBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.FUEL_DISPENSER_SUMP.get(), position, state);
    }

    @Nullable
    private FuelDispenserBlockEntity dispenser() {
        if (level == null || !(getBlockState().getBlock() instanceof FuelDispenserSumpBlock)) return null;
        BlockPos root = FuelDispenserSumpBlock.dispenserAbove(level, worldPosition, getBlockState());
        return root != null && level.getBlockEntity(root) instanceof FuelDispenserBlockEntity dispenser ? dispenser : null;
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        BlockState state = getBlockState();
        if (side != null && state.getBlock() instanceof FuelDispenserSumpBlock block) {
            if (capability == ForgeCapabilities.FLUID_HANDLER) {
                FuelDispenserBlock.Grade grade = FuelDispenserSumpBlock.grade(state, side);
                FuelDispenserBlockEntity dispenser = grade == null ? null : dispenser();
                return dispenser == null ? LazyOptional.empty() : dispenser.fuelInput(grade).cast();
            }
            if (capability == ForgeCapabilities.ENERGY && block.hasPowerPort(state, side)) {
                FuelDispenserBlockEntity dispenser = dispenser();
                return dispenser == null ? LazyOptional.empty() : dispenser.energyInput().cast();
            }
        }
        return super.getCapability(capability, side);
    }
}
