package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.FluidTankBlock;
import com.antaurora.apofirstlight.fluid.FluidPipeTransfer;
import com.antaurora.apofirstlight.fluid.FluidPortTransferBudget;
import com.antaurora.apofirstlight.fluid.FluidTankStoredFluid;
import com.antaurora.apofirstlight.fluid.FluidTankStructures;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fluid Tank V2 cell (docs/models/fluid_tank_v2.md). A single tank holds its own fluid; in a joined tank
 * (fluid/FluidTankStructures) the master cell holds all of it (capacity cells x {@link #CAPACITY_MB}) and the other cells
 * hold none. Every cell with a top face not joined upward fills the tank through its AFL fluid port, every cell with a
 * bottom face not joined downward drains it, each port at most 25 mB a tick (FluidPortTransferBudget); a bottom port
 * with a Fluid Pipe V2 under it pushes into the pipe network every tick. A cell glows by its own share of the fluid.
 */
public final class FluidTankBlockEntity extends BlockEntity {
    /** Per tank block, AFL's fluid scale (1 mB = 1 L): about 0.8 m3 inside the glass (was 20,000 until 2026-10-04). */
    public static final int CAPACITY_MB = 800;
    private static final String VISUAL_CAPACITY_KEY = "VisualCapacity";
    private boolean rebuildingTopology;
    private boolean preserveOnBreak;
    private FluidStack preparedDropFluid = FluidStack.EMPTY;
    private final FluidPortTransferBudget inputBudget = new FluidPortTransferBudget();
    private final FluidPortTransferBudget outputBudget = new FluidPortTransferBudget();
    private final FluidTank localTank = new FluidTank(CAPACITY_MB) {
        @Override
        protected void onContentsChanged() {
            FluidTankBlockEntity.this.onFluidChanged();
        }
    };
    private final IFluidHandler topFillHandler = new PortHandler(true);
    private final IFluidHandler bottomDrainHandler = new PortHandler(false);
    private LazyOptional<IFluidHandler> topCapability = LazyOptional.of(() -> topFillHandler);
    private LazyOptional<IFluidHandler> bottomCapability = LazyOptional.of(() -> bottomDrainHandler);

    public FluidTankBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.FLUID_TANK.get(), position, state);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, FluidTankBlockEntity tank) {
        if (!(level instanceof ServerLevel server)) return;
        // a hot liquid (lava) in an ordinary tank melts it, cell by cell (FluidHeat); the heat-resistant tank holds it
        if (state.getBlock() instanceof FluidTankBlock block && !block.heatResistant() && com.antaurora.apofirstlight.fluid.FluidHeat.isHot(tank.getFluid())) {
            com.antaurora.apofirstlight.fluid.FluidHeat.melt(server, position);
            return;
        }
        com.antaurora.apofirstlight.fluid.FluidLighting.update(server, position,
                com.antaurora.apofirstlight.fluid.FluidLighting.emission(tank.getMemberFluidSlice(), 9));
        FluidPipeTransfer.transferFrom(server, tank);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    // ---- the tank this cell belongs to

    public FluidTankStructures.Shape shape() {
        return level == null ? new FluidTankStructures.Shape(worldPosition, 1, 1, 1) : FluidTankStructures.shapeOf(level, worldPosition, getBlockState());
    }

    private FluidTankBlockEntity master() {
        if (level == null) return this;
        BlockPos master = shape().master();
        return master.equals(worldPosition) ? this : level.getBlockEntity(master) instanceof FluidTankBlockEntity m ? m : this;
    }

    /** The falling stream (FluidTankRenderer) reaches down through the cells below. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        int below = level == null ? 0 : shape().layerOf(worldPosition);
        return new net.minecraft.world.phys.AABB(worldPosition.getX(), worldPosition.getY() - below, worldPosition.getZ(),
                worldPosition.getX() + 1, worldPosition.getY() + 1, worldPosition.getZ() + 1);
    }

    public FluidStack getFluid() {
        FluidStack fluid = master().localTank.getFluid();
        return fluid.isEmpty() ? FluidStack.EMPTY : fluid.copy();
    }

    public int getFluidAmount() {
        return master().localTank.getFluidAmount();
    }

    public int getCapacity() {
        return master().localTank.getCapacity();
    }

    /** Fuel leaking out of a bullet hole or gone up in a burst (fluid/FuelLeaks): taken from the shared tank. */
    public int drainShared(int mb) {
        return master().localTank.drain(mb, IFluidHandler.FluidAction.EXECUTE).getAmount();
    }

    public boolean hasBottomPort() {
        return !getBlockState().getValue(FluidTankBlock.JOINED.get(Direction.DOWN));
    }

    public boolean sharesFluidStorageWith(FluidTankBlockEntity other) {
        return shape().master().equals(other.shape().master());
    }

    /** This cell's share of its tank's fluid (filled from the bottom layer up): its light and its drop. */
    public FluidStack getMemberFluidSlice() {
        FluidStack fluid = master().localTank.getFluid();
        if (fluid.isEmpty()) return FluidStack.EMPTY;
        int amount = FluidTankStructures.shareAt(fluid.getAmount(), shape(), worldPosition);
        if (amount <= 0) return FluidStack.EMPTY;
        FluidStack slice = fluid.copy();
        slice.setAmount(amount);
        return slice;
    }

    /** Fills the tank (a pipe transfer's rollback, tests); returns the amount taken. */
    public int restoreControllerFluid(FluidStack fluid) {
        return fluid.isEmpty() ? 0 : master().localTank.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
    }

    public boolean interactWithFluidContainer(Player player, InteractionHand hand) {
        return FluidUtil.interactWithFluidHandler(player, hand, master().localTank);
    }

    // ---- structure changes (FluidTankStructures)

    /** This cell's own contents (not its tank's). */
    public FluidStack ownContents() {
        FluidStack fluid = localTank.getFluid();
        return fluid.isEmpty() ? FluidStack.EMPTY : fluid.copy();
    }

    /** Takes this cell's own contents out (left empty, capacity one cell). */
    public FluidStack takeContents() {
        FluidStack fluid = ownContents();
        setContents(CAPACITY_MB, FluidStack.EMPTY);
        return fluid;
    }

    /** Sets this cell's own capacity and contents (a single tank that held more keeps it: the capacity grows to fit). */
    public void setContents(int capacity, FluidStack fluid) {
        rebuildingTopology = true;
        localTank.setCapacity(Math.max(capacity, fluid.isEmpty() ? 0 : fluid.getAmount()));
        localTank.setFluid(fluid.isEmpty() ? FluidStack.EMPTY : fluid.copy());
        rebuildingTopology = false;
        refreshCapabilities();
        setChanged();
    }

    public void syncAfterTopologyChange() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            BlockState state = getBlockState();
            serverLevel.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** A player is breaking this cell: a survival player gets its share of the fluid in the dropped tank. */
    public void preparePlayerBreakDrop(boolean preserveInDrop) {
        preserveOnBreak = preserveInDrop;
    }

    /** FluidTankStructures, as this cell goes: its share, kept for the drop when a survival player broke it. */
    public void keepForDrop(FluidStack share) {
        preparedDropFluid = preserveOnBreak && !share.isEmpty() ? share.copy() : FluidStack.EMPTY;
    }

    public FluidStack getPreparedDropFluid() {
        return preparedDropFluid.isEmpty() ? FluidStack.EMPTY : preparedDropFluid.copy();
    }

    private void onFluidChanged() {
        if (rebuildingTopology) return;
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
        tag.put(FluidTankStoredFluid.FLUID_KEY, localTank.writeToNBT(new CompoundTag()));
        tag.putInt(VISUAL_CAPACITY_KEY, localTank.getCapacity());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        rebuildingTopology = true;
        int capacity = tag.contains(VISUAL_CAPACITY_KEY, Tag.TAG_INT) ? Math.max(CAPACITY_MB, tag.getInt(VISUAL_CAPACITY_KEY)) : CAPACITY_MB;
        localTank.setCapacity(capacity);
        if (tag.contains(FluidTankStoredFluid.FLUID_KEY, Tag.TAG_COMPOUND)) {
            localTank.readFromNBT(tag.getCompound(FluidTankStoredFluid.FLUID_KEY));
            if (localTank.getFluidAmount() > capacity) localTank.getFluid().setAmount(capacity);
        } else {
            localTank.setFluid(FluidStack.EMPTY);
        }
        rebuildingTopology = false;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.put(FluidTankStoredFluid.FLUID_KEY, localTank.writeToNBT(new CompoundTag()));
        tag.putInt(VISUAL_CAPACITY_KEY, localTank.getCapacity());
        return tag;
    }

    @Override
    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.FLUID_HANDLER && side != null && !isRemoved()
                && getBlockState().getBlock() instanceof FluidTankBlock block && block.hasFluidPort(getBlockState(), side)) {
            return side == Direction.UP ? topCapability.cast() : bottomCapability.cast();
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

    /** One port: fills (top) or drains (bottom) the whole tank, at most 25 mB a tick through this port. */
    private final class PortHandler implements IFluidHandler {
        private final boolean fill;

        private PortHandler(boolean fill) {
            this.fill = fill;
        }

        private FluidTank tank() {
            return master().localTank;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tankIndex) {
            FluidStack fluid = tank().getFluid();
            return fluid.isEmpty() ? FluidStack.EMPTY : fluid.copy();
        }

        @Override
        public int getTankCapacity(int tankIndex) {
            return tank().getCapacity();
        }

        @Override
        public boolean isFluidValid(int tankIndex, @NotNull FluidStack stack) {
            return fill && tank().isFluidValid(tankIndex, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!fill || resource.isEmpty()) return 0;
            int limited = inputBudget.limit(level, resource.getAmount());
            if (limited <= 0) return 0;
            FluidStack part = resource.copy();
            part.setAmount(limited);
            int filled = tank().fill(part, action);
            if (action == FluidAction.EXECUTE && filled > 0) inputBudget.record(level, filled);
            return filled;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (fill || resource.isEmpty()) return FluidStack.EMPTY;
            int limited = outputBudget.limit(level, resource.getAmount());
            if (limited <= 0) return FluidStack.EMPTY;
            FluidStack part = resource.copy();
            part.setAmount(limited);
            FluidStack drained = tank().drain(part, action);
            if (action == FluidAction.EXECUTE && !drained.isEmpty()) outputBudget.record(level, drained.getAmount());
            return drained;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            if (fill) return FluidStack.EMPTY;
            int limited = outputBudget.limit(level, maxDrain);
            if (limited <= 0) return FluidStack.EMPTY;
            FluidStack drained = tank().drain(limited, action);
            if (action == FluidAction.EXECUTE && !drained.isEmpty()) outputBudget.record(level, drained.getAmount());
            return drained;
        }
    }
}
