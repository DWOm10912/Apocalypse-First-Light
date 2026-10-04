package com.antaurora.apofirstlight.temperature;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Which temperature numbers the HUD may show (Temperature V1; numbers only, no words: "8°C", "36.4°").
 * - Air temperature: while a Source says so; the wrist thermometer in the wrist slot is one (registered here). Drawn
 *   as the arc around the temperature dial plus the number above it.
 * - Core body temperature: the clinical thermometer's reading (the core when the measurement finished) for 10 s, or
 *   continuously while a Source says so (a future survival monitor). Drawn inside the dial in place of its glyph.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TemperatureReadout {
    public interface Source {
        boolean showsCore(LocalPlayer player);
        boolean showsAmbient(LocalPlayer player);
    }
    public static final double READING_SECONDS = 10;
    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();
    private static float reading = Float.NaN;
    private static long readingAt;
    private TemperatureReadout() {}

    public static void register(Source source) { SOURCES.add(source); }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        register(new Source() {
            @Override public boolean showsCore(LocalPlayer player) { return false; }
            @Override public boolean showsAmbient(LocalPlayer player) {
                return AflEquipmentSlots.get(player, AflEquipmentSlots.WRIST).is(AflItems.WRIST_THERMOMETER.get());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> reading = Float.NaN);
    }

    /** ClinicalThermometerItem: the measurement finished for this entity (the local player only). */
    static void measured(LivingEntity entity) {
        var state = ClientTemperature.state();
        if (entity != Minecraft.getInstance().player || state == null) return;
        reading = state.core();
        readingAt = System.nanoTime();
    }

    /** The local player is holding the clinical thermometer under the arm right now. */
    static boolean measuring(LocalPlayer player) {
        return player.isUsingItem() && player.getUseItem().is(AflItems.CLINICAL_THERMOMETER.get());
    }

    static boolean ambientShown(LocalPlayer player) {
        for (var source : SOURCES) if (source.showsAmbient(player)) return true;
        return false;
    }
    /** The core value to print in the dial: live while a Source shows it, else the last reading for 10 s; NaN otherwise. */
    static double coreShown(LocalPlayer player, TemperaturePackets.State state) {
        for (var source : SOURCES) if (source.showsCore(player)) return state.core();
        if (!Float.isNaN(reading) && (System.nanoTime() - readingAt) / 1e9 < READING_SECONDS) return reading;
        return Double.NaN;
    }
}
