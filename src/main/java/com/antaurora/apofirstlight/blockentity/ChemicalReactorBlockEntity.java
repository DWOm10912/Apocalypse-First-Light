package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.ChemicalReactorBlock;
import com.antaurora.apofirstlight.block.PowerCableBlock;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.fluid.FluidPipeTransfer;
import com.antaurora.apofirstlight.fluid.FluidPortTransferBudget;
import com.antaurora.apofirstlight.menu.ChemicalReactorMenu;
import com.antaurora.apofirstlight.recipe.ChemicalReactingRecipe;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
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

public final class ChemicalReactorBlockEntity extends BaseContainerBlockEntity {
    public static final int TANK_CAPACITY_MB = 8_000;
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int CONTAINER_SIZE = 2;
    public static final int DATA_COUNT = 12;

    private static final String ENERGY_KEY = "EnergyStored";
    public static final String INPUT_TANK_KEY = "InputTank";
    public static final String WASTE_TANK_KEY = "WasteTank";
    private static final String PROGRESS_KEY = "Progress";
    private static final String ACTIVE_RECIPE_KEY = "ActiveRecipe";

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private int energyStored;
    private int progress;
    private int requiredTicks;
    @Nullable
    private ResourceLocation activeRecipeId;
    private int balanceRevision = -1;
    private long receiveBudgetTick = Long.MIN_VALUE;
    private int receivedThisTick;
    private boolean loadingData;
    private final FluidPortTransferBudget automaticInputBudget = new FluidPortTransferBudget();
    private final FluidPortTransferBudget automaticWasteOutputBudget = new FluidPortTransferBudget();

    private final FluidTank inputTank = new FluidTank(TANK_CAPACITY_MB) {
        @Override
        protected void onContentsChanged() {
            ChemicalReactorBlockEntity.this.onFluidChanged();
        }
    };
    private final FluidTank wasteTank = new FluidTank(TANK_CAPACITY_MB) {
        @Override
        protected void onContentsChanged() {
            ChemicalReactorBlockEntity.this.onFluidChanged();
        }
    };
    private final IFluidHandler inputFluidHandler =
            new SidedTankHandler(inputTank, true, false, automaticInputBudget);
    private final IFluidHandler wasteFluidHandler =
            new SidedTankHandler(wasteTank, false, true, automaticWasteOutputBudget);

