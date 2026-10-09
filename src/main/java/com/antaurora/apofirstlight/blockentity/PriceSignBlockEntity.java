package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.PriceSignBlock;
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
 * The PRAIRIE price sign's power (docs/models/fuel_stop_a1_details_v1.md): a small FE buffer fed through the port in the
 * master cell's underside, at most {@code max_receive_fe_per_tick} a tick. Every {@link #PERIOD} ticks it pays for the period:
 * the LED prices {@code light_fe_per_tick}, and while it is dark ({@code !level.isDay()}) the light box twice that. Powered,
 * the prices show (always the same: nobody has changed them since); the light box only by night. Off, it comes back on
 * only once the buffer holds two periods' worth (at most its capacity), so a weak supply does not flicker. Values:
 * machine_balance/price_sign.json (MachineBalanceManager.priceSign()).
 */
public class PriceSignBlockEntity extends BlockEntity {
    public static final int PERIOD = 20, BOX_FACTOR = 2;
    private static final String ENERGY_KEY = "EnergyStored", POWERED_KEY = "Powered";

    private int energyStored;
    private boolean powered;
    private long receiveBudgetTick = Long.MIN_VALUE;
    private int receivedThisTick;

    private final IEnergyStorage inputStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            MachineBalanceManager.ApplianceBalance values = MachineBalanceManager.priceSign();
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

        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energyStored; }
        @Override public int getMaxEnergyStored() { return MachineBalanceManager.priceSign().capacityFe(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };
    private LazyOptional<IEnergyStorage> inputCapability = LazyOptional.of(() -> inputStorage);

    public PriceSignBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.PRICE_SIGN.get(), position, state);
    }

    public int energyStored() {
        return energyStored;
    }

    /** Server (the block's ticker, master only). */
    public void serverTick() {
        if (level == null || level.isClientSide || level.getGameTime() % PERIOD != 0) return;
        if (!(getBlockState().getBlock() instanceof PriceSignBlock sign)) return;
        MachineBalanceManager.ApplianceBalance values = MachineBalanceManager.priceSign();
        if (energyStored > values.capacityFe()) energyStored = values.capacityFe();   // after a balance reload
        boolean dark = !level.isDay();
        long demand = (long) values.lightFePerTick() * PERIOD * (dark ? 1 + BOX_FACTOR : 1);
        boolean on = demand > 0 && energyStored >= (powered ? demand : Math.max(demand, Math.min(2 * demand, values.capacityFe())));
        if (on) energyStored -= (int) demand;
        if (on || powered != on) {
            powered = on;
            setChanged();
        }
        sign.setLook(level, worldPosition, on, on && dark);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY && side == Direction.DOWN) return inputCapability.cast();
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
