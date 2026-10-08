package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.energy.PowerPlugs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Wall Outlet (墙上插座, Power Outlets V1, docs/models/power_outlets_v1.md): a NA duplex receptacle on a white plate, 1.5 x
 * real size, hung on a wall only (FACING = away from the wall); a floor-cell outlet sits 0.35 m up, one in the cell above
 * a counter 1.35 m. A static baked OBJ (tools/build-power-outlets-v1.mjs), no block entity: UPPER / LOWER only record
 * which socket a plug is in. Power comes over the building's hidden wiring (energy/PowerPlugs). No collision; any tool
 * or the hand breaks it (a small plastic fitting), drops itself.
 */
public class WallOutletBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");
    public static final BooleanProperty LOWER = BooleanProperty.create("lower");
    /** px (facing north, the generator's frame): plate centre height, socket offset, the faces' front plane (from the cell's -Z side). */
    public static final double CENTRE_Y = 5.6, PITCH = 0.4572, FACE_Z = 15.8168;
    private static final Map<Direction, VoxelShape> SHAPES = HorizontalShapeUtils.rotations(Block.box(6.9, 3.4, 15.5, 9.1, 7.8, 16));

    public WallOutletBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(UPPER, false).setValue(LOWER, false));
    }

    public static boolean used(BlockState state, int socket) {
        return state.getValue(socket == 0 ? UPPER : LOWER);
    }

    public static BlockState withUsed(BlockState state, int socket, boolean used) {
        return state.setValue(socket == 0 ? UPPER : LOWER, used);
    }

    /** Turns a facing-north offset from the cell centre (px) to the block's facing; returns world coordinates. */
    public static Vec3 local(BlockPos pos, Direction facing, double x, double y, double z) {
        double rx, rz;
        switch (facing) {
            case EAST -> { rx = -z; rz = x; }
            case SOUTH -> { rx = -x; rz = -z; }
            case WEST -> { rx = z; rz = -x; }
            default -> { rx = x; rz = z; }
        }
        return new Vec3(pos.getX() + 0.5 + rx / 16, pos.getY() + y / 16, pos.getZ() + 0.5 + rz / 16);
    }

    /** The centre of a socket's face (0 upper, 1 lower), world coordinates; the plug's axis is FACING. */
    public static Vec3 socket(BlockPos pos, BlockState state, int socket) {
        return local(pos, state.getValue(FACING), 0, CENTRE_Y + (socket == 0 ? PITCH : -PITCH), FACE_Z - 8);
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
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        return PowerPlugs.useOutlet(level, pos, state, player, hit);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
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
        builder.add(FACING, UPPER, LOWER);
    }
}
