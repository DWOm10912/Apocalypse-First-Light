package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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

public final class FluidTankBlockEntity extends BlockEntity {
    public static final int CAPACITY_MB = 20_000;
    private static final String FLUID_KEY = "Fluid";

    private final FluidTank tank = new FluidTank(CAPACITY_MB) {
        @Override
        protected void onContentsChanged() {
            FluidTankBlockEntity.this.onFluidChanged();
        }
    };
    private final IFluidHandler topFillHandler = new SidedFluidHandler(tank, true, false);
    private final IFluidHandler bottomDrainHandler = new SidedFluidHandler(tank, false, true);
    private LazyOptional<IFluidHandler> topCapability = LazyOptional.of(() -> topFillHandler);
    private LazyOptional<IFluidHandler> bottomCapability = LazyOptional.of(() -> bottomDrainHandler);

    public FluidTankBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.FLUID_TANK.get(), position, state);
    }

    public FluidStack getFluid() {
        FluidStack fluid = tank.getFluid();
        return fluid.isEmpty() ? FluidStack.EMPTY : fluid.copy();
    }

    public int getFluidAmount() {
        return tank.getFluidAmount();
    }

    public int getCapacity() {
        return CAPACITY_MB;
    }

    private void onFluidChanged() {
        setChanged();
        Level currentLevel = getLevel();
        if (currentLevel != null && !currentLevel.isClientSide()) {
            BlockState state = getBlockState();
            currentLevel.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(FLUID_KEY, tank.writeToNBT(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(FLUID_KEY, Tag.TAG_COMPOUND)) {
            tank.readFromNBT(tag.getCompound(FLUID_KEY));
            if (tank.getFluidAmount() > CAPACITY_MB) {
                tank.getFluid().setAmount(CAPACITY_MB);
            }
        } else {
            tank.setFluid(FluidStack.EMPTY);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability,
                                                       @Nullable Direction side) {
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            if (side == Direction.UP) {
                return topCapability.cast();
            }
            if (side == Direction.DOWN) {
                return bottomCapability.cast();
            }
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        topCapability.invalidate();
        bottomCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        topCapability = LazyOptional.of(() -> topFillHandler);
        bottomCapability = LazyOptional.of(() -> bottomDrainHandler);
    }

    private static final class SidedFluidHandler implements IFluidHandler {
        private final FluidTank tank;
        private final boolean allowFill;
        private final boolean allowDrain;

        private SidedFluidHandler(FluidTank tank, boolean allowFill, boolean allowDrain) {
            this.tank = tank;
            this.allowFill = allowFill;
            this.allowDrain = allowDrain;
        }

        @Override
        public int getTanks() {
            return tank.getTanks();
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tankIndex) {
            FluidStack fluid = tank.getFluidInTank(tankIndex);
            return fluid.isEmpty() ? FluidStack.EMPTY : fluid.copy();
        }

        @Override
        public int getTankCapacity(int tankIndex) {
            return tank.getTankCapacity(tankIndex);
        }

        @Override
        public boolean isFluidValid(int tankIndex, @NotNull FluidStack stack) {
            return allowFill && tank.isFluidValid(tankIndex, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return allowFill ? tank.fill(resource, action) : 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return allowDrain ? tank.drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return allowDrain ? tank.drain(maxDrain, action) : FluidStack.EMPTY;
        }
    }
}
