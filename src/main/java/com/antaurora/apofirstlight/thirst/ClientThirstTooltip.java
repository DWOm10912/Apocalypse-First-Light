package com.antaurora.apofirstlight.thirst;

import com.antaurora.apofirstlight.client.AflRingIcon;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import java.math.BigDecimal;

/**
 * A drink's tooltip line: the HUD's water drop (ClientThirstHud#WATER_DROP, drawn in real pixels like the ring) and
 * "+amount", both in the ring's water blue. One text line high.
 */
public final class ClientThirstTooltip implements ClientTooltipComponent {
    private static final int ICON = 9, GAP = 2;
    private final String text;

    public ClientThirstTooltip(ThirstTooltip tooltip) {
        this.text = "+" + BigDecimal.valueOf(tooltip.amount()).stripTrailingZeros().toPlainString();
    }

    @Override public int getHeight() { return 10; }
    @Override public int getWidth(Font font) { return ICON + GAP + font.width(text); }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        AflRingIcon.glyph(graphics, x, y - 0.5, ICON, ClientThirstHud.WATER_BLUE, 1.0, ClientThirstHud.WATER_DROP);
        graphics.drawString(font, text, x + ICON + GAP, y, 0xFF000000 | ClientThirstHud.WATER_BLUE, true);
    }
}
