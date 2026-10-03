package com.antaurora.apofirstlight.thirst;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.AflRingIcon;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.client.FieldAttachmentViewState;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Temporary Thirst V1 HUD until the survival HUD is designed (口渴.md §10): a ring right next to the load bar's left end
 * (the stamina ring sits further left), always shown in Survival / Adventure, filling clockwise from the top with the
 * thirst, a water drop in it (client/AflRingIcon). Muted water blue, amber below 30, red below 15; eased like the load
 * bar. Also the water bottles' liquid colours (the vanilla potion layers, tinted): dirty murky olive, boiled pale
 * blue-green with yellow-green radioactive specks (an untinted third layer), purified clear blue.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientThirstHud {
    private static final double RADIUS = 5.5, BAND = 1.5, OFFSET_X = 9;
    private static final double EASE_SECONDS = 0.12;
    /** Liquid tints: dirty murky olive; boiled close to purified, a little greener (its specks layer shows the radiation). */
    public static final int DIRTY_WATER = 0x76703E, BOILED_WATER = 0x98D4DC, PURIFIED_WATER = 0x8FD3F0;
    private static double shown = Double.NaN;
    private static long lastNanos;
    private ClientThirstHud() {}

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "thirst_ring", OVERLAY);
    }

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

    static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        var mc = Minecraft.getInstance();
        var state = ClientThirst.state();
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.25, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (state == null || state.max() <= 0 || mc.player == null) { shown = Double.NaN; return; }
        if (mc.options.hideGui || !gui.shouldDrawSurvivalElements() || FieldAttachmentViewState.isActive()) return;
        double fraction = Math.max(0, Math.min(1, state.value() / state.max()));
        shown = Double.isNaN(shown) ? fraction : shown + (fraction - shown) * (1 - Math.exp(-dt / EASE_SECONDS));
        var c = ThirstConfig.get();
        double value = shown * state.max();
        int colour = value < c.dehydratedBelow ? 0xBE443A : value < c.thirstyBelow ? 0xD6983A : WATER_BLUE;
        AflRingIcon.draw(graphics, screenWidth / 2.0 - 91 - OFFSET_X, screenHeight - 29.5, RADIUS, BAND, shown, colour, 1.0,
                WATER_DROP);
    };
}
