package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class AflCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ApocalypseFirstLight.MOD_ID);

    public static final RegistryObject<CreativeModeTab> BLOCKS = CREATIVE_MODE_TABS.register("blocks", () ->
            CreativeModeTab.builder()
                    .icon(() -> new ItemStack(AflItems.STEEL_BLOCK.get()))
                    .title(Component.translatable("itemGroup.apocalypse_firstlight.blocks"))
                    .displayItems((parameters, output) -> {
                        output.accept(AflItems.REINFORCED_CONCRETE.get());
                        output.accept(AflItems.STEEL_BLOCK.get());
                        output.accept(AflItems.STEEL_PLATE.get());
                        output.accept(AflItems.STEEL_GRATE.get());
                    })
                    .build());

    private AflCreativeTabs() {
    }
}
