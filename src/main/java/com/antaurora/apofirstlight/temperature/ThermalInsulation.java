package com.antaurora.apofirstlight.temperature;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/**
 * Thermal insulation of worn equipment (Temperature V1), from data/<namespace>/thermal_insulation/*.json:
 * <pre>{ "items": { "minecraft:leather_chestplate": 3.0 }, "tags": [ { "tag": "forge:armors/helmets", "value": 1.0 } ] }</pre>
 * An item's value is its own entry, else the largest matching tag entry, else 0. Only the four armour slots count in
 * V1; a future clothing system registers a Source (V1 insulation only helps against cold). Files that do not parse are
 * rejected with a log line; the other files still load.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class ThermalInsulation {
    private record TagValue(TagKey<Item> tag, double value) {}
    private static volatile Map<ResourceLocation, Double> items = Map.of();
    private static volatile List<TagValue> tags = List.of();
    private ThermalInsulation() {}

    public static double of(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Double own = items.get(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        if (own != null) return own;
        double best = 0;
        for (var t : tags) if (stack.is(t.tag())) best = Math.max(best, t.value());
        return best;
    }
    /** Another worn layer (a future clothing system, a backpack lining): warmth in °C added to the armour. None in V1. */
    public interface Source { double warmth(Player player); }
    private static final List<Source> SOURCES = new java.util.concurrent.CopyOnWriteArrayList<>();
    public static void register(Source source) { SOURCES.add(source); }

    /** Everything worn: the armour plus the registered sources. Lowers the comfort range's lower end by this many °C. */
    public static double total(Player player) {
        double total = worn(player);
        for (var source : SOURCES) total += source.warmth(player);
        return total;
    }
    /** Sum over the worn armour. */
    public static double worn(Player player) {
        double total = 0;
        for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            total += of(player.getItemBySlot(slot));
        return total;
    }

    @SubscribeEvent public static void register(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "thermal_insulation") {
            @Override protected void apply(Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
                Map<ResourceLocation, Double> nextItems = new HashMap<>();
                List<TagValue> nextTags = new ArrayList<>();
                for (var id : new TreeSet<>(input.keySet())) {
                    try {
                        var root = input.get(id).getAsJsonObject();
                        Map<ResourceLocation, Double> fileItems = new HashMap<>();
                        List<TagValue> fileTags = new ArrayList<>();
                        if (root.has("items")) for (var e : root.getAsJsonObject("items").entrySet()) {
                            double v = e.getValue().getAsDouble();
                            if (!Double.isFinite(v) || v < 0) throw new IllegalArgumentException("bad value for " + e.getKey());
                            fileItems.put(new ResourceLocation(e.getKey()), v);
                        }
                        if (root.has("tags")) for (var e : root.getAsJsonArray("tags")) {
                            var o = e.getAsJsonObject();
                            double v = o.get("value").getAsDouble();
                            if (!Double.isFinite(v) || v < 0) throw new IllegalArgumentException("bad value for " + o.get("tag"));
                            fileTags.add(new TagValue(TagKey.create(Registries.ITEM, new ResourceLocation(o.get("tag").getAsString())), v));
                        }
                        nextItems.putAll(fileItems);
                        nextTags.addAll(fileTags);
                    } catch (RuntimeException ex) {
                        ApocalypseFirstLight.LOGGER.error("[AFL TEMPERATURE] Rejected insulation {}: {}", id, ex.getMessage());
                    }
                }
                items = Map.copyOf(nextItems);
                tags = List.copyOf(nextTags);
            }
        });
    }
}
