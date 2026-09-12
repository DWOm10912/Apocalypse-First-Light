package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.CommercialGlassDoubleDoorBlockEntity;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CommercialGlassDoubleDoorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    private static final Map<Direction, VoxelShape> LOWER_LEFT_CLOSED = HorizontalShapeUtils.rotations(lower(false, false));
    private static final Map<Direction, VoxelShape> LOWER_RIGHT_CLOSED = HorizontalShapeUtils.rotations(lower(true, false));
    private static final Map<Direction, VoxelShape> LOWER_LEFT_OPEN = HorizontalShapeUtils.rotations(lower(false, true));
    private static final Map<Direction, VoxelShape> LOWER_RIGHT_OPEN = HorizontalShapeUtils.rotations(lower(true, true));
    private static final Map<Direction, VoxelShape> UPPER_LEFT_CLOSED = HorizontalShapeUtils.rotations(upper(false, false));
    private static final Map<Direction, VoxelShape> UPPER_RIGHT_CLOSED = HorizontalShapeUtils.rotations(upper(true, false));
    private static final Map<Direction, VoxelShape> UPPER_LEFT_OPEN = HorizontalShapeUtils.rotations(upper(false, true));
    private static final Map<Direction, VoxelShape> UPPER_RIGHT_OPEN = HorizontalShapeUtils.rotations(upper(true, true));
    private static final Set<BlockPos> REMOVING = new HashSet<>();
    private static final Set<BlockPos> SUPPORT_REMOVING = new HashSet<>();

    public CommercialGlassDoubleDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(PART, Part.LOWER_LEFT));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedFace() != Direction.UP) {
            return null;
        }
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos anchor = context.getClickedPos();
        if (!canPlaceStructure(context, anchor, facing)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, Part.LOWER_LEFT);
    }

    private boolean canPlaceStructure(BlockPlaceContext context, BlockPos anchor, Direction facing) {
        Level level = context.getLevel();
        BlockPos width = anchor.relative(widthDirection(facing));
        for (Part doorPart : Part.values()) {
            BlockPos part = partPosition(anchor, facing, doorPart);
            if (!level.hasChunkAt(part) || !level.getWorldBorder().isWithinBounds(part)
                    || part.getY() >= level.getMaxBuildHeight()
                    || !level.getBlockState(part).canBeReplaced(BlockPlaceContext.at(context, part, Direction.UP))
                    || !level.getFluidState(part).isEmpty()
                    || !level.isUnobstructed(defaultBlockState().setValue(FACING, facing).setValue(PART, doorPart), part,
                    CollisionContext.empty())) return false;
            Player player = context.getPlayer();
            if (player != null && (!level.mayInteract(player, part)
                    || !player.mayUseItemAt(part, Direction.UP, context.getItemInHand()))) return false;
        }
        return level.getBlockState(anchor.below()).isFaceSturdy(level, anchor.below(), Direction.UP)
                && level.getBlockState(width.below()).isFaceSturdy(level, width.below(), Direction.UP);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        boolean open = state.getValue(OPEN);
        BlockState base = defaultBlockState().setValue(FACING, facing).setValue(OPEN, open);
        Direction width = widthDirection(facing);
        for (Part part : List.of(Part.LOWER_RIGHT, Part.UPPER_LEFT, Part.UPPER_RIGHT)) {
            BlockPos peer = partPosition(position, facing, part);
            if (!level.setBlock(peer, base.setValue(PART, part), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE)) {
                removeParts(level, position, facing, null);
                return;
            }
        }
        for (BlockPos peer : partPositions(position, facing)) level.updateNeighborsAt(peer, this);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos position) {
        BlockPos anchor = anchorPosition(position, state);
        if (state.getValue(PART).isUpper()) {
            BlockState lower = level.getBlockState(position.below());
            return lower.is(this) && lower.getValue(FACING) == state.getValue(FACING)
                    && lower.getValue(OPEN) == state.getValue(OPEN);
        }
        Direction width = widthDirection(state.getValue(FACING));
        return level.getBlockState(anchor.below()).isFaceSturdy(level, anchor.below(), Direction.UP)
                && level.getBlockState(anchor.relative(width).below())
                .isFaceSturdy(level, anchor.relative(width).below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && state.getValue(PART).isLower()
                && !neighborState.isFaceSturdy(level, neighborPos, Direction.UP)) {
            BlockPos anchor = anchorPosition(currentPos, state);
            if (SUPPORT_REMOVING.add(anchor.immutable())) {
                try {
                    if (level instanceof Level serverLevel && !serverLevel.isClientSide()) {
                        Block.popResource(serverLevel, anchor, new ItemStack(AflItems.COMMERCIAL_GLASS_DOUBLE_DOOR.get()));
                    }
                    removeParts(level, anchor, state.getValue(FACING), null);
                } finally {
                    SUPPORT_REMOVING.remove(anchor);
                }
            }
            return Blocks.AIR.defaultBlockState();
        }
        if (state.getValue(PART).isUpper() && direction == Direction.DOWN && !neighborState.is(this)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        BlockPos anchor = anchorPosition(position, state);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockState anchorState = level.getBlockState(anchor);
        if (!anchorState.is(this) || anchorState.getValue(PART) != Part.LOWER_LEFT
                || anchorState.getValue(FACING) != state.getValue(FACING)) {
            return InteractionResult.PASS;
        }
        BlockEntity anchorEntity = level.getBlockEntity(anchor);
        if (!(anchorEntity instanceof CommercialGlassDoubleDoorBlockEntity door)
                || !door.canToggle(level.getGameTime())) return InteractionResult.CONSUME;
        boolean oldOpen = anchorState.getValue(OPEN);
        boolean open = !oldOpen;
        Direction facing = anchorState.getValue(FACING);
        Direction width = widthDirection(facing);
        for (Part part : Part.values()) {
            BlockState expected = level.getBlockState(partPosition(anchor, facing, part));
            if (!expected.is(this) || expected.getValue(PART) != part
                    || expected.getValue(FACING) != facing || expected.getValue(OPEN) != oldOpen)
                return InteractionResult.CONSUME;
        }
        for (BlockPos partPosition : partPositions(anchor, facing)) {
            BlockState partState = level.getBlockState(partPosition);
            level.setBlock(partPosition, partState.setValue(OPEN, open), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
        }
        door.markToggled(level.getGameTime());
        door.triggerDoorAnimation(open);
        level.playSound(null, anchor.getX() + width.getStepX() * 0.5 + 0.5,
                anchor.getY() + 1.0, anchor.getZ() + width.getStepZ() * 0.5 + 0.5,
                open ? AflSounds.GLASS_DOOR_OPEN.get() : AflSounds.GLASS_DOOR_CLOSE.get(),
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        for (BlockPos partPosition : partPositions(anchor, facing)) level.updateNeighborsAt(partPosition, this);
        return InteractionResult.CONSUME;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        BlockPos anchor = anchorPosition(position, state);
        if (!level.isClientSide() && !player.isCreative() && !state.getValue(PART).isLowerLeft()
                && player.getMainHandItem().isCorrectToolForDrops(state)) {
            Block.popResource(level, anchor, new ItemStack(AflItems.COMMERCIAL_GLASS_DOUBLE_DOOR.get()));
        }
        removeParts(level, anchor, state.getValue(FACING), position);
        super.playerWillDestroy(level, position, state, player);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        return state.getValue(PART).isLowerLeft() ? super.getDrops(state, builder) : List.of();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState newState, boolean movedByPiston) {
        BlockPos anchor = anchorPosition(position, state);
        if (!state.is(newState.getBlock()) && !REMOVING.contains(anchor)) {
            removeParts(level, anchor, state.getValue(FACING), position);
        }
        super.onRemove(state, level, position, newState, movedByPiston);
    }

    private static void removeParts(LevelAccessor level, BlockPos anchor, Direction facing, @Nullable BlockPos keep) {
        BlockPos canonicalAnchor = anchor.immutable();
        if (!REMOVING.add(canonicalAnchor)) {
            return;
        }
        try {
            for (BlockPos partPosition : partPositions(canonicalAnchor, facing)) {
                if (keep != null && partPosition.equals(keep)) {
                    continue;
                }
                if (level.getBlockState(partPosition).getBlock() instanceof CommercialGlassDoubleDoorBlock) {
                    level.setBlock(partPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        } finally {
            REMOVING.remove(canonicalAnchor);
        }
    }

    private static List<BlockPos> partPositions(BlockPos anchor, Direction facing) {
        Direction width = widthDirection(facing);
        return List.of(anchor, anchor.relative(width), anchor.above(), anchor.above().relative(width));
    }

    private static Direction widthDirection(Direction facing) {
        return facing.getClockWise();
    }

    private static BlockPos anchorPosition(BlockPos position, BlockState state) {
        Direction width = widthDirection(state.getValue(FACING));
        return switch (state.getValue(PART)) {
            case LOWER_LEFT -> position;
            case LOWER_RIGHT -> position.relative(width.getOpposite());
            case UPPER_LEFT -> position.below();
            case UPPER_RIGHT -> position.below().relative(width.getOpposite());
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return partShape(state);
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos position) {
        return partShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position,
                                        CollisionContext context) {
        return partShape(state);
    }

    private static VoxelShape partShape(BlockState state) {
        boolean open = state.getValue(OPEN);
        Map<Direction, VoxelShape> shapes = switch (state.getValue(PART)) {
            case LOWER_LEFT -> open ? LOWER_LEFT_OPEN : LOWER_LEFT_CLOSED;
            case LOWER_RIGHT -> open ? LOWER_RIGHT_OPEN : LOWER_RIGHT_CLOSED;
            case UPPER_LEFT -> open ? UPPER_LEFT_OPEN : UPPER_LEFT_CLOSED;
            case UPPER_RIGHT -> open ? UPPER_RIGHT_OPEN : UPPER_RIGHT_CLOSED;
        };
        return shapes.get(state.getValue(FACING));
    }

    private static VoxelShape lower(boolean right, boolean open) {
        double start = right ? 7.95 : 0.0, end = right ? 16.0 : 8.05;
        VoxelShape fixed = Block.box(start, 0, 6, end, 16, 10);
        VoxelShape leaf = open
                ? Block.box(right ? 7.3 : 7.95, 0.26, 8, right ? 8.05 : 8.7, 16, 16)
                : Block.box(right ? 0.05 : 8.05, 0.26, 7.2, right ? 7.95 : 15.95, 16, 8.8);
        return Shapes.or(fixed, leaf).optimize();
    }

    private static VoxelShape upper(boolean right, boolean open) {
        double start = right ? 7.95 : 0.0, end = right ? 16.0 : 8.05;
        VoxelShape fixed = Shapes.or(Block.box(start, 0, 6, end, 16, 10),
                Block.box(0, 14, 6, 16, 16, 10));
        VoxelShape leaf = open
                ? Block.box(right ? 7.3 : 7.95, 0, 8, right ? 8.05 : 8.7, 13.85, 16)
                : Block.box(right ? 0.05 : 8.05, 0, 7.2, right ? 7.95 : 15.95, 13.85, 8.8);
        return Shapes.or(fixed, leaf).optimize();
    }

    private static BlockPos partPosition(BlockPos anchor, Direction facing, Part part) {
        BlockPos result = part == Part.LOWER_RIGHT || part == Part.UPPER_RIGHT
                ? anchor.relative(widthDirection(facing)) : anchor;
        return part.isUpper() ? result.above() : result;
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
    @Nullable
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return state.getValue(PART).isLowerLeft()
                ? new CommercialGlassDoubleDoorBlockEntity(position, state) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, PART);
    }

    public enum Part implements StringRepresentable {
        LOWER_LEFT("lower_left"),
        LOWER_RIGHT("lower_right"),
        UPPER_LEFT("upper_left"),
        UPPER_RIGHT("upper_right");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public boolean isLower() {
            return this == LOWER_LEFT || this == LOWER_RIGHT;
        }

        public boolean isUpper() {
            return !isLower();
        }

        public boolean isLowerLeft() {
            return this == LOWER_LEFT;
        }
    }
}
