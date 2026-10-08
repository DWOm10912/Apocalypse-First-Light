package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.PowerStripBlockEntity;
import com.antaurora.apofirstlight.energy.PowerPlugs;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
 * Power Strip (插线板, Power Outlets V1, docs/models/power_outlets_v1.md): the 3-outlet and the 2x3 (6-outlet) strip in
 * black ABS, 1.5 x real size, on the floor or on the office desk (LOWERED, as the desktop props). Its own cord ends in a
 * plug that goes into a wall outlet (sneak + empty hand: take the plug; right-click an outlet with it); the lit rocker
 * (right-click) is ON, LIT while it has power. Its sockets take the plug-in appliances' plugs (right-click with one in hand). Baked OBJ per state (tools/build-power-outlets-v1.mjs); the block entity
 * keeps the plug's place and its renderer draws the cord. No collision; any tool or the hand breaks it, drops itself.
 */
public class PowerStripBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty ON = BooleanProperty.create("on");
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final BooleanProperty LOWERED = OfficeDesktopDecorationBlock.LOWERED;
    public static final double DESK_SINK = -2.5;   // px
    private final int outlets;
    private final Map<Direction, VoxelShape> shapes, loweredShapes;

    public PowerStripBlock(Properties properties, int outlets) {
        super(properties);
        this.outlets = outlets;
        VoxelShape north = outlets == 3 ? Block.box(5.3, 0, 7.15, 10.7, 1.25, 8.85) : Block.box(4.9, 0, 6.5, 11.1, 1.25, 9.5);
        shapes = HorizontalShapeUtils.rotations(north);
        loweredShapes = HorizontalShapeUtils.rotations(north.move(0, DESK_SINK / 16, 0));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, true).setValue(LIT, false).setValue(LOWERED, false));
    }

    public int outlets() { return outlets; }

    /** Where the cord leaves the strip (the grommet's end), world coordinates; the cord leaves along {@link #cordDirection}. */
    public Vec3 cordExit(BlockPos pos, BlockState state) {
        double x = outlets == 3 ? 2.616 : 3.048, y = 0.432 + (state.getValue(LOWERED) ? DESK_SINK : 0);
        return WallOutletBlock.local(pos, state.getValue(FACING), x, y, 0);
    }

    /**
     * Socket face centres, px in the facing-north frame (tools/build-power-outlets-v1.mjs STRIP: x, z; the faces' front at
     * {@link #FACE_Y}), and toward their slots along Z (+1 / -1): one row on the 3-outlet strip, two rows on the 2x3
     * (ground holes toward the long edges, slots toward the middle).
     */
    private static final double[][] SOCKETS_3 = {{-1.392, 0, 1}, {-0.192, 0, 1}, {1.008, 0, 1}};
    private static final double[][] SOCKETS_6 = {{-1.44, -0.576, 1}, {-0.144, -0.576, 1}, {1.152, -0.576, 1}, {-1.44, 0.576, -1}, {-0.144, 0.576, -1}, {1.152, 0.576, -1}};
    public static final double FACE_Y = 0.9168;

    private double[] socket(int i) { return (outlets == 3 ? SOCKETS_3 : SOCKETS_6)[Math.max(0, Math.min(outlets - 1, i))]; }

    public Vec3 socketPoint(BlockPos pos, BlockState state, int i) {
        double[] s = socket(i);
        return WallOutletBlock.local(pos, state.getValue(FACING), s[0], FACE_Y + (state.getValue(LOWERED) ? DESK_SINK : 0), s[1]);
    }

    public Vec3 socketUp(BlockState state, int i) {
        return com.antaurora.apofirstlight.energy.PlugCord.dir(state.getValue(FACING), 0, 0, socket(i)[2]);
    }

    public static Vec3 cordDirection(BlockState state) {
        Vec3 a = WallOutletBlock.local(BlockPos.ZERO, state.getValue(FACING), 0, 0, 0), b = WallOutletBlock.local(BlockPos.ZERO, state.getValue(FACING), 16, 0, 0);
        return b.subtract(a);
    }

    private static boolean onDesk(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof ModernOfficeDeskBlock;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(LOWERED, onDesk(context.getLevel(), context.getClickedPos()));
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (onDesk(level, pos)) return true;
        BlockPos floor = pos.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN) {
            if (!state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
            return state.setValue(LOWERED, onDesk(level, pos));
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof PowerStripBlockEntity strip) || !(player instanceof ServerPlayer server)) return InteractionResult.PASS;
        BlockPos carried = PowerPlugs.carried(player);
        // an appliance's plug in hand: into one of the strip's sockets
        if (!player.isShiftKeyDown() && carried != null && !carried.equals(pos)) return PowerPlugs.plugCarried(level, pos, hit, server);
        // sneaking: the strip's own plug
        if (player.isShiftKeyDown()) return PowerPlugs.useDevice(level, pos, server);
        level.setBlock(pos, state.cycle(ON).setValue(LIT, false), 3);
        strip.updateLit();
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, state.getValue(ON) ? 1.1F : 1.4F);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PowerStripBlockEntity strip) strip.release();
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(LOWERED) ? loweredShapes : shapes).get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerStripBlockEntity(pos, state);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != AflBlockEntities.POWER_STRIP.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<PowerStripBlockEntity>) (l, p, s, strip) -> strip.serverTick();
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
        builder.add(FACING, ON, LIT, LOWERED);
    }
}
