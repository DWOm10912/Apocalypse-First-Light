package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.ServiceMeterBoxBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshBlockEntity;
import com.antaurora.apofirstlight.energy.BuildingPowerZone;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Service Meter Box (Building Power V1, docs/models/building_power_v1.md): the building's service entry. Power arriving at
 * the bottom port goes straight on to the Distribution Panel of the building whose wall the box hangs on (the wall
 * column behind it lies in the panel's footprint; the run in between counts as hidden wiring), while the disconnect is
 * on. It keeps no buffer and counts every FE that passes (the meter reading). Later the city grid feeds the same port.
 */
public class ServiceMeterBoxBlockEntity extends AflAnimatedMeshBlockEntity {
    public static final ResourceLocation PROFILE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/service_meter_box.json");
    private static final String READING_KEY = "MeterReading", PANEL_KEY = "Panel";

    private long reading;
    /** synced for the crosshair hint: the panel served (relative to this box), or null */
    @Nullable private BlockPos panel;
    private long nextLookup, nextSync;
    private long lastReadingSynced;

    private final IEnergyStorage input = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) {
            DistributionPanelBlockEntity target = target();
            if (target == null) return 0;
            int accepted = target.input().receiveEnergy(max, simulate);
            if (!simulate && accepted > 0) { reading += accepted; setChanged(); }
            return accepted;
        }
        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return 0; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return isOn(); }
    };
    private LazyOptional<IEnergyStorage> inputCap = LazyOptional.of(() -> input);

    public ServiceMeterBoxBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.SERVICE_METER_BOX.get(), pos, state, PROFILE);
    }

    public boolean isOn() {
        BlockState state = getBlockState();
        return state.hasProperty(ServiceMeterBoxBlock.ON) && state.getValue(ServiceMeterBoxBlock.ON);
    }
    public long reading() { return reading; }
    @Nullable public BlockPos panel() { return panel; }

    @Nullable
    private DistributionPanelBlockEntity target() {
        if (!isOn() || level == null || level.isClientSide()) return null;
        long now = level.getGameTime();
        if (now >= nextLookup) {
            nextLookup = now + 40;
            BlockPos wall = worldPosition.relative(getBlockState().getValue(ServiceMeterBoxBlock.FACING).getOpposite());
            BlockPos found = BuildingPowerZone.panelServing(level, wall);
            if (!java.util.Objects.equals(found, panel)) { panel = found; sync(); }
        }
        if (panel == null || !level.isLoaded(panel)) return null;
        return level.getBlockEntity(panel) instanceof DistributionPanelBlockEntity p ? p : null;
    }

    /** Server tick: refreshes the panel lookup and keeps the hint's reading roughly current (synced every 5 s at most). */
    public void serverTick() {
        if (level == null) return;
        target();
        long now = level.getGameTime();
        if (now >= nextSync && reading != lastReadingSynced) { nextSync = now + 100; lastReadingSynced = reading; sync(); }
    }

    @Override
    protected boolean meshAnimationTarget(String channel) {
        return channel.equals("off") && !isOn();
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0.5);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY && side == Direction.DOWN) return inputCap.cast();
        return super.getCapability(capability, side);
    }

    @Override public void invalidateCaps() { super.invalidateCaps(); inputCap.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); inputCap = LazyOptional.of(() -> input); }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong(READING_KEY, reading);
        if (panel != null) tag.putLong(PANEL_KEY, panel.subtract(worldPosition).asLong());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        reading = tag.getLong(READING_KEY);
        panel = tag.contains(PANEL_KEY) ? worldPosition.offset(BlockPos.of(tag.getLong(PANEL_KEY))) : null;
    }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) { super.onDataPacket(connection, packet); refreshMeshAnimationTargets(); }
    @Override public void handleUpdateTag(CompoundTag tag) { super.handleUpdateTag(tag); refreshMeshAnimationTargets(); }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
}
