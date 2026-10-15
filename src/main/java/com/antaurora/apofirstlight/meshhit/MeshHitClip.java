package com.antaurora.apofirstlight.meshhit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Vanilla's BlockGetter#clip with hit meshes (docs/rendering/mesh_hit_runtime_v1.md): cell by cell along the ray, a block
 * with a hit mesh (MeshHitModels) stops it on the model's surface, with the true normal, or lets it through the cell
 * (between handles, past a round drum's corner); any other block, and a block whose shape for this context is empty (it
 * would not stop the ray anyway: the mesh never adds stopping power), as vanilla. A block above that reaches down into
 * the cell ({@link MeshHitReachesDown}: the emergency radio on a table) is tried too, the nearer hit wins. Fluids as vanilla.
 */
public final class MeshHitClip {
    private MeshHitClip() {
    }

    public static BlockHitResult clip(BlockGetter level, ClipContext context) {
        return BlockGetter.traverseBlocks(context.getFrom(), context.getTo(), context, (ctx, pos) -> {
            BlockState state = level.getBlockState(pos);
            FluidState fluid = level.getFluidState(pos);
            Vec3 from = ctx.getFrom(), to = ctx.getTo();
            VoxelShape shape = ctx.getBlockShape(state, level, pos);
            BlockHitResult block = cell(level, ctx, pos, state, shape, from, to);
            // a block above reaching down into this cell (MeshHitReachesDown): the nearer hit
            BlockPos up = pos.above();
            BlockState above = level.getBlockState(up);
            if (above.getBlock() instanceof MeshHitReachesDown) {
                BlockHitResult down = cell(level, ctx, up, above, ctx.getBlockShape(above, level, up), from, to);
                if (down != null && (block == null || from.distanceToSqr(down.getLocation()) < from.distanceToSqr(block.getLocation()))) block = down;
            }
            BlockHitResult liquid = ctx.getFluidShape(fluid, level, pos).clip(from, to, pos);
            double a = block == null ? Double.MAX_VALUE : from.distanceToSqr(block.getLocation());
            double b = liquid == null ? Double.MAX_VALUE : from.distanceToSqr(liquid.getLocation());
            return a <= b ? block : liquid;
        }, ctx -> {
            Vec3 back = ctx.getFrom().subtract(ctx.getTo());
            return BlockHitResult.miss(ctx.getTo(), Direction.getNearest(back.x, back.y, back.z), BlockPos.containing(ctx.getTo()));
        });
    }

    /** The ray against the block at pos: its hit mesh, else its shape as vanilla; null when it passes. */
    private static BlockHitResult cell(BlockGetter level, ClipContext ctx, BlockPos pos, BlockState state, VoxelShape shape, Vec3 from, Vec3 to) {
        if (shape.isEmpty()) return null;
        MeshHitModels.Shape mesh = MeshHitModels.shape(level, pos, state);
        if (mesh == null) return level.clipWithInteractionOverride(from, to, pos, shape, state);
        MeshBlockHitResult hit = mesh.clip(from, to, pos);
        return hit == null ? null : MeshHitModels.inCell(level, hit, state);
    }
}
