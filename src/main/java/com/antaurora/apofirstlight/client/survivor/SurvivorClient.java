package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.survivor.SurvivorPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Survivor Death and Succession V0, client side (docs/gameplay/survivor_death_succession_v0.md 11): replaces the
 * vanilla death screen with {@link AflDeathScreen} while the server's summary is known, and keeps the one-time mode
 * choice open until the server confirms it. Shows what the server sent; decides nothing.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class SurvivorClient {
    @Nullable
    private static SurvivorPackets.Summary summary;
    private static boolean modePrompt;

    private SurvivorClient() {
    }

    /** Packet: the dead survivor's summary (it can arrive a moment before the client sees its own death). */
    public static void summary(SurvivorPackets.Summary packet) {
        summary = packet;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isDeadOrDying() && !(mc.screen instanceof AflDeathScreen)) {
            mc.setScreen(new AflDeathScreen(packet));
        }
    }

    /** Packet: open or close the mode choice. */
    public static void modePrompt(boolean open) {
        modePrompt = open;
        Minecraft mc = Minecraft.getInstance();
        if (!open && mc.screen instanceof AflSurvivorModeScreen) mc.setScreen(null);
    }

    /**
     * Before vanilla's own screen logic each tick: a dead player with a summary always looks at the AFL death screen
     * (also when the vanilla screen came first, after a relog, or with immediate respawn off and a HARD end); the mode
     * choice opens as soon as nothing else is on screen (not over the terrain loading screen).
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (summary != null && mc.player.isDeadOrDying()) {
            if (!(mc.screen instanceof AflDeathScreen)) mc.setScreen(new AflDeathScreen(summary));
            return;
        }
        if (modePrompt && mc.screen == null) mc.setScreen(new AflSurvivorModeScreen());
    }

    /** The vanilla death screen is swapped for the AFL one whenever the summary is known. */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (summary == null || !(event.getNewScreen() instanceof DeathScreen)) return;
        if (event.getCurrentScreen() instanceof AflDeathScreen) {
            event.setCanceled(true);
        } else {
            event.setNewScreen(new AflDeathScreen(summary));
        }
    }

    /** The respawn arrived: the successor plays, the summary is done. */
    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        summary = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AflDeathScreen) mc.setScreen(null);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        summary = null;
        modePrompt = false;
    }
}
