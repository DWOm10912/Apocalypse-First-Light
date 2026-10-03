package com.antaurora.apofirstlight.thirst;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import com.mojang.datafixers.util.Either;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client side of Thirst V1: the own thirst from the server (HUD, can-drink check) and the hand sip. Sneak +
 * empty main hand + right-click at a vanilla water source (not flowing water) asks the server for one sip, at most every
 * 10 ticks; looking at water with a block behind it within reach, the block is not used.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class ClientThirst {
    private static final int SIP_COOLDOWN = 10;
    private static ThirstPackets.State state;
    private static long lastSip = Long.MIN_VALUE;
    private ClientThirst() {}

    public static ThirstPackets.State state() { return state; }

    static void accept(ThirstPackets.State packet) {
        state = packet;
        PlayerThirst.clientValue = packet.value();
        PlayerThirst.clientMax = packet.max();
    }
    @SubscribeEvent public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        state = null;
        PlayerThirst.clientValue = Float.NaN;
    }

    /** Every drink (water bottles, the thirst "foods"): a water-drop icon and the amount, right under the item name. */
    @SubscribeEvent public static void tooltip(RenderTooltipEvent.GatherComponents event) {
        double amount = ThirstConfig.get().hydration(event.getItemStack());
        if (amount <= 0) return;
        var elements = event.getTooltipElements();
        elements.add(Math.min(1, elements.size()), Either.right(new ThirstTooltip(amount)));
    }

    /** True when this click is a sip (and was sent). */
    private static boolean trySip(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
                || PlayerThirst.waterSourceInView(player) == null) return false;
        if (!PlayerThirst.canDrink(player)) return false; // full: the click does whatever it would do
        long now = player.level().getGameTime();
        if (now - lastSip >= SIP_COOLDOWN) {
            lastSip = now;
            AflNetwork.thirstSip(); // the server swings the arm and plays the sound
        }
        return true;
    }
    @SubscribeEvent public static void rightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (event.getLevel().isClientSide) trySip(event.getEntity(), event.getHand());
    }
    @SubscribeEvent public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide || Minecraft.getInstance().player != event.getEntity()) return;
        if (trySip(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
