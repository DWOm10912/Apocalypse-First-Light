package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchMenu;
import com.antaurora.apofirstlight.menu.ThermalGeneratorMenu;
import com.antaurora.apofirstlight.menu.EnergyCellMenu;
import com.antaurora.apofirstlight.menu.CrusherMenu;
import com.antaurora.apofirstlight.menu.IndustrialFurnaceMenu;
import com.antaurora.apofirstlight.menu.CompressorMenu;
import com.antaurora.apofirstlight.menu.AlloyFurnaceMenu;
import com.antaurora.apofirstlight.menu.ChemicalReactorMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public final class AflMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ApocalypseFirstLight.MOD_ID);
    public static final RegistryObject<MenuType<com.antaurora.apofirstlight.menu.GunMaintenanceMenu>> GUN_MAINTENANCE =
            MENUS.register("gun_maintenance_bench",()->IForgeMenuType.create(com.antaurora.apofirstlight.menu.GunMaintenanceMenu::new));

    public static final RegistryObject<MenuType<ThermalGeneratorMenu>> THERMAL_GENERATOR =
            MENUS.register("thermal_generator", () -> IForgeMenuType.create(ThermalGeneratorMenu::new));
    public static final RegistryObject<MenuType<EnergyCellMenu>> ENERGY_CELL =
            MENUS.register("energy_cell", () -> IForgeMenuType.create(EnergyCellMenu::new));
    public static final RegistryObject<MenuType<CrusherMenu>> CRUSHER =
            MENUS.register("crusher", () -> IForgeMenuType.create(CrusherMenu::new));
    public static final RegistryObject<MenuType<IndustrialFurnaceMenu>> INDUSTRIAL_FURNACE =
            MENUS.register("industrial_furnace", () -> IForgeMenuType.create(IndustrialFurnaceMenu::new));
    public static final RegistryObject<MenuType<AlloyFurnaceMenu>> ALLOY_FURNACE =
            MENUS.register("alloy_furnace", () -> IForgeMenuType.create(AlloyFurnaceMenu::new));
    public static final RegistryObject<MenuType<CompressorMenu>> COMPRESSOR =
            MENUS.register("compressor", () -> IForgeMenuType.create(CompressorMenu::new));
    public static final RegistryObject<MenuType<ChemicalReactorMenu>> CHEMICAL_REACTOR =
            MENUS.register("chemical_reactor", () -> IForgeMenuType.create(ChemicalReactorMenu::new));
    /** Progressive Container Search grids, one type per row count like vanilla GENERIC_9xN; no extra open data. */
    public static final List<RegistryObject<MenuType<AflContainerSearchMenu>>> SEARCHABLE_CONTAINERS = List.of(
            searchableContainerType(1), searchableContainerType(2), searchableContainerType(3),
            searchableContainerType(4), searchableContainerType(5), searchableContainerType(6));

    private AflMenus() {
    }

    public static MenuType<AflContainerSearchMenu> searchableContainer(int rows) {
        return SEARCHABLE_CONTAINERS.get(rows - 1).get();
    }

    private static RegistryObject<MenuType<AflContainerSearchMenu>> searchableContainerType(int rows) {
        return MENUS.register("searchable_container_9x" + rows, () -> IForgeMenuType.create(
                (containerId, inventory, buffer) -> AflContainerSearchMenu.client(rows, containerId, inventory)));
    }
}
