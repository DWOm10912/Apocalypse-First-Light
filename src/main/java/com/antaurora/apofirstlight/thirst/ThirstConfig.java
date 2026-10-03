package com.antaurora.apofirstlight.thirst;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/**
 * Thirst V1 numbers (docs/项目内容/01 - 设计/生存/口渴.md), from data/apocalypse_firstlight/thirst/thirst_v1.json
 * (/reload). Fields default to the same values; a file that does not parse or fails the checks is rejected and the
 * previous numbers stay.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class ThirstConfig {
    public static final class Sickness {
        public double chanceBottle = 0.2, chanceSip = 0.05, durationSeconds = 300, thirstMultiplier = 2;
        public double extraExhaustionPerSecond = 0.05, staminaRegen = 0.75;
        public double symptomMinSeconds = 60, symptomMaxSeconds = 90, symptomThirst = 8, symptomNauseaSeconds = 4;
        public int symptomFood = 2;
    }

    public double max = 100, basePerHour = 100, bottle = 20, sip = 5;
    /** thirst lost per 100 stamina spent (the naming policy would not split the digits) */
    @SerializedName("exertion_per_100_stamina") public double exertionPer100Stamina = 3;
    public int sipCooldownTicks = 10;
    /** item id → thirst restored when it is eaten or drunk */
    public Map<String, Double> foods = Map.of();
    public double thirstyBelow = 30, dehydratedBelow = 15, staminaRegenThirsty = 0.75, staminaRegenDehydrated = 0.5;
    public double damageIntervalSeconds = 10;
    public Sickness sickness = new Sickness();
    /** By contamination level 0-5: cumulative dose (RU) added by drinking a bottle / one sip. */
    public double[] dosePerBottle = {0, 0.02, 0.05, 0.15, 0.4, 1.0};
    public double[] dosePerSip = {0, 0.005, 0.0125, 0.04, 0.1, 0.25};

    private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    private static volatile ThirstConfig current = new ThirstConfig();
    public static ThirstConfig get() { return current; }

    /** Thirst restored by drinking / eating one of this item: the water bottles, or the "foods" table; 0 otherwise. */
    public double hydration(net.minecraft.world.item.ItemStack stack) {
        if (stack.getItem() instanceof ThirstWaterItem) return bottle;
        Double amount = foods.get(String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())));
        return amount == null ? 0 : amount;
    }

    public double dose(int level, boolean sip) {
        double[] table = sip ? dosePerSip : dosePerBottle;
        return level <= 0 || table.length == 0 ? 0 : table[Math.min(level, table.length - 1)];
    }

    private void validate() {
        if (!(max > 0) || basePerHour < 0 || exertionPer100Stamina < 0 || bottle < 0 || sip < 0 || sipCooldownTicks < 0
                || dehydratedBelow < 0 || thirstyBelow < dehydratedBelow || damageIntervalSeconds <= 0 || sickness == null
                || sickness.symptomMinSeconds <= 0 || sickness.symptomMaxSeconds < sickness.symptomMinSeconds
                || dosePerBottle == null || dosePerSip == null)
            throw new IllegalArgumentException("invalid thirst numbers");
        if (foods == null) foods = Map.of();
    }

    @SubscribeEvent public static void register(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "thirst") {
            @Override protected void apply(Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
                ThirstConfig loaded = null;
                for (var id : new TreeSet<>(input.keySet())) {
                    try {
                        var config = GSON.fromJson(input.get(id), ThirstConfig.class);
                        config.validate();
                        loaded = config;
                    } catch (RuntimeException ex) {
                        ApocalypseFirstLight.LOGGER.error("[AFL THIRST] Rejected {}: {}", id, ex.getMessage());
                    }
                }
                if (loaded != null) current = loaded;
            }
        });
    }
}
