package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Square LED Panel Light (方形面板灯, registry ID industrial_utility_light, Building Lights V1, docs/models/building_lights_v1.md):
 * a 600 x 600 mm flat panel in a 64 mm white surface kit, a Pure Mesh OBJ (tools/build-building-lights-v1.mjs). On the
 * ceiling (FACING down); walls stay allowed for the buildings that already hang the old light there (the linear light is
 * the wall light). LIT while the building's lighting circuit has power ({@link BuildingLightBlock}). Industrial salvage:
 * pickaxe + diamond tier; it drops itself when its support goes (and in explosions, IndustrialMaterialExplosionDrops).
 * No collision.
 */
public class IndustrialUtilityLightBlock extends BuildingLightBlock {
    public static final DirectionProperty FACING = DirectionProperty.create("facing", direction -> direction != Direction.UP);
    // the outline (tools/build-building-lights-v1.mjs SELECTION.panel), thickened to 1.6 px so the thin panel is easy to aim at
    private static final VoxelShape CEILING_SHAPE = Block.box(3.2, 14.4, 3.2, 12.8, 16, 12.8);
    private static final VoxelShape NORTH_SHAPE = Block.box(3.2, 3.2, 14.4, 12.8, 12.8, 16);
    private static final VoxelShape SOUTH_SHAPE = Block.box(3.2, 3.2, 0, 12.8, 12.8, 1.6);
    private static final VoxelShape EAST_SHAPE = Block.box(0, 3.2, 3.2, 1.6, 12.8, 12.8);
    private static final VoxelShape WEST_SHAPE = Block.box(14.4, 3.2, 3.2, 16, 12.8, 12.8);
    private static final Set<BlockPos> PLAYER_DESTROYING = new HashSet<>();
    private static final Set<BlockPos> EXPLOSION_DESTROYING = new HashSet<>();

    public IndustrialUtilityLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(LIT, false));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        if (clickedFace == Direction.UP || !canAttach(context.getLevel(), context.getClickedPos(), clickedFace)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, clickedFace);
    }

    @Override
    public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos position) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPosition = position.relative(facing.getOpposite());
        return level.getBlockState(supportPosition).isFaceSturdy(level, supportPosition, facing);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite()
                && !neighborState.isFaceSturdy(level, neighborPos, state.getValue(FACING))) {
            if (!PLAYER_DESTROYING.contains(currentPos)
                    && !EXPLOSION_DESTROYING.remove(currentPos.immutable())
                    && level instanceof Level serverLevel
                    && !serverLevel.isClientSide()) {
                popResource(serverLevel, currentPos, new ItemStack(AflItems.INDUSTRIAL_UTILITY_LIGHT.get()));
            }
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case DOWN -> CEILING_SHAPE;
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> Shapes.empty();
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        PLAYER_DESTROYING.add(position);
        try {
            if (!player.isCreative() && player.getMainHandItem().isCorrectToolForDrops(state)) {
                popResource(level, position, new ItemStack(AflItems.INDUSTRIAL_UTILITY_LIGHT.get()));
            }
            super.playerWillDestroy(level, position, state, player);
        } finally {
            PLAYER_DESTROYING.remove(position);
        }
    }

    public static void markExplosion(BlockPos position) {
        EXPLOSION_DESTROYING.add(position.immutable());
    }

    public static void clearExplosionMarks() {
        EXPLOSION_DESTROYING.clear();
    }

    private static boolean canAttach(Level level, BlockPos position, Direction facing) {
        BlockPos supportPosition = position.relative(facing.getOpposite());
        return level.getBlockState(supportPosition).isFaceSturdy(level, supportPosition, facing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }
}
