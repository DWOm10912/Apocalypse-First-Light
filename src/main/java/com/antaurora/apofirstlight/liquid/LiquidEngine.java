package com.antaurora.apofirstlight.liquid;

import com.antaurora.apofirstlight.liquid.ground.GroundViewV0;
import net.minecraft.core.Direction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.antaurora.apofirstlight.liquid.LiquidUnits.*;

/**
 * The volume liquid of one level (docs/gameplay/water_v1_design.md 5): package 1, the core without the game. It reads
 * the ground only through GroundViewV0 and keeps everything else itself.
 * <ul>
 *   <li><b>Cells</b> (LiquidCell): the layers of 1 m columns, whole millilitres each, made on demand: a column exists when
 *   it holds liquid, gets rain or borders one that does.</li>
 *   <li><b>Flow</b>: the pipe model (O'Brien and Hodgins 1995, Mei et al. 2007) on the faces: each face's flow gains
 *   g x its open cross-section below the higher side x the level difference, less friction (Manning n = 0.03 over the
 *   flow's depth, plus a light damping). No cell gives more than it holds, no capped layer takes more than its room. Each
 *   step moves whole millilitres, the same number off one side and onto the other; the fraction waits on the face. So the
 *   total is exact to the millilitre, always. Substeps keep a wave within half a cell a step.</li>
 *   <li><b>Basins</b> (LiquidBasin): cells quiet for 2 s sleep. Sleeping cells are not stepped; rain on them is spread to
 *   one level every half second; a perimeter face open to a level more than {@link #WAKE} away wakes them all: that is
 *   the overflow at the outlet (5.5).</li>
 *   <li><b>Boundaries</b>: a cell can be held at a fixed level (a natural river, lake or the sea in V1, 5.7): it gives and
 *   takes without limit; the ledger counts what crossed.</li>
 *   <li><b>Geometry changes</b> (GroundEvents): the columns are rebuilt and their liquid laid into the new layers by the
 *   old water's heights, to the millilitre; what no longer has room goes to the neighbours, or limbo (5.3).</li>
 *   <li><b>Chunks</b>: every cell belongs to its column's chunk, every chunk keeps a ledger. A chunk not loaded is a wall
 *   (GroundView says UNKNOWN). save keeps the flow on the faces, so a save and load mid-flow changes nothing; unload stops
 *   the faces across its border first (5.10).</li>
 * </ul>
 * Not here yet: catchment runoff, infiltration and evaporation (package 3), the game's storage, sync and hooks (packages 2
 * and 4). Single threaded; deterministic: cells and faces always go in sorted order.
 */
public final class LiquidEngine {
    public static final double DT = 0.05, G = 9.81, MANNING = 0.03, DAMPING = 0.5;
    /** A level difference across an open perimeter face that wakes a basin, m. */
    public static final double WAKE = 0.002;
    /**
     * Quiet: the level moved less than LEVEL_QUIET m in a tick and no face carries 5 mL a tick (FLOW_QUIET m3/s) faster
     * than U_QUIET m/s. Moving whole millilitres leaves still water rocking by some tens of mL a cell, for ever: up to
     * 6e-5 m3/s through a shallow face, 1.7e-4 m3/s (0.1 mm/s) through a 1.5 m deep one (offline check); below these it is
     * still. What still drains slower than this is left to the wake check (WAKE), so a pool stops about 2 mm above its
     * outlet. Or the cell gained or lost no more than ROCK_ML in the tick: in a sliver of a column (a pond's edge over a
     * smoothed slope, 1-2 % of a metre open) one millilitre moves the level more than LEVEL_QUIET, so that rocking alone
     * kept a whole pond awake for ever (offline perf, river plan step 1, 2026-10-26).
     */
    static final double LEVEL_QUIET = 5e-5, FLOW_QUIET = 1e-4, U_QUIET = 1e-3;
    static final long ROCK_ML = 5;
    static final int QUIET_STEPS = 40, SLEEP_EVERY = 20, LEVEL_EVERY = 40;
    /** 2: the account's fromSurface (package 3a); version 1 saves still read. */
    private static final int SAVE_VERSION = 2;

    public interface RainField {
        /** Rain at column (x, z), mm per game hour. */
        double mmPerHour(int x, int z);

        int minX();
        int minZ();
        int maxX();
        int maxZ();
    }

    private final GroundViewV0 ground;
    private final int y0, y1;
    private final short[] areaBuf;
    private final byte[] edgeBuf;
    /** Columns in key order (every walk over them is deterministic), and the same by key for lookups. */
    private final TreeMap<Long, LiquidColumn> columns = new TreeMap<>();
    /**
     * Columns by position. Not a HashMap of Long: Long's hash of x << 32 | z is x ^ z, so a 96 x 96 m area falls into 128
     * buckets and every lookup walks a tree (offline perf, river plan step 1); fastutil mixes the bits.
     */
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<LiquidColumn> index = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private final TreeMap<Long, LiquidLedger> ledgers = new TreeMap<>();
    private final List<LiquidCell> cells = new ArrayList<>();
    private final List<LiquidFace> faces = new ArrayList<>();
    private final List<LiquidBasin> basins = new ArrayList<>();
    private final List<LiquidCell> wetted = new ArrayList<>();
    private boolean dirty, basinsDirty;

    // ---- falling liquid (docs/gameplay/water_falling_v1_design.md 3) --------------------------------------------------

    /**
     * Liquid goes over an edge and falls when the surface it goes to stands this far below the opening (m), and the drawn
     * ground is no slope there (GroundViewV0#ramp). 0.5 m, not 0.1 (falling water 15, in game 2026-10-25): films ran down
     * the steps of smoothed slopes' collision as thin streams that were not there; a lower step is a chute it runs down.
     */
    public static final double FALL_MIN = 0.5;
    /** Flights land at most this many ticks ahead (a ring of lists; 12.8 s, a fall of some 800 m). */
    private static final int FLIGHT_TICKS = 256;

    /** Liquid in the air: it left one cell and lands in another when its fall is done. */
    private record Flight(LiquidCell into, long ml) {
    }

    @SuppressWarnings("unchecked")
    private final List<Flight>[] flights = new List[FLIGHT_TICKS];
    private final Map<LiquidFace, LiquidFall> falls = new HashMap<>();
    /** Chunks whose falls started, stopped or changed their flow by a fifth since the last takeFallsChanged. */
    private final java.util.TreeSet<Long> fallsChanged = new java.util.TreeSet<>();
    private long tick;
    private RainField rain;
    private int substeps;
    /** Films the floor holds and soaks up (LiquidSoak); off in the offline scenes that test the flow alone. */
    private boolean surfaceStorage = true;

    /** An engine over blocks y0 (inclusive) to y1 (exclusive) of every column. */
    public LiquidEngine(GroundViewV0 ground, int y0, int y1) {
        this.ground = ground;
        this.y0 = y0;
        this.y1 = y1;
        areaBuf = new short[(y1 - y0) * SLABS];
        edgeBuf = new byte[(y1 - y0) * SLABS];
    }

    // ---- chunks -------------------------------------------------------------------------------------------------

