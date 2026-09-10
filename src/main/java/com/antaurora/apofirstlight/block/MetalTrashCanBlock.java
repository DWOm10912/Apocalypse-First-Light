package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Static city prop. Container and lid behavior are intentionally deferred. */
public final class MetalTrashCanBlock extends Block {
    private static final VoxelShape COLLISION_SHAPE = Shapes.or(
            Block.box(2.2, 0.35, 3.0, 13.8, 14.45, 13.0),
            Block.box(3.0, 0.35, 2.2, 13.0, 14.45, 13.8),
            Block.box(1.75, 14.45, 3.0, 14.25, 15.55, 13.0),
            Block.box(3.0, 14.45, 1.75, 13.0, 15.55, 14.25),
            Block.box(6.1, 15.55, 6.9, 9.9, 17.2, 9.1)
    ).optimize();
    private static final VoxelShape OUTLINE_SHAPE =
            Block.box(1.4, 0.35, 1.75, 14.6, 17.2, 14.25);

    public MetalTrashCanBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return OUTLINE_SHAPE;
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos position) {
        return OUTLINE_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position,
                                        CollisionContext context) {
        return COLLISION_SHAPE;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos position) {
        return Shapes.empty();
    }
}
