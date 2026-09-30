package com.antaurora.apofirstlight.meshshape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by a Block (no BlockEntity needed) whose shapes come from a Mesh Shape profile. The block overrides
 * {@code getShape} with {@link #meshSelectionShape} and {@code getCollisionShape} with {@link #meshPhysicalShape};
 * dynamic assets map their block state to a profile state name, multi-block assets to a cell.
 */
public interface AflMeshShapeBlock {
    /** Profile id, e.g. {@code apocalypse_firstlight:industrial_locker} -> data/apocalypse_firstlight/mesh_shapes/industrial_locker.json. */
    ResourceLocation meshShapeProfile();

    /** Discrete shape state (e.g. "closed" / "open"); static props keep the single "default" state. */
    default String meshShapeState(BlockState state) {
        return AflMeshShapeProfile.DEFAULT_STATE;
    }

    default Direction meshShapeFacing(BlockState state) {
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    /** Vertical cell of a multi-block asset (0 = bottom). */
    default int meshShapeCell(BlockState state) {
        return 0;
    }

    default AflMeshShapeProfile.Resolved meshShape(BlockState state) {
        return AflMeshShapes.get(meshShapeProfile()).resolve(meshShapeState(state), meshShapeFacing(state), meshShapeCell(state));
    }

    default VoxelShape meshPhysicalShape(BlockState state) {
        return meshShape(state).physical();
    }

    default VoxelShape meshSelectionShape(BlockState state) {
        return meshShape(state).selection();
    }

    /**
     * Interaction region the player is aiming at on this block, from the player's own view ray (same test on client and
     * server, independent of where the reported hit point was clamped). Null when no region is aimed at.
     */
    @Nullable
    default AflMeshShapeProfile.RegionHit meshInteraction(BlockState state, BlockPos pos, Player player) {
        var eye = player.getEyePosition();
        return meshShape(state).raycast(pos, eye, eye.add(player.getViewVector(1.0F).scale(player.getBlockReach())));
    }
}
