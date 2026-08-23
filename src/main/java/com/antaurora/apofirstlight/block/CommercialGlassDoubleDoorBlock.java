package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
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
import java.util.Set;

public class CommercialGlassDoubleDoorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    private static final VoxelShape SHAPE_NORTH = Block.box(0, 0, 0, 16, 16, 2);
    private static final VoxelShape SHAPE_SOUTH = Block.box(0, 0, 14, 16, 16, 16);
    private static final VoxelShape SHAPE_WEST = Block.box(0, 0, 0, 2, 16, 16);
    private static final VoxelShape SHAPE_EAST = Block.box(14, 0, 0, 16, 16, 16);
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
        BlockPos upper = anchor.above();
        BlockPos upperWidth = width.above();
        if (!level.getBlockState(anchor).canBeReplaced(context)
                || !level.getBlockState(width).canBeReplaced(context)
                || !level.getBlockState(upper).canBeReplaced(context)
                || !level.getBlockState(upperWidth).canBeReplaced(context)) {
            return false;
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
        level.setBlock(position.relative(width), base.setValue(PART, Part.LOWER_RIGHT), Block.UPDATE_ALL);
        level.setBlock(position.above(), base.setValue(PART, Part.UPPER_LEFT), Block.UPDATE_ALL);
        level.setBlock(position.above().relative(width), base.setValue(PART, Part.UPPER_RIGHT), Block.UPDATE_ALL);
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
        boolean clickedOpen = state.getValue(OPEN);
        if (level.isClientSide()) {
            ApocalypseFirstLight.LOGGER.debug(
                    "[AFL GLASS DOOR DEBUG] use side=CLIENT clickedPart={} resolvedAnchor={} openOld={} openNew={} authoritativeToggle=SERVER",
                    position, anchor, clickedOpen, !clickedOpen);
            return InteractionResult.SUCCESS;
        }
        BlockState anchorState = level.getBlockState(anchor);
        if (!anchorState.is(this)) {
            return InteractionResult.PASS;
        }
        boolean oldOpen = anchorState.getValue(OPEN);
        boolean open = !oldOpen;
        ApocalypseFirstLight.LOGGER.debug(
                "[AFL GLASS DOOR DEBUG] use side=SERVER clickedPart={} resolvedAnchor={} openOld={} openNew={}",
                position, anchor, oldOpen, open);
        Direction facing = anchorState.getValue(FACING);
        for (BlockPos partPosition : partPositions(anchor, facing)) {
            BlockState partState = level.getBlockState(partPosition);
            if (partState.is(this)) {
                level.setBlock(partPosition, partState.setValue(OPEN, open), Block.UPDATE_ALL);
            }
        }
        BlockEntity anchorEntity = level.getBlockEntity(anchor);
        if (anchorEntity instanceof CommercialGlassDoubleDoorBlockEntity door) {
            door.triggerDoorAnimation(open);
        } else {
            ApocalypseFirstLight.LOGGER.debug(
                    "[AFL GLASS DOOR DEBUG] triggerAnim side=SERVER missingMasterBE anchor={} actualBE={}",
                    anchor, anchorEntity == null ? "null" : anchorEntity.getClass().getName());
        }
        level.playSound(null, anchor,
                open ? AflSounds.GLASS_DOOR_OPEN.get() : AflSounds.GLASS_DOOR_CLOSE.get(),
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.CONSUME;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        BlockPos anchor = anchorPosition(position, state);
        if (!level.isClientSide() && !player.isCreative() && !state.getValue(PART).isLowerLeft()) {
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
        return doorPlaneShape(state);
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos position) {
        return doorPlaneShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position,
                                        CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : doorPlaneShape(state);
    }

    private static VoxelShape doorPlaneShape(BlockState state) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_NORTH;
        };
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
