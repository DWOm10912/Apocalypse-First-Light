package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.PavementArrowBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pavement Markings V1: places a whole pavement arrow ({@link PavementArrowBlock}). The clicked cell (or the cell above the
 * clicked ground) takes the tail; the arrow points the way the player looks. Every cell must be free and on a full top face.
 */
public final class PavementArrowItem extends Item {
    private final PavementArrowBlock.Kind kind;

    public PavementArrowItem(PavementArrowBlock.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        BlockPos tail = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(context.getClickedFace());
        Direction facing = context.getHorizontalDirection();
        PavementArrowBlock block = (PavementArrowBlock) AflBlocks.PAVEMENT_ARROW.get();
        PavementArrowBlock.Cell[] cells = PavementArrowBlock.cells(kind);
        for (PavementArrowBlock.Cell cell : cells) {
            BlockPos at = PavementArrowBlock.at(tail, facing, cell);
            if (!level.getBlockState(at).canBeReplaced() || !block.part(kind, facing, cell.part()).canSurvive(level, at))
                return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            for (PavementArrowBlock.Cell cell : cells)
                level.setBlock(PavementArrowBlock.at(tail, facing, cell), block.part(kind, facing, cell.part()), Block.UPDATE_ALL);
            SoundType sound = block.defaultBlockState().getSoundType();
            level.playSound(null, tail, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            Player player = context.getPlayer();
            if (player == null || !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
