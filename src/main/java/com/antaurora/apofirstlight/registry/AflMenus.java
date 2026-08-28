package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.menu.ThermalGeneratorMenu;
import com.antaurora.apofirstlight.menu.EnergyCellMenu;
import com.antaurora.apofirstlight.menu.CrusherMenu;
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
    public static final RegistryObject<MenuType<EnergyCellMenu>> ENERGY_CELL =
            MENUS.register("energy_cell", () -> IForgeMenuType.create(EnergyCellMenu::new));
    public static final RegistryObject<MenuType<CrusherMenu>> CRUSHER =
            MENUS.register("crusher", () -> IForgeMenuType.create(CrusherMenu::new));

    private AflMenus() {
    }
}
