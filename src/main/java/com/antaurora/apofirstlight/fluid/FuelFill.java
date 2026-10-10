package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fuel filled at random where a building is placed (A1 export policy, 2026-10-09; docs/worldgen/fuel_stop_a1_design_v1.md
 * "导出 NBT 时的状态"). An exported building holds no fuel: authoring/ExportState swaps each fuel holder's fuel for a
 * marker ({@link #KEY}) naming its fill rule, and for a container the fuel it held. The holder rolls the marker the first
 * time it is in a server level (its load with a level, else its onLoad) and drops it. So it works like a vanilla chest's
 * loot table: rolled where it lands, whatever placed it.
 * <p>
 * Rules: {@code data/<namespace>/fuel_fills/<rule>.json} = {@code {"empty_chance": 0.2, "min": 0.05, "max": 0.4,
 * "unit": "fraction" | "litres", "fluids": [{"fluid": id, "weight": n}]}}. {@code fluids} is used only by a holder whose
 * fuel is neither fixed by its block nor recorded in the marker. The roll is seeded by the world seed and the position:
 * one world, one result.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FuelFill {
    public static final String KEY = "AflFuelFill", RULE = "Rule", FLUID = "Fluid";
    public static final ResourceLocation UNDERGROUND_TANK = id("underground_tank"), FUEL_CONTAINER = id("fuel_container"),
            PORTABLE_GENERATOR = id("portable_generator");
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<ResourceLocation, Rule> rules = Map.of();

    private FuelFill() {
    }

    public record Rule(double emptyChance, double min, double max, boolean litres, List<Weighted> fluids) {
    }

    public record Weighted(ResourceLocation fluid, int weight) {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, path);
    }

    /** The marker for a holder: its rule, and the fuel it held when the building was exported (null: none or fixed). */
    public static CompoundTag marker(ResourceLocation rule, @Nullable ResourceLocation fluid) {
        CompoundTag tag = new CompoundTag();
        tag.putString(RULE, rule.toString());
        if (fluid != null) tag.putString(FLUID, fluid.toString());
        return tag;
    }

    /**
     * Rolls the marker into {@code tank}: {@code fixed} is the holder's own fuel (a tank's block, the generator's diesel);
     * null takes the marker's fuel, else the rule's weighted pick. False while the rules are not loaded yet (keep the
     * marker); true once it is used up (an unknown rule or fuel leaves the holder empty, logged).
     */
    public static boolean fill(ServerLevel level, BlockPos pos, CompoundTag marker, FluidTank tank, @Nullable Fluid fixed) {
        Map<ResourceLocation, Rule> loaded = rules;
        if (loaded.isEmpty()) return false;
        ResourceLocation ruleId = ResourceLocation.tryParse(marker.getString(RULE));
        Rule rule = ruleId == null ? null : loaded.get(ruleId);
        if (rule == null) {
            ApocalypseFirstLight.LOGGER.warn("[AFL FUEL FILL] unknown rule {} at {}; left empty", marker.getString(RULE), pos.toShortString());
            return true;
        }
        RandomSource random = RandomSource.create(level.getSeed() ^ pos.asLong() * 0x9E3779B97F4A7C15L);
        if (random.nextDouble() < rule.emptyChance()) return true;
        Fluid fluid = fixed;
        if (fluid == null && marker.contains(FLUID)) fluid = ForgeRegistries.FLUIDS.getValue(ResourceLocation.tryParse(marker.getString(FLUID)));
        if (fluid == null) fluid = pick(rule, random);
        if (fluid == null) {
            ApocalypseFirstLight.LOGGER.warn("[AFL FUEL FILL] no fuel for rule {} at {}; left empty", ruleId, pos.toShortString());
            return true;
        }
        double share = rule.min() + (rule.max() - rule.min()) * random.nextDouble();
        int litres = (int) Math.round(rule.litres() ? share : share * tank.getCapacity());   // 1 mB = 1 L
        litres = Math.max(0, Math.min(tank.getCapacity(), litres));
        if (litres > 0) tank.setFluid(new FluidStack(fluid, litres));
        return true;
    }

    private static @Nullable Fluid pick(Rule rule, RandomSource random) {
        int total = 0;
        for (Weighted w : rule.fluids()) total += Math.max(0, w.weight());
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (Weighted w : rule.fluids()) {
            roll -= Math.max(0, w.weight());
            if (roll < 0) return ForgeRegistries.FLUIDS.getValue(w.fluid());
        }
        return null;
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new Listener());
    }

    private static final class Listener extends SimpleJsonResourceReloadListener {
        private Listener() {
            super(GSON, "fuel_fills");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, Rule> loaded = new HashMap<>();
            resources.forEach((file, json) -> {
                try {
                    JsonObject o = GsonHelper.convertToJsonObject(json, "fuel fill");
                    List<Weighted> fluids = new ArrayList<>();
                    if (o.has("fluids")) for (JsonElement e : GsonHelper.getAsJsonArray(o, "fluids")) {
                        JsonObject f = GsonHelper.convertToJsonObject(e, "fuel fill fluid");
                        fluids.add(new Weighted(new ResourceLocation(GsonHelper.getAsString(f, "fluid")), GsonHelper.getAsInt(f, "weight", 1)));
                    }
                    double min = GsonHelper.getAsDouble(o, "min"), max = GsonHelper.getAsDouble(o, "max");
                    String unit = GsonHelper.getAsString(o, "unit", "fraction");
                    if (!unit.equals("fraction") && !unit.equals("litres")) throw new IllegalArgumentException("unit must be fraction or litres");
                    if (min < 0 || max < min) throw new IllegalArgumentException("need 0 <= min <= max");
                    loaded.put(file, new Rule(GsonHelper.getAsDouble(o, "empty_chance", 0), min, max, unit.equals("litres"), List.copyOf(fluids)));
                } catch (RuntimeException e) {
                    ApocalypseFirstLight.LOGGER.error("[AFL FUEL FILL] bad rule {}: {}", file, e.getMessage());
                }
            });
            rules = Map.copyOf(loaded);
            ApocalypseFirstLight.LOGGER.info("[AFL FUEL FILL] {} rules", loaded.size());
        }
    }
}
