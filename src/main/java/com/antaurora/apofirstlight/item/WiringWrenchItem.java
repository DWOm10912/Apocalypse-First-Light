package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.wiring.WiringTool;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The wiring wrench (Building Wiring V1, docs/gameplay/building_wiring_v1.md): holding it is wiring mode. Use on a block
 * lays the selected circuit's wire in it (held down and swept, one cell after another, like Terraria's wrench); attack cuts
 * (wiring.client.WiringClient turns the attack into a cut, so the wrench never mines); sneak + scroll picks the circuit.
 * It acts before the block's own use (a lampholder's pull chain, a panel's door), so devices can be wired by clicking them.
 */
public final class WiringWrenchItem extends Item {
    public WiringWrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.getPlayer() == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        return context.getPlayer() instanceof ServerPlayer player ? WiringTool.use(player, context.getClickedPos()) : InteractionResult.PASS;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }
}
