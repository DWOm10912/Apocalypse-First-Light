package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.PowerCableBlock;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EnergyCellBlockEntity extends BlockEntity {
    private int energyStored;
    private int balanceRevision = -1;
    private long transferBudgetTick = Long.MIN_VALUE;
    private int receivedThisTick;
    private int extractedThisTick;

    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            applyCurrentBalance();
            resetTransferBudget();
            MachineBalanceManager.EnergyCellBalance balance = MachineBalanceManager.energyCell();
            int tickBudget = Math.max(0, balance.maxReceiveFePerTick() - receivedThisTick);
            int accepted = Math.min(Math.max(0, maxReceive),
                    Math.min(tickBudget, balance.capacityFe() - energyStored));
            if (!simulate && accepted > 0) {
                energyStored += accepted;
                receivedThisTick += accepted;
                setChanged();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            applyCurrentBalance();
            resetTransferBudget();
            MachineBalanceManager.EnergyCellBalance balance = MachineBalanceManager.energyCell();
            int tickBudget = Math.max(0, balance.maxExtractFePerTick() - extractedThisTick);
            int extracted = Math.min(Math.max(0, maxExtract), Math.min(tickBudget, energyStored));
            if (!simulate && extracted > 0) {
                energyStored -= extracted;
                extractedThisTick += extracted;
                setChanged();
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            applyCurrentBalance();
            return energyStored;
        }

        @Override
        public int getMaxEnergyStored() {
            return MachineBalanceManager.energyCell().capacityFe();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> energyStorage);

    public EnergyCellBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.ENERGY_CELL.get(), position, state);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, EnergyCellBlockEntity cell) {
        cell.applyCurrentBalance();
    }

    private void applyCurrentBalance() {
        int currentRevision = MachineBalanceManager.revision();
        if (balanceRevision == currentRevision) {
            return;
        }
        balanceRevision = currentRevision;
        int capacity = MachineBalanceManager.energyCell().capacityFe();
        if (energyStored > capacity) {
            ApocalypseFirstLight.LOGGER.warn(
                    "[AFL ELECTRICITY] Energy Cell at {} stored {} FE but reloaded capacity is {}; clamping",
                    worldPosition, energyStored, capacity);
            energyStored = capacity;
            setChanged();
        }
    }

    private void resetTransferBudget() {
        long gameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (transferBudgetTick != gameTime) {
            transferBudgetTick = gameTime;
            receivedThisTick = 0;
            extractedThisTick = 0;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("EnergyStored", energyStored);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energyStored = Math.max(0, Math.min(tag.getInt("EnergyStored"),
                MachineBalanceManager.energyCell().capacityFe()));
        balanceRevision = -1;
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability,
                                                       @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY && side != null
                && PowerCableBlock.isUtilityPortFace(getBlockState(), side)) {
            return energyCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        energyCapability = LazyOptional.of(() -> energyStorage);
    }
}
