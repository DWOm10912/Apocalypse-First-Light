package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.AABB;

/**
 * Steel-frame doors V1 (docs/models/steel_frame_doors_v1.md), drawn by the AFL Animated Block Mesh Runtime
 * (docs/rendering/animated_block_mesh_runtime_v1.md): one profile per hinge side (tools/build-steel-frame-doors-v1.mjs,
 * '<id>_right' authored with the hinge at -X, '<id>_left' its mirror), bones 'frame' / 'leaf', the channel 'open' following
 * the door's OPEN. Lives on the lower half only; the block keeps the state, the shapes and the interaction. The mesh is
 * authored with the face toward the placer at -Z, so it turns with FACING.getOpposite() (a vanilla door's FACING is the way
 * the placer looked).
 */
public abstract class SteelFrameDoorBlockEntity extends AflAnimatedMeshBlockEntity {
    protected SteelFrameDoorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, String id) {
        super(type, pos, state, new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/" + id + "_"
                + (state.hasProperty(DoorBlock.HINGE) && state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT ? "left" : "right") + ".json"));
    }

    @Override
    protected boolean meshAnimationTarget(String channel) {
        BlockState state = getBlockState();
        return state.hasProperty(DoorBlock.OPEN) && state.getValue(DoorBlock.OPEN);
    }

    @Override
    public Direction meshFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(DoorBlock.FACING) ? state.getValue(DoorBlock.FACING).getOpposite() : Direction.SOUTH;
    }

    /**
     * Both halves, a block of room on every side for the swinging leaf and the drip cap. Kept explicit (not the profile's
     * bounds) so it is the same on the server, where the mesh profiles are not loaded.
     */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.getX() - 1.0, worldPosition.getY() - 0.125, worldPosition.getZ() - 1.0,
                worldPosition.getX() + 2.0, worldPosition.getY() + 2.125, worldPosition.getZ() + 2.0);
    }
}
