package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Sneaking normally skips a block's use(); for a power cable clicked with an empty main hand the click goes through, so
 * PowerCableBlock#use can cut or join a side (also with something in the off hand). Fires on both sides.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class PowerCableToggleEvents {
    private PowerCableToggleEvents() {
    }

    @SubscribeEvent
    public static void allowSneakToggle(PlayerInteractEvent.RightClickBlock event) {
        if (PowerCableBlock.canToggle(event.getEntity(), event.getHand())
                && event.getLevel().getBlockState(event.getPos()).is(AflBlocks.POWER_CABLE.get()))
            event.setUseBlock(Event.Result.ALLOW);
    }
}
