package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.IndustrialFurnaceBlock;
import com.antaurora.apofirstlight.block.PowerCableBlock;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.menu.IndustrialFurnaceMenu;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
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
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
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

public final class IndustrialFurnaceBlockEntity extends BaseContainerBlockEntity {
    public static final int LANE_COUNT = 3;
    public static final int CONTAINER_SIZE = 6;
    public static final int DATA_COUNT = 17;
    public static final int AUTO_BALANCE_DATA_INDEX = 16;

    private static final String ENERGY_KEY = "EnergyStored";
    private static final String AUTO_BALANCE_KEY = "AutoBalanceEnabled";
    private static final String LANE_PROGRESS_PREFIX = "LaneProgress";
    private static final String ACTIVE_RECIPE_PREFIX = "ActiveRecipe";
    private static final String ACTIVE_COOKING_TIME_PREFIX = "ActiveCookingTime";
    private static final String ACTIVE_RESULT_PREFIX = "ActiveResult";

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private int energyStored;
    private final int[] laneProgress = new int[LANE_COUNT];
    private final int[] laneRequiredTicks = new int[LANE_COUNT];
    private final ResourceLocation[] activeRecipeIds = new ResourceLocation[LANE_COUNT];
    private final int[] activeCookingTimes = new int[LANE_COUNT];
    private final NonNullList<ItemStack> activeResults =
            NonNullList.withSize(LANE_COUNT, ItemStack.EMPTY);
    private int balanceRevision = -1;
    private long receiveBudgetTick = Long.MIN_VALUE;
    private int receivedThisTick;
    private boolean autoBalanceEnabled;
    private boolean balancingInputs;

