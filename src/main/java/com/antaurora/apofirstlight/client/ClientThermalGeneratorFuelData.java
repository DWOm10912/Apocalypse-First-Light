package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;
import java.util.Map;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientThermalGeneratorFuelData {
    private static volatile Map<ResourceLocation, Integer> fuelEnergies = Map.of();

    private ClientThermalGeneratorFuelData() {
    }

    public static void replace(Map<ResourceLocation, Integer> updatedFuelEnergies) {
        fuelEnergies = Map.copyOf(updatedFuelEnergies);
    }

    public static boolean isThermalGeneratorFuel(ItemStack stack) {
        return !stack.isEmpty()
                && fuelEnergies.containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    @SubscribeEvent
    public static void appendFuelTooltip(ItemTooltipEvent event) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        Integer energyFe = fuelEnergies.get(itemId);
        if (energyFe != null) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.apocalypse_firstlight.thermal_generation",
                    String.format(Locale.ROOT, "%,d", energyFe)).withStyle(ChatFormatting.GRAY));
        }
    }

    @SubscribeEvent
    public static void clearOnDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        fuelEnergies = Map.of();
    }
}
