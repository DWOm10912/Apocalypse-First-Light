package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Lightning V2 (docs/gameplay/lightning_v2_plan.md 4 A): a modern copper air terminal, real size (tools/
 * build-lightning-rods-v1.mjs, model V2): a cast bronze base, a 5/8" x 36" copper rod 976 mm overall braced by a copper
 * tripod, its down conductor running along the roof to the cell edge on the FACING side (away from the placing player). It stands on anything with a centre to stand on, has no
 * collision (a thin rod) and takes strikes near it (LightningRodTip).
 */
public class CopperAirTerminalBlock extends HorizontalDirectionalBlock implements LightningRodTip {
    /** The tip, 976 mm up. */
    public static final double TIP = 0.976;
    // the generator's SELECTION boxes, the thin rod widened to 1 px for aiming, the brace's feet as one thin pad
    private static final VoxelShape NORTH = Shapes.or(Block.box(7.04, 0, 7.04, 8.96, 1.1, 8.96), Block.box(7.5, 0, 7.5, 8.5, 15.62, 8.5),
            Block.box(7.7, 7.74, 7.7, 8.3, 8.26, 8.3), Block.box(2.7, 0, 2.7, 13.3, 0.25, 13.3), Block.box(7.6, 0, 0, 8.4, 0.7, 7.5));
    private static final Map<Direction, VoxelShape> SHAPES = HorizontalShapeUtils.rotations(NORTH);

    public CopperAirTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction from, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return from == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, from, neighbour, level, pos, neighbourPos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public double tip(BlockState state) {
        return TIP;
    }
}
