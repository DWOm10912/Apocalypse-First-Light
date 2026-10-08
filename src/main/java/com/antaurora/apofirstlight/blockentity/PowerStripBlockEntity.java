package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.PowerStripBlock;
import com.antaurora.apofirstlight.block.WallOutletBlock;
import com.antaurora.apofirstlight.energy.PowerPlugs;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Power Strip (docs/models/power_outlets_v1.md): where its plug is. Plugged into a wall outlet's socket, carried in a
 * player's hand (not saved: the plug falls back beside the strip on reload, logout, dimension change, or when the carrier
 * walks {@link PowerPlugs#CARRY_LIMIT} blocks away), or lying beside the strip. Checks its socket every second (the outlet
 * gone, or out of the cord's reach: unplugged) and keeps LIT = switched on and the outlet has power. Its sockets serve
 * the plug-in appliances of step 2b through {@link #draw}.
 */
public class PowerStripBlockEntity extends BlockEntity {
    private static final String OUTLET_KEY = "Outlet", SOCKET_KEY = "Socket", CARRIER_KEY = "CarrierId";
    @Nullable private BlockPos outlet;
    private int socket;
    @Nullable private UUID carrier;
    /** synced for the renderer: the carrier's entity id, or -1 */
    private int carrierId = -1;
    private long nextCheck;

    public PowerStripBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.POWER_STRIP.get(), pos, state);
    }

    @Nullable public BlockPos outlet() { return outlet; }
    public int socket() { return socket; }
    public int carrierId() { return carrierId; }

    public Vec3 cordExit() {
        BlockState state = getBlockState();
        return state.getBlock() instanceof PowerStripBlock b ? b.cordExit(worldPosition, state) : Vec3.atCenterOf(worldPosition);
    }

    private boolean switchedOn() {
        BlockState state = getBlockState();
        return state.hasProperty(PowerStripBlock.ON) && state.getValue(PowerStripBlock.ON);
    }

    /** Switched on, plugged in, and the outlet has power. */
    public boolean live() {
        return level != null && switchedOn() && outlet != null && PowerPlugs.outletLive(level, outlet);
    }

    public int draw(int fe, boolean simulate) {
        if (level == null || !switchedOn() || outlet == null) return 0;
        return PowerPlugs.draw(level, outlet, fe, simulate);
    }

    // ---- the plug ----

    public void plugInto(BlockPos outletPos, int socketIndex) {
        outlet = outletPos.immutable(); socket = socketIndex;
        carrier = null; carrierId = -1;
        nextCheck = 0;
        sync();
    }

    /** Pulls the plug out of its socket (the outlet's flag is cleared); the plug then lies beside the strip. */
    public void unplug() {
        freeSocket();
        updateLit();
        sync();
    }

    private void freeSocket() {
        if (level != null && outlet != null) {
            BlockState s = level.getBlockState(outlet);
            if (s.getBlock() instanceof WallOutletBlock && WallOutletBlock.used(s, socket)) level.setBlock(outlet, WallOutletBlock.withUsed(s, socket, false), 3);
        }
        outlet = null;
    }

    public void setCarrier(@Nullable ServerPlayer player) {
        carrier = player == null ? null : player.getUUID();
        carrierId = player == null ? -1 : player.getId();
        sync();
    }

    /** The strip is going away: free its socket and the carrier's hand. */
    public void release() {
        if (carrier != null) PowerPlugs.forget(carrier, worldPosition);
        carrier = null;
        freeSocket();   // no state update here: the strip's cell already holds the new block
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        if (carrier != null && level.getGameTime() % 5 == 0) {
            Player player = level.getPlayerByUUID(carrier);
            if (player == null || player.position().add(0, 1, 0).distanceTo(cordExit()) > PowerPlugs.CARRY_LIMIT) {
                if (player != null) PowerPlugs.tell(player, "hint.apocalypse_firstlight.power_strip.dropped");
                PowerPlugs.forget(carrier, worldPosition);
                setCarrier(null);
            }
        }
        if (level.getGameTime() < nextCheck) return;
        nextCheck = level.getGameTime() + 20;
        if (outlet != null) {
            BlockState s = level.isLoaded(outlet) ? level.getBlockState(outlet) : null;
            boolean ok = s != null && s.getBlock() instanceof WallOutletBlock && WallOutletBlock.used(s, socket)
                    && WallOutletBlock.socket(outlet, s, socket).distanceTo(cordExit()) <= PowerPlugs.CORD + 0.05;
            if (s != null && !ok) unplug();
        }
        updateLit();
    }

    private void updateLit() {
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
        if (outlet != null) { tag.putLong(OUTLET_KEY, outlet.subtract(worldPosition).asLong()); tag.putInt(SOCKET_KEY, socket); }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        outlet = tag.contains(OUTLET_KEY) ? worldPosition.offset(BlockPos.of(tag.getLong(OUTLET_KEY))) : null;
        socket = tag.getInt(SOCKET_KEY);
        carrierId = tag.contains(CARRIER_KEY) ? tag.getInt(CARRIER_KEY) : -1;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = saveWithoutMetadata();
        tag.putInt(CARRIER_KEY, carrierId);
        return tag;
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(PowerPlugs.CARRY_LIMIT + 1);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
}
