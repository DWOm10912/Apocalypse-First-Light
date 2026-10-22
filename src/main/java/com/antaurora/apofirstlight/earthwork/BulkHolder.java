package com.antaurora.apofirstlight.earthwork;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import java.util.Map;
import java.util.TreeMap;

/**
 * Something that carries loose bulk earth (docs/gameplay/sandbox_v1_design.md 4.2, docs/worldgen/earthwork_a2.md 4): a
 * shovel now, a wheelbarrow or a truck bed later. Amounts are quanta (EarthGrid: 1 quantum = 0.244 L), materials by
 * BulkMaterials id. A holder carries one material: earth of another put in with it mixes, and the whole load becomes
 * backfill (sandbox 4.3).
 */
public interface BulkHolder {
    Capability<BulkHolder> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});

    /** The most it carries (quanta). */
    long capacity();

    /** What it carries: material id → quanta (empty, or one material). */
    Map<String, Long> contents();

    default long stored() {
        long s = 0;
        for (long q : contents().values()) s += q;
        return s;
    }

    default long room() {
        return Math.max(0, capacity() - stored());
    }

    /** The material it carries, or null when empty. */
    default String material() {
        for (var e : contents().entrySet()) if (e.getValue() > 0) return e.getKey();
        return null;
    }

    /** Puts up to quanta of material in; what went in (or would, simulated). */
    long insert(String material, long quanta, boolean simulate);

    /** Takes up to quanta out (of any material: a holder carries one); what came out (or would, simulated). */
    long extract(long quanta, boolean simulate);

    /** The load in a stack's tag {@link #TAG} (id → quanta). */
    String TAG = "AflBulk";

    /** A holder kept in an item stack's tag. */
    final class InStack implements BulkHolder {
        private final ItemStack stack;
        private final long capacity;

        public InStack(ItemStack stack, long capacity) {
            this.stack = stack;
            this.capacity = capacity;
        }

        public static Map<String, Long> read(ItemStack stack) {
            Map<String, Long> out = new TreeMap<>();
            CompoundTag t = stack.getTag();
            if (t == null || !t.contains(TAG)) return out;
            CompoundTag b = t.getCompound(TAG);
            for (String k : b.getAllKeys()) if (b.getLong(k) > 0) out.put(k, b.getLong(k));
            return out;
        }

        private void write(Map<String, Long> load) {
            CompoundTag b = new CompoundTag();
            load.forEach((k, v) -> { if (v > 0) b.putLong(k, v); });
            if (b.isEmpty()) {
                if (stack.getTag() != null) stack.getTag().remove(TAG);
                if (stack.getTag() != null && stack.getTag().isEmpty()) stack.setTag(null);
            } else stack.getOrCreateTag().put(TAG, b);
        }

        @Override public long capacity() { return capacity; }

        @Override public Map<String, Long> contents() { return read(stack); }

        @Override
        public long insert(String material, long quanta, boolean simulate) {
            Map<String, Long> load = read(stack);
            long in = Math.max(0, Math.min(quanta, capacity - load.values().stream().mapToLong(Long::longValue).sum()));
            if (in <= 0 || simulate) return in;
            String had = load.isEmpty() ? null : load.keySet().iterator().next();
            if (had == null || had.equals(material)) load.merge(material, in, Long::sum);
            else {   // two materials: all of it backfill
                long all = load.values().stream().mapToLong(Long::longValue).sum() + in;
                load.clear();
                load.put("backfill", all);
            }
            write(load);
            return in;
        }

        @Override
        public long extract(long quanta, boolean simulate) {
            Map<String, Long> load = read(stack);
            String m = load.isEmpty() ? null : load.keySet().iterator().next();
            if (m == null) return 0;
            long out = Math.max(0, Math.min(quanta, load.get(m)));
            if (out <= 0 || simulate) return out;
            load.put(m, load.get(m) - out);
            write(load);
            return out;
        }
    }
}
