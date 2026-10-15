package com.antaurora.apofirstlight.cabin;

import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshHost;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshAnimationState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Emergency Radio V1 (docs/models/emergency_radio_v1.md): the radio's charge, in ticks of play left (at most
 * {@link EmergencyRadio#CAPACITY}). The crank winds it up; while the switch is on it plays one tick a tick, and the block's
 * {@code lit} (the dial and lamp) follows: on with charge. The charge goes to clients (the prompt's; the client plays its
 * copy down in step) and stays with the item when the radio is broken (the loot table copies "Charge").
 * <p>The crank's host (AFL Animated Block Mesh, 2026-10-15, docs/rendering/animated_block_mesh_runtime_v1.md): profile
 * emergency_radio_crank_&lt;surface&gt; (its origin lowered as far as the pieces sink), the wrapping channel 'crank' the
 * block's {@code crank} quarter / 4: a held crank steps a quarter each 4 ticks and the arm turns a quarter in 4, on and on.
 * Only the arm is in the mesh, so the renderer draws it at rest too (the block model is the cabin one).
 */
public class EmergencyRadioBlockEntity extends BlockEntity implements AflAnimatedMeshHost {
    private static final ResourceLocation[] CRANK = new ResourceLocation[EmergencyRadio.Surface.values().length];

    static {
        for (EmergencyRadio.Surface s : EmergencyRadio.Surface.values())
            CRANK[s.ordinal()] = new ResourceLocation(com.antaurora.apofirstlight.ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/emergency_radio_crank_" + s.getSerializedName() + ".json");
    }

    private final AflBlockMeshAnimationState meshAnimation = new AflBlockMeshAnimationState();
    private int charge;

    public EmergencyRadioBlockEntity(BlockPos pos, BlockState state) { super(EmergencyRadio.RADIO, pos, state); }

    public int charge() { return charge; }

    // ----------------------------------------------------------------------------------- the crank's mesh

    @Override
    public ResourceLocation meshProfile() { return CRANK[getBlockState().getValue(EmergencyRadio.Radio.SURFACE).ordinal()]; }

    @Override
    public AflBlockMeshAnimationState meshAnimation() { return meshAnimation; }

    @Override
    public Direction meshFacing() { return getBlockState().getValue(EmergencyRadio.Radio.FACING); }

    @Override
    public void refreshMeshAnimationTargets() { AflAnimatedMeshHost.refreshValueTargets(level, meshProfile(), meshAnimation, this::meshChannelValue); }

    @Override
    public boolean meshChannelTarget(String channel) { return false; }

    @Override
    public double meshChannelValue(String channel) { return "crank".equals(channel) ? getBlockState().getValue(EmergencyRadio.Radio.CRANK) / 4.0 : 0; }

    /** A small part: the hit mesh keeps the arm down. */
    @Override
    public boolean meshChannelAffectsHits(String channel) { return false; }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshMeshAnimationTargets();
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        refreshMeshAnimationTargets();
    }

    /** The cell and the crank's turn (under the cell's floor on a table). */
    @Override
    public AABB getRenderBoundingBox() { return new AABB(worldPosition).minmax(AflAnimatedMeshHost.renderBounds(worldPosition, meshProfile(), meshFacing())); }

    /** Winds `ticks` of play in (capped); a switched-on radio lights. */
    void wind(int ticks) {
        charge = Math.min(EmergencyRadio.CAPACITY, charge + ticks);
        follow(getBlockState());
        changed();
    }

    /** The switch was moved: lit follows. */
    void switched(BlockState state) {
        follow(state);
        changed();
    }

    /** Sets lit on the block when it changed (on with charge). */
    private void follow(BlockState state) {
        if (level == null || level.isClientSide || !(state.getBlock() instanceof EmergencyRadio.Radio)) return;
        BlockState next = state.setValue(EmergencyRadio.Radio.LIT, state.getValue(EmergencyRadio.Radio.ON) && charge > 0);
        if (next != state) level.setBlock(worldPosition, next, 3);
    }

    void serverTick(BlockState state) {
        if (!state.getValue(EmergencyRadio.Radio.LIT)) return;
        charge = Math.max(0, charge - 1);
        if (charge == 0) { follow(state); changed(); }
        else if (level.getGameTime() % 20 == 0) setChanged();
        // the static's noise every 2 s while it plays, as far as the client's loop carries (forest home sounds V1)
        if (level instanceof net.minecraft.server.level.ServerLevel server && server.getGameTime() % 40 == Math.floorMod(worldPosition.asLong(), 40))
            com.antaurora.apofirstlight.noise.NoiseSystem.emit(new com.antaurora.apofirstlight.noise.NoiseEvent(null, worldPosition.getCenter(),
                    com.antaurora.apofirstlight.noise.NoiseType.MACHINE, server.getGameTime(),
                    net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock()), EmergencyRadio.STATIC_RADIUS), server);
    }

    /** Client: the charge plays down here too (for the prompt and Jade between updates). */
    void clientTick(BlockState state) {
        if (state.getValue(EmergencyRadio.Radio.LIT)) charge = Math.max(0, charge - 1);
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Charge", charge);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        charge = Math.max(0, Math.min(EmergencyRadio.CAPACITY, tag.getInt("Charge")));
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
