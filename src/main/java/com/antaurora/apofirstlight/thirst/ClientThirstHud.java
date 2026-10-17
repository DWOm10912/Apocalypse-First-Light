package com.antaurora.apofirstlight.thirst;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.AflRingIcon;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Thirst V1 client visuals (口渴.md §10). The thirst gauge itself is the water-drop needs ring of the HUD V2 body module
 * (client/SurvivalVitalsHud, 2026-10-16; it replaced the crescent of the survival cluster). Here: the drop glyph and the
 * water blue they share with the tooltip line, and the water bottles' liquid colours (the vanilla potion layers, tinted):
 * dirty murky olive, boiled pale blue-green with yellow-green radioactive specks (an untinted third layer), purified
 * clear blue.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientThirstHud {
    /** Liquid tints: dirty murky olive; boiled close to purified, a little greener (its specks layer shows the radiation). */
    public static final int DIRTY_WATER = 0x76703E, BOILED_WATER = 0x98D4DC, PURIFIED_WATER = 0x8FD3F0;
    private ClientThirstHud() {}

    @SubscribeEvent
    public static void itemColours(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? DIRTY_WATER : -1, AflItems.DIRTY_WATER_BOTTLE.get());
        event.register((stack, tint) -> tint == 0 ? BOILED_WATER : -1, AflItems.BOILED_WATER_BOTTLE.get());
        event.register((stack, tint) -> tint == 0 ? PURIFIED_WATER : -1, AflItems.PURIFIED_WATER_BOTTLE.get());
    }

    /** Water drop: a circle and the triangle tangent to it up to the tip (unit box, y down). Also the tooltip's icon. */
    public static final AflRingIcon.Glyph WATER_DROP = (x, y) -> Math.min(Math.hypot(x - 0.5, y - 0.62) - 0.27,
            AflRingIcon.convex(x, y, 0.5, 0.10, 0.731, 0.48, 0.269, 0.48));
    /** The ring's normal colour; the tooltip line uses it too. */
    public static final int WATER_BLUE = 0x6FA3C7;

    @SubscribeEvent
    public static void tooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(ThirstTooltip.class, ClientThirstTooltip::new);
    }
}
