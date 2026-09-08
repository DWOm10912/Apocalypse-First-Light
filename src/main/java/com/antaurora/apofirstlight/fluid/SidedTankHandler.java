package com.antaurora.apofirstlight.fluid;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;

public final class SidedTankHandler implements IFluidHandler {
        private final java.util.function.Supplier<net.minecraft.world.level.Level> level;
        private final FluidTank tank;
        private final boolean allowFill;
        private final boolean allowDrain;
        private final FluidPortTransferBudget budget;

        public SidedTankHandler(FluidTank tank, boolean allowFill, boolean allowDrain,
                                 FluidPortTransferBudget budget, java.util.function.Supplier<net.minecraft.world.level.Level> level) {
            this.level = level;
            this.tank = tank;
            this.allowFill = allowFill;
            this.allowDrain = allowDrain;
            this.budget = budget;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tankIndex) {
            return tank.getFluid().copy();
        }

        @Override
        public int getTankCapacity(int tankIndex) {
            return tank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tankIndex, @NotNull FluidStack stack) {
            return allowFill && tank.isFluidValid(tankIndex, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!allowFill || resource.isEmpty()) {
                return 0;
            }
            int limitedAmount = budget.limit(level.get(), resource.getAmount());
            if (limitedAmount <= 0) {
                return 0;
            }
            FluidStack limitedResource = resource.copy();
            limitedResource.setAmount(limitedAmount);
            int filled = tank.fill(limitedResource, action);
            if (action == FluidAction.EXECUTE && filled > 0) {
                budget.record(level.get(), filled);
            }
            return filled;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (!allowDrain || resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            int limitedAmount = budget.limit(level.get(), resource.getAmount());
            if (limitedAmount <= 0) {
                return FluidStack.EMPTY;
            }
            FluidStack limitedResource = resource.copy();
            limitedResource.setAmount(limitedAmount);
            FluidStack drained = tank.drain(limitedResource, action);
            if (action == FluidAction.EXECUTE && !drained.isEmpty()) {
                budget.record(level.get(), drained.getAmount());
            }
            return drained;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            if (!allowDrain) {
                return FluidStack.EMPTY;
            }
            int limitedAmount = budget.limit(level.get(), maxDrain);
            if (limitedAmount <= 0) {
                return FluidStack.EMPTY;
            }
            FluidStack drained = tank.drain(limitedAmount, action);
            if (action == FluidAction.EXECUTE && !drained.isEmpty()) {
                budget.record(level.get(), drained.getAmount());
            }
            return drained;
        }
    }

