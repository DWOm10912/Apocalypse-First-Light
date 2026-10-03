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
    /** One point of the penalty curve: at this load ratio (load / comfort), movement speed and jump velocity × these. */
    public record CurvePoint(double ratio, double speed, double jump) {}
    /**
     * Server-only movement penalties (policy.penalties); the client receives the resulting multipliers in its state.
     * Worn armor counts armorLoadFactor of its mass toward the load. Sprint stops at sprintBlockRatio and is allowed
     * again below sprintResumeRatio.
     */
    public record Penalties(double armorLoadFactor, List<CurvePoint> curve, double sprintBlockRatio, double sprintResumeRatio) {
        public static final Penalties NONE = new Penalties(1.0, List.of(), Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        public Penalties { curve = List.copyOf(curve); }
        public double speed(double ratio) { return at(ratio, true); }
        public double jump(double ratio) { return at(ratio, false); }
        /** Linear between points; below the first and above the last, that point's value. */
        private double at(double ratio, boolean speed) {
            if (curve.isEmpty()) return 1.0;
            CurvePoint previous = null;
            for (var point : curve) {
                if (ratio <= point.ratio()) {
                    if (previous == null) return speed ? point.speed() : point.jump();
                    double t = (ratio - previous.ratio()) / (point.ratio() - previous.ratio());
                    double a = speed ? previous.speed() : previous.jump(), b = speed ? point.speed() : point.jump();
                    return a + (b - a) * t;
                }
                previous = point;
            }
            return speed ? previous.speed() : previous.jump();
        }
    }
    /** carry: item → carry factor (awkward bulky furniture weighs more as load than as mass), from carry_factors tags. */
    public record Snapshot(long revision, Policy policy, Map<ResourceLocation, Unit> units,
                           Map<ResourceLocation, Gun> guns, Penalties penalties, Map<ResourceLocation, Double> carry) {
        public Snapshot { units = Map.copyOf(units); guns = Map.copyOf(guns); carry = Map.copyOf(carry); }
        /** The client's copy: masses only. */
        public Snapshot(long revision, Policy policy, Map<ResourceLocation, Unit> units, Map<ResourceLocation, Gun> guns) {
            this(revision, policy, units, guns, Penalties.NONE, Map.of());
        }
        public Unit unit(ResourceLocation id) {
            return units.getOrDefault(id, new Unit(policy.fallbackGrams(), true, "fallback:" + id));
        }
        public double carryFactor(ResourceLocation id) { return carry.getOrDefault(id, 1.0); }
    }
    private record TagRule(ResourceLocation tag, int priority, Unit unit) {}
    private record CarryRule(ResourceLocation tag, double factor) {}
    private record Rules(Map<ResourceLocation, Unit> items, Map<ResourceLocation, Gun> guns,
                         List<TagRule> tags, List<CarryRule> carry, Policy policy, Penalties penalties) {}
    // Emergency/test defaults, not balanced gameplay limits. Packaged JSON supplies the same policy.
    private static final Policy DEFAULT = new Policy(250, 30000, 1.0, 2.0);
    private static volatile Rules pending = new Rules(Map.of(), Map.of(), List.of(), List.of(), DEFAULT, Penalties.NONE);
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
    private static double number(JsonObject o, String key) {
        var p = o.get(key);
        if (p == null || !p.isJsonPrimitive() || !p.getAsJsonPrimitive().isNumber() || !Double.isFinite(p.getAsDouble()))
            throw new IllegalArgumentException(key + " must be a finite number");
        return p.getAsDouble();
    }
    private static Penalties penalties(JsonObject o) {
        double armor = number(o, "armor_load_factor");
        List<CurvePoint> curve = new ArrayList<>();
        for (var entry : o.getAsJsonArray("curve")) {
            var c = entry.getAsJsonObject();
            var point = new CurvePoint(number(c, "load_ratio"), number(c, "speed"), number(c, "jump"));
            if (point.ratio() < 0 || point.speed() <= 0 || point.speed() > 1 || point.jump() <= 0 || point.jump() > 1
                    || (!curve.isEmpty() && point.ratio() <= curve.get(curve.size() - 1).ratio()))
                throw new IllegalArgumentException("Invalid penalty curve point " + c);
            curve.add(point);
        }
        double block = number(o, "sprint_block_ratio"), resume = number(o, "sprint_resume_ratio");
        if (armor <= 0 || curve.isEmpty() || resume <= 0 || resume > block) throw new IllegalArgumentException("Invalid penalties");
        return new Penalties(armor, curve, block, resume);
    }
    @SubscribeEvent public static void register(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "item_mass") {
            @Override protected void apply(Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
                Map<ResourceLocation, Unit> items = new HashMap<>();
                Map<ResourceLocation, Gun> guns = new HashMap<>();
                List<TagRule> tags = new ArrayList<>();
                List<CarryRule> carry = new ArrayList<>();
                Policy policy = DEFAULT;
                Penalties penalties = Penalties.NONE;
                boolean policySeen = false;
                // Deterministic file precedence for duplicate invalid entries; each file is validated atomically.
                for (var id : new TreeSet<>(input.keySet())) {
                    try {
                        var root = input.get(id).getAsJsonObject();
                        if (new BigDecimal(root.get("format_version").getAsString()).intValueExact() != 1)
                            throw new IllegalArgumentException("format_version must be 1");
                        var nextItems = new HashMap<>(items); var nextGuns = new HashMap<>(guns);
                        var nextTags = new ArrayList<>(tags); var nextCarry = new ArrayList<>(carry);
                        Policy nextPolicy = policy; Penalties nextPenalties = penalties;
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
                        if (root.has("carry_factors")) for (var entry : root.getAsJsonArray("carry_factors")) {
                            var c = entry.getAsJsonObject();
                            double factor = number(c, "factor");
                            if (factor <= 0) throw new IllegalArgumentException("carry factor must be positive");
                            nextCarry.add(new CarryRule(new ResourceLocation(c.get("tag").getAsString()), factor));
                        }
                        if (root.has("policy")) {
                            if (policySeen) throw new IllegalArgumentException("Multiple policy records");
                            var p = root.getAsJsonObject("policy");
                            long fallback = kg(p, "fallback_unit_mass_kg"), comfort = kg(p, "comfort_capacity_kg");
                            double onset = p.get("severity_onset_ratio").getAsDouble(), severe = p.get("severe_ratio").getAsDouble();
                            // onset may be the comfort itself (1.0): penalties start above the comfort capacity
                            if (fallback <= 0 || comfort <= 0 || !Double.isFinite(onset) || !Double.isFinite(severe)
                                    || onset < 0 || severe <= onset) throw new IllegalArgumentException("Invalid policy");
                            nextPolicy = new Policy(fallback, comfort, onset, severe);
                            nextPenalties = p.has("penalties") ? penalties(p.getAsJsonObject("penalties")) : Penalties.NONE;
                        }
                        items = nextItems; guns = nextGuns; tags = nextTags; carry = nextCarry;
                        policy = nextPolicy; penalties = nextPenalties;
                        policySeen |= root.has("policy");
                    } catch (RuntimeException ex) {
                        ApocalypseFirstLight.LOGGER.error("[AFL WEIGHT] Rejected {} (no old rule retained): {}", id, ex.getMessage());
                    }
                }
                pending = new Rules(Map.copyOf(items), Map.copyOf(guns), List.copyOf(tags), List.copyOf(carry), policy, penalties);
                needsPublish = true;
            }
        });
    }
    private static void publish() {
        Rules rules = pending;
        Map<ResourceLocation, Unit> resolved = new HashMap<>(rules.items());
        Map<ResourceLocation, Double> carry = new HashMap<>();
        for (var item : ForgeRegistries.ITEMS.getValues()) {
            var id = ForgeRegistries.ITEMS.getKey(item);
            // the largest matching carry factor
            rules.carry().stream().filter(r -> item.builtInRegistryHolder().is(TagKey.create(Registries.ITEM, r.tag())))
                    .mapToDouble(CarryRule::factor).max().ifPresent(factor -> { if (factor != 1.0) carry.put(id, factor); });
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
        current = new Snapshot(current.revision() + 1, rules.policy(), resolved, rules.guns(), rules.penalties(), carry);
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
        pending = new Rules(Map.of(), Map.of(), List.of(), List.of(), DEFAULT, Penalties.NONE); needsPublish = true;
    }
}
