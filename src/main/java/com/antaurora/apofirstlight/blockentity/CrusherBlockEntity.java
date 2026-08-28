package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.CrusherBlock;
import com.antaurora.apofirstlight.block.PowerCableBlock;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.menu.CrusherMenu;
import com.antaurora.apofirstlight.recipe.CrushingRecipe;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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

import java.util.List;

public final class CrusherBlockEntity extends BaseContainerBlockEntity {
    public static final int INPUT_SLOT = 0;
    public static final int FIRST_OUTPUT_SLOT = 1;
    public static final int OUTPUT_SLOT_COUNT = 6;
    public static final int CONTAINER_SIZE = 7;
    public static final int DATA_COUNT = 8;

    private static final String ENERGY_KEY = "EnergyStored";
    private static final String PROGRESS_KEY = "ProcessingProgress";
    private static final String PROCESSING_TIME_KEY = "ProcessingTime";
    private static final String ACTIVE_RECIPE_KEY = "ActiveRecipe";
    private static final String PENDING_RESULTS_KEY = "PendingResults";

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private int energyStored;
    private int processingProgress;
    private int processingTime;
    @Nullable
    private ResourceLocation activeRecipeId;
    private NonNullList<ItemStack> pendingResults =
            NonNullList.withSize(CrushingRecipe.MAX_RESULTS, ItemStack.EMPTY);
    private int balanceRevision = -1;
    private long receiveBudgetTick = Long.MIN_VALUE;
    private int receivedThisTick;

    private final IEnergyStorage inputStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            applyCurrentBalance();
            resetReceiveBudget();
            MachineBalanceManager.CrusherBalance balance = MachineBalanceManager.crusher();
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
            return MachineBalanceManager.crusher().capacityFe();
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
                case 2 -> lowWord(MachineBalanceManager.crusher().capacityFe());
                case 3 -> highWord(MachineBalanceManager.crusher().capacityFe());
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

