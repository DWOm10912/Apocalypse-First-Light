package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.survivor.SurvivorMode;
import com.antaurora.apofirstlight.survivor.SurvivorPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The one-time mode choice of a new single-player world on the host's first join (docs/gameplay/survivor_death_succession_v0.md
 * 2, 11): the fallback for a world created without the Create World screen's pick (SurvivorCreateWorldClient). NORMAL
 * or HARD, then "start". Also states the item rule up front (everything stays on the body, keepInventory keeps
 * nothing). Pauses single player, cannot be closed; the server confirms the choice and closes it, and checks that it
 * came from the host of a world without a mode.
 */
public final class AflSurvivorModeScreen extends Screen {
    private static final float PANEL_W = 300, PAD = 14, TILE_H = 34, GAP = 6, BUTTON_H = 20;

    private SurvivorMode selected = SurvivorMode.UNSET;
    private boolean sent;
    private float panelX, tileY, buttonY, innerW;

    AflSurvivorModeScreen() {
        super(Component.translatable("survivor.apocalypse_firstlight.mode.title"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, AflUiStyle.argb(0x0A0B0C, 0.55F), AflUiStyle.argb(0x0A0B0C, 0.80F));
        float panelW = Math.min(PANEL_W, width - 32);
        innerW = panelW - 2 * PAD;
        List<FormattedCharSequence> rules = font.split(Component.translatable("survivor.apocalypse_firstlight.mode.rule_items"), (int) innerW);
        float line = font.lineHeight + 3;
        float panelH = 2 * PAD + font.lineHeight + 10 + 2 * TILE_H + GAP + 10 + rules.size() * line + line + 8 + BUTTON_H;
        panelX = (width - panelW) / 2;
        float y = Math.max(8, (height - panelH) / 2);
        AflUiDraw.panel(g, panelX, y, panelW, panelH, 1.0F);
        float cx = width / 2.0F;
        y += PAD;
        AflUiDraw.centred(g, font, title, cx, y, AflUiStyle.TEXT, 1.0F);
        y += font.lineHeight + 10;
        tileY = y;
        tile(g, SurvivorMode.NORMAL, y, mouseX, mouseY);
        tile(g, SurvivorMode.HARD, y + TILE_H + GAP, mouseX, mouseY);
        y += 2 * TILE_H + GAP + 10;
        for (FormattedCharSequence s : rules) {
            g.drawString(font, s, (int) (panelX + PAD), (int) y, AflUiStyle.argb(AflUiStyle.TEXT_DIM, 1.0F), false);
            y += line;
        }
        AflUiDraw.text(g, font, Component.translatable("survivor.apocalypse_firstlight.mode.locked"), panelX + PAD, y,
                AflUiStyle.TEXT_DIM, 1.0F);
        y += line + 8;
        buttonY = y;
        boolean ready = selected != SurvivorMode.UNSET && !sent;
        boolean hover = ready && inside(mouseX, mouseY, panelX + PAD, buttonY, innerW, BUTTON_H);
        float sink = AflUiDraw.button(g, panelX + PAD, buttonY, innerW, BUTTON_H, hover, false, ready, 1.0F);
        Component label = Component.translatable(sent ? "survivor.apocalypse_firstlight.mode.waiting" : "survivor.apocalypse_firstlight.mode.start");
        AflUiDraw.centred(g, font, label, cx, buttonY + sink + (BUTTON_H - font.lineHeight) / 2.0F + 1,
                ready ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1.0F);
    }

    /** One choice: name and its one-line consequence; the chosen one carries the accent hairline. */
    private void tile(GuiGraphics g, SurvivorMode mode, float y, int mouseX, int mouseY) {
        boolean chosen = selected == mode;
        boolean hover = !sent && inside(mouseX, mouseY, panelX + PAD, y, innerW, TILE_H);
        AflUiShapes.box(g, panelX + PAD, y, innerW, TILE_H, AflUiStyle.RADIUS_TILE,
                hover ? AflUiStyle.BACK_HOVER : AflUiStyle.BACK, hover ? AflUiStyle.BACK_HOVER_ALPHA : AflUiStyle.BACK_ALPHA,
                chosen ? AflUiStyle.ACCENT : AflUiStyle.LINE, chosen ? 0.9F : hover ? AflUiStyle.LINE_HOVER_ALPHA : AflUiStyle.LINE_ALPHA,
                0, 0);
        String key = "survivor.apocalypse_firstlight.mode." + mode.id();
        AflUiDraw.text(g, font, Component.translatable(key), panelX + PAD + 8, y + 6, chosen ? AflUiStyle.ACCENT : AflUiStyle.TEXT, 1.0F);
        AflUiDraw.text(g, font, Component.translatable(key + ".line"), panelX + PAD + 8, y + 7 + font.lineHeight + 2,
                AflUiStyle.TEXT_DIM, 1.0F);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || sent) return false;
        if (inside(mouseX, mouseY, panelX + PAD, tileY, innerW, TILE_H)) {
            selected = SurvivorMode.NORMAL;
            return true;
        }
        if (inside(mouseX, mouseY, panelX + PAD, tileY + TILE_H + GAP, innerW, TILE_H)) {
            selected = SurvivorMode.HARD;
            return true;
        }
        if (selected != SurvivorMode.UNSET && inside(mouseX, mouseY, panelX + PAD, buttonY, innerW, BUTTON_H)) {
            sent = true;
            AflNetwork.survivorToServer(new SurvivorPackets.ModeChoice(selected));
            return true;
        }
        return false;
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
