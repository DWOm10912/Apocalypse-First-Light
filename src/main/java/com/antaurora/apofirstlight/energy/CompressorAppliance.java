package com.antaurora.apofirstlight.energy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.function.Supplier;

/**
 * The power side of a cold appliance (Beverage Cooler, Chest Freezer), owned by its master block entity: a tiny FE buffer
 * (about a second of use, so the appliance goes dark soon after the cable is cut) fed only through its power port, at most
 * {@code max_receive_fe_per_tick} a tick; the lights draw while lit and the compressor while it runs, in cycles (on / off
 * ticks) that restart when the power comes back. Lit is the host's state ({@link Host#lit()}): on while the buffer pays
 * for the lights, back on only once the buffer is full again, so a weak supply does not flicker. The compressor starts at
 * the beginning of an on phase and stops at its end or when the buffer cannot pay, so it starts at most once a cycle;
 * start / stop are played here, the running loop on clients (client/BlockLoopSoundController) from the synced
 * {@link #compressorRunning()}. Values: machine_balance (MachineBalanceManager.ApplianceBalance).
 *
 * <p>Lights only (the Vending Machine, the Water Dispenser): built without compressor sounds, it never runs the compressor and ignores the
 * balance's compressor fields; the buffer and the lights work the same.
 */
public final class CompressorAppliance {
    private static final String ENERGY_KEY = "EnergyStored";
    private static final String CYCLE_KEY = "CompressorCycle";
    private static final String COMPRESSOR_KEY = "CompressorRunning";

    /** The appliance's block entity side. */
    public interface Host {
        boolean lit();

        /** Server: lights on / off (the host stores and syncs it). */
        void setLit(boolean lit);

        /** Where the compressor sounds come from. */
        Vec3 compressorPosition();

        /** Server: send the block entity's update packet (the compressor state changed). */
        void syncAppliance();
    }

    private final BlockEntity owner;
    private final Host host;
    private final Supplier<MachineBalanceManager.ApplianceBalance> balance;
    private final Supplier<SoundEvent> startSound;
    private final Supplier<SoundEvent> stopSound;
    private final float pitch;
    private int energyStored;
    private long receiveBudgetTick = Long.MIN_VALUE;
    private int receivedThisTick;
    private int compressorCycle;
    private boolean compressorRunning;

    private final IEnergyStorage inputStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            MachineBalanceManager.ApplianceBalance values = balance.get();
            Level level = owner.getLevel();
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
                owner.setChanged();
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
            return balance.get().capacityFe();
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

    /** pitch: the start / stop sounds' base pitch (randomised by +-0.02); the loop's is set in BlockLoopSoundController. */
    public <T extends BlockEntity & Host> CompressorAppliance(T owner, Supplier<MachineBalanceManager.ApplianceBalance> balance,
                                                             Supplier<SoundEvent> startSound, Supplier<SoundEvent> stopSound,
                                                             float pitch) {
        this.owner = owner;
        this.host = owner;
        this.balance = balance;
        this.startSound = startSound;
        this.stopSound = stopSound;
        this.pitch = pitch;
    }

    /** Lights only, no compressor. */
    public <T extends BlockEntity & Host> CompressorAppliance(T owner, Supplier<MachineBalanceManager.ApplianceBalance> balance) {
        this(owner, balance, null, null, 1.0F);
    }

    /** Server, master only, every tick. */
    public void serverTick() {
        Level level = owner.getLevel();
        if (level == null) return;
        MachineBalanceManager.ApplianceBalance values = balance.get();
        if (energyStored > values.capacityFe()) energyStored = values.capacityFe();   // after a balance reload
        boolean lit = host.lit();
        int before = energyStored;
        if (lit && energyStored < values.lightFePerTick()) {
            lit = false;
            host.setLit(false);
        } else if (!lit && energyStored >= values.capacityFe()) {
            lit = true;
            compressorCycle = 0;
            host.setLit(true);
        }
        if (lit) {
            energyStored -= values.lightFePerTick();
            if (startSound == null) {
                if (energyStored != before) owner.setChanged();
                return;
            }
            int period = values.compressorOnTicks() + values.compressorOffTicks();
            int phase = Math.floorMod(compressorCycle, period);
            if (!compressorRunning && phase == 0 && energyStored >= values.compressorFePerTick()) setCompressor(level, true);
            else if (compressorRunning && (phase >= values.compressorOnTicks() || energyStored < values.compressorFePerTick()))
                setCompressor(level, false);
            if (compressorRunning) energyStored -= values.compressorFePerTick();
            compressorCycle = (phase + 1) % period;
        } else if (compressorRunning) {
            setCompressor(level, false);
        }
        if (energyStored != before) owner.setChanged();
    }

    private void setCompressor(Level level, boolean running) {
        compressorRunning = running;
        Vec3 at = host.compressorPosition();
        SoundEvent sound = running ? startSound.get() : stopSound.get();
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, 1.0F, pitch - 0.02F + level.random.nextFloat() * 0.04F);
        host.syncAppliance();
    }

    /** Synced: the compressor is running (clients play its loop). */
    public boolean compressorRunning() {
        return compressorRunning;
    }

    public LazyOptional<IEnergyStorage> capability() {
        return inputCapability;
    }

    public void invalidateCaps() {
        inputCapability.invalidate();
    }

    public void reviveCaps() {
        inputCapability = LazyOptional.of(() -> inputStorage);
    }

    public void load(CompoundTag tag) {
        energyStored = Math.max(0, Math.min(tag.getInt(ENERGY_KEY), balance.get().capacityFe()));
        compressorCycle = Math.max(0, tag.getInt(CYCLE_KEY));
        compressorRunning = tag.getBoolean(COMPRESSOR_KEY);
    }

    public void save(CompoundTag tag) {
        tag.putInt(ENERGY_KEY, energyStored);
        tag.putInt(CYCLE_KEY, compressorCycle);
        tag.putBoolean(COMPRESSOR_KEY, compressorRunning);
    }
}
