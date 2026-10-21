package com.antaurora.apofirstlight.client.settings;

import com.antaurora.apofirstlight.client.FirstPersonBodyConfig;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;

import java.util.List;

/**
 * AFL settings (Mods &gt; Apocalypse First Light &gt; Config; docs/vehicles/first_pickup_v0_design.md 8.12): the client's own
 * choices in the overlay style (docs/ui/afl_overlay_ui_style_v1.md), each a row of options, the chosen one in the accent.
 * Stored in config/apocalypse_firstlight-client.toml (FirstPersonBodyConfig) the moment it is clicked. V1: the vehicle
 * dashboard's style and its speed unit (user 2026-10-16: both dashboards in the game, the player picks).
 */
public final class AflSettingsScreen extends Screen {
    private static final float PANEL_W = 300, PAD = 14, ROW_H = 22, GAP = 6, BUTTON_H = 20, LABEL_W = 96;

    /** A setting row under its section's heading (a new section starts where the key changes). */
    private record Row(String section, String key, ForgeConfigSpec.EnumValue<?> value, List<Enum<?>> choices) {}

    private static final List<Row> ROWS = List.of(
            new Row("vehicles", "dash_style", FirstPersonBodyConfig.DASH_STYLE, List.of(FirstPersonBodyConfig.DashStyle.values())),
            new Row("vehicles", "speed_unit", FirstPersonBodyConfig.SPEED_UNIT, List.of(FirstPersonBodyConfig.SpeedUnit.values())),
            new Row("body", "wound_effect", FirstPersonBodyConfig.WOUND_EFFECT, List.of(FirstPersonBodyConfig.WoundEffect.values())),
            new Row("map", "minimap_turn", FirstPersonBodyConfig.MINIMAP_TURN, List.of(FirstPersonBodyConfig.MinimapTurn.values())),
            new Row("map", "compass", FirstPersonBodyConfig.COMPASS, List.of(FirstPersonBodyConfig.CompassBar.values())));
    /** Each row's top this frame (render), for the clicks. */
    private final float[] rowTops = new float[ROWS.size()];

    private final Screen parent;
    private float panelX, rowsY, buttonY, innerW;

    public AflSettingsScreen(Screen parent) {
        super(Component.translatable("screen.apocalypse_firstlight.settings.title"));
        this.parent = parent;
    }

    /** Mod constructor, client only: the Mods list's Config button opens this screen. */
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new AflSettingsScreen(parent)));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (minecraft != null && minecraft.level == null) renderDirtBackground(g);
        g.fillGradient(0, 0, width, height, AflUiStyle.argb(0x0A0B0C, 0.55F), AflUiStyle.argb(0x0A0B0C, 0.80F));
        float panelW = Math.min(PANEL_W, width - 32);
        innerW = panelW - 2 * PAD;
        int sections = 0;
        for (int i = 0; i < ROWS.size(); i++) if (i == 0 || !ROWS.get(i).section().equals(ROWS.get(i - 1).section())) sections++;
        float panelH = 2 * PAD + font.lineHeight + 10 + sections * (font.lineHeight + 6) + (sections - 1) * 4 + ROWS.size() * (ROW_H + GAP) + 8 + BUTTON_H;
        panelX = (width - panelW) / 2;
        float y = Math.max(8, (height - panelH) / 2);
        AflUiDraw.panel(g, panelX, y, panelW, panelH, 1.0F);
        y += PAD;
        AflUiDraw.centred(g, font, title, width / 2.0F, y, AflUiStyle.TEXT, 1.0F);
        y += font.lineHeight + 10;
        rowsY = y;
        for (int i = 0; i < ROWS.size(); i++) {
            Row row = ROWS.get(i);
            if (i == 0 || !row.section().equals(ROWS.get(i - 1).section())) {
                if (i > 0) y += 4;
                AflUiDraw.text(g, font, Component.translatable("screen.apocalypse_firstlight.settings." + row.section()), panelX + PAD, y, AflUiStyle.TEXT_DIM, 1.0F);
                y += font.lineHeight + 6;
            }
            rowTops[i] = y;
            row(g, row, y, mouseX, mouseY);
            y += ROW_H + GAP;
        }
        y += 8;
        buttonY = y;
        boolean hover = inside(mouseX, mouseY, panelX + PAD, buttonY, innerW, BUTTON_H);
        float sink = AflUiDraw.button(g, panelX + PAD, buttonY, innerW, BUTTON_H, hover, false, true, 1.0F);
        AflUiDraw.centred(g, font, Component.translatable("gui.done"), width / 2.0F, buttonY + sink + (BUTTON_H - font.lineHeight) / 2.0F + 1,
                AflUiStyle.TEXT, 1.0F);
    }

    /** A setting: its name on the left, its choices side by side on the right; the chosen one carries the accent. */
    private void row(GuiGraphics g, Row row, float y, int mouseX, int mouseY) {
        AflUiDraw.text(g, font, Component.translatable("screen.apocalypse_firstlight.settings." + row.key()), panelX + PAD,
                y + (ROW_H - font.lineHeight) / 2.0F + 1, AflUiStyle.TEXT, 1.0F);
        float x = panelX + PAD + LABEL_W, w = (innerW - LABEL_W - (row.choices().size() - 1) * 4) / row.choices().size();
        Object current = row.value().get();
        for (Enum<?> choice : row.choices()) {
            boolean chosen = choice == current, hover = inside(mouseX, mouseY, x, y, w, ROW_H);
            AflUiShapes.box(g, x, y, w, ROW_H, AflUiStyle.RADIUS_TILE,
                    hover ? AflUiStyle.BACK_HOVER : AflUiStyle.BACK, hover ? AflUiStyle.BACK_HOVER_ALPHA : AflUiStyle.BACK_ALPHA,
                    chosen ? AflUiStyle.ACCENT : AflUiStyle.LINE, chosen ? 0.9F : hover ? AflUiStyle.LINE_HOVER_ALPHA : AflUiStyle.LINE_ALPHA, 0, 0);
            AflUiDraw.centred(g, font, choiceName(row, choice), x + w / 2, y + (ROW_H - font.lineHeight) / 2.0F + 1,
                    chosen ? AflUiStyle.ACCENT : AflUiStyle.TEXT_DIM, 1.0F);
            x += w + 4;
        }
    }

    private static Component choiceName(Row row, Enum<?> choice) {
        return Component.translatable("screen.apocalypse_firstlight.settings." + row.key() + "." + choice.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        for (int i = 0; i < ROWS.size(); i++) {
            Row row = ROWS.get(i);
            float y = rowTops[i];
            float x = panelX + PAD + LABEL_W, w = (innerW - LABEL_W - (row.choices().size() - 1) * 4) / row.choices().size();
            for (Enum<?> choice : row.choices()) {
                if (inside(mouseX, mouseY, x, y, w, ROW_H)) {
                    ((ForgeConfigSpec.EnumValue) row.value()).set(choice);
                    row.value().save();
                    return true;
                }
                x += w + 4;
            }
        }
        if (inside(mouseX, mouseY, panelX + PAD, buttonY, innerW, BUTTON_H)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
