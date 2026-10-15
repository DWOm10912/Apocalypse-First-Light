package com.antaurora.apofirstlight.wiring.client;

import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.wiring.WiringNode;
import com.antaurora.apofirstlight.wiring.WiringPackets;
import com.antaurora.apofirstlight.wiring.WiringTool;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * The wiring wrench's group picker (Building Wiring V1 phase 5, docs/gameplay/building_wiring_v1_phase5_draft.md 3): a small
 * panel over the world (style docs/ui/afl_overlay_ui_style_v1.md, like the panel screen) for the light or switch just
 * clicked. The groups the circuit uses with their lights and switches (the panel's last solve), the lowest free group as
 * 新组, for a light also 常亮 and 光控; the current one marked. A click sets it and closes; Esc changes nothing.
 */
public final class WiringGroupScreen extends Screen {
    private static final int W = 196, HEADER = 26, ROW_H = 16, PAD = 10;
    /** The circuits' insulation, as the x-ray draws it (1-4 red, blue, green, yellow; the feeder black). */
    private static final int[] WIRE = {0xC4342A, 0x2C60C4, 0x349648, 0xDEB828, 0x343438};

    private record Item(int group, Component label, Component counts) {}

    private final WiringPackets.Groups data;
    private final List<Item> items = new ArrayList<>();
    private Component heading = Component.empty();
    private float left, top, panelH;

    WiringGroupScreen(WiringPackets.Groups data) {
        super(Component.translatable("screen.apocalypse_firstlight.wiring_group.title"));
        this.data = data;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.apocalypse_firstlight.wiring_group." + key, args);
    }

    @Override
    protected void init() {
        items.clear();
        int[] lights = data.lights(), switches = data.switches();
        boolean light = !data.isSwitch();
        if (light) items.add(new Item(WiringNode.ALWAYS, tr("always"), counts(lights[0], 0)));
        int free = 0;
        for (int g = 1; g <= WiringNode.GROUPS; g++) {
            boolean used = lights[g] > 0 || switches[g] > 0;
            if (used || g == data.group()) items.add(new Item(g, tr("group", g), counts(lights[g], switches[g])));
            else if (free == 0) free = g;
        }
        if (free > 0) items.add(new Item(free, tr("new_group", free), Component.empty()));
        if (light) items.add(new Item(WiringNode.PHOTOCELL, tr("photocell"), counts(lights[WiringNode.PHOTOCELL], 0)));
        heading = minecraft != null && minecraft.level != null
                ? Component.translatable(minecraft.level.getBlockState(data.pos()).getBlock().getDescriptionId()) : Component.empty();
        panelH = HEADER + items.size() * ROW_H + 8;
        left = (width - W) / 2F;
        top = (height - panelH) / 2F;
    }

    /** "2 灯 1 开关", either part left out at 0. */
    private static Component counts(int lights, int switches) {
        var out = Component.empty();
        if (lights > 0) out.append(tr("lights", lights));
        if (lights > 0 && switches > 0) out.append(" ");
        if (switches > 0) out.append(tr("switches", switches));
        return out;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float x = left, y = top;
        AflUiDraw.panel(g, x, y, W, panelH, 1);
        // header: the device, then its circuit with the wire's colour
        Component circuit = WiringTool.circuitName(data.circuit());
        float cw = font.width(circuit);
        AflUiDraw.text(g, font, font.width(heading) > W - 2 * PAD - cw - 16 ? Component.literal(font.plainSubstrByWidth(heading.getString(), W - 2 * PAD - (int) cw - 22) + "…") : heading,
                x + PAD, y + 9, AflUiStyle.TEXT, 1);
        AflUiDraw.text(g, font, circuit, x + W - PAD - cw, y + 9, AflUiStyle.TEXT_DIM, 1);
        AflUiShapes.fill(g, x + W - PAD - cw - 9, y + 10.5F, 5, 5, 2.5F, WIRE[WiringNode.colourOf(data.circuit())], 1);
        AflUiShapes.fill(g, x + 8, y + HEADER - 4, W - 16, 0.6F, 0, AflUiStyle.LINE, AflUiStyle.LINE_ALPHA);
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            float ry = y + HEADER + i * ROW_H;
            boolean hover = inside(mouseX, mouseY, x + 6, ry, W - 12, ROW_H - 1), current = it.group() == data.group();
            if (hover) AflUiShapes.box(g, x + 6, ry, W - 12, ROW_H - 1, AflUiStyle.RADIUS_TILE, AflUiStyle.LINE, 0.06F, AflUiStyle.LINE, AflUiStyle.LINE_HOVER_ALPHA, 0, 0);
            AflUiDraw.text(g, font, it.label(), x + PAD + 2, ry + 4, current || hover ? AflUiStyle.TEXT : AflUiStyle.TEXT_DIM, 1);
            AflUiDraw.text(g, font, it.counts(), x + 82, ry + 4, AflUiStyle.TEXT_DISABLED, 1);
            if (current) AflUiShapes.fill(g, x + W - PAD - 7, ry + 5, 5, 5, 2.5F, AflUiStyle.ACCENT, 1);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        for (int i = 0; i < items.size(); i++) {
            if (!inside(mx, my, left + 6, top + HEADER + i * ROW_H, W - 12, ROW_H - 1)) continue;
            int group = items.get(i).group();
            if (group != data.group()) AflNetwork.wiringToServer(new WiringPackets.SetGroup(data.pos(), group));
            if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.35F));
            onClose();
            return true;
        }
        if (!inside(mx, my, left, top, W, panelH)) {   // a click beside the panel closes it, like Esc
            onClose();
            return true;
        }
        return true;
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
