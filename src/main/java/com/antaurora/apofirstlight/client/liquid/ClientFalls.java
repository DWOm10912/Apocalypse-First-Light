package com.antaurora.apofirstlight.client.liquid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.LiquidDecals;
import com.antaurora.apofirstlight.client.LiquidJetRenderer;
import com.antaurora.apofirstlight.fluid.LiquidJet;
import com.antaurora.apofirstlight.liquid.world.LiquidPackets;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Water in the air (docs/gameplay/water_falling_v1_design.md 4): the falls the server finds (LiquidPackets.Falls) and the
 * players pouring (LiquidPackets.Pour), drawn by their flow.
 * <ul>
 *   <li>Drips (under {@link #DRIP_MAX} mL/s): a drop of {@link #DROP_ML} mL off the edge as often as the flow fills one;
 *   a fall that stops drips on for a few seconds, ever more slowly.</li>
 *   <li>Falls side by side along one edge gather (falling water 16): their water leaves at one place. Too little to jump
 *   clear ({@link #CLING_FLOW}) runs down the wall as a clear film; more leaves as one stream. The water running to the
 *   edge on top is drawn as standing water's running films are (ClientLiquid, falling water 17).</li>
 *   <li>A stream: a flat ribbon facing the camera along the free-fall parabola from the lip to where it lands, as wide as
 *   the flow through it at its speed and narrowing as it speeds up, its edges' normals leaning out so it reads round,
 *   streaks running down it; below where it breaks up it thins, turns white and drops come away from it.</li>
 *   <li>A sheet (from {@link #SHEET_Q} L/s a metre): strips side by side along the free-fall parabola over the opening's
 *   width, drawn in a little at the sides as it falls; each strip comes apart from its own height down, thinner, gappier
 *   and whiter, drops flung off its sides.</li>
 *   <li>Where it lands: drops splash up, on AFL water rings spread (into afl_water: {@link #impacts}), on dry ground a wet
 *   mark stays a while (LiquidDecals).</li>
 * </ul>
 * Without a shader pack all of it has the standing water's look (afl_water_fall, falling water 17). All of it is a
 * picture: the water itself falls and lands on the server (LiquidEngine#fall).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class ClientFalls {
    private static final double G = 9.8, RANGE = 64;
    /** One drop off an edge, mL (Tate's law, a water drop off a lip: 0.05-0.15 mL); below this flow, mL/s, it drips. */
    static final double DROP_ML = 0.1, DRIP_MAX = 3;
    /** L/s a metre of edge from which a fall is a sheet (critical depth about 7 mm, a coherent nappe). */
    static final double SHEET_Q = 2.0;
    /**
     * L/s: a group whose water is under CLING_FLOW runs down the wall under the lip as a film (too little to jump clear of
     * a rough natural edge); from it, a stream. Side by side falls gather until they carry STRAND_FLOW over 1.5 m of edge
     * (0.75 m once the flow a metre passes 0.5 L/s), never more than {@link #GATHER_MAX} m, into one place it leaves.
     */
    static final double CLING_FLOW = 0.04, STRAND_FLOW = 0.15, GATHER_MAX = 4;
    /** Falls along one edge whose lips differ by more than this (m) are not one edge. */
    private static final double SAME_EDGE = 0.3;
    /** Group kinds: drips off one point, a film down the wall, a stream, a sheet. */
    static final int DRIP = 0, CLING = 1, STRAND = 2, SHEET = 3;
    private static final int MAX_DROPS = 800, MAX_WET = 128, MAX_IMPACTS = 16;
    /** Drops farther than this (m) are not drawn: a pixel or less. */
    private static final double DROP_RANGE = 32;
    private static final ResourceLocation STREAM = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/liquid/water_surface"),
            FALL_SPRITE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/liquid/water_fall"),
            DROP = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/liquid_drop");
    private static final int WATER_RGB = 0xD8E6EE;

    /** One fall from the server, and what is drawn of it. */
    static final class Fall {
        final int x, z;
        final boolean alongX;
        final int sign;
        float lip, depth, bottom, width, flow, speed;
        float shown;
        /** Seconds of drips left after it stopped (ended falls). */
        float tail = -1;
        double drip;
        /** Where along the lip its water leaves when it is its group's main fall (0-1, from its seed; -1 not yet). */
        float at = -1;
        /** It was its group's main fall last tick (kept while it has 80 % of the group's most). */
        boolean main;
        /** Where the wall under its lip ends (m; NaN: not looked yet). */
        float wall = Float.NaN;
        final long seed;

        Fall(int x, int z, boolean alongX, int sign) {
            this.x = x;
            this.z = z;
            this.alongX = alongX;
            this.sign = sign;
            seed = (long) x * 341873128712L ^ (long) z * 132897987541L ^ (alongX ? 7 : 0) ^ sign;
        }

        boolean same(Fall o) {
            return x == o.x && z == o.z && alongX == o.alongX && sign == o.sign;
        }

        Vec3 along() {
            return alongX ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
        }

        Vec3 out() {
            return alongX ? new Vec3(0, 0, sign) : new Vec3(sign, 0, 0);
        }

        /** A point on the lip, f 0-1 across the opening (centred on the edge), h above the lip. */
        Vec3 lipPoint(double f, double h) {
            double w = Math.min(1, width), from = 0.5 - w / 2;
            Vec3 a = along();
            return new Vec3(x + a.x * (from + w * f), lip + h, z + a.z * (from + w * f));
        }

        double fallTime() {
            return Math.sqrt(2 * Math.max(0.01, lip + depth * 0.5 - bottom) / G);
        }

        /** Where the middle of it lands. */
        Vec3 landing() {
            return landing(0.5);
        }

        /** Where water leaving at f across the lip lands. */
        Vec3 landing(double f) {
            double t = fallTime();
            Vec3 p = lipPoint(f, depth * 0.5);
            return p.add(out().scale(speed * t)).add(0, -0.5 * G * t * t, 0);
        }

        double shownWidth() {
            return Math.max(0.05, Math.min(1, width));
        }
    }

    /** A drop in flight (off an edge, off a sheet's side, a splash). */
    static final class Drop {
        Vec3 pos, prev, vel;
        final float size;
        final boolean splash;
        int age;
        /** Where it ends (m): water's surface or the ground under it, found once (NaN: not yet); onto water or not. */
        double floor = Double.NaN;
        boolean ontoWater;

        Drop(Vec3 pos, Vec3 vel, float size, boolean splash) {
            this.pos = this.prev = pos;
            this.vel = vel;
            this.size = size;
            this.splash = splash;
        }
    }

    /** A wet mark where water landed on dry ground. */
    record Wet(Vec3 at, Direction face, long seed, float size, long born) {
    }

    /** Rings on AFL water: x, z in world blocks, how strong, when (seconds of WaterLook's clock; NaN: steady). */
    record Impact(double x, double z, float strength, float start) {
    }

    /** A player pouring: the stream from their hand. */
    static final class Pour {
        final LiquidJet jet = new LiquidJet(G, 6);
        float flow, speed;
    }

    /** A burst pipe's jet (LiquidPackets.Burst, M4): where and how it leaves, flown with the server's air drag. */
    static final class Burst {
        final LiquidJet jet;
        Vec3 at, dir;
        float speed, flow;

        Burst(float drag) {
            jet = new LiquidJet(G, 6).drag(drag);
        }
    }

    /** Falls side by side along one edge at about one height whose water runs together on top and leaves at one place. */
    static final class Group {
        final List<Fall> falls = new ArrayList<>(4);
        Fall main;
        /** L/s of all its falls; m of edge they cover. */
        double flow, width;
        int kind = -1;
    }

    private static final Long2ObjectOpenHashMap<List<Fall>> CHUNKS = new Long2ObjectOpenHashMap<>();
    /** This tick's groups (gather); the falls near enough by their edge. */
    private static final List<Group> GROUPS = new ArrayList<>();
    private static final Long2ObjectOpenHashMap<Fall> EDGES = new Long2ObjectOpenHashMap<>();
    private static final List<Fall> ENDED = new ArrayList<>();
    private static final List<Drop> DROPS = new ArrayList<>();
    /** Drops made while DROPS is being stepped (splashes of landing drops): they join it after. */
    private static final List<Drop> NEW_DROPS = new ArrayList<>();
    private static final List<Wet> WET = new ArrayList<>();
    private static final List<Impact> RINGS = new ArrayList<>();
    private static final Int2ObjectOpenHashMap<Pour> POURS = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<Burst> BURSTS = new Int2ObjectOpenHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static ClientLevel tracked;
    /** Dev switch ({@code /afl_render falls}); diagnostics. */
    public static boolean enabled = true;
    public static int lastDrops, lastJets, lastSheets, lastFilms, lastGroups;
    /** Readout: the last frame's drawing and the last tick's step (µs). */
    public static long lastDrawMicros, lastTickMicros;

    private ClientFalls() {
    }

    private static void track() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == tracked) return;
        CHUNKS.clear();
        ENDED.clear();
        GROUPS.clear();
        DROPS.clear();
        NEW_DROPS.clear();
        WET.clear();
        RINGS.clear();
        POURS.clear();
        BURSTS.clear();
        tracked = level;
    }

    public static void receive(LiquidPackets.Falls p) {
        track();
        long key = ChunkPos.asLong(p.chunkX(), p.chunkZ());
        List<Fall> old = CHUNKS.remove(key);
        List<Fall> now = new ArrayList<>();
        for (int i = 0; i < p.x().length; i++) {
            Fall f = new Fall(p.x()[i], p.z()[i], (p.dir()[i] & LiquidPackets.Falls.ALONG_X) != 0,
                    (p.dir()[i] & LiquidPackets.Falls.NEGATIVE) != 0 ? -1 : 1);
            if (old != null) {
                for (Fall o : old) {
                    if (!o.same(f)) continue;
                    f.shown = o.shown;
                    f.at = o.at;
                    f.main = o.main;
                    f.drip = o.drip;
                    o.tail = -2;   // carried on
                    break;
                }
            }
            f.lip = p.lip()[i];
            f.depth = p.depth()[i];
            f.bottom = p.bottom()[i];
            f.width = p.width()[i];
            f.flow = p.flow()[i];
            f.speed = p.speed()[i];
            now.add(f);
        }
        if (old != null) {
            for (Fall o : old) {
                if (o.tail == -2) continue;
                o.tail = 2.5F;   // it stopped: the last drops
                o.flow = 0;
                ENDED.add(o);
            }
        }
        if (!now.isEmpty()) CHUNKS.put(key, now);
    }

    public static void pour(LiquidPackets.Pour p) {
        track();
        Pour pour = POURS.get(p.entity());
        if (pour == null) {
            if (p.flow() <= 0) return;
            POURS.put(p.entity(), pour = new Pour());
        }
        pour.flow = p.flow();
        pour.speed = p.speed();
    }

    public static void burst(LiquidPackets.Burst p) {
        track();
        Burst b = BURSTS.get(p.id());
        if (b == null) {
            if (p.flow() <= 0) return;
            BURSTS.put(p.id(), b = new Burst(p.drag()));
        }
        b.at = new Vec3(p.x(), p.y(), p.z());
        b.dir = new Vec3(p.dx(), p.dy(), p.dz());
        b.speed = p.speed();
        b.flow = p.flow();
    }

    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() == null || !event.getLevel().isClientSide()) return;
        CHUNKS.remove(event.getChunk().getPos().toLong());
    }

    // ---- the tick ---------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed) return;
        Minecraft mc = Minecraft.getInstance();
        track();
        if (mc.isPaused() || mc.level == null || mc.player == null) return;
        try {
            long t0 = System.nanoTime();
            step(mc);
            lastTickMicros = (System.nanoTime() - t0) / 1000;
        } catch (RuntimeException e) {
            // a picture only: switched off, the game goes on
            failed = true;
            LogUtils.getLogger().error("AFL falling water: switched off after an error", e);
        }
    }

    private static void step(Minecraft mc) {
        ClientLevel level = mc.level;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double r2 = RANGE * RANGE;
        for (List<Fall> falls : CHUNKS.values()) {
            for (Fall f : falls) {
                if (f.lipPoint(0.5, 0).distanceToSqr(cam) > r2) continue;
                f.shown += (f.flow - f.shown) * 0.2F;
            }
        }
        for (Iterator<Fall> it = ENDED.iterator(); it.hasNext(); ) {
            Fall f = it.next();
            f.tail -= 0.05F;
            f.shown *= 0.7F;
            // the last drops, ever fewer
            if (f.tail > 0 && RANDOM.nextFloat() < 0.35F * f.tail / 2.5F) {
                drop(f.lipPoint(f.at < 0 ? RANDOM.nextFloat() : f.at, 0), f.out().scale(0.05), 0.016F, false);
            }
            if (f.tail <= 0) it.remove();
        }
        gather(cam);
        for (Group g : GROUPS) run(level, g);
        for (Iterator<Int2ObjectOpenHashMap.Entry<Pour>> it = POURS.int2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
            Int2ObjectOpenHashMap.Entry<Pour> e = it.next();
            Pour pour = e.getValue();
            Entity who = level.getEntity(e.getIntKey());
            if (who != null && pour.flow > 0) {
                Vec3 look = who.getLookAngle();
                Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
                Vec3 from = who.getEyePosition().add(look.scale(0.45)).add(right.scale(0.25)).add(0, -0.3, 0);
                pour.jet.emit(from, look.scale(pour.speed), RANDOM.nextDouble() * 0.05);
            } else pour.jet.gap();
            tickJet(level, pour.jet, pour.flow, null);
            if ((who == null || pour.flow <= 0) && pour.jet.isEmpty()) it.remove();
        }
        for (Iterator<Burst> it = BURSTS.values().iterator(); it.hasNext(); ) {
            Burst b = it.next();
            if (b.flow > 0) {
                // two a tick: a fast jet's parcels are over a metre apart at one
                Vec3 v = b.dir.scale(b.speed);
                b.jet.emit(b.at, v, LiquidJet.STEP);
                b.jet.emit(b.at, v, 0);
            } else b.jet.gap();
            tickJet(level, b.jet, b.flow, null);
            if (b.flow <= 0 && b.jet.isEmpty()) it.remove();
        }
        DROPS.addAll(NEW_DROPS);
        NEW_DROPS.clear();
        tickDrops(level);
        DROPS.addAll(NEW_DROPS);
        NEW_DROPS.clear();
        long now = level.getGameTime();
        WET.removeIf(w -> now - w.born > 400);
        float t = clock(level, 0);
        RINGS.removeIf(i -> !Float.isNaN(i.start) && (t - i.start > 2.5F || t < i.start));
    }

    /** The edge a fall goes over, as a key: (x, z) its west or north end, its direction. */
    private static long edge(int x, int z, Fall like) {
        return ((long) (x & 0x3FFFFFF) << 28) | ((long) (z & 0x3FFFFFF) << 2) | (like.alongX ? 1 : 0) | (like.sign < 0 ? 2 : 0);
    }

    /** This tick's groups: the falls near the camera, edge by edge, each run of side by side falls split into groups. */
    private static void gather(Vec3 cam) {
        GROUPS.clear();
        EDGES.clear();
        double r2 = RANGE * RANGE;
        for (Fall f : ENDED) if (f.shown * 1000 >= 0.5 && f.lipPoint(0.5, 0).distanceToSqr(cam) <= r2) EDGES.put(edge(f.x, f.z, f), f);
        for (List<Fall> falls : CHUNKS.values()) {
            for (Fall f : falls) if (f.lipPoint(0.5, 0).distanceToSqr(cam) <= r2) EDGES.put(edge(f.x, f.z, f), f);
        }
        List<Fall> run = new ArrayList<>();
        for (Fall f : EDGES.values()) {
            int ax = f.alongX ? 1 : 0, az = f.alongX ? 0 : 1;
            Fall before = EDGES.get(edge(f.x - ax, f.z - az, f));
            if (before != null && Math.abs(before.lip - f.lip) < SAME_EDGE) continue;   // not where a run starts
            run.clear();
            for (Fall c = f; c != null && run.size() < 256; ) {
                run.add(c);
                Fall next = EDGES.get(edge(c.x + ax, c.z + az, c));
                c = next != null && Math.abs(next.lip - c.lip) < SAME_EDGE ? next : null;
            }
            split(run);
        }
    }

    /**
     * One run of side by side falls into groups: along the edge, falls gather until they carry STRAND_FLOW over their span
     * (or GATHER_MAX m); a sheet is a group of its own, the water either side of it goes its own way; too little left at the
     * end goes with the group before it.
     */
    private static void split(List<Fall> run) {
        List<Group> made = new ArrayList<>();
        Group g = null;
        for (Fall f : run) {
            double w = f.shownWidth();
            if (f.shown * 1000 >= DRIP_MAX && f.shown / w >= SHEET_Q) {
                g = null;
                Group sheet = new Group();
                sheet.falls.add(f);
                sheet.flow = f.shown;
                sheet.width = w;
                sheet.kind = SHEET;
                made.add(sheet);
                continue;
            }
            if (g == null) made.add(g = new Group());
            g.falls.add(f);
            g.flow += f.shown;
            g.width += w;
            double span = g.flow / g.width < 0.5 ? 1.5 : 0.75;
            if (g.flow >= STRAND_FLOW && g.width >= span || g.width >= GATHER_MAX) g = null;
        }
        if (g != null && g.flow < STRAND_FLOW && made.size() > 1) {
            Group before = made.get(made.size() - 2);
            if (before.kind != SHEET) {
                before.falls.addAll(g.falls);
                before.flow += g.flow;
                before.width += g.width;
                made.remove(made.size() - 1);
            }
        }
        for (Group m : made) {
            if (m.kind != SHEET) m.kind = m.flow * 1000 < DRIP_MAX ? DRIP : m.flow < CLING_FLOW ? CLING : STRAND;
            choose(m);
            GROUPS.add(m);
        }
    }

    /** Where a group's water leaves: the fall with the most (the last one kept while it has 80 % of that), at its seed's place. */
    private static void choose(Group g) {
        Fall best = g.falls.get(0), kept = null;
        for (Fall f : g.falls) {
            if (f.shown > best.shown) best = f;
            if (f.main) kept = f;
        }
        Fall main = kept != null && kept.shown >= 0.8 * best.shown ? kept : best;
        for (Fall f : g.falls) f.main = f == main;
        g.main = main;
        if (main.at < 0) main.at = g.kind == SHEET ? 0.5F : 0.25F + 0.5F * RandomSource.create(main.seed).nextFloat();
    }

    /** A group this tick: its drips, the drips off the end of its film, its stream's splash and the drops off it, or its sheet's. */
    private static void run(ClientLevel level, Group g) {
        Fall f = g.main;
        switch (g.kind) {
            case DRIP -> {
                f.drip = Math.min(f.drip + g.flow * 1000 / 20, DROP_ML * 8);
                int n = 0;
                while (f.drip >= DROP_ML && n++ < 4) {
                    f.drip -= DROP_ML;
                    drop(f.lipPoint(f.at, 0).add(f.out().scale(0.01)), f.out().scale(Math.min(0.3, f.speed)), 0.016F, false);
                }
            }
            case CLING -> {
                // the film runs down the wall; where the wall ends above where it lands, it drips off there
                float wall = wall(level, f);
                if (wall > f.bottom + 0.05) {
                    f.drip = Math.min(f.drip + g.flow * 1000 / 20, DROP_ML * 16);
                    int n = 0;
                    while (f.drip >= DROP_ML * 3 && n++ < 2) {
                        f.drip -= DROP_ML * 3;
                        drop(f.lipPoint(f.at, wall - f.lip).add(f.out().scale(0.01)), Vec3.ZERO, 0.02F, false);
                    }
                }
            }
            case STRAND -> {
                double height = f.lip + f.depth * 0.5 - f.bottom;
                if (RANDOM.nextFloat() < 0.5F) splash(level, f.landing(f.at), g.flow, height, 0.15);
                // below where it breaks up, drops come away from it
                double broken = breakup(g.flow, f.speed);
                if (height > broken && RANDOM.nextFloat() < 0.6F) {
                    double down = broken + RANDOM.nextDouble() * (height - broken);
                    double t = Math.sqrt(2 * down / G);
                    Vec3 p = f.lipPoint(f.at, f.depth * 0.5).add(f.out().scale(f.speed * t)).add(0, -down, 0);
                    Vec3 v = f.out().scale(f.speed).add((RANDOM.nextDouble() - 0.5) * 0.3, -G * t, (RANDOM.nextDouble() - 0.5) * 0.3);
                    drop(p, v, 0.018F, false);
                }
            }
            case SHEET -> {
                // drops flung off its sides on the way down, and the splash where it lands
                double q = g.flow / g.width;
                double t = f.fallTime() * RANDOM.nextDouble();
                if (RANDOM.nextFloat() < Math.min(1, q * 0.3)) {
                    double side = RANDOM.nextBoolean() ? 0 : 1;
                    Vec3 p = f.lipPoint(side, f.depth).add(f.out().scale(f.speed * t)).add(0, -0.5 * G * t * t, 0);
                    Vec3 v = f.out().scale(f.speed).add(f.along().scale((side - 0.5) * 0.8)).add(0, -G * t, 0);
                    drop(p, v, 0.02F, false);
                }
                splash(level, f.landing(), f.shown, f.lip - f.bottom, g.width);
            }
            default -> {
            }
        }
    }

    /**
     * How far (m) a stream falls before it breaks into drops: off a rough natural edge, sooner than a smooth jet; 0.3 m and
     * 30 diameters more, at most 2 m (picked to look right, not from a formula).
     */
    private static double breakup(double flow, double speed) {
        return Math.min(2, 0.3 + 30 * 2 * radius(flow, Math.max(0.2, speed)));
    }

    /** Where the wall under f's lip ends (m): the top of the first open block under where its water leaves, or where it lands. */
    private static float wall(ClientLevel level, Fall f) {
        if (Float.isNaN(f.wall)) {
            float end = f.bottom;
            Vec3 in = f.lipPoint(Math.max(0, f.at), 0).subtract(f.out().scale(0.05));
            int top = Mth.floor(f.lip - 0.01), low = Mth.floor(f.bottom);
            for (int y = top; y >= low && y > top - 32; y--) {
                BlockPos p = BlockPos.containing(in.x, y + 0.5, in.z);
                if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                    end = Math.max(f.bottom, y + 1);
                    break;
                }
            }
            f.wall = end;
        }
        return f.wall;
    }

    /** One stream's parcels fly; where they land, a splash (flow L/s through the stream; from a fall, or null). */
    private static void tickJet(ClientLevel level, LiquidJet jet, double flow, Fall from) {
        jet.tick(level, null, (p, hit) -> {
            if (RANDOM.nextFloat() < 0.5F) landed(level, p.pos, hit.getDirection(), hit.getBlockPos(), p.vel, flow);
        }, ClientLiquid::surfaceBelow, (p, at) -> {
            if (RANDOM.nextFloat() < 0.5F) splashUp(at, p.vel, flow, 2);
            if (RANDOM.nextFloat() < 0.15F) ring(at, (float) Math.min(0.5, 0.08 + flow * 0.3));
        });
    }

    /** Where a fall lands each tick: rings (steady ones are {@link #impacts}'s) and drops thrown up. */
    private static void splash(ClientLevel level, Vec3 at, double flow, double height, double width) {
        int n = (int) Math.min(6, flow * Math.max(0.3, height) * 0.8 + RANDOM.nextFloat());
        Vec3 down = new Vec3(0, -Math.sqrt(2 * G * Math.max(0.1, height)), 0);
        double surface = ClientLiquid.surfaceBelow(at.x, at.y + 0.05, at.z);
        for (int i = 0; i < n; i++) {
            Vec3 p = at.add((RANDOM.nextDouble() - 0.5) * width, 0, (RANDOM.nextDouble() - 0.5) * width);
            if (!Double.isNaN(surface)) splashUp(new Vec3(p.x, surface, p.z), down, flow, 1);
            else {
                BlockHitResult hit = level.clip(new ClipContext(p.add(0, 0.3, 0), p.add(0, -0.6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
                if (hit.getType() == HitResult.Type.BLOCK) landed(level, hit.getLocation(), hit.getDirection(), hit.getBlockPos(), down, flow / n);
            }
        }
    }

    private static void splashUp(Vec3 at, Vec3 vel, double flow, int count) {
        double speed = Math.min(3, vel.length());
        for (int i = 0; i < count; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2, s = speed * (0.15 + RANDOM.nextDouble() * 0.25);
            drop(at.add(0, 0.01, 0), new Vec3(Math.cos(a) * s * 0.6, s * (0.8 + RANDOM.nextDouble() * 0.6), Math.sin(a) * s * 0.6), 0.01F, true);
        }
    }

    /** Water reaching a block: a few small drops bounce off. */
    private static void landed(ClientLevel level, Vec3 at, Direction face, BlockPos block, Vec3 vel, double flow) {
        splashUp(at.add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.01)), vel, flow, 1);
        // no stain of its own: the water lands in the engine, and its film is the wet ground (ClientLiquid#drawDamp)
    }

    private static void drop(Vec3 at, Vec3 vel, float size, boolean splash) {
        if (DROPS.size() + NEW_DROPS.size() >= MAX_DROPS) return;
        NEW_DROPS.add(new Drop(at, vel, size, splash));
    }

    private static void ring(Vec3 at, float strength) {
        Minecraft mc = Minecraft.getInstance();
        if (RINGS.size() >= 64) RINGS.remove(0);
        RINGS.add(new Impact(at.x, at.z, strength, clock(mc.level, 0)));
    }

    /**
     * Drops fall under gravity until they reach where they end: AFL water's surface, or the ground under them, found by
     * one ray cast when the drop starts (not every tick: that was the cost, falling water 15). A drop thrown sideways off
     * an edge may end a little short of or past it.
     */
    private static void tickDrops(ClientLevel level) {
        for (Iterator<Drop> it = DROPS.iterator(); it.hasNext(); ) {
            Drop d = it.next();
            if (Double.isNaN(d.floor)) findFloor(level, d);
            d.prev = d.pos;
            d.vel = d.vel.add(0, -G / 20, 0);
            Vec3 next = d.pos.add(d.vel.scale(1 / 20.0));
            if (++d.age > 120) {
                it.remove();
                continue;
            }
            if (d.vel.y < 0 && next.y <= d.floor) {
                Vec3 at = new Vec3(next.x, d.floor, next.z);
                if (d.ontoWater) {
                    if (!d.splash || RANDOM.nextFloat() < 0.3F) ring(at, d.splash ? 0.06F : 0.18F);
                    if (!d.splash && RANDOM.nextFloat() < 0.5F) splashUp(at, d.vel, 0.001, 1);
                } else if (!d.splash && RANDOM.nextFloat() < 0.5F) splashUp(at, d.vel, 0.001, 1);
                it.remove();
                continue;
            }
            d.pos = next;
        }
    }

    /** Where a drop ends: a splash comes back down where it was thrown up; any other the water or ground below it. */
    private static void findFloor(ClientLevel level, Drop d) {
        double water = ClientLiquid.surfaceBelow(d.pos.x, d.pos.y + 0.05, d.pos.z);
        if (d.splash) {
            d.floor = d.pos.y - 0.02;
            d.ontoWater = !Double.isNaN(water);
            return;
        }
        BlockHitResult hit = level.clip(new ClipContext(d.pos, d.pos.add(0, -24, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        double ground = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().y : d.pos.y - 24;
        // the nearest AFL water under it, above that ground
        double below = Double.NaN;
        for (double y = d.pos.y; y > ground; y -= 1) {
            below = ClientLiquid.surfaceBelow(d.pos.x, y, d.pos.z);
            if (!Double.isNaN(below)) break;
        }
        d.ontoWater = !Double.isNaN(below) && below > ground;
        d.floor = d.ontoWater ? below : ground;
    }

    /** WaterLook's clock: seconds, wrapping every 1,200 s (afl_water's WaterTime). */
    static float clock(ClientLevel level, float partial) {
        return level == null ? 0 : ((level.getGameTime() % 24000L) + partial) / 20F;
    }

    // ---- for the water program ------------------------------------------------------------------------------------

    /**
     * Up to 16 rings for afl_water, nearest the camera first, into {@code out} (x, z from the texture origin (ox, oz),
     * strength, start or -1 for a steady one); returns how many.
     */
    static int impacts(float[] out, Vec3 cam, double ox, double oz) {
        List<Impact> all = new ArrayList<>(RINGS);
        for (Group g : GROUPS) {
            if (g.kind != STRAND && g.kind != SHEET) continue;
            Fall f = g.main;
            Vec3 at = f.landing(f.at);
            if (Double.isNaN(ClientLiquid.surfaceBelow(at.x, f.bottom + 0.05, at.z))) continue;
            all.add(new Impact(at.x, at.z, (float) Math.min(0.6, 0.08 + g.flow * 0.12), Float.NaN));
        }
        all.sort(java.util.Comparator.comparingDouble(i -> (i.x - cam.x) * (i.x - cam.x) + (i.z - cam.z) * (i.z - cam.z)));
        int n = Math.min(MAX_IMPACTS, all.size());
        for (int i = 0; i < n; i++) {
            Impact im = all.get(i);
            out[i * 4] = (float) (im.x - ox);
            out[i * 4 + 1] = (float) (im.z - oz);
            out[i * 4 + 2] = im.strength;
            out[i * 4 + 3] = Float.isNaN(im.start) ? -1 : im.start;
        }
        return n;
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    private static boolean failed;

    /** After the water (ClientLiquid#render, the same stage, a lower priority): streams, sheets, drops and wet marks. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !enabled || failed) return;
        if (AflShaderCompat.activeShadowPass()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        track();
        if (CHUNKS.isEmpty() && ENDED.isEmpty() && POURS.isEmpty() && BURSTS.isEmpty() && DROPS.isEmpty() && WET.isEmpty()) return;
        try {
            long t0 = System.nanoTime();
            render(mc, mc.level, event.getPoseStack(), event.getCamera().getPosition(), event.getPartialTick(), event.getProjectionMatrix());
            lastDrawMicros = (System.nanoTime() - t0) / 1000;
        } catch (RuntimeException e) {
            failed = true;
            LogUtils.getLogger().error("AFL falling water: switched off after an error", e);
        }
    }

    private static final BufferBuilder BUILDER = new BufferBuilder(1 << 18);
    private static VertexBuffer buffer;
    /** This frame draws the water in the air with afl_water_fall (no shader pack): the vertex colours carry its inputs. */
    private static boolean look;
    public static boolean lastLook;

    /**
     * Two ways (as the standing water, ClientLiquid): without a shader pack the streams, sheets and drops go into one buffer
     * drawn by afl_water_fall over a fresh copy of the frame (the standing water in it): clear water that bends what is
     * behind it, mirrors the sky at grazing angles, whitens where it is broken up. With a pack (or in Fabulous), vanilla's
     * translucent entity type, faint. Wet marks (with a pack only: no darkened ground without one) and the runs down walls
     * are decals.
     */
    private static void render(Minecraft mc, ClientLevel level, PoseStack pose, Vec3 cam, float partial, Matrix4f projection) {
        var atlas = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        // without a shader pack water in the air is drawn again, now with the standing water's own look
        // (afl_water_look.glsl, Mesh water M3, the user's 9.1 2026-10-26); falling water 19 had turned it off because
        // it did not match ("水帘你不删吗？材质都不一样", 2026-10-25)
        TextureAtlasSprite stream = atlas.apply(FALL_SPRITE), sheet = atlas.apply(FALL_SPRITE), drop = atlas.apply(DROP);
        MultiBufferSource.BufferSource vanilla = mc.renderBuffers().bufferSource();
        ShaderInstance program = AflShaderCompat.shaderPackInUse() || Minecraft.useShaderTransparency() ? null : WaterLook.fallProgram();
        look = program != null;
        lastLook = look;
        MultiBufferSource buffers;
        PoseStack at;
        if (look) {
            BUILDER.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            buffers = type -> BUILDER;
            at = new PoseStack();   // camera-relative, unturned: drawn with the view matrix
        } else {
            buffers = vanilla;
            at = pose;
        }
        int jets = 0, sheets = 0, films = 0;
        for (Group g : GROUPS) {
            Fall f = g.main;
            switch (g.kind) {
                case STRAND -> {
                    drawStrand(level, at, buffers, cam, partial, g, stream);
                    jets++;
                }
                case SHEET -> {
                    drawSheet(level, at, buffers, cam, f, sheet);
                    sheets++;
                }
                case CLING -> {
                    if (drawFilm(level, at, buffers, cam, partial, g, sheet) > 0) films++;
                }
                default -> {
                }
            }
        }
        for (Int2ObjectOpenHashMap.Entry<Pour> e : POURS.int2ObjectEntrySet()) {
            Pour p = e.getValue();
            if (p.jet.isEmpty()) continue;
            BlockPos origin = BlockPos.containing(p.jet.parcels().getLast().pos);
            double radius = radius(Math.max(p.flow, 0.05), Math.max(p.speed, 0.5));
            at.pushPose();
            at.translate(origin.getX() - cam.x, origin.getY() - cam.y, origin.getZ() - cam.z);
            LiquidJetRenderer.render(at, buffers, level, origin, p.jet, null, partial, stream, streamRgb(), streamAlpha(), radius, p.speed, 6,
                    0.25 + radius * 20);
            at.popPose();
            jets++;
        }
        for (Burst b : BURSTS.values()) {
            if (b.jet.isEmpty()) continue;
            BlockPos origin = BlockPos.containing(b.jet.parcels().getLast().pos);
            double radius = radius(Math.max(b.flow, 0.05), Math.max(b.speed, 0.5));
            at.pushPose();
            at.translate(origin.getX() - cam.x, origin.getY() - cam.y, origin.getZ() - cam.z);
            LiquidJetRenderer.render(at, buffers, level, origin, b.jet, null, partial, stream, streamRgb(), streamAlpha(), radius, b.speed, 6,
                    0.25 + radius * 20);
            at.popPose();
            jets++;
        }
        drawDrops(level, at, buffers, cam, partial, drop);
        long now = level.getGameTime();
        // wet marks darken the ground: only for a shader pack's eyes; without one the ground is not drawn darker (the
        // user 2026-10-25: wet looks are the pack's)
        if (AflShaderCompat.shaderPackInUse()) for (Wet w : WET) {
            float age = (now - w.born + partial) / 400F;
            int alpha = (int) (110 * (1 - age) * Math.min(1, age * 40));
            Vec3 u = new Vec3(1, 0, 0), v = new Vec3(0, 0, 1);
            LiquidDecals.draw(pose, vanilla, level, cam, w.at, w.face, u, v, w.seed, w.size, -1, 1, -1, 1, 0, 0, 0x3A3530, alpha);
        }
        vanilla.endBatch();
        if (look) {
            BufferBuilder.RenderedBuffer rendered = BUILDER.endOrDiscardIfEmpty();
            if (rendered != null) {
                RenderTarget main = mc.getMainRenderTarget();
                // a copy with the standing water in it: what is only partly there blends with it (falling water 17)
                if (!WaterLook.copyScene(main)) rendered.release();
                else {
                    WaterLook.setUpFall(program, level, cam, partial, projection, main);
                    RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
                    mc.gameRenderer.lightTexture().turnOnLightLayer();
                    RenderSystem.disableBlend();
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthMask(true);
                    RenderSystem.disableCull();
                    if (buffer == null) buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
                    buffer.bind();
                    buffer.upload(rendered);
                    buffer.drawWithShader(pose.last().pose(), projection, program);
                    VertexBuffer.unbind();
                    RenderSystem.enableCull();
                    mc.gameRenderer.lightTexture().turnOffLightLayer();
                }
            }
        }
        lastJets = jets;
        lastSheets = sheets;
        lastFilms = films;
        lastGroups = GROUPS.size();
        lastDrops = DROPS.size();
    }

    /**
     * The vertex colour of water in the air. For afl_water_fall: red 1 for a drop (the sprite's alpha is its outline), green
     * how broken up and white it is (times the streak texture's alpha), alpha how much of it is there (dithered). For the
     * vanilla type: a faint near-white.
     */
    private static int streamRgb() {
        return look ? 0x002600 : WATER_RGB;
    }

    private static int streamAlpha() {
        return look ? 255 : 110;
    }

    /**
     * A group with too little water to jump clear: a clear film down the wall under where it leaves, from the lip to where
     * the wall ends (or it lands), as wide as its flow, wandering a little and narrowing as it runs down; its edges fade and
     * their normals lean away so it reads as a rounded run of water, not a stain (no darkening: the user, 2026-10-25).
     * Returns its half width at the top (m), 0 when there is no wall to run down.
     */
    private static double drawFilm(ClientLevel level, PoseStack pose, MultiBufferSource buffers, Vec3 cam, float partial, Group g,
                                   TextureAtlasSprite sprite) {
        Fall f = g.main;
        double length = f.lip - wall(level, f);
        if (length < 0.05) return 0;
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        double half = Math.min(0.2, 0.03 + g.flow * 2);
        int rows = Math.max(2, Math.min(10, (int) Math.ceil(length / 0.2)));
        Vec3 outward = f.out(), along = f.along();
        Vec3 top = f.lipPoint(f.at, 0).add(outward.scale(0.004));
        double phase = (f.seed & 1023) / 163.0;
        float u0 = sprite.getU0(), u1 = sprite.getU1(), um = (u0 + u1) / 2;
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(top.add(outward.scale(0.3))));
        Vec3[][] row = new Vec3[rows + 1][3];
        for (int r = 0; r <= rows; r++) {
            double d = (double) r / rows, down = length * d;
            double wander = Math.sin(phase + down * 3.1) * 0.02 * d;
            double w = half * (1 - 0.3 * d) * (1 + 0.2 * Math.sin(phase * 2 + down * 7.3));
            Vec3 c = top.add(along.scale(wander)).add(0, -down, 0);
            row[r][0] = c.subtract(along.scale(w)).subtract(cam);
            row[r][1] = c.subtract(cam);
            row[r][2] = c.add(along.scale(w)).subtract(cam);
        }
        Vec3[] normal = {outward.scale(0.6).subtract(along).normalize(), outward, outward.scale(0.6).add(along).normalize()};
        float[] us = {u0, um, u1};
        float[] cover = {0.45F, 1, 0.45F};
        for (int r = 0; r < rows; r++) {
            float va = sprite.getV((float) ((length * r / rows) % 1.0) * 16), vb = sprite.getV(Math.min(1, (float) ((length * r / rows) % 1.0 + length / rows)) * 16);
            float[] vs = {va, vb};
            for (int k = 0; k < 2; k++) {
                int[][] idx = {{r, k}, {r + 1, k}, {r + 1, k + 1}, {r, k + 1}};
                for (int[] c : idx) {
                    Vec3 p = row[c[0]][c[1]], n = normal[c[1]];
                    VertexConsumer vc = out.vertex(m, (float) p.x, (float) p.y, (float) p.z);
                    // the program: metres across and down, a film a few millimetres thick
                    if (look) vc.color(0, 20, 8, (int) (255 * cover[c[1]])).uv((float) ((c[1] - 1) * half), (float) (length * c[0] / rows));
                    else vc.color(235, 242, 246, (int) (70 * cover[c[1]])).uv(us[c[1]], vs[c[0] - r]);
                    vc.overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, (float) n.x, (float) n.y, (float) n.z).endVertex();
                }
            }
        }
        return half;
    }

    /** A stream's radius (m): its flow (L/s) through a round section at its speed (m/s). */
    private static double radius(double flow, double speed) {
        return Math.max(0.003, Math.min(0.05, Math.sqrt(flow / 1000 / (Math.PI * speed))));
    }

    /** Segments a stream's ribbon is cut into along its fall. */
    private static final int STRAND_SEGMENTS = 10;

    /**
     * A group's stream: a ribbon facing the camera along the free-fall parabola from where it leaves to where it lands, two
     * quads across (the middle facing the camera, the edges' normals leaning out, so it reads round). As wide as its flow
     * at its speed, never under 1 cm across to be seen, narrowing as it speeds up (the same flow through a smaller section,
     * radius as the square root of the speed's ratio); a slow sway. Below where it breaks up ({@link #breakup}) it
     * thins further, turns white and is only partly there. Returns its half width where it leaves (m).
     */
    private static double drawStrand(ClientLevel level, PoseStack pose, MultiBufferSource buffers, Vec3 cam, float partial, Group g,
                                     TextureAtlasSprite sprite) {
        Fall f = g.main;
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        double speed = Math.max(0.2, f.speed);
        double head = Math.max(0.006, radius(g.flow, speed));
        double total = f.fallTime(), height = Math.max(0.01, f.lip + f.depth * 0.5 - f.bottom);
        double broken = breakup(g.flow, speed);
        Vec3 outward = f.out(), along = f.along();
        float time = clock(level, partial);
        float u0 = sprite.getU0(), u1 = sprite.getU1(), um = (u0 + u1) / 2;
        double phase = (f.seed & 1023) / 163.0;
        Vec3[] p = new Vec3[STRAND_SEGMENTS + 1];
        float[] v = new float[STRAND_SEGMENTS + 1], white = new float[STRAND_SEGMENTS + 1], cover = new float[STRAND_SEGMENTS + 1];
        double[] half = new double[STRAND_SEGMENTS + 1];
        double length = 0;
        for (int i = 0; i <= STRAND_SEGMENTS; i++) {
            double t = total * i / STRAND_SEGMENTS;
            double down = 0.5 * G * t * t;
            double sway = Math.sin(time * 3.1 + phase + t * 9) * 0.01 * Math.min(1, down);
            p[i] = f.lipPoint(f.at, f.depth * 0.5).add(outward.scale(f.speed * t)).add(along.scale(sway)).add(0, -down, 0);
            if (i > 0) length += p[i].distanceTo(p[i - 1]);
            v[i] = (float) length;
            double vel = Math.sqrt(speed * speed + G * t * G * t);
            double b = Math.max(0, Math.min(1, (down - broken) / Math.max(0.2, height - broken)));
            half[i] = Math.max(0.005, head * Math.sqrt(speed / vel) * (1 - 0.3 * b));
            white[i] = (float) Math.min(1, 0.12 + 0.75 * b);
            cover[i] = (float) (1 - 0.65 * b);
        }
        for (int i = 0; i < STRAND_SEGMENTS; i++) {
            Vec3 a = p[i], b = p[i + 1];
            Vec3 dir = b.subtract(a);
            if (dir.lengthSqr() < 1e-8) continue;
            dir = dir.normalize();
            int la = LevelRenderer.getLightColor(level, BlockPos.containing(a)), lb = LevelRenderer.getLightColor(level, BlockPos.containing(b));
            // a frame of the streak sprite a metre of fall, running down
            float va = sprite.getV((v[i] % 1.0F) * 16), vb = sprite.getV(Math.min(1, (v[i] % 1.0F) + (v[i + 1] - v[i])) * 16);
            Vec3[] at = {a, b};
            float[] vs = {va, vb};
            int[] ls = {la, lb};
            Vec3[][] row = new Vec3[2][3];
            Vec3[][] nrm = new Vec3[2][3];
            for (int e = 0; e < 2; e++) {
                Vec3 rel = at[e].subtract(cam);
                Vec3 side = dir.cross(rel);
                if (side.lengthSqr() < 1e-8) side = along;
                side = side.normalize();
                double w = half[i + e];
                Vec3 toEye = rel.normalize().scale(-1);
                row[e][0] = rel.subtract(side.scale(w));
                row[e][1] = rel;
                row[e][2] = rel.add(side.scale(w));
                nrm[e][0] = toEye.scale(0.45).subtract(side).normalize();
                nrm[e][1] = toEye;
                nrm[e][2] = toEye.scale(0.45).add(side).normalize();
            }
            float[] us = {u0, um, u1};
            for (int k = 0; k < 2; k++) {
                int[][] idx = {{0, k}, {1, k}, {1, k + 1}, {0, k + 1}};
                for (int[] c : idx) {
                    Vec3 pos = row[c[0]][c[1]], nn = nrm[c[0]][c[1]];
                    float wh = white[i + c[0]], cv = cover[i + c[0]];
                    VertexConsumer vc = out.vertex(m, (float) pos.x, (float) pos.y, (float) pos.z);
                    // the program: metres across and down it, the water through it as thick as it is wide
                    double w = half[i + c[0]];
                    if (look) vc.color(0, (int) (255 * wh), (int) Math.min(255, 255 * 2 * w / 0.1), (int) (255 * cv))
                            .uv((float) ((c[1] - 1) * w), v[i + c[0]]);
                    else vc.color(235, 242, 246, (int) ((110 + 80 * wh) * cv)).uv(us[c[1]], vs[c[0]]);
                    vc.overlayCoords(OverlayTexture.NO_OVERLAY).uv2(ls[c[0]]).normal(nm, (float) nn.x, (float) nn.y, (float) nn.z).endVertex();
                }
            }
        }
        return head;
    }

    /**
     * A sheet: strips side by side along the free-fall parabola over the opening's width, both sides, drawn in a little at
     * the sides as it falls. Each strip comes apart from its own share of the height down (35-75 %, from the fall's seed):
     * narrower, so gaps open between them, partly there and whiter, so its lower edge is ragged, not a cut line.
     */
    private static void drawSheet(ClientLevel level, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Fall f, TextureAtlasSprite sprite) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        int segments = 12;
        double total = f.fallTime(), height = Math.max(0.05, f.lip + f.depth - f.bottom);
        Vec3 outward = f.out(), along = f.along();
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        int strips = Math.max(3, (int) Math.ceil(f.shownWidth() * 8));
        int thick = (int) Math.min(255, 255 * Math.max(0.004, f.depth) / 0.1);
        RandomSource r = RandomSource.create(f.seed);
        float[] apart = new float[strips];
        for (int j = 0; j < strips; j++) apart[j] = 0.35F + 0.4F * r.nextFloat();
        Vec3[] drop = new Vec3[segments + 1];
        double[] down = new double[segments + 1], pull = new double[segments + 1];
        float[] v = new float[segments + 1];
        int[] light = new int[segments + 1];
        double length = 0;
        for (int i = 0; i <= segments; i++) {
            double t = total * i / segments;
            down[i] = 0.5 * G * t * t;
            drop[i] = outward.scale(f.speed * t).add(0, -down[i], 0);
            pull[i] = 1 - 0.08 * Math.min(1, down[i] / height);
            if (i > 0) length += drop[i].distanceTo(drop[i - 1]);
            v[i] = (float) length;
            light[i] = LevelRenderer.getLightColor(level, BlockPos.containing(f.lipPoint(0.5, f.depth).add(drop[i])));
        }
        for (int i = 0; i < segments; i++) {
            Vec3 normal = drop[i + 1].subtract(drop[i]).cross(along).normalize();
            float va = sprite.getV((v[i] % 1.0F) * 16), vb = sprite.getV(Math.min(1, (v[i] % 1.0F) + (v[i + 1] - v[i])) * 16);
            for (int j = 0; j < strips; j++) {
                double mid = (j + 0.5) / strips, half = 0.5 / strips;
                Vec3[] lo = new Vec3[2], hi = new Vec3[2];
                float[] cover = new float[2], white = new float[2];
                for (int e = 0; e < 2; e++) {
                    int k = i + e;
                    double b = Math.max(0, Math.min(1, (down[k] / height - apart[j]) / (1 - apart[j])));
                    double w = half * (1 - 0.45 * b);
                    double a0 = 0.5 + (mid - w - 0.5) * pull[k], a1 = 0.5 + (mid + w - 0.5) * pull[k];
                    lo[e] = f.lipPoint(a0, f.depth).add(drop[k]).subtract(cam);
                    hi[e] = f.lipPoint(a1, f.depth).add(drop[k]).subtract(cam);
                    cover[e] = (float) (1 - 0.7 * b);
                    white[e] = (float) Math.min(1, 0.1 + 0.8 * Math.max(b, Math.max(0, (down[k] - 0.4) / 1.5)));
                }
                float ua = u0 + (u1 - u0) * (float) (mid - half), ub = u0 + (u1 - u0) * (float) (mid + half);
                for (int side = 0; side < 2; side++) {
                    Vec3 n = side == 0 ? normal : normal.scale(-1);
                    Vec3[] q = side == 0 ? new Vec3[]{lo[0], lo[1], hi[1], hi[0]} : new Vec3[]{hi[0], hi[1], lo[1], lo[0]};
                    float[] us = side == 0 ? new float[]{ua, ua, ub, ub} : new float[]{ub, ub, ua, ua};
                    float[] vs = {va, vb, vb, va};
                    int[] ls = {light[i], light[i + 1], light[i + 1], light[i]};
                    int[] ends = {0, 1, 1, 0};
                    float wa = (float) ((mid - half - 0.5) * f.shownWidth()), wb = (float) ((mid + half - 0.5) * f.shownWidth());
                    float[] across = side == 0 ? new float[]{wa, wa, wb, wb} : new float[]{wb, wb, wa, wa};
                    for (int k = 0; k < 4; k++) {
                        float cv = cover[ends[k]], wh = white[ends[k]];
                        VertexConsumer vc = out.vertex(m, (float) q[k].x, (float) q[k].y, (float) q[k].z);
                        // the program: metres across and down it, the water through it as thick as it leaves the lip
                        if (look) vc.color(0, (int) (255 * wh), thick, (int) (255 * cv)).uv(across[k], v[i + ends[k]]);
                        else vc.color(235, 242, 246, (int) ((90 + 100 * wh) * cv)).uv(us[k], vs[k]);
                        vc.overlayCoords(OverlayTexture.NO_OVERLAY).uv2(ls[k]).normal(nm, (float) n.x, (float) n.y, (float) n.z).endVertex();
                    }
                }
            }
        }
    }

    /** Drops: small bright beads stretched along their motion, facing the camera. */
    private static void drawDrops(ClientLevel level, PoseStack pose, MultiBufferSource buffers, Vec3 cam, float partial, TextureAtlasSprite sprite) {
        if (DROPS.isEmpty()) return;
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        for (Drop d : DROPS) {
            Vec3 p = d.prev.lerp(d.pos, partial);
            Vec3 rel = p.subtract(cam);
            double dist2 = rel.lengthSqr();
            if (dist2 > DROP_RANGE * DROP_RANGE || dist2 < 0.01) continue;
            Vec3 dir = d.vel.lengthSqr() < 1e-6 ? new Vec3(0, -1, 0) : d.vel.normalize();
            Vec3 side = dir.cross(rel).normalize().scale(d.size * 0.5);
            Vec3 half = dir.scale(d.size * 0.5 + Math.min(0.08, d.vel.length() * 0.008));
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(p));
            Vec3[] q = {rel.subtract(half).subtract(side), rel.subtract(half).add(side), rel.add(half).add(side), rel.add(half).subtract(side)};
            float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
            // a bead: the normal leans out toward each corner from facing the camera
            Vec3 toEye = rel.normalize().scale(-1);
            for (int k = 0; k < 4; k++) {
                Vec3 n = toEye.add(q[k].subtract(rel).normalize().scale(0.9)).normalize();
                VertexConsumer vc = out.vertex(m, (float) q[k].x, (float) q[k].y, (float) q[k].z);
                if (look) vc = vc.color(255, d.splash ? 90 : 40, (int) (255 * d.size / 0.1), 255);
                else vc = vc.color(225, 236, 244, 170);
                vc.uv(uv[k][0], uv[k][1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, (float) n.x, (float) n.y, (float) n.z).endVertex();
            }
        }
    }

    /** Dev readout ({@code /afl_render falls status}). */
    public static String status() {
        int falls = 0;
        for (List<Fall> f : CHUNKS.values()) falls += f.size();
        return String.format(Locale.ROOT, "%s; %d falls (%d stopping), %d pours, %d bursts; %d groups; last frame %d streams, %d sheets, %d wall films, %d drops (max %d), %d wet marks, %d rings; draw %d us (%s), tick %d us",
                enabled ? "on" : "off", falls, ENDED.size(), POURS.size(), BURSTS.size(), lastGroups, lastJets, lastSheets, lastFilms, lastDrops, MAX_DROPS, WET.size(), RINGS.size(),
                lastDrawMicros, lastLook ? "AFL falling-water program" : AflShaderCompat.shaderPackInUse() ? "vanilla translucent (shader pack)" : "vanilla translucent (Fabulous)", lastTickMicros);
    }
}
