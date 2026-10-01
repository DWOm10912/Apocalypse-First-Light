package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.ChargingStationBlock;
import com.antaurora.apofirstlight.block.PowerCableBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshHost;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshAnimationState;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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
 * Charging Station V1. The master (left) cell holds an FE buffer fed only by power cables through the two back power ports
 * (the right cell's block entity forwards its port to the master's storage, so one network touching both ports counts it
 * once), and one item on the tray, charged from the buffer each server tick (machine_balance/charging_station.json). The
 * station never outputs power and never drains the item. No loss, no idle draw.
 * Clients get the tray item and a display state: powered (buffer above zero, or power received within the last second;
 * lights the rack lamps and the screens) and the item's energy / capacity, synced when the charge crosses a whole percent.
 * Also the AFL Animated Block Mesh Runtime host: no animations; the lit or unlit lamp set by the powered state.
 * Sounds (tools/build-charging-station-sounds-v1.mjs): the start beeps and the full chime are played here when the synced
 * state changes; the charging hum is client-side (client/ChargingStationSoundController, from {@link #charging()}).
 */
public final class ChargingStationBlockEntity extends BlockEntity implements AflAnimatedMeshHost {
    public static final ResourceLocation MESH_PROFILE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/charging_station.json");
    private static final int POWER_HOLD_TICKS = 20;
    private static final String ITEM_KEY = "Item";
    private static final String ENERGY_KEY = "EnergyStored";
    private static final String POWERED_KEY = "Powered";
    private static final String ITEM_ENERGY_KEY = "ItemEnergy";
    private static final String ITEM_CAPACITY_KEY = "ItemCapacity";

    private final AflBlockMeshAnimationState meshAnimation = new AflBlockMeshAnimationState();
    private ItemStack item = ItemStack.EMPTY;
    private int energyStored;
    private long receiveBudgetTick = Long.MIN_VALUE;
    private int receivedThisTick;
    private long lastReceiveTick = -POWER_HOLD_TICKS - 1L;
    private boolean contentsDropped;
    /** Display state; on the server the values last sent to clients. */
    private boolean powered;
    private int itemEnergy;
    private int itemCapacity;

    private final IEnergyStorage inputStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            MachineBalanceManager.ChargingStationBalance balance = MachineBalanceManager.chargingStation();
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
                lastReceiveTick = tick;
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
            return MachineBalanceManager.chargingStation().capacityFe();
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

    public ChargingStationBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.CHARGING_STATION.get(), pos, state);
    }

    public boolean isMaster() {
        return getBlockState().getValue(ChargingStationBlock.PART) == ChargingStationBlock.Part.LEFT;
    }

    /** Anything with a Forge Energy capability that accepts energy (the Energy Battery, later tools and charge weapons). */
    public static boolean canCharge(ItemStack stack) {
        return !stack.isEmpty() && stack.getCapability(ForgeCapabilities.ENERGY).map(IEnergyStorage::canReceive).orElse(false);
    }

    public ItemStack item() {
        return item;
    }

    public boolean powered() {
        return powered;
    }

    public int itemEnergy() {
        return itemEnergy;
    }

    public int itemCapacity() {
        return itemCapacity;
    }

    /** Whole percent shown on the readout: 100 only when completely full. */
    public static int percent(int energy, int capacity) {
        if (capacity <= 0) return 0;
        if (energy >= capacity) return 100;
        return (int) Math.min(99, Math.max(0, (long) energy * 100 / capacity));
    }

    public void place(ItemStack stack) {
        if (!item.isEmpty() || stack.isEmpty()) return;
        item = stack.copyWithCount(1);
        updateDisplay(true);
    }

    public ItemStack take() {
        ItemStack removed = item;
        item = ItemStack.EMPTY;
        updateDisplay(true);
        return removed;
    }

    /** Server, master only: one tick of charging, then the display state. */
    public void serverTick() {
        if (level == null || !isMaster()) return;
        MachineBalanceManager.ChargingStationBalance balance = MachineBalanceManager.chargingStation();
        if (energyStored > balance.capacityFe()) energyStored = balance.capacityFe();   // after a balance reload
        if (!item.isEmpty() && energyStored > 0) {
            IEnergyStorage storage = item.getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
            if (storage != null && storage.canReceive()) {
                int accepted = storage.receiveEnergy(Math.min(balance.chargeFePerTick(), energyStored), false);
                if (accepted > 0) {
                    energyStored -= Math.min(accepted, energyStored);
                    setChanged();
                }
            }
        }
        updateDisplay(false);
    }

    private void updateDisplay(boolean force) {
        if (level == null || level.isClientSide) return;
        int energy = 0, capacity = 0;
        if (!item.isEmpty()) {
            IEnergyStorage storage = item.getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
            if (storage != null) {
                energy = storage.getEnergyStored();
                capacity = storage.getMaxEnergyStored();
            }
        }
        boolean nowPowered = energyStored > 0 || level.getGameTime() - lastReceiveTick <= POWER_HOLD_TICKS;
        if (!force && nowPowered == powered && capacity == itemCapacity
                && percent(energy, capacity) == percent(itemEnergy, itemCapacity)) return;
        boolean wasCharging = charging(), wasFullAndLit = powered && full(itemEnergy, itemCapacity);
        powered = nowPowered;
        itemEnergy = energy;
        itemCapacity = capacity;
        // start beeps when charging begins (item placed on a powered station, or power back); the chime once the item is
        // full, also for an item placed already full
        if (charging() && !wasCharging) playStatusSound(AflSounds.CHARGING_STATION_START.get());
        else if (powered && full(itemEnergy, itemCapacity) && !wasFullAndLit) playStatusSound(AflSounds.CHARGING_STATION_FULL.get());
        sync();
    }

    /** Powered with an item that is not full yet: the screens count up and the hum plays (ChargingStationSoundController). */
    public boolean charging() {
        return powered && !item.isEmpty() && itemCapacity > 0 && percent(itemEnergy, itemCapacity) < 100;
    }

    private static boolean full(int energy, int capacity) {
        return capacity > 0 && percent(energy, capacity) >= 100;
    }

    /** From the front control strip (tools/build-charging-station-v1.mjs PANEL). */
    private void playStatusSound(SoundEvent sound) {
        if (level == null) return;
        Vec3 at = ChargingStationBlock.sourceToWorld(worldPosition, getBlockState().getValue(ChargingStationBlock.FACING), 8.0, 10.8, -7.0);
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** Every removal path of the master cell ends here (ChargingStationBlock#onRemove): the tray item drops once. */
    public void dropContentsOnce() {
        if (contentsDropped || level == null) return;
        contentsDropped = true;
        if (!item.isEmpty()) Block.popResource(level, worldPosition, item.copy());
        item = ItemStack.EMPTY;
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        item = tag.contains(ITEM_KEY) ? ItemStack.of(tag.getCompound(ITEM_KEY)) : ItemStack.EMPTY;
        if (item.getCount() > 1) item = item.copyWithCount(1);
        energyStored = Math.max(0, Math.min(tag.getInt(ENERGY_KEY), MachineBalanceManager.chargingStation().capacityFe()));
        powered = tag.getBoolean(POWERED_KEY);
        itemEnergy = tag.getInt(ITEM_ENERGY_KEY);
        itemCapacity = tag.getInt(ITEM_CAPACITY_KEY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!item.isEmpty()) tag.put(ITEM_KEY, item.save(new CompoundTag()));
        tag.putInt(ENERGY_KEY, energyStored);
        tag.putBoolean(POWERED_KEY, powered);
        tag.putInt(ITEM_ENERGY_KEY, itemEnergy);
        tag.putInt(ITEM_CAPACITY_KEY, itemCapacity);
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

    // ---- power ports: both cells' back faces feed the master's buffer ----

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY && side != null && PowerCableBlock.isUtilityPortFace(getBlockState(), side)) {
            if (isMaster()) return inputCapability.cast();
            BlockPos master = ChargingStationBlock.masterPosition(worldPosition, getBlockState());
            if (level != null && level.isLoaded(master) && level.getBlockEntity(master) instanceof ChargingStationBlockEntity station
                    && station != this && station.isMaster()) return station.inputCapability.cast();
            return LazyOptional.empty();
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

    // ---- AFL Animated Block Mesh Runtime ----

    @Override
    public AABB getRenderBoundingBox() {
        return isMaster() ? AflAnimatedMeshHost.renderBounds(worldPosition, MESH_PROFILE, meshFacing()) : new AABB(worldPosition);
    }

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
        return getBlockState().getValue(ChargingStationBlock.FACING);
    }

    @Override
    public void refreshMeshAnimationTargets() {
        AflAnimatedMeshHost.refreshTargets(level, MESH_PROFILE, meshAnimation, channel -> false);
    }

    @Override
    public boolean meshPartVisible(String part) {
        return switch (part) {
            case "lamps" -> !powered;
            case "lamps_lit" -> powered;
            default -> true;
        };
    }

    @Override
    public boolean meshPartEmissive(String part) {
        return "lamps_lit".equals(part);
    }
}
