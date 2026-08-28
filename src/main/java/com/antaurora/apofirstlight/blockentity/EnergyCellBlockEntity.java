package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.PowerCableBlock;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.menu.EnergyCellMenu;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EnergyCellBlockEntity extends BlockEntity implements MenuProvider {
    public static final int DATA_COUNT = 4;
    private static final String ENERGY_KEY = "EnergyStored";
    private static final String VISUAL_CAPACITY_KEY = "EnergyCapacity";
    private static final int VISUAL_SYNC_INTERVAL_TICKS = 5;
    private static final float VISUAL_SMOOTHING = 0.2F;
    private static final float VISUAL_SNAP_THRESHOLD = 0.0005F;

    private int energyStored;
    private int visualCapacity;
    private int balanceRevision = -1;
    private long transferBudgetTick = Long.MIN_VALUE;
    private long lastVisualSyncTick = Long.MIN_VALUE;
    private int receivedThisTick;
    private int extractedThisTick;
    private boolean visualSyncPending;
    private float displayedEnergyRatio;
    private boolean displayedEnergyInitialized;

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
                markVisualSyncNeeded();
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
                markVisualSyncNeeded();
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

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> lowWord(energyStored);
                case 1 -> highWord(energyStored);
                case 2 -> lowWord(MachineBalanceManager.energyCell().capacityFe());
                case 3 -> highWord(MachineBalanceManager.energyCell().capacityFe());
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energyStored = withLowWord(energyStored, value);
                case 1 -> energyStored = withHighWord(energyStored, value);
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public EnergyCellBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.ENERGY_CELL.get(), position, state);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, EnergyCellBlockEntity cell) {
        cell.applyCurrentBalance();
        cell.syncVisualEnergy(level);
    }

    public int getStoredEnergy() {
        return energyStored;
    }

    public float getDisplayedEnergyRatio() {
        int capacity = visualCapacity > 0
                ? visualCapacity
                : MachineBalanceManager.energyCell().capacityFe();
        float targetRatio = capacity <= 0
                ? 0.0F
                : Mth.clamp((float) energyStored / capacity, 0.0F, 1.0F);
        if (!displayedEnergyInitialized) {
            displayedEnergyRatio = targetRatio;
            displayedEnergyInitialized = true;
        } else if (Math.abs(targetRatio - displayedEnergyRatio) < VISUAL_SNAP_THRESHOLD) {
            displayedEnergyRatio = targetRatio;
        } else {
            displayedEnergyRatio += (targetRatio - displayedEnergyRatio) * VISUAL_SMOOTHING;
        }
        return displayedEnergyRatio;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.apocalypse_firstlight.energy_cell");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new EnergyCellMenu(containerId, inventory, this, data);
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
            markVisualSyncNeeded();
        }
    }

    private void markVisualSyncNeeded() {
        visualSyncPending = true;
    }

    private void syncVisualEnergy(Level level) {
        if (!visualSyncPending || level.isClientSide()) {
            return;
        }
        long gameTime = level.getGameTime();
        int capacity = MachineBalanceManager.energyCell().capacityFe();
        boolean endpoint = energyStored <= 0 || energyStored >= capacity;
        if (!endpoint && lastVisualSyncTick != Long.MIN_VALUE
                && gameTime - lastVisualSyncTick < VISUAL_SYNC_INTERVAL_TICKS) {
            return;
        }

        visualSyncPending = false;
        lastVisualSyncTick = gameTime;
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
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
        tag.putInt(ENERGY_KEY, energyStored);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energyStored = Math.max(0, Math.min(tag.getInt(ENERGY_KEY),
                MachineBalanceManager.energyCell().capacityFe()));
        visualCapacity = tag.contains(VISUAL_CAPACITY_KEY)
                ? Math.max(0, tag.getInt(VISUAL_CAPACITY_KEY))
                : MachineBalanceManager.energyCell().capacityFe();
        balanceRevision = -1;
        visualSyncPending = true;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(ENERGY_KEY, energyStored);
        tag.putInt(VISUAL_CAPACITY_KEY, MachineBalanceManager.energyCell().capacityFe());
        return tag;
    }

    @Override
    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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

    private static int lowWord(int value) {
        return value & 0xFFFF;
    }

    private static int highWord(int value) {
        return value >>> 16 & 0xFFFF;
    }

    private static int withLowWord(int value, int lowWord) {
        return (value & 0xFFFF0000) | (lowWord & 0xFFFF);
    }

    private static int withHighWord(int value, int highWord) {
        return (value & 0xFFFF) | ((highWord & 0xFFFF) << 16);
    }
}
