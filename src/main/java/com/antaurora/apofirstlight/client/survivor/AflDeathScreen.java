package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.survivor.SurvivorPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The AFL death summary (docs/gameplay/survivor_death_succession_v0.md 11), in place of the vanilla death screen: the
 * survivor's name, career index, days survived, cause, place and the end of that life. NORMAL offers "start another
 * life" (the vanilla respawn request; the server hands the body to the stored successor) and the title screen; HARD
 * offers only "end the journey" (leave the world). Nothing here can revive anyone: the server refuses a HARD respawn.
 *
 * <p>Modern overlay style (docs/ui/afl_overlay_ui_style_v1.md): dark translucent panel, warm hairline, short status
 * lines, no explanations. Buttons wake after one second, as vanilla's do, so a click meant for the fight does not hit
 * them.
 */
public final class AflDeathScreen extends Screen {
    private static final int BUTTON_DELAY_TICKS = 20;
    private static final int FADE_TICKS = 12;
    private static final float PANEL_W = 280, PAD = 14, BUTTON_H = 20, BUTTON_GAP = 6, NAME_SCALE = 1.6F;

    private record Action(Component label, Runnable run, boolean primary) {
    }

    private final SurvivorPackets.Summary summary;
    private final List<Action> actions = new ArrayList<>();
    private int age;
    private boolean sent;
    private float panelX, panelY, panelW, buttonsY;

    AflDeathScreen(SurvivorPackets.Summary summary) {
        super(Component.literal(summary.name()));
        this.summary = summary;
    }

    @Override
    protected void init() {
        actions.clear();
        if (summary.ended()) {
            actions.add(new Action(Component.translatable("survivor.apocalypse_firstlight.death.end_journey"), this::leave, true));
        } else {
            actions.add(new Action(Component.translatable("survivor.apocalypse_firstlight.death.next_life"), this::nextLife, true));
            actions.add(new Action(Component.translatable("survivor.apocalypse_firstlight.death.title_screen"), this::leave, false));
        }
    }

    private void nextLife() {
        if (sent || minecraft == null || minecraft.player == null) return;
        sent = true;
        minecraft.player.respawn();
    }

    /** As vanilla's death screen leaves: disconnect, save, title screen. */
    private void leave() {
        if (sent || minecraft == null) return;
        sent = true;
        if (minecraft.level != null) minecraft.level.disconnect();
        minecraft.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        minecraft.setScreen(new TitleScreen());
    }

    @Override
    public void tick() {
        age++;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float fade = Mth.clamp((age + partialTick) / FADE_TICKS, 0.0F, 1.0F);
        g.fillGradient(0, 0, width, height, AflUiStyle.argb(0x0A0B0C, 0.45F * fade), AflUiStyle.argb(0x0A0B0C, 0.78F * fade));

        panelW = Math.min(PANEL_W, width - 32);
        float textW = panelW - 2 * PAD;
        Component life = Component.translatable("survivor.apocalypse_firstlight.death.life", summary.lifeIndex(), days());
        List<FormattedCharSequence> cause = font.split(
                Component.translatable("survivor.apocalypse_firstlight.death.cause", summary.cause()), (int) textW);
        List<FormattedCharSequence> place = font.split(Component.translatable("survivor.apocalypse_firstlight.death.place",
                dimension(), summary.x(), summary.y(), summary.z()), (int) textW);
        Component over = Component.translatable(summary.ended() ? "survivor.apocalypse_firstlight.death.run_over"
                : "survivor.apocalypse_firstlight.death.life_over", summary.name());
        List<FormattedCharSequence> overLines = font.split(over, (int) textW);

        float line = font.lineHeight + 3;
        float nameH = font.lineHeight * NAME_SCALE;
        float contentH = nameH + 6 + line + 8 + (cause.size() + place.size()) * line + 8 + overLines.size() * line + 10
                + actions.size() * (BUTTON_H + BUTTON_GAP) - BUTTON_GAP;
        float panelH = contentH + 2 * PAD;
        panelX = (width - panelW) / 2;
        panelY = Math.max(8, (height - panelH) / 2);
        AflUiDraw.panel(g, panelX, panelY, panelW, panelH, fade);

        float cx = width / 2.0F;
        float y = panelY + PAD;
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(NAME_SCALE, NAME_SCALE, 1);
        AflUiDraw.centred(g, font, title, 0, 0, AflUiStyle.TEXT, fade);
        g.pose().popPose();
        y += nameH + 6;
        AflUiDraw.centred(g, font, life, cx, y, AflUiStyle.TEXT_DIM, fade);
        y += line + 8;
        for (FormattedCharSequence s : cause) {
            drawLine(g, s, panelX + PAD, y, AflUiStyle.TEXT, fade);
            y += line;
        }
        for (FormattedCharSequence s : place) {
            drawLine(g, s, panelX + PAD, y, AflUiStyle.TEXT_DIM, fade);
            y += line;
        }
        y += 8;
        for (FormattedCharSequence s : overLines) {
            drawLine(g, s, cx - font.width(s) / 2.0F, y, AflUiStyle.ACCENT, fade);
            y += line;
        }
        y += 10;
        buttonsY = y;
        boolean awake = age >= BUTTON_DELAY_TICKS && !sent;
        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);
            float by = buttonsY + i * (BUTTON_H + BUTTON_GAP);
            boolean hover = awake && inside(mouseX, mouseY, panelX + PAD, by, textW, BUTTON_H);
            float sink = AflUiDraw.button(g, panelX + PAD, by, textW, BUTTON_H, hover, false, awake, fade);
            int color = !awake ? AflUiStyle.TEXT_DISABLED : action.primary() ? AflUiStyle.TEXT : AflUiStyle.TEXT_DIM;
            AflUiDraw.centred(g, font, action.label(), cx, by + sink + (BUTTON_H - font.lineHeight) / 2.0F + 1, color, fade);
        }
    }

    private void drawLine(GuiGraphics g, FormattedCharSequence text, float x, float y, int rgb, float alpha) {
        if (alpha <= 0.02F) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.drawString(font, text, 0, 0, AflUiStyle.argb(rgb, alpha), false);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || age < BUTTON_DELAY_TICKS || sent) return false;
        float textW = panelW - 2 * PAD;
        for (int i = 0; i < actions.size(); i++) {
            float by = buttonsY + i * (BUTTON_H + BUTTON_GAP);
            if (inside(mouseX, mouseY, panelX + PAD, by, textW, BUTTON_H)) {
                actions.get(i).run().run();
                return true;
            }
        }
        return false;
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private Component days() {
        int days = (int) Math.floor(summary.days());
        return days < 1 ? Component.translatable("survivor.apocalypse_firstlight.days.less_than_one")
                : days == 1 ? Component.translatable("survivor.apocalypse_firstlight.days.one")
                : Component.translatable("survivor.apocalypse_firstlight.days", days);
    }

    private Component dimension() {
        return switch (summary.dimension()) {
            case "minecraft:overworld" -> Component.translatable("survivor.apocalypse_firstlight.dimension.overworld");
            case "minecraft:the_nether" -> Component.translatable("survivor.apocalypse_firstlight.dimension.the_nether");
            case "minecraft:the_end" -> Component.translatable("survivor.apocalypse_firstlight.dimension.the_end");
            default -> Component.literal(summary.dimension());
        };
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
