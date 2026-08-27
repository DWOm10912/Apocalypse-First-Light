package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.PowerCableBlock;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.energy.PowerCableTransfer;
import com.antaurora.apofirstlight.menu.ThermalGeneratorMenu;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ThermalGeneratorBlockEntity extends BaseContainerBlockEntity {
    public static final int FUEL_SLOT = 0;
    public static final int CONTAINER_SIZE = 1;
    public static final int DATA_COUNT = 8;

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private int energyStored;
    private int fuelEnergyRemaining;
    private int fuelEnergyTotal;
    private int balanceRevision = -1;
    private long extractionBudgetTick = Long.MIN_VALUE;
    private int extractedThisTick;

    private final IEnergyStorage outputStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            resetExtractionBudget();
            MachineBalanceManager.ThermalGeneratorBalance balance = MachineBalanceManager.thermalGenerator();
            int tickBudget = Math.max(0, balance.maxOutputFePerTick() - extractedThisTick);
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
            return energyStored;
        }

        @Override
        public int getMaxEnergyStored() {
            return MachineBalanceManager.thermalGenerator().capacityFe();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };
    private LazyOptional<IEnergyStorage> outputCapability = LazyOptional.of(() -> outputStorage);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            MachineBalanceManager.ThermalGeneratorBalance balance = MachineBalanceManager.thermalGenerator();
            return switch (index) {
                case 0 -> lowWord(energyStored);
                case 1 -> highWord(energyStored);
                case 2 -> lowWord(balance.capacityFe());
                case 3 -> highWord(balance.capacityFe());
                case 4 -> lowWord(fuelEnergyRemaining);
                case 5 -> highWord(fuelEnergyRemaining);
                case 6 -> lowWord(fuelEnergyTotal);
                case 7 -> highWord(fuelEnergyTotal);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energyStored = withLowWord(energyStored, value);
                case 1 -> energyStored = withHighWord(energyStored, value);
                case 4 -> fuelEnergyRemaining = withLowWord(fuelEnergyRemaining, value);
                case 5 -> fuelEnergyRemaining = withHighWord(fuelEnergyRemaining, value);
                case 6 -> fuelEnergyTotal = withLowWord(fuelEnergyTotal, value);
                case 7 -> fuelEnergyTotal = withHighWord(fuelEnergyTotal, value);
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ThermalGeneratorBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.THERMAL_GENERATOR.get(), position, state);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state,
                                  ThermalGeneratorBlockEntity generator) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        boolean changed = generator.applyCurrentBalance();
        if (generator.fuelEnergyRemaining <= 0) {
            changed |= generator.loadNextFuel(serverLevel);
        }

        MachineBalanceManager.ThermalGeneratorBalance balance = MachineBalanceManager.thermalGenerator();
        if (generator.fuelEnergyRemaining > 0
                && (!balance.pauseBurnWhenFull() || generator.energyStored < balance.capacityFe())) {
            int converted = Math.min(balance.generationFePerTick(),
                    Math.min(generator.fuelEnergyRemaining, balance.capacityFe() - generator.energyStored));
            if (converted > 0) {
                generator.fuelEnergyRemaining -= converted;
                generator.energyStored += converted;
                changed = true;
            }
        }

        if (generator.energyStored > 0) {
            Direction outputFace = PowerCableBlock.utilityPortFace(state);
            generator.getCapability(ForgeCapabilities.ENERGY, outputFace).resolve().ifPresent(source ->
                    PowerCableTransfer.transferFrom(serverLevel, position, outputFace, source,
                            balance.maxOutputFePerTick()));
        }

        if (changed) {
            generator.setChanged();
        }
    }

    public int getStoredEnergy() {
        return energyStored;
    }

    private boolean applyCurrentBalance() {
        int currentRevision = MachineBalanceManager.revision();
        if (balanceRevision == currentRevision) {
            return false;
        }
        balanceRevision = currentRevision;
        int capacity = MachineBalanceManager.thermalGenerator().capacityFe();
        if (energyStored > capacity) {
            ApocalypseFirstLight.LOGGER.warn(
                    "[AFL ELECTRICITY] Thermal Generator at {} stored {} FE but reloaded capacity is {}; clamping",
                    worldPosition, energyStored, capacity);
            energyStored = capacity;
            return true;
        }
        return false;
    }

    private boolean loadNextFuel(ServerLevel level) {
        ItemStack fuelStack = items.get(FUEL_SLOT);
        MachineBalanceManager.FuelBalance fuel = MachineBalanceManager.thermalGeneratorFuel(fuelStack);
        if (fuel == null) {
            boolean changed = fuelEnergyRemaining != 0 || fuelEnergyTotal != 0;
            fuelEnergyRemaining = 0;
            fuelEnergyTotal = 0;
            return changed;
        }

        fuelStack.shrink(1);
        fuelEnergyRemaining = fuel.energyFe();
        fuelEnergyTotal = fuel.energyFe();
        if (fuel.remainder() != null) {
            ItemStack remainder = new ItemStack(fuel.remainder());
            if (fuelStack.isEmpty()) {
                items.set(FUEL_SLOT, remainder);
            } else if (ItemStack.isSameItemSameTags(fuelStack, remainder)
                    && fuelStack.getCount() < fuelStack.getMaxStackSize()) {
                fuelStack.grow(1);
            } else {
                Containers.dropItemStack(level,
                        worldPosition.getX() + 0.5,
                        worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5,
                        remainder);
            }
        }
        return true;
    }

    private void resetExtractionBudget() {
        long gameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (extractionBudgetTick != gameTime) {
            extractionBudgetTick = gameTime;
            extractedThisTick = 0;
        }
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.apocalypse_firstlight.thermal_generator");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new ThermalGeneratorMenu(containerId, inventory, this, data);
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    public boolean isEmpty() {
        return items.get(FUEL_SLOT).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == FUEL_SLOT && MachineBalanceManager.isThermalGeneratorFuel(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        energyStored = Math.max(0, Math.min(tag.getInt("EnergyStored"),
                MachineBalanceManager.thermalGenerator().capacityFe()));
        fuelEnergyRemaining = Math.max(0, tag.getInt("FuelEnergyRemaining"));
        fuelEnergyTotal = Math.max(fuelEnergyRemaining, tag.getInt("FuelEnergyTotal"));
        balanceRevision = -1;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("EnergyStored", energyStored);
        tag.putInt("FuelEnergyRemaining", fuelEnergyRemaining);
        tag.putInt("FuelEnergyTotal", fuelEnergyTotal);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability,
                                                       @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY && side != null
                && PowerCableBlock.isUtilityPortFace(getBlockState(), side)) {
            return outputCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        outputCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        outputCapability = LazyOptional.of(() -> outputStorage);
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
