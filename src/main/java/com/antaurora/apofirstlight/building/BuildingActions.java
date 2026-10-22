package com.antaurora.apofirstlight.building;

import com.antaurora.apofirstlight.inventory.CarriedItems;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * The building hammer on the server (building hammer B0, docs/gameplay/building_hammer_b0.md): installing one piece from
 * the catalog (one finished piece taken from the body, none in Creative) and taking one down (the same piece given back,
 * none in Creative, no loot), each after BuildingRules says yes again here. Taking down works only on catalog pieces, so
 * the hammer never takes machines, terrain, furniture or anything else apart. Both come only from edit mode (client
 * BuildingHammerClient, which also keeps edit mode's left clicks from mining); out of it the hammer is a plain tool.
 */
public final class BuildingActions {
    private BuildingActions() {}

    public static boolean holdingHammer(net.minecraft.world.entity.player.Player player) {
        return player.getMainHandItem().is(AflItems.BUILDING_HAMMER.get());
    }

    public static void place(ServerPlayer player, ResourceLocation pieceId, BuildingRules.Placement p) {
        if (!holdingHammer(player)) return;
        var piece = BuildingCatalog.byId(pieceId);
        if (piece == null) return;
        var level = player.serverLevel();
        if (!level.isLoaded(p.pos())) return;
        String problem = BuildingRules.placeProblem(player, piece, p);
        var item = piece.item();
        var state = BuildingRules.placed(level, piece, p);
        if (problem != null || item == null || state == null) {
            refuse(player, problem == null ? "unknown" : problem, item);
            return;
        }
        if (!level.setBlock(p.pos(), state, Block.UPDATE_ALL)) return;
        if (!player.getAbilities().instabuild) CarriedItems.take(player, item, 1);
        state.getBlock().setPlacedBy(level, p.pos(), state, player, new ItemStack(item));
        var sound = state.getSoundType(level, p.pos(), player);
        level.playSound(null, p.pos(), sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8F);
        level.gameEvent(player, GameEvent.BLOCK_PLACE, p.pos());
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    public static void remove(ServerPlayer player, BlockPos pos) {
        if (!holdingHammer(player)) return;
        var level = player.serverLevel();
        if (!level.isLoaded(pos)) return;
        String problem = BuildingRules.removeProblem(player, pos);
        if (problem != null) {
            refuse(player, problem, null);
            return;
        }
        var state = level.getBlockState(pos);
        var piece = BuildingCatalog.byBlock(state.getBlock());
        var item = piece == null ? null : piece.item();
        if (item == null) return;
        // gone first (in water it leaves the water), then the one piece back: no loot table, so nothing doubles
        level.levelEvent(2001, pos, Block.getId(state));
        if (!level.removeBlock(pos, false)) return;
        level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
        if (!player.getAbilities().instabuild) CarriedItems.give(player, new ItemStack(item));
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static void refuse(ServerPlayer player, String reason, @org.jetbrains.annotations.Nullable net.minecraft.world.item.Item item) {
        Component name = item == null ? Component.empty() : new ItemStack(item).getHoverName();
        player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.building." + reason, name), true);
    }
}
