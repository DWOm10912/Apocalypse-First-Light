package com.antaurora.apofirstlight.meshshape;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A Mesh Shape block with world interaction prompts: the shared client prompt (WorldInteractionHint) asks which label the
 * aimed region shows in the current state, and draws it at that region's anchor. The block's use() must apply the same
 * region -> action decision, so prompt and behaviour never disagree.
 */
public interface AflMeshInteractionBlock extends AflMeshShapeBlock {
    /** Translation key of the prompt for this region in this state, or null when the region offers nothing now. */
    @Nullable
    String interactionHintKey(Level level, BlockState state, BlockPos pos, String region);
}
