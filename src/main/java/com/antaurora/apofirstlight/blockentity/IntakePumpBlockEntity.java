package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.IntakePumpBlock;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.fluid.FluidHeat;
import com.antaurora.apofirstlight.fluid.FluidPipeTransfer;
import com.antaurora.apofirstlight.fluid.PumpSourceRules;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Intake Pump V1 (docs/models/intake_pump_v1.md), on the bank cell. While its isolator is on and its FE buffer pays for
 * the tick ({@code work_fe_per_tick}), it draws {@code pump_mb_per_tick} of the source liquid in the block under the front
 * cell (only there; only a source block) into a small
 * {@link #BUFFER_MB} buffer, and pushes the buffer out through the fluid port on the bank cell's top face into a Fluid
 * Pipe V2 network (FluidPipeTransfer, 25 mB a tick at most). Water (and lava, for the later high-temperature pump) comes
 * only from a pool of at least 3 x 3 x 1 sources and is never used up; any other liquid uses its source block up after
 * {@link PumpSourceRules#SOURCE_MB} (a liquid block becomes air, a waterlogged block gives its fluid up). See
 * fluid/PumpSourceRules (user, 2026-10-05). The buffer never takes fluid in from a pipe.
 * Values: machine_balance/intake_pump.json (MachineBalanceManager.intakePump()). The status lamp (IntakePumpBlock.LAMP) is
 * set every {@link #LAMP_PERIOD} ticks: green if it pumped in that period, amber if it is on and powered but cannot pump,
 * dark when off or without power. Jade shows {@link #status()}.
 */
public class IntakePumpBlockEntity extends BlockEntity {
    public static final int BUFFER_MB = 250;
    public static final int LAMP_PERIOD = 10;
    private static final String ENERGY_KEY = "EnergyStored";
    private static final String BUFFER_KEY = "Buffer";
    private static final String DRAWN_KEY = "Drawn";
    private static final String DRAWN_FLUID_KEY = "DrawnFluid";

    /** Why the pump is or is not pumping (Jade). */
    public enum Status {
        OFF, NO_POWER, NO_LIQUID, TOO_HOT, POOL_TOO_SMALL, BLOCKED, RUNNING;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private int energyStored;
    private long receiveBudgetTick = Long.MIN_VALUE;
    private int receivedThisTick;
    /** mB of a finite liquid drawn since its last source block was used up, and which fluid that was. */
    private int drawn;
    @Nullable
    private Fluid drawnFluid;
    private boolean pumpedThisPeriod;
    private Status status = Status.OFF;
    /** The liquid under the front cell as last seen (Jade), or empty. */
    private FluidStack sourceFluid = FluidStack.EMPTY;

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

    /** The discharge as pipes see it: drain only; it accepts nothing, so it is never a pipe's sink. */
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

    public IntakePumpBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.INTAKE_PUMP.get(), position, state);
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

    public FluidStack sourceFluid() {
        return sourceFluid;
    }

    private Direction facing() {
        return getBlockState().getValue(IntakePumpBlock.FACING);
    }

    /** Server, bank cell only (the block's ticker). */
    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof IntakePumpBlock) || !IntakePumpBlock.isBank(state)) return;
        MachineBalanceManager.IntakePumpBalance values = MachineBalanceManager.intakePump();
        if (energyStored > values.capacityFe()) energyStored = values.capacityFe();   // after a balance reload

        // push what is in the buffer out through the discharge port (whatever the switch says: it is only draining)
        if (!buffer.isEmpty()) FluidPipeTransfer.transferFrom(server, this, Direction.UP, stack -> buffer.fill(stack, IFluidHandler.FluidAction.EXECUTE));

        BlockPos source = IntakePumpBlock.sourcePosition(worldPosition, facing());
        FluidState liquid = server.hasChunkAt(source) ? server.getFluidState(source) : Fluids.EMPTY.defaultFluidState();
        Fluid fluid = liquid.isSource() ? liquid.getType() : Fluids.EMPTY;
        FluidStack seen = fluid == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(fluid, 1);
        if (!seen.isFluidEqual(sourceFluid)) sourceFluid = seen;
        // an ordinary pump that starts on a hot liquid (lava) melts (FluidHeat); the heat-resistant pump draws it
        if (!((IntakePumpBlock) state.getBlock()).heatResistant() && FluidHeat.isHot(fluid) && state.getValue(IntakePumpBlock.ON)
                && energyStored >= values.workFePerTick()) {
            FluidHeat.melt(server, worldPosition);
            return;
        }

        status = pump(server, state, values, source, fluid);
        if (status == Status.RUNNING) pumpedThisPeriod = true;
        if (server.getGameTime() % LAMP_PERIOD == 0) {
            IntakePumpBlock.Lamp lamp = !state.getValue(IntakePumpBlock.ON) || status == Status.OFF || status == Status.NO_POWER && !pumpedThisPeriod
                    ? IntakePumpBlock.Lamp.OFF : pumpedThisPeriod ? IntakePumpBlock.Lamp.RUN : IntakePumpBlock.Lamp.IDLE;
            pumpedThisPeriod = false;
            IntakePumpBlock.Lamp was = state.getValue(IntakePumpBlock.LAMP);
            if ((lamp == IntakePumpBlock.Lamp.RUN) != (was == IntakePumpBlock.Lamp.RUN)) playMotor(server, worldPosition, facing(), lamp == IntakePumpBlock.Lamp.RUN);
            if (was != lamp) server.setBlock(worldPosition, state.setValue(IntakePumpBlock.LAMP, lamp), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /** Model px of the motor (the start / stop sounds and the client's loop come from there). */
    public static final double[] MOTOR = {0.0, -1.6, 4.5};
    /** The client's loop waits this many ticks after the lamp turns green, then fades in over the second number (the start sound's tail fades out over it; tools/build-intake-pump-sounds-v1.mjs). */
    public static final int[] LOOP_FADE_IN = {26, 12};

    /** Server: the motor starting (contactor and wind-up) or stopping (winding down). */
    public static void playMotor(net.minecraft.world.level.Level level, BlockPos bank, Direction facing, boolean start) {
        net.minecraft.world.phys.Vec3 at = IntakePumpBlock.world(bank, facing, MOTOR[0], MOTOR[1], MOTOR[2]);
        level.playSound(null, at.x, at.y, at.z, (start ? com.antaurora.apofirstlight.registry.AflSounds.INTAKE_PUMP_START
                : com.antaurora.apofirstlight.registry.AflSounds.INTAKE_PUMP_STOP).get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private Status pump(ServerLevel server, BlockState state, MachineBalanceManager.IntakePumpBalance values, BlockPos source,
                        Fluid fluid) {
        if (!state.getValue(IntakePumpBlock.ON)) return !((IntakePumpBlock) state.getBlock()).heatResistant() && FluidHeat.isHot(fluid) ? Status.TOO_HOT : Status.OFF;
        if (energyStored < values.workFePerTick()) return Status.NO_POWER;
        if (fluid == Fluids.EMPTY) return Status.NO_LIQUID;
        boolean pool = PumpSourceRules.isPoolFluid(fluid);
        if (pool && !PumpSourceRules.inPool(server, source, fluid)) return Status.POOL_TOO_SMALL;
        int amount = values.pumpMbPerTick();
        FluidStack intake = new FluidStack(fluid, amount);
        if (buffer.fill(intake, IFluidHandler.FluidAction.SIMULATE) < amount) return Status.BLOCKED;
        buffer.fill(intake, IFluidHandler.FluidAction.EXECUTE);
        energyStored -= values.workFePerTick();
        if (!pool) {   // a finite liquid: every SOURCE_MB uses a source block up
            if (drawnFluid != fluid) {
                drawnFluid = fluid;
                drawn = 0;
            }
            drawn += amount;
            if (drawn >= PumpSourceRules.SOURCE_MB) {
                drawn -= PumpSourceRules.SOURCE_MB;
                useUp(server, source);
            }
        }
        setChanged();
        return Status.RUNNING;
    }

    private static void useUp(ServerLevel level, BlockPos source) {
        BlockState state = level.getBlockState(source);
        if (state.getBlock() instanceof LiquidBlock) level.setBlock(source, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        else if (state.getBlock() instanceof BucketPickup pickup) pickup.pickupBlock(level, source, state);   // a waterlogged block
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        BlockState state = getBlockState();
        if (side != null && state.getBlock() instanceof IntakePumpBlock block && IntakePumpBlock.isBank(state)) {
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
        drawn = Math.max(0, tag.getInt(DRAWN_KEY));
        drawnFluid = tag.contains(DRAWN_FLUID_KEY) ? ForgeRegistries.FLUIDS.getValue(new net.minecraft.resources.ResourceLocation(tag.getString(DRAWN_FLUID_KEY))) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(ENERGY_KEY, energyStored);
        tag.put(BUFFER_KEY, buffer.writeToNBT(new CompoundTag()));
        tag.putInt(DRAWN_KEY, drawn);
        if (drawnFluid != null) {
            net.minecraft.resources.ResourceLocation id = ForgeRegistries.FLUIDS.getKey(drawnFluid);
            if (id != null) tag.putString(DRAWN_FLUID_KEY, id.toString());
        }
    }
}