    private final IEnergyStorage inputStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            applyCurrentBalance();
            resetReceiveBudget();
            MachineBalanceManager.IndustrialFurnaceBalance balance =
                    MachineBalanceManager.industrialFurnace();
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
            return MachineBalanceManager.industrialFurnace().capacityFe();
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
            if (index == 0) {
                return lowWord(energyStored);
            }
            if (index == 1) {
                return highWord(energyStored);
            }
            if (index == 2) {
                return lowWord(MachineBalanceManager.industrialFurnace().capacityFe());
            }
            if (index == 3) {
                return highWord(MachineBalanceManager.industrialFurnace().capacityFe());
            }
            if (index >= 4 && index < AUTO_BALANCE_DATA_INDEX) {
                int lane = (index - 4) / 4;
                return switch ((index - 4) % 4) {
                    case 0 -> lowWord(laneProgress[lane]);
                    case 1 -> highWord(laneProgress[lane]);
                    case 2 -> lowWord(laneRequiredTicks[lane]);
                    case 3 -> highWord(laneRequiredTicks[lane]);
                    default -> 0;
                };
            }
            if (index == AUTO_BALANCE_DATA_INDEX) {
                return autoBalanceEnabled ? 1 : 0;
            }
            return 0;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                energyStored = withLowWord(energyStored, value);
            } else if (index == 1) {
                energyStored = withHighWord(energyStored, value);
            } else if (index >= 4 && index < AUTO_BALANCE_DATA_INDEX) {
                int lane = (index - 4) / 4;
                switch ((index - 4) % 4) {
                    case 0 -> laneProgress[lane] = withLowWord(laneProgress[lane], value);
                    case 1 -> laneProgress[lane] = withHighWord(laneProgress[lane], value);
                    case 2 -> laneRequiredTicks[lane] = withLowWord(laneRequiredTicks[lane], value);
                    case 3 -> laneRequiredTicks[lane] = withHighWord(laneRequiredTicks[lane], value);
                    default -> {
                    }
                }
            } else if (index == AUTO_BALANCE_DATA_INDEX) {
                autoBalanceEnabled = value != 0;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public IndustrialFurnaceBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.INDUSTRIAL_FURNACE.get(), position, state);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state,
                                  IndustrialFurnaceBlockEntity furnace) {
        boolean changed = furnace.applyCurrentBalance();
        boolean progressedAnyLane = false;
        int workCost = MachineBalanceManager.industrialFurnace().workFePerTickPerLane();

        for (int lane = 0; lane < LANE_COUNT; lane++) {
            AbstractCookingRecipe recipe = furnace.findPreferredRecipe(level, lane);
            if (recipe == null) {
                changed |= furnace.resetLane(lane);
                continue;
            }

            ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
            if (result.isEmpty()) {
                changed |= furnace.resetLane(lane);
                continue;
            }

            if (!furnace.matchesActiveRecipe(lane, recipe, result)) {
                furnace.setActiveRecipe(lane, recipe, result);
                changed = true;
            }

            int requiredTicks = requiredTicks(recipe.getCookingTime());
            if (furnace.laneRequiredTicks[lane] != requiredTicks) {
                furnace.laneRequiredTicks[lane] = requiredTicks;
                changed = true;
            }
            if (furnace.laneProgress[lane] >= requiredTicks) {
                furnace.laneProgress[lane] = requiredTicks - 1;
                changed = true;
            }

            if (!furnace.canAcceptResult(lane, result) || furnace.energyStored < workCost) {
                continue;
            }

            furnace.energyStored -= workCost;
            furnace.laneProgress[lane]++;
            progressedAnyLane = true;
            changed = true;

            if (furnace.laneProgress[lane] >= requiredTicks
                    && furnace.completeLane(level, lane, recipe, result)) {
                changed = true;
            }
        }

        if (state.getValue(IndustrialFurnaceBlock.LIT) != progressedAnyLane) {
            level.setBlock(position, state.setValue(IndustrialFurnaceBlock.LIT, progressedAnyLane),
                    Block.UPDATE_CLIENTS);
        }
        if (changed) {
            furnace.setChanged();
        }
    }

    private static int requiredTicks(int originalCookingTime) {
        double scaled = Math.ceil(Math.max(0, originalCookingTime)
                * MachineBalanceManager.industrialFurnace().processingTimeMultiplier());
        return (int) Math.max(1.0D, Math.min(Integer.MAX_VALUE, scaled));
    }

    @Nullable
    private AbstractCookingRecipe findPreferredRecipe(Level level, int lane) {
        return findPreferredRecipe(level, items.get(inputSlot(lane)));
    }

    @Nullable
    private AbstractCookingRecipe findPreferredRecipe(Level level, ItemStack stack) {
        SimpleContainer input = recipeInput(stack);
        if (input.isEmpty()) {
            return null;
        }
        return level.getRecipeManager().getRecipeFor(RecipeType.BLASTING, input, level)
                .map(recipe -> (AbstractCookingRecipe) recipe)
                .orElseGet(() -> level.getRecipeManager()
                        .getRecipeFor(RecipeType.SMELTING, input, level)
                        .map(recipe -> (AbstractCookingRecipe) recipe)
                        .orElse(null));
    }

    private SimpleContainer recipeInput(int lane) {
        return recipeInput(items.get(inputSlot(lane)));
    }

    private SimpleContainer recipeInput(ItemStack stack) {
        SimpleContainer input = new SimpleContainer(1);
        input.setItem(0, stack);
        return input;
    }

    private boolean matchesActiveRecipe(int lane, AbstractCookingRecipe recipe, ItemStack result) {
        ItemStack activeResult = activeResults.get(lane);
        return recipe.getId().equals(activeRecipeIds[lane])
                && recipe.getCookingTime() == activeCookingTimes[lane]
                && activeResult.getCount() == result.getCount()
                && ItemStack.isSameItemSameTags(activeResult, result);
    }

    private void setActiveRecipe(int lane, AbstractCookingRecipe recipe, ItemStack result) {
        laneProgress[lane] = 0;
        laneRequiredTicks[lane] = 0;
        activeRecipeIds[lane] = recipe.getId();
        activeCookingTimes[lane] = recipe.getCookingTime();
        activeResults.set(lane, result.copy());
    }

    private boolean canAcceptResult(int lane, ItemStack result) {
        ItemStack output = items.get(outputSlot(lane));
        int stackLimit = Math.min(result.getMaxStackSize(), getMaxStackSize());
        if (output.isEmpty()) {
            return result.getCount() <= stackLimit;
        }
        return ItemStack.isSameItemSameTags(output, result)
                && output.getCount() + result.getCount()
                <= Math.min(output.getMaxStackSize(), getMaxStackSize());
    }

    private boolean completeLane(Level level, int lane, AbstractCookingRecipe expectedRecipe,
                                 ItemStack expectedResult) {
        AbstractCookingRecipe currentRecipe = findPreferredRecipe(level, lane);
        if (currentRecipe == null
                || !currentRecipe.getId().equals(expectedRecipe.getId())
                || !currentRecipe.matches(recipeInput(lane), level)) {
            resetLane(lane);
            return false;
        }

        ItemStack currentResult = currentRecipe.getResultItem(level.registryAccess()).copy();
        if (currentRecipe.getCookingTime() != expectedRecipe.getCookingTime()
                || currentResult.getCount() != expectedResult.getCount()
                || !ItemStack.isSameItemSameTags(currentResult, expectedResult)
                || !canAcceptResult(lane, currentResult)) {
            resetLane(lane);
            return false;
        }

        items.get(inputSlot(lane)).shrink(1);
        ItemStack output = items.get(outputSlot(lane));
        if (output.isEmpty()) {
            items.set(outputSlot(lane), currentResult.copy());
        } else {
            output.grow(currentResult.getCount());
        }
        resetLane(lane);
        rebalanceInputsIfNeeded();
        return true;
    }

    private boolean applyCurrentBalance() {
        int currentRevision = MachineBalanceManager.revision();
        if (balanceRevision == currentRevision) {
            return false;
        }
        balanceRevision = currentRevision;
        int capacity = MachineBalanceManager.industrialFurnace().capacityFe();
        if (energyStored > capacity) {
            ApocalypseFirstLight.LOGGER.warn(
                    "[AFL ELECTRICITY] Smelting Factory at {} stored {} FE but reloaded capacity is {}; clamping",
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

    private boolean resetLane(int lane) {
        if (laneProgress[lane] == 0 && laneRequiredTicks[lane] == 0 && activeRecipeIds[lane] == null
                && activeCookingTimes[lane] == 0 && activeResults.get(lane).isEmpty()) {
            return false;
        }
        laneProgress[lane] = 0;
        laneRequiredTicks[lane] = 0;
        activeRecipeIds[lane] = null;
        activeCookingTimes[lane] = 0;
        activeResults.set(lane, ItemStack.EMPTY);
        return true;
    }

    public int getStoredEnergy() {
        return energyStored;
    }

    public int getLaneProgress(int lane) {
        return lane >= 0 && lane < LANE_COUNT ? laneProgress[lane] : 0;
    }

    public int getLaneRequiredTicks(Level level, int lane) {
        return lane >= 0 && lane < LANE_COUNT ? laneRequiredTicks[lane] : 0;
    }

    public boolean isAutoBalanceEnabled() {
        return autoBalanceEnabled;
    }

    public void toggleAutoBalance() {
        if (level == null || level.isClientSide()) {
            return;
        }
        autoBalanceEnabled = !autoBalanceEnabled;
        if (autoBalanceEnabled) {
            rebalanceInputsIfNeeded();
        }
        setChanged();
    }

    public boolean rebalanceInputsIfNeeded() {
        if (!autoBalanceEnabled || balancingInputs || level == null || level.isClientSide()) {
            return false;
        }

        ItemStack prototype = ItemStack.EMPTY;
        int totalCount = 0;
        for (int lane = 0; lane < LANE_COUNT; lane++) {
            ItemStack stack = items.get(inputSlot(lane));
            if (stack.isEmpty()) {
                continue;
            }
            if (prototype.isEmpty()) {
                prototype = stack.copy();
                prototype.setCount(1);
            } else if (!sameInputIdentity(prototype, stack)) {
                return false;
            }
            totalCount += stack.getCount();
        }

        if (prototype.isEmpty() || findPreferredRecipe(level, prototype) == null) {
            return false;
        }

        int base = totalCount / LANE_COUNT;
        int remainder = totalCount % LANE_COUNT;
        int[] targetCounts = {
                base + (remainder > 0 ? 1 : 0),
                base + (remainder > 1 ? 1 : 0),
                base
        };
        int afterTotal = 0;
        for (int targetCount : targetCounts) {
            if (targetCount < 0 || targetCount > prototype.getMaxStackSize()) {
                return false;
            }
            afterTotal += targetCount;
        }
        if (afterTotal != totalCount) {
            return false;
        }

        boolean changed = false;
        for (int lane = 0; lane < LANE_COUNT; lane++) {
            ItemStack current = items.get(inputSlot(lane));
            if (current.getCount() != targetCounts[lane]
                    || (targetCounts[lane] > 0 && !sameInputIdentity(current, prototype))) {
                changed = true;
                break;
            }
        }
        if (!changed) {
            return false;
        }

        balancingInputs = true;
        try {
            for (int lane = 0; lane < LANE_COUNT; lane++) {
                ItemStack previous = items.get(inputSlot(lane));
                ItemStack target = targetCounts[lane] == 0 ? ItemStack.EMPTY : prototype.copy();
                if (!target.isEmpty()) {
                    target.setCount(targetCounts[lane]);
                }
                if (!sameInputIdentity(previous, target)) {
                    resetLane(lane);
                }
                items.set(inputSlot(lane), target);
            }
        } finally {
            balancingInputs = false;
        }
        setChanged();
        return true;
    }

    public static int inputSlot(int lane) {
        return lane;
    }

    public static int outputSlot(int lane) {
        return LANE_COUNT + lane;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.apocalypse_firstlight.industrial_furnace");
    }

    @Override
    @Nullable
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new IndustrialFurnaceMenu(containerId, inventory, this, data);
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
        ItemStack previous = slot >= 0 && slot < CONTAINER_SIZE ? items.get(slot).copy() : ItemStack.EMPTY;
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            if (slot >= 0 && slot < LANE_COUNT
                    && !sameInputIdentity(previous, items.get(slot))) {
                resetLane(slot);
            }
            setChanged();
            rebalanceInputsIfNeeded();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty() && slot >= 0 && slot < LANE_COUNT) {
            resetLane(slot);
        }
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        ItemStack previous = items.get(slot).copy();
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        if (slot >= 0 && slot < LANE_COUNT && !sameInputIdentity(previous, stack)) {
            resetLane(slot);
        }
        setChanged();
        rebalanceInputsIfNeeded();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < LANE_COUNT;
    }

    @Override
    public boolean stillValid(Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        for (int lane = 0; lane < LANE_COUNT; lane++) {
            resetLane(lane);
        }
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        energyStored = Math.max(0, Math.min(tag.getInt(ENERGY_KEY),
                MachineBalanceManager.industrialFurnace().capacityFe()));
        autoBalanceEnabled = tag.getBoolean(AUTO_BALANCE_KEY);
        for (int lane = 0; lane < LANE_COUNT; lane++) {
            laneProgress[lane] = Math.max(0, tag.getInt(LANE_PROGRESS_PREFIX + lane));
            activeRecipeIds[lane] = tag.contains(ACTIVE_RECIPE_PREFIX + lane)
                    ? ResourceLocation.tryParse(tag.getString(ACTIVE_RECIPE_PREFIX + lane))
                    : null;
            activeCookingTimes[lane] = Math.max(0, tag.getInt(ACTIVE_COOKING_TIME_PREFIX + lane));
            laneRequiredTicks[lane] = activeCookingTimes[lane] <= 0
                    ? 0
                    : requiredTicks(activeCookingTimes[lane]);
            activeResults.set(lane, tag.contains(ACTIVE_RESULT_PREFIX + lane)
                    ? ItemStack.of(tag.getCompound(ACTIVE_RESULT_PREFIX + lane))
                    : ItemStack.EMPTY);
        }
        balanceRevision = -1;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt(ENERGY_KEY, energyStored);
        tag.putBoolean(AUTO_BALANCE_KEY, autoBalanceEnabled);
        for (int lane = 0; lane < LANE_COUNT; lane++) {
            tag.putInt(LANE_PROGRESS_PREFIX + lane, laneProgress[lane]);
            if (activeRecipeIds[lane] != null) {
                tag.putString(ACTIVE_RECIPE_PREFIX + lane, activeRecipeIds[lane].toString());
                tag.putInt(ACTIVE_COOKING_TIME_PREFIX + lane, activeCookingTimes[lane]);
                if (!activeResults.get(lane).isEmpty()) {
                    tag.put(ACTIVE_RESULT_PREFIX + lane,
                            activeResults.get(lane).save(new CompoundTag()));
                }
            }
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

    private static boolean sameInputIdentity(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return ItemStack.isSameItemSameTags(first, second);
    }
}
