package com.antaurora.apofirstlight.boulder;

import com.antaurora.apofirstlight.earthwork.Earthwork;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Natural rock takes no hand (Rock R2, docs/gameplay/rock_r2_rock_face_mining_design.md 9.7): in survival a stone block
 * never breaks (no break progress, the break refused on the server); the rock in the open is the pick's
 * (BoulderStrike: a strike loosens it into rubble, the shovel takes that). Underground rock waits for R3. Creative breaks
 * it as before.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
public final class RockFace {
    private RockFace() {}

    @SubscribeEvent
    public static void speed(PlayerEvent.BreakSpeed e) {
        if (!e.getEntity().isCreative() && Earthwork.rock(e.getState().getBlock())) e.setNewSpeed(0);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void breaking(BlockEvent.BreakEvent e) {
        if (!e.getPlayer().isCreative() && Earthwork.rock(e.getState().getBlock())) e.setCanceled(true);
    }

    /** The first hit on rock says what it takes (server side, once per press). */
    @SubscribeEvent
    public static void hit(PlayerInteractEvent.LeftClickBlock e) {
        if (e.getLevel().isClientSide() || e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || e.getEntity().isCreative()
                || !Earthwork.rock(e.getLevel().getBlockState(e.getPos()).getBlock())) return;
        e.getEntity().displayClientMessage(Component.translatable("message.apocalypse_firstlight.pick.needs_pick"), true);
    }
}
