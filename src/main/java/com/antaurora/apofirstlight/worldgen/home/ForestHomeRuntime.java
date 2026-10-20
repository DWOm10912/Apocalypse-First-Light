package com.antaurora.apofirstlight.worldgen.home;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.survivor.SurvivorSceneStarts;
import com.antaurora.apofirstlight.survivor.SurvivorScenes;
import com.antaurora.apofirstlight.tree.ForestPlan;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.lock.WorldgenGateState;
import com.antaurora.apofirstlight.worldgen.lock.WorldgenGates;
import com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime;
import com.antaurora.apofirstlight.worldgen.network.contract.RoadNode;
import com.antaurora.apofirstlight.worldgen.network.contract.RotatedRect;
import com.antaurora.apofirstlight.worldgen.network.contract.StructureReservation;
import com.antaurora.apofirstlight.worldgen.network.road.RoadCounty;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import com.antaurora.apofirstlight.worldgen.structure.AflTemplatePlacer;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The forest start home in the running server (M4, docs/worldgen/forest_home_b_m4_placement.md).
 * <ul>
 *   <li>A new federal world with the start_area gate (every federal world created since 2026-10-19, WorldgenRules; the
 *   version lock binds the gate before this event) takes its house site from the road network: the county road G3
 *   chose it (RoadCounty.Home, G2 with the roads' zones) and runs a gravel driveway from the node H to the county road.
 *   The reservation, the driveway's farm gate and the mailbox (cells and facings derived here from G3's anchors) are
 *   stored (ForestHomeSavedData). No home in the network (no site, or no road network): no house, logged.</li>
 *   <li>Every overworld load activates the stored reservation (whatever the gate says now: a world that started placing
 *   its house must finish it): the template is loaded here on the server thread, then read by the chunk workers
 *   (ForestHomeChunkPass) as each chunk of the plot, its graded ring and the driveway fixtures generates.</li>
 *   <li>Natural mesh trees give way (ForestPlan.exclude): no trunk within TREE_CLEARANCE of the plot.</li>
 *   <li>The forest-home start scene (SurvivorScenes) stands at the foot of the porch steps (SurvivorSceneStarts): the
 *   world spawn and the first survivors' spots.</li>
 * </ul>
 * Worlds made with the round-1 test gate (folder AFL_HOME_*, own G2 site, no driveway) keep their stored house.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForestHomeRuntime {
    private static final String LOG = "[AFL forest home] ";

    /** The reservation as the chunk workers read it: everything in world blocks, fixed while the server runs. */
    public record Active(ServerLevel level, long seed, StructureReservation reservation, RotatedRect plot, RotatedRect keep,
                         BoundingBox box, int minX, int minZ, int maxX, int maxZ, StructureTemplate template, Rotation rotation,
                         BlockPos origin, ForestHomeSavedData.@Nullable Fixture gate, ForestHomeSavedData.@Nullable Fixture mailbox) {
        public int groundY() { return ForestHomeLayout.groundY(reservation); }

        public RoadNode node() { return reservation.nodes().get(0); }
    }

    private static volatile @Nullable Active active;

    static {
        ForestPlan.exclude(ForestHomeRuntime::excludesTree);
        SurvivorSceneStarts.register(SurvivorScenes.DEFAULT, ForestHomeRuntime::startSpot);
    }

    private ForestHomeRuntime() {}

    public static @Nullable Active active() { return active; }

    /** ForestPlan.Exclusion: a trunk closer than TREE_CLEARANCE to the active plot (only for that world's seed). */
    public static boolean excludesTree(long seed, int x, int z, int radius) {
        Active a = active;
        return a != null && a.seed == seed && a.plot.distance(x + 0.5, z + 0.5) < ForestHomeLayout.TREE_CLEARANCE;
    }

    /** SurvivorSceneStarts.Place of the forest-home start: the grass at the foot of the porch steps. */
    static @Nullable SurvivorSceneStarts.Spot startSpot(ServerLevel overworld, int index) {
        Active a = active;
        if (a == null || a.level != overworld || index >= ForestHomeLayout.START_SPOTS) return null;
        double[] p = ForestHomeLayout.startSpot(a.reservation, index);
        return new SurvivorSceneStarts.Spot(new BlockPos((int) Math.floor(p[0]), a.groundY(), (int) Math.floor(p[1])),
                ForestHomeLayout.facingHouse(a.reservation.rotation()));
    }

    public static ForestHomeSavedData data(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(ForestHomeSavedData::load, ForestHomeSavedData::new, ForestHomeSavedData.NAME);
    }

    public static @Nullable TerrainPlanSurface surface(ServerLevel level) {
        RandomStateSeedAccess access = (RandomStateSeedAccess) (Object) level.getChunkSource().randomState();
        return access.apocalypse$hasTerrainPlan() ? access.apocalypse$terrainPlanSurface() : null;
    }

    public static String worldName(ServerLevel level) {
        var p = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        return p == null ? "" : p.toString();
    }

    /**
     * Runs G2 alone for a level (/afl dev forest_home find): the C1 start region without the roads' zones. Diagnostics
     * only: the house's site comes from the road network (RoadCounty.Home).
     */
    public static StartAreaPlanner.Result plan(ServerLevel level, TerrainPlanSurface surface) {
        FederalMap.Start start = FederalMap.forSeed(surface.seed).start();
        return StartAreaPlanner.plan(new StartAreaPlanner.Context(surface.seed, surface, start.x(), start.z(), start.radius(), List.of(), List.of()));
    }

    /** A one-line-per-site report of a planner result. */
    public static String report(StartAreaPlanner.Result r) {
        StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "%d sites (%d woodland, %d riparian) in %d ms",
                r.sites().size(), r.woodland(), r.riparian(), r.millis()));
        for (int i = 0; i < r.sites().size(); i++) {
            StartAreaPlanner.Site s = r.sites().get(i);
            b.append(String.format(Locale.ROOT, "\n#%d %d %d r%d ground %d%s stream %.0f m (%.0f m wide) house range %.1f build range %.1f%s",
                    i + 1, s.x(), s.z(), s.rotation(), s.groundY(), s.riparian() ? " riparian" : "", s.streamEdge(), s.streamWidth(),
                    s.houseRange(), s.buildRange(), s.softClear() ? "" : " soft corridor"));
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ the driveway's fixtures (county road G3 anchors)

    /**
     * The farm gate across the driveway: G3's anchor is its centre on the driveway and the driveway's direction toward the
     * road. The gate faces the road (its sign's front: PRIVATE DRIVE readable from the road), turned to the nearest of
     * the four directions (a block structure); its four columns run along the facing's clockwise side, centred on the
     * anchor. Standing on the first free cell over the works' columns under its four bottom cells (G3 levels a pad there:
     * the gravel's collision tops at Y - 1).
     */
    public static ForestHomeSavedData.@Nullable Fixture gate(@Nullable RoadNetworkRuntime.Network n, RoadCounty.Home h) {
        double[] g = h.gate();
        if (g == null || g.length < 5) return null;
        Direction facing = Direction.getNearest((float) g[3], 0F, (float) g[4]);
        if (facing.getAxis().isVertical()) return null;
        Direction cw = facing.getClockWise();
        int ox = (int) Math.floor(g[0] - 1.5 * cw.getStepX()), oz = (int) Math.floor(g[2] - 1.5 * cw.getStepZ());
        int y = Integer.MIN_VALUE;
        for (int c = 0; c < 4; c++) y = Math.max(y, firstFree(n, ox + c * cw.getStepX(), oz + c * cw.getStepZ(), g[1]));
        return new ForestHomeSavedData.Fixture(new BlockPos(ox, y, oz), facing);
    }

    /** The mailbox at G3's anchor, its door toward the road (the anchor's facing, nearest of the four directions). */
    public static ForestHomeSavedData.@Nullable Fixture mailbox(@Nullable RoadNetworkRuntime.Network n, RoadCounty.Home h) {
        double[] m = h.mailbox();
        if (m == null || m.length < 5) return null;
        Direction facing = Direction.getNearest((float) m[3], 0F, (float) m[4]);
        if (facing.getAxis().isVertical()) return null;
        int x = (int) Math.floor(m[0]), z = (int) Math.floor(m[2]);
        return new ForestHomeSavedData.Fixture(new BlockPos(x, firstFree(n, x, z, m[1]), z), facing);
    }

    /** The first cell over the road works' finish of column (x, z) (its collision or turf top + 1); off the works the anchor's height rounded. */
    public static int firstFree(@Nullable RoadNetworkRuntime.Network n, int x, int z, double anchorY) {
        // the same finish the road's chunk pass writes (RoadChunkPass#columns caches exactly this)
        RoadWorks.Column c = n == null || !n.works().touches(x >> 4, z >> 4) ? null : n.works().column(x, z, RoadWorks.Ground.of(n.plan()));
        if (c == null || c.kind() == RoadWorks.Kind.NONE) return (int) Math.round(anchorY);
        return c.top() + 1;
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !Level.OVERWORLD.equals(level.dimension())) return;
        TerrainPlanSurface surface = surface(level);
        if (surface == null || !surface.federal()) return;
        ForestHomeSavedData data = data(level);
        if (data.reservation() != null) return;
        if (!WorldgenGateState.enabled(level, WorldgenGates.START_AREA)) return;
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(level);
        RoadCounty.Home home = n == null ? null : n.home();
        String world = worldName(level);
        if (home == null) {
            String why = n == null ? "the world has no road network" : "the county road G3 found no home site";
            ApocalypseFirstLight.LOGGER.warn(LOG + "world {} seed {}: {}; this world has no start home", world, surface.seed, why);
            data.set(null, why);
            return;
        }
        StructureReservation r = home.reservation();
        ForestHomeSavedData.Fixture gate = gate(n, home), mailbox = mailbox(n, home);
        String report = String.format(Locale.ROOT, "county road G3: site %d %d r%d ground %d, link %s, beyond %s, road %s; gate %s; mailbox %s",
                home.x(), home.z(), home.rotation(), home.groundY(), home.link(), home.beyond(), home.road(), describe(gate), describe(mailbox));
        ApocalypseFirstLight.LOGGER.info(LOG + "world {} seed {}: {}", world, surface.seed, report);
        data.set(r, gate, mailbox, report);
        activate(level, data);
    }

    private static String describe(ForestHomeSavedData.@Nullable Fixture f) {
        return f == null ? "none" : f.origin().toShortString() + " facing " + f.facing().getName();
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && Level.OVERWORLD.equals(level.dimension())) activate(level, data(level));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { active = null; }

    private static void activate(ServerLevel level, ForestHomeSavedData data) { activate(level, data.reservation(), data.gate(), data.mailbox()); }

    /**
     * Makes a reservation the level's active start home without driveway fixtures (the M4 GameTest activates one of its
     * own before the chunks there generate). Null clears it.
     */
    public static @Nullable Active activate(ServerLevel level, @Nullable StructureReservation r) { return activate(level, r, null, null); }

    public static @Nullable Active activate(ServerLevel level, @Nullable StructureReservation r, ForestHomeSavedData.@Nullable Fixture gate,
                                            ForestHomeSavedData.@Nullable Fixture mailbox) {
        if (r == null || r.state() == StructureReservation.State.SKIPPED) { active = null; return null; }
        Optional<StructureTemplate> t;
        try {
            t = AflTemplatePlacer.load(level, new ResourceLocation(r.template()), false);
        } catch (Exception e) {
            t = Optional.empty();
        }
        if (t.isEmpty() || t.get().getSize().getX() != ForestHomeLayout.SIZE_X || t.get().getSize().getY() != ForestHomeLayout.SIZE_Y
                || t.get().getSize().getZ() != ForestHomeLayout.SIZE_Z) {
            ApocalypseFirstLight.LOGGER.error(LOG + "template {} missing or not {} x {} x {}; the start home is not placed", r.template(),
                    ForestHomeLayout.SIZE_X, ForestHomeLayout.SIZE_Y, ForestHomeLayout.SIZE_Z);
            active = null;
            return null;
        }
        Rotation rotation = Rotation.values()[r.rotation() & 3];
        BlockPos origin = new BlockPos(r.x(), r.y(), r.z());
        BoundingBox box = t.get().getBoundingBox(AflTemplatePlacer.settings(rotation, Mirror.NONE, false), origin);
        RotatedRect plot = ForestHomeLayout.plot(r);
        RotatedRect keep = plot.grow(ForestHomeLayout.GRADE_MARGIN);
        double[] b = keep.bounds();
        Active a = new Active(level, level.getSeed(), r, plot, keep, box, (int) Math.floor(b[0]), (int) Math.floor(b[1]),
                (int) Math.ceil(b[2]) - 1, (int) Math.ceil(b[3]) - 1, t.get(), rotation, origin, gate, mailbox);
        active = a;
        ApocalypseFirstLight.LOGGER.info(LOG + "active: origin {} {} {} rotation {} box {} ground {} node H {} {} ({}); gate {}; mailbox {}", r.x(), r.y(), r.z(),
                r.rotation(), box, ForestHomeLayout.groundY(r), Math.round(r.nodes().get(0).x()), Math.round(r.nodes().get(0).z()),
                r.nodes().get(0).state(), describe(gate), describe(mailbox));
        return a;
    }
}
