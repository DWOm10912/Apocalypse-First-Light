package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.FuelCanopyColumnBlock;
import com.antaurora.apofirstlight.block.FuelCanopyNetwork;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fuel Canopy Kit V1 column (every segment has one; only the base's is used): a small FE buffer fed through the port on
 * the base's bottom face, at most {@code max_receive_fe_per_tick} a tick. Every {@link #PERIOD} ticks each base scans its
 * network (FuelCanopyNetwork); the controller (the lowest base) pays {@code light_fe_per_tick} per lamp for the period
 * from all the network's bases and switches every lamp on or off. Off, it comes back on only once the bases hold two
 * periods' worth (at most their whole capacity), so a weak supply does not flicker. Values:
 * machine_balance/fuel_canopy.json (MachineBalanceManager.fuelCanopy(), lights only).
 */
public class FuelCanopyColumnBlockEntity extends BlockEntity {
    public static final int PERIOD = 20;
    private static final String ENERGY_KEY = "EnergyStored";
    private static final String POWERED_KEY = "Powered";

    private int energyStored;
    /** This base ran its network last period with the lamps on. */
    private boolean powered;
    private long receiveBudgetTick = Long.MIN_VALUE;
    private int receivedThisTick;

    private final IEnergyStorage inputStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            MachineBalanceManager.ApplianceBalance values = MachineBalanceManager.fuelCanopy();
            long tick = level == null ? 0 : level.getGameTime();
            if (receiveBudgetTick != tick) {
                receiveBudgetTick = tick;
                receivedThisTick = 0;
            }
            int accepted = Math.min(Math.max(0, maxReceive), Math.min(
                    Math.max(0, values.maxReceiveFePerTick() - receivedThisTick),
                    Math.max(0, values.capacityFe() - energyStored)));
            if (!simulate && accepted > 0) {
                energyStored += accepted;
                receivedThisTick += accepted;
                setChanged();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energyStored;
        }

        @Override
        public int getMaxEnergyStored() {
            return MachineBalanceManager.fuelCanopy().capacityFe();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };
    private LazyOptional<IEnergyStorage> inputCapability = LazyOptional.of(() -> inputStorage);

    public FuelCanopyColumnBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.FUEL_CANOPY_COLUMN.get(), position, state);
    }

    private boolean isBase() {
        return getBlockState().getBlock() instanceof FuelCanopyColumnBlock
                && getBlockState().getValue(FuelCanopyColumnBlock.SEGMENT) == FuelCanopyColumnBlock.Segment.BASE;
    }

    /** Server, base only (the block's ticker). */
    public void serverTick() {
        if (level == null || level.isClientSide || level.getGameTime() % PERIOD != 0) return;
        MachineBalanceManager.ApplianceBalance values = MachineBalanceManager.fuelCanopy();
        if (energyStored > values.capacityFe()) energyStored = values.capacityFe();   // after a balance reload
        FuelCanopyNetwork.Network network = FuelCanopyNetwork.scan(level, worldPosition);
        if (!worldPosition.equals(network.controller())) {
            if (powered) {
                powered = false;
                setChanged();
            }
            return;
        }
        long demand = (long) network.lights().size() * values.lightFePerTick() * PERIOD, stored = 0, capacity = 0;
        for (BlockPos pos : network.bases()) {
            if (level.getBlockEntity(pos) instanceof FuelCanopyColumnBlockEntity base) {
                stored += base.energyStored;
                capacity += values.capacityFe();
            }
        }
        boolean on = !network.lights().isEmpty() && stored >= (powered ? demand : Math.max(demand, Math.min(2 * demand, capacity)));
        if (on) {
            long left = demand;
            for (BlockPos pos : network.bases()) {
                if (left <= 0) break;
                if (level.getBlockEntity(pos) instanceof FuelCanopyColumnBlockEntity base && base.energyStored > 0) {
                    int take = (int) Math.min(left, base.energyStored);
                    base.energyStored -= take;
                    base.setChanged();
                    left -= take;
                }
            }
        }
        if (powered != on) {
            powered = on;
            setChanged();
        }
        FuelCanopyNetwork.setLights(level, network.lights(), on);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY && side != null && isBase()
                && ((FuelCanopyColumnBlock) getBlockState().getBlock()).hasPowerPort(getBlockState(), side)) return inputCapability.cast();
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inputCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        inputCapability = LazyOptional.of(() -> inputStorage);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energyStored = Math.max(0, tag.getInt(ENERGY_KEY));
        powered = tag.getBoolean(POWERED_KEY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(ENERGY_KEY, energyStored);
        tag.putBoolean(POWERED_KEY, powered);
    }
}
