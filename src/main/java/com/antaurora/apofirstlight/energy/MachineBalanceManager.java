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
    private static final ResourceLocation CRUSHER_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "crusher");
    private static final ResourceLocation INDUSTRIAL_FURNACE_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "industrial_furnace");
    private static final ResourceLocation COMPRESSOR_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "compressor");
    private static final ResourceLocation ALLOY_FURNACE_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "alloy_furnace");

    private static volatile ThermalGeneratorBalance thermalGenerator = fallbackThermalGenerator();
    private static volatile EnergyCellBalance energyCell = fallbackEnergyCell();
    private static volatile CrusherBalance crusher = fallbackCrusher();
    private static volatile IndustrialFurnaceBalance industrialFurnace = fallbackIndustrialFurnace();
    private static volatile CompressorBalance compressor = fallbackCompressor();
    private static volatile AlloyFurnaceBalance alloyFurnace = fallbackAlloyFurnace();
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
        event.getPlayers().forEach(player -> {
            AflNetwork.sendThermalGeneratorFuels(player, fuelEnergies);
            AflNetwork.sendCrusherBalance(player, crusherWorkFePerTick);
            AflNetwork.sendCompressorBalance(player, compressorWorkFePerTick);
            AflNetwork.sendAlloyFurnaceBalance(player, alloyFurnaceWorkFePerTick);
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

    private static final class BalanceReloadListener extends SimpleJsonResourceReloadListener {
        private BalanceReloadListener() {
            super(GSON, "machine_balance");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            ThermalGeneratorBalance loadedThermal = loadThermalGenerator(resources.get(THERMAL_GENERATOR_ID));
            EnergyCellBalance loadedCell = loadEnergyCell(resources.get(ENERGY_CELL_ID));
            CrusherBalance loadedCrusher = loadCrusher(resources.get(CRUSHER_ID));
            IndustrialFurnaceBalance loadedIndustrialFurnace =
                    loadIndustrialFurnace(resources.get(INDUSTRIAL_FURNACE_ID));
            CompressorBalance loadedCompressor = loadCompressor(resources.get(COMPRESSOR_ID));
            AlloyFurnaceBalance loadedAlloyFurnace = loadAlloyFurnace(resources.get(ALLOY_FURNACE_ID));
            thermalGenerator = loadedThermal;
            energyCell = loadedCell;
            crusher = loadedCrusher;
            industrialFurnace = loadedIndustrialFurnace;
            compressor = loadedCompressor;
            alloyFurnace = loadedAlloyFurnace;
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

    private static ThermalGeneratorBalance fallbackThermalGenerator() {
        return new ThermalGeneratorBalance(100_000, 16, 16, true, Map.of());
    }

    private static EnergyCellBalance fallbackEnergyCell() {
        return new EnergyCellBalance(1_000_000, 128, 128);
    }

    private static CrusherBalance fallbackCrusher() {
        return new CrusherBalance(20_000, 32, 16);
    }

    private static IndustrialFurnaceBalance fallbackIndustrialFurnace() {
        return new IndustrialFurnaceBalance(60_000, 128, 24, 0.5D);
    }

    private static CompressorBalance fallbackCompressor() {
        return new CompressorBalance(20_000, 32, 16);
    }

    private static AlloyFurnaceBalance fallbackAlloyFurnace() {
        return new AlloyFurnaceBalance(40_000, 64, 24);
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
