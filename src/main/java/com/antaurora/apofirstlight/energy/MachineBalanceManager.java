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

    private static volatile ThermalGeneratorBalance thermalGenerator = fallbackThermalGenerator();
    private static volatile EnergyCellBalance energyCell = fallbackEnergyCell();
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
    public static void syncThermalGeneratorFuels(OnDatapackSyncEvent event) {
        Map<ResourceLocation, Integer> fuelEnergies = thermalGeneratorFuelEnergies();
        event.getPlayers().forEach(player -> AflNetwork.sendThermalGeneratorFuels(player, fuelEnergies));
    }

    public record ThermalGeneratorBalance(int capacityFe, int generationFePerTick,
                                          int maxOutputFePerTick, boolean pauseBurnWhenFull,
                                          Map<Item, FuelBalance> fuels) {
    }

    public record FuelBalance(int energyFe, @Nullable Item remainder) {
    }

    public record EnergyCellBalance(int capacityFe, int maxReceiveFePerTick, int maxExtractFePerTick) {
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
            thermalGenerator = loadedThermal;
            energyCell = loadedCell;
            revision++;

            ApocalypseFirstLight.LOGGER.info(
                    "[AFL ELECTRICITY] Thermal Generator balance: capacity={} FE, generation={} FE/t, output={} FE/t, pauseWhenFull={}, fuels={}",
                    loadedThermal.capacityFe(), loadedThermal.generationFePerTick(),
                    loadedThermal.maxOutputFePerTick(), loadedThermal.pauseBurnWhenFull(),
                    loadedThermal.fuels().size());
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL ELECTRICITY] Energy Cell balance: capacity={} FE, receive={} FE/t, extract={} FE/t (DEVELOPMENT / NOT FROZEN)",
                    loadedCell.capacityFe(), loadedCell.maxReceiveFePerTick(), loadedCell.maxExtractFePerTick());

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

    private static ThermalGeneratorBalance fallbackThermalGenerator() {
        Map<Item, FuelBalance> fuels = new LinkedHashMap<>();
        fuels.put(Items.COAL, new FuelBalance(4_000, null));
        fuels.put(Items.CHARCOAL, new FuelBalance(4_000, null));
        fuels.put(Items.COAL_BLOCK, new FuelBalance(40_000, null));
        fuels.put(Items.LAVA_BUCKET, new FuelBalance(50_000, Items.BUCKET));
        return new ThermalGeneratorBalance(100_000, 16, 16, true, Map.copyOf(fuels));
    }

    private static EnergyCellBalance fallbackEnergyCell() {
        return new EnergyCellBalance(1_000_000, 1_024, 1_024);
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
