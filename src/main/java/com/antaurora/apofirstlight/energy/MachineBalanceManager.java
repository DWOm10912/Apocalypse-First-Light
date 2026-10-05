package com.antaurora.apofirstlight.energy;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MachineBalanceManager {
    private static final Gson GSON = new GsonBuilder().create();
    private static final ResourceLocation THERMAL_GENERATOR_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "thermal_generator");
    private static final ResourceLocation ENERGY_CELL_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "energy_cell");
    private static final ResourceLocation ENERGY_BATTERY_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "energy_battery");
    private static final ResourceLocation BEVERAGE_COOLER_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "beverage_cooler");
    private static final ResourceLocation CHEST_FREEZER_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "chest_freezer");
    private static final ResourceLocation VENDING_MACHINE_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "vending_machine");
    private static final ResourceLocation WATER_DISPENSER_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "water_dispenser");
    private static final ResourceLocation FUEL_CANOPY_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "fuel_canopy");
    private static final ResourceLocation FUEL_DISPENSER_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "fuel_dispenser");
    private static final ResourceLocation CHARGING_STATION_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "charging_station");
    private static final ResourceLocation CRUSHER_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "crusher");
    private static final ResourceLocation INDUSTRIAL_FURNACE_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "industrial_furnace");
    private static final ResourceLocation COMPRESSOR_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "compressor");
    private static final ResourceLocation ALLOY_FURNACE_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "alloy_furnace");
    private static final ResourceLocation CHEMICAL_REACTOR_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "chemical_reactor");
    private static final ResourceLocation INTAKE_PUMP_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "intake_pump");

    private static volatile ThermalGeneratorBalance thermalGenerator = fallbackThermalGenerator();
    private static volatile EnergyCellBalance energyCell = fallbackEnergyCell();
    private static volatile EnergyBatteryBalance energyBattery = fallbackEnergyBattery();
    private static volatile ChargingStationBalance chargingStation = fallbackChargingStation();
    private static volatile ApplianceBalance beverageCooler = fallbackBeverageCooler();
    private static volatile ApplianceBalance chestFreezer = fallbackChestFreezer();
    private static volatile ApplianceBalance vendingMachine = fallbackVendingMachine();
    private static volatile ApplianceBalance waterDispenser = fallbackWaterDispenser();
    private static volatile ApplianceBalance fuelDispenser = fallbackFuelDispenser();
    private static volatile ApplianceBalance fuelCanopy = fallbackFuelCanopy();
    private static volatile CrusherBalance crusher = fallbackCrusher();
    private static volatile IndustrialFurnaceBalance industrialFurnace = fallbackIndustrialFurnace();
    private static volatile CompressorBalance compressor = fallbackCompressor();
    private static volatile AlloyFurnaceBalance alloyFurnace = fallbackAlloyFurnace();
    private static volatile ChemicalReactorBalance chemicalReactor = fallbackChemicalReactor();
    private static volatile IntakePumpBalance intakePump = fallbackIntakePump();
    private static volatile int revision;

    private MachineBalanceManager() {
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new BalanceReloadListener());
    }

    public static ThermalGeneratorBalance thermalGenerator() {
        return thermalGenerator;
    }

    public static EnergyCellBalance energyCell() {
        return energyCell;
    }

    public static EnergyBatteryBalance energyBattery() {
        return energyBattery;
    }

    public static ChargingStationBalance chargingStation() {
        return chargingStation;
    }

    public static ApplianceBalance beverageCooler() {
        return beverageCooler;
    }

    public static ApplianceBalance chestFreezer() {
        return chestFreezer;
    }

    /** Lights only: the compressor fields are 0. */
    public static ApplianceBalance vendingMachine() {
        return vendingMachine;
    }

    /** Lights only (the indicator LEDs): the compressor fields are 0. */
    public static ApplianceBalance waterDispenser() {
        return waterDispenser;
    }

    /** Lights only (the lamp under the header): the compressor fields are 0. */
    public static ApplianceBalance fuelDispenser() {
        return fuelDispenser;
    }

    /**
     * Fuel canopy, lights only: each column base's buffer and intake, and light_fe_per_tick per lamp of its network
     * (FuelCanopyColumnBlockEntity); the compressor fields are 0.
     */
    public static ApplianceBalance fuelCanopy() {
        return fuelCanopy;
    }

    public static CrusherBalance crusher() {
        return crusher;
    }

    public static IndustrialFurnaceBalance industrialFurnace() {
        return industrialFurnace;
    }

    public static CompressorBalance compressor() {
        return compressor;
    }

    public static AlloyFurnaceBalance alloyFurnace() {
        return alloyFurnace;
    }

    public static ChemicalReactorBalance chemicalReactor() {
        return chemicalReactor;
    }

    /** Intake Pump (IntakePumpBlockEntity): buffer, cable input limit, the draw while pumping and the pumping rate. */
    public static IntakePumpBalance intakePump() {
        return intakePump;
    }

    public static int revision() {
        return revision;
    }

    public static boolean isThermalGeneratorFuel(ItemStack stack) {
        return !stack.isEmpty() && thermalGenerator.fuels().containsKey(stack.getItem());
    }

    public static Map<ResourceLocation, Integer> thermalGeneratorFuelEnergies() {
        Map<ResourceLocation, Integer> energies = new LinkedHashMap<>();
        thermalGenerator.fuels().forEach((item, fuel) ->
                energies.put(BuiltInRegistries.ITEM.getKey(item), fuel.energyFe()));
        return Map.copyOf(energies);
    }

    @Nullable
    public static FuelBalance thermalGeneratorFuel(ItemStack stack) {
        return stack.isEmpty() ? null : thermalGenerator.fuels().get(stack.getItem());
    }

    @SubscribeEvent
    public static void syncMachineBalanceData(OnDatapackSyncEvent event) {
        Map<ResourceLocation, Integer> fuelEnergies = thermalGeneratorFuelEnergies();
        int crusherWorkFePerTick = crusher.workFePerTick();
        int compressorWorkFePerTick = compressor.workFePerTick();
        int alloyFurnaceWorkFePerTick = alloyFurnace.workFePerTick();
        int chemicalWorkFePerTick = chemicalReactor.workFePerTick();
        int industrialWorkFePerTickPerLane = industrialFurnace.workFePerTickPerLane();
        event.getPlayers().forEach(player -> {
            AflNetwork.sendThermalGeneratorFuels(player, fuelEnergies);
            AflNetwork.sendCrusherBalance(player, crusherWorkFePerTick);
            AflNetwork.sendCompressorBalance(player, compressorWorkFePerTick);
            AflNetwork.sendAlloyFurnaceBalance(player, alloyFurnaceWorkFePerTick);
            AflNetwork.sendProcessingMachineBalance(player, chemicalWorkFePerTick,
                    industrialWorkFePerTickPerLane);
        });
    }

    public record ThermalGeneratorBalance(int capacityFe, int generationFePerTick,
                                          int maxOutputFePerTick, boolean pauseBurnWhenFull,
                                          Map<Item, FuelBalance> fuels) {
    }

    public record FuelBalance(int energyFe, @Nullable Item remainder) {
    }

    public record EnergyCellBalance(int capacityFe, int maxReceiveFePerTick, int maxExtractFePerTick) {
    }

    /** Energy Battery item: capacity and per-tick transfer limits in both directions. */
    public record EnergyBatteryBalance(int capacityFe, int maxReceiveFePerTick, int maxExtractFePerTick) {
    }

    /** Charging Station: internal buffer, cable input limit, and the charge rate into the item on the tray. */
    public record ChargingStationBalance(int capacityFe, int maxReceiveFePerTick, int chargeFePerTick) {
    }

    /**
     * Cold appliances (Beverage Cooler, Chest Freezer; energy/CompressorAppliance): small buffer, cable input limit, the
     * lights' draw whenever lit, and the compressor's draw while it runs, in cycles of on / off ticks. Lights-only
     * appliances (Vending Machine, Water Dispenser) have no compressor fields in their file and 0 here.
     */
    public record ApplianceBalance(int capacityFe, int maxReceiveFePerTick, int lightFePerTick, int compressorFePerTick,
                                   int compressorOnTicks, int compressorOffTicks) {
    }

    public record CrusherBalance(int capacityFe, int maxReceiveFePerTick, int workFePerTick) {
    }

    public record IndustrialFurnaceBalance(int capacityFe, int maxReceiveFePerTick,
                                           int workFePerTickPerLane,
                                           double processingTimeMultiplier) {
    }

    public record CompressorBalance(int capacityFe, int maxReceiveFePerTick, int workFePerTick) {
    }

    public record AlloyFurnaceBalance(int capacityFe, int maxReceiveFePerTick, int workFePerTick) {
    }

    public record ChemicalReactorBalance(int capacityFe, int maxReceiveFePerTick, int workFePerTick) {
    }

    /** work_fe_per_tick is paid only on ticks it pumps; pump_mb_per_tick is the liquid drawn on such a tick (1 mB = 1 L). */
    public record IntakePumpBalance(int capacityFe, int maxReceiveFePerTick, int workFePerTick, int pumpMbPerTick) {
    }

    private static final class BalanceReloadListener extends SimpleJsonResourceReloadListener {
        private BalanceReloadListener() {
            super(GSON, "machine_balance");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            ThermalGeneratorBalance loadedThermal = loadThermalGenerator(resources.get(THERMAL_GENERATOR_ID));
            EnergyCellBalance loadedCell = loadEnergyCell(resources.get(ENERGY_CELL_ID));
            EnergyBatteryBalance loadedBattery = loadEnergyBattery(resources.get(ENERGY_BATTERY_ID));
            ChargingStationBalance loadedStation = loadChargingStation(resources.get(CHARGING_STATION_ID));
            ApplianceBalance loadedCooler = loadAppliance(resources.get(BEVERAGE_COOLER_ID), "beverage_cooler.json",
                    fallbackBeverageCooler());
            ApplianceBalance loadedFreezer = loadAppliance(resources.get(CHEST_FREEZER_ID), "chest_freezer.json",
                    fallbackChestFreezer());
            ApplianceBalance loadedVending = loadLightsOnly(resources.get(VENDING_MACHINE_ID), "vending_machine.json",
                    fallbackVendingMachine());
            ApplianceBalance loadedDispenser = loadLightsOnly(resources.get(WATER_DISPENSER_ID), "water_dispenser.json",
                    fallbackWaterDispenser());
            ApplianceBalance loadedFuelDispenser = loadLightsOnly(resources.get(FUEL_DISPENSER_ID), "fuel_dispenser.json",
                    fallbackFuelDispenser());
            ApplianceBalance loadedFuelCanopy = loadLightsOnly(resources.get(FUEL_CANOPY_ID), "fuel_canopy.json",
                    fallbackFuelCanopy());
            CrusherBalance loadedCrusher = loadCrusher(resources.get(CRUSHER_ID));
            IndustrialFurnaceBalance loadedIndustrialFurnace =
                    loadIndustrialFurnace(resources.get(INDUSTRIAL_FURNACE_ID));
            CompressorBalance loadedCompressor = loadCompressor(resources.get(COMPRESSOR_ID));
            AlloyFurnaceBalance loadedAlloyFurnace = loadAlloyFurnace(resources.get(ALLOY_FURNACE_ID));
            ChemicalReactorBalance loadedChemicalReactor =
                    loadChemicalReactor(resources.get(CHEMICAL_REACTOR_ID));
            IntakePumpBalance loadedIntakePump = loadIntakePump(resources.get(INTAKE_PUMP_ID));
            thermalGenerator = loadedThermal;
            energyCell = loadedCell;
            energyBattery = loadedBattery;
            chargingStation = loadedStation;
            beverageCooler = loadedCooler;
            chestFreezer = loadedFreezer;
            vendingMachine = loadedVending;
            waterDispenser = loadedDispenser;
            fuelDispenser = loadedFuelDispenser;
            fuelCanopy = loadedFuelCanopy;
            crusher = loadedCrusher;
            industrialFurnace = loadedIndustrialFurnace;
            compressor = loadedCompressor;
            alloyFurnace = loadedAlloyFurnace;
            chemicalReactor = loadedChemicalReactor;
            intakePump = loadedIntakePump;
            revision++;

            ApocalypseFirstLight.LOGGER.info(
                    "[AFL ELECTRICITY] Thermal Generator balance: capacity={} FE, generation={} FE/t, output={} FE/t, pauseWhenFull={}, fuels={}",
                    loadedThermal.capacityFe(), loadedThermal.generationFePerTick(),
                    loadedThermal.maxOutputFePerTick(), loadedThermal.pauseBurnWhenFull(),
                    loadedThermal.fuels().size());
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL ELECTRICITY] Energy Cell balance: capacity={} FE, receive={} FE/t, extract={} FE/t",
                    loadedCell.capacityFe(), loadedCell.maxReceiveFePerTick(), loadedCell.maxExtractFePerTick());
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL ELECTRICITY] Energy Battery balance: capacity={} FE, receive={} FE/t, extract={} FE/t",
                    loadedBattery.capacityFe(), loadedBattery.maxReceiveFePerTick(), loadedBattery.maxExtractFePerTick());
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL ELECTRICITY] Charging Station balance: capacity={} FE, receive={} FE/t, charge={} FE/t",
                    loadedStation.capacityFe(), loadedStation.maxReceiveFePerTick(), loadedStation.chargeFePerTick());
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL ELECTRICITY] Beverage Cooler balance: capacity={} FE, receive={} FE/t, light={} FE/t, compressor={} FE/t, cycle={}/{} ticks",
                    loadedCooler.capacityFe(), loadedCooler.maxReceiveFePerTick(), loadedCooler.lightFePerTick(),
                    loadedCooler.compressorFePerTick(), loadedCooler.compressorOnTicks(), loadedCooler.compressorOffTicks());
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL ELECTRICITY] Chest Freezer balance: capacity={} FE, receive={} FE/t, light={} FE/t, compressor={} FE/t, cycle={}/{} ticks",
                    loadedFreezer.capacityFe(), loadedFreezer.maxReceiveFePerTick(), loadedFreezer.lightFePerTick(),
                    loadedFreezer.compressorFePerTick(), loadedFreezer.compressorOnTicks(), loadedFreezer.compressorOffTicks());
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL ELECTRICITY] Vending Machine balance: capacity={} FE, receive={} FE/t, light={} FE/t",
                    loadedVending.capacityFe(), loadedVending.maxReceiveFePerTick(), loadedVending.lightFePerTick());
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL ELECTRICITY] Water Dispenser balance: capacity={} FE, receive={} FE/t, light={} FE/t",
                    loadedDispenser.capacityFe(), loadedDispenser.maxReceiveFePerTick(), loadedDispenser.lightFePerTick());
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL ELECTRICITY] Crusher balance: capacity={} FE, receive={} FE/t, work={} FE/t",
                    loadedCrusher.capacityFe(), loadedCrusher.maxReceiveFePerTick(), loadedCrusher.workFePerTick());
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL ELECTRICITY] Smelting Factory balance: capacity={} FE, receive={} FE/t, work/lane={} FE/t, time multiplier={}",
                    loadedIndustrialFurnace.capacityFe(), loadedIndustrialFurnace.maxReceiveFePerTick(),
                    loadedIndustrialFurnace.workFePerTickPerLane(),
                    loadedIndustrialFurnace.processingTimeMultiplier());
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL ELECTRICITY] Compressor balance: capacity={} FE, receive={} FE/t, work={} FE/t",
                    loadedCompressor.capacityFe(), loadedCompressor.maxReceiveFePerTick(),
                    loadedCompressor.workFePerTick());
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL ELECTRICITY] Alloy Furnace balance: capacity={} FE, receive={} FE/t, work={} FE/t",
                    loadedAlloyFurnace.capacityFe(), loadedAlloyFurnace.maxReceiveFePerTick(),
                    loadedAlloyFurnace.workFePerTick());
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL ELECTRICITY] Chemical Reactor balance: capacity={} FE, receive={} FE/t, work={} FE/t",
                    loadedChemicalReactor.capacityFe(), loadedChemicalReactor.maxReceiveFePerTick(),
                    loadedChemicalReactor.workFePerTick());
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL ELECTRICITY] Intake Pump balance: capacity={} FE, receive={} FE/t, work={} FE/t, pump={} mB/t",
                    loadedIntakePump.capacityFe(), loadedIntakePump.maxReceiveFePerTick(),
                    loadedIntakePump.workFePerTick(), loadedIntakePump.pumpMbPerTick());

        }
    }

    private static ThermalGeneratorBalance loadThermalGenerator(@Nullable JsonElement element) {
        try {
            JsonObject root = requireObject(element, "thermal_generator.json");
            int capacity = requirePositiveInt(root, "capacity_fe", "thermal_generator.json");
            int generation = requirePositiveInt(root, "generation_fe_per_tick", "thermal_generator.json");
            int output = requirePositiveInt(root, "max_output_fe_per_tick", "thermal_generator.json");
            boolean pauseWhenFull = requireBoolean(root, "pause_burn_when_full", "thermal_generator.json");
            JsonObject fuelsObject = requireObject(root.get("fuels"), "thermal_generator.json fuels");
            if (fuelsObject.size() == 0) {
                throw new IllegalArgumentException("thermal_generator.json fuels must not be empty");
            }

            Map<Item, FuelBalance> fuels = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : fuelsObject.entrySet()) {
                ResourceLocation itemId = parseRegisteredItemId(entry.getKey(), "thermal_generator.json fuel");
                Item item = BuiltInRegistries.ITEM.get(itemId);
                JsonObject fuelObject = requireObject(entry.getValue(), "fuel " + itemId);
                int energy = requirePositiveInt(fuelObject, "energy_fe", "fuel " + itemId);
                Item remainder = null;
                if (fuelObject.has("remainder")) {
                    JsonElement remainderElement = fuelObject.get("remainder");
                    if (!remainderElement.isJsonPrimitive() || !remainderElement.getAsJsonPrimitive().isString()) {
                        throw new IllegalArgumentException("fuel " + itemId + " remainder must be an item id string");
                    }
                    ResourceLocation remainderId = parseRegisteredItemId(
                            remainderElement.getAsString(), "fuel " + itemId + " remainder");
                    remainder = BuiltInRegistries.ITEM.get(remainderId);
                }
                if (fuels.put(item, new FuelBalance(energy, remainder)) != null) {
                    throw new IllegalArgumentException("thermal_generator.json defines duplicate fuel item " + itemId);
                }
            }
            return new ThermalGeneratorBalance(capacity, generation, output, pauseWhenFull, Map.copyOf(fuels));
        } catch (RuntimeException exception) {
            ApocalypseFirstLight.LOGGER.error(
                    "[AFL ELECTRICITY] Invalid or missing machine_balance/thermal_generator.json; using safe fallback: {}",
                    exception.getMessage());
            return fallbackThermalGenerator();
        }
    }

    private static EnergyCellBalance loadEnergyCell(@Nullable JsonElement element) {
        try {
            JsonObject root = requireObject(element, "energy_cell.json");
            return new EnergyCellBalance(
                    requirePositiveInt(root, "capacity_fe", "energy_cell.json"),
                    requirePositiveInt(root, "max_receive_fe_per_tick", "energy_cell.json"),
                    requirePositiveInt(root, "max_extract_fe_per_tick", "energy_cell.json"));
        } catch (RuntimeException exception) {
            ApocalypseFirstLight.LOGGER.error(
                    "[AFL ELECTRICITY] Invalid or missing machine_balance/energy_cell.json; using safe fallback: {}",
                    exception.getMessage());
            return fallbackEnergyCell();
        }
    }

    private static EnergyBatteryBalance loadEnergyBattery(@Nullable JsonElement element) {
        try {
            JsonObject root = requireObject(element, "energy_battery.json");
            return new EnergyBatteryBalance(
                    requirePositiveInt(root, "capacity_fe", "energy_battery.json"),
                    requirePositiveInt(root, "max_receive_fe_per_tick", "energy_battery.json"),
                    requirePositiveInt(root, "max_extract_fe_per_tick", "energy_battery.json"));
        } catch (RuntimeException exception) {
            ApocalypseFirstLight.LOGGER.error(
                    "[AFL ELECTRICITY] Invalid or missing machine_balance/energy_battery.json; using safe fallback: {}",
                    exception.getMessage());
            return fallbackEnergyBattery();
        }
    }

    private static ChargingStationBalance loadChargingStation(@Nullable JsonElement element) {
        try {
            JsonObject root = requireObject(element, "charging_station.json");
            return new ChargingStationBalance(
                    requirePositiveInt(root, "capacity_fe", "charging_station.json"),
                    requirePositiveInt(root, "max_receive_fe_per_tick", "charging_station.json"),
                    requirePositiveInt(root, "charge_fe_per_tick", "charging_station.json"));
        } catch (RuntimeException exception) {
            ApocalypseFirstLight.LOGGER.error(
                    "[AFL ELECTRICITY] Invalid or missing machine_balance/charging_station.json; using safe fallback: {}",
                    exception.getMessage());
            return fallbackChargingStation();
        }
    }

    private static ApplianceBalance loadAppliance(@Nullable JsonElement element, String file, ApplianceBalance fallback) {
        try {
            JsonObject root = requireObject(element, file);
            return new ApplianceBalance(
                    requirePositiveInt(root, "capacity_fe", file),
                    requirePositiveInt(root, "max_receive_fe_per_tick", file),
                    requirePositiveInt(root, "light_fe_per_tick", file),
                    requirePositiveInt(root, "compressor_fe_per_tick", file),
                    requirePositiveInt(root, "compressor_on_ticks", file),
                    requirePositiveInt(root, "compressor_off_ticks", file));
        } catch (RuntimeException exception) {
            ApocalypseFirstLight.LOGGER.error(
                    "[AFL ELECTRICITY] Invalid or missing machine_balance/{}; using safe fallback: {}",
                    file, exception.getMessage());
            return fallback;
        }
    }

    private static ApplianceBalance loadLightsOnly(@Nullable JsonElement element, String file, ApplianceBalance fallback) {
        try {
            JsonObject root = requireObject(element, file);
            return new ApplianceBalance(
                    requirePositiveInt(root, "capacity_fe", file),
                    requirePositiveInt(root, "max_receive_fe_per_tick", file),
                    requirePositiveInt(root, "light_fe_per_tick", file), 0, 0, 0);
        } catch (RuntimeException exception) {
            ApocalypseFirstLight.LOGGER.error(
                    "[AFL ELECTRICITY] Invalid or missing machine_balance/{}; using safe fallback: {}",
                    file, exception.getMessage());
            return fallback;
        }
    }

    private static CrusherBalance loadCrusher(@Nullable JsonElement element) {
        try {
            JsonObject root = requireObject(element, "crusher.json");
            return new CrusherBalance(
                    requirePositiveInt(root, "capacity_fe", "crusher.json"),
                    requirePositiveInt(root, "max_receive_fe_per_tick", "crusher.json"),
                    requirePositiveInt(root, "work_fe_per_tick", "crusher.json"));
        } catch (RuntimeException exception) {
            ApocalypseFirstLight.LOGGER.error(
                    "[AFL ELECTRICITY] Invalid or missing machine_balance/crusher.json; using safe fallback: {}",
                    exception.getMessage());
            return fallbackCrusher();
        }
    }

    private static IndustrialFurnaceBalance loadIndustrialFurnace(@Nullable JsonElement element) {
        try {
            JsonObject root = requireObject(element, "industrial_furnace.json");
            return new IndustrialFurnaceBalance(
                    requirePositiveInt(root, "capacity_fe", "industrial_furnace.json"),
                    requirePositiveInt(root, "max_receive_fe_per_tick", "industrial_furnace.json"),
                    requirePositiveInt(root, "work_fe_per_tick_per_lane", "industrial_furnace.json"),
                    requirePositiveDouble(root, "processing_time_multiplier", "industrial_furnace.json"));
        } catch (RuntimeException exception) {
            ApocalypseFirstLight.LOGGER.error(
                    "[AFL ELECTRICITY] Invalid or missing machine_balance/industrial_furnace.json; using safe fallback: {}",
                    exception.getMessage());
            return fallbackIndustrialFurnace();
        }
    }

    private static IntakePumpBalance loadIntakePump(@Nullable JsonElement element) {
        try {
            JsonObject root = requireObject(element, "intake_pump.json");
            return new IntakePumpBalance(
                    requirePositiveInt(root, "capacity_fe", "intake_pump.json"),
                    requirePositiveInt(root, "max_receive_fe_per_tick", "intake_pump.json"),
                    requirePositiveInt(root, "work_fe_per_tick", "intake_pump.json"),
                    requirePositiveInt(root, "pump_mb_per_tick", "intake_pump.json"));
        } catch (RuntimeException exception) {
            ApocalypseFirstLight.LOGGER.error(
                    "[AFL ELECTRICITY] Invalid or missing machine_balance/intake_pump.json; using safe fallback: {}",
                    exception.getMessage());
            return fallbackIntakePump();
        }
    }

    private static CompressorBalance loadCompressor(@Nullable JsonElement element) {
        try {
            JsonObject root = requireObject(element, "compressor.json");
            return new CompressorBalance(
                    requirePositiveInt(root, "capacity_fe", "compressor.json"),
                    requirePositiveInt(root, "max_receive_fe_per_tick", "compressor.json"),
                    requirePositiveInt(root, "work_fe_per_tick", "compressor.json"));
        } catch (RuntimeException exception) {
            ApocalypseFirstLight.LOGGER.error(
                    "[AFL ELECTRICITY] Invalid or missing machine_balance/compressor.json; using safe fallback: {}",
                    exception.getMessage());
            return fallbackCompressor();
        }
    }

    private static AlloyFurnaceBalance loadAlloyFurnace(@Nullable JsonElement element) {
        try {
            JsonObject root = requireObject(element, "alloy_furnace.json");
            return new AlloyFurnaceBalance(
                    requirePositiveInt(root, "capacity_fe", "alloy_furnace.json"),
                    requirePositiveInt(root, "max_receive_fe_per_tick", "alloy_furnace.json"),
                    requirePositiveInt(root, "work_fe_per_tick", "alloy_furnace.json"));
        } catch (RuntimeException exception) {
            ApocalypseFirstLight.LOGGER.error(
                    "[AFL ELECTRICITY] Invalid or missing machine_balance/alloy_furnace.json; using safe fallback: {}",
                    exception.getMessage());
            return fallbackAlloyFurnace();
        }
    }

    private static ChemicalReactorBalance loadChemicalReactor(@Nullable JsonElement element) {
        try {
            JsonObject root = requireObject(element, "chemical_reactor.json");
            return new ChemicalReactorBalance(
                    requirePositiveInt(root, "capacity_fe", "chemical_reactor.json"),
                    requirePositiveInt(root, "max_receive_fe_per_tick", "chemical_reactor.json"),
                    requirePositiveInt(root, "work_fe_per_tick", "chemical_reactor.json"));
        } catch (RuntimeException exception) {
            ApocalypseFirstLight.LOGGER.error(
                    "[AFL ELECTRICITY] Invalid or missing machine_balance/chemical_reactor.json; using safe fallback: {}",
                    exception.getMessage());
            return fallbackChemicalReactor();
        }
    }

    private static ThermalGeneratorBalance fallbackThermalGenerator() {
        return new ThermalGeneratorBalance(100_000, 16, 16, true, Map.of(
                Items.COAL, new FuelBalance(500, null),
                Items.CHARCOAL, new FuelBalance(500, null),
                Items.COAL_BLOCK, new FuelBalance(5_000, null),
                Items.LAVA_BUCKET, new FuelBalance(20_000, Items.BUCKET)));
    }

    private static EnergyCellBalance fallbackEnergyCell() {
        return new EnergyCellBalance(1_000_000, 128, 128);
    }

    /** Same values as machine_balance/energy_battery.json; also what clients use without the server's data. */
    private static EnergyBatteryBalance fallbackEnergyBattery() {
        return new EnergyBatteryBalance(50_000, 128, 128);
    }

    /** Same values as machine_balance/charging_station.json. */
    private static ChargingStationBalance fallbackChargingStation() {
        return new ChargingStationBalance(10_000, 256, 128);
    }

    /** Same values as machine_balance/beverage_cooler.json. */
    private static ApplianceBalance fallbackBeverageCooler() {
        return new ApplianceBalance(20, 32, 1, 4, 400, 800);
    }

    /** Same values as machine_balance/chest_freezer.json. */
    private static ApplianceBalance fallbackChestFreezer() {
        return new ApplianceBalance(20, 32, 1, 6, 600, 600);
    }

    /** Same values as machine_balance/vending_machine.json (lights only). */
    private static ApplianceBalance fallbackVendingMachine() {
        return new ApplianceBalance(20, 32, 1, 0, 0, 0);
    }

    /** Same values as machine_balance/water_dispenser.json (lights only). */
    private static ApplianceBalance fallbackWaterDispenser() {
        return new ApplianceBalance(20, 32, 1, 0, 0, 0);
    }

    /** Same values as machine_balance/fuel_dispenser.json (lights only). */
    private static ApplianceBalance fallbackFuelDispenser() {
        return new ApplianceBalance(40, 32, 2, 0, 0, 0);
    }

    /** Same values as machine_balance/fuel_canopy.json (lights only, light_fe_per_tick per lamp). */
    private static ApplianceBalance fallbackFuelCanopy() {
        return new ApplianceBalance(2000, 200, 1, 0, 0, 0);
    }

    private static CrusherBalance fallbackCrusher() {
        return new CrusherBalance(20_000, 32, 16);
    }

    private static IndustrialFurnaceBalance fallbackIndustrialFurnace() {
        return new IndustrialFurnaceBalance(60_000, 128, 24, 0.5D);
    }

    /** Same values as machine_balance/intake_pump.json. */
    private static IntakePumpBalance fallbackIntakePump() {
        return new IntakePumpBalance(2000, 32, 8, 5);
    }

    private static CompressorBalance fallbackCompressor() {
        return new CompressorBalance(20_000, 32, 16);
    }

    private static AlloyFurnaceBalance fallbackAlloyFurnace() {
        return new AlloyFurnaceBalance(40_000, 64, 24);
    }

    private static ChemicalReactorBalance fallbackChemicalReactor() {
        return new ChemicalReactorBalance(60_000, 128, 32);
    }

    private static JsonObject requireObject(@Nullable JsonElement element, String context) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(context + " must be a JSON object");
        }
        return element.getAsJsonObject();
    }

    private static int requirePositiveInt(JsonObject object, String field, String context) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(context + " missing integer field " + field);
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw new IllegalArgumentException(context + " field " + field + " must be an integer");
        }
        try {
            int value = new BigDecimal(primitive.getAsString()).intValueExact();
            if (value <= 0) {
                throw new IllegalArgumentException(context + " field " + field + " must be > 0");
            }
            return value;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(context + " field " + field + " must be a 32-bit integer");
        }
    }

    private static double requirePositiveDouble(JsonObject object, String field, String context) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(context + " missing numeric field " + field);
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value) || value <= 0.0D) {
            throw new IllegalArgumentException(context + " field " + field + " must be finite and > 0");
        }
        return value;
    }

    private static boolean requireBoolean(JsonObject object, String field, String context) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(context + " field " + field + " must be a boolean");
        }
        return element.getAsBoolean();
    }

    private static ResourceLocation parseRegisteredItemId(String value, String context) {
        ResourceLocation itemId = ResourceLocation.tryParse(value);
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)
                || BuiltInRegistries.ITEM.get(itemId) == Items.AIR) {
            throw new IllegalArgumentException(context + " references unknown item " + value);
        }
        return itemId;
    }
}
