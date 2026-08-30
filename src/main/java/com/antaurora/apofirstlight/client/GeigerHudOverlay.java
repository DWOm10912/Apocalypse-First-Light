package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.config.GeigerHudConfig;
import com.antaurora.apofirstlight.client.config.GeigerHudConfigManager;
import com.antaurora.apofirstlight.radiation.RadiationZone;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Locale;

public final class GeigerHudOverlay {
    private static final ResourceLocation BACKGROUND = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/geiger_hud.png");
    private static final ResourceLocation SYMBOL = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/radiation_symbol.png");
    private static final int WIDTH = 128;
    private static final int HEIGHT = 48;
    private static final int MARGIN = 8;

    private GeigerHudOverlay() {}

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, width, height) -> render(graphics, width, height);

    private static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || (!minecraft.player.getMainHandItem().is(AflItems.GEIGER_COUNTER.get())
                && !minecraft.player.getOffhandItem().is(AflItems.GEIGER_COUNTER.get()))) return;
        GeigerHudConfig config = GeigerHudConfigManager.get();
        if (!config.enabled()) return;

        int x = Math.round(screenWidth - WIDTH * config.hudScale() - MARGIN - config.offsetX());
        int y = Math.round(screenHeight - HEIGHT * config.hudScale() - MARGIN - config.offsetY());
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(config.hudScale(), config.hudScale(), 1.0F);
        graphics.blit(BACKGROUND, 0, 0, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
        pose.pushPose();
        pose.translate(config.symbol().x(), config.symbol().y(), 0);
        pose.scale(config.symbol().scale(), config.symbol().scale(), 1.0F);
        graphics.blit(SYMBOL, 0, 0, 0, 0, 18, 18, 18, 18);
        pose.popPose();
        pose.popPose();

        ClientGeigerData.Snapshot data = ClientGeigerData.snapshot();
        boolean residualMode = data.zone() == RadiationZone.SAFE;
        double measuredRate = residualMode ? data.residualRadiationRate() : data.currentRate();
        String rateValue = measuredRate >= ClientGeigerData.GEIGER_MAX_RATE
                ? Component.translatable("hud.apocalypse_firstlight.geiger.over_range").getString()
                : String.format(Locale.ROOT, "%.2f RU/h", measuredRate);
        String rateLabel = residualMode ? "hud.apocalypse_firstlight.geiger.residual"
                : "hud.apocalypse_firstlight.geiger.ambient";
        String rate = Component.translatable(rateLabel).getString() + ": " + rateValue;
        String dose = String.format(Locale.ROOT, "%s: %.4f RU",
                Component.translatable("hud.apocalypse_firstlight.geiger.dose").getString(), data.cumulativeDose());
        String zone = Component.translatable("hud.apocalypse_firstlight.geiger.zone").getString() + ": "
                + Component.translatable(zoneKey(data.zone())).getString();
        drawText(graphics, minecraft.font, rate, x, y, config, config.rows().radiation(), 0);
        drawText(graphics, minecraft.font, dose, x, y, config, config.rows().dose(), 1);
        drawText(graphics, minecraft.font, zone, x, y, config, config.rows().zone(), 2);
    }

    private static void drawText(GuiGraphics graphics, Font font, String text, float hudX, float hudY,
                                 GeigerHudConfig config, GeigerHudConfig.RowOffset row, int index) {
        float x = hudX + (config.text().x() + row.offsetX()) * config.hudScale();
        float y = hudY + (config.text().y() + config.text().lineSpacing() * index + row.offsetY()) * config.hudScale();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(config.text().fontScale(), config.text().fontScale(), 1.0F);
        graphics.drawString(font, text, 0, 0, 0xE8F2C4, false);
        pose.popPose();
    }

    private static String zoneKey(RadiationZone zone) {
        return "radiation_zone.apocalypse_firstlight." + zone.name().toLowerCase(Locale.ROOT);
    }
}
