package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.PowerCableBlock;
import com.antaurora.apofirstlight.block.ThermalGeneratorBlock;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.energy.ThermalFuelDefinitions;
import com.antaurora.apofirstlight.fluid.SidedTankHandler;
import com.antaurora.apofirstlight.fluid.FluidPortTransferBudget;
import com.antaurora.apofirstlight.fluid.FluidPipeTransfer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.Connection;
import net.minecraft.world.item.BlockItem;
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
    public static final int DATA_COUNT = 10;
    public static final int TANK_CAPACITY_MB = ThermalFuelDefinitions.TANK_CAPACITY_MB;
    public enum FuelSource { NONE, SOLID, LIQUID }
    public enum VisualState { OFF, RUNNING, FULL, ERROR }
    private VisualState visualState = VisualState.OFF;
    private VisualState lastSyncedVisualState = VisualState.OFF;
    private ItemStack lastSyncedFuel = ItemStack.EMPTY;
    private long rotorTicks;
    private long rotorSnapshotTime;
    private FuelSource activeFuelSource = FuelSource.NONE;
    private int liquidEnergyFraction;
    private boolean loadingData, visualDirty;
    private long lastVisualSync = Long.MIN_VALUE;
    private FuelSource lastSyncedSource = FuelSource.NONE;
    private boolean lastSyncedRunning;
    private final FluidTank liquidTank = new FluidTank(TANK_CAPACITY_MB, ThermalFuelDefinitions::accepts) {
        @Override protected void onContentsChanged() {
            if (!loadingData) { setChanged(); visualDirty=true; }
        }
    };
    private final IFluidHandler liquidInput = new SidedTankHandler(liquidTank,true,false,new FluidPortTransferBudget(),()->level);
    private final IFluidHandler liquidOutput = new SidedTankHandler(liquidTank,false,true,new FluidPortTransferBudget(),()->level);
    private LazyOptional<IFluidHandler> liquidInputCapability = LazyOptional.of(()->liquidInput);
    private LazyOptional<IFluidHandler> liquidOutputCapability = LazyOptional.of(()->liquidOutput);

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
                case 8 -> getLiquidAmount();
                case 9 -> getTankCapacity();
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
        if (generator.fuelEnergyRemaining <= 0 && generator.energyStored < MachineBalanceManager.thermalGenerator().capacityFe()) {
            changed |= generator.loadNextFuel(serverLevel);
        }

        MachineBalanceManager.ThermalGeneratorBalance balance = MachineBalanceManager.thermalGenerator();
        // Refill only a liquid cycle, and only enough to use this tick's conversion budget.
        // A newly available solid waits for the already consumed liquid remainder to finish.
        if (generator.activeFuelSource == FuelSource.LIQUID && generator.fuelEnergyRemaining > 0
                && generator.fuelEnergyRemaining < balance.generationFePerTick()
                && balance.capacityFe()-generator.energyStored > generator.fuelEnergyRemaining
                && !MachineBalanceManager.isThermalGeneratorFuel(generator.items.get(FUEL_SLOT))) {
            changed |= generator.loadLiquidUnit();
        }
        boolean convertedThisTick = false;
        if (generator.fuelEnergyRemaining > 0
                && (!balance.pauseBurnWhenFull() || generator.energyStored < balance.capacityFe())) {
            int converted = Math.min(balance.generationFePerTick(),
                    Math.min(generator.fuelEnergyRemaining, balance.capacityFe() - generator.energyStored));
            if (converted > 0) {
                generator.fuelEnergyRemaining -= converted;
                generator.energyStored += converted;
                convertedThisTick = true;
                changed = true;
            }
        }

        if (generator.energyStored > 0) {
            Direction outputFace = PowerCableBlock.utilityPortFace(state);
            generator.getCapability(ForgeCapabilities.ENERGY, outputFace).resolve().ifPresent(source ->
                    PowerCableTransfer.transferFrom(serverLevel, position, outputFace, source,
                            balance.maxOutputFePerTick()));
        }

        boolean running = convertedThisTick && generator.energyStored < balance.capacityFe();
        generator.visualState = generator.energyStored >= balance.capacityFe() ? VisualState.FULL
                : running ? VisualState.RUNNING : VisualState.OFF;
        if (running) generator.rotorTicks++;
        generator.rotorSnapshotTime = level.getGameTime();
        if (state.getValue(ThermalGeneratorBlock.LIT) != running) {
            level.setBlock(position, state.setValue(ThermalGeneratorBlock.LIT, running),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }

        if (changed) {
            generator.setChanged();
            generator.visualDirty=true;
        }
        if (generator.fuelEnergyRemaining<=0) generator.activeFuelSource=FuelSource.NONE;
        if (!generator.liquidTank.isEmpty()) FluidPipeTransfer.transferFrom(serverLevel,generator,
                ThermalGeneratorBlock.outputFluidFace(generator.getBlockState()),generator::restoreLiquid);
        generator.syncVisualState();
    }

    public int getStoredEnergy() {
        return energyStored;
    }

    public int getFuelEnergyRemaining() {
        return fuelEnergyRemaining;
    }

    public int getFuelEnergyTotal() {
        return fuelEnergyTotal;
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
            activeFuelSource=FuelSource.NONE;
            return loadLiquidUnit() || changed;
        }

        fuelStack.shrink(1);
        fuelEnergyRemaining = fuel.energyFe();
        fuelEnergyTotal = fuel.energyFe();
        activeFuelSource=FuelSource.SOLID;
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

    private boolean loadLiquidUnit() {
        int perBucket=ThermalFuelDefinitions.energyPer1000Mb(liquidTank.getFluid());
        if (liquidTank.isEmpty() || perBucket<=0) return false;
        liquidTank.drain(1,IFluidHandler.FluidAction.EXECUTE);
        long available=(long)perBucket+liquidEnergyFraction;
        fuelEnergyRemaining+=(int)(available/1000);
        liquidEnergyFraction=(int)(available%1000);
        fuelEnergyTotal=Math.max(fuelEnergyTotal,fuelEnergyRemaining);
        activeFuelSource=FuelSource.LIQUID;
        return true;
    }

    public FluidStack getLiquidFuel() { return liquidTank.getFluid().copy(); }
    public int getLiquidAmount() { return liquidTank.getFluidAmount(); }
    public int getTankCapacity() { return liquidTank.getCapacity(); }
    public FuelSource getActiveFuelSource() { return fuelEnergyRemaining>0?activeFuelSource:FuelSource.NONE; }
    public boolean isRunning() { return getBlockState().getValue(ThermalGeneratorBlock.LIT); }
    public VisualState getVisualState() { return visualState; }
    public double getRotorTime(float partialTick) {
        return rotorTicks + (visualState == VisualState.RUNNING && level != null
                ? Math.max(0, level.getGameTime() - rotorSnapshotTime) + partialTick : 0);
    }
    public int restoreLiquid(FluidStack fluid) { return liquidTank.fill(fluid,IFluidHandler.FluidAction.EXECUTE); }

    public void writeDropData(ItemStack stack) {
        if (energyStored == 0 && liquidTank.isEmpty() && liquidEnergyFraction == 0
                && !(activeFuelSource == FuelSource.LIQUID && fuelEnergyRemaining > 0)) return;
        CompoundTag tag=new CompoundTag();
        tag.putInt("EnergyStored",energyStored);
        tag.put("LiquidTank",liquidTank.writeToNBT(new CompoundTag()));
        tag.putInt("LiquidEnergyFraction",liquidEnergyFraction);
        // Preserve already-debited liquid energy without putting it back into the tank.
        if (activeFuelSource==FuelSource.LIQUID) {
            tag.putString("ActiveFuelSource",FuelSource.LIQUID.name());
            tag.putInt("FuelEnergyRemaining",fuelEnergyRemaining);
            tag.putInt("FuelEnergyTotal",fuelEnergyTotal);
        }
        BlockItem.setBlockEntityData(stack,AflBlockEntities.THERMAL_GENERATOR.get(),tag);
    }

    private void readLiquidState(CompoundTag tag) {
        loadingData=true;
        FluidStack stored=FluidStack.loadFluidStackFromNBT(tag.getCompound("LiquidTank"));
        if (!ThermalFuelDefinitions.accepts(stored)) stored=FluidStack.EMPTY;
        else stored.setAmount(Math.min(TANK_CAPACITY_MB,stored.getAmount()));
        liquidTank.setFluid(stored);
        loadingData=false;
        liquidEnergyFraction=Math.max(0,Math.min(999,tag.getInt("LiquidEnergyFraction")));
        try { activeFuelSource=FuelSource.valueOf(tag.getString("ActiveFuelSource")); }
        catch (IllegalArgumentException ignored) { activeFuelSource=fuelEnergyRemaining>0?FuelSource.SOLID:FuelSource.NONE; }
    }

    private void syncVisualState() {
        if (level==null || level.isClientSide()) return;
        FuelSource source=getActiveFuelSource();boolean running=isRunning();long now=level.getGameTime();
        boolean transition=source!=lastSyncedSource || running!=lastSyncedRunning
                || visualState!=lastSyncedVisualState
                || !ItemStack.matches(lastSyncedFuel, items.get(FUEL_SLOT));
        if (transition || (visualDirty && (lastVisualSync==Long.MIN_VALUE || now-lastVisualSync>=10))) {
            level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            lastSyncedSource=source;lastSyncedRunning=running;lastVisualSync=now;visualDirty=false;
            lastSyncedVisualState=visualState;lastSyncedFuel=items.get(FUEL_SLOT).copy();
        }
    }

    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag=new CompoundTag();tag.put("LiquidTank",liquidTank.writeToNBT(new CompoundTag()));
        tag.putString("VisualState",visualState.name());tag.putLong("RotorTicks",rotorTicks);
        tag.putLong("RotorSnapshotTime",level==null?rotorSnapshotTime:level.getGameTime());
        tag.put("VisibleFuel",items.get(FUEL_SLOT).save(new CompoundTag()));
        tag.putString("ActiveFuelSource",getActiveFuelSource().name());
        tag.putInt("FuelEnergyRemaining",fuelEnergyRemaining);tag.putInt("FuelEnergyTotal",fuelEnergyTotal);
        tag.putInt("LiquidEnergyFraction",liquidEnergyFraction);return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag) {
        fuelEnergyRemaining=tag.getInt("FuelEnergyRemaining");fuelEnergyTotal=tag.getInt("FuelEnergyTotal");readLiquidState(tag);
        try { visualState=VisualState.valueOf(tag.getString("VisualState")); }
        catch(IllegalArgumentException ignored) { visualState=VisualState.OFF; }
        rotorTicks=tag.getLong("RotorTicks");rotorSnapshotTime=tag.getLong("RotorSnapshotTime");
        items.set(FUEL_SLOT,ItemStack.of(tag.getCompound("VisibleFuel")));
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection,ClientboundBlockEntityDataPacket packet) {
        if(packet.getTag()!=null)handleUpdateTag(packet.getTag());
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
        readLiquidState(tag);
        rotorTicks=Math.max(0,tag.getLong("RotorTicks"));
        visualDirty=true;
        balanceRevision = -1;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("EnergyStored", energyStored);
        tag.putInt("FuelEnergyRemaining", fuelEnergyRemaining);
        tag.putInt("FuelEnergyTotal", fuelEnergyTotal);
        tag.put("LiquidTank",liquidTank.writeToNBT(new CompoundTag()));
        tag.putString("ActiveFuelSource",getActiveFuelSource().name());
        tag.putInt("LiquidEnergyFraction",liquidEnergyFraction);
        tag.putLong("RotorTicks",rotorTicks);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability,
                                                       @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY && side != null
                && PowerCableBlock.isUtilityPortFace(getBlockState(), side)) {
            return outputCapability.cast();
        }
        if (capability==ForgeCapabilities.FLUID_HANDLER && side!=null) {
            if(side==ThermalGeneratorBlock.inputFluidFace(getBlockState()))return liquidInputCapability.cast();
            if(side==ThermalGeneratorBlock.outputFluidFace(getBlockState()))return liquidOutputCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        outputCapability.invalidate();
        liquidInputCapability.invalidate();liquidOutputCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        outputCapability = LazyOptional.of(() -> outputStorage);
        liquidInputCapability=LazyOptional.of(()->liquidInput);liquidOutputCapability=LazyOptional.of(()->liquidOutput);
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
