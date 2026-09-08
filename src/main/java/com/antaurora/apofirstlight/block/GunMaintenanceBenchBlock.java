package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;

public final class GunMaintenanceBenchBlock extends StaticWorkstationBlock implements EntityBlock {
    public GunMaintenanceBenchBlock(Properties properties) { super(properties); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART)==Part.BASE ? new GunMaintenanceBenchBlockEntity(pos,state) : null;
    }
    @Override protected InteractionResult useAtRoot(Level level, BlockPos root, Player player, InteractionHand hand) {
        if(player instanceof ServerPlayer server && level.getBlockEntity(root) instanceof GunMaintenanceBenchBlockEntity bench)
            NetworkHooks.openScreen(server,bench,root);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if(!state.equals(replacement) && !level.isClientSide
                && level.getBlockEntity(rootPosition(pos,state)) instanceof GunMaintenanceBenchBlockEntity bench) {
            // Clear before peer teardown re-enters onRemove; every multiblock part shares this one slot.
            var gun=bench.removeItemNoUpdate(0);
            if(!gun.isEmpty())Containers.dropItemStack(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,gun);
        }
        super.onRemove(state,level,pos,replacement,moved);
    }
}
