package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExplosionTinnitusClientState {
    private static final ExplosionTinnitusEnvelope ENVELOPE = new ExplosionTinnitusEnvelope();
    private static final ExplosionTinnitusEnvelope OVERLAY_ENVELOPE = new ExplosionTinnitusEnvelope();
    private static ClientLevel trackedLevel;
    private static LocalPlayer trackedPlayer;
    private static ExplosionTinnitusSound activeSound;

    private ExplosionTinnitusClientState() {}

    public static void trigger(float severity, int playerId, ResourceLocation dimension) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || mc.player.isSpectator()
                || mc.player.getId() != playerId || !mc.level.dimension().location().equals(dimension)) return;
        if (trackedLevel != mc.level || trackedPlayer != mc.player) clear();
        trackedLevel = mc.level;
        trackedPlayer = mc.player;
        OVERLAY_ENVELOPE.triggerOverlay(severity);
        if (ENVELOPE.trigger(severity) == ExplosionTinnitusEnvelope.TriggerResult.RESTART) {
            // Stop in SoundManager too, before play(): never leave two OpenAL channels ringing.
            stopSound();
            activeSound = new ExplosionTinnitusSound(ENVELOPE);
            mc.getSoundManager().play(activeSound);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level != trackedLevel || mc.player != trackedPlayer
                || !mc.player.isAlive() || mc.player.isSpectator()) {
            clear();
            return;
        }
        if (mc.isPaused()) return;
        ENVELOPE.tick();
        OVERLAY_ENVELOPE.tick();
        if (!ENVELOPE.active()) stopSound();
    }

    public static float overlayAlpha(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != trackedLevel || mc.player != trackedPlayer) return 0;
        return OVERLAY_ENVELOPE.overlayAlpha(mc.isPaused() ? 0 : partialTick);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.Clone event) { clear(); }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide() && event.getLevel() == trackedLevel) clear();
    }

    private static void stopSound() {
        if (activeSound != null) {
            activeSound.stopNow();
            Minecraft.getInstance().getSoundManager().stop(activeSound);
            activeSound = null;
        }
    }

    private static void clear() {
        stopSound();
        ENVELOPE.clear();
        OVERLAY_ENVELOPE.clear();
        trackedLevel = null;
        trackedPlayer = null;
    }
}
