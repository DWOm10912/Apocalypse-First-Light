package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import java.util.List;
import java.util.Locale;

/** Shared reactor/generator fluid frame, atlas fill, tick overlay and tooltip. */
public final class MachineFluidBarRenderer {
    private MachineFluidBarRenderer() {}
    private static final ResourceLocation FLUID_BAR_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/common/fluid_bar.png");
    private static final int FLUID_BAR_TICK_U = 8;
    private static final int FLUID_BAR_TICK_WIDTH = 3;
    private static final int[] FLUID_BAR_TICK_ROWS = {8, 14, 20, 26, 32, 38, 44};

    public static void drawFluidBar(GuiGraphics graphics, MachineGuiLayout layout, int left, int top,
                              String barName, String fillName, FluidStack fluid,
                              int amount, int capacity) {
        MachineGuiLayout.Element bar = layout.element(barName);
        MachineGuiLayout.Element fill = layout.element(fillName);
        graphics.blit(FLUID_BAR_TEXTURE,
                left + bar.x(), top + bar.y(),
                0, 0, bar.width(), bar.height(), bar.width(), bar.height());
        MachineGuiRenderHelper.drawFluidFill(graphics, left, top, fill, fluid, amount, capacity);
        for (int tickRow : FLUID_BAR_TICK_ROWS) {
            graphics.blit(FLUID_BAR_TEXTURE,
                    left + bar.x() + FLUID_BAR_TICK_U, top + bar.y() + tickRow,
                    FLUID_BAR_TICK_U, tickRow, FLUID_BAR_TICK_WIDTH, 1,
                    bar.width(), bar.height());
        }
    }

    public static void renderFluidTooltip(GuiGraphics graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY,
                                    String titleKey, FluidStack fluid, int amount, int capacity) {
        Component fluidName = fluid.isEmpty()
                ? Component.translatable("gui.apocalypse_firstlight.chemical_reactor.empty_fluid")
                : fluid.getDisplayName();
        graphics.renderComponentTooltip(font, List.of(
                Component.translatable(titleKey),
                Component.translatable("tooltip.apocalypse_firstlight.stored_fluid", fluidName),
                Component.translatable("tooltip.apocalypse_firstlight.stored_fluid_amount",
                        String.format(Locale.ROOT, "%,d", amount),
                        String.format(Locale.ROOT, "%,d", capacity))), mouseX, mouseY);
    }
}
