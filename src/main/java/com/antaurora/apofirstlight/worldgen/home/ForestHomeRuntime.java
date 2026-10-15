package com.antaurora.apofirstlight.worldgen.home;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.tree.ForestPlan;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.network.contract.RoadNode;
import com.antaurora.apofirstlight.worldgen.network.contract.RotatedRect;
import com.antaurora.apofirstlight.worldgen.network.contract.StructureReservation;
import com.antaurora.apofirstlight.worldgen.structure.AflTemplatePlacer;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import net.minecraft.core.BlockPos;
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
 * The forest start home in the running server (M4 round 1, docs/worldgen/forest_home_b_m4_placement.md).
 * <ul>
 *   <li>A new world (LevelEvent.CreateSpawnPosition, before the Terrain V2 spawn) on the federal surface whose folder name
 *   starts with {@link #GATE} runs the start-area planner (G2, StartAreaPlanner) round the C1 start region and stores the
 *   best site (ForestHomeSavedData). The gate stands in for C3.1's world switch until that is in Main.</li>
 *   <li>Every overworld load activates the stored reservation: the template is loaded here on the server thread, then
 *   read by the chunk workers (ForestHomeChunkPass) as each chunk of the plot and its graded ring generates.</li>
 *   <li>Natural mesh trees give way (ForestPlan.exclude): no trunk within TREE_CLEARANCE of the plot.</li>
 * </ul>
 * The spawn is unchanged in this round (P4: next round). No road or driveway is built: the node H waits for C's roads.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForestHomeRuntime {
    /** World folder prefix of the test gate (round 1). */
    public static final String GATE = "AFL_HOME_";
    private static final String LOG = "[AFL forest home] ";

    /** The reservation as the chunk workers read it: everything in world blocks, fixed while the server runs. */
    public record Active(ServerLevel level, long seed, StructureReservation reservation, RotatedRect plot, RotatedRect keep,
                         BoundingBox box, int minX, int minZ, int maxX, int maxZ, StructureTemplate template, Rotation rotation,
                         BlockPos origin) {
        public int groundY() { return ForestHomeLayout.groundY(reservation); }

        public RoadNode node() { return reservation.nodes().get(0); }
    }

    private static volatile @Nullable Active active;

    static {
        ForestPlan.exclude(ForestHomeRuntime::excludesTree);
    }

    private ForestHomeRuntime() {}

    public static @Nullable Active active() { return active; }

    /** ForestPlan.Exclusion: a trunk closer than TREE_CLEARANCE to the active plot (only for that world's seed). */
    public static boolean excludesTree(long seed, int x, int z, int radius) {
        Active a = active;
        return a != null && a.seed == seed && a.plot.distance(x + 0.5, z + 0.5) < ForestHomeLayout.TREE_CLEARANCE;
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

    /** Runs the planner for a level (also /afl dev forest_home find): the C1 start region, no corridors yet. */
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

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !Level.OVERWORLD.equals(level.dimension())) return;
        TerrainPlanSurface surface = surface(level);
        if (surface == null || !surface.federal()) return;
        String world = worldName(level);
        if (!world.startsWith(GATE)) return;
        ForestHomeSavedData data = data(level);
        if (data.reservation() != null) return;
        StartAreaPlanner.Result r = plan(level, surface);
        String report = report(r);
        ApocalypseFirstLight.LOGGER.info(LOG + "world {} seed {}: {}", world, surface.seed, report);
        if (r.reservations().isEmpty()) {
            ApocalypseFirstLight.LOGGER.warn(LOG + "no site in the start region; this world has no start home");
            data.set(null, report);
            return;
        }
        data.set(r.reservations().get(0), report);
        activate(level, data);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && Level.OVERWORLD.equals(level.dimension())) activate(level, data(level));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { active = null; }

    private static void activate(ServerLevel level, ForestHomeSavedData data) { activate(level, data.reservation()); }

    /**
     * Makes a reservation the level's active start home (world load; the M4 GameTest activates one of its own before the
     * chunks there generate). Null clears it.
     */
    public static @Nullable Active activate(ServerLevel level, @Nullable StructureReservation r) {
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
                (int) Math.ceil(b[2]) - 1, (int) Math.ceil(b[3]) - 1, t.get(), rotation, origin);
        active = a;
        ApocalypseFirstLight.LOGGER.info(LOG + "active: origin {} {} {} rotation {} box {} ground {} node H {} {} ({})", r.x(), r.y(), r.z(),
                r.rotation(), box, ForestHomeLayout.groundY(r), Math.round(r.nodes().get(0).x()), Math.round(r.nodes().get(0).z()),
                r.nodes().get(0).state());
        return a;
    }
}
