package com.antaurora.apofirstlight.inventory;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * AFL has no off hand (docs/ui/afl_inventory_grid_v0_plan.md 16): the player's actions and guns are two-handed, and an
 * item in the left hand would fight them. In every game mode the swap key (F) does nothing, nothing can be put into the
 * off hand (PlayerStorageCapacity#rejects: clicks, number keys, shift-clicks, Forge insertion, pickups), and whatever
 * still gets there (an older save, a command, the creative inventory) goes back into the inventory the next tick, the
 * way a closed menu gives its cursor back (grids in Survival / Adventure), dropped at the feet only when nothing has room.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class NoOffHand {
    private NoOffHand() {}

    @SubscribeEvent
    public static void swapHands(LivingSwapItemsEvent.Hands event) {
        if (event.getEntity() instanceof Player) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Inventory inventory = player.getInventory();
        ItemStack off = inventory.offhand.get(0);
        if (off.isEmpty() || player.isSpectator() || !player.isAlive()) return;
        if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND) player.stopUsingItem();
        inventory.offhand.set(0, ItemStack.EMPTY);
        inventory.placeItemBackInInventory(off);
    }
}
