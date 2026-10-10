package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.PowerStripBlock;
import com.antaurora.apofirstlight.block.WallOutletBlock;
import com.antaurora.apofirstlight.energy.PlugCord;
import com.antaurora.apofirstlight.energy.PowerPlugs;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Power Strip (docs/models/power_outlets_v1.md): its own cord and plug ({@link PlugCord}, 2.5 blocks, wall outlets only)
 * and its sockets, which the plug-in appliances use. LIT = switched on and the outlet its plug is in has power, checked
 * every second; what is plugged into it draws through {@link #draw}: through the switch and its plug to the outlet.
 */
public class PowerStripBlockEntity extends BlockEntity implements PlugCord.Owner {
    private static final String USED_KEY = "UsedSockets";
    private final PlugCord cord = new PlugCord(this, true, PlugCord.STRIP_LENGTH, this::cordGeometry);
    /** sockets with a plug in, by index (PowerStripBlock#socketPoint) */
    private int usedSockets;
    private long nextLit;

    public PowerStripBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.POWER_STRIP.get(), pos, state);
    }

    @Override public PlugCord plugCord() { return cord; }

    private PlugCord.Geometry cordGeometry() {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof PowerStripBlock b)) return PlugCord.Geometry.hardWired(worldPosition.getCenter(), new net.minecraft.world.phys.Vec3(1, 0, 0));
        return PlugCord.Geometry.hardWired(b.cordExit(worldPosition, state), PowerStripBlock.cordDirection(state));
    }

    public boolean socketUsed(int socket) { return (usedSockets & (1 << socket)) != 0; }

    public void setSocketUsed(int socket, boolean used) {
        int next = used ? usedSockets | (1 << socket) : usedSockets & ~(1 << socket);
        if (next != usedSockets) { usedSockets = next; sync(); }
    }

    private boolean switchedOn() {
        BlockState state = getBlockState();
        return state.hasProperty(PowerStripBlock.ON) && state.getValue(PowerStripBlock.ON);
    }

    /** Switched on, plugged into a wall outlet that has power, or into a socket host that is live (a running generator). */
    public boolean live() {
        BlockPos host = cord.host();
        if (level == null || !switchedOn() || host == null) return false;
        var block = level.getBlockState(host).getBlock();
        if (block instanceof com.antaurora.apofirstlight.energy.PlugSocketHost h) return h.live(level, host);
        return block instanceof WallOutletBlock && PowerPlugs.outletLive(level, host);
    }

    public int draw(int fe, boolean simulate) {
        return switchedOn() ? cord.draw(fe, simulate) : 0;
    }

    /** The strip is going away: free its outlet socket and the carrier's hand (what is plugged into it notices by itself). */
    public void release() {
        cord.release();
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        cord.serverTick();
        if (level.getGameTime() < nextLit) return;
        nextLit = level.getGameTime() + 20;
        updateLit();
    }

    public void updateLit() {
        if (level == null || level.isClientSide()) return;
        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof PowerStripBlock)) return;
        boolean lit = live();
        if (state.getValue(PowerStripBlock.LIT) != lit) level.setBlock(worldPosition, state.setValue(PowerStripBlock.LIT, lit), Block.UPDATE_CLIENTS);
    }

    // ---- save / sync ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        cord.save(tag);
        if (usedSockets != 0) tag.putInt(USED_KEY, usedSockets);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        cord.load(tag);
        cord.loadLegacy(tag, "Outlet", "Socket");
        usedSockets = tag.getInt(USED_KEY);
    }

    @Override public CompoundTag getUpdateTag() { CompoundTag tag = saveWithoutMetadata(); cord.writeSync(tag); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public AABB getRenderBoundingBox() {
        return cord.renderBounds(new AABB(worldPosition));
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Nullable public BlockPos outletPos() { return cord.host(); }
}
