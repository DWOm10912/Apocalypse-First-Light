package com.antaurora.apofirstlight.map;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.PrairieRoadMapBlock;
import com.antaurora.apofirstlight.cabin.EmergencyRadio;
import com.antaurora.apofirstlight.cabin.WoodFurniture;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.home.ForestHomeRuntime;
import com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime;
import com.antaurora.apofirstlight.worldgen.network.road.RoadCounty;
import com.antaurora.apofirstlight.worldgen.network.road.RoadDesign;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.structure.AflTemplatePlacer;
import com.antaurora.apofirstlight.worldgen.terrain.v2.EcologyBiomes;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The start map (AFL Map V1, docs/ui/afl_map_v1_plan.md 5.4; user 2026-10-21: "开局地图摊在老宅厨房桌上，拿起来就揭开 Linden
 * 一带和去的路线"): a PRAIRIE road map spread on the forest home's table (PrairieRoadMapBlock, put there once the table's
 * chunk is loaded); picked up it is a map whose record already knows the way to Linden: the home and its driveway, the
 * shortest way along the built roads from the driveway's mouth to Linden's limit, and Linden itself, the way traced and
 * Linden circled in pencil (MapStore marks). All of it from the plan, no chunk is generated: the chunks' ground is sampled
 * off the terrain plan and its ecology (as MapConcept does) on a worker; walking there later samples the real ground over it.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class StartMap {
    private StartMap() {}

    /** The town the map is for (Linden). */
    public static final String TOWN = "valley_town";
    /** Item tag of a map's kind (AflMapItem names and describes it): the PRAIRIE road map. */
    public static final String KIND = "AflMapKind", ROAD_MAP = "prairie_road";
    /** The route graph: stations every STEP along each road; two roads meet where stations come within JOIN and JOIN_DY (m). */
    static final double STEP = 16, JOIN = 14, JOIN_DY = 2.5, JOIN_COST = 4;
    /** A road's end (a T junction: it stops at the other road's edge) meets the nearest other road within END_JOIN and END_DY (m). */
    static final double END_JOIN = 40, END_DY = 4;
    /** With no road within START of the mouth (a house without a county road) the way starts at the nearest station within FAR_START, straight across (m). */
    static final double FAR_START = 800;
    /** The search starts on the county road within START of the driveway's mouth and ends within TOWN_REACH of Linden's limit (m). */
    public static final double START = 40, TOWN_REACH = 24;
    /**
     * What the map shows (m): chunks within ROUTE_BAND of the route, HOME_RADIUS of the house, and all of Linden (the
     * home's people shop there often, user 2026-10-21): TOWN_MARGIN round the town's extent (its limit and every lot).
     */
    static final double ROUTE_BAND = 64, HOME_RADIUS = 160, TOWN_MARGIN = 96;
    /** The traced line keeps a point every MARK_STEP (m); the circle round the limit's box, half sides times CIRCLE_K plus CIRCLE_ADD. */
    static final double MARK_STEP = 8, CIRCLE_K = 1.08, CIRCLE_ADD = 24;
    /** The table the map lies on: the cell over the dining table by the kitchen wall (template frame), the chair west of it. */
    static final BlockPos TABLE_CELL = new BlockPos(21, 5, 18);
    static final Direction TABLE_FACING = Direction.EAST;

    /**
     * What the map knows: the traced way {x0, z0, x1, z1, ...} from the house (empty: none found), Linden's circle
     * {cx, cz, rx, rz} (null: no Linden), the chunks it shows, the way's length (m) and a line for the log.
     */
    public record Plan(double[] route, double[] circle, Set<Long> chunks, double length, String report) {}

    // ------------------------------------------------------------------ the plan (pure, also offline)

    public static Plan plan(List<RoadDesign> roads, List<TownPlan> towns, RoadCounty.Home home) {
        TownPlan town = null;
        for (TownPlan p : towns) if (TOWN.equals(p.town)) town = p;
        List<double[]> way = new ArrayList<>();
        if (home != null) {
            if (home.node() != null && home.node().length >= 3) way.add(new double[]{home.node()[0], home.node()[2]});
            double[] d = home.drive();
            if (d != null) for (int i = 0; i + 2 < d.length; i += 3) way.add(new double[]{d[i], d[i + 1]});
        }
        String found = "no house";
        if (home != null && town != null && !way.isEmpty()) {
            double[] mouth = way.get(way.size() - 1);
            List<double[]> roadWay = search(roads, town, home.road(), mouth);
            found = roadWay == null ? "no way to Linden" : "way found";
            if (roadWay != null) way.addAll(roadWay);
        } else if (town == null) found = "no Linden";
        double[] route = simplify(way);
        double length = 0;
        for (int i = 2; i + 1 < route.length; i += 2) length += Math.hypot(route[i] - route[i - 2], route[i + 1] - route[i - 1]);
        Set<Long> chunks = new HashSet<>();
        for (int i = 0; i + 1 < route.length; i += 2) {
            reveal(chunks, route[i], route[i + 1], ROUTE_BAND);
            if (i + 3 < route.length) {
                double dx = route[i + 2] - route[i], dz = route[i + 3] - route[i + 1], l = Math.hypot(dx, dz);
                for (double t = 12; t < l; t += 12) reveal(chunks, route[i] + dx * t / l, route[i + 1] + dz * t / l, ROUTE_BAND);
            }
        }
        if (home != null) reveal(chunks, home.x(), home.z(), HOME_RADIUS);
        double[] circle = null;
        if (town != null) {
            double[] box = box(town), e = extent(town);
            for (int cx = Math.floorDiv((int) Math.floor(box[0] - TOWN_MARGIN), 16); cx <= Math.floorDiv((int) Math.ceil(box[2] + TOWN_MARGIN), 16); cx++)
                for (int cz = Math.floorDiv((int) Math.floor(box[1] - TOWN_MARGIN), 16); cz <= Math.floorDiv((int) Math.ceil(box[3] + TOWN_MARGIN), 16); cz++) {
                    double[] l = town.local(cx * 16 + 8, cz * 16 + 8);
                    if (l[0] >= e[0] - TOWN_MARGIN && l[0] <= e[1] + TOWN_MARGIN && l[1] >= e[2] - TOWN_MARGIN && l[1] <= e[3] + TOWN_MARGIN)
                        chunks.add(MapStore.key(cx, cz));
                }
            circle = new double[]{(box[0] + box[2]) / 2, (box[1] + box[3]) / 2, (box[2] - box[0]) / 2 * CIRCLE_K + CIRCLE_ADD,
                    (box[3] - box[1]) / 2 * CIRCLE_K + CIRCLE_ADD};
        }
        String report = String.format(Locale.ROOT, "%s, traced %.0f m (%d points), %d chunks", found, length, route.length / 2, chunks.size());
        return new Plan(route, circle, chunks, length, report);
    }

    /** A town's extent in town coordinates {s0, s1, v0, v1}: its limit and every lot (landmarks included). */
    static double[] extent(TownPlan p) {
        double[] e = p.limit.clone();
        for (TownPlan.Lot l : p.lots) {
            e[0] = Math.min(e[0], Math.min(l.s0(), l.s1())); e[1] = Math.max(e[1], Math.max(l.s0(), l.s1()));
            e[2] = Math.min(e[2], Math.min(l.v0(), l.v1())); e[3] = Math.max(e[3], Math.max(l.v0(), l.v1()));
        }
        return e;
    }

    /** A town extent's world box {x0, z0, x1, z1}. */
    static double[] box(TownPlan p) {
        double[] e = extent(p);
        double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, z0 = Double.POSITIVE_INFINITY, z1 = Double.NEGATIVE_INFINITY;
        for (double s : new double[]{e[0], e[1]})
            for (double v : new double[]{e[2], e[3]}) {
                double[] w = p.world(s, v);
                x0 = Math.min(x0, w[0]); x1 = Math.max(x1, w[0]); z0 = Math.min(z0, w[1]); z1 = Math.max(z1, w[1]);
            }
        return new double[]{x0, z0, x1, z1};
    }

    /**
     * The shortest way along the roads from the mouth (on the county road when there is one) to Linden: stations every
     * STEP along each road, a road's stations joined in order, two roads' stations joined where they meet (within JOIN,
     * at the same height: an overpass is no junction), a road's ends to the nearest other road within END_JOIN (a T
     * junction stops at the other road's edge); Dijkstra to the first station near Linden's limit. With no road at the
     * mouth it starts at the nearest station within FAR_START (the house without a county road). Its points
     * {x, z} from the mouth, or null.
     */
    static List<double[]> search(List<RoadDesign> roads, TownPlan town, String county, double[] mouth) {
        int count = 0;
        int[] first = new int[roads.size() + 1];
        for (int d = 0; d < roads.size(); d++) {
            first[d] = count;
            count += Math.max(1, (int) Math.ceil(roads.get(d).length / STEP)) + 1;
        }
        first[roads.size()] = count;
        double[] x = new double[count], z = new double[count], y = new double[count];
        int[] road = new int[count];
        Map<Long, List<Integer>> cells = new HashMap<>();
        for (int d = 0; d < roads.size(); d++) {
            RoadDesign r = roads.get(d);
            int k = first[d + 1] - first[d] - 1;
            for (int i = 0; i <= k; i++) {
                int n = first[d] + i;
                double s = r.length * i / k;
                double[] p = r.point(s, 0);
                x[n] = p[0];
                z[n] = p[1];
                y[n] = r.datum(s);
                road[n] = d;
                cells.computeIfAbsent(cell(p[0], p[1]), q -> new ArrayList<>()).add(n);
            }
        }
        double[] dist = new double[count];
        int[] from = new int[count];
        java.util.Arrays.fill(dist, Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(from, -1);
        // a road's ends: each to the nearest station of another road (both ways)
        Map<Integer, List<double[]>> ends = new HashMap<>();
        for (int d = 0; d < roads.size(); d++)
            for (int n : new int[]{first[d], first[d + 1] - 1}) {
                int best = -1;
                double bl = END_JOIN;
                int cx = (int) Math.floor(x[n] / JOIN), cz = (int) Math.floor(z[n] / JOIN), reach = (int) Math.ceil(END_JOIN / JOIN);
                for (int i = -reach; i <= reach; i++)
                    for (int j = -reach; j <= reach; j++) {
                        List<Integer> there = cells.get(((long) (cx + i) << 32) ^ ((cz + j) & 0xffffffffL));
                        if (there == null) continue;
                        for (int m : there) {
                            if (road[m] == d || Math.abs(y[m] - y[n]) > END_DY) continue;
                            double l = Math.hypot(x[m] - x[n], z[m] - z[n]);
                            if (l <= bl) { bl = l; best = m; }
                        }
                    }
                if (best < 0) continue;
                ends.computeIfAbsent(n, q -> new ArrayList<>()).add(new double[]{best, bl});
                ends.computeIfAbsent(best, q -> new ArrayList<>()).add(new double[]{n, bl});
            }
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        int nearest = -1;
        double nd = FAR_START;
        for (int n = 0; n < count; n++) {
            boolean ok = county == null || county.equals(roads.get(road[n]).id);
            double dm = Math.hypot(x[n] - mouth[0], z[n] - mouth[1]);
            if (ok && dm <= START && dm < dist[n]) {
                dist[n] = dm;
                open.add(new double[]{dm, n});
            }
            if (dm < nd) { nd = dm; nearest = n; }
        }
        if (open.isEmpty() && nearest >= 0) {
            dist[nearest] = nd;
            open.add(new double[]{nd, nearest});
        }
        int goal = -1;
        while (!open.isEmpty()) {
            double[] top = open.poll();
            int n = (int) top[1];
            if (top[0] > dist[n]) continue;
            if (near(town, x[n], z[n])) { goal = n; break; }
            int d = road[n];
            for (int m : new int[]{n - 1, n + 1})
                if (m >= first[d] && m < first[d + 1]) relax(open, dist, from, n, m, Math.hypot(x[m] - x[n], z[m] - z[n]));
            int cx = (int) Math.floor(x[n] / JOIN), cz = (int) Math.floor(z[n] / JOIN);
            for (int i = -1; i <= 1; i++)
                for (int j = -1; j <= 1; j++) {
                    List<Integer> there = cells.get(((long) (cx + i) << 32) ^ ((cz + j) & 0xffffffffL));
                    if (there == null) continue;
                    for (int m : there) {
                        if (road[m] == d || Math.abs(y[m] - y[n]) > JOIN_DY) continue;
                        double l = Math.hypot(x[m] - x[n], z[m] - z[n]);
                        if (l <= JOIN) relax(open, dist, from, n, m, l + JOIN_COST);
                    }
                }
            for (double[] e : ends.getOrDefault(n, List.of())) relax(open, dist, from, n, (int) e[0], e[1] + JOIN_COST);
        }
        if (goal < 0) return null;
        List<double[]> out = new ArrayList<>();
        for (int n = goal; n >= 0; n = from[n]) out.add(0, new double[]{x[n], z[n]});
        return out;
    }

    private static long cell(double x, double z) {
        return ((long) (int) Math.floor(x / JOIN) << 32) ^ ((int) Math.floor(z / JOIN) & 0xffffffffL);
    }

    private static void relax(PriorityQueue<double[]> open, double[] dist, int[] from, int n, int m, double cost) {
        double d = dist[n] + cost;
        if (d >= dist[m]) return;
        dist[m] = d;
        from[m] = n;
        open.add(new double[]{d, m});
    }

    /** Within TOWN_REACH of the town's limit (or inside it). */
    static boolean near(TownPlan t, double x, double z) {
        double[] l = t.local(x, z);
        return l[0] >= t.limit[0] - TOWN_REACH && l[0] <= t.limit[1] + TOWN_REACH && l[1] >= t.limit[2] - TOWN_REACH && l[1] <= t.limit[3] + TOWN_REACH;
    }

    /** The way's points {x, z} as a flat array, one every MARK_STEP or more (the first and last kept). */
    static double[] simplify(List<double[]> way) {
        List<double[]> keep = new ArrayList<>();
        for (int i = 0; i < way.size(); i++) {
            double[] p = way.get(i);
            if (keep.isEmpty() || i == way.size() - 1 || Math.hypot(p[0] - keep.get(keep.size() - 1)[0], p[1] - keep.get(keep.size() - 1)[1]) >= MARK_STEP)
                keep.add(p);
        }
        double[] out = new double[keep.size() * 2];
        for (int i = 0; i < keep.size(); i++) { out[2 * i] = keep.get(i)[0]; out[2 * i + 1] = keep.get(i)[1]; }
        return out;
    }

    /** The chunks whose middle lies within r of (x, z). */
    static void reveal(Set<Long> chunks, double x, double z, double r) {
        int c0 = Math.floorDiv((int) Math.floor(x - r), 16), c1 = Math.floorDiv((int) Math.floor(x + r), 16);
        int d0 = Math.floorDiv((int) Math.floor(z - r), 16), d1 = Math.floorDiv((int) Math.floor(z + r), 16);
        for (int cx = c0; cx <= c1; cx++)
            for (int cz = d0; cz <= d1; cz++)
                if (Math.hypot(cx * 16 + 8 - x, cz * 16 + 8 - z) <= r) chunks.add(MapStore.key(cx, cz));
    }

    /**
     * The chunks' samples off the plan (MapGround packing, the biome as its ecology index, which is its MapStyle index):
     * each 2 m cell's column, the planned ground's top block, or the top water block on planned water.
     */
    public static Map<Long, int[]> ground(Set<Long> chunks, RouteTerrain terrain) {
        TerrainPlanSurface s = terrain.plan;
        Map<Long, int[]> out = new HashMap<>();
        for (long k : chunks) {
            int cx = (int) (k >> 32), cz = (int) k;
            int[] a = new int[MapGround.CELLS * MapGround.CELLS];
            for (int j = 0; j < MapGround.CELLS; j++)
                for (int i = 0; i < MapGround.CELLS; i++) {
                    double x = cx * 16 + i * MapGround.CELL + 0.5, z = cz * 16 + j * MapGround.CELL + 0.5;
                    int w = terrain.water(x, z);
                    boolean water = w == RouteTerrain.RIVER || w == RouteTerrain.LAKE || w == RouteTerrain.SEA;
                    int top = water ? (int) Math.floor(terrain.waterSurface(x, z)) - 1 : (int) Math.floor(terrain.height(x, z) - 0.5);
                    a[j * MapGround.CELLS + i] = MapGround.pack(top, EcologyBiomes.biomeAt(s, x, z), water);
                }
            out.put(k, a);
        }
        return out;
    }

    /** The plan's marks as the map keeps them: the traced way and the circle round Linden. */
    static List<MapStore.Mark> marks(Plan p) {
        List<MapStore.Mark> out = new ArrayList<>();
        if (p.route().length >= 4) {
            float[] f = new float[p.route().length];
            for (int i = 0; i < f.length; i++) f[i] = (float) p.route()[i];
            out.add(new MapStore.Mark(MapStore.Mark.LINE, f));
        }
        if (p.circle() != null)
            out.add(new MapStore.Mark(MapStore.Mark.LOOP, new float[]{(float) p.circle()[0], (float) p.circle()[1], (float) p.circle()[2], (float) p.circle()[3]}));
        return out;
    }

    // ------------------------------------------------------------------ the running server

    /**
     * A new PRAIRIE road map: numbered at once, its record filled from the overworld's road network and plan on a worker,
     * then on the server thread (MapServer#fill). Without a road network it is a blank map of that kind.
     */
    public static ItemStack make(MinecraftServer server) {
        ItemStack stack = new ItemStack(AflItems.MAP.get());
        stack.getOrCreateTag().putString(KIND, ROAD_MAP);
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(server.overworld());
        if (n == null) return stack;
        int id = MapServer.newId(server);
        stack.getOrCreateTag().putInt(MapServer.TAG, id);
        long t0 = System.nanoTime();
        CompletableFuture.supplyAsync(() -> {
            Plan p = plan(n.works().roads(), n.works().towns().plans(), n.home());
            return Map.entry(p, ground(p.chunks(), new RouteTerrain(n.plan(), FederalMap.forSeed(n.plan().seed))));
        }, Util.backgroundExecutor()).thenAcceptAsync(e -> {
            MapServer.fill(server, id, e.getValue(), marks(e.getKey()));
            ApocalypseFirstLight.LOGGER.info("[AFL map] start map {}: {} in {} ms", id, e.getKey().report(), (System.nanoTime() - t0) / 1_000_000);
        }, server).exceptionally(e -> {
            ApocalypseFirstLight.LOGGER.error("[AFL map] start map {} could not be drawn; it starts blank", id, e);
            return null;
        });
        return stack;
    }

    /** Whether the start map is on the home's table yet (once per world: taken away, it does not come back). */
    static final class Laid extends SavedData {
        static final String NAME = "afl_start_map";
        String state = "";

        static Laid load(CompoundTag tag) {
            Laid l = new Laid();
            l.state = tag.getString("state");
            return l;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putString("state", state);
            return tag;
        }
    }

    /**
     * Lays the map on the home's table once its chunk is loaded (every two seconds until then): on the dining table by the
     * kitchen wall, its top away from the chair. Worlds whose house was there before the map came get it too; a table
     * someone moved or covered gets none (logged).
     */
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 40 != 0) return;
        ServerLevel level = event.getServer().overworld();
        ForestHomeRuntime.Active a = ForestHomeRuntime.active();
        if (a == null || a.level() != level) return;
        Laid laid = level.getDataStorage().computeIfAbsent(Laid::load, Laid::new, Laid.NAME);
        if (!laid.state.isEmpty()) return;
        BlockPos pos = StructureTemplate.calculateRelativePosition(AflTemplatePlacer.settings(a.rotation(), Mirror.NONE, false), TABLE_CELL).offset(a.origin());
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return;
        BlockState here = level.getBlockState(pos), below = level.getBlockState(pos.below());
        if (here.isAir() && below.getBlock() instanceof WoodFurniture.Table) {
            level.setBlock(pos, AflBlocks.PRAIRIE_ROAD_MAP.get().defaultBlockState()
                    .setValue(PrairieRoadMapBlock.FACING, a.rotation().rotate(TABLE_FACING))
                    .setValue(PrairieRoadMapBlock.SURFACE, EmergencyRadio.Surface.TABLE), 3);
            laid.state = "laid " + pos.toShortString();
        } else laid.state = "skipped " + pos.toShortString() + ": " + here + " over " + below;
        laid.setDirty();
        ApocalypseFirstLight.LOGGER.info("[AFL map] start map {}", laid.state);
    }
}
