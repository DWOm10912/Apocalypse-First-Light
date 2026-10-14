package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity;
import com.antaurora.apofirstlight.energy.AflPowerPortBlock;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Distribution panels (Building Power V1, docs/models/building_power_v1.md): North American panels hung on a wall
 * (FACING = away from the wall, like the Industrial Electrical Box). {@link Kind#COMMERCIAL}: the commercial panelboard
 * ({@code distribution_panel}, 商业配电盘); {@link Kind#RESIDENTIAL}: the residential load center ({@code load_center},
 * 住宅配电箱, 2026-10-13, about 0.33 x 0.45 m). Same circuits, screen and rules. Power ports on the bottom face (in) and the
 * top face (out to cable devices), each the standard steel port on a pull box at the face centre; a pull box (and its
 * outline) only shows while a cable plugs into its port. Right-click opens the panel screen; OPEN (the door) stands open
 * while someone looks. Mining: pickaxe + diamond tier (industrial infrastructure), drops itself.
 */
public class DistributionPanelBlock extends HorizontalDirectionalBlock implements EntityBlock, AflPowerPortBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    /** Which panel: its mesh and its outline (canonical, facing north: front -Z, wall at z 16, px; tools/build-building-power-v1.mjs). */
    public enum Kind {
        COMMERCIAL(Block.box(3.5, 2.4, 13.6, 12.5, 13.6, 16),
                Shapes.or(Block.box(5, 0, 5, 11, 2.0, 11), Block.box(7.4, 1.0, 10.5, 8.6, 2.6, 15.0)),
                Shapes.or(Block.box(5, 14.0, 5, 11, 16, 11), Block.box(7.4, 13.4, 10.5, 8.6, 15.0, 15.0))),
        RESIDENTIAL(Block.box(5.36, 4.8, 14.1, 10.64, 12.0, 16),
                Shapes.or(Block.box(5, 0, 5, 11, 2.0, 11), Block.box(7.4, 1.0, 10.5, 8.6, 5.0, 15.4)),
                Shapes.or(Block.box(5, 14.0, 5, 11, 16, 11), Block.box(7.4, 11.8, 10.5, 8.6, 15.0, 15.4)));

        /** By the pull boxes shown: 0 none, 1 below, 2 above, 3 both. */
        private final List<Map<Direction, VoxelShape>> shapes = new ArrayList<>();

        Kind(VoxelShape body, VoxelShape below, VoxelShape above) {
            for (int k = 0; k < 4; k++) {
                VoxelShape s = body;
                if ((k & 1) != 0) s = Shapes.or(s, below);
                if ((k & 2) != 0) s = Shapes.or(s, above);
                shapes.add(HorizontalShapeUtils.rotations(s));
            }
        }
    }

    private final Kind kind;

    public DistributionPanelBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    public Kind kind() {
        return kind;
    }

    /** A cable plugs into the panel's bottom (or top) port: that pull box shows. */
    public static boolean cabled(BlockGetter level, BlockPos pos, Direction side) {
        BlockState next = level.getBlockState(pos.relative(side));
        return next.getBlock() instanceof PowerCableBlock && PowerCableBlock.isConnected(next, side.getOpposite());
    }

    @Override
    public boolean hasPowerPort(BlockState state, Direction face) {
        return face == Direction.DOWN || face == Direction.UP;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (!face.getAxis().isHorizontal()) return null;
        BlockState state = defaultBlockState().setValue(FACING, face);
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos wall = pos.relative(facing.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (!level.isClientSide() && player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof DistributionPanelBlockEntity panel) {
            NetworkHooks.openScreen(server, panel, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int k = (cabled(level, pos, Direction.DOWN) ? 1 : 0) | (cabled(level, pos, Direction.UP) ? 2 : 0);
        return kind.shapes.get(k).get(state.getValue(FACING));
    }

    /** Moving, glass and lit parts: the AFL Animated Block Mesh Runtime renderer; resting parts: the chunk model (client/blockmesh/AflStaticMeshModel, docs/dev/render_performance_v1.md). */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DistributionPanelBlockEntity(pos, state);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != AflBlockEntities.DISTRIBUTION_PANEL.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<DistributionPanelBlockEntity>) (l, p, s, panel) -> panel.serverTick();
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }
}
