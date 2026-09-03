package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.FluidTankBlock;
import com.antaurora.apofirstlight.fluid.FluidPipeTransfer;
import com.antaurora.apofirstlight.fluid.FluidTankStacks;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FluidTankBlockEntity extends BlockEntity {
    public static final int CAPACITY_MB = 20_000;
    private static final String FLUID_KEY = "Fluid";
    private static final String VISUAL_CAPACITY_KEY = "VisualCapacity";

    private boolean rebuildingTopology;
    private final FluidTank localTank = new FluidTank(CAPACITY_MB) {
        @Override
        protected void onContentsChanged() {
            FluidTankBlockEntity.this.onFluidChanged();
        }
    };
    private final IFluidHandler topFillHandler = new StackSidedFluidHandler(this, true, false);
    private final IFluidHandler bottomDrainHandler = new StackSidedFluidHandler(this, false, true);
    private LazyOptional<IFluidHandler> topCapability = LazyOptional.of(() -> topFillHandler);
    private LazyOptional<IFluidHandler> bottomCapability = LazyOptional.of(() -> bottomDrainHandler);

    public FluidTankBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.FLUID_TANK.get(), position, state);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state,
                                  FluidTankBlockEntity tank) {
        if (level instanceof ServerLevel serverLevel && tank.isController()) {
            FluidPipeTransfer.transferFrom(serverLevel, tank);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    public FluidStack getFluid() {
        FluidTankBlockEntity controller = resolveController();
        FluidStack fluid = controller.localTank.getFluid();
        return fluid.isEmpty() ? FluidStack.EMPTY : fluid.copy();
    }

    public int getFluidAmount() {
        return resolveController().localTank.getFluidAmount();
    }

    public int getCapacity() {
        return resolveController().localTank.getCapacity();
    }

    public boolean isController() {
        BlockState state = getBlockState();
        return state.hasProperty(FluidTankBlock.HAS_TANK_BELOW)
                && !state.getValue(FluidTankBlock.HAS_TANK_BELOW);
    }

    public boolean isTopmost() {
        BlockState state = getBlockState();
        return state.hasProperty(FluidTankBlock.HAS_TANK_ABOVE)
                && !state.getValue(FluidTankBlock.HAS_TANK_ABOVE);
    }

    public int getStackIndex() {
        return Math.max(0, worldPosition.getY() - findControllerPosition().getY());
    }

    public int getStackSize() {
        if (level == null) {
            return 1;
        }
        BlockPos cursor = findControllerPosition();
        int size = 1;
        while (size < FluidTankStacks.MAX_TANK_STACK_HEIGHT) {
            BlockState state = level.getBlockState(cursor);
            if (!state.hasProperty(FluidTankBlock.HAS_TANK_ABOVE)
                    || !state.getValue(FluidTankBlock.HAS_TANK_ABOVE)) {
                break;
            }
            cursor = cursor.above();
            size++;
        }
        return size;
    }

    public FluidStack getLocalFluidForTopology() {
        FluidStack fluid = localTank.getFluid();
        return fluid.isEmpty() ? FluidStack.EMPTY : fluid.copy();
    }

    public void clearLocalFluidForTopology() {
        rebuildingTopology = true;
        localTank.setCapacity(CAPACITY_MB);
        localTank.setFluid(FluidStack.EMPTY);
        rebuildingTopology = false;
    }

    public void applyTopologyRole(boolean controller, int stackSize, FluidStack fluid) {
        rebuildingTopology = true;
        localTank.setCapacity(controller ? CAPACITY_MB * stackSize : CAPACITY_MB);
        localTank.setFluid(controller && !fluid.isEmpty() ? fluid.copy() : FluidStack.EMPTY);
        rebuildingTopology = false;
        refreshCapabilities();
        setChanged();
    }

    public int restoreControllerFluid(FluidStack fluid) {
        if (!isController() || fluid.isEmpty()) {
            return 0;
        }
        return localTank.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
    }

    public void syncAfterTopologyChange() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            BlockState state = getBlockState();
            serverLevel.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private BlockPos findControllerPosition() {
        if (level == null) {
            return worldPosition;
        }
        BlockPos cursor = worldPosition;
        for (int offset = 1; offset < FluidTankStacks.MAX_TANK_STACK_HEIGHT; offset++) {
            BlockState state = level.getBlockState(cursor);
            if (!state.hasProperty(FluidTankBlock.HAS_TANK_BELOW)
                    || !state.getValue(FluidTankBlock.HAS_TANK_BELOW)) {
                break;
            }
            cursor = cursor.below();
        }
        return cursor;
    }

    private FluidTankBlockEntity resolveController() {
        if (level == null || isController()) {
            return this;
        }
        BlockEntity blockEntity = level.getBlockEntity(findControllerPosition());
        return blockEntity instanceof FluidTankBlockEntity controller ? controller : this;
    }

    private boolean canFillFromTop() {
        return !isRemoved() && isTopmost();
    }

    private boolean canDrainFromBottom() {
        return !isRemoved() && isController();
    }

    private void onFluidChanged() {
        if (rebuildingTopology) {
            return;
        }
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            BlockState state = getBlockState();
            serverLevel.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void refreshCapabilities() {
        topCapability.invalidate();
        bottomCapability.invalidate();
        topCapability = LazyOptional.of(() -> topFillHandler);
        bottomCapability = LazyOptional.of(() -> bottomDrainHandler);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(FLUID_KEY, localTank.writeToNBT(new CompoundTag()));
        tag.putInt(VISUAL_CAPACITY_KEY, localTank.getCapacity());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        rebuildingTopology = true;
        int capacity = tag.contains(VISUAL_CAPACITY_KEY, Tag.TAG_INT)
                ? Math.max(CAPACITY_MB, tag.getInt(VISUAL_CAPACITY_KEY))
                : CAPACITY_MB;
        localTank.setCapacity(capacity);
        if (tag.contains(FLUID_KEY, Tag.TAG_COMPOUND)) {
            localTank.readFromNBT(tag.getCompound(FLUID_KEY));
            if (localTank.getFluidAmount() > capacity) {
                localTank.getFluid().setAmount(capacity);
            }
        } else {
            localTank.setFluid(FluidStack.EMPTY);
        }
        rebuildingTopology = false;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.put(FLUID_KEY, localTank.writeToNBT(new CompoundTag()));
        tag.putInt(VISUAL_CAPACITY_KEY, localTank.getCapacity());
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
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            if (side == Direction.UP && canFillFromTop()) {
                return topCapability.cast();
            }
            if (side == Direction.DOWN && canDrainFromBottom()) {
                return bottomCapability.cast();
            }
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        topCapability.invalidate();
        bottomCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        topCapability = LazyOptional.of(() -> topFillHandler);
        bottomCapability = LazyOptional.of(() -> bottomDrainHandler);
    }

    private static final class StackSidedFluidHandler implements IFluidHandler {
        private final FluidTankBlockEntity owner;
        private final boolean allowFill;
        private final boolean allowDrain;

        private StackSidedFluidHandler(FluidTankBlockEntity owner, boolean allowFill, boolean allowDrain) {
            this.owner = owner;
            this.allowFill = allowFill;
            this.allowDrain = allowDrain;
        }

        private FluidTankBlockEntity controller() {
            return owner.resolveController();
        }

        private boolean fillAllowed() {
            return allowFill && owner.canFillFromTop();
        }

        private boolean drainAllowed() {
            return allowDrain && owner.canDrainFromBottom();
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tankIndex) {
            FluidStack fluid = controller().localTank.getFluid();
            return fluid.isEmpty() ? FluidStack.EMPTY : fluid.copy();
        }

        @Override
        public int getTankCapacity(int tankIndex) {
            return controller().localTank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tankIndex, @NotNull FluidStack stack) {
            return fillAllowed() && controller().localTank.isFluidValid(tankIndex, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return fillAllowed() ? controller().localTank.fill(resource, action) : 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return drainAllowed() ? controller().localTank.drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return drainAllowed() ? controller().localTank.drain(maxDrain, action) : FluidStack.EMPTY;
        }
    }
}