    public CrusherBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.CRUSHER.get(), position, state);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, CrusherBlockEntity crusher) {
        boolean changed = crusher.applyCurrentBalance();
        CrushingRecipe recipe = crusher.findRecipe(level);
        boolean workedThisTick = false;

        if (recipe == null) {
            changed |= crusher.resetProgress();
        } else {
            if (!recipe.getId().equals(crusher.activeRecipeId)) {
                crusher.activeRecipeId = recipe.getId();
                crusher.processingProgress = 0;
                crusher.processingTime = recipe.processingTime();
                crusher.clearPendingResults();
                changed = true;
            } else if (crusher.processingTime != recipe.processingTime()) {
                crusher.processingTime = recipe.processingTime();
                changed = true;
            }

            if (crusher.hasPendingResults()) {
                changed |= crusher.tryCommitPendingResults(recipe, level);
            } else {
                NonNullList<ItemStack> plannedOutputs = crusher.planOutputs(recipe.maximumResults());
                int workCost = MachineBalanceManager.crusher().workFePerTick();
                if (plannedOutputs != null && crusher.energyStored >= workCost) {
                    crusher.energyStored -= workCost;
                    crusher.processingProgress++;
                    workedThisTick = true;
                    changed = true;

                    if (crusher.processingProgress >= recipe.processingTime()) {
                        crusher.setPendingResults(recipe.rollResults(level.random));
                        changed |= crusher.tryCommitPendingResults(recipe, level);
                    }
                }
            }
        }

        if (state.getValue(CrusherBlock.LIT) != workedThisTick) {
            level.setBlock(position, state.setValue(CrusherBlock.LIT, workedThisTick), Block.UPDATE_CLIENTS);
        }
        if (changed) {
            crusher.setChanged();
        }
    }

    @Nullable
    private CrushingRecipe findRecipe(Level level) {
        if (items.get(INPUT_SLOT).isEmpty()) {
            return null;
        }
        return level.getRecipeManager()
                .getRecipeFor(AflRecipes.CRUSHING_TYPE.get(), recipeInput(), level)
                .orElse(null);
    }

    private SimpleContainer recipeInput() {
        SimpleContainer input = new SimpleContainer(1);
        input.setItem(0, items.get(INPUT_SLOT));
        return input;
    }

    @Nullable
    private NonNullList<ItemStack> planOutputs(List<ItemStack> results) {
        NonNullList<ItemStack> planned = NonNullList.withSize(OUTPUT_SLOT_COUNT, ItemStack.EMPTY);
        for (int index = 0; index < OUTPUT_SLOT_COUNT; index++) {
            planned.set(index, items.get(FIRST_OUTPUT_SLOT + index).copy());
        }

        for (ItemStack result : results) {
            if (result.isEmpty()) {
                continue;
            }
            ItemStack remaining = result.copy();
            for (int index = 0; index < OUTPUT_SLOT_COUNT && !remaining.isEmpty(); index++) {
                ItemStack existing = planned.get(index);
                if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, remaining)) {
                    int freeSpace = Math.min(existing.getMaxStackSize(), getMaxStackSize()) - existing.getCount();
                    int moved = Math.min(Math.max(0, freeSpace), remaining.getCount());
                    if (moved > 0) {
                        existing.grow(moved);
                        remaining.shrink(moved);
                    }
                }
            }
            for (int index = 0; index < OUTPUT_SLOT_COUNT && !remaining.isEmpty(); index++) {
                if (planned.get(index).isEmpty()) {
                    int moved = Math.min(remaining.getCount(),
                            Math.min(remaining.getMaxStackSize(), getMaxStackSize()));
                    ItemStack inserted = remaining.copy();
                    inserted.setCount(moved);
                    planned.set(index, inserted);
                    remaining.shrink(moved);
                }
            }
            if (!remaining.isEmpty()) {
                return null;
            }
        }
        return planned;
    }

    private boolean tryCommitPendingResults(CrushingRecipe recipe, Level level) {
        if (!recipe.matches(recipeInput(), level)) {
            return resetProgress();
        }
        NonNullList<ItemStack> finalOutputs = planOutputs(pendingResults);
        if (finalOutputs == null) {
            return false;
        }

        items.get(INPUT_SLOT).shrink(1);
        for (int index = 0; index < OUTPUT_SLOT_COUNT; index++) {
            items.set(FIRST_OUTPUT_SLOT + index, finalOutputs.get(index));
        }
        resetProgress();
        return true;
    }

    private boolean hasPendingResults() {
        return pendingResults.stream().anyMatch(stack -> !stack.isEmpty());
    }

    private void setPendingResults(List<ItemStack> results) {
        clearPendingResults();
        for (int index = 0; index < results.size(); index++) {
            pendingResults.set(index, results.get(index).copy());
        }
    }

    private void clearPendingResults() {
        for (int index = 0; index < pendingResults.size(); index++) {
            pendingResults.set(index, ItemStack.EMPTY);
        }
    }

    public int getStoredEnergy() {
        return energyStored;
    }

    public int getProcessingProgress() {
        return processingProgress;
    }

    public int getProcessingTime() {
        return processingTime;
    }

    private boolean applyCurrentBalance() {
        int currentRevision = MachineBalanceManager.revision();
        if (balanceRevision == currentRevision) {
            return false;
        }
        balanceRevision = currentRevision;
        int capacity = MachineBalanceManager.crusher().capacityFe();
        if (energyStored > capacity) {
            ApocalypseFirstLight.LOGGER.warn(
                    "[AFL ELECTRICITY] Crusher at {} stored {} FE but reloaded capacity is {}; clamping",
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
        if (processingProgress == 0 && processingTime == 0 && activeRecipeId == null
                && !hasPendingResults()) {
            return false;
        }
        processingProgress = 0;
        processingTime = 0;
        activeRecipeId = null;
        clearPendingResults();
        return true;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.apocalypse_firstlight.crusher");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new CrusherMenu(containerId, inventory, this, data);
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
            if (slot == INPUT_SLOT) {
                resetProgress();
            }
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (slot == INPUT_SLOT && !removed.isEmpty()) {
            resetProgress();
        }
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        if (slot == INPUT_SLOT) {
            resetProgress();
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT_SLOT;
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
                MachineBalanceManager.crusher().capacityFe()));
        processingProgress = Math.max(0, tag.getInt(PROGRESS_KEY));
        processingTime = Math.max(0, tag.getInt(PROCESSING_TIME_KEY));
        activeRecipeId = tag.contains(ACTIVE_RECIPE_KEY)
                ? ResourceLocation.tryParse(tag.getString(ACTIVE_RECIPE_KEY))
                : null;
        pendingResults = NonNullList.withSize(CrushingRecipe.MAX_RESULTS, ItemStack.EMPTY);
        if (tag.contains(PENDING_RESULTS_KEY, Tag.TAG_COMPOUND)) {
            ContainerHelper.loadAllItems(tag.getCompound(PENDING_RESULTS_KEY), pendingResults);
        }
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
        if (hasPendingResults()) {
            CompoundTag pendingTag = new CompoundTag();
            ContainerHelper.saveAllItems(pendingTag, pendingResults, true);
            tag.put(PENDING_RESULTS_KEY, pendingTag);
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
