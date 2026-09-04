package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.antaurora.apofirstlight.fluid.FluidTankStoredFluid;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientFluidTankStoredFluidTooltip {
    private ClientFluidTankStoredFluidTooltip() {
    }

    @SubscribeEvent
    public static void appendStoredFluid(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(AflItems.FLUID_TANK.get())) {
            return;
        }

        FluidStack storedFluid = FluidTankStoredFluid.read(stack);
        if (storedFluid.isEmpty()) {
            return;
        }

        event.getToolTip().add(Component.translatable(
                "tooltip.apocalypse_firstlight.stored_fluid",
                storedFluid.getDisplayName()).withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.translatable(
                "tooltip.apocalypse_firstlight.stored_fluid_amount",
                String.format(Locale.ROOT, "%,d", storedFluid.getAmount()),
                String.format(Locale.ROOT, "%,d", FluidTankBlockEntity.CAPACITY_MB))
                .withStyle(ChatFormatting.GRAY));
    }
}
