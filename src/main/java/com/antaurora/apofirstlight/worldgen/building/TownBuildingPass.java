package com.antaurora.apofirstlight.worldgen.building;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.GradedTurfBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflStructureProcessors;
import com.antaurora.apofirstlight.worldgen.structure.AflTemplatePlacer;
import com.antaurora.apofirstlight.worldgen.lock.WorldgenGateState;
import com.antaurora.apofirstlight.worldgen.lock.WorldgenGates;
import com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime;
import com.antaurora.apofirstlight.worldgen.network.town.TownBuildings;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.network.town.TownWorks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The town buildings' chunk pass (Town buildings V1, docs/worldgen/town_buildings_v1.md): right after the road's chunk
 * pass (ChunkGeneratorRoadWorksMixin), every building site (TownBuildings) reaching the chunk is drawn by its kind's kit
 * (BuildingKits) and the canvas cells in this chunk written, the footing carried down to the ground under the footprint.
 * Each chunk writes only its own columns; a building is the same pure function of its site in every chunk, so the
 * order chunks generate in never shows. The lots are the road works' columns, so later features of any chunk cannot
 * write into them (WorldGenRegionRoadGuardMixin). Only in worlds created with the town_buildings gate.
 * <p>
 * Fuel Stop A1 (2026-10-20, TownBuildings#fuelStop) is a structure template, not a kit: each chunk places its own slice
 * of gas_station_01 (clipped to the chunk, known shape, the AFL block entity processor turning the packed wiring, plugs
 * and fuel with it, as ForestHomeChunkPass), its ground layer A1_GROUND on the site's floor block; the road guard lets
 * these writes through (#placing). On its banked strips along the streets (TownBuildings#bank, 2026-10-20) the template
 * leaves the works' graded ground: its air and its grass layer there are not written (the feed cables under it are), and
 * what stands on a strip (the light poles, the price sign) is raised as a whole to the ground under it, its feed cable
 * carried up under it (#A1Strips), the ground round its foot filled solid so the cable stays buried.
 */
public final class TownBuildingPass {
    private TownBuildingPass() {}

    /** The deepest the footing goes under the floor to reach the ground, blocks. */
    static final int FOOTING = 16;
    private static final int CACHE = 64;
    private static final ThreadLocal<Boolean> PLACING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static volatile StructureTemplate fuelStop;
    private static volatile A1Objects fuelStopObjects;

    /** The sites by chunk, per network. */
    private static final Map<RoadNetworkRuntime.Network, Map<Long, List<TownBuildings.Site>>> INDEX = new WeakHashMap<>();
    /** Recently drawn buildings (bounded; recomputed when evicted: a pure function of the site). */
    private static final Map<TownBuildings.Site, BuildingCanvas> CANVASES = new LinkedHashMap<>(CACHE, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<TownBuildings.Site, BuildingCanvas> e) { return size() > CACHE; }
    };

    /** Every building site reaching chunk (cx, cz) of the network's towns (an empty list when none). */
    public static List<TownBuildings.Site> sites(RoadNetworkRuntime.Network n, int cx, int cz) {
        Map<Long, List<TownBuildings.Site>> index;
        synchronized (INDEX) {
            index = INDEX.computeIfAbsent(n, TownBuildingPass::index);
        }
        return index.getOrDefault(key(cx, cz), List.of());
    }

    private static Map<Long, List<TownBuildings.Site>> index(RoadNetworkRuntime.Network n) {
        Map<Long, List<TownBuildings.Site>> out = new HashMap<>();
        for (TownPlan p : n.works().towns().plans())
            for (TownBuildings.Site s : TownBuildings.sites(p, n.works().towns().fuelStop(p)))
                for (int cx = s.x0() >> 4; cx <= s.x1() >> 4; cx++)
                    for (int cz = s.z0() >> 4; cz <= s.z1() >> 4; cz++) out.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(s);
        return out;
    }

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    /** The site's drawn building (cached), or null when its kind has no kit. */
    public static BuildingCanvas canvas(TownBuildings.Site s) {
        synchronized (CANVASES) {
            BuildingCanvas c = CANVASES.get(s);
            if (c != null) return c;
        }
        BuildingKit kit = BuildingKits.of(s.kind());
        if (kit == null) return null;
        BuildingCanvas c = new BuildingCanvas(s, kit.height(s));
        kit.build(s, c);
        synchronized (CANVASES) { CANVASES.put(s, c); }
        return c;
    }

    public static void apply(WorldGenLevel level, ChunkAccess chunk) {
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(level.getLevel());
        if (n == null || n.works().towns().isEmpty() || !WorldgenGateState.enabled(level.getLevel(), WorldgenGates.TOWN_BUILDINGS)) return;
        int cx = chunk.getPos().x, cz = chunk.getPos().z;
        List<TownBuildings.Site> here = sites(n, cx, cz);
        if (here.isEmpty()) return;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (TownBuildings.Site s : here) {
            if (TownBuildings.FUEL_STOP.equals(s.kind())) { placeFuelStop(level, chunk, n, s); continue; }
            BuildingCanvas c = canvas(s);
            if (c == null) continue;
            for (int w = 0; w < c.depth; w++)
                for (int u = 0; u < c.width; u++) {
                    int x = c.x(u, w), z = c.z(u, w);
                    if (x >> 4 != cx || z >> 4 != cz) continue;
                    for (int y = -BuildingCanvas.BELOW; y < c.height; y++) {
                        BlockState st = c.get(u, y, w);
                        if (st != null) set(chunk, p, x, c.worldY(y), z, st);
                    }
                    // the footing down to the ground (a lot's graded margin may fall away under the footprint)
                    BlockState foot = c.get(u, -BuildingCanvas.BELOW, w);
                    if (foot == null) continue;
                    for (int k = 1, y = c.worldY(-BuildingCanvas.BELOW) - 1; k <= FOOTING && y >= chunk.getMinBuildHeight(); k++, y--) {
                        BlockState under = chunk.getBlockState(p.set(x, y, z));
                        if (!(under.isAir() || under.canBeReplaced() || !under.getFluidState().isEmpty() || under.getBlock() instanceof GradedTurfBlock)) break;
                        chunk.setBlockState(p, foot, false);
                    }
                }
        }
    }

    /** Whether a town building's template is being written (WorldGenRegionRoadGuardMixin lets it into the lot). */
    public static boolean placing() { return PLACING.get(); }

    /** This chunk's slice of Fuel Stop A1 on its site. */
    static void placeFuelStop(WorldGenLevel level, ChunkAccess chunk, RoadNetworkRuntime.Network n, TownBuildings.Site s) {
        StructureTemplate t = fuelStopTemplate(level);
        if (t == null) return;
        A1Objects objects = fuelStopObjects;
        // the template faces north; its (0, 0) is the site's front right corner (u = A1 - 1, w = 0, BuildingCanvas)
        Rotation rotation = s.fz() < 0 ? Rotation.NONE : s.fx() > 0 ? Rotation.CLOCKWISE_90 : s.fz() > 0 ? Rotation.CLOCKWISE_180 : Rotation.COUNTERCLOCKWISE_90;
        int dx = -s.fx(), dz = -s.fz(), rx = -dz, rz = dx;
        int cx = rx + dx > 0 ? s.x0() : s.x1(), cz = rz + dz > 0 ? s.z0() : s.z1();
        BlockPos origin = new BlockPos(cx + rx * (TownBuildings.A1 - 1), s.floorY() - TownBuildings.A1_FLOOR, cz + rz * (TownBuildings.A1 - 1));
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        BoundingBox slice = new BoundingBox(x0, chunk.getMinBuildHeight(), z0, x0 + 15, chunk.getMaxBuildHeight() - 1, z0 + 15);
        // each object's lift: the whole ground under its foot above the floor (a pure function of the site: the same in
        // every chunk an object spans). An object whose foot borders the level part (the curb round the paving) stays at
        // the floor: that side cannot be banked up, and a raised foot would show its feed cable there (2026-10-20, user)
        int[] lift = new int[objects.feet().size()];
        for (int g = 0; g < lift.length; g++) {
            int dy = Integer.MAX_VALUE;
            for (int[] f : objects.feet().get(g))
                for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int ox = f[0] + o[0], oz = f[1] + o[1];
                    if (ox >= 0 && oz >= 0 && ox < TownBuildings.A1 && oz < TownBuildings.A1 && !TownBuildings.bank(ox, oz)) dy = 0;
                }
            for (int[] f : objects.feet().get(g)) {
                int u = TownBuildings.A1 - 1 - f[0];
                TownWorks.Spot sp = n.works().towns().spot(cx + rx * u + dx * f[1] + 0.5, cz + rz * u + dz * f[1] + 0.5);
                dy = Math.min(dy, sp == null ? 0 : (int) Math.round(sp.h() - s.floorY()));
            }
            lift[g] = Math.max(0, dy == Integer.MAX_VALUE ? 0 : dy);
        }
        StructurePlaceSettings settings = AflTemplatePlacer.settings(rotation, Mirror.NONE, false).setBoundingBox(slice).setKnownShape(true).setKeepLiquids(false)
                .addProcessor(new A1Strips(objects, lift));
        PLACING.set(Boolean.TRUE);
        try {
            t.placeInWorld(level, origin, origin, settings, RandomSource.create(s.seed() ^ chunk.getPos().toLong()), Block.UPDATE_CLIENTS);
            // round each object's foot (2026-10-20, user: cables still showing): its foot and the strip cells round it
            // solid from a block under the floor up to under the foot, grass on top, so the feed cable rising into the
            // foot (from the template's riser, carried up under a raised object) runs inside the ground, never in a pit
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            BlockState cable = AflBlocks.POWER_CABLE.get().defaultBlockState();
            for (var e : PipeBlock.PROPERTY_BY_DIRECTION.entrySet()) cable = cable.setValue(e.getValue(), e.getKey().getAxis().isVertical());
            for (int pass = 0; pass < 2; pass++)
                for (int g = 0; g < lift.length; g++) {
                    int top = s.floorY() + lift[g] - 1;
                    for (int[] f : objects.feet().get(g))
                        for (int ox = -1; ox <= 1; ox++)
                            for (int oz = -1; oz <= 1; oz++) {
                                boolean riser = ox == 0 && oz == 0 && f[2] != 0;
                                if (pass == 1 && !riser || !TownBuildings.bank(f[0] + ox, f[1] + oz)) continue;
                                int u = TownBuildings.A1 - 1 - (f[0] + ox), x = cx + rx * u + dx * (f[1] + oz), z = cz + rz * u + dz * (f[1] + oz);
                                if (x >> 4 != chunk.getPos().x || z >> 4 != chunk.getPos().z) continue;
                                for (int y = s.floorY() - 1; y <= top; y++) {
                                    if (pass == 1) {
                                        // the template's riser is at floor - 1; above it the cable carried up to the foot
                                        if (y >= s.floorY()) level.setBlock(p.set(x, y, z), cable, Block.UPDATE_CLIENTS);
                                        continue;
                                    }
                                    BlockState here = level.getBlockState(p.set(x, y, z));
                                    if (here.isAir() || here.canBeReplaced() || here.getBlock() instanceof GradedTurfBlock || here.is(Blocks.DIRT) || here.is(Blocks.GRASS_BLOCK))
                                        level.setBlock(p, (y == top ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), Block.UPDATE_CLIENTS);
                                }
                            }
                }
        } finally {
            PLACING.set(Boolean.FALSE);
        }
    }

    /**
     * What stands on Fuel Stop A1's banked strips (TownBuildings#bank): each object (6-connected cells above the ground
     * layer, reached from a strip column) by its cells' template positions; its feet (the columns it stands on at
     * A1_FLOOR: {x, z, whether a feed cable rises under it}).
     */
    record A1Objects(Map<Long, Integer> cells, List<List<int[]>> feet) {
        /** The largest object taken (a strip cell reaching the building would take it all: left in place). */
        static final int MAX = 400;

        static A1Objects of(StructureTemplate t) {
            CompoundTag tag = t.save(new CompoundTag());
            ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND), blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
            Map<Long, String> names = new HashMap<>();
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag b = blocks.getCompound(i);
                ListTag pos = b.getList("pos", Tag.TAG_INT);
                String name = palette.getCompound(b.getInt("state")).getString("Name");
                if (!name.equals("minecraft:air") && !name.equals("minecraft:structure_void"))
                    names.put(BlockPos.asLong(pos.getInt(0), pos.getInt(1), pos.getInt(2)), name);
            }
            String cable = ApocalypseFirstLight.MOD_ID + ":power_cable";
            Map<Long, Integer> cells = new HashMap<>();
            List<List<int[]>> feet = new ArrayList<>();
            for (int x = 0; x < TownBuildings.A1; x++)
                for (int z = 0; z < TownBuildings.A1; z++) {
                    long start = BlockPos.asLong(x, TownBuildings.A1_FLOOR, z);
                    if (!TownBuildings.bank(x, z) || !names.containsKey(start) || cells.containsKey(start)) continue;
                    List<Long> group = new ArrayList<>();
                    java.util.ArrayDeque<Long> open = new java.util.ArrayDeque<>();
                    java.util.Set<Long> seen = new java.util.HashSet<>();
                    open.add(start);
                    seen.add(start);
                    while (!open.isEmpty() && group.size() <= MAX) {
                        long c = open.poll();
                        group.add(c);
                        BlockPos q = BlockPos.of(c);
                        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                            BlockPos r = q.relative(d);
                            long k = r.asLong();
                            if (r.getY() < TownBuildings.A1_FLOOR || !names.containsKey(k) || !seen.add(k)) continue;
                            open.add(k);
                        }
                    }
                    if (group.size() > MAX) {
                        ApocalypseFirstLight.LOGGER.warn("[AFL town buildings] Fuel Stop A1: the object on its strip at {} {} reaches more than {} cells; left at the floor", x, z, MAX);
                        continue;
                    }
                    List<int[]> foot = new ArrayList<>();
                    for (long c : group) {
                        cells.put(c, feet.size());
                        BlockPos q = BlockPos.of(c);
                        if (q.getY() == TownBuildings.A1_FLOOR)
                            foot.add(new int[]{q.getX(), q.getZ(), cable.equals(names.get(BlockPos.asLong(q.getX(), TownBuildings.A1_GROUND, q.getZ()))) ? 1 : 0});
                    }
                    feet.add(foot);
                }
            return new A1Objects(cells, feet);
        }
    }

    /**
     * Fuel Stop A1's strips on the works' graded ground (TownBuildings#bank): the template's air and grass layer there
     * skipped, its objects raised by their lift. Built per placement, never serialized (its type is the AFL processor's).
     */
    private static final class A1Strips extends StructureProcessor {
        private final A1Objects objects;
        private final int[] lift;

        A1Strips(A1Objects objects, int[] lift) {
            this.objects = objects;
            this.lift = lift;
        }

        @Override
        public @Nullable StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pivot,
                StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings) {
            BlockPos o = original.pos();
            if (!TownBuildings.bank(o.getX(), o.getZ()) || o.getY() < TownBuildings.A1_GROUND) return current;
            BlockState st = current.state();
            if (o.getY() == TownBuildings.A1_GROUND) return st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT) ? null : current;
            if (st.isAir()) return null;
            Integer g = objects.cells().get(o.asLong());
            if (g == null || lift[g] == 0) return current;
            return new StructureTemplate.StructureBlockInfo(current.pos().above(lift[g]), st, current.nbt());
        }

        @Override
        protected StructureProcessorType<?> getType() {
            return AflStructureProcessors.BLOCK_ENTITY.get();
        }
    }

    private static StructureTemplate fuelStopTemplate(WorldGenLevel level) {
        StructureTemplate t = fuelStop;
        if (t != null) return t;
        synchronized (TownBuildingPass.class) {
            if (fuelStop == null) {
                t = level.getLevel().getStructureManager().get(new ResourceLocation(ApocalypseFirstLight.MOD_ID, TownBuildings.FUEL_STOP_TEMPLATE)).orElse(null);
                if (t == null || t.getSize().getX() != TownBuildings.A1 || t.getSize().getZ() != TownBuildings.A1) {
                    ApocalypseFirstLight.LOGGER.error("[AFL town buildings] template {} missing or not {} x {}; Fuel Stop A1 not placed", TownBuildings.FUEL_STOP_TEMPLATE, TownBuildings.A1, TownBuildings.A1);
                    return null;
                }
                fuelStopObjects = A1Objects.of(t);
                fuelStop = t;
            }
            return fuelStop;
        }
    }

    private static void set(ChunkAccess chunk, BlockPos.MutableBlockPos p, int x, int y, int z, BlockState s) {
        if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) return;
        chunk.setBlockState(p.set(x, y, z), s, false);
    }
}
