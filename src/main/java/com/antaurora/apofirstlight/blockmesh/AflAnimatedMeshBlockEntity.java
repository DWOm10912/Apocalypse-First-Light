package com.antaurora.apofirstlight.blockmesh;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/** Optional base for animated mesh BEs; authority and interaction remain in the concrete block. See AflAnimatedMeshHost. */
public abstract class AflAnimatedMeshBlockEntity extends BlockEntity implements AflAnimatedMeshHost {
    private final ResourceLocation meshProfile;
    private final AflBlockMeshAnimationState meshAnimation = new AflBlockMeshAnimationState();

    protected AflAnimatedMeshBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                         ResourceLocation meshProfile) {
        super(type, pos, state);
        this.meshProfile = meshProfile;
    }

    public final ResourceLocation meshProfile() { return meshProfile; }
    public final AflBlockMeshAnimationState meshAnimation() { return meshAnimation; }
    protected abstract boolean meshAnimationTarget(String channel);
    @Override public final boolean meshChannelTarget(String channel) { return meshAnimationTarget(channel); }

    public Direction meshFacing() {
        var state = getBlockState();
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    @Override public void onLoad() {
        super.onLoad();
        refreshMeshAnimationTargets();
    }

    @Override public void setBlockState(BlockState state) {
        super.setBlockState(state);
        refreshMeshAnimationTargets(); // Includes authoritative updates arriving offscreen.
    }

    /** NBT-driven targets must also call this after applying update tag / BE packet data. */
    public final void refreshMeshAnimationTargets() {
        AflAnimatedMeshHost.refreshValueTargets(level, meshProfile, meshAnimation, this::meshChannelValue);
    }

    @Override public AABB getRenderBoundingBox() {
        return AflAnimatedMeshHost.renderBounds(worldPosition, meshProfile, meshFacing());
    }
}
