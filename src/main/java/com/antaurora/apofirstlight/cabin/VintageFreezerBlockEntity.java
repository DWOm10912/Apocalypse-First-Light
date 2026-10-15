package com.antaurora.apofirstlight.cabin;

import com.antaurora.apofirstlight.energy.CompressorAppliance;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.energy.PlugCord;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Wood Furniture V2 (docs/models/wood_furniture_v2.md, 2026-10-15): the vintage chest freezer's master (its left cell):
 * 27 slots as plain storage ({@link WoodCounterBlockEntity}: the lid up while anyone has it open), and the power of a
 * plug-in appliance, the commercial chest freezer's ({@link CompressorAppliance} on its balance, machine_balance/
 * chest_freezer.json, and its compressor sounds): a detachable cord from the C14 inlet low on the back ({@link PlugCord}),
 * the pilot lamp ({@code lit}) while the freezer has power, the compressor in cycles. Food keeps the same as anywhere
 * (no spoilage system yet).
 */
public class VintageFreezerBlockEntity extends WoodCounterBlockEntity implements PlugCord.Owner, CompressorAppliance.Host {
    private final CompressorAppliance power = new CompressorAppliance(this, MachineBalanceManager::chestFreezer,
            AflSounds.BEVERAGE_COOLER_COMPRESSOR_START, AflSounds.BEVERAGE_COOLER_COMPRESSOR_STOP, COMPRESSOR_PITCH).cord(this::cordGeometry);
    /** The commercial freezer's compressor pitch, a little deeper (an older, bigger compressor). */
    public static final float COMPRESSOR_PITCH = 0.82F;

    public VintageFreezerBlockEntity(BlockPos pos, BlockState state) { super(WoodFurnitureV2.FREEZER, pos, state); }

    private Direction facing() { return getBlockState().getValue(WoodFurnitureV2.VintageFreezer.FACING); }

    /**
     * The cord (px from the master cell's middle, the generators' frame: +x the viewer's left, -z the front): the inlet at
     * north-frame block (0.38, 0.16) on the back (z 0.92), the cabinet 0.2..1.8 across two cells, 0.9 high
     * (tools/build-wood-furniture-v2.mjs FREEZER).
     */
    private PlugCord.Geometry cordGeometry() {
        double[] in = WoodFurnitureV2.VintageFreezer.INLET;
        return PlugCord.appliance(worldPosition, facing(), (in[0] - 0.5) * 16, in[1] * 16, (WoodFurnitureV2.VintageFreezer.BACK - 0.5) * 16,
                (0.2 - 0.5) * 16, (1.8 - 0.5) * 16, 0, 0.9 * 16);
    }

    @Override
    public PlugCord plugCord() { return power.plugCord(); }

    /** Server, every tick (the master only). */
    public void serverTick() {
        if (level == null || !(getBlockState().getBlock() instanceof WoodFurnitureV2.VintageFreezer)) return;
        power.serverTick();
    }

    public boolean compressorRunning() { return power.compressorRunning(); }

    /** Lit: the pilot lamp, the block's {@code lit} (the chunk draws the lamp). */
    @Override
    public boolean lit() { return getBlockState().getValue(WoodFurnitureV2.VintageFreezer.LIT); }

    @Override
    public void setLit(boolean lit) {
        if (level != null && getBlockState().getValue(WoodFurnitureV2.VintageFreezer.LIT) != lit)
            level.setBlock(worldPosition, getBlockState().setValue(WoodFurnitureV2.VintageFreezer.LIT, lit), Block.UPDATE_CLIENTS);
    }

    /** The compressor sits under the cabinet at the lamp's end, at the back. */
    @Override
    public Vec3 compressorPosition() { return WoodFurnitureV2.VintageFreezer.local(worldPosition, facing(), 0.45, 0.1, 0.75); }

    @Override
    public void syncAppliance() { sync(); }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        power.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        power.save(tag);
    }

    /** Clients: the compressor and the cord (never the items). */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        power.save(tag);
        power.plugCord().writeSync(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void handleUpdateTag(CompoundTag tag) { power.load(tag); }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }

    /** Both cells, the lid's swing and the cord's reach. */
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos other = worldPosition.relative(facing().getClockWise());
        return power.plugCord().renderBounds(new AABB(worldPosition).minmax(new AABB(other)).expandTowards(0, 1, 0));
    }
}
