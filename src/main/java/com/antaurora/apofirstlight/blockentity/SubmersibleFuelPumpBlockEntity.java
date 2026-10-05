package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.SubmersibleFuelPumpBlock;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.fluid.FluidHeat;
import com.antaurora.apofirstlight.fluid.FluidPipeTransfer;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Submersible Fuel Pump V1 (docs/models/fuel_station_sump_v1.md). While its FE buffer pays for the tick
 * ({@code work_fe_per_tick}), it draws {@code pump_mb_per_tick} from the tank under it (the fluid handler of the block
 * entity below, asked for its top face: the Underground Fuel Tank's port) into a small {@link #BUFFER_MB} buffer, and
 * pushes the buffer out through the outlet port (FACING) into a Fluid Pipe V2 network (FluidPipeTransfer, 25 mB a tick
 * at most). Values: the intake pump's (machine_balance/intake_pump.json, MachineBalanceManager.intakePump()). A hot
 * liquid (FluidHeat) melts it, as every ordinary pump. The buffer never takes fluid in from a pipe. Jade shows
 * {@link #status()}.
 */
public class SubmersibleFuelPumpBlockEntity extends BlockEntity {
    public static final int BUFFER_MB = 250;
    private static final String ENERGY_KEY = "EnergyStored";
    private static final String BUFFER_KEY = "Buffer";

    /** Why the pump is or is not pumping (Jade). */
    public enum Status {
        NO_POWER, NO_TANK, EMPTY, BLOCKED, RUNNING;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private int energyStored;
    private long receiveBudgetTick = Long.MIN_VALUE;
    private int receivedThisTick;
    private Status status = Status.NO_POWER;
    /** What the tank under it holds, as last seen (Jade), or empty. */
    private FluidStack tankFluid = FluidStack.EMPTY;

    private final FluidTank buffer = new FluidTank(BUFFER_MB) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    private final IEnergyStorage inputStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            MachineBalanceManager.IntakePumpBalance values = MachineBalanceManager.intakePump();
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
            return MachineBalanceManager.intakePump().capacityFe();
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

    /** The outlet as pipes see it: drain only; it accepts nothing, so it is never a pipe's sink. */
    private final IFluidHandler output = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return buffer.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return buffer.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return buffer.drain(resource, action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return buffer.drain(maxDrain, action);
        }
    };

    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> inputStorage);
    private LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> output);

    public SubmersibleFuelPumpBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.SUBMERSIBLE_FUEL_PUMP.get(), position, state);
    }

    public FluidTank buffer() {
        return buffer;
    }

    public int energyStored() {
        return energyStored;
    }

    public Status status() {
        return status;
    }

    public FluidStack tankFluid() {
        return tankFluid;
    }

    private Direction facing() {
        return getBlockState().getValue(SubmersibleFuelPumpBlock.FACING);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server) || !(getBlockState().getBlock() instanceof SubmersibleFuelPumpBlock)) return;
        MachineBalanceManager.IntakePumpBalance values = MachineBalanceManager.intakePump();
        if (energyStored > values.capacityFe()) energyStored = values.capacityFe();   // after a balance reload

        if (!buffer.isEmpty()) FluidPipeTransfer.transferFrom(server, this, facing(), stack -> buffer.fill(stack, IFluidHandler.FluidAction.EXECUTE));

        BlockEntity below = server.getBlockEntity(worldPosition.below());
        IFluidHandler tank = below == null ? null : below.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).resolve().orElse(null);
        FluidStack seen = tank == null ? FluidStack.EMPTY : tank.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (!seen.isFluidEqual(tankFluid)) tankFluid = seen.isEmpty() ? FluidStack.EMPTY : new FluidStack(seen, 1);
        status = pump(server, values, tank, seen);
    }

    private Status pump(ServerLevel server, MachineBalanceManager.IntakePumpBalance values, @Nullable IFluidHandler tank, FluidStack seen) {
        if (energyStored < values.workFePerTick()) return Status.NO_POWER;
        if (tank == null) return Status.NO_TANK;
        if (seen.isEmpty()) return Status.EMPTY;
        if (FluidHeat.isHot(seen.getFluid())) {   // an ordinary pump: a hot liquid melts it
            FluidHeat.melt(server, worldPosition);
            return Status.BLOCKED;
        }
        FluidStack want = new FluidStack(seen, Math.min(values.pumpMbPerTick(), buffer.getSpace()));
        if (want.isEmpty() || buffer.fill(want, IFluidHandler.FluidAction.SIMULATE) <= 0) return Status.BLOCKED;
        FluidStack drawn = tank.drain(want, IFluidHandler.FluidAction.EXECUTE);
        if (drawn.isEmpty()) return Status.EMPTY;
        buffer.fill(drawn, IFluidHandler.FluidAction.EXECUTE);
        energyStored -= values.workFePerTick();
        setChanged();
        return Status.RUNNING;
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        BlockState state = getBlockState();
        if (side != null && state.getBlock() instanceof SubmersibleFuelPumpBlock block) {
            if (capability == ForgeCapabilities.ENERGY && block.hasPowerPort(state, side)) return energyCapability.cast();
            if (capability == ForgeCapabilities.FLUID_HANDLER && block.hasFluidPort(state, side)) return fluidCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCapability.invalidate();
        fluidCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        energyCapability = LazyOptional.of(() -> inputStorage);
        fluidCapability = LazyOptional.of(() -> output);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energyStored = Math.max(0, tag.getInt(ENERGY_KEY));
        buffer.readFromNBT(tag.getCompound(BUFFER_KEY));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(ENERGY_KEY, energyStored);
        tag.put(BUFFER_KEY, buffer.writeToNBT(new CompoundTag()));
    }
}
