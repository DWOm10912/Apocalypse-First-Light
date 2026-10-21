package com.antaurora.apofirstlight.tree;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mesh Trees P2 (docs/rendering/mesh_trees_p2_felling.md, B): the client's half of chopping. A left click on a standing
 * tree's trunk base (the cells P0-B makes pickable) with an axe starts a swing, and so does holding the button there,
 * the next as soon as the last is over. Since 2026-10-20 a swing is {@value FellingRules#CHOP_TICKS} ticks
 * (FellingSwingClient plays it at once; the felling axe's skeleton chop, docs/gameplay/felling_axe_v1.md 5); the server
 * lands it {@value FellingRules#IMPACT_TICKS} ticks in and decides everything else, answering with block changes, sounds
 * and particles. The attack key itself is taken there (survival and creative alike), so vanilla never mines the cell:
 * no hit sound, no hit particles, no arm swing over the chop (2026-10-20, user). Without an axe: a short hint, and
 * vanilla's endless zero-progress mining.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT)
public final class FellingClient {
    // game time is never negative; Long.MIN_VALUE here overflowed "now - last" (first P2 test)
    private static long lastHint = -1000;

    private FellingClient() {}

    /** Every tick the attack key is pressed or held at a block (Minecraft.startAttack / continueAttack). */
    @SubscribeEvent
    public static void attackKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() || event.getHand() != InteractionHand.MAIN_HAND || !TreeBlocks.ready()) return;
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null || player.isSpectator()) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (!FellingServer.axe(player.getMainHandItem())) return;
        if (FellingServer.choppable(mc.level, hit.getBlockPos(), TreeIndex.CLIENT) == null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!FellingSwingClient.busy(player)) {
            FellingSwingClient.start(player);
            TreeNetwork.sendChop(hit.getBlockPos());
        }
    }

    /** Without an axe the attack reaches vanilla mining: say what is missing. */
    @SubscribeEvent
    public static void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() || !TreeBlocks.ready()) return;
        var player = event.getEntity();
        if (FellingServer.axe(player.getMainHandItem())) return;
        if (FellingServer.choppable(event.getLevel(), event.getPos(), TreeIndex.CLIENT) == null) return;
        long now = event.getLevel().getGameTime();
        if (now - lastHint > 60 || now < lastHint) {
            lastHint = now;
            player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.felling.need_axe"), true);
        }
    }
}
