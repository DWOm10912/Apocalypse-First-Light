package com.antaurora.apofirstlight.weight;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.math.BigDecimal;

/**
 * The hovered stack only; all mass rules come from the connected server. One line: the weight, or "unit × count = total"
 * for a stack. Data quality (estimates) stays in /aflweight; advanced tooltips (F3+H) only flag a fallback mass.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class ClientWeightTooltip {
    private ClientWeightTooltip() {}
    static String format(long grams) {
        return grams < 1000 ? grams + " g" : BigDecimal.valueOf(grams, 3).stripTrailingZeros().toPlainString() + " kg";
    }
    @SubscribeEvent public static void append(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (stack.isEmpty() || Minecraft.getInstance().level == null) return;
        var data = ClientWeightState.data();
        if (data == null) {
            event.getToolTip().add(Component.translatable("tooltip.apocalypse_firstlight.weight.pending")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        var single = stack.copy(); single.setCount(1);
        var unit = StackMassCalculator.mass(single, data);
        String key = stack.getItem() instanceof NativeGunItem ? "tooltip.apocalypse_firstlight.weight.assembled" : "tooltip.apocalypse_firstlight.weight.single";
        // the calculator multiplies a stack exactly once, so unit × count is the stack's mass
        Component line = stack.getCount() == 1 ? Component.translatable(key, format(unit.grams()))
                : Component.translatable("tooltip.apocalypse_firstlight.weight.stack", format(unit.grams()), stack.getCount(),
                        format(MassResult.multiply(unit.grams(), stack.getCount())));
        event.getToolTip().add(line.copy().withStyle(ChatFormatting.GRAY));
        if (event.getFlags().isAdvanced() && unit.issues().stream().anyMatch(issue -> issue.startsWith("fallback:")))
            event.getToolTip().add(Component.translatable("tooltip.apocalypse_firstlight.weight.fallback").withStyle(ChatFormatting.DARK_GRAY));
    }
}
