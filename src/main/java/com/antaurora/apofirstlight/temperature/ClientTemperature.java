package com.antaurora.apofirstlight.temperature;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client side of Temperature V1: the own state from the server. The dial's inputs (coldness, heat, danger, trend)
 * come precomputed from the server's numbers, so the client needs no copy of the temperature data file; core and
 * ambient temperatures are kept for a future readout device (TemperatureReadout).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class ClientTemperature {
    private static TemperaturePackets.State state;
    private ClientTemperature() {}

    public static TemperaturePackets.State state() { return state; }
    /** 0 at normal core temperature, 1 at full ice blue. */
    public static double coldness() { return state == null ? 0 : state.coldness(); }
    /** 0 at normal core temperature, 1 at full orange. */
    public static double heat() { return state == null ? 0 : state.heat(); }
    /** 0..1 how far into the extreme (damage-bound) range the core is. */
    public static double danger() { return state == null ? 0 : state.danger(); }
    /** −1 cooling .. +1 warming: where the core is heading. */
    public static double trend() { return state == null ? 0 : state.trend(); }
    /** −1 .. +1: where the core is heading in this place, on the dial's scale (heat − cold of the target temperature). */
    public static double target() { return state == null ? 0 : state.target(); }
    /** Weapon sway amplitude × this while shivering (NativeWeaponSway). */
    public static float swayScale() { return state == null ? 1f : state.swayScale(); }

    static void accept(TemperaturePackets.State packet) { state = packet; }

    @SubscribeEvent public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) { state = null; }
}
