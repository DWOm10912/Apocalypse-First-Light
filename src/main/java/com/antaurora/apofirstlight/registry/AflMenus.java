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


public final class AflMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ApocalypseFirstLight.MOD_ID);
    public static final RegistryObject<MenuType<com.antaurora.apofirstlight.menu.GunMaintenanceMenu>> GUN_MAINTENANCE =
            MENUS.register("gun_maintenance_bench",()->IForgeMenuType.create(com.antaurora.apofirstlight.menu.GunMaintenanceMenu::new));

    /** An ExternalGridHost's grid (the pickup's cargo bed): the open data is the host entity, the layout and the items. */
    public static final RegistryObject<MenuType<com.antaurora.apofirstlight.inventory.grid.ExternalGridMenu>> EXTERNAL_GRID =
            MENUS.register("external_grid", () -> IForgeMenuType.create(com.antaurora.apofirstlight.inventory.grid.ExternalGridMenu::client));

    public static final RegistryObject<MenuType<ThermalGeneratorMenu>> THERMAL_GENERATOR =
            MENUS.register("thermal_generator", () -> IForgeMenuType.create(ThermalGeneratorMenu::new));
    public static final RegistryObject<MenuType<com.antaurora.apofirstlight.menu.DistributionPanelMenu>> DISTRIBUTION_PANEL =
            MENUS.register("distribution_panel", () -> IForgeMenuType.create(com.antaurora.apofirstlight.menu.DistributionPanelMenu::new));
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
     * The Progressive Container Search menu of every searchable container (since the grid sections, 2026-10-27: until then
     * one type per layout like vanilla GENERIC_9xN). The open data is the container's grid sections (AflContainerSearch#open).
     */
    public static final RegistryObject<MenuType<AflContainerSearchMenu>> SEARCHABLE_CONTAINER =
            MENUS.register("searchable_container", () -> IForgeMenuType.create(AflContainerSearchMenu::client));

    private AflMenus() {
    }
}
