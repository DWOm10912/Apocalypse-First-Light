package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.menu.ThermalGeneratorMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ApocalypseFirstLight.MOD_ID);

    public static final RegistryObject<MenuType<ThermalGeneratorMenu>> THERMAL_GENERATOR =
            MENUS.register("thermal_generator", () -> IForgeMenuType.create(ThermalGeneratorMenu::new));

    private AflMenus() {
    }
}
