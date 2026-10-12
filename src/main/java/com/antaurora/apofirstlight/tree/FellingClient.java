package com.antaurora.apofirstlight.tree;

import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Mesh Trees P2 (docs/rendering/mesh_trees_p2_felling.md, B): the client's half of chopping. A left click on a standing
 * tree's trunk base (the cells P0-B makes pickable) with an axe sends a chop, and so does holding the button there,
 * every {@value FellingRules#CHOP_TICKS} ticks (survival: vanilla's hold events; creative: its repeated starts, which
 * are cancelled so the cell is not broken). The usual arm swing plays; the server decides everything else and answers
 * with block changes, sounds and particles. Without an axe: a short hint, and vanilla's endless zero-progress mining.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT)
public final class FellingClient {
    // game time is never negative; Long.MIN_VALUE here overflowed "now - last" and no chop was ever sent (first P2 test)
    private static long lastSent = -1000, lastHint = -1000;

    private FellingClient() {}

    @SubscribeEvent
    public static void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() || !TreeBlocks.ready()) return;
        if (FellingServer.choppable(event.getLevel(), event.getPos(), TreeIndex.CLIENT) == null) return;
        var player = event.getEntity();
        long now = event.getLevel().getGameTime();
        if (!FellingServer.axe(player.getMainHandItem())) {
            if (now - lastHint > 60 || now < lastHint) {
                lastHint = now;
                player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.felling.need_axe"), true);
            }
            return;
        }
        if (player.isCreative()) event.setCanceled(true);
        if (now - lastSent >= FellingRules.CHOP_TICKS || now < lastSent) {
            lastSent = now;
            TreeNetwork.sendChop(event.getPos());
        }
    }
}
