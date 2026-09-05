package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.AlloyFurnaceBlock;
import com.antaurora.apofirstlight.block.PowerCableBlock;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.menu.AlloyFurnaceMenu;
import com.antaurora.apofirstlight.recipe.AlloyingRecipe;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AlloyFurnaceBlockEntity extends BaseContainerBlockEntity {
    public static final int INPUT_A_SLOT = 0;
    public static final int INPUT_B_SLOT = 1;
    public static final int OUTPUT_SLOT = 2;
    public static final int CONTAINER_SIZE = 3;
    public static final int DATA_COUNT = 8;

    private static final String ENERGY_KEY = "EnergyStored";
    private static final String PROGRESS_KEY = "ProcessingProgress";
    private static final String PROCESSING_TIME_KEY = "ProcessingTime";
    private static final String ACTIVE_RECIPE_KEY = "ActiveRecipe";

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private int energyStored;
    private int processingProgress;
    private int processingTime;
    @Nullable
    private ResourceLocation activeRecipeId;
    private int balanceRevision = -1;
    private long receiveBudgetTick = Long.MIN_VALUE;
    private int receivedThisTick;

    private final IEnergyStorage inputStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            applyCurrentBalance();
            resetReceiveBudget();
            MachineBalanceManager.AlloyFurnaceBalance balance = MachineBalanceManager.alloyFurnace();
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
            return 0;
        }

        @Override
        public int getEnergyStored() {
            applyCurrentBalance();
            return energyStored;
        }

        @Override
        public int getMaxEnergyStored() {
            return MachineBalanceManager.alloyFurnace().capacityFe();
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

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> lowWord(energyStored);
                case 1 -> highWord(energyStored);
                case 2 -> lowWord(MachineBalanceManager.alloyFurnace().capacityFe());
                case 3 -> highWord(MachineBalanceManager.alloyFurnace().capacityFe());
                case 4 -> lowWord(processingProgress);
                case 5 -> highWord(processingProgress);
                case 6 -> lowWord(processingTime);
                case 7 -> highWord(processingTime);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energyStored = withLowWord(energyStored, value);
                case 1 -> energyStored = withHighWord(energyStored, value);
                case 4 -> processingProgress = withLowWord(processingProgress, value);
                case 5 -> processingProgress = withHighWord(processingProgress, value);
                case 6 -> processingTime = withLowWord(processingTime, value);
                case 7 -> processingTime = withHighWord(processingTime, value);
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public AlloyFurnaceBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.ALLOY_FURNACE.get(), position, state);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state,
                                  AlloyFurnaceBlockEntity furnace) {
        boolean changed = furnace.applyCurrentBalance();
        AlloyingRecipe recipe = furnace.findRecipe(level);
        boolean workedThisTick = false;

        if (recipe == null) {
            changed |= furnace.resetProgress();
        } else {
            if (!recipe.getId().equals(furnace.activeRecipeId)) {
                furnace.activeRecipeId = recipe.getId();
                furnace.processingProgress = 0;
                furnace.processingTime = recipe.processingTime();
                changed = true;
            } else if (furnace.processingTime != recipe.processingTime()) {
                furnace.processingTime = recipe.processingTime();
                changed = true;
            }

            ItemStack result = recipe.getResultItem(level.registryAccess());
            int workCost = MachineBalanceManager.alloyFurnace().workFePerTick();
            if (furnace.planOutput(result) != null && furnace.energyStored >= workCost) {
                furnace.energyStored -= workCost;
                furnace.processingProgress++;
                workedThisTick = true;
                changed = true;

                if (furnace.processingProgress >= recipe.processingTime()) {
                    changed |= furnace.tryComplete(recipe, level);
                }
            }
        }

        if (state.getValue(AlloyFurnaceBlock.LIT) != workedThisTick) {
            level.setBlock(position, state.setValue(AlloyFurnaceBlock.LIT, workedThisTick), Block.UPDATE_CLIENTS);
        }
        if (changed) {
            furnace.setChanged();
        }
    }

    @Nullable
    private AlloyingRecipe findRecipe(Level level) {
        if (items.get(INPUT_A_SLOT).isEmpty() && items.get(INPUT_B_SLOT).isEmpty()) {
            return null;
        }
        return level.getRecipeManager()
                .getRecipeFor(AflRecipes.ALLOYING_TYPE.get(), recipeInput(), level)
                .orElse(null);
    }

    private SimpleContainer recipeInput() {
        SimpleContainer input = new SimpleContainer(2);
        input.setItem(0, items.get(INPUT_A_SLOT));
        input.setItem(1, items.get(INPUT_B_SLOT));
        return input;
    }

    @Nullable
    private ItemStack planOutput(ItemStack result) {
        if (result.isEmpty()) {
            return null;
        }
        ItemStack existing = items.get(OUTPUT_SLOT);
        if (existing.isEmpty()) {
            return result.getCount() <= Math.min(result.getMaxStackSize(), getMaxStackSize())
                    ? result.copy()
                    : null;
        }
        if (!ItemStack.isSameItemSameTags(existing, result)) {
            return null;
        }
        int maxCount = Math.min(existing.getMaxStackSize(), getMaxStackSize());
        if (existing.getCount() + result.getCount() > maxCount) {
            return null;
        }
        ItemStack merged = existing.copy();
        merged.grow(result.getCount());
        return merged;
    }

    private boolean tryComplete(AlloyingRecipe expectedRecipe, Level level) {
        AlloyingRecipe currentRecipe = findRecipe(level);
        if (currentRecipe == null || !currentRecipe.getId().equals(expectedRecipe.getId())) {
            return resetProgress();
        }
        AlloyingRecipe.Match match = currentRecipe.match(recipeInput());
        ItemStack finalOutput = planOutput(currentRecipe.getResultItem(level.registryAccess()));
        if (match == null || finalOutput == null) {
            return false;
        }

        if (match.firstSlotCount() > 0) {
            items.get(INPUT_A_SLOT).shrink(match.firstSlotCount());
        }
        if (match.secondSlotCount() > 0) {
            items.get(INPUT_B_SLOT).shrink(match.secondSlotCount());
        }
        items.set(OUTPUT_SLOT, finalOutput);
        resetProgress();
        return true;
    }

    public int getStoredEnergy() {
        return energyStored;
    }

    public int getEnergyCapacity() {
        return MachineBalanceManager.alloyFurnace().capacityFe();
    }

    public int getProcessingProgress() {
        return processingProgress;
    }

    public int getProcessingTime() {
        return processingTime;
    }

    public ItemStack getInputStack(int inputIndex) {
        if (inputIndex < 0 || inputIndex > 1) {
            return ItemStack.EMPTY;
        }
        return items.get(inputIndex).copy();
    }

    public ItemStack getOutputStack() {
        return items.get(OUTPUT_SLOT).copy();
    }

    @Nullable
    public AlloyingRecipe getCurrentRecipe(Level level) {
        return findRecipe(level);
    }

    private boolean applyCurrentBalance() {
        int currentRevision = MachineBalanceManager.revision();
        if (balanceRevision == currentRevision) {
            return false;
        }
        balanceRevision = currentRevision;
        int capacity = MachineBalanceManager.alloyFurnace().capacityFe();
        if (energyStored > capacity) {
            ApocalypseFirstLight.LOGGER.warn(
                    "[AFL ELECTRICITY] Alloy Furnace at {} stored {} FE but reloaded capacity is {}; clamping",
                    worldPosition, energyStored, capacity);
            energyStored = capacity;
            return true;
        }
        return false;
    }

    private void resetReceiveBudget() {
        long gameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (receiveBudgetTick != gameTime) {
            receiveBudgetTick = gameTime;
            receivedThisTick = 0;
        }
    }

    private boolean resetProgress() {
        if (processingProgress == 0 && processingTime == 0 && activeRecipeId == null) {
            return false;
        }
        processingProgress = 0;
        processingTime = 0;
        activeRecipeId = null;
        return true;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.apocalypse_firstlight.alloy_furnace");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new AlloyFurnaceMenu(containerId, inventory, this, data);
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
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
        return slot == INPUT_A_SLOT || slot == INPUT_B_SLOT;
    }

    @Override
    public boolean stillValid(Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        resetProgress();
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        energyStored = Math.max(0, Math.min(tag.getInt(ENERGY_KEY),
                MachineBalanceManager.alloyFurnace().capacityFe()));
        processingProgress = Math.max(0, tag.getInt(PROGRESS_KEY));
        processingTime = Math.max(0, tag.getInt(PROCESSING_TIME_KEY));
        activeRecipeId = tag.contains(ACTIVE_RECIPE_KEY)
                ? ResourceLocation.tryParse(tag.getString(ACTIVE_RECIPE_KEY))
                : null;
        balanceRevision = -1;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt(ENERGY_KEY, energyStored);
        tag.putInt(PROGRESS_KEY, processingProgress);
        tag.putInt(PROCESSING_TIME_KEY, processingTime);
        if (activeRecipeId != null) {
            tag.putString(ACTIVE_RECIPE_KEY, activeRecipeId.toString());
        }
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability,
                                                       @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY && side != null
                && PowerCableBlock.isUtilityPortFace(getBlockState(), side)) {
            return inputCapability.cast();
        }
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
