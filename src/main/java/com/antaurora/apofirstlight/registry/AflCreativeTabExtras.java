package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.item.EnergyBatteryItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Creative tab entries that are stacks with data rather than plain items (AflCreativeTabs lists plain items). */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AflCreativeTabExtras {
    private AflCreativeTabExtras() {
    }

    @SubscribeEvent
    public static void addEntries(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == AflCreativeTabs.INDUSTRY.getKey()) {
            // a fully charged Energy Battery right after the empty one, for testing before the charging station exists
            event.getEntries().putAfter(new ItemStack(AflItems.ENERGY_BATTERY.get()),
                    EnergyBatteryItem.charged(AflItems.ENERGY_BATTERY.get()), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}
