package com.antaurora.apofirstlight.meshhit;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A block whose model is put together from pieces at run time (a dynamic baked model: the fluid pipes) names the pieces'
 * models itself, the same ones its client model picks (docs/rendering/mesh_hit_runtime_v1.md). Pieces are not turned.
 */
public interface MeshHitProvider {
    List<ResourceLocation> meshHitModels(BlockGetter level, BlockPos pos, BlockState state);
}
