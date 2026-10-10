package com.antaurora.apofirstlight.compat.jei;

import com.mojang.datafixers.util.Either;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluid amounts in litres in JEI (2026-10-09, user: show litres everywhere, AFL does not aim at other tech mods; AFL's
 * scale is 1 mB = 1 L, docs/项目内容/01 - 设计/工业/流体系统.md section 0). JEI's fluid renderer writes its own amount line
 * ("jei.tooltip.liquid.amount[.with.capacity]", "N mB"); a slot built through {@link #of} drops that line and adds
 * "N L".
 */
final class AflJeiLitres {
    private static final String JEI_AMOUNT = "jei.tooltip.liquid.amount";

    private AflJeiLitres() {
    }

    static IRecipeSlotBuilder of(IRecipeSlotBuilder slot) {
        return slot.addRichTooltipCallback((view, tooltip) -> {
            var fluid = view.getDisplayedIngredient(ForgeTypes.FLUID_STACK).orElse(null);
            if (fluid == null) return;
            List<Component> jei = new ArrayList<>();
            for (Either<FormattedText, ?> line : tooltip.getLines()) {
                line.left().ifPresent(text -> {
                    if (text instanceof Component c && c.getContents() instanceof TranslatableContents t && t.getKey().startsWith(JEI_AMOUNT)) jei.add(c);
                });
            }
            if (jei.isEmpty()) return;
            tooltip.removeAll(jei);
            tooltip.add(Component.translatable("tooltip.apocalypse_firstlight.fluid_litres",
                    NumberFormat.getIntegerInstance().format(fluid.getAmount())).withStyle(ChatFormatting.GRAY));
        });
    }
}
