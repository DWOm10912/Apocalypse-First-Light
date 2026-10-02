package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.BeverageCoolerBlock;
import com.antaurora.apofirstlight.block.BeverageCoolerLayout;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshHost;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshAnimationState;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The master cell's block entity: door transitions and the AFL Animated Block Mesh Runtime host (Beverage Cooler V2,
 * tools/build-beverage-cooler-v2.mjs; channels {@code left_open} / {@code right_open}). BlockState owns the durable door
 * poses and is committed {@link BeverageCoolerBlock#ANIMATION_TICKS} after a click; the server announces each started
 * transition with a block event, so clients start the swing at the click instead of at the commit.
 * Also the display: {@link BeverageCoolerLayout#SLOTS} slots of one item each (front rank 0-29, back rank 30-59), saved as
 * {@code Items} and synced to clients for rendering, the same contract as the retail shelf.
 * Power (2026-10-01, machine_balance/beverage_cooler.json): a tiny FE buffer (about a second of use, so the cooler goes
 * dark soon after the cable is cut) fed only through the power port on the master's back; the lights draw while lit and the compressor while it runs, in cycles (on / off ticks) that restart
 * when the power comes back. Lit = the LIT block state (BeverageCoolerBlock#setLit): on while the buffer pays for the
 * lights, back on only once the buffer is full again, so a weak supply does not flicker. The
 * compressor starts at the beginning of an on phase and stops at its end or when the buffer cannot pay, so it starts at
 * most once a cycle; start / stop are played here, the running loop on clients (client/BlockLoopSoundController) from
 * the synced {@link #compressorRunning()}. No effect on the goods yet.
 */
public final class BeverageCoolerBlockEntity extends BlockEntity implements AflAnimatedMeshHost, Container {
    public static final int SIZE = BeverageCoolerLayout.SLOTS;
    public static final ResourceLocation MESH_PROFILE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/beverage_cooler.json");
    private static final int EVENT_LEFT_DOOR = 1;
    private static final int EVENT_RIGHT_DOOR = 2;
    private static final String ENERGY_KEY = "EnergyStored";
    private static final String CYCLE_KEY = "CompressorCycle";
    private static final String COMPRESSOR_KEY = "CompressorRunning";

    private final AflBlockMeshAnimationState meshAnimation = new AflBlockMeshAnimationState();
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean contentsDropped;
    private Boolean pendingLeft;
    private Boolean pendingRight;
    private long leftFinishTick;
    private long rightFinishTick;
    /** Client: door targets announced by the server and not yet committed to the block state. */
    private Boolean announcedLeft;
    private Boolean announcedRight;
    private int energyStored;
    private long receiveBudgetTick = Long.MIN_VALUE;
    private int receivedThisTick;
    private int compressorCycle;
    private boolean compressorRunning;

    private final IEnergyStorage inputStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            MachineBalanceManager.BeverageCoolerBalance balance = MachineBalanceManager.beverageCooler();
            long tick = level == null ? 0 : level.getGameTime();
            if (receiveBudgetTick != tick) {
                receiveBudgetTick = tick;
                receivedThisTick = 0;
            }
            int accepted = Math.min(Math.max(0, maxReceive), Math.min(
                    Math.max(0, balance.maxReceiveFePerTick() - receivedThisTick),
                    Math.max(0, balance.capacityFe() - energyStored)));
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
            return MachineBalanceManager.beverageCooler().capacityFe();
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

    public BeverageCoolerBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.BEVERAGE_COOLER.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        Direction right = getBlockState().getValue(BeverageCoolerBlock.FACING).getCounterClockWise();
        BlockPos other = worldPosition.relative(right);
        return new AABB(Math.min(worldPosition.getX(), other.getX()) - 1.5, worldPosition.getY(),
                Math.min(worldPosition.getZ(), other.getZ()) - 1.5,
                Math.max(worldPosition.getX(), other.getX()) + 2.5, worldPosition.getY() + 2.1,
                Math.max(worldPosition.getZ(), other.getZ()) + 2.5);
    }

    public boolean startDoor(boolean left, boolean targetOpen, long tick) {
        if (left ? pendingLeft != null : pendingRight != null) return false;
        if (left) {
            pendingLeft = targetOpen;
            leftFinishTick = tick + BeverageCoolerBlock.ANIMATION_TICKS;
        } else {
            pendingRight = targetOpen;
            rightFinishTick = tick + BeverageCoolerBlock.ANIMATION_TICKS;
        }
        if (level != null) {
            level.blockEvent(worldPosition, getBlockState().getBlock(), left ? EVENT_LEFT_DOOR : EVENT_RIGHT_DOOR, targetOpen ? 1 : 0);
        }
        return true;
    }

    public void completeDue(long tick) {
        if (!(getBlockState().getBlock() instanceof BeverageCoolerBlock block) || level == null) return;
        if (pendingLeft != null && tick >= leftFinishTick) {
            boolean target = pendingLeft;
            pendingLeft = null;
            block.commitDoor(level, worldPosition, true, target);
        }
        if (pendingRight != null && tick >= rightFinishTick) {
            boolean target = pendingRight;
            pendingRight = null;
            block.commitDoor(level, worldPosition, false, target);
        }
    }

    public long ticksUntilNextCompletion(long tick) {
        long left = pendingLeft == null ? Long.MAX_VALUE : Math.max(1, leftFinishTick - tick);
        long right = pendingRight == null ? Long.MAX_VALUE : Math.max(1, rightFinishTick - tick);
        return Math.min(left, right) == Long.MAX_VALUE ? 0 : Math.min(left, right);
    }

    /** Server: true broadcasts the door event to nearby clients; client: the announced swing starts now. */
    @Override
    public boolean triggerEvent(int id, int param) {
        if (id != EVENT_LEFT_DOOR && id != EVENT_RIGHT_DOOR) return super.triggerEvent(id, param);
        if (level != null && level.isClientSide) {
            if (id == EVENT_LEFT_DOOR) announcedLeft = param != 0;
            else announcedRight = param != 0;
            refreshMeshAnimationTargets();
        }
        return true;
    }

    private boolean doorTarget(boolean left) {
        Boolean announced = left ? announcedLeft : announcedRight;
        return announced != null ? announced
                : getBlockState().getValue(left ? BeverageCoolerBlock.LEFT_OPEN : BeverageCoolerBlock.RIGHT_OPEN);
    }

    // ---- power: lights and compressor ----

    /** Server, master only (BeverageCoolerBlock#getTicker). */
    public void serverTick() {
        if (level == null || !(getBlockState().getBlock() instanceof BeverageCoolerBlock block)) return;
        MachineBalanceManager.BeverageCoolerBalance balance = MachineBalanceManager.beverageCooler();
        if (energyStored > balance.capacityFe()) energyStored = balance.capacityFe();   // after a balance reload
        boolean lit = getBlockState().getValue(BeverageCoolerBlock.LIT);
        int before = energyStored;
        if (lit && energyStored < balance.lightFePerTick()) {
            lit = false;
            block.setLit(level, worldPosition, false);
        } else if (!lit && energyStored >= balance.capacityFe()) {
            lit = true;
            compressorCycle = 0;
            block.setLit(level, worldPosition, true);
        }
        if (lit) {
            energyStored -= balance.lightFePerTick();
            int period = balance.compressorOnTicks() + balance.compressorOffTicks();
            int phase = Math.floorMod(compressorCycle, period);
            if (!compressorRunning && phase == 0 && energyStored >= balance.compressorFePerTick()) setCompressor(true);
            else if (compressorRunning && (phase >= balance.compressorOnTicks() || energyStored < balance.compressorFePerTick()))
                setCompressor(false);
            if (compressorRunning) energyStored -= balance.compressorFePerTick();
            compressorCycle = (phase + 1) % period;
        } else if (compressorRunning) {
            setCompressor(false);
        }
        if (energyStored != before) setChanged();
    }

    private void setCompressor(boolean running) {
        compressorRunning = running;
        if (level != null) {
            Vec3 at = BeverageCoolerBlock.compressorPosition(worldPosition, getBlockState().getValue(BeverageCoolerBlock.FACING));
            SoundEvent sound = running ? AflSounds.BEVERAGE_COOLER_COMPRESSOR_START.get() : AflSounds.BEVERAGE_COOLER_COMPRESSOR_STOP.get();
            level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, 1.0F, 0.98F + level.random.nextFloat() * 0.04F);
        }
        sync();
    }

    /** Synced: the compressor is running (clients play its loop). */
    public boolean compressorRunning() {
        return compressorRunning;
    }

    private boolean lit() {
        return getBlockState().getValue(BeverageCoolerBlock.LIT);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY && side != null && getBlockState().getBlock() instanceof BeverageCoolerBlock block
                && block.hasPowerPort(getBlockState(), side)) return inputCapability.cast();
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

    // ---- display (one item per slot) ----

    public boolean isEmpty(int slot) {
        return items.get(slot).isEmpty();
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < SIZE && items.get(slot).isEmpty() && !stack.isEmpty();
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
        if (!removed.isEmpty()) sync();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        sync();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.replaceAll(ignored -> ItemStack.EMPTY);
        sync();
    }

    public void insertOne(int slot, ItemStack source) {
        if (canPlaceItem(slot, source)) setItem(slot, source);
    }

    public ItemStack removeOne(int slot) {
        ItemStack removed = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        sync();
        return removed;
    }

    /** Every removal path of the master cell ends here (BeverageCoolerBlock#onRemove): each item drops once. */
    public void dropContentsOnce() {
        if (contentsDropped || level == null) return;
        contentsDropped = true;
        for (ItemStack item : items) {
            if (!item.isEmpty()) Block.popResource(level, worldPosition, item.copy());
        }
        items.replaceAll(ignored -> ItemStack.EMPTY);
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        for (int slot = 0; slot < SIZE; slot++) {
            if (items.get(slot).getCount() > 1) items.set(slot, items.get(slot).copyWithCount(1));
        }
        energyStored = Math.max(0, Math.min(tag.getInt(ENERGY_KEY), MachineBalanceManager.beverageCooler().capacityFe()));
        compressorCycle = Math.max(0, tag.getInt(CYCLE_KEY));
        compressorRunning = tag.getBoolean(COMPRESSOR_KEY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt(ENERGY_KEY, energyStored);
        tag.putInt(CYCLE_KEY, compressorCycle);
        tag.putBoolean(COMPRESSOR_KEY, compressorRunning);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // ---- AFL Animated Block Mesh Runtime ----

    @Override
    public ResourceLocation meshProfile() {
        return MESH_PROFILE;
    }

    @Override
    public AflBlockMeshAnimationState meshAnimation() {
        return meshAnimation;
    }

    @Override
    public Direction meshFacing() {
        return getBlockState().getValue(BeverageCoolerBlock.FACING);
    }

    @Override
    public void refreshMeshAnimationTargets() {
        AflAnimatedMeshHost.refreshTargets(level, MESH_PROFILE, meshAnimation,
                channel -> "left_open".equals(channel) ? doorTarget(true) : "right_open".equals(channel) && doorTarget(false));
    }

    /** The lit light set (LabPBR emissive, full brightness) while LIT, else the unlit one. */
    @Override
    public boolean meshPartVisible(String part) {
        return switch (part) {
            case "lights" -> !lit();
            case "lights_lit" -> lit();
            default -> true;
        };
    }

    @Override
    public boolean meshPartEmissive(String part) {
        return "lights_lit".equals(part);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshMeshAnimationTargets();
    }

    /** The committed state catches up with an announced swing: follow the block state again. */
    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        if (announcedLeft != null && state.getValue(BeverageCoolerBlock.LEFT_OPEN) == announcedLeft) announcedLeft = null;
        if (announcedRight != null && state.getValue(BeverageCoolerBlock.RIGHT_OPEN) == announcedRight) announcedRight = null;
        refreshMeshAnimationTargets();
    }
}
