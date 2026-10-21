package com.antaurora.apofirstlight.map;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * One map's record on the server (AFL Map V1, docs/ui/afl_map_v1_plan.md 5.3; user 2026-10-21: the drawing stays over
 * leaving and coming back): its chunks' samples (MapGround packing, the biome as an index into this map's own palette of
 * biome ids) in a folder of the world save, {@code data/afl_maps/map_<id>/}: {@code palette.txt} (one biome id a line) and
 * a file per 512 m region, {@code r.<rx>.<rz>.dat} (gzip: version, count, then per chunk its x and z in the region and
 * its 64 samples). Only changed regions are written. When each chunk was sampled is not kept: after a restart a chunk is
 * sampled again when the map comes by. Server thread only.
 */
public final class MapStore {
    static final int VERSION = 1, REGION = 32;
    public final int id;
    private final Path dir;
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Integer> paletteIndex = new HashMap<>();
    private final Map<Long, int[]> chunks = new LinkedHashMap<>();
    private final Map<Long, Long> taken = new HashMap<>();
    private final Set<Long> dirtyRegions = new HashSet<>();
    private boolean paletteDirty;

    MapStore(int id, Path dir) {
        this.id = id;
        this.dir = dir;
    }

    public static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    /** The palette index of a biome id (added when new; 253 at most, past that the last). */
    public int biome(String id) {
        Integer i = paletteIndex.get(id);
        if (i != null) return i;
        if (palette.size() >= 253) return palette.size() - 1;
        palette.add(id);
        paletteIndex.put(id, palette.size() - 1);
        paletteDirty = true;
        return palette.size() - 1;
    }

    public List<String> palette() { return palette; }

    public Map<Long, int[]> chunks() { return chunks; }

    public int[] chunk(long key) { return chunks.get(key); }

    /** When a chunk was last sampled this run (ms), or null. */
    public Long taken(long key) { return taken.get(key); }

    /** A new sample of a chunk; whether it differs from what was recorded. */
    public boolean put(int cx, int cz, int[] samples, long now) {
        long k = key(cx, cz);
        taken.put(k, now);
        int[] old = chunks.put(k, samples);
        if (old != null && java.util.Arrays.equals(old, samples)) return false;
        dirtyRegions.add(key(Math.floorDiv(cx, REGION), Math.floorDiv(cz, REGION)));
        return true;
    }

    // ------------------------------------------------------------------ the files

    void load() throws IOException {
        Path pal = dir.resolve("palette.txt");
        if (Files.exists(pal))
            for (String line : Files.readAllLines(pal, StandardCharsets.UTF_8))
                if (!line.isBlank()) { paletteIndex.put(line.trim(), palette.size()); palette.add(line.trim()); }
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                String n = f.getFileName().toString();
                if (!n.startsWith("r.") || !n.endsWith(".dat")) continue;
                String[] parts = n.substring(2, n.length() - 4).split("\\.");
                if (parts.length != 2) continue;
                int rx = Integer.parseInt(parts[0]), rz = Integer.parseInt(parts[1]);
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(f))))) {
                    if (in.readInt() != VERSION) continue;
                    int count = in.readInt();
                    for (int c = 0; c < count; c++) {
                        int lx = in.readUnsignedByte(), lz = in.readUnsignedByte();
                        int[] s = new int[MapGround.CELLS * MapGround.CELLS];
                        for (int i = 0; i < s.length; i++) s[i] = in.readInt();
                        chunks.put(key(rx * REGION + lx, rz * REGION + lz), s);
                    }
                }
            }
        }
    }

    /** Writes the palette and the changed regions (each to a temporary file, then moved over the old one). */
    void save() throws IOException {
        if (!paletteDirty && dirtyRegions.isEmpty()) return;
        Files.createDirectories(dir);
        if (paletteDirty) {
            write(dir.resolve("palette.txt"), out -> out.write(String.join("\n", palette).getBytes(StandardCharsets.UTF_8)), false);
            paletteDirty = false;
        }
        Map<Long, List<Long>> byRegion = new HashMap<>();
        for (long k : chunks.keySet()) {
            int cx = (int) (k >> 32), cz = (int) k;
            long r = key(Math.floorDiv(cx, REGION), Math.floorDiv(cz, REGION));
            if (dirtyRegions.contains(r)) byRegion.computeIfAbsent(r, x -> new ArrayList<>()).add(k);
        }
        for (Map.Entry<Long, List<Long>> e : byRegion.entrySet()) {
            int rx = (int) (e.getKey() >> 32), rz = (int) (long) e.getKey();
            write(dir.resolve("r." + rx + "." + rz + ".dat"), raw -> {
                DataOutputStream out = new DataOutputStream(raw);
                out.writeInt(VERSION);
                out.writeInt(e.getValue().size());
                for (long k : e.getValue()) {
                    int cx = (int) (k >> 32), cz = (int) k;
                    out.writeByte(Math.floorMod(cx, REGION));
                    out.writeByte(Math.floorMod(cz, REGION));
                    for (int s : chunks.get(k)) out.writeInt(s);
                }
                out.flush();
            }, true);
        }
        dirtyRegions.clear();
    }

    private interface Writer { void write(java.io.OutputStream out) throws IOException; }

    private static void write(Path file, Writer w, boolean gzip) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (var raw = new BufferedOutputStream(Files.newOutputStream(tmp))) {
            if (gzip) {
                try (GZIPOutputStream z = new GZIPOutputStream(raw)) { w.write(z); }
            } else w.write(raw);
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
