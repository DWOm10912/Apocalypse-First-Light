package com.antaurora.apofirstlight.dev.earthwork;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.earthwork.BulkMaterials;
import com.antaurora.apofirstlight.earthwork.EarthChunk;
import com.antaurora.apofirstlight.earthwork.EarthGrid;
import com.antaurora.apofirstlight.earthwork.Earthwork;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Earthwork probe (dev only; src/dev/earth-probe-client.init.gradle, Rock R2 round 2, 2026-10-22): with
 * {@code -Dafl.earthprobe=WORLD} a development client in build/earth-probe opens a copy of a save and, around the player
 * (RADIUS columns each way), writes to {@code afl_earth_probe.txt}:
 * <ul>
 *   <li>a map of the columns: edited, editable or locked, and why (Earthwork#describe); the surface heights;</li>
 *   <li>per column, how many vertices a pour there could move (Earthwork#editable over the pour cone);</li>
 *   <li>the edited columns' skins (heights, loose earth, floor, rock limit);</li>
 *   <li>then, changing the copy: one strike on every bare-rock column (Earthwork#strikable, #strike) and one 100 L pour of
 *   rubble on every column (Earthwork#pour), each result.</li>
 * </ul>
 * Then quits. No captures.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class EarthProbe {
    private static final String WORLD = System.getProperty("afl.earthprobe", "");
    private static final int RADIUS = 8, WAIT = 20 * 8;
    private static boolean started, done;
    private static int ticks = -1;

    private EarthProbe() {}

    static {
        if (!WORLD.isEmpty()) {
            Thread watchdog = new Thread(() -> {
                try { Thread.sleep(6 * 60_000L); } catch (InterruptedException e) { return; }
                ApocalypseFirstLight.LOGGER.info("[AFL EARTH PROBE] TIMEOUT: halting");
                Runtime.getRuntime().halt(3);
            }, "AFL earth probe watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
        }
    }

    @SubscribeEvent
    public static void onTitle(ScreenEvent.Init.Post event) {
        if (WORLD.isEmpty() || started || !(event.getScreen() instanceof TitleScreen screen)) return;
        started = true;
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> mc.createWorldOpenFlows().loadLevel(screen, WORLD));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (WORLD.isEmpty() || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (++ticks < WAIT) return;
        done = true;
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            String out;
            try {
                ServerLevel level = server.overworld();
                var p = server.getPlayerList().getPlayers().get(0);
                out = report(level, p.getBlockX(), p.getBlockZ(), p.getY());
            } catch (Throwable t) {
                out = "FAILED: " + t;
                ApocalypseFirstLight.LOGGER.error("[AFL EARTH PROBE]", t);
            }
            try { Files.writeString(Path.of("afl_earth_probe.txt"), out); } catch (Exception e) { ApocalypseFirstLight.LOGGER.info("[AFL EARTH PROBE] write failed: " + e); }
            ApocalypseFirstLight.LOGGER.info("[AFL EARTH PROBE]\n{}", out);
            mc.execute(mc::stop);
        });
    }

    /** Legend letter of a column's state (describe's text). */
    private static char code(String d, boolean rock) {
        if (d.equals("edited")) return rock ? 'R' : 'E';
        if (d.equals("editable")) return rock ? 'r' : 's';
        if (d.contains("two or more higher")) return 'W';
        if (d.contains("two or more lower")) return 'L';
        if (d.contains("is water")) return 'w';
        if (d.contains("neighbour") && d.contains("not natural ground")) return 'N';
        if (d.contains("pinned")) return 'P';
        if (d.contains("not open")) return 'A';
        if (d.contains("under its top")) return 'U';
        if (d.contains("not loose ground") && d.contains("its top")) return 'T';
        if (d.contains("not a solid block")) return 'S';
        if (d.contains("no natural surface") || d.contains("not Terrain Smooth's natural surface")) return 'X';
        if (d.contains("not loaded")) return '-';
        return '?';
    }

    private static String report(ServerLevel level, int px, int pz, double py) {
        StringBuilder s = new StringBuilder();
        s.append(String.format(Locale.ROOT, "world %s, player at %d %.2f %d, radius %d%n%n", WORLD, px, py, pz, RADIUS));
        int n = RADIUS * 2 + 1;
        char[][] map = new char[n][n];
        int[][] top = new int[n][n];
        String[][] top0 = new String[n][n];
        Map<String, Integer> reasons = new LinkedHashMap<>();
        StringBuilder near = new StringBuilder();
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            int x = px - RADIUS + i, z = pz - RADIUS + j;
            var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            var data = chunk == null ? null : TerrainSmooth.data(level, chunk);
            int t = data == null ? SmoothRules.NO_SURFACE : data.surface(x, z);
            EarthChunk e = Earthwork.get(level, x >> 4, z >> 4);
            int col = (x & 15) | (z & 15) << 4;
            if (e != null && e.edited(col)) t = e.top0(col);
            top[j][i] = t;
            var b = t == SmoothRules.NO_SURFACE ? null : level.getBlockState(new BlockPos(x, t, z));
            top0[j][i] = b == null ? "-" : BuiltInRegistries.BLOCK.getKey(b.getBlock()).getPath();
            String d = Earthwork.describe(level, x, z);
            boolean rock = b != null && Earthwork.rock(b.getBlock());
            map[j][i] = code(d, rock);
            String key = d.replaceAll("-?\\d+ -?\\d+", "X Z");
            reasons.merge(key, 1, Integer::sum);
            if (Math.abs(i - RADIUS) <= 4 && Math.abs(j - RADIUS) <= 4 && map[j][i] != 'E' && map[j][i] != 'R' && map[j][i] != 's' && map[j][i] != 'r')
                near.append(String.format(Locale.ROOT, "  %d %d (top y %d %s): %s%n", x, z, t, top0[j][i], d));
        }
        s.append("Column map (north up, x east; @ = player). E edited soil, R edited rock, s editable soil, r editable rock;\n")
                .append("locked: W next to a wall (2+ higher), L next to a drop (2+ lower), N next to non-natural / not open, P pinned corner,\n")
                .append("A not open above, T top not loose ground, U under top not loose, S no solid under, X no natural surface, w next to water\n");
        grid(s, map, n, px, pz);
        s.append("\nSurface (top y - player's block y; '.' none):\n");
        int feet = (int) Math.floor(py);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) s.append(top[j][i] == SmoothRules.NO_SURFACE ? "  ." : String.format(Locale.ROOT, "%3d", top[j][i] - feet));
            s.append('\n');
        }
        s.append("\nTop blocks:\n");
        Map<String, Character> letters = new LinkedHashMap<>();
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                String k = top0[j][i];
                Character c = letters.get(k);
                if (c == null) letters.put(k, c = (char) ('a' + letters.size()));
                s.append(' ').append(c);
            }
            s.append('\n');
        }
        letters.forEach((k, c) -> s.append("  ").append(c).append(" = ").append(k).append('\n'));
        s.append("\nReasons (count):\n");
        reasons.forEach((k, c) -> s.append(String.format(Locale.ROOT, "  %4d  %s%n", c, k)));
        s.append("\nLocked columns within 4 of the player:\n").append(near);

        // a pour's reach: movable vertices of the cone at each column's middle
        s.append("\nPour at each column's middle: movable vertices of the cone (r ").append(Earthwork.POUR_RADIUS).append(" m) out of all:\n");
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double cx = px - RADIUS + i + 0.5, cz = pz - RADIUS + j + 0.5;
                int all = 0, ok = 0;
                int r = (int) Math.ceil(Earthwork.POUR_RADIUS * EarthGrid.PER_BLOCK);
                int vx0 = (int) Math.round(cx * EarthGrid.PER_BLOCK), vz0 = (int) Math.round(cz * EarthGrid.PER_BLOCK);
                for (int vz = vz0 - r; vz <= vz0 + r; vz++) for (int vx = vx0 - r; vx <= vx0 + r; vx++) {
                    double dx = vx / (double) EarthGrid.PER_BLOCK - cx, dz = vz / (double) EarthGrid.PER_BLOCK - cz;
                    if (dx * dx + dz * dz > Earthwork.POUR_RADIUS * Earthwork.POUR_RADIUS) continue;
                    all++;
                    if (Earthwork.editable(level, vx, vz)) ok++;
                }
                s.append(String.format(Locale.ROOT, "%3d", ok));
            }
            s.append('\n');
        }

        // edited columns: skin and loose earth
        s.append("\nEdited columns (x z: top0, floor, rock limit, skin min..max, middle vertex h / loose / material), heights relative to the player's block y:\n");
        int[] h = new int[EarthGrid.SIDE * EarthGrid.SIDE];
        double f = feet * EarthGrid.Q;
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            int x = px - RADIUS + i, z = pz - RADIUS + j;
            EarthChunk e = Earthwork.get(level, x >> 4, z >> 4);
            int col = (x & 15) | (z & 15) << 4;
            if (e == null || !e.edited(col) || !Earthwork.heights(level, x, z, h, null)) continue;
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int v : h) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            int mvx = x * EarthGrid.PER_BLOCK + 2, mvz = z * EarthGrid.PER_BLOCK + 2;
            EarthChunk m = Earthwork.get(level, mvx >> 6, mvz >> 6);
            int k = EarthGrid.vertex(mvx & 63, mvz & 63);
            int loose = m.loose(k);
            s.append(String.format(Locale.ROOT, "  %d %d: top0 %d, floor %.2f, rock %s, skin %.2f..%.2f, middle %.2f / %.1f L / %s%n",
                    x, z, e.top0(col) - feet, (e.floor(col) - f) / EarthGrid.Q,
                    e.rock(col) == EarthGrid.NONE ? "none" : String.format(Locale.ROOT, "%.2f", (e.rock(col) - f) / EarthGrid.Q),
                    (lo - f) / EarthGrid.Q, (hi - f) / EarthGrid.Q, (m.h(k) - f) / EarthGrid.Q, loose * EarthGrid.LITRES_PER_QUANTUM,
                    loose > 0 ? BulkMaterials.table().get(m.mat(k)).id() : "-"));
        }

        // holes (user 2026-10-22, sky through pit walls): a skin cell whose block is not ground (EarthSkin draws nothing
        // there), and an unedited neighbour's full block beside one of our ground cells whose face is culled although our
        // skin edge on that side lies below that cell's top (nothing then covers the part between)
        s.append("\nHole candidates (x z side: what), heights relative to the player's block y:\n");
        int holes = 0;
        int[][] dir = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        String[] sideName = {"north", "south", "west", "east"};
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            int x = px - RADIUS + i, z = pz - RADIUS + j;
            EarthChunk e = Earthwork.get(level, x >> 4, z >> 4);
            int col = (x & 15) | (z & 15) << 4;
            if (e == null || !e.edited(col) || !Earthwork.heights(level, x, z, h, null)) continue;
            int lo = EarthGrid.min(h), hi = EarthGrid.max(h);
            for (int y = Math.floorDiv(lo, EarthGrid.Q); y <= EarthGrid.top(hi); y++) {
                BlockState own = level.getBlockState(new BlockPos(x, y, z));
                if (EarthGrid.kind(lo, hi, y) == EarthGrid.SURFACE && !Earthwork.ground(own.getBlock())) {
                    s.append(String.format(Locale.ROOT, "  %d %d cell %d: skin cell holds %s, its skin is not drawn%n", x, z, y - feet, own.getBlock()));
                    holes++;
                }
            }
            for (int d = 0; d < 4; d++) {
                int nx = x + dir[d][0], nz = z + dir[d][1];
                EarthChunk ne = Earthwork.get(level, nx >> 4, nz >> 4);
                if (ne != null && ne.edited((nx & 15) | (nz & 15) << 4)) continue;   // shared vertices: no step
                int edgeLo = Integer.MAX_VALUE, edgeHi = Integer.MIN_VALUE;
                for (int k = 0; k <= EarthGrid.PER_BLOCK; k++) {
                    int ei = d == 2 ? 0 : d == 3 ? EarthGrid.PER_BLOCK : k, ej = d == 0 ? 0 : d == 1 ? EarthGrid.PER_BLOCK : k;
                    int v = h[EarthGrid.at(ei, ej)];
                    edgeLo = Math.min(edgeLo, v);
                    edgeHi = Math.max(edgeHi, v);
                }
                for (int y = Math.floorDiv(lo, EarthGrid.Q); y <= EarthGrid.top(hi); y++) {
                    BlockPos np = new BlockPos(nx, y, nz);
                    BlockState nb = level.getBlockState(np), own = level.getBlockState(new BlockPos(x, y, z));
                    if (!nb.canOcclude() || !own.canOcclude() || edgeLo >= (y + 1) * EarthGrid.Q) continue;
                    s.append(String.format(Locale.ROOT, "  %d %d %s: neighbour %s at %d culled by our %s, our edge %.2f..%.2f%n",
                            x, z, sideName[d], nb.getBlock(), y - feet, own.getBlock(), (edgeLo - f) / EarthGrid.Q, (edgeHi - f) / EarthGrid.Q));
                    holes++;
                }
            }
        }
        s.append("  ").append(holes).append(" in all\n");

        // changing the copy: strikes on bare rock, then pours of rubble
        s.append("\nStrike at each bare-rock column's middle (strikable, then cut L / vertices refused):\n");
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            int x = px - RADIUS + i, z = pz - RADIUS + j;
            if (top[j][i] == SmoothRules.NO_SURFACE || !Earthwork.rock(level.getBlockState(new BlockPos(x, top[j][i], z)).getBlock())) continue;
            String why = Earthwork.strikable(level, x + 0.5, z + 0.5);
            if (why != null) {
                s.append(String.format(Locale.ROOT, "  %d %d: %s%n", x, z, why));
                continue;
            }
            var r = Earthwork.strike(level, x + 0.5, z + 0.5);
            s.append(String.format(Locale.ROOT, "  %d %d: %.1f L, refused %d%n", x, z, -r.moved() * EarthGrid.LITRES_PER_QUANTUM, r.refused()));
        }
        int rubble = BulkMaterials.table().index("rubble"), amount = (int) Math.round(100 / EarthGrid.LITRES_PER_QUANTUM);
        s.append("\nPour 100 L of rubble at each column's middle (L put down; '  .' none), in order west to east, north to south:\n");
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int x = px - RADIUS + i, z = pz - RADIUS + j;
                if (top[j][i] == SmoothRules.NO_SURFACE) { s.append("    ."); continue; }
                double y = Earthwork.surfaceAt(level, x + 0.5, z + 0.5);
                if (Double.isNaN(y)) y = top[j][i] + 1;
                var r = Earthwork.pour(level, x + 0.5, y, z + 0.5, amount, rubble);
                long put = 0;
                for (long q : r.taken().values()) if (q < 0) put -= q;
                s.append(String.format(Locale.ROOT, "%5.0f", put * EarthGrid.LITRES_PER_QUANTUM));
            }
            s.append('\n');
        }
        s.append("\nSnapshot mismatches (a vertex two columns put at different heights): ").append(Earthwork.mismatches).append('\n');
        s.append("\nAfter: ");
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++)
            map[j][i] = code(Earthwork.describe(level, px - RADIUS + i, pz - RADIUS + j), map[j][i] == 'r' || map[j][i] == 'R');
        s.append("column map\n");
        grid(s, map, n, px, pz);
        return s.toString();
    }

    private static void grid(StringBuilder s, char[][] map, int n, int px, int pz) {
        s.append(String.format(Locale.ROOT, "  x %d .. %d, z %d .. %d%n", px - RADIUS, px + RADIUS, pz - RADIUS, pz + RADIUS));
        for (int j = 0; j < n; j++) {
            s.append("  ");
            for (int i = 0; i < n; i++) s.append(' ').append(i == RADIUS && j == RADIUS ? '@' : map[j][i]);
            s.append('\n');
        }
    }
}