    private final IEnergyStorage inputEnergyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            applyCurrentBalance();
            resetReceiveBudget();
            MachineBalanceManager.ChemicalReactorBalance balance = MachineBalanceManager.chemicalReactor();
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
            return MachineBalanceManager.chemicalReactor().capacityFe();
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

    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> inputEnergyStorage);
    private LazyOptional<IFluidHandler> inputFluidCapability = LazyOptional.of(() -> inputFluidHandler);
    private LazyOptional<IFluidHandler> wasteFluidCapability = LazyOptional.of(() -> wasteFluidHandler);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> lowWord(energyStored);
                case 1 -> highWord(energyStored);
                case 2 -> lowWord(MachineBalanceManager.chemicalReactor().capacityFe());
                case 3 -> highWord(MachineBalanceManager.chemicalReactor().capacityFe());
                case 4 -> inputTank.getFluidAmount();
                case 5 -> inputTank.getCapacity();
                case 6 -> wasteTank.getFluidAmount();
                case 7 -> wasteTank.getCapacity();
                case 8 -> lowWord(progress);
                case 9 -> highWord(progress);
                case 10 -> lowWord(requiredTicks);
                case 11 -> highWord(requiredTicks);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energyStored = withLowWord(energyStored, value);
                case 1 -> energyStored = withHighWord(energyStored, value);
                case 8 -> progress = withLowWord(progress, value);
                case 9 -> progress = withHighWord(progress, value);
                case 10 -> requiredTicks = withLowWord(requiredTicks, value);
                case 11 -> requiredTicks = withHighWord(requiredTicks, value);
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ChemicalReactorBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.CHEMICAL_REACTOR.get(), position, state);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state,
                                  ChemicalReactorBlockEntity reactor) {
        boolean changed = reactor.applyCurrentBalance();
        ChemicalReactingRecipe recipe = reactor.findRecipe(level);
        if (recipe == null) {
            changed |= reactor.resetProgress();
        } else {
            if (!recipe.getId().equals(reactor.activeRecipeId)) {
                reactor.progress = 0;
                reactor.activeRecipeId = recipe.getId();
                changed = true;
            }
            if (reactor.requiredTicks != recipe.processingTime()) {
                reactor.requiredTicks = recipe.processingTime();
                changed = true;
            }
            if (reactor.progress >= reactor.requiredTicks) {
                reactor.progress = reactor.requiredTicks - 1;
                changed = true;
            }

            int workCost = MachineBalanceManager.chemicalReactor().workFePerTick();
            if (reactor.energyStored >= workCost && reactor.canComplete(recipe)) {
                reactor.energyStored -= workCost;
                reactor.progress++;
                changed = true;
                if (reactor.progress >= reactor.requiredTicks) {
                    reactor.completeRecipe(level, recipe);
                }
            }
        }

        if (level instanceof ServerLevel serverLevel && !reactor.wasteTank.isEmpty()) {
            FluidPipeTransfer.transferFrom(serverLevel, reactor,
                    ChemicalReactorBlock.wasteFluidFace(state), reactor::restoreWasteFluid);
        }
        if (changed) {
            reactor.setChanged();
        }
    }

    @Nullable
    private ChemicalReactingRecipe findRecipe(Level level) {
        ItemStack input = items.get(INPUT_SLOT);
        if (input.isEmpty()) {
            return null;
        }
        ChemicalReactingRecipe itemMatch = null;
        for (ChemicalReactingRecipe recipe : level.getRecipeManager()
                .getAllRecipesFor(AflRecipes.CHEMICAL_REACTING_TYPE.get())) {
            if (recipe.itemInput().test(input)) {
                if (itemMatch == null) {
                    itemMatch = recipe;
                }
                if (recipe.matches(input, inputTank.getFluid())) {
                    return recipe;
                }
            }
        }
        return itemMatch;
    }

    private boolean canComplete(ChemicalReactingRecipe recipe) {
        if (!recipe.matches(items.get(INPUT_SLOT), inputTank.getFluid())) {
            return false;
        }
        ItemStack result = recipe.itemOutput();
        ItemStack output = items.get(OUTPUT_SLOT);
        int outputLimit = Math.min(result.getMaxStackSize(), getMaxStackSize());
        if ((!output.isEmpty() && !ItemStack.isSameItemSameTags(output, result))
                || output.getCount() + result.getCount() > outputLimit) {
            return false;
        }
        FluidStack waste = recipe.wasteOutput();
        FluidStack storedWaste = wasteTank.getFluid();
        return (storedWaste.isEmpty() || storedWaste.isFluidEqual(waste))
                && wasteTank.getCapacity() - wasteTank.getFluidAmount() >= waste.getAmount();
    }

    private boolean completeRecipe(Level level, ChemicalReactingRecipe expectedRecipe) {
        ChemicalReactingRecipe currentRecipe = findRecipe(level);
        if (currentRecipe == null || !currentRecipe.getId().equals(expectedRecipe.getId())
                || currentRecipe.processingTime() != expectedRecipe.processingTime()
                || !canComplete(currentRecipe)) {
            resetProgress();
            return false;
        }

        ItemStack result = currentRecipe.itemOutput();
        FluidStack fluidInput = currentRecipe.fluidInput();
        FluidStack wasteOutput = currentRecipe.wasteOutput();
        items.get(INPUT_SLOT).shrink(1);
        inputTank.drain(fluidInput.getAmount(), IFluidHandler.FluidAction.EXECUTE);
        ItemStack output = items.get(OUTPUT_SLOT);
        if (output.isEmpty()) {
            items.set(OUTPUT_SLOT, result.copy());
        } else {
            output.grow(result.getCount());
        }
        wasteTank.fill(wasteOutput, IFluidHandler.FluidAction.EXECUTE);
        resetProgress();
        setChanged();
        return true;
    }

    private boolean resetProgress() {
        if (progress == 0 && requiredTicks == 0 && activeRecipeId == null) {
            return false;
        }
        progress = 0;
        requiredTicks = 0;
        activeRecipeId = null;
        return true;
    }

    public int getStoredEnergy() {
        applyCurrentBalance();
        return energyStored;
    }

    public int getEnergyCapacity() {
        return MachineBalanceManager.chemicalReactor().capacityFe();
    }

    public int getProcessingProgress() {
        return progress;
    }

    public int getProcessingTime() {
        return requiredTicks;
    }

    public FluidStack getInputFluid() {
        return copyFluid(inputTank.getFluid());
    }

    public FluidStack getWasteFluid() {
        return copyFluid(wasteTank.getFluid());
    }

    public int restoreWasteFluid(FluidStack fluid) {
        return fluid.isEmpty() ? 0 : wasteTank.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
    }

    public void writeDropData(ItemStack stack) {
        if (energyStored <= 0 && inputTank.isEmpty() && wasteTank.isEmpty()) {
            return;
        }
        CompoundTag blockEntityData = new CompoundTag();
        blockEntityData.putInt(ENERGY_KEY, energyStored);
        blockEntityData.put(INPUT_TANK_KEY, inputTank.writeToNBT(new CompoundTag()));
        blockEntityData.put(WASTE_TANK_KEY, wasteTank.writeToNBT(new CompoundTag()));
        BlockItem.setBlockEntityData(stack, AflBlockEntities.CHEMICAL_REACTOR.get(), blockEntityData);
    }

    private boolean applyCurrentBalance() {
        int currentRevision = MachineBalanceManager.revision();
        if (balanceRevision == currentRevision) {
            return false;
        }
        balanceRevision = currentRevision;
        int capacity = MachineBalanceManager.chemicalReactor().capacityFe();
        if (energyStored > capacity) {
            ApocalypseFirstLight.LOGGER.warn(
                    "[AFL ELECTRICITY] Chemical Reactor at {} stored {} FE but reloaded capacity is {}; clamping",
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

    private void onFluidChanged() {
        if (loadingData) {
            return;
        }
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.apocalypse_firstlight.chemical_reactor");
    }

    @Override
    @Nullable
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new ChemicalReactorMenu(containerId, inventory, this, data);
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
        ItemStack previous = items.get(slot).copy();
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            if (slot == INPUT_SLOT && !sameItemIdentity(previous, items.get(slot))) {
                resetProgress();
            }
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty() && slot == INPUT_SLOT) {
            resetProgress();
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
        if (slot == INPUT_SLOT && !sameItemIdentity(previous, stack)) {
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
        items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        resetProgress();
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        loadingData = true;
        energyStored = Math.max(0, Math.min(tag.getInt(ENERGY_KEY),
                MachineBalanceManager.chemicalReactor().capacityFe()));
        readTank(tag, INPUT_TANK_KEY, inputTank);
        readTank(tag, WASTE_TANK_KEY, wasteTank);
        loadingData = false;
        progress = Math.max(0, tag.getInt(PROGRESS_KEY));
        activeRecipeId = tag.contains(ACTIVE_RECIPE_KEY)
                ? ResourceLocation.tryParse(tag.getString(ACTIVE_RECIPE_KEY))
                : null;
        requiredTicks = 0;
        balanceRevision = -1;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt(ENERGY_KEY, energyStored);
        tag.put(INPUT_TANK_KEY, inputTank.writeToNBT(new CompoundTag()));
        tag.put(WASTE_TANK_KEY, wasteTank.writeToNBT(new CompoundTag()));
        tag.putInt(PROGRESS_KEY, progress);
        if (activeRecipeId != null) {
            tag.putString(ACTIVE_RECIPE_KEY, activeRecipeId.toString());
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
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
        BlockState state = getBlockState();
        if (capability == ForgeCapabilities.ENERGY && side != null
                && PowerCableBlock.isUtilityPortFace(state, side)) {
            return energyCapability.cast();
        }
        if (capability == ForgeCapabilities.FLUID_HANDLER && side != null) {
            if (ChemicalReactorBlock.isInputFluidFace(state, side)) {
                return inputFluidCapability.cast();
            }
            if (ChemicalReactorBlock.isWasteFluidFace(state, side)) {
                return wasteFluidCapability.cast();
            }
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCapability.invalidate();
        inputFluidCapability.invalidate();
        wasteFluidCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        energyCapability = LazyOptional.of(() -> inputEnergyStorage);
        inputFluidCapability = LazyOptional.of(() -> inputFluidHandler);
        wasteFluidCapability = LazyOptional.of(() -> wasteFluidHandler);
    }

    private static void readTank(CompoundTag tag, String key, FluidTank tank) {
        tank.setFluid(FluidStack.EMPTY);
        if (tag.contains(key, Tag.TAG_COMPOUND)) {
            tank.readFromNBT(tag.getCompound(key));
            if (tank.getFluidAmount() > TANK_CAPACITY_MB) {
                tank.getFluid().setAmount(TANK_CAPACITY_MB);
            }
        }
    }

    private static FluidStack copyFluid(FluidStack fluid) {
        return fluid.isEmpty() ? FluidStack.EMPTY : fluid.copy();
    }

    private static boolean sameItemIdentity(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return ItemStack.isSameItemSameTags(first, second);
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

    private final class SidedTankHandler implements IFluidHandler {
        private final FluidTank tank;
        private final boolean allowFill;
        private final boolean allowDrain;
        private final FluidPortTransferBudget budget;

        private SidedTankHandler(FluidTank tank, boolean allowFill, boolean allowDrain,
                                 FluidPortTransferBudget budget) {
            this.tank = tank;
            this.allowFill = allowFill;
            this.allowDrain = allowDrain;
            this.budget = budget;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tankIndex) {
            return copyFluid(tank.getFluid());
        }

        @Override
        public int getTankCapacity(int tankIndex) {
            return tank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tankIndex, @NotNull FluidStack stack) {
            return allowFill && tank.isFluidValid(tankIndex, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!allowFill || resource.isEmpty()) {
                return 0;
            }
            int limitedAmount = budget.limit(level, resource.getAmount());
            if (limitedAmount <= 0) {
                return 0;
            }
            FluidStack limitedResource = resource.copy();
            limitedResource.setAmount(limitedAmount);
            int filled = tank.fill(limitedResource, action);
            if (action == FluidAction.EXECUTE && filled > 0) {
                budget.record(level, filled);
            }
            return filled;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (!allowDrain || resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            int limitedAmount = budget.limit(level, resource.getAmount());
            if (limitedAmount <= 0) {
                return FluidStack.EMPTY;
            }
            FluidStack limitedResource = resource.copy();
            limitedResource.setAmount(limitedAmount);
            FluidStack drained = tank.drain(limitedResource, action);
            if (action == FluidAction.EXECUTE && !drained.isEmpty()) {
                budget.record(level, drained.getAmount());
            }
            return drained;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            if (!allowDrain) {
                return FluidStack.EMPTY;
            }
            int limitedAmount = budget.limit(level, maxDrain);
            if (limitedAmount <= 0) {
                return FluidStack.EMPTY;
            }
            FluidStack drained = tank.drain(limitedAmount, action);
            if (action == FluidAction.EXECUTE && !drained.isEmpty()) {
                budget.record(level, drained.getAmount());
            }
            return drained;
        }
    }
}
