package com.antaurora.apofirstlight.block;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Dry wall-hung lavatory, one cell (Restroom Fixtures V2, docs/models/restroom_fixtures_v2.md): real size, rim 0.84 m,
 * exposed chrome trap and stops; a wall mirror can hang in the cell above. The boxes are the generator's
 * (tools/build-restroom-fixtures-v2.mjs SHAPES.lav, which checks every vertex lies inside one).
 * <p>
 * V1 was two cells tall. {@link #HALF} stays so saved worlds keep loading: {@code half=upper} is an obsolete leftover,
 * invisible, empty, replaceable and dropping nothing, which removes itself on its next update or random tick. Only the
 * lower half is ever placed.
 */
public final class CommercialWallMountedSinkBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape NORTH = Shapes.or(
            // the china deck and apron, the basin underside, the backsplash
            Block.box(3.75, 11.7, 8.5, 12.25, 13.5, 16.0),
            Block.box(4.8, 10.8, 9.4, 11.2, 11.8, 15.0),
            Block.box(3.75, 13.4, 15.3, 12.25, 15.15, 16.0),
            // faucet, the trap and its arm into the wall, the two angle stops with their supplies
            Block.box(7.35, 13.4, 12.6, 8.65, 15.4, 15.65),
            Block.box(7.4, 7.4, 11.3, 8.6, 11.0, 16.0),
            Block.box(5.55, 8.1, 14.6, 10.45, 11.9, 16.0)
    ).optimize();
    private static final Map<Direction, VoxelShape> SHAPES = HorizontalShapeUtils.rotations(NORTH);

    public CommercialWallMountedSinkBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    private static boolean legacyUpper(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos position, BlockPos neighborPosition) {
        if (legacyUpper(state)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbor, level, position, neighborPosition);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return legacyUpper(state);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
        if (legacyUpper(state)) level.removeBlock(position, false);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return legacyUpper(state) || super.canBeReplaced(state, context);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        return legacyUpper(state) ? List.of() : super.getDrops(state, builder);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return legacyUpper(state) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return legacyUpper(state) ? Shapes.empty() : SHAPES.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position,
                                        CollisionContext context) {
        return getShape(state, level, position, context);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos position) {
        return Shapes.empty();
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }
}
