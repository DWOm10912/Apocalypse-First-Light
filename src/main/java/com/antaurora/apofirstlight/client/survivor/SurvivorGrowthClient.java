package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.survivor.SurvivorCatalog;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Growth on the client (docs/gameplay/survivor_background_skills_v1_plan.md 9 and 16, P3): the growth page's key (K by
 * default, in the game; the page itself closes on it too) and the level-up notice in the top left corner (the server's
 * LevelUp packet: "Strength up to Lv 5", "Pistol learned: experienced reload" at a node), with a light sound, about 4 s.
 * Experience alone shows nothing.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class SurvivorGrowthClient {
    public static final KeyMapping GROWTH = new KeyMapping("key.apocalypse_firstlight.growth", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.apocalypse_firstlight");
    private static final long SHOW_NANOS = 4_000_000_000L, FADE_IN = 200_000_000L, FADE_OUT = 500_000_000L;
    private static final int MAX_SHOWN = 3;
    private static final List<Notice> NOTICES = new ArrayList<>();

    private record Notice(Component name, Component what, long start) {
    }

    private SurvivorGrowthClient() {
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (GROWTH.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new AflGrowthScreen(false));
        }
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        NOTICES.clear();
    }

    /** The LevelUp packet: a level with a node names what was learned, otherwise the level. */
    public static void levelUp(String id, int level, boolean body) {
        Component name = Component.translatable(SurvivorCatalog.key(body ? "body" : "growth", id));
        String perk = body ? null : SurvivorSheetClient.perk(id, level);
        Component what = perk != null ? SetupCanvas.tr("growth_page.toast_perk", SetupCanvas.tr("perk." + perk))
                : SetupCanvas.tr("growth_page.toast_level", level);
        NOTICES.add(new Notice(name, what, System.nanoTime()));
        while (NOTICES.size() > MAX_SHOWN) NOTICES.remove(0);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.2F, 0.5F));
    }

    private static final IGuiOverlay OVERLAY = (gui, g, partialTick, screenWidth, screenHeight) -> render(g);

    private static void render(GuiGraphics g) {
        if (NOTICES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        NOTICES.removeIf(n -> now - n.start() > SHOW_NANOS);
        if (mc.options.hideGui) return;
        var font = mc.font;
        float y = 6;
        for (Notice n : NOTICES) {
            long age = now - n.start();
            float alpha = age < FADE_IN ? age / (float) FADE_IN : age > SHOW_NANOS - FADE_OUT ? (SHOW_NANOS - age) / (float) FADE_OUT : 1;
            alpha = Math.max(0.05F, Math.min(1, alpha));
            float slide = (1 - alpha) * -8;
            int nameW = font.width(n.name()), w = nameW + 6 + font.width(n.what()) + 12;
            float x = 6 + slide;
            AflUiShapes.box(g, x, y, w, 16, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, 0.9F * alpha, AflUiStyle.LINE,
                    AflUiStyle.LINE_ALPHA * alpha, AflUiStyle.SHADOW_ALPHA * alpha, 0);
            AflUiShapes.fill(g, x + 3, y + 4, 1.6F, 8, 0.8F, AflUiStyle.ACCENT, alpha);
            g.drawString(font, n.name(), (int) (x + 8), (int) (y + 4), AflUiStyle.argb(AflUiStyle.TEXT, alpha), false);
            g.drawString(font, n.what(), (int) (x + 8 + nameW + 6), (int) (y + 4), AflUiStyle.argb(AflUiStyle.ACCENT, alpha), false);
            y += 19;
        }
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(GROWTH);
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.CHAT_PANEL.id(), "growth_level_up", OVERLAY);
        }
    }
}
