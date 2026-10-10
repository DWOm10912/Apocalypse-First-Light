package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.FuelCanBlock;
import com.antaurora.apofirstlight.fluid.FuelFill;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
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
 * A fuel container's fuel (block/FuelCanBlock, docs/models/fuel_containers_v1.md): one tank of the size's capacity
 * (1 mB = 1 L), gasoline or diesel only and one of them at a time. Reached from any side by the dispenser's nozzle, a
 * pouring can and the hand pump (no pipe port: AFL pipes only join AflFluidPortBlock faces). Saved under {@link #FLUID_KEY}
 * only while it holds something, so the loot table's copy leaves an empty container's item without NBT.
 */
public class FuelCanBlockEntity extends BlockEntity {
    public static final String FLUID_KEY = "Fluid";

    private final FluidTank tank;
    private LazyOptional<IFluidHandler> capability;
    private @Nullable CompoundTag fill;   // FuelFill marker until it is rolled

    public FuelCanBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.FUEL_CAN.get(), pos, state);
        int capacity = state.getBlock() instanceof FuelCanBlock block ? block.size().capacity : 20;
        tank = new FluidTank(capacity, FuelCanBlockEntity::isFuel) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
        capability = LazyOptional.of(() -> tank);
    }

    public static boolean isFuel(FluidStack stack) {
        return stack.getFluid().isSame(AflFluids.GASOLINE.get()) || stack.getFluid().isSame(AflFluids.DIESEL.get());
    }

    public FluidTank tank() {
        return tank;
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> type, @Nullable Direction side) {
        if (type == ForgeCapabilities.FLUID_HANDLER) return capability.cast();
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
        tank.setFluid(FluidStack.EMPTY);
        if (tag.contains(FLUID_KEY, Tag.TAG_COMPOUND)) {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag.getCompound(FLUID_KEY));
            if (isFuel(fluid)) {
                fluid.setAmount(Math.min(fluid.getAmount(), tank.getCapacity()));
                tank.setFluid(fluid);
            }
        }
        fill = tag.contains(FuelFill.KEY, Tag.TAG_COMPOUND) ? tag.getCompound(FuelFill.KEY) : null;
        rollFill();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        rollFill();
    }

    /** An exported building's container (fluid/FuelFill): the fuel it held then, else the rule's pick; rolled once in a server level. */
    private void rollFill() {
        if (fill != null && level instanceof ServerLevel server && FuelFill.fill(server, worldPosition, fill, tank, null)) {
            fill = null;
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!tank.isEmpty()) tag.put(FLUID_KEY, tank.getFluid().writeToNBT(new CompoundTag()));
        if (fill != null) tag.put(FuelFill.KEY, fill);
    }
}
