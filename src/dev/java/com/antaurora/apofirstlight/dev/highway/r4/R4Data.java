package com.antaurora.apofirstlight.dev.highway.r4;

import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The R4 test segment of one dimension (docs/worldgen/highway_r4_sample_v1.md, F/K): the test-world authorisation, the
 * phase, the recipe and the whole write journal (position, exact state before, exact state written) in a state palette.
 * Written and saved to disk before the first block is placed, so remove can always tell which blocks are still the
 * works' own: it restores a position only while it still holds the written state.
 */
public final class R4Data extends SavedData {
    public static final String ID = "afl_dev_highway_r4";
    public static final int SCHEMA = 1;
    public static final Set<String> PHASES = Set.of("EMPTY", "BUILDING", "COMPLETE", "REMOVING", "PARTIAL");

    public boolean authorized;
    public String worldName = "";
    public long seed;
    public String phase = "EMPTY";
    public String recipe = "", version = "";
    public UUID instance = new UUID(0, 0);
    public long[] pos = new long[0];
    public int[] before = new int[0], after = new int[0];
    public List<BlockState> palette = new ArrayList<>();
    /** BUILDING: entries [0, progress) written; REMOVING: entries [progress, size) handled, counting down */
    public int progress;
    public int preserved, conflicts;
    /** build: entries written over a natural change found at write time (the journal records the state found) */
    public int natural;
    public String stats = "";

    public static R4Data create() { return new R4Data(); }

    public static R4Data load(CompoundTag tag, HolderGetter<Block> blocks) {
        if (tag.getInt("schema") != SCHEMA) throw new IllegalArgumentException("Unsupported R4 saved-data schema; refusing to modify it");
        var d = new R4Data();
        d.authorized = tag.getBoolean("authorized");
        d.worldName = tag.getString("world");
        d.seed = tag.getLong("seed");
        d.phase = tag.getString("phase");
        if (!PHASES.contains(d.phase)) throw new IllegalArgumentException("Unknown R4 phase " + d.phase);
        d.recipe = tag.getString("recipe");
        d.version = tag.getString("version");
        if (tag.hasUUID("instance")) d.instance = tag.getUUID("instance");
        d.pos = tag.getLongArray("pos");
        d.before = tag.getIntArray("before");
        d.after = tag.getIntArray("after");
        ListTag list = tag.getList("palette", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) d.palette.add(NbtUtils.readBlockState(blocks, list.getCompound(i)));
        d.progress = tag.getInt("progress");
        d.preserved = tag.getInt("preserved");
        d.conflicts = tag.getInt("conflicts");
        d.natural = tag.getInt("natural");
        d.stats = tag.getString("stats");
        if (d.pos.length != d.before.length || d.pos.length != d.after.length || d.progress < 0 || d.progress > d.pos.length)
            throw new IllegalArgumentException("Inconsistent R4 journal");
        for (int i = 0; i < d.pos.length; i++)
            if (d.before[i] < 0 || d.before[i] >= d.palette.size() || d.after[i] < 0 || d.after[i] >= d.palette.size())
                throw new IllegalArgumentException("R4 journal palette index out of range");
        if (!d.phase.equals("EMPTY") && (d.recipe.isEmpty() || !d.version.matches("[a-f0-9]{64}")))
            throw new IllegalArgumentException("R4 journal without a recipe");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema", SCHEMA);
        tag.putBoolean("authorized", authorized);
        tag.putString("world", worldName);
        tag.putLong("seed", seed);
        tag.putString("phase", phase);
        tag.putString("recipe", recipe);
        tag.putString("version", version);
        tag.putUUID("instance", instance);
        tag.putLongArray("pos", pos);
        tag.putIntArray("before", before);
        tag.putIntArray("after", after);
        ListTag list = new ListTag();
        for (BlockState s : palette) list.add(NbtUtils.writeBlockState(s));
        tag.put("palette", list);
        tag.putInt("progress", progress);
        tag.putInt("preserved", preserved);
        tag.putInt("conflicts", conflicts);
        tag.putInt("natural", natural);
        tag.putString("stats", stats);
        return tag;
    }

    public static R4Data get(ServerLevel level) {
        HolderGetter<Block> blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup();
        var file = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),
                level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("data").resolve(ID + ".dat");
        R4Data data = level.getDataStorage().get(tag -> load(tag, blocks), ID);
        // Vanilla swallows a failed load and returns null: never take an unreadable journal for an empty plot
        if (data == null && java.nio.file.Files.exists(file)) throw new IllegalStateException("R4_SAVED_DATA_UNREADABLE; refusing to replace the journal");
        if (data == null) {
            data = new R4Data();
            level.getDataStorage().set(ID, data);
        }
        return data;
    }

    /** Store a checked journal; the caller saves the data storage before writing any block. */
    public void begin(R4Recipe r, long[] positions, BlockState[] was, BlockState[] will, String statsLine) {
        Map<BlockState, Integer> index = new HashMap<>();
        palette = new ArrayList<>();
        before = new int[positions.length];
        after = new int[positions.length];
        for (int i = 0; i < positions.length; i++) {
            before[i] = index.computeIfAbsent(was[i], s -> { palette.add(s); return palette.size() - 1; });
            after[i] = index.computeIfAbsent(will[i], s -> { palette.add(s); return palette.size() - 1; });
        }
        pos = positions.clone();
        recipe = r.json();
        version = r.version();
        instance = UUID.randomUUID();
        phase = "BUILDING";
        progress = 0;
        preserved = conflicts = natural = 0;
        stats = statsLine;
        setDirty();
    }

    public void clear() {
        phase = "EMPTY";
        recipe = version = stats = "";
        pos = new long[0];
        before = after = new int[0];
        palette = new ArrayList<>();
        progress = preserved = conflicts = natural = 0;
        instance = new UUID(0, 0);
        setDirty();
    }

    public boolean active() { return !phase.equals("EMPTY"); }

    /** Palette index of a state, added when new (an original state recorded during the build). */
    public int index(BlockState s) {
        int i = palette.indexOf(s);
        if (i < 0) { palette.add(s); i = palette.size() - 1; }
        return i;
    }
}
