package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.FuelCanopyColumnBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Places a fuel canopy column; aimed at the top of a straight island curb, the column's base takes that curb's place
 * (its model draws the curb segment, the port in its bottom; FuelCanopyColumnBlock gives the curb back when it goes).
 */
public final class FuelCanopyColumnItem extends BlockItem {
    public FuelCanopyColumnItem(FuelCanopyColumnBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos curb = context.getClickedPos().below();
        BlockState under = level.getBlockState(curb);
        if (!context.canPlace() || !under.is(AflBlocks.FUEL_ISLAND_CURB.get())) return super.place(context);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player != null && (!level.mayInteract(player, curb) || !player.mayUseItemAt(curb, Direction.UP, stack))) return InteractionResult.FAIL;
        BlockState state = ((FuelCanopyColumnBlock) getBlock()).embedded(level, curb, under.getValue(HorizontalDirectionalBlock.FACING));
        if (!level.setBlock(curb, state, Block.UPDATE_ALL_IMMEDIATE)) return InteractionResult.FAIL;
        if (player instanceof ServerPlayer serverPlayer) CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, curb, stack);
        SoundType sound = state.getSoundType(level, curb, player);
        level.playSound(player, curb, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        level.gameEvent(GameEvent.BLOCK_PLACE, curb, GameEvent.Context.of(player, state));
        if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
