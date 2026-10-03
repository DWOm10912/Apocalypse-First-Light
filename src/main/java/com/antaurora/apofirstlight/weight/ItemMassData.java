package com.antaurora.apofirstlight.weight;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.math.BigDecimal;
import java.util.*;

/** Server-only definitions. Fresh reload input replaces old rules; tags resolve after server tag binding. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class ItemMassData {
    public record Unit(long grams, boolean estimated, String source) {}
    public record Gun(Unit receiver, Unit magazine) {}
    public record Policy(long fallbackGrams, long comfortGrams, double onset, double severe) {}
    public record Snapshot(long revision, Policy policy, Map<ResourceLocation, Unit> units,
                           Map<ResourceLocation, Gun> guns) {
        public Snapshot { units = Map.copyOf(units); guns = Map.copyOf(guns); }
        public Unit unit(ResourceLocation id) {
            return units.getOrDefault(id, new Unit(policy.fallbackGrams(), true, "fallback:" + id));
        }
    }
    private record TagRule(ResourceLocation tag, int priority, Unit unit) {}
    private record Rules(Map<ResourceLocation, Unit> items, Map<ResourceLocation, Gun> guns,
                         List<TagRule> tags, Policy policy) {}
    // Emergency/test defaults, not balanced gameplay limits. Packaged JSON supplies the same policy.
    private static final Policy DEFAULT = new Policy(250, 30000, .75, 2.0);
    private static volatile Rules pending = new Rules(Map.of(), Map.of(), List.of(), DEFAULT);
    private static volatile Snapshot current = new Snapshot(0, DEFAULT, Map.of(), Map.of());
    private static boolean needsPublish = true;
    private ItemMassData() {}
    public static Snapshot snapshot() { return current; }

    private static long kg(JsonObject o, String key) {
        var p = o.get(key);
        if (p == null || !p.isJsonPrimitive() || !p.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException(key + " must be numeric kg");
        long grams = new BigDecimal(p.getAsString()).movePointRight(3).longValueExact();
        if (grams < 0) throw new IllegalArgumentException(key + " must be nonnegative");
        return grams;
    }
    private static Unit unit(JsonObject o, String key, String source) {
        return new Unit(kg(o, key), o.has("estimated") && o.get("estimated").getAsBoolean(), source);
    }
    private static ResourceLocation itemId(String text) {
        var id = new ResourceLocation(text);
        if (!ForgeRegistries.ITEMS.containsKey(id)) throw new IllegalArgumentException("Unknown item " + id);
        return id;
    }
    private static <T> void unique(Map<ResourceLocation,T> map, ResourceLocation id, T value) {
        if (map.putIfAbsent(id, value) != null) throw new IllegalArgumentException("Duplicate rule " + id);
    }
    @SubscribeEvent public static void register(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "item_mass") {
            @Override protected void apply(Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
                Map<ResourceLocation, Unit> items = new HashMap<>();
                Map<ResourceLocation, Gun> guns = new HashMap<>();
                List<TagRule> tags = new ArrayList<>();
                Policy policy = DEFAULT;
                boolean policySeen = false;
                // Deterministic file precedence for duplicate invalid entries; each file is validated atomically.
                for (var id : new TreeSet<>(input.keySet())) {
                    try {
                        var root = input.get(id).getAsJsonObject();
                        if (new BigDecimal(root.get("format_version").getAsString()).intValueExact() != 1)
                            throw new IllegalArgumentException("format_version must be 1");
                        var nextItems = new HashMap<>(items); var nextGuns = new HashMap<>(guns);
                        var nextTags = new ArrayList<>(tags); Policy nextPolicy = policy;
                        if (root.has("items")) for (var entry : root.getAsJsonObject("items").entrySet())
                            unique(nextItems, itemId(entry.getKey()), unit(entry.getValue().getAsJsonObject(), "unit_mass_kg", "item:" + entry.getKey()));
                        if (root.has("native_guns")) for (var entry : root.getAsJsonObject("native_guns").entrySet()) {
                            var g = entry.getValue().getAsJsonObject();
                            unique(nextGuns, new ResourceLocation(entry.getKey()), new Gun(
                                    unit(g, "receiver_mass_kg", "receiver:" + entry.getKey()),
                                    unit(g, "default_magazine_empty_mass_kg", "default_magazine:" + entry.getKey())));
                        }
                        if (root.has("tag_defaults")) for (var entry : root.getAsJsonArray("tag_defaults")) {
                            var t = entry.getAsJsonObject();
                            nextTags.add(new TagRule(new ResourceLocation(t.get("tag").getAsString()),
                                    new BigDecimal(t.get("priority").getAsString()).intValueExact(),
                                    unit(t, "unit_mass_kg", "tag:" + t.get("tag").getAsString())));
                        }
                        if (root.has("policy")) {
                            if (policySeen) throw new IllegalArgumentException("Multiple policy records");
                            var p = root.getAsJsonObject("policy");
                            long fallback = kg(p, "fallback_unit_mass_kg"), comfort = kg(p, "comfort_capacity_kg");
                            double onset = p.get("severity_onset_ratio").getAsDouble(), severe = p.get("severe_ratio").getAsDouble();
                            if (fallback <= 0 || comfort <= 0 || !Double.isFinite(onset) || !Double.isFinite(severe)
                                    || onset < 0 || onset >= 1 || severe <= 1) throw new IllegalArgumentException("Invalid policy");
                            nextPolicy = new Policy(fallback, comfort, onset, severe);
                        }
                        items = nextItems; guns = nextGuns; tags = nextTags; policy = nextPolicy;
                        policySeen |= root.has("policy");
                    } catch (RuntimeException ex) {
                        ApocalypseFirstLight.LOGGER.error("[AFL WEIGHT] Rejected {} (no old rule retained): {}", id, ex.getMessage());
                    }
                }
                pending = new Rules(Map.copyOf(items), Map.copyOf(guns), List.copyOf(tags), policy);
                needsPublish = true;
            }
        });
    }
    private static void publish() {
        Rules rules = pending;
        Map<ResourceLocation, Unit> resolved = new HashMap<>(rules.items());
        for (var item : ForgeRegistries.ITEMS.getValues()) {
            var id = ForgeRegistries.ITEMS.getKey(item);
            if (resolved.containsKey(id)) continue;
            List<TagRule> matches = rules.tags().stream().filter(r -> item.builtInRegistryHolder()
                    .is(TagKey.create(Registries.ITEM, r.tag()))).toList();
            if (matches.isEmpty()) continue;
            int priority = matches.stream().mapToInt(TagRule::priority).max().orElseThrow();
            var winners = matches.stream().filter(r -> r.priority() == priority).sorted(Comparator.comparing(r -> r.tag().toString())).toList();
            long grams = winners.get(0).unit().grams();
            if (winners.stream().anyMatch(r -> r.unit().grams() != grams)) {
                ApocalypseFirstLight.LOGGER.warn("[AFL WEIGHT] Conflicting tag masses for {} at priority {}; using fallback", id, priority);
                resolved.put(id, new Unit(rules.policy().fallbackGrams(), true, "fallback:tag_conflict:" + id));
            } else resolved.put(id, new Unit(grams, winners.stream().anyMatch(r -> r.unit().estimated()), winners.get(0).unit().source()));
        }
        current = new Snapshot(current.revision() + 1, rules.policy(), resolved, rules.guns());
        needsPublish = false;
        PlayerWeightRuntime.invalidateAll();
    }
    @SubscribeEvent public static void sync(OnDatapackSyncEvent event) {
        // Forge fires this after the completed reload, when item tags are available.
        if (needsPublish || event.getPlayer() == null) publish();
        for (var player : event.getPlayer() == null ? event.getPlayerList().getPlayers() : List.of(event.getPlayer())) {
            WeightPackets.sendPolicy(player, current);
            WeightPackets.sendData(player, current);
            PlayerWeightRuntime.force(player);
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        current = new Snapshot(0, DEFAULT, Map.of(), Map.of());
        pending = new Rules(Map.of(), Map.of(), List.of(), DEFAULT); needsPublish = true;
    }
}
