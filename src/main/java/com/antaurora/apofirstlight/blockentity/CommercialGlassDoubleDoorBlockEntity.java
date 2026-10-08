package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.CommercialGlassDoubleDoorBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Commercial Glass Double Door V2 (docs/models/commercial_glass_double_door_v2.md), drawn by the AFL Animated Block Mesh
 * Runtime (docs/rendering/animated_block_mesh_runtime_v1.md): one mesh, a profile per finish (silver / black,
 * tools/build-commercial-glass-double-door-v2.mjs), bones 'leaf_a' / 'leaf_b' on the channel 'open', which follows the
 * block's OPEN. Lives on the lower-left part only; the block keeps the state, the shapes and the interaction.
 */
public class CommercialGlassDoubleDoorBlockEntity extends AflAnimatedMeshBlockEntity {
    public static final ResourceLocation PROFILE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/commercial_glass_double_door.json");
    public static final ResourceLocation PROFILE_BLACK =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/commercial_glass_double_door_black.json");
    private long lastToggleTick = -100;

    public CommercialGlassDoubleDoorBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.COMMERCIAL_GLASS_DOUBLE_DOOR.get(), position, state,
                state.is(AflBlocks.COMMERCIAL_GLASS_DOUBLE_DOOR_BLACK.get()) ? PROFILE_BLACK : PROFILE);
    }

    /** The one channel 'open': both leaves swing with the block's OPEN. */
    @Override
    protected boolean meshAnimationTarget(String channel) {
        BlockState state = getBlockState();
        return state.hasProperty(CommercialGlassDoubleDoorBlock.OPEN) && state.getValue(CommercialGlassDoubleDoorBlock.OPEN);
    }

    /**
     * Both columns and both levels, with a block of room in depth for the leaves swinging out. Kept explicit (not the
     * profile's bounds) so it is the same on the server, where the mesh profiles are not loaded.
     */
    @Override
    public AABB getRenderBoundingBox() {
        Direction width = getBlockState().getValue(CommercialGlassDoubleDoorBlock.FACING).getClockWise();
        BlockPos otherHalf = worldPosition.relative(width);
        return new AABB(
                Math.min(worldPosition.getX(), otherHalf.getX()) - 1.0,
                worldPosition.getY() - 0.125,
                Math.min(worldPosition.getZ(), otherHalf.getZ()) - 1.0,
                Math.max(worldPosition.getX(), otherHalf.getX()) + 2.0,
                worldPosition.getY() + 2.125,
                Math.max(worldPosition.getZ(), otherHalf.getZ()) + 2.0);
    }

    public boolean canToggle(long tick) { return tick - lastToggleTick >= 12; }
    public void markToggled(long tick) { lastToggleTick = tick; }
}
