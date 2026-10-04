package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchLayout;
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
    /**
     * Progressive Container Search menus, one type per layout like vanilla GENERIC_9xN / GENERIC_3x3: the six chest
     * grids, then the 3 x 3 grid, then AFL's 6 x 3 and 3 x 4 grids. No extra open data.
     */
    public static final List<RegistryObject<MenuType<AflContainerSearchMenu>>> SEARCHABLE_CONTAINERS = List.of(
            searchableContainerType("9x1", AflContainerSearchLayout.chest(1)), searchableContainerType("9x2", AflContainerSearchLayout.chest(2)),
            searchableContainerType("9x3", AflContainerSearchLayout.chest(3)), searchableContainerType("9x4", AflContainerSearchLayout.chest(4)),
            searchableContainerType("9x5", AflContainerSearchLayout.chest(5)), searchableContainerType("9x6", AflContainerSearchLayout.chest(6)),
            searchableContainerType("3x3", AflContainerSearchLayout.GRID_3X3), searchableContainerType("6x3", AflContainerSearchLayout.GRID_6X3),
            searchableContainerType("3x4", AflContainerSearchLayout.GRID_3X4));

    private AflMenus() {
    }

    public static MenuType<AflContainerSearchMenu> searchableContainer(AflContainerSearchLayout layout) {
        return SEARCHABLE_CONTAINERS.get(layout.isChest() ? layout.rows() - 1 : layout == AflContainerSearchLayout.GRID_3X3 ? 6
                : layout == AflContainerSearchLayout.GRID_6X3 ? 7 : 8).get();
    }

    private static RegistryObject<MenuType<AflContainerSearchMenu>> searchableContainerType(String name, AflContainerSearchLayout layout) {
        return MENUS.register("searchable_container_" + name, () -> IForgeMenuType.create(
                (containerId, inventory, buffer) -> AflContainerSearchMenu.client(layout, containerId, inventory)));
    }
}
