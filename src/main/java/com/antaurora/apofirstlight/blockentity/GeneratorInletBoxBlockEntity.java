package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.GeneratorInletBoxBlock;
import com.antaurora.apofirstlight.block.LogMount;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshBlockEntity;
import com.antaurora.apofirstlight.energy.BuildingSupply;
import com.antaurora.apofirstlight.energy.PlugCord;
import com.antaurora.apofirstlight.energy.PowerPlugs;
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
import org.jetbrains.annotations.Nullable;

/**
 * Generator Inlet Box (docs/models/generator_inlet_v1.md): keeps the 15 m L14-30 cord ({@link PlugCord}, stowing on the
 * box's hook) and feeds what comes in through it to the panel of the building whose wall the box hangs on (the wall
 * column behind it in the panel's footprint, or its feeder wire to a WIRED panel: {@link BuildingSupply#entryPanel}; the run in between is hidden wiring),
 * as the service meter box does. The draw follows the building: the panel's load (its average), plus up to 4 FE/t while
 * its buffer refills, never more than the genset's rating unless the load itself is more, so an overloaded house trips
 * the genset's breaker as a real one would. The genset's rating is passed on as the panel's supply (its capacity readout).
 * No buffer of its own.
 */
public class GeneratorInletBoxBlockEntity extends AflAnimatedMeshBlockEntity implements PlugCord.Owner {
    public static final ResourceLocation PROFILE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/generator_inlet_box.json");
    /** Where the cord leaves the connector's tail and which way (px, the generator's facing-north frame; tools/build-generator-inlet-v1.mjs EXIT). */
    private static final double EXIT_Y = 5.622928, EXIT_Z = 5.6112, DIR_Y = -0.866025, DIR_Z = -0.5;
    /** FE/t over the panel's load while its buffer is not full. */
    private static final int REFILL = 4;
    private static final String PANEL_KEY = "Panel", FEED_KEY = "Feed";

    private final PlugCord cord = new PlugCord(this, PlugCord.Kind.L14_30, false, PlugCord.GENERATOR_LENGTH, this::cordGeometry).stowing();
    /** synced for the crosshair hint / Jade: the panel served (relative), and the feed (FE/t, averaged over about 5 s) */
    @Nullable private BlockPos panel;
    private float feed, feedSynced;
    private long nextLookup, nextSync;

    public GeneratorInletBoxBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.GENERATOR_INLET_BOX.get(), pos, state, PROFILE);
    }

    private PlugCord.Geometry cordGeometry() {
        Direction facing = meshFacing();
        double z = EXIT_Z - LogMount.depth(getBlockState()) * 16;   // on a log wall's pad the box stands out
        return PlugCord.Geometry.hardWired(PlugCord.at(worldPosition, facing, 0, EXIT_Y, z), PlugCord.dir(facing, 0, DIR_Y, DIR_Z));
    }

    @Override public PlugCord plugCord() { return cord; }
    @Nullable public BlockPos panel() { return panel; }
    public float feed() { return feed; }

    @Nullable
    private DistributionPanelBlockEntity target() {
        if (level == null || level.isClientSide()) return null;
        long now = level.getGameTime();
        if (now >= nextLookup) {
            nextLookup = now + 40;
            BlockPos wall = worldPosition.relative(getBlockState().getValue(GeneratorInletBoxBlock.FACING).getOpposite());
            BlockPos found = BuildingSupply.entryPanel(level, worldPosition, wall);
            if (!java.util.Objects.equals(found, panel)) { panel = found; sync(); }
        }
        if (panel == null || !level.isLoaded(panel)) return null;
        return level.getBlockEntity(panel) instanceof DistributionPanelBlockEntity p ? p : null;
    }

    /** Server tick: the cord (reach, carrier), then the feed from the genset into the panel. */
    public void serverTick() {
        if (level == null) return;
        cord.serverTick();
        DistributionPanelBlockEntity target = target();
        int got = 0;
        BlockPos host = cord.host();
        if (target != null && host != null && level.isLoaded(host)) {
            int capacity = PowerPlugs.capacity(level, host);
            target.networkSupply(level.getGameTime(), capacity);
            if (capacity > 0) {
                int load = (int) Math.ceil(target.loadNow()), space = target.input().receiveEnergy(Integer.MAX_VALUE, true);
                int ask = Math.min(space, Math.max(load, Math.min(capacity, load + REFILL)));
                if (ask > 0) {
                    got = cord.draw(ask, false);
                    if (got > 0) target.input().receiveEnergy(got, false);
                }
            }
        }
        feed += (got - feed) * 0.01F;
        long now = level.getGameTime();
        if (now >= nextSync && Math.abs(feed - feedSynced) > 0.1F) { nextSync = now + 100; feedSynced = feed; sync(); }
    }

    @Override
    protected boolean meshAnimationTarget(String channel) {
        return channel.equals("open") && getBlockState().getValue(GeneratorInletBoxBlock.OPEN);
    }

    /** The coil on the hook only while the cord is stowed (its plug in no socket and no hand). */
    @Override
    public boolean meshPartVisible(String part) {
        return !part.equals("coil") || cord.stowed();
    }

    @Override
    public AABB getRenderBoundingBox() {
        return cord.renderBounds(super.getRenderBoundingBox());
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        cord.save(tag);
        if (panel != null) tag.putLong(PANEL_KEY, panel.subtract(worldPosition).asLong());
        tag.putFloat(FEED_KEY, feed);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        cord.load(tag);
        panel = tag.contains(PANEL_KEY) ? worldPosition.offset(BlockPos.of(tag.getLong(PANEL_KEY))) : null;
        feed = tag.getFloat(FEED_KEY);
    }

    @Override public CompoundTag getUpdateTag() { CompoundTag tag = saveWithoutMetadata(); cord.writeSync(tag); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) { super.onDataPacket(connection, packet); refreshMeshAnimationTargets(); }
    @Override public void handleUpdateTag(CompoundTag tag) { super.handleUpdateTag(tag); refreshMeshAnimationTargets(); }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
}
