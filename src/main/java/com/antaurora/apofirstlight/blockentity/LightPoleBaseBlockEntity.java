package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.AreaLightBlock;
import com.antaurora.apofirstlight.block.LampGlowBlock;
import com.antaurora.apofirstlight.block.LightPoleBlock;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Site Lighting V1 pole base (docs/models/site_lighting_v1.md): a small FE buffer fed through the port in the base's bottom
 * face, at most {@code max_receive_fe_per_tick} a tick. Every {@link #PERIOD} ticks it finds the pole's head (the
 * AreaLightBlock on top of the segments) and lights it while it is dark ({@code !level.isDay()}: the head's photocontrol)
 * and the buffer pays {@code light_fe_per_tick} for each head for the period; off, it comes back on only once the buffer
 * holds two periods' worth (at most its capacity), so a weak supply does not flicker. Values: machine_balance/site_light.json
 * (MachineBalanceManager.siteLight()).
 * <p>
 * Lit, the base hangs the hidden light points (LampGlowBlock, light 14) {@link #GLOW_HEIGHT} blocks above itself on a
 * 3 x 3 grid {@link #GLOW_STEP} blocks apart: vanilla block light falls by one a block, and the head 8 m up alone leaves the
 * ground nearly dark. A point goes only into air open to the sky (not into the store, under a canopy or into the pole: the
 * centre point moves one cell out the first head's way); the base remembers the points it put and takes them away when it
 * goes dark or is removed.
 */
public class LightPoleBaseBlockEntity extends BlockEntity {
    public static final int PERIOD = 20, GLOW_HEIGHT = 4, GLOW_STEP = 6, MAX_SEGMENTS = 24;
    private static final String ENERGY_KEY = "EnergyStored", POWERED_KEY = "Powered", GLOWS_KEY = "Glows";

    private int energyStored;
    private boolean powered;
    /** The light points this base put (absolute positions). */
    private final Set<BlockPos> glows = new HashSet<>();
    private long receiveBudgetTick = Long.MIN_VALUE;
    private int receivedThisTick;

    private final IEnergyStorage inputStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            MachineBalanceManager.ApplianceBalance values = MachineBalanceManager.siteLight();
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
            return MachineBalanceManager.siteLight().capacityFe();
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

    public LightPoleBaseBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.LIGHT_POLE_BASE.get(), position, state);
    }

    public int energyStored() {
        return energyStored;
    }

    public boolean powered() {
        return powered;
    }

    /** The head cell on top of this base's segments, or null (no head yet). */
    @Nullable
    public BlockPos head() {
        if (level == null) return null;
        BlockPos.MutableBlockPos at = worldPosition.mutable();
        for (int i = 0; i <= MAX_SEGMENTS; i++) {
            BlockState state = level.getBlockState(at.move(Direction.UP));
            if (state.getBlock() instanceof AreaLightBlock) return at.immutable();
            if (!(state.getBlock() instanceof LightPoleBlock)) return null;
        }
        return null;
    }

    /** Server (the block's ticker). */
    public void serverTick() {
        if (level == null || level.isClientSide || level.getGameTime() % PERIOD != 0) return;
        MachineBalanceManager.ApplianceBalance values = MachineBalanceManager.siteLight();
        if (energyStored > values.capacityFe()) energyStored = values.capacityFe();   // after a balance reload
        BlockPos headPos = head();
        BlockState head = headPos == null ? null : level.getBlockState(headPos);
        int heads = head == null ? 0 : head.getValue(AreaLightBlock.HEADS).count();
        long demand = (long) heads * values.lightFePerTick() * PERIOD;
        boolean on = heads > 0 && !level.isDay()
                && energyStored >= (powered ? demand : Math.max(demand, Math.min(2 * demand, values.capacityFe())));
        if (on) energyStored -= (int) demand;
        if (powered != on || on) {
            powered = on;
            setChanged();
        }
        if (head != null && head.getValue(AreaLightBlock.LIT) != on) level.setBlock(headPos, head.setValue(AreaLightBlock.LIT, on), Block.UPDATE_ALL);
        updateGlows(on ? head.getValue(AreaLightBlock.FACING) : null);
    }

    /** Puts the light points for a lit pole whose first head faces {@code facing}, or takes them all away (null). */
    private void updateGlows(@Nullable Direction facing) {
        if (level == null) return;
        Set<BlockPos> wanted = new HashSet<>();
        if (facing != null) {
            BlockPos centre = worldPosition.above(GLOW_HEIGHT);
            for (int dx = -GLOW_STEP; dx <= GLOW_STEP; dx += GLOW_STEP) for (int dz = -GLOW_STEP; dz <= GLOW_STEP; dz += GLOW_STEP) {
                BlockPos at = dx == 0 && dz == 0 ? centre.relative(facing) : centre.offset(dx, 0, dz);
                if (level.isLoaded(at) && (glows.contains(at) ? isGlow(at) : level.getBlockState(at).isAir())
                        && level.getBrightness(LightLayer.SKY, at) >= level.getMaxLightLevel()) wanted.add(at);
            }
        }
        boolean changed = false;
        for (BlockPos at : new ArrayList<>(glows)) {
            if (wanted.contains(at)) continue;
            if (level.isLoaded(at) && isGlow(at)) level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            glows.remove(at);
            changed = true;
        }
        for (BlockPos at : wanted) {
            if (glows.contains(at)) continue;
            level.setBlock(at, AflBlocks.LAMP_GLOW.get().defaultBlockState(), Block.UPDATE_ALL);
            glows.add(at);
            changed = true;
        }
        if (changed) setChanged();
    }

    private boolean isGlow(BlockPos at) {
        return level != null && level.getBlockState(at).getBlock() instanceof LampGlowBlock;
    }

    /** The base is going: its light points go with it. */
    public void clearGlows() {
        if (level == null || level.isClientSide) return;
        for (BlockPos at : glows) if (level.isLoaded(at) && isGlow(at)) level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        glows.clear();
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
        glows.clear();
        for (long packed : tag.getLongArray(GLOWS_KEY)) glows.add(BlockPos.of(packed));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(ENERGY_KEY, energyStored);
        tag.putBoolean(POWERED_KEY, powered);
        tag.putLongArray(GLOWS_KEY, glows.stream().mapToLong(BlockPos::asLong).toArray());
    }
}