    /** Load chunk (chunkX, chunkZ): from its save, or empty when {@code data} is null. */
    public void loadChunk(int chunkX, int chunkZ, byte[] data) {
        long ck = LiquidColumn.chunkKey(chunkX, chunkZ);
        if (ledgers.containsKey(ck)) throw new IllegalStateException("chunk already loaded " + chunkX + " " + chunkZ);
        if (data == null) ledgers.put(ck, new LiquidLedger());
        else try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int version = in.readInt();
            if (version < 1 || version > SAVE_VERSION) throw new IOException("liquid save version " + version);
            ledgers.put(ck, LiquidLedger.read(in, version));
            tick = Math.max(tick, in.readLong());
            int n = in.readInt();
            List<Object[]> moved = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int x = (chunkX << 4) + in.readByte(), z = (chunkZ << 4) + in.readByte();
                LiquidColumn col = LiquidColumn.build(ground, x, z, y0, y1, areaBuf);
                double rainCarry = in.readDouble();
                int cellCount = in.readInt();
                List<long[]> lost = new ArrayList<>();
                for (int k = 0; k < cellCount; k++) {
                    int floor = in.readInt();
                    in.readInt();
                    String id = in.readUTF();
                    long volume = in.readLong();
                    double level = in.readDouble();
                    int quiet = in.readInt();
                    boolean sleeping = in.readBoolean();
                    LiquidCell c = col == null ? null : cellWithFloor(col, floor);
                    Liquid liquid = Liquids.byId(id);
                    if (liquid == null) liquid = Liquids.WATER;
                    if (c != null) {
                        c.volume = volume;
                        c.liquid = volume > 0 ? liquid : null;
                        c.quiet = quiet;
                        c.sleeping = sleeping && volume > 0;
                        c.refreshLevel();
                        c.lastLevel = c.level;
                        c.lastVolume = c.volume;
                        if (volume > 0) wetted.add(c);
                    } else if (volume > 0) {
                        lost.add(new long[]{volume, Double.doubleToLongBits(level)});
                    }
                }
                int faceCount = in.readInt();
                for (int k = 0; k < faceCount; k++) {
                    boolean south = in.readBoolean();
                    int aFloor = in.readInt(), bFloor = in.readInt();
                    double q = in.readDouble(), carry = in.readDouble();
                    if (col != null) col.pendingFaces.put(LiquidFace.key(south, aFloor, bFloor), new double[]{q, carry});
                }
                if (col == null) {
                    for (long[] l : lost) ledgers.get(ck).limbo += l[0];
                    continue;
                }
                col.rainCarry = rainCarry;
                LiquidColumn there = removeColumn(col.key);
                if (there != null) disconnect(there);
                putColumn(col);
                connect(col);
                for (long[] l : lost) moved.add(new Object[]{col, l});
            }
            // the ground changed while the chunk was away: that liquid goes to the layer at its old level
            for (Object[] m : moved) {
                long[] l = (long[]) m[1];
                LiquidCell c = nearest((LiquidColumn) m[0], Double.longBitsToDouble(l[1]));
                if (c == null) ledgers.get(ck).limbo += l[0];
                else addTo(c, l[0]);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (data != null) ledgers.get(ck).changed = true;
        dirty = basinsDirty = true;
        activateWetted();
        // wet columns already here along the chunk's edges reach into it now
        int x0 = chunkX << 4, z0 = chunkZ << 4;
        for (int k = 0; k < 16; k++) {
            if (wet(x0 - 1, z0 + k)) column(x0, z0 + k);
            if (wet(x0 + 16, z0 + k)) column(x0 + 15, z0 + k);
            if (wet(x0 + k, z0 - 1)) column(x0 + k, z0);
            if (wet(x0 + k, z0 + 16)) column(x0 + k, z0 + 15);
        }
    }

    private boolean wet(int x, int z) {
        LiquidColumn col = index.get(LiquidColumn.key(x, z));
        if (col == null) return false;
        for (LiquidCell c : col.cells) if (c.volume > 0 || c.boundary) return true;
        return false;
    }

    /** The chunk's liquid as a save, the chunk staying loaded (an autosave). */
    public byte[] saveChunk(int chunkX, int chunkZ) {
        return save(chunkX, chunkZ);
    }

    /** Unload: the faces across the chunk's border stop (they are walls now), then it saves and goes. */
    public byte[] unloadChunk(int chunkX, int chunkZ) {
        long ck = LiquidColumn.chunkKey(chunkX, chunkZ);
        for (LiquidFace f : faces) {
            if (f.crossChunk && (f.a.column.chunk() == ck || f.b.column.chunk() == ck)) {
                f.q = 0;
                f.carry = 0;
            }
        }
        byte[] data = save(chunkX, chunkZ);
        List<LiquidColumn> gone = new ArrayList<>();
        for (LiquidColumn col : columns.values()) if (col.chunk() == ck) gone.add(col);
        for (LiquidColumn col : gone) {
            disconnect(col);
            removeColumn(col.key);
        }
        ledgers.remove(ck);
        dirty = basinsDirty = true;
        return data;
    }

