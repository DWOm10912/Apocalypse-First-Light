package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.RuralMailboxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Rural Driveway V1 roadside mailbox (docs/models/rural_driveway_v1.md, tools/build-rural-driveway-v1.mjs): a standard
 * black steel rural mailbox on a 4 x 4 wood post, 1 cell, 2 high (the box sits at 1.07 m). FACING points at the placing
 * player: the door faces the road. Right-click any cell to open it: 5 slots (a hopper screen), plain storage, not a
 * searched container; the door drops open while anyone has it open (OPEN, on the master). Sneak-use with an empty hand
 * raises or lowers the red flag (FLAG, on every cell). The bottom cell is the master: block entity, drop and contents.
 */
public class RuralMailboxBlock extends RectMultiblockBlock<RuralMailboxBlock.Cell> implements EntityBlock {
    public enum Cell implements StringRepresentable, RectMultiblockBlock.GridCell {
        R0, R1;

        @Override public int col() { return 0; }
        @Override public int row() { return ordinal(); }
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final EnumProperty<Cell> CELL = EnumProperty.create("cell", Cell.class);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty FLAG = BooleanProperty.create("flag");
    // structure frame px (the generator's MAILBOX mm + 500, x 0.016): the post, the cradle board, the box
    private static final double[][] BOXES = {
            {7.288, 0, 8.248, 8.712, 16.56, 9.672},
            {6.88, 16.48, 4.64, 9.12, 17.12, 11.36},
            {6.68, 17.12, 4.136, 9.32, 20.78, 11.864},
    };

    public RuralMailboxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CELL, Cell.R0)
                .setValue(OPEN, false).setValue(FLAG, false));
    }

    @Override protected EnumProperty<Cell> cellProperty() { return CELL; }
    @Override protected Cell[] cells() { return Cell.values(); }
    @Override public Cell master() { return Cell.R0; }
    @Override protected double[][] boxes() { return BOXES; }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        BlockPos master = masterPosition(pos, state);
        if (player.isSecondaryUseActive()) {
            if (!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
            boolean flag = !state.getValue(FLAG);
            BlockPos origin = origin(pos, state);
            Direction facing = state.getValue(FACING);
            for (Cell cell : Cell.values()) {
                BlockPos at = cellPosition(origin, facing, cell);
                BlockState other = level.getBlockState(at);
                if (other.is(this) && other.getValue(CELL) == cell) level.setBlock(at, other.setValue(FLAG, flag), UPDATE_CLIENTS);
            }
            level.playSound(player, master.above(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.35F, flag ? 1.5F : 1.35F);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && level.getBlockEntity(master) instanceof RuralMailboxBlockEntity mailbox) player.openMenu(mailbox);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The structure check, then (on the master) the openers recheck the block entity schedules. */
    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        if (level.getBlockEntity(pos) instanceof RuralMailboxBlockEntity mailbox) mailbox.recheckOpen();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean movedByPiston) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof RuralMailboxBlockEntity mailbox) {
            Containers.dropContents(level, pos, mailbox);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, replacement, movedByPiston);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMaster(state) ? new RuralMailboxBlockEntity(pos, state) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CELL, OPEN, FLAG);
    }
}
