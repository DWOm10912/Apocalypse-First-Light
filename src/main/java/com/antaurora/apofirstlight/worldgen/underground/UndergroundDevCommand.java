package com.antaurora.apofirstlight.worldgen.underground;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Cave;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.CaveKind;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Entrance;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.FacilityPart;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Kit;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Place;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * {@code /afl dev underground here} (U1, docs/worldgen/underground_geology_caves_v0_design.md 19.16 and 20): the
 * underground plan read back where the caller stands. The rock underfoot, the nearest cave systems, giant caves and
 * canyons and the least kit that walks into them from the surface, the nearest entrances, and the research facility.
 * Every place has a [go] that runs {@code /afl dev underground goto <x> <z>}, which sets the caller on the ground there.
 * In a deep world (U2 preset federal_deep_proto) the caves are cut into its chunks, and each cave also has an [in] that
 * runs {@code /afl dev underground cave <id>}: the caller is set inside it. In any other world the plan is read-only
 * (nothing is built). The plan is UndergroundRuntime's, made once per world (about 1–2 s on the server thread).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UndergroundDevCommand {
    private UndergroundDevCommand() {}

    private record Planned(GeologyQuery q, List<BitSet> reach) {}

    // the routes per plan (the plan itself is UndergroundRuntime's, shared with the chunk cut)
    private static final Map<GeologyQuery, Planned> CACHE = new WeakHashMap<>();

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(Commands.literal("underground")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("here").executes(c -> here(c.getSource())))
                .then(Commands.literal("goto").then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                .executes(c -> go(c.getSource(), DoubleArgumentType.getDouble(c, "x"), DoubleArgumentType.getDouble(c, "z"))))))
                .then(Commands.literal("cave").then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .executes(c -> into(c.getSource(), IntegerArgumentType.getInteger(c, "id"))))))));
    }

    private static synchronized Planned planned(RoadNetworkRuntime.Network n) {
        GeologyQuery q = UndergroundRuntime.query(n);
        Planned p = CACHE.get(q);
        if (p != null) return p;
        List<BitSet> reach = new ArrayList<>();
        for (Kit k : UndergroundPlan.KITS) reach.add(UndergroundRoutes.reachIn(q.plan, k));
        p = new Planned(q, reach);
        CACHE.put(q, p);
        return p;
    }

    /** True when this world's chunks have the planned caves cut in (a deep world with its plan bound). */
    private static boolean built(ServerLevel level) {
        UndergroundRef ref = ((com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess) (Object) level.getChunkSource().randomState()).apocalypse$underground();
        return ref != null && ref.get() != null;
    }

    private static int here(CommandSourceStack source) {
        ServerLevel level = source.getServer().overworld();
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(level);
        if (n == null) {
            source.sendFailure(Component.literal("AFL underground: not a federal world (no terrain plan, roads or towns)."));
            return 0;
        }
        long t0 = System.nanoTime();
        boolean fresh = !UndergroundRuntime.planned(n);
        boolean built = built(level);
        Planned pd = planned(n);
        GeologyQuery q = pd.q();
        UndergroundPlan u = q.plan;
        Vec3 pos = source.getPosition();
        double x = pos.x, y = pos.y, z = pos.z;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(String.format(Locale.ROOT, built
                        ? "AFL underground (U2 deep world: these caves are cut into the chunks; no vanilla caves). Plan floor Y %d, this world's floor Y %d.%s"
                        : "AFL underground (plan only, read-only: nothing is built, the world still has the vanilla caves). Plan floor Y %d, this world's floor Y %d.%s",
                UndergroundRuntime.MIN_Y, level.getMinBuildHeight(), fresh ? String.format(Locale.ROOT, " Planned in %d ms.", (System.nanoTime() - t0) / 1_000_000) : ""))
                .withStyle(ChatFormatting.GRAY));

        // the column
        GeologyModel.Column c = q.column(x, z);
        int unit = q.geology.unitAt(c, y - 1);
        double[] fd = new double[1];
        GeologyModel.Fault f = q.geology.nearestFault(x, z, 3000, fd);
        int cave = q.caveAt(x, y - 1, z);
        lines.add(Component.literal(String.format(Locale.ROOT,
                "Here (%.0f, %.0f, %.0f): underfoot %s%s, layer %d; soil %s %.1f m; bedrock %s; planned ground Y %.0f, water table Y %.0f; protection %s; fault %s; planned hollow underfoot: %s",
                x, y, z, name(q.rockAt(x, y - 1, z)), unit >= 0 && unit < GeologyModel.UNIT_NAME.length ? " (" + GeologyModel.UNIT_NAME[unit] + ")" : "",
                q.layerAt(x, y - 1, z), name(c.soilType), c.soil, name(q.bedrock(x, z)), c.h, c.waterTable, q.zone(x, z).name().toLowerCase(Locale.ROOT),
                f == null ? "none within 3 km" : String.format(Locale.ROOT, "%.0f m (throw %.0f m)", fd[0], f.throwM()),
                cave < 0 ? "no" : "#" + cave + " " + u.caves.get(cave).kind)));

        // cave systems, giant caves and canyons
        lines.add(Component.literal("Nearest cave systems:").withStyle(ChatFormatting.YELLOW));
        for (Cave cv : nearest(u, x, z, 3, cv -> cv.system())) lines.add(caveLine(u, pd.reach(), cv, x, z, built));
        lines.add(Component.literal("Nearest giant caves and canyons:").withStyle(ChatFormatting.YELLOW));
        for (Cave cv : nearest(u, x, z, 2, cv -> cv.kind == CaveKind.GIANT || cv.kind == CaveKind.CANYON)) lines.add(caveLine(u, pd.reach(), cv, x, z, built));

        // entrances
        lines.add(Component.literal("Nearest entrances:").withStyle(ChatFormatting.YELLOW));
        List<Entrance> es = new ArrayList<>(u.entrances);
        es.sort(Comparator.comparingDouble((Entrance e) -> Math.hypot(e.x() - x, e.z() - z)).thenComparingInt(Entrance::id));
        for (Entrance e : es.subList(0, Math.min(5, es.size()))) {
            Cave to = e.target() >= 0 && e.target() < u.caves.size() ? u.caves.get(e.target()) : null;
            int kit = to == null ? -1 : leastKit(u, pd.reach(), to);
            MutableComponent l = Component.literal(to == null
                    ? String.format(Locale.ROOT, "  #%d %s at (%.0f, %.0f, %.0f) %.0f m away, %.0f m deep, a closed hollow (no cave behind it) ",
                            e.id(), e.kind().name().toLowerCase(Locale.ROOT), e.x(), e.surfaceY(), e.z(), Math.hypot(e.x() - x, e.z() - z), e.depth())
                    : String.format(Locale.ROOT, "  #%d %s at (%.0f, %.0f, %.0f) %.0f m away, %.0f m deep into #%d %s; kit %s ",
                            e.id(), e.kind().name().toLowerCase(Locale.ROOT), e.x(), e.surfaceY(), e.z(), Math.hypot(e.x() - x, e.z() - z), e.depth(),
                            to.id, caveName(to), kitName(kit)));
            lines.add(l.append(go(e.x(), e.z())));
        }

        // the facility
        UndergroundPlan.Facility fc = u.facility;
        if (fc == null) lines.add(Component.literal("Research facility: none in this world's plan."));
        else {
            Place portal = null, lab = null;
            for (FacilityPart fp : fc.parts()) {
                if (fp.role().equals("hidden_entrance") && fp.place() >= 0) portal = u.places.get(fp.place());
                if (fp.role().equals("laboratory") && fp.place() >= 0) lab = u.places.get(fp.place());
            }
            double px = portal == null ? fc.x() : portal.x(), pz = portal == null ? fc.z() : portal.z();
            MutableComponent l = Component.literal(String.format(Locale.ROOT, "Research facility in %s: hidden entrance (%.0f, %.0f) %.0f m away%s ",
                    name(fc.host()), px, pz, Math.hypot(px - x, pz - z),
                    lab == null ? "" : String.format(Locale.ROOT, ", laboratory at Y %.0f", lab.y())));
            lines.add(l.withStyle(ChatFormatting.LIGHT_PURPLE).append(go(px, pz)));
        }

        MutableComponent all = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) all.append("\n");
            all.append(lines.get(i));
        }
        source.sendSuccess(() -> all, false);
        return 1;
    }

    private static MutableComponent caveLine(UndergroundPlan u, List<BitSet> reach, Cave cv, double x, double z, boolean built) {
        int kit = leastKit(u, reach, cv);
        // the entrance that leads into it, if any: the [go] then points at the surface, else at the cave's centre column
        Entrance in = null;
        for (Entrance e : u.entrances)
            if (e.target() == cv.id && (in == null || Math.hypot(e.x() - x, e.z() - z) < Math.hypot(in.x() - x, in.z() - z))) in = e;
        MutableComponent l = Component.literal(String.format(Locale.ROOT, "  #%d %s centre (%.0f, %.0f, %.0f) %.0f m away, in %s, layer %d; from the surface: %s",
                cv.id, caveName(cv), cv.x, cv.y, cv.z,
                Math.hypot(cv.x - x, cv.z - z), name(cv.host), cv.layer, kit < 0 ? "not reachable without diving" : "kit " + kitName(kit)));
        l.append(Component.literal(" centre "));
        l.append(go(cv.x, cv.z));
        if (built) l.append(Component.literal(" ")).append(link("[in]", "/afl dev underground cave " + cv.id));
        if (in != null) {
            l.append(Component.literal(String.format(Locale.ROOT, " its entrance #%d %s ", in.id(), in.kind().name().toLowerCase(Locale.ROOT))));
            l.append(go(in.x(), in.z()));
        }
        return l;
    }

    /** The least kit (KITS index) that walks in from the surface to any place of the cave, or -1. */
    private static int leastKit(UndergroundPlan u, List<BitSet> reach, Cave cv) {
        int best = -1;
        for (int pl : cv.places) {
            int k = UndergroundRoutes.leastKitIn(u, pl, reach);
            if (k >= 0 && (best < 0 || k < best)) best = k;
        }
        return best;
    }

    private static List<Cave> nearest(UndergroundPlan u, double x, double z, int n, java.util.function.Predicate<Cave> which) {
        List<Cave> l = new ArrayList<>();
        for (Cave cv : u.caves) if (which.test(cv)) l.add(cv);
        l.sort(Comparator.comparingDouble((Cave cv) -> Math.hypot(cv.x - x, cv.z - z)).thenComparingInt(cv -> cv.id));
        return l.subList(0, Math.min(n, l.size()));
    }

    /** Kind plus label; a built hollow is named by its label alone ("mine coal", "facility"). */
    private static String caveName(Cave cv) {
        if (cv.kind == UndergroundPlan.CaveKind.BUILT) return cv.label.isEmpty() ? "built workings" : cv.label;
        return cv.kind.name().toLowerCase(Locale.ROOT) + (cv.label.isEmpty() ? "" : " " + cv.label);
    }

    private static String kitName(int k) { return k < 0 ? "none reaches it" : UndergroundPlan.KITS.get(k).name(); }

    private static String name(RockType r) { return r == null ? "air" : r.name().toLowerCase(Locale.ROOT); }

    private static MutableComponent go(double x, double z) {
        return link("[go]", String.format(Locale.ROOT, "/afl dev underground goto %.0f %.0f", x, z));
    }

    private static MutableComponent link(String text, String cmd) {
        return Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(cmd))));
    }

    /** Sets the caller on the ground at the column (loads that one chunk); nothing is placed or changed. */
    private static int go(CommandSourceStack source, double x, double z) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("AFL underground goto: players only."));
            return 0;
        }
        ServerLevel level = source.getServer().overworld();
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        // Level#getHeight answers the world's floor for a chunk that is not loaded: load (or generate) that one first
        int y = level.getChunk(bx >> 4, bz >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx & 15, bz & 15) + 1;
        player.teleportTo(level, bx + 0.5, y, bz + 0.5, player.getYRot(), player.getXRot());
        boolean built = built(level);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, built
                ? "AFL underground: at (%d, %d, %d), on the ground."
                : "AFL underground: at (%d, %d, %d), on the ground. This world has the plan only: no cave or entrance is built here.", bx, y, bz)), false);
        return 1;
    }

    /**
     * Sets the caller inside cave #id of a deep world (U2): on the floor under the first of its places that is dry,
     * else in the water of its first place (flooded cave). Loads that one chunk; nothing is placed or changed.
     */
    private static int into(CommandSourceStack source, int id) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("AFL underground cave: players only."));
            return 0;
        }
        ServerLevel level = source.getServer().overworld();
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(level);
        if (n == null || !built(level)) {
            source.sendFailure(Component.literal("AFL underground cave: this world has no caves cut in (only a deep world, preset AFL Federation Deep, has them). Use goto."));
            return 0;
        }
        UndergroundPlan u = planned(n).q().plan;
        if (id >= u.caves.size()) {
            source.sendFailure(Component.literal("AFL underground cave: no cave #" + id + " (this plan has " + u.caves.size() + ")."));
            return 0;
        }
        Cave cv = u.caves.get(id);
        Place at = null;
        for (int pl : cv.places) {
            Place p = u.places.get(pl);
            if (p.y() + 1 >= cv.waterY && u.airAt(p.x(), p.y() + 1, p.z())) { at = p; break; }
        }
        boolean flooded = at == null;
        if (at == null && !cv.places.isEmpty()) at = u.places.get(cv.places.get(0));
        double px = at == null ? cv.x : at.x(), py = at == null ? cv.y : at.y(), pz = at == null ? cv.z : at.z();
        int bx = (int) Math.floor(px), bz = (int) Math.floor(pz), by = (int) Math.floor(py) + 1;
        var chunk = level.getChunk(bx >> 4, bz >> 4);
        // down to the cave floor (the place's Y is the plan's, the cut floor may lie a little lower), at most 24 m
        net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos(bx, by, bz);
        for (int i = 0; i < 24 && m.getY() > level.getMinBuildHeight() && chunk.getBlockState(m.below()).isAir(); i++) m.move(0, -1, 0);
        int fy = m.getY();
        player.teleportTo(level, bx + 0.5, fy, bz + 0.5, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "AFL underground: in #%d %s at (%d, %d, %d)%s.", cv.id, caveName(cv), bx, fy, bz,
                flooded ? ", flooded: no dry place in this cave, you are in its water" : "")), false);
        return 1;
    }
}