    private byte[] save(int chunkX, int chunkZ) {
        long ck = LiquidColumn.chunkKey(chunkX, chunkZ);
        LiquidLedger ledger = ledgers.get(ck);
        if (ledger == null) throw new IllegalStateException("chunk not loaded " + chunkX + " " + chunkZ);
        // liquid in the air is not saved: it lands now (at most a few seconds early)
        landAll(ck);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(SAVE_VERSION);
            ledger.write(out);
            out.writeLong(tick);
            List<LiquidColumn> keep = new ArrayList<>();
            for (LiquidColumn col : columns.values()) if (col.chunk() == ck && worthSaving(col)) keep.add(col);
            out.writeInt(keep.size());
            for (LiquidColumn col : keep) {
                out.writeByte(col.x & 15);
                out.writeByte(col.z & 15);
                out.writeDouble(col.rainCarry);
                int wet = 0;
                for (LiquidCell c : col.cells) if (c.volume > 0) wet++;
                out.writeInt(wet);
                for (LiquidCell c : col.cells) {
                    if (c.volume <= 0) continue;
                    out.writeInt(c.floorSlab);
                    out.writeInt(c.ceilSlab);
                    out.writeUTF(c.liquid == null ? Liquids.WATER.id() : c.liquid.id());
                    out.writeLong(c.volume);
                    out.writeDouble(c.level);
                    out.writeInt(c.quiet);
                    out.writeBoolean(c.sleeping);
                }
                List<LiquidFace> owned = new ArrayList<>();
                for (LiquidCell c : col.cells)
                    for (LiquidFace f : c.faces) if (f.a == c && (f.q != 0 || f.carry != 0)) owned.add(f);
                // faces whose other side is away keep what they had pending
                out.writeInt(owned.size() + col.pendingFaces.size());
                for (LiquidFace f : owned) {
                    out.writeBoolean(f.south);
                    out.writeInt(f.a.floorSlab);
                    out.writeInt(f.b.floorSlab);
                    out.writeDouble(f.q);
                    out.writeDouble(f.carry);
                }
                for (Map.Entry<Long, double[]> e : new TreeMap<>(col.pendingFaces).entrySet()) {
                    long key = e.getKey();
                    out.writeBoolean((key >>> 62) == 1);
                    out.writeInt((int) ((key >>> 31) & 0x7fffffffL) - (1 << 24));
                    out.writeInt((int) (key & 0x7fffffffL) - (1 << 24));
                    out.writeDouble(e.getValue()[0]);
                    out.writeDouble(e.getValue()[1]);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static boolean worthSaving(LiquidColumn col) {
        if (col.rainCarry != 0 || !col.pendingFaces.isEmpty()) return true;
        for (LiquidCell c : col.cells) {
            if (c.volume > 0) return true;
            for (LiquidFace f : c.faces) if (f.a == c && (f.q != 0 || f.carry != 0)) return true;
        }
        return false;
    }

    // ---- columns and faces ----------------------------------------------------------------------------------------

    private void putColumn(LiquidColumn col) {
        col.ledger = ledgers.get(col.chunk());
        columns.put(col.key, col);
        index.put(col.key, col);
    }

    private LiquidColumn removeColumn(long key) {
        index.remove(key);
        return columns.remove(key);
    }

    /** The column at (x, z), made when its chunk is loaded here and the ground knows it; else null. */
    public LiquidColumn column(int x, int z) {
        LiquidColumn col = index.get(LiquidColumn.key(x, z));
        if (col != null) return col;
        if (!ledgers.containsKey(LiquidColumn.chunkKey(x >> 4, z >> 4))) return null;
        col = LiquidColumn.build(ground, x, z, y0, y1, areaBuf);
        if (col == null) return null;
        putColumn(col);
        connect(col);
        dirty = true;
        return col;
    }

    private void connect(LiquidColumn col) {
        LiquidColumn w = index.get(LiquidColumn.key(col.x - 1, col.z)), e = index.get(LiquidColumn.key(col.x + 1, col.z));
        LiquidColumn n = index.get(LiquidColumn.key(col.x, col.z - 1)), s = index.get(LiquidColumn.key(col.x, col.z + 1));
        if (w != null) buildFaces(w, col, false);
        if (e != null) buildFaces(col, e, false);
        if (n != null) buildFaces(n, col, true);
        if (s != null) buildFaces(col, s, true);
    }

    private void buildFaces(LiquidColumn a, LiquidColumn b, boolean south) {
        if (a.cells.isEmpty() || b.cells.isEmpty()) return;
        // only the blocks the two columns' layers span
        int bottom = Integer.MAX_VALUE, top = Integer.MIN_VALUE;
        for (LiquidCell c : a.cells) {
            bottom = Math.min(bottom, c.floorSlab);
            top = Math.max(top, c.ceilSlab);
        }
        for (LiquidCell c : b.cells) {
            bottom = Math.min(bottom, c.floorSlab);
            top = Math.max(top, c.ceilSlab);
        }
        int from = Math.floorDiv(bottom, SLABS), to = Math.min(y1, Math.floorDiv(top + SLABS - 1, SLABS));
        ground.openEdge(a.x, a.z, south ? Direction.SOUTH : Direction.EAST, from, to, edgeBuf);
        if (edgeBuf[0] == GroundViewV0.UNKNOWN) return;
        int base = from * SLABS;
        basinsDirty = true;
        for (LiquidCell ca : a.cells) {
            for (LiquidCell cb : b.cells) {
                int lo = Math.max(ca.floorSlab, cb.floorSlab), hi = Math.min(ca.ceilSlab, cb.ceilSlab);
                if (lo >= hi) continue;
                int[] width = new int[hi - lo];
                boolean open = false;
                for (int k = 0; k < width.length; k++) {
                    width[k] = Math.max(0, edgeBuf[lo - base + k]);
                    open |= width[k] > 0;
                }
                if (!open) continue;
                LiquidFace f = new LiquidFace(ca, cb, south, lo, width);
                double[] pending = a.pendingFaces.remove(f.key());
                if (pending != null) {
                    f.q = pending[0];
                    f.carry = pending[1];
                }
                ca.faces.add(f);
                cb.faces.add(f);
                faces.add(f);
                if (f.q != 0 || f.carry != 0) freshFlow.add(f);
            }
        }
        dirty = true;
    }

    private void disconnect(LiquidColumn col) {
        for (LiquidCell c : col.cells) {
            for (LiquidFace f : c.faces) {
                f.removed = true;
                f.other(c).faces.remove(f);
            }
            c.faces.clear();
        }
        faces.removeIf(f -> f.removed);
        dirty = true;
    }

    private void rebuild() {
        cells.clear();
        for (LiquidColumn col : columns.values()) cells.addAll(col.cells);
        faces.sort(FACE_ORDER);
        for (LiquidCell c : cells) c.owned.clear();
        for (LiquidFace f : faces) f.a.owned.add(f);
        dirty = false;
    }

    private static final Comparator<LiquidFace> FACE_ORDER = Comparator
            .comparingLong((LiquidFace f) -> f.a.column.key)
            .thenComparingInt(f -> f.a.floorSlab)
            .thenComparing(f -> f.south)
            .thenComparingInt(f -> f.b.floorSlab);

    private static LiquidCell cellWithFloor(LiquidColumn col, int floor) {
        for (LiquidCell c : col.cells) if (c.floorSlab == floor) return c;
        return null;
    }

    /** The layer holding m, or the closest one. */
    private static LiquidCell nearest(LiquidColumn col, double m) {
        LiquidCell best = null;
        double bestD = Double.MAX_VALUE;
        for (LiquidCell c : col.cells) {
            double d = c.contains(m) ? 0 : Math.min(Math.abs(m - c.floor()), Math.abs(m - c.ceiling()));
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    // ---- inputs -----------------------------------------------------------------------------------------------------

    /** Pour ml into the layer at (x, y, z) (dev fill, test); returns false when there is no room there. */
    public boolean add(int x, double y, int z, long ml) {
        LiquidColumn col = column(x, z);
        LiquidCell c = col == null ? null : col.cellAt(y);
        if (c == null || c.boundary) return false;
        addTo(c, ml);
        ledger(c).added += ml;
        return true;
    }

    /** Cells a fill spreads over at most (a pool of about 64 x 64 m). */
    static final int FILL_MAX = 4096;

    /**
     * Pours ml into the water at (x, y, z) at once, as if it had been filled slowly: the pool there rises evenly over the
     * cells its new surface reaches through openings below it (dry ones too; at most {@link #FILL_MAX}), so a dev fill
     * does not stand up as a tower of water in one column (docs/gameplay/water_falling_v1_design.md 12). Returns how many
     * cells took some; 0 when there is no open layer there or it is held at a fixed level; -1 (nothing poured) when the
     * water would spread over {@link #FILL_MAX} cells or more: open ground, not a pool (falling water 15).
     */
    public int fill(int x, double y, int z, long ml) {
        LiquidColumn col = column(x, z);
        LiquidCell start = col == null ? null : col.cellAt(y);
        if (start == null || start.boundary || ml <= 0) return 0;
        double lo = start.level, hi = start.level + (double) ml / ML_PER_M3 + 1;
        for (int it = 0; it < 40; it++) {
            double mid = (lo + hi) / 2;
            if (want(reach(start, mid), mid) < ml) lo = mid;
            else hi = mid;
        }
        List<LiquidCell> region = reach(start, hi);
        // reaching the limit, it is no pool but open ground running on downhill (a slope): nothing poured
        if (region.size() >= FILL_MAX) return -1;
        long[] give = new long[region.size()];
        double total = want(region, hi);
        long sum = 0;
        for (int i = 0; i < give.length; i++) {
            LiquidCell c = region.get(i);
            double w = Math.max(0, c.volumeBelow(hi) - c.volume);
            give[i] = total <= 0 ? 0 : (long) Math.floor(w * ml / total);
            sum += give[i];
        }
        // what rounding left goes to the start, then the cells in order
        give[0] += ml - sum;
        int n = 0;
        for (int i = 0; i < give.length; i++) {
            if (give[i] <= 0) continue;
            LiquidCell c = region.get(i);
            addTo(c, give[i]);
            ledger(c).added += give[i];
            n++;
        }
        basinsDirty = true;
        return n;
    }

    /** The cells water standing at m around start reaches (start first), through openings below m; dry columns are built. */
    private List<LiquidCell> reach(LiquidCell start, double m) {
        List<LiquidCell> out = new ArrayList<>();
        java.util.Set<LiquidCell> seen = new java.util.HashSet<>();
        out.add(start);
        seen.add(start);
        for (int i = 0; i < out.size() && out.size() < FILL_MAX; i++) {
            LiquidCell c = out.get(i);
            column(c.column.x - 1, c.column.z);
            column(c.column.x + 1, c.column.z);
            column(c.column.x, c.column.z - 1);
            column(c.column.x, c.column.z + 1);
            for (LiquidFace f : c.faces) {
                LiquidCell o = f.other(c);
                if (o.boundary || o.floor() >= m || f.crossSection(m) <= 0 || !seen.add(o)) continue;
                out.add(o);
                if (out.size() >= FILL_MAX) break;
            }
        }
        return out;
    }

    /** mL the cells need to stand at m. */
    private static double want(List<LiquidCell> cells, double m) {
        double w = 0;
        for (LiquidCell c : cells) w += Math.max(0, c.volumeBelow(m) - c.volume);
        return w;
    }

    /** Take up to ml out of the layer at (x, y, z); returns what was taken. */
    public long remove(int x, double y, int z, long ml) {
        LiquidColumn col = column(x, z);
        LiquidCell c = col == null ? null : col.cellAt(y);
        if (c == null || c.boundary) return 0;
        long taken = Math.min(ml, c.volume);
        c.volume -= taken;
        if (c.volume == 0) c.liquid = null;
        ledger(c).removed += taken;
        touch(c);
        return taken;
    }

    /** Hold the layer at (x, y, z) at a fixed level (a natural water body, V1: 5.7). */
    public boolean setBoundary(int x, double y, int z, double level) {
        LiquidColumn col = column(x, z);
        LiquidCell c = col == null ? null : col.cellAt(y);
        if (c == null) return false;
        if (c.volume > 0) {
            ledger(c).boundaryOut += c.volume;
            c.volume = 0;
        }
        c.boundary = true;
        c.liquid = Liquids.WATER;
        c.level = level;
        c.sleeping = false;
        c.column.ledger.changed = true;
        basinsDirty = true;
        wetted.add(c);
        activateWetted();
        return true;
    }

    public void setSurfaceStorage(boolean on) {
        surfaceStorage = on;
    }

    /** What a cell can give to the flow: all of it, less the film its floor holds. */
    private long available(LiquidCell c) {
        return surfaceStorage ? Math.max(0, c.volume - c.storage) : c.volume;
    }

    /**
     * Films (no more than the floor holds) soak in and dry, every {@code ticks} ticks (LiquidSoak): written as
     * infiltration and evaporation in the cell's account. Water deeper than that keeps its water for now.
     */
    private void soak(int ticks) {
        for (LiquidCell c : cells) {
            if (c.boundary || c.volume <= 0 || c.volume > c.storage) continue;
            double rate = c.soakPerTick + c.dryPerTick;
            if (rate <= 0) continue;
            c.soakCarry += rate * ticks;
            long ml = Math.min(c.volume, (long) c.soakCarry);
            if (ml <= 0) continue;
            c.soakCarry -= ml;
            long in = Math.min(ml, Math.round(ml * c.soakPerTick / rate));
            LiquidLedger l = ledger(c);
            l.infiltration += in;
            l.evaporation += ml - in;
            l.changed = true;
            c.volume -= ml;
            if (c.volume == 0) {
                c.liquid = null;
                c.soakCarry = 0;
                // a dry cell is not part of a basin (a basin of one dry cell per film cost the wake check its perimeter)
                c.sleeping = false;
                basinsDirty = true;
            }
            c.refreshLevel();
            // soaking in is not the water moving: a film fed by a trickle and soaking every 20 ticks never got quiet
            c.lastLevel = c.level;
            c.lastVolume = c.volume;
        }
    }

    public void setRain(RainField rain) {
        this.rain = rain;
    }

    public void setTick(long tick) {
        this.tick = tick;
    }

    private void addTo(LiquidCell c, long ml) {
        if (ml <= 0) return;
        boolean dry = c.volume == 0;
        c.volume += ml;
        if (c.liquid == null) c.liquid = Liquids.WATER;
        if (dry) wetted.add(c);
        touch(c);
        activateWetted();
    }

    /** Something changed in c from outside the flow: its level, and its basin wakes. */
    private void touch(LiquidCell c) {
        c.column.ledger.changed = true;
        c.refreshLevel();
        c.quiet = 0;
        if (c.sleeping) wake(c.basin);
    }

    private static LiquidLedger ledger(LiquidCell c) {
        return c.column.ledger;
    }

    // ---- the step ---------------------------------------------------------------------------------------------------

    /** Nanoseconds spent so far: rain and levelling, wake check, flow, quiet and sleep (dev readout). */
    final long[] spent = new long[4];

    public void tick() {
        long t0 = System.nanoTime();
        tick++;
        if (dirty) rebuild();
        if (basinsDirty) rebuildBasins();
        land();
        depositRain();
        if (tick % LEVEL_EVERY == 0) levelBasins();
        long t1 = System.nanoTime();
        wakeCheck();
        if (basinsDirty) rebuildBasins();
        if (dirty) rebuild();
        long t2 = System.nanoTime();
        flow();
        long t3 = System.nanoTime();
        spent[0] += t1 - t0;
        spent[1] += t2 - t1;
        spent[2] += t3 - t2;
        tickQuiet();
        if (tick % 20 == 0) measureFalls();
        if (surfaceStorage && tick % 20 == 0) soak(20);
        spent[3] += System.nanoTime() - t3;
    }

    private void tickQuiet() {
        // only what the flow looked at: dry cells away from all water have nothing to settle
        for (LiquidCell c : liveCells) {
            c.refreshLevel();
            boolean flowing = false;
            for (LiquidFace f : c.faces) {
                double q = Math.abs(f.q);
                if (q > FLOW_QUIET) flowing |= q > U_QUIET * f.crossSection(Math.max(f.a.level, f.b.level));
            }
            boolean still = Math.abs(c.level - c.lastLevel) < LEVEL_QUIET || Math.abs(c.volume - c.lastVolume) <= ROCK_ML;
            c.quiet = still && !flowing ? c.quiet + 1 : 0;
            c.lastLevel = c.level;
            c.lastVolume = c.volume;
        }
        activateWetted();
        if (tick % SLEEP_EVERY == 0) sleepCheck();
    }

    private void depositRain() {
        if (rain == null) return;
        for (int x = rain.minX(); x <= rain.maxX(); x++) {
            for (int z = rain.minZ(); z <= rain.maxZ(); z++) {
                double mm = rain.mmPerHour(x, z);
                if (mm <= 0) continue;
                LiquidColumn col = column(x, z);
                if (col == null || col.cells.isEmpty()) continue;
                LiquidCell top = col.cells.get(col.cells.size() - 1);
                if (!top.openTop || top.boundary) continue;
                col.rainCarry += mm * ML_PER_TICK_PER_MM_H;
                long ml = (long) col.rainCarry;
                if (ml <= 0) continue;
                col.rainCarry -= ml;
                rainInto(top, ml);
                ledger(top).rain += ml;
            }
        }
        activateWetted();
        if (dirty) rebuild();
    }

    /** Rain into a cell open to the sky: its basin, if it sleeps, spreads it at the next levelling. */
    private void rainInto(LiquidCell top, long ml) {
        boolean dry = top.volume == 0;
        top.volume += ml;
        top.refreshLevel();
        top.column.ledger.changed = true;
        if (top.liquid == null) top.liquid = Liquids.WATER;
        if (dry) wetted.add(top);
        if (!top.sleeping) top.quiet = 0;
        else if (top.basin != null) top.basin.needsLevel = true;
        else basinsDirty = true;
    }

    /**
     * Natural rain (docs/gameplay/water_rain_v1_design.md 4.1): whether water stands open to the sky at (x, z), so the
     * rain there goes into it rather than onto the ground (SurfaceWater, LiquidRain).
     */
    public boolean surfaceWet(int x, int z) {
        LiquidColumn col = index.get(LiquidColumn.key(x, z));
        if (col == null || col.cells.isEmpty()) return false;
        LiquidCell top = col.cells.get(col.cells.size() - 1);
        // a film the floor holds is the ground's, not standing water: rain on it goes to the surface account, so the film
        // soaks in and sleeps rather than being kept awake by every drop (falling water 15)
        return top.openTop && !top.boundary && top.volume > (surfaceStorage ? top.storage : 0);
    }

    /**
     * Hands ml of natural rain (and the surface water the column held) from the chunk's SurfaceWater to the water standing
     * at (x, z), written as the account's fromSurface; false (nothing moved) when no water stands open there.
     */
    public boolean fromSurface(int x, int z, long ml) {
        if (ml <= 0 || !surfaceWet(x, z)) return false;
        LiquidColumn col = index.get(LiquidColumn.key(x, z));
        LiquidCell top = col.cells.get(col.cells.size() - 1);
        rainInto(top, ml);
        ledger(top).fromSurface += ml;
        activateWetted();
        return true;
    }

    /** Columns next to newly wet cells, so the liquid can go there. */
    private void activateWetted() {
        while (!wetted.isEmpty()) {
            LiquidCell c = wetted.remove(wetted.size() - 1);
            column(c.column.x - 1, c.column.z);
            column(c.column.x + 1, c.column.z);
            column(c.column.x, c.column.z - 1);
            column(c.column.x, c.column.z + 1);
        }
    }

    private static double head(LiquidCell c) {
        return c.level;
    }

    private static boolean takesPart(LiquidCell c) {
        return c.boundary || (!c.sleeping);
    }

    /** This tick's faces that can carry liquid and its awake cells: sleeping water costs nothing in the step. */
    private final List<LiquidFace> live = new ArrayList<>();
    private final List<LiquidCell> liveCells = new ArrayList<>();
    /** Last tick's live faces, and faces built since with a saved flow: cleared if the flow does not look at them. */
    private final List<LiquidFace> lastLive = new ArrayList<>(), freshFlow = new ArrayList<>();

    private void flow() {
        live.clear();
        liveCells.clear();
        // only cells with water (or a boundary) taking part and their neighbours: a face between two dry cells carries
        // nothing, and a film that wetted a hillside once leaves thousands of dry columns loaded (offline perf, river plan
        // step 1). The faces in the engine's order still: each cell's own faces, cell by cell.
        for (LiquidCell c : cells) {
            if (!(c.volume > 0 || c.boundary) || !takesPart(c)) continue;
            c.near = tick;
            for (LiquidFace f : c.faces) f.other(c).near = tick;
        }
        double maxDepth = 0;
        for (LiquidCell c : cells) {
            if (c.near != tick) continue;
            if (c.awake()) {
                liveCells.add(c);
                if (c.volume > 0) maxDepth = Math.max(maxDepth, c.level - c.floor());
            }
            for (LiquidFace f : c.owned) {
                f.seen = tick;
                LiquidCell a = f.a, b = f.b;
                boolean active = takesPart(a) && takesPart(b) && !(a.boundary && b.boundary)
                        && (a.volume > 0 || b.volume > 0 || a.boundary || b.boundary)
                        && (a.liquid == null || b.liquid == null || a.liquid == b.liquid);
                if (active) live.add(f);
                else {
                    f.q = 0;
                    f.carry = 0;
                }
            }
        }
        // a face not looked at carries nothing: last tick's live ones that went dry, and new ones given a saved flow
        for (LiquidFace f : lastLive) {
            if (f.seen == tick) continue;
            f.q = 0;
            f.carry = 0;
        }
        for (LiquidFace f : freshFlow) {
            if (f.seen == tick) continue;
            f.q = 0;
            f.carry = 0;
        }
        freshFlow.clear();
        lastLive.clear();
        lastLive.addAll(live);
        substeps = Math.max(1, (int) Math.ceil(DT * Math.sqrt(G * maxDepth) / 0.5));
        if (live.isEmpty()) return;
        double dt = DT / substeps;
        for (int i = 0; i < substeps; i++) {
            if (i > 0) for (LiquidCell c : liveCells) c.refreshLevel();
            flowStep(dt);
        }
    }

    private void flowStep(double dt) {
        double toMl = dt * ML_PER_M3, nn = G * MANNING * MANNING;
        for (LiquidCell c : liveCells) {
            c.out = c.in = 0;
            c.space = c.room();
        }
        for (LiquidFace f : live) {
            LiquidCell a = f.a, b = f.b;
            double ha = head(a), hb = head(b), hi = Math.max(ha, hb);
            double area = f.crossSection(hi);
            if (area <= 0) {
                f.q = 0;
                continue;
            }
            double depth = Math.max(area / f.topWidth(hi), 1e-3);
            double friction = nn * Math.abs(f.q / area) / (depth * Math.cbrt(depth));
            // the water set moving is the metre between the cells, or in deep water the depth (a U-tube's column): without
            // that two deep shafts slosh four times too fast and never settle (offline 2026-10-24)
            double inertia = Math.max(1, depth);
            double q = (f.q + dt * G * area * (ha - hb) / inertia) / (1 + dt * (DAMPING + friction));
            // no faster than water falling its own depth (about twice critical flow): thin films on steep ground stay
            // believable, waves in deep water are not touched
            double cap = area * Math.sqrt(2 * G * depth);
            if (q > cap) q = cap;
            else if (q < -cap) q = -cap;
            f.q = q;
            if (q > 0) a.out += q * toMl;
            else b.out -= q * toMl;
        }
        // no cell gives more than it holds; then what each takes in
        for (LiquidFace f : live) {
            double q = f.q;
            if (q == 0) continue;
            LiquidCell src = q > 0 ? f.a : f.b;
            if (!src.boundary) {
                long has = available(src);
                if (src.out > has) f.q = q *= has / src.out;
            }
            if (q > 0) f.b.in += q * toMl;
            else f.a.in -= q * toMl;
        }
        // no capped layer takes more than its room; whole millilitres, the same off one side and onto the other
        for (LiquidFace f : live) {
            LiquidCell into = f.q > 0 ? f.b : f.q < 0 ? f.a : null;
            if (into != null && !into.boundary && into.in > into.space) f.q *= into.space / into.in;
            if (f.q == 0 && Math.abs(f.carry) < 1) continue;
            f.carry += f.q * toMl;
            long due = (long) f.carry;
            if (due == 0) continue;
            LiquidCell src = due > 0 ? f.a : f.b, dst = due > 0 ? f.b : f.a;
            long ml = Math.abs(due);
            if (!src.boundary) ml = Math.min(ml, available(src));
            if (!dst.boundary && !dst.openTop) ml = Math.min(ml, (long) Math.floor(dst.room()));
            if (ml <= 0) continue;
            if (!dst.boundary && f.sill() - dst.level > FALL_MIN && !ramp(f)) fall(f, src, dst, ml);
            else move(src, dst, ml);
            f.carry -= due > 0 ? ml : -ml;
        }
    }

    /** The ground across f is one drawn slope (asked once a face; a ground change makes new faces). */
    private boolean ramp(LiquidFace f) {
        if (f.ramp < 0) {
            LiquidCell a = f.a, b = f.b;
            int ya = Math.floorDiv(a.floorSlab - 1, SLABS), yb = Math.floorDiv(b.floorSlab - 1, SLABS);
            f.ramp = (byte) (ground.ramp(a.column.x, a.column.z, ya, b.column.x, b.column.z, yb) ? 1 : 0);
        }
        return f.ramp == 1;
    }

    private void move(LiquidCell src, LiquidCell dst, long ml) {
        src.column.ledger.changed = dst.column.ledger.changed = true;
        if (src.boundary) ledger(dst).boundaryIn += ml;
        else {
            src.volume -= ml;
            if (src.volume == 0) src.liquid = null;
        }
        if (dst.boundary) ledger(src).boundaryOut += ml;
        else {
            if (dst.volume == 0) {
                dst.liquid = src.liquid != null ? src.liquid : Liquids.WATER;
                wetted.add(dst);
            }
            dst.volume += ml;
        }
        if (!src.boundary && !dst.boundary && src.column.chunk() != dst.column.chunk()) {
            ledger(src).crossOut += ml;
            ledger(dst).crossIn += ml;
        }
    }

    /**
     * As move, but over an edge: the liquid leaves src now and lands in dst after its fall (free fall from the opening to
     * dst's surface), meanwhile on dst's chunk's ledger as in the air.
     */
    private void fall(LiquidFace f, LiquidCell src, LiquidCell dst, long ml) {
        src.column.ledger.changed = dst.column.ledger.changed = true;
        if (src.boundary) ledger(dst).boundaryIn += ml;
        else {
            src.volume -= ml;
            if (src.volume == 0) src.liquid = null;
            if (src.column.chunk() != dst.column.chunk()) {
                ledger(src).crossOut += ml;
                ledger(dst).crossIn += ml;
            }
        }
        ledger(dst).inFlight += ml;
        double drop = Math.max(0, f.sill() - dst.level);
        int ticks = Math.max(1, Math.min(FLIGHT_TICKS - 1, (int) Math.ceil(Math.sqrt(2 * drop / G) / DT)));
        int slot = (int) ((tick + ticks) % FLIGHT_TICKS);
        if (flights[slot] == null) flights[slot] = new ArrayList<>();
        flights[slot].add(new Flight(dst, ml));
        LiquidFall fl = falls.get(f);
        if (fl == null) falls.put(f, fl = new LiquidFall(f));
        fl.src = src;
        fl.dst = dst;
        fl.ml += ml;
        fl.last = tick;
    }

    /** The liquid whose fall ends this tick lands: in its cell, or where that cell's water is now (the ground changed). */
    private void land() {
        List<Flight> due = flights[(int) (tick % FLIGHT_TICKS)];
        if (due == null || due.isEmpty()) return;
        List<Flight> now = new ArrayList<>(due);
        due.clear();
        for (Flight fl : now) arrive(fl);
    }

    private void arrive(Flight fl) {
        LiquidCell c = fl.into;
        LiquidLedger l = ledger(c);
        l.inFlight -= fl.ml;
        LiquidColumn col = index.get(c.column.key);
        LiquidCell into = col == c.column && col.cells.contains(c) ? c : col == null ? null : nearest(col, c.level);
        if (into == null) l.limbo += fl.ml;
        else if (into.boundary) ledger(into).boundaryOut += fl.ml;
        else addTo(into, fl.ml);
        l.changed = true;
    }

    /** Lands at once everything falling into the chunk (it saves or goes). */
    private void landAll(long chunk) {
        for (List<Flight> slot : flights) {
            if (slot == null || slot.isEmpty()) continue;
            for (java.util.Iterator<Flight> it = slot.iterator(); it.hasNext(); ) {
                Flight fl = it.next();
                if (fl.into.column.chunk() != chunk) continue;
                it.remove();
                arrive(fl);
            }
        }
    }

    /** Once a second: each fall's flow over the second; falls dry for two seconds end. */
    private void measureFalls() {
        for (java.util.Iterator<LiquidFall> it = falls.values().iterator(); it.hasNext(); ) {
            LiquidFall fl = it.next();
            fl.flow = fl.ml / 1000.0;
            fl.ml = 0;
            boolean gone = fl.face.removed || !index.containsKey(fl.src.column.key) || tick - fl.last > 40;
            if (gone) {
                it.remove();
                fallsChanged.add(fl.src.column.chunk());
            } else if (fl.sent < 0 || Math.abs(fl.flow - fl.sent) > 0.2 * Math.max(fl.sent, 1e-3)) {
                fl.sent = fl.flow;
                fallsChanged.add(fl.src.column.chunk());
            }
        }
    }

    /** The falls from the chunk's cells (sync). */
    public List<LiquidFall> fallsIn(int chunkX, int chunkZ) {
        long ck = LiquidColumn.chunkKey(chunkX, chunkZ);
        List<LiquidFall> out = new ArrayList<>();
        for (LiquidFall fl : falls.values()) if (fl.src.column.chunk() == ck && fl.flow > 0) out.add(fl);
        out.sort(Comparator.comparingInt(LiquidFall::x).thenComparingInt(LiquidFall::z).thenComparing(LiquidFall::alongX));
        return out;
    }

    /** The chunks (engine keys) whose falls started, stopped or changed since the last call. */
    public long[] takeFallsChanged() {
        long[] out = new long[fallsChanged.size()];
        int i = 0;
        for (long k : fallsChanged) out[i++] = k;
        fallsChanged.clear();
        return out;
    }

    // ---- basins -----------------------------------------------------------------------------------------------------

    private void sleepCheck() {
        Set<LiquidCell> done = new HashSet<>();
        for (LiquidCell start : cells) {
            if (!candidate(start) || done.contains(start)) continue;
            List<LiquidCell> comp = new ArrayList<>();
            ArrayDeque<LiquidCell> queue = new ArrayDeque<>();
            queue.add(start);
            done.add(start);
            boolean busy = false;
            while (!queue.isEmpty()) {
                LiquidCell c = queue.poll();
                comp.add(c);
                for (LiquidFace f : c.faces) {
                    LiquidCell o = f.other(c);
                    if (!joined(f)) continue;
                    if (candidate(o)) {
                        if (done.add(o)) queue.add(o);
                    } else if (o.awake() && o.volume > 0) {
                        busy = true;
                    }
                }
            }
            if (busy) continue;
            for (LiquidCell c : comp) {
                c.sleeping = true;
                for (LiquidFace f : c.faces) {
                    f.q = 0;
                    f.carry = 0;
                }
            }
            basinsDirty = true;
        }
        if (basinsDirty) rebuildBasins();
    }

    private static boolean candidate(LiquidCell c) {
        return c.awake() && c.volume > 0 && c.quiet >= QUIET_STEPS;
    }

    /**
     * One body of water across f: both sides wet and standing above the opening. A film running on below an outlet is not
     * joined to the pool above it, so spreading a basin to one level never carries water over a sill.
     */
    private static boolean joined(LiquidFace f) {
        return f.a.volume > 0 && f.b.volume > 0 && f.crossSection(Math.min(f.a.level, f.b.level)) > 0;
    }

    /** How far water across f stands above what holds it back: the lower side's level or the sill, metres. */
    private static double drive(LiquidFace f) {
        LiquidCell a = f.a, b = f.b;
        boolean aWet = a.volume > 0 || a.boundary, bWet = b.volume > 0 || b.boundary;
        if (!aWet && !bWet) return 0;
        double hi = Math.max(aWet ? a.level : -Double.MAX_VALUE, bWet ? b.level : -Double.MAX_VALUE);
        double lo = Math.min(aWet ? a.level : Double.MAX_VALUE, bWet ? b.level : Double.MAX_VALUE);
        if (!aWet || !bWet) lo = -Double.MAX_VALUE;
        if (f.crossSection(hi) <= 0) return 0;
        return hi - Math.max(lo, f.sill());
    }

    /** Basins are always the connected components of sleeping cells joined into one body of water. */
    private void rebuildBasins() {
        if (dirty) rebuild();
        basins.clear();
        for (LiquidCell c : cells) {
            c.lastBasin = c.basin;
            c.basin = null;
        }
        for (LiquidCell start : cells) {
            if (!start.sleeping || start.basin != null) continue;
            List<LiquidCell> comp = new ArrayList<>();
            ArrayDeque<LiquidCell> queue = new ArrayDeque<>();
            LiquidBasin basin = new LiquidBasin(comp, new ArrayList<>());
            start.basin = basin;
            queue.add(start);
            while (!queue.isEmpty()) {
                LiquidCell c = queue.poll();
                comp.add(c);
                for (LiquidFace f : c.faces) {
                    LiquidCell o = f.other(c);
                    if (o.sleeping && o.basin == null && joined(f)) {
                        o.basin = basin;
                        queue.add(o);
                    }
                }
            }
            double spill = Double.MAX_VALUE;
            for (LiquidCell c : comp) {
                for (LiquidFace f : c.faces) {
                    LiquidCell o = f.other(c);
                    if (o.basin == basin) continue;
                    basin.perimeter.add(f);
                    spill = Math.min(spill, Math.max(f.sill(), o.volume > 0 || o.boundary ? o.level : f.sill()));
                }
            }
            basin.spill = spill;
            // the same cells as a basin already spread to one level: nothing to do (spreading again would give the same)
            LiquidBasin was = comp.get(0).lastBasin;
            boolean same = was != null && !was.needsLevel && was.cells.size() == comp.size();
            for (int i = 1; same && i < comp.size(); i++) same = comp.get(i).lastBasin == was;
            basin.needsLevel = !same;
            basins.add(basin);
        }
        for (LiquidCell c : cells) c.lastBasin = null;
        basinsDirty = false;
    }

    private void wakeCheck() {
        List<LiquidBasin> wake = new ArrayList<>();
        for (LiquidBasin basin : basins) {
            for (LiquidFace f : basin.perimeter) {
                LiquidCell in = f.a.basin == basin ? f.a : f.b, out = f.other(in);
                if (drive(f) > WAKE) {
                    wake.add(basin);
                    if (out.sleeping && out.basin != null) wake.add(out.basin);
                    break;
                }
            }
        }
        for (LiquidBasin b : wake) wake(b);
    }

    private void wake(LiquidBasin basin) {
        if (basin == null) return;
        for (LiquidCell c : basin.cells) {
            if (!c.sleeping) continue;
            c.sleeping = false;
            c.quiet = 0;
            c.lastLevel = c.level;
            c.lastVolume = c.volume;
        }
        basinsDirty = true;
    }

    /** Spread each basin that got rain to one level over its cells, to the millilitre. */
    private void levelBasins() {
        for (LiquidBasin basin : basins) {
            if (!basin.needsLevel) continue;
            basin.needsLevel = false;
            level(basin);
        }
    }

    private void level(LiquidBasin basin) {
        List<LiquidCell> cs = basin.cells;
        long total = 0;
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (LiquidCell c : cs) {
            total += c.volume;
            lo = Math.min(lo, c.floor());
            hi = Math.max(hi, c.ceiling());
            c.column.ledger.spread -= c.volume;
        }
        // bounds from the geometry alone, so spreading the same total over the same cells always gives the same split
        hi += (double) total / ML_PER_M3 + 1;
        for (int it = 0; it < 48; it++) {   // a window under 100 m: to 1e-12 m
            double mid = (lo + hi) / 2, v = 0;
            for (LiquidCell c : cs) v += c.volumeBelow(mid);
            if (v < total) lo = mid;
            else hi = mid;
        }
        double eta = (lo + hi) / 2;
        long[] alloc = new long[cs.size()];
        double[] frac = new double[cs.size()];
        long sum = 0;
        for (int i = 0; i < cs.size(); i++) {
            double v = Math.min(cs.get(i).volumeBelow(eta), cs.get(i).openTop ? Double.MAX_VALUE : cs.get(i).capacity());
            alloc[i] = (long) Math.floor(v);
            frac[i] = v - alloc[i];
            sum += alloc[i];
        }
        int n = cs.size();
        long rest = total - sum;
        if (rest > n) {
            // only a sealed, overfull basin gets here: spread the bulk evenly first
            long each = rest / n;
            for (int i = 0; i < n; i++) alloc[i] += each;
            rest -= each * n;
        }
        if (rest != 0) {
            // largest remainders first (smallest when taking back), ties by position: the cell index in the low 20 bits
            long[] order = new long[n];
            for (int i = 0; i < n; i++) {
                long f = (long) ((rest > 0 ? 1 - frac[i] : frac[i]) * (1L << 40));
                order[i] = f << 20 | i;
            }
            Arrays.sort(order);
            for (int k = 0; rest != 0; k = (k + 1) % n) {
                int i = (int) (order[k] & 0xfffff);
                if (rest > 0) {
                    alloc[i]++;
                    rest--;
                } else if (alloc[i] > 0) {
                    alloc[i]--;
                    rest++;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            LiquidCell c = cs.get(i);
            c.volume = alloc[i];
            c.column.ledger.spread += c.volume;
            c.column.ledger.changed = true;
            c.liquid = c.volume > 0 ? Liquids.WATER : null;
            c.refreshLevel();
            if (c.volume == 0) {
                // a dry cell is no part of a body of water: awake, so water can run into it again
                c.sleeping = false;
                c.quiet = 0;
                basinsDirty = true;
            }
        }
        // spreading moves liquid between chunks too: each chunk's net change, once
        for (LiquidCell c : cs) {
            LiquidLedger l = c.column.ledger;
            if (l.spread > 0) l.crossIn += l.spread;
            else l.crossOut -= l.spread;
            l.spread = 0;
        }
    }

    // ---- geometry changes -------------------------------------------------------------------------------------------

    /**
     * GroundEvents: the ground of chunk (chunkX, chunkZ) changed between blocks minY and maxY. Its columns are rebuilt and
     * every millilitre laid into the new layers (docs/gameplay/water_v1_design.md 5.3, user 2026-10-24: the liquid must be
     * kept exactly).
     */
    public void geometryChanged(int chunkX, int chunkZ, int minY, int maxY) {
        long ck = LiquidColumn.chunkKey(chunkX, chunkZ);
        if (!ledgers.containsKey(ck)) return;
        List<LiquidColumn> old = new ArrayList<>();
        for (LiquidColumn col : columns.values()) if (col.chunk() == ck) old.add(col);
        rebuildColumns(old);
    }

    /**
     * The ground changed in the blocks [x0, x1] x [z0, z1] (inclusive): only the columns there are rebuilt, the liquid kept
     * exactly as in {@link #geometryChanged}. For a block placed or broken next to a pond (the game's block changes until
     * C's GroundEvents merges), so the rest of the chunk is not read again.
     */
    public void groundChanged(int x0, int z0, int x1, int z1) {
        List<LiquidColumn> old = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                LiquidColumn col = index.get(LiquidColumn.key(x, z));
                if (col != null) old.add(col);
            }
        }
        if (!old.isEmpty()) rebuildColumns(old);
    }

    private void rebuildColumns(List<LiquidColumn> old) {
        if (basinsDirty) rebuildBasins();
        for (LiquidColumn col : old) {
            for (LiquidCell c : col.cells) if (c.sleeping) wake(c.basin);
            for (LiquidCell c : col.cells)
                for (LiquidFace f : c.faces) if (f.other(c).sleeping) wake(f.other(c).basin);
        }
        for (LiquidColumn col : old) {
            disconnect(col);
            removeColumn(col.key);
            LiquidColumn fresh = LiquidColumn.build(ground, col.x, col.z, y0, y1, areaBuf);
            col.ledger.changed = true;
            if (fresh == null) {
                long lost = 0;
                for (LiquidCell c : col.cells) lost += c.volume;
                col.ledger.limbo += lost;
                continue;
            }
            fresh.rainCarry = col.rainCarry;
            putColumn(fresh);
            relay(col, fresh);
            connect(fresh);
        }
        dirty = basinsDirty = true;
        activateWetted();
    }

    /** Lay the old column's liquid into the new one by the old water's heights; the rest rises, or goes next door. */
    private void relay(LiquidColumn old, LiquidColumn fresh) {
        long[] got = new long[fresh.cells.size()];
        for (LiquidCell o : old.cells) {
            if (o.boundary) {
                // a river stays a river: the new layer under its surface holds the same level
                LiquidCell n = null;
                for (LiquidCell c : fresh.cells) if (n == null && c.floor() < o.level && c.ceiling() > o.floor()) n = c;
                if (n == null) n = nearest(fresh, o.floor());
                if (n != null) {
                    n.boundary = true;
                    n.liquid = Liquids.WATER;
                    n.level = o.level;
                    wetted.add(n);
                }
                continue;
            }
            if (o.volume <= 0) continue;
            double lo = o.floor(), hi = Math.min(o.level, o.ceiling());
            double[] share = new double[fresh.cells.size()];
            int target = -1;
            for (int i = 0; i < fresh.cells.size(); i++) {
                LiquidCell n = fresh.cells.get(i);
                double from = Math.max(lo, n.floor()), to = Math.min(hi, n.ceiling());
                if (to > from) {
                    share[i] = o.volumeBelow(to) - o.volumeBelow(from);
                    target = i;   // the highest layer the old water reaches: what lost its room rises there
                }
            }
            if (target < 0) {
                // the old water's span is solid now: the nearest layer above it, else below
                double best = Double.MAX_VALUE;
                for (int i = 0; i < fresh.cells.size(); i++) {
                    LiquidCell n = fresh.cells.get(i);
                    double d = n.floor() >= lo ? n.floor() - lo : lo - n.ceiling() + 1000;
                    if (d < best) {
                        best = d;
                        target = i;
                    }
                }
            }
            if (target < 0) {
                displace(old, o, o.volume);
                continue;
            }
            long given = 0;
            for (int i = 0; i < share.length; i++) {
                if (i == target) continue;
                long v = (long) Math.floor(share[i]);
                got[i] += v;
                given += v;
            }
            got[target] += o.volume - given;
        }
        for (int i = 0; i < got.length; i++) {
            if (got[i] <= 0) continue;
            LiquidCell n = fresh.cells.get(i);
            if (n.boundary) {
                ledgers.get(fresh.chunk()).boundaryOut += got[i];
                continue;
            }
            n.volume = got[i];
            n.liquid = Liquids.WATER;
            n.refreshLevel();
            n.lastLevel = n.level;
            n.lastVolume = n.volume;
            wetted.add(n);
        }
    }

    /** Liquid whose column has no room left: into a neighbouring column's layer at that height, else limbo. */
    private void displace(LiquidColumn from, LiquidCell old, long ml) {
        int[][] around = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] d : around) {
            LiquidColumn n = index.get(LiquidColumn.key(from.x + d[0], from.z + d[1]));
            if (n == null || n.cells.isEmpty()) continue;
            LiquidCell c = nearest(n, old.level);
            if (c == null || c.boundary) continue;
            if (n.chunk() != from.chunk()) {
                ledgers.get(from.chunk()).crossOut += ml;
                ledgers.get(n.chunk()).crossIn += ml;
            }
            addTo(c, ml);
            return;
        }
        ledgers.get(from.chunk()).limbo += ml;
    }

    // ---- readouts ---------------------------------------------------------------------------------------------------

    public LiquidCell cellAt(int x, double y, int z) {
        LiquidColumn col = index.get(LiquidColumn.key(x, z));
        return col == null ? null : col.cellAt(y);
    }

    public List<LiquidCell> cells() {
        if (dirty) rebuild();
        return Collections.unmodifiableList(cells);
    }

    public List<LiquidFace> faces() {
        if (dirty) rebuild();
        return Collections.unmodifiableList(faces);
    }

    public List<LiquidBasin> basins() {
        if (basinsDirty) rebuildBasins();
        return Collections.unmodifiableList(basins);
    }

    public LiquidLedger ledger(int chunkX, int chunkZ) {
        return ledgers.get(LiquidColumn.chunkKey(chunkX, chunkZ));
    }

    public boolean loaded(int chunkX, int chunkZ) {
        return ledgers.containsKey(LiquidColumn.chunkKey(chunkX, chunkZ));
    }

    /** The column at (x, z) when there is one (asking makes none: WaterQuery). */
    public LiquidColumn existingColumn(int x, int z) {
        return index.get(LiquidColumn.key(x, z));
    }

    /** Is there a column at (x, z) yet (the ground there matters to the liquid)? */
    public boolean hasColumn(int x, int z) {
        return index.containsKey(LiquidColumn.key(x, z));
    }

    /** Whether the chunk has anything to save: liquid, limbo, rain or flow state not settled. */
    public boolean worthSaving(int chunkX, int chunkZ) {
        long ck = LiquidColumn.chunkKey(chunkX, chunkZ);
        LiquidLedger l = ledgers.get(ck);
        if (l == null) return false;
        if (l.limbo != 0) return true;
        for (int i = 0; i < 16; i++) {
            for (int k = 0; k < 16; k++) {
                LiquidColumn col = index.get(LiquidColumn.key((chunkX << 4) + i, (chunkZ << 4) + k));
                if (col != null && worthSaving(col)) return true;
            }
        }
        return false;
    }

    /** The chunks holding liquid (or limbo), in key order. */
    public long[] wetChunks() {
        java.util.TreeSet<Long> out = new java.util.TreeSet<>();
        for (LiquidColumn col : columns.values()) {
            if (out.contains(col.chunk())) continue;
            for (LiquidCell c : col.cells) {
                if (c.volume > 0) {
                    out.add(col.chunk());
                    break;
                }
            }
        }
        for (Map.Entry<Long, LiquidLedger> e : ledgers.entrySet()) if (e.getValue().limbo != 0) out.add(e.getKey());
        long[] a = new long[out.size()];
        int i = 0;
        for (long k : out) a[i++] = k;
        return a;
    }

    /** The chunks some of whose liquid moved since the last call, in key order; their flags cleared. */
    public long[] takeChanged() {
        int n = 0;
        for (LiquidLedger l : ledgers.values()) if (l.changed) n++;
        long[] out = new long[n];
        int i = 0;
        for (Map.Entry<Long, LiquidLedger> e : ledgers.entrySet()) {
            if (!e.getValue().changed) continue;
            e.getValue().changed = false;
            out[i++] = e.getKey();
        }
        return out;
    }

    /** The chunk's cells holding liquid or held at a level (sync, readouts), in column order. */
    public List<LiquidCell> wetCells(int chunkX, int chunkZ) {
        long ck = LiquidColumn.chunkKey(chunkX, chunkZ);
        List<LiquidCell> out = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            for (int k = 0; k < 16; k++) {
                LiquidColumn col = index.get(LiquidColumn.key((chunkX << 4) + i, (chunkZ << 4) + k));
                if (col == null) continue;
                for (LiquidCell c : col.cells) if (c.volume > 0 || c.boundary) out.add(c);
            }
        }
        return out;
    }

    /** Columns, cells, wet and awake cells, faces, basins, chunks (dev readout). */
    public String stats() {
        int wet = 0, awake = 0;
        for (LiquidCell c : cells) {
            if (c.volume > 0) wet++;
            if (c.awake() && c.volume > 0) awake++;
        }
        return String.format(java.util.Locale.ROOT, "%d chunks, %d columns, %d cells (%d wet, %d flowing), %d faces (%d live), %d basins, %d substeps; ms so far: rain + levels %.1f, wake %.1f, flow %.1f, quiet %.1f",
                ledgers.size(), columns.size(), cells.size(), wet, awake, faces.size(), live.size(), basins.size(), substeps,
                spent[0] / 1e6, spent[1] / 1e6, spent[2] / 1e6, spent[3] / 1e6);
    }

    public long tickCount() {
        return tick;
    }

    public int substeps() {
        return substeps;
    }

    /** Liquid in the loaded cells, mL. */
    public long total() {
        long v = 0;
        for (LiquidColumn col : columns.values()) for (LiquidCell c : col.cells) v += c.volume;
        for (LiquidLedger l : ledgers.values()) v += l.inFlight;
        return v;
    }

    /** Liquid in the air, mL (falling over an edge, not landed yet). */
    public long inFlight() {
        long v = 0;
        for (LiquidLedger l : ledgers.values()) v += l.inFlight;
        return v;
    }

    /** The time spent so far by phase, ns: rain and levelling, wake check, flow, quiet and sleep (/afl dev water perf). */
    public long[] spentNanos() {
        return spent.clone();
    }

    public int awakeWetCells() {
        int n = 0;
        for (LiquidColumn col : columns.values()) for (LiquidCell c : col.cells) if (c.awake() && c.volume > 0) n++;
        return n;
    }

    /**
     * What is wrong, if anything: a negative volume, a chunk whose cells and limbo are not its ledger's net to the
     * millilitre, a face between cells of different liquids that moved liquid.
     */
    public List<String> verify() {
        List<String> problems = new ArrayList<>();
        Map<Long, Long> held = new HashMap<>();
        for (LiquidColumn col : columns.values()) {
            for (LiquidCell c : col.cells) {
                if (c.volume < 0) problems.add("negative volume at " + col.x + " " + col.z + ": " + c.volume);
                if (c.boundary && c.volume != 0) problems.add("boundary holds liquid at " + col.x + " " + col.z);
                held.merge(col.chunk(), c.volume, Long::sum);
            }
        }
        for (Map.Entry<Long, LiquidLedger> e : ledgers.entrySet()) {
            long h = held.getOrDefault(e.getKey(), 0L) + e.getValue().limbo + e.getValue().inFlight;
            if (e.getValue().inFlight < 0) problems.add("negative liquid in the air over chunk " + (e.getKey() >> 32) + " " + (int) (long) e.getKey());
            if (h != e.getValue().net())
                problems.add("chunk " + (e.getKey() >> 32) + " " + (int) (long) e.getKey() + " holds " + h + " mL, ledger says " + e.getValue().net());
        }
        return problems;
    }
}
