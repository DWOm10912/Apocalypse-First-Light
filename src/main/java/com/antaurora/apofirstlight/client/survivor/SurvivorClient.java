package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.survivor.SurvivorPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Survivor Death and Succession V0, client side (docs/gameplay/survivor_death_succession_v0.md 11, 13): replaces the
 * vanilla death screen with {@link AflDeathScreen} while the server's summary is known, keeps the one-time mode choice
 * and then the first survivor's name choice open until the server confirms them, and names client-side players after
 * their survivors (nametags). Shows what the server sent; decides nothing.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class SurvivorClient {
    @Nullable
    private static SurvivorPackets.Summary summary;
    private static boolean modePrompt;
    private static boolean namePrompt;
    /** The open setup prompt (catalog and info), or null. */
    @Nullable
    private static com.antaurora.apofirstlight.survivor.SurvivorPackets.SetupPrompt setupPrompt;

    private SurvivorClient() {
    }

    /** Packet: the dead survivor's summary (it can arrive a moment before the client sees its own death). */
    public static void summary(SurvivorPackets.Summary packet) {
        summary = packet;
        Minecraft mc = Minecraft.getInstance();
        if (DeathCamera.holdsDeathScreen()) return;   // the death camera first; the tick opens it after
        if (mc.player != null && mc.player.isDeadOrDying() && !(mc.screen instanceof AflDeathScreen)) {
            mc.setScreen(new AflDeathScreen(packet));
        }
    }

    /** The server sent the dead survivor's summary (the AFL death screen will show). */
    static boolean hasSummary() {
        return summary != null;
    }

    /** Packet: a corpse's falling or final pose (Ragdoll V1; a survivor's or, since Infected Z1a, a creature's body). */
    public static void corpsePose(SurvivorPackets.CorpsePose packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(packet.entity()) instanceof com.antaurora.apofirstlight.creature.CorpsePoseHolder corpse) {
            corpse.receivePose(packet.origin(), packet.pose(), packet.last());
        }
    }

    /** Packet: open or close the mode choice. */
    public static void modePrompt(boolean open) {
        modePrompt = open;
        Minecraft mc = Minecraft.getInstance();
        if (!open && mc.screen instanceof AflSurvivorModeScreen) mc.setScreen(null);
    }

    /** Packet: open or close the name choice, or keep it open with the reason the last name was refused. */
    public static void namePrompt(boolean open, String error) {
        namePrompt = open;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AflSurvivorNameScreen screen)) return;
        if (!open) {
            mc.setScreen(null);
        } else if (!error.isEmpty()) {
            screen.refused(error);
        }
    }

    /** Packet: open (with its data) or close the survivor setup screen. */
    public static void setupPrompt(com.antaurora.apofirstlight.survivor.SurvivorPackets.SetupPrompt packet) {
        setupPrompt = packet.open() ? packet : null;
        Minecraft mc = Minecraft.getInstance();
        if (!packet.open() && (mc.screen instanceof AflSurvivorSetupScreen || mc.screen instanceof AflAppearanceScreen)) mc.setScreen(null);
    }

    /** Packet: the server refused the confirm; the screen shows why and can send again. */
    public static void setupResult(com.antaurora.apofirstlight.survivor.SurvivorPackets.SetupResult packet) {
        if (Minecraft.getInstance().screen instanceof AflSurvivorSetupScreen screen) screen.refused(packet.problems());
    }

    /** Client display name of a player (the nametag over them): the survivor's name the server sent. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!event.getEntity().level().isClientSide) return;
        String name = SurvivorIdentityClient.name(event.getEntity().getUUID());
        if (name != null && !name.equals(event.getUsername())) event.setDisplayname(Component.literal(name));
    }

    /**
     * Before vanilla's own screen logic each tick: a dead player with a summary always looks at the AFL death screen
     * (also when the vanilla screen came first, after a relog, or with immediate respawn off and a HARD end); the mode
     * choice opens as soon as nothing else is on screen (not over the terrain loading screen), then the survivor setup
     * (which also takes the name), or the name choice alone.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (summary != null && mc.player.isDeadOrDying()) {
            if (DeathCamera.holdsDeathScreen()) return;
            if (!(mc.screen instanceof AflDeathScreen)) mc.setScreen(new AflDeathScreen(summary));
            return;
        }
        if (mc.screen != null) return;
        if (modePrompt) {
            mc.setScreen(new AflSurvivorModeScreen());
        } else if (setupPrompt != null) {
            mc.setScreen(new AflAppearanceScreen(setupPrompt));
        } else if (namePrompt) {
            mc.setScreen(new AflSurvivorNameScreen());
        }
    }

    /**
     * The vanilla death screen is swapped for the AFL one whenever the summary is known; while the death camera holds,
     * neither opens yet (Ragdoll V1: the vanilla one is kept for later when there is no summary).
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if ((event.getNewScreen() instanceof DeathScreen || event.getNewScreen() instanceof AflDeathScreen)
                && DeathCamera.holdsDeathScreen()) {
            if (event.getNewScreen() instanceof DeathScreen) DeathCamera.defer(event.getNewScreen());
            event.setCanceled(true);
            return;
        }
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
        namePrompt = false;
        setupPrompt = null;
        SurvivorSheetClient.clear();
        SurvivorIdentityClient.clear();
    }
}
