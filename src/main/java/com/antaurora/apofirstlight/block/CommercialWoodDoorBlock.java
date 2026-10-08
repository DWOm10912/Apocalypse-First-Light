package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.CommercialWoodDoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Commercial Wood Door (`commercial_wood_door`, zh "商业木门"). Steel-frame doors V1 (2026-10-07,
 * docs/models/steel_frame_doors_v1.md): the charcoal hollow-metal frame of the Steel Door with a light maple veneer leaf,
 * drawn by the AFL Animated Block Mesh Runtime (CommercialWoodDoorBlockEntity on the lower half; the block model is
 * particle-only). A vanilla door (wood sounds, opened by hand and by redstone) whose leaf hangs at the face toward the
 * placer and swings away from the placer inside its own cell, so from the room it was placed from it sits flush with the
 * wall face. STYLE (plain / restroom / vision) is appearance only: an empty-hand sneak use cycles it on both halves; the
 * drop is always the plain item.
 */
public class CommercialWoodDoorBlock extends DoorBlock implements EntityBlock {
    public static final EnumProperty<CommercialDoorStyle> STYLE = EnumProperty.create("style", CommercialDoorStyle.class);

    public CommercialWoodDoorBlock(Properties properties, BlockSetType type) {
        super(properties, type);
        registerDefaultState(defaultBlockState().setValue(STYLE, CommercialDoorStyle.PLAIN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STYLE);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            if (!level.isClientSide()) {
                // the other half takes the new look in updateShape
                level.setBlock(pos, state.setValue(STYLE, state.getValue(STYLE).next()), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.6F, 1.2F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    /** Vanilla keeps facing / open / hinge / powered in step between the halves; STYLE too. */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        BlockState updated = super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
        DoubleBlockHalf half = state.getValue(HALF);
        if (updated.is(this) && direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)
                && neighborState.is(this) && neighborState.getValue(HALF) != half) {
            updated = updated.setValue(STYLE, neighborState.getValue(STYLE));
        }
        return updated;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SteelFrameDoorShapes.shape(state, true);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** The lower half draws the whole door. */
    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new CommercialWoodDoorBlockEntity(pos, state) : null;
    }
}
