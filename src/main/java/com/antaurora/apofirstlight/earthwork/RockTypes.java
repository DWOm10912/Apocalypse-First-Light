package com.antaurora.apofirstlight.earthwork;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.NaturalRockBlock;
import com.antaurora.apofirstlight.worldgen.underground.RockType;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The natural rocks' data (U3a, docs/worldgen/underground_u3_rocks_ores_design.md 2.1), data-driven from
 * data/apocalypse_firstlight/rock_types/&lt;id&gt;.json (id = RockType#id). The built-in table below is the same as the
 * shipped files and stands in until they load (and for a file that fails).
 * <p>
 * density: the rock's, kg/m3; strike: the litres one pick strike cuts (design 3.2, 4.2: RockMining's block cut, and
 * R2's open cut scaled from granite's; test values); look: the boulder look its rocks take (EntranceRocks#look,
 * UndergroundPlan.ROCK_LOOKS); anchor: whether a rope anchor holds (RockType#anchor); stone: the item it breaks into
 * (design 3.1: a material, never a block of it).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class RockTypes {
    private static final Logger LOG = LoggerFactory.getLogger("AFL rock types");

    public record Rock(RockType type, double density, double strike, String look, boolean anchor, String stone) {}

    /** The boulder looks a rock can take (Rock R3). */
    public static final List<String> LOOKS = List.of("granite", "limestone", "sandstone", "shale");

    private static volatile Map<RockType, Rock> table = builtIn();

    private RockTypes() {}

    /** The rocks that are blocks (RockType's rocks but the coal seam, which is ore: U3b). */
    public static final List<RockType> BLOCKS = List.of(RockType.GRANITE, RockType.GNEISS, RockType.SCHIST, RockType.SANDSTONE,
            RockType.LIMESTONE, RockType.DOLOMITE, RockType.SHALE, RockType.MARL, RockType.ROCK_SALT);

    public static Map<RockType, Rock> table() { return table; }

    /** A rock type's data (null: not a rock block). */
    public static Rock of(RockType t) { return table.get(t); }

    /** A block's rock data: a natural rock's own, vanilla stone as granite (Rock R2 drew it so), else null. */
    public static Rock of(Block b) {
        if (b instanceof com.antaurora.apofirstlight.block.OreRockBlock o) return o.data;   // U3b: an ore rock's own
        if (b instanceof NaturalRockBlock r) return table.get(r.rock);
        return b == net.minecraft.world.level.block.Blocks.STONE ? table.get(RockType.GRANITE) : null;
    }

    static Map<RockType, Rock> builtIn() {
        Map<RockType, Rock> m = new EnumMap<>(RockType.class);
        put(m, RockType.GRANITE, 2700, 50, "granite", "field_stone");
        put(m, RockType.GNEISS, 2750, 50, "granite", "gneiss_stone");
        put(m, RockType.SCHIST, 2750, 60, "granite", "schist_stone");
        put(m, RockType.SANDSTONE, 2300, 60, "sandstone", "sandstone_stone");
        put(m, RockType.LIMESTONE, 2600, 60, "limestone", "limestone_stone");
        put(m, RockType.DOLOMITE, 2800, 55, "limestone", "dolomite_stone");
        put(m, RockType.SHALE, 2500, 80, "shale", "shale_shard");
        put(m, RockType.MARL, 2200, 100, "limestone", "marl_lump");
        put(m, RockType.ROCK_SALT, 2160, 85, "shale", "rock_salt_lump");
        return m;
    }

    private static void put(Map<RockType, Rock> m, RockType t, double density, double strike, String look, String stone) {
        m.put(t, new Rock(t, density, strike, look, t.anchor, ApocalypseFirstLight.MOD_ID + ":" + stone));
    }

    /** The litres a strike of R2's open cut takes from this rock against granite's (Earthwork.STRIKE_DEPTH is granite's). */
    public static double strikeScale(Rock r) {
        Rock g = table.get(RockType.GRANITE);
        return r == null || g == null || g.strike() <= 0 ? 1 : r.strike() / g.strike();
    }

    /** The item a rock breaks into (a granite stone if its id is unknown). */
    public static net.minecraft.world.item.Item stone(Rock r) {
        if (r != null) {
            var i = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(r.stone()));
            if (i != null && i != net.minecraft.world.item.Items.AIR) return i;
        }
        return com.antaurora.apofirstlight.registry.AflItems.FIELD_STONE.get();
    }

    /** The item a block of rock breaks into (vanilla stone: granite's), a granite stone for anything else. */
    public static net.minecraft.world.item.Item stone(Block b) { return stone(of(b)); }

    /** The item a boulder or loose stone of this look breaks into (Rock R3's looks are rock types). */
    public static net.minecraft.world.item.Item stone(com.antaurora.apofirstlight.boulder.BoulderRock look) {
        return stone(table.get(switch (look) {
            case LIMESTONE -> RockType.LIMESTONE;
            case SANDSTONE -> RockType.SANDSTONE;
            case SHALE -> RockType.SHALE;
            default -> RockType.GRANITE;
        }));
    }

    /** One file (no registries: the offline check reads the shipped files with it). */
    public static Rock parse(String id, JsonObject o) {
        RockType t = null;
        for (RockType r : BLOCKS) if (r.id.equals(id)) t = r;
        if (t == null) throw new IllegalArgumentException("not a rock block: " + id);
        double density = number(o, "density_kg_m3"), strike = number(o, "strike_litres");
        if (density < 1000 || density > 4000) throw new IllegalArgumentException("density_kg_m3 out of 1000..4000");
        if (strike <= 0 || strike > 1000) throw new IllegalArgumentException("strike_litres out of (0, 1000]");
        String look = o.get("boulder_look").getAsString();
        if (!LOOKS.contains(look)) throw new IllegalArgumentException("boulder_look must be one of " + LOOKS);
        String stone = o.get("stone").getAsString();
        if (!stone.contains(":")) throw new IllegalArgumentException("stone must be an item id");
        return new Rock(t, density, strike, look, o.get("anchor").getAsBoolean(), stone);
    }

    private static double number(JsonObject o, String key) {
        double d = o.get(key).getAsDouble();
        if (!Double.isFinite(d)) throw new IllegalArgumentException(key + " must be a finite number");
        return d;
    }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "rock_types") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
                Map<RockType, Rock> read = builtIn();
                for (var en : input.entrySet()) {
                    if (!en.getKey().getNamespace().equals(ApocalypseFirstLight.MOD_ID)) continue;
                    try {
                        Rock r = parse(en.getKey().getPath(), en.getValue().getAsJsonObject());
                        read.put(r.type(), r);
                    } catch (RuntimeException ex) {
                        LOG.error("rock type {} skipped: {}", en.getKey(), ex.getMessage());
                    }
                }
                table = read;
            }
        });
    }
}
