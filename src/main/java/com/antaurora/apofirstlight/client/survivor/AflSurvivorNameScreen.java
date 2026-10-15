package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.survivor.SurvivorNames;
import com.antaurora.apofirstlight.survivor.SurvivorPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The first survivor's one-time name choice (docs/gameplay/survivor_death_succession_v0.md 13): keep the account name or
 * type a nickname (SurvivorNames rules; the field takes only allowed characters). Pauses single player, cannot be
 * closed; the server checks the name, then closes this or reports why it refused it.
 */
public final class AflSurvivorNameScreen extends Screen {
    private static final float PANEL_W = 300, PAD = 14, FIELD_H = 22, GAP = 6, BUTTON_H = 20;

    private EditBox field;
    private boolean sent;
    @Nullable
    private Component refusal;
    private float panelX, panelY, panelH, innerW, fieldY, statusY, buttonY, buttonW;

    AflSurvivorNameScreen() {
        super(Component.translatable("survivor.apocalypse_firstlight.name.title"));
    }

    private String account() {
        return minecraft.player != null ? minecraft.player.getGameProfile().getName() : minecraft.getUser().getName();
    }

    @Override
    protected void init() {
        float panelW = Math.min(PANEL_W, width - 32);
        innerW = panelW - 2 * PAD;
        float line = font.lineHeight + 3;
        panelH = 2 * PAD + font.lineHeight + 10 + FIELD_H + 6 + line + 8 + BUTTON_H;
        panelX = (width - panelW) / 2;
        panelY = Math.max(8, (height - panelH) / 2);
        fieldY = panelY + PAD + font.lineHeight + 10;
        statusY = fieldY + FIELD_H + 6;
        buttonY = statusY + line + 8;
        buttonW = (innerW - GAP) / 2;

        String text = field != null ? field.getValue() : account();
        field = new EditBox(font, (int) (panelX + PAD + 8), (int) (fieldY + (FIELD_H - font.lineHeight) / 2.0F + 1),
                (int) (innerW - 16), font.lineHeight, title);
        field.setBordered(false);
        field.setMaxLength(SurvivorNames.NICKNAME_MAX);
        field.setTextColor(AflUiStyle.argb(AflUiStyle.TEXT, 1.0F));
        field.setValue(text);
        field.setFilter(s -> s.codePoints().allMatch(SurvivorNames::nicknameChar));
        field.setResponder(s -> refusal = null);
        field.setEditable(!sent);
        addRenderableWidget(field);
        setInitialFocus(field);
    }

    @Override
    public void tick() {
        field.tick();
    }

    private boolean ready() {
        return !sent && (field.getValue().strip().equals(account()) || SurvivorNames.nickname(field.getValue()) != null);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, AflUiStyle.argb(0x0A0B0C, 0.55F), AflUiStyle.argb(0x0A0B0C, 0.80F));
        AflUiDraw.panel(g, panelX, panelY, innerW + 2 * PAD, panelH, 1.0F);
        float cx = width / 2.0F;
        AflUiDraw.centred(g, font, title, cx, panelY + PAD, AflUiStyle.TEXT, 1.0F);
        boolean focus = field.isFocused() && !sent;
        AflUiShapes.box(g, panelX + PAD, fieldY, innerW, FIELD_H, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, AflUiStyle.BACK_ALPHA,
                focus ? AflUiStyle.ACCENT : AflUiStyle.LINE, focus ? 0.9F : AflUiStyle.LINE_ALPHA, 0, 0);
        Component status = refusal != null ? refusal : Component.translatable("survivor.apocalypse_firstlight.name.locked");
        AflUiDraw.text(g, font, status, panelX + PAD, statusY, refusal != null ? AflUiStyle.ACCENT : AflUiStyle.TEXT_DIM, 1.0F);

        float leftX = panelX + PAD, rightX = leftX + buttonW + GAP;
        boolean leftHover = !sent && inside(mouseX, mouseY, leftX, buttonY, buttonW, BUTTON_H);
        float sink = AflUiDraw.button(g, leftX, buttonY, buttonW, BUTTON_H, leftHover, false, !sent, 1.0F);
        AflUiDraw.centred(g, font, Component.translatable("survivor.apocalypse_firstlight.name.account"), leftX + buttonW / 2,
                buttonY + sink + (BUTTON_H - font.lineHeight) / 2.0F + 1, sent ? AflUiStyle.TEXT_DISABLED : AflUiStyle.TEXT, 1.0F);
        boolean ready = ready();
        boolean rightHover = ready && inside(mouseX, mouseY, rightX, buttonY, buttonW, BUTTON_H);
        sink = AflUiDraw.button(g, rightX, buttonY, buttonW, BUTTON_H, rightHover, false, ready, 1.0F);
        Component confirm = Component.translatable(sent ? "survivor.apocalypse_firstlight.name.waiting"
                : "survivor.apocalypse_firstlight.name.confirm");
        AflUiDraw.centred(g, font, confirm, rightX + buttonW / 2, buttonY + sink + (BUTTON_H - font.lineHeight) / 2.0F + 1,
                ready ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1.0F);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !sent) {
            float leftX = panelX + PAD;
            if (inside(mouseX, mouseY, leftX, buttonY, buttonW, BUTTON_H)) {
                send(account());
                return true;
            }
            if (ready() && inside(mouseX, mouseY, leftX + buttonW + GAP, buttonY, buttonW, BUTTON_H)) {
                send(field.getValue());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && ready()) {
            send(field.getValue());
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void send(String name) {
        sent = true;
        refusal = null;
        field.setEditable(false);
        AflNetwork.survivorToServer(new SurvivorPackets.NameChoice(name));
    }

    /** Server: the name was refused ("invalid", "taken"); the field is editable again. */
    void refused(String reason) {
        sent = false;
        field.setEditable(true);
        refusal = Component.translatableWithFallback("survivor.apocalypse_firstlight.name.refused." + reason, reason);
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
