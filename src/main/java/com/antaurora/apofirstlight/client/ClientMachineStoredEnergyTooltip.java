package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.energy.MachineStoredEnergy;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientMachineStoredEnergyTooltip {
    private ClientMachineStoredEnergyTooltip() {
    }

    @SubscribeEvent
    public static void appendStoredEnergy(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(AflItems.THERMAL_GENERATOR.get())
                && !stack.is(AflItems.ENERGY_CELL.get())
                && !stack.is(AflItems.CRUSHER.get())) {
            return;
        }
        int storedEnergy = MachineStoredEnergy.read(stack);
        if (storedEnergy > 0) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.apocalypse_firstlight.stored_energy",
                    String.format(Locale.ROOT, "%,d", storedEnergy)).withStyle(ChatFormatting.GRAY));
        }
    }
}
