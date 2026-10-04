package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.function.Supplier;

/**
 * AFL creative tabs, in display order (each tab is chained after the previous one). Every registered AFL item appears in
 * exactly one tab, except the ammunition casings, which stay development-only registrations.
 */
public final class AflCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ApocalypseFirstLight.MOD_ID);

    public static final RegistryObject<CreativeModeTab> BUILDING_BLOCKS = tab("building_blocks", null, AflItems.REINFORCED_CONCRETE,
            AflItems.REINFORCED_CONCRETE,
            AflItems.REINFORCED_CONCRETE_SLAB,
            AflItems.REINFORCED_CONCRETE_STAIRS,
            AflItems.LEAD_SHIELDING_BRICKS,
            AflItems.STEEL_BLOCK,
            AflItems.STEEL_BLOCK_SLAB,
            AflItems.STEEL_BLOCK_STAIRS,
            AflItems.STEEL_PLATE,
            AflItems.STEEL_PLATE_SLAB,
            AflItems.STEEL_PLATE_STAIRS,
            AflItems.STEEL_BEAM,
            AflItems.STEEL_BRACE,
            AflItems.STEEL_CABLE,
            AflItems.STEEL_GRATE,
            AflItems.STEEL_RAILING,
            AflItems.STEEL_DOOR,
            AflItems.COMMERCIAL_GLASS_DOUBLE_DOOR,
            AflItems.INDUSTRIAL_UTILITY_LIGHT,
            AflItems.ASPHALT,
            AflItems.EDGE_LANE_WHITE,
            AflItems.EDGE_LANE_YELLOW,
            AflItems.WHITE_LANE_DIVIDER);

    public static final RegistryObject<CreativeModeTab> NATURAL_BLOCKS = tab("natural_blocks", BUILDING_BLOCKS, AflItems.GALENA_ORE,
            AflItems.BAUXITE_ORE,
            AflItems.GALENA_ORE,
            AflItems.SPHALERITE_ORE,
            AflItems.CASSITERITE_ORE,
            AflItems.PENTLANDITE_ORE,
            AflItems.WOLFRAMITE_ORE,
            AflItems.SPODUMENE_ORE,
            AflItems.FALLOUT_SOIL,
            AflItems.SCORCHED_SOIL,
            AflItems.FUSED_GROUND,
            AflItems.POPLAR_LOG,
            AflItems.STRIPPED_POPLAR_LOG,
            AflItems.POPLAR_WOOD,
            AflItems.STRIPPED_POPLAR_WOOD,
            AflItems.POPLAR_PLANKS,
            AflItems.POPLAR_STAIRS,
            AflItems.POPLAR_SLAB,
            AflItems.POPLAR_DOOR,
            AflItems.POPLAR_TRAPDOOR,
            AflItems.POPLAR_LEAVES,
            AflItems.POPLAR_SAPLING);

    public static final RegistryObject<CreativeModeTab> INDUSTRY = tab("industry", NATURAL_BLOCKS, AflItems.CRUSHER,
            AflItems.THERMAL_GENERATOR,
            AflItems.ENERGY_CELL,
            AflItems.ENERGY_BATTERY,
            AflItems.POWER_CABLE,
            AflItems.CHARGING_STATION,
            AflItems.CRUSHER,
            AflItems.INDUSTRIAL_FURNACE,
            AflItems.ALLOY_FURNACE,
            AflItems.COMPRESSOR,
            AflItems.CHEMICAL_REACTOR,
            AflItems.FLUID_PIPE,
            AflItems.FLUID_TANK,
            AflItems.INDUSTRIAL_WASTE_BUCKET,
            AflItems.GUN_MAINTENANCE_BENCH,
            AflItems.PRECISION_FABRICATION_STATION);

    public static final RegistryObject<CreativeModeTab> FURNITURE = tab("furniture", INDUSTRY, AflItems.VENDING_MACHINE,
            AflItems.INDUSTRIAL_LOCKER,
            AflItems.LEAD_CHEST,
            AflItems.INDUSTRIAL_ELECTRICAL_BOX,
            AflItems.RETAIL_SHELF_SINGLE,
            AflItems.CHECKOUT_COUNTER,
            AflItems.CHECKOUT_COUNTER_DISPLAY,
            AflItems.CHECKOUT_COUNTER_GATE,
            AflItems.BACK_BAR_SHELF,
            AflItems.CASH_REGISTER,
            AflItems.BEVERAGE_COOLER,
            AflItems.CHEST_FREEZER,
            AflItems.VENDING_MACHINE,
            AflItems.WATER_DISPENSER,
            AflItems.METAL_TRASH_CAN,
            AflItems.COMMERCIAL_DUMPSTER,
            AflItems.COMMERCIAL_DUMPSTER_BLUE,
            AflItems.COMMERCIAL_DUMPSTER_BROWN,
            AflItems.COMMERCIAL_DUMPSTER_GRAY,
            AflItems.MODERN_OFFICE_DESK,
            AflItems.MODERN_OFFICE_CHAIR,
            AflItems.MODERN_LCD_MONITOR,
            AflItems.OFFICE_COMPUTER_STATION,
            AflItems.OFFICE_KEYBOARD,
            AflItems.OFFICE_MOUSE,
            AflItems.OFFICE_CUBICLE_PARTITION,
            AflItems.LOW_FILING_CABINET,
            AflItems.TALL_FILING_CABINET,
            AflItems.OFFICE_MULTIFUNCTION_PRINTER,
            AflItems.RESTROOM_PARTITION,
            AflItems.RESTROOM_STALL_DOOR,
            AflItems.COMMERCIAL_FLUSHOMETER_TOILET,
            AflItems.COMMERCIAL_WALL_MOUNTED_SINK);

    // By production chain (docs/gameplay/material_system_v1.md): steel, lead, nickel, tungsten, lithium, then ore
    // concentrates that still wait for a consumer, then salvage.
    public static final RegistryObject<CreativeModeTab> MATERIALS = tab("materials", FURNITURE, AflItems.STEEL_BILLET,
            AflItems.STEEL_BILLET,
            AflItems.STEEL_SCRAP,
            AflItems.GALENA,
            AflItems.LEAD_BRICK,
            AflItems.PENTLANDITE,
            AflItems.ELECTROLYTIC_NICKEL,
            AflItems.WOLFRAMITE,
            AflItems.TUNGSTEN_OXIDE,
            AflItems.TUNGSTEN_POWDER,
            AflItems.TUNGSTEN_FILAMENT,
            AflItems.TUNGSTEN_CARBIDE_POWDER,
            AflItems.CEMENTED_CARBIDE_BLANK,
            AflItems.SPODUMENE_CONCENTRATE,
            AflItems.LITHIUM_CARBONATE,
            AflItems.BAUXITE,
            AflItems.ALUMINA,
            AflItems.SPHALERITE,
            AflItems.CASSITERITE,
            AflItems.SILVER_SCRAP,
            AflItems.CONCRETE_RUBBLE,
            AflItems.PLASTIC_SCRAP,
            AflItems.PLASTIC_PELLETS);

    // Pistols, then rifles, then shotguns.
    public static final RegistryObject<CreativeModeTab> FIREARMS = tab("firearms", MATERIALS, AflItems.BR51_01,
            AflItems.P9_01,
            AflItems.BLACKRIDGE_50,
            AflItems.BR51_01,
            AflItems.HR55,
            AflItems.CAT,
            AflItems.SILVERWOOD_12);

    // By slot (sight, muzzle, magazine); pistol attachments before rifle ones within a slot.
    public static final RegistryObject<CreativeModeTab> ATTACHMENTS = tab("attachments", FIREARMS, AflItems.RIFLE_RED_DOT_01,
            AflItems.PISTOL_RED_DOT,
            AflItems.RIFLE_RED_DOT_01,
            AflItems.PISTOL_SUPPRESSOR_01,
            AflItems.RIFLE_SUPPRESSOR_01,
            AflItems.HEAVY_SUPPRESSOR_01,
            AflItems.P9_01_EXTENDED_MAGAZINE,
            AflItems.BR51_EXTENDED_MAGAZINE_35,
            AflItems.BR51_DRUM_MAGAZINE_50);

    // In the firearms order: 9mm (P9), .50 AE (Blackridge), 7.62 (BR51, C.A.T.), 12.7x55 (HR55), 12 gauge (Silverwood).
    public static final RegistryObject<CreativeModeTab> AMMUNITION = tab("ammunition", ATTACHMENTS, AflItems.ROUND_762MM,
            AflItems.ROUND_9MM,
            AflItems.ROUND_50_AE,
            AflItems.ROUND_762MM,
            AflItems.ROUND_127MM,
            AflItems.ROUND_12_GAUGE);

    public static final RegistryObject<CreativeModeTab> MELEE_AND_EQUIPMENT = tab("melee_and_equipment", AMMUNITION, AflItems.CROWBAR,
            AflItems.CROWBAR,
            AflItems.SIMPLE_HEARING_PROTECTION,
            AflItems.GEIGER_COUNTER);

    // Thirst V1: water first; later drinks, food and medicine
    public static final RegistryObject<CreativeModeTab> SURVIVAL_SUPPLIES = tab("survival_supplies", MELEE_AND_EQUIPMENT,
            AflItems.PURIFIED_WATER_BOTTLE,
            AflItems.DIRTY_WATER_BOTTLE,
            AflItems.BOILED_WATER_BOTTLE,
            AflItems.PURIFIED_WATER_BOTTLE,
            AflItems.CLINICAL_THERMOMETER,
            AflItems.WRIST_THERMOMETER);

    /** Title key itemGroup.apocalypse_firstlight.&lt;id&gt;; placed after {@code after} when given. */
    @SafeVarargs
    private static RegistryObject<CreativeModeTab> tab(String id, RegistryObject<CreativeModeTab> after,
                                                       Supplier<? extends Item> icon, Supplier<? extends Item>... items) {
        List<Supplier<? extends Item>> contents = List.of(items);
        return CREATIVE_MODE_TABS.register(id, () -> {
            var builder = CreativeModeTab.builder();
            if (after != null) builder.withTabsBefore(after.getId());
            return builder.icon(() -> new ItemStack(icon.get()))
                    .title(Component.translatable("itemGroup." + ApocalypseFirstLight.MOD_ID + "." + id))
                    .displayItems((parameters, output) -> contents.forEach(item -> output.accept(item.get())))
                    .build();
        });
    }

    private AflCreativeTabs() {
    }
}
