package com.antaurora.apofirstlight.worldgen.lock;

import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanFingerprint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The overworld's {@link WorldgenLockRecord} as SavedData: {@code data/apocalypse_firstlight_worldgen_lock.dat}. */
public final class WorldgenLockData extends SavedData {
    public static final String NAME = "apocalypse_firstlight_worldgen_lock";

    WorldgenLockRecord record;

    WorldgenLockData(WorldgenLockRecord record) {
        this.record = record;
    }

    public WorldgenLockRecord record() { return record; }

    static WorldgenLockData load(CompoundTag tag) {
        CompoundTag f = tag.getCompound("fingerprint");
        TerrainPlanFingerprint.Tiles tiles = null;
        if (f.contains("tiles", Tag.TAG_COMPOUND)) {
            CompoundTag t = f.getCompound("tiles");
            tiles = new TerrainPlanFingerprint.Tiles(t.getInt("cols"), t.getInt("rows"), t.getIntArray("meanHeightCm"),
                    t.getIntArray("wetCells"), t.getIntArray("riverVertices"), t.getIntArray("zoneSum"));
        }
        WorldgenFingerprint fp = new WorldgenFingerprint(f.getString("profile"), f.getLong("seed"), strings(f.getCompound("systems")),
                f.getInt("planCacheFormat"), f.getString("planDigest"), strings(f.getCompound("probes")), tiles);
        List<WorldgenLockRecord.HistoryEntry> history = new ArrayList<>();
        ListTag list = tag.getList("history", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            history.add(new WorldgenLockRecord.HistoryEntry(e.getLong("time"), e.getString("event"), e.getString("detail")));
        }
        return new WorldgenLockData(new WorldgenLockRecord(tag.getInt("schema"), tag.getString("origin"), tag.getLong("created"),
                tag.getString("createdBy"), fp, history));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        WorldgenLockRecord r = record;
        tag.putInt("schema", r.schema());
        tag.putString("origin", r.origin());
        tag.putLong("created", r.createdMillis());
        tag.putString("createdBy", r.createdBy());
        WorldgenFingerprint fp = r.fingerprint();
        CompoundTag f = new CompoundTag();
        f.putString("profile", fp.profile());
        f.putLong("seed", fp.seed());
        f.put("systems", compound(fp.systems()));
        f.putInt("planCacheFormat", fp.planCacheFormat());
        f.putString("planDigest", fp.planDigest());
        f.put("probes", compound(fp.probes()));
        f.putString("id", fp.id());   // for people reading the file; recomputed on load, never trusted
        if (fp.tiles() != null) {
            CompoundTag t = new CompoundTag();
            t.putInt("cols", fp.tiles().cols());
            t.putInt("rows", fp.tiles().rows());
            t.putIntArray("meanHeightCm", fp.tiles().meanHeightCm());
            t.putIntArray("wetCells", fp.tiles().wetCells());
            t.putIntArray("riverVertices", fp.tiles().riverVertices());
            t.putIntArray("zoneSum", fp.tiles().zoneSum());
            f.put("tiles", t);
        }
        tag.put("fingerprint", f);
        ListTag list = new ListTag();
        for (WorldgenLockRecord.HistoryEntry e : r.history()) {
            CompoundTag c = new CompoundTag();
            c.putLong("time", e.timeMillis());
            c.putString("event", e.event());
            c.putString("detail", e.detail());
            list.add(c);
        }
        tag.put("history", list);
        return tag;
    }

    private static Map<String, String> strings(CompoundTag t) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String k : t.getAllKeys()) m.put(k, t.getString(k));
        return m;
    }

    private static CompoundTag compound(Map<String, String> m) {
        CompoundTag t = new CompoundTag();
        m.forEach(t::putString);
        return t;
    }
}
