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

/** The hovered stack only; all mass rules come from the connected server. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class ClientWeightTooltip {
    private ClientWeightTooltip() {}
    static String format(long grams) {
        return grams < 1000 ? grams + " g" : BigDecimal.valueOf(grams, 3).stripTrailingZeros().toPlainString() + " kg";
    }
    private static Component line(String key, MassResult mass) {
        Component estimate = mass.issues().isEmpty() ? Component.empty()
                : Component.translatable("tooltip.apocalypse_firstlight.weight.estimated");
        return Component.translatable(key, format(mass.grams()), estimate).withStyle(ChatFormatting.GRAY);
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
        var total = stack.getCount() == 1 ? unit : StackMassCalculator.mass(stack, data);
        event.getToolTip().add(line(stack.getItem() instanceof NativeGunItem
                ? "tooltip.apocalypse_firstlight.weight.assembled" : "tooltip.apocalypse_firstlight.weight.unit", unit));
        event.getToolTip().add(line("tooltip.apocalypse_firstlight.weight.stack", total));
    }
}
