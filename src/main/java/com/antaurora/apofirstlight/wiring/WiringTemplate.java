package com.antaurora.apofirstlight.wiring;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongUnaryOperator;

/**
 * Building Wiring V1 phase 6, structure templates (docs/gameplay/building_wiring_v1_phase6_draft.md): the wiring lives in
 * the chunks, not in the blocks, so a template carries it in its panels' block entity data.
 * <ul>
 *   <li>export ({@link #pack}, at /afl_author validate and export): each panel's wiring, every circuit tapping it and the
 *   feeder, walked as a solve walks it but only inside the plot, goes into the panel's copy in the template:
 *   {@link #KEY} (pairs of longs: the cell's offset from the panel, the node) and {@link #FACING_KEY} (the panel's facing
 *   then). Links, slope links and taps leading out of the plot are cut off; wire no panel reaches is left out and reported;</li>
 *   <li>placement: worldgen/structure/AflBlockEntityProcessor turns it with the building ({@link #transform}); placed without
 *   it (vanilla /place template) the panel turns it by the difference between its facing and the packed one ({@link #between});</li>
 *   <li>the panel writes it back into the chunks ({@link #unpack}), merging with wire already there, keeping what lies in
 *   unloaded chunks for later and dropping cells that cannot hold wire where the building now stands.</li>
 * </ul>
 */
public final class WiringTemplate {
    public static final String KEY = "PackedWiring", FACING_KEY = "PackedFacing";
    /** The orphan cells a report lists by position. */
    public static final int SAMPLE = 5;
    private static final boolean[] UP_DOWN = {true, false};

    private WiringTemplate() {}

    /**
     * What an export packed: the nodes, the panels carrying them, the nodes no panel reaches (left out; the first few cells),
     * circuits joined to a panel packed before (packed once), the links and taps cut off at the plot's edge, a circuit cut
     * short at {@link WiringSupply#MAX_NODES}.
     */
    public record Report(int nodes, int panels, int orphans, List<BlockPos> orphanCells, int joined, int clipped, boolean capped) {
        public static final Report NONE = new Report(0, 0, 0, List.of(), 0, 0, false);

        public String text() {
            StringBuilder b = new StringBuilder("暗线 " + nodes + " 个节点 · 配电箱 " + panels + " 个 · 没打包 " + orphans + " 个");
            if (!orphanCells.isEmpty()) {
                b.append("（");
                for (int i = 0; i < orphanCells.size(); i++) b.append(i == 0 ? "" : "，").append(orphanCells.get(i).toShortString());
                b.append(orphans > orphanCells.size() ? "……）" : "）");
            }
            if (joined > 0) b.append(" · 互连 ").append(joined).append(" 路");
            if (clipped > 0) b.append(" · 出界剪掉 ").append(clipped).append(" 处");
            if (capped) b.append(" · 有一路超过 ").append(WiringSupply.MAX_NODES).append(" 个节点，没打包全");
            return b.toString();
        }
    }

    // ---------------------------------------------------------------------------------------------------------- pack

    /**
     * Packs the plot's wiring into the panels of a captured template (its nbt, before it is saved): see the class comment.
     * The world is not changed. A panel without wire gets no keys (A1's LEGACY_AREA panels stay as they were).
     */
    public static Report pack(ServerLevel level, BlockPos origin, Vec3i size, CompoundTag template) {
        BoundingBox box = BoundingBox.fromCorners(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1));
        Long2IntOpenHashMap claimed = new Long2IntOpenHashMap();   // cell -> circuits packed (bit c)
        int nodes = 0, panels = 0, joined = 0, clipped = 0;
        boolean capped = false;
        ListTag blocks = template.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            if (!block.contains("nbt", Tag.TAG_COMPOUND)) continue;
            ListTag at = block.getList("pos", Tag.TAG_INT);
            BlockPos panel = origin.offset(at.getInt(0), at.getInt(1), at.getInt(2));
            BlockState s = level.getBlockState(panel);
            CompoundTag nbt = block.getCompound("nbt");
            nbt.remove(KEY);   // a panel still unpacking a placed template: its leftovers are not this plot's
            nbt.remove(FACING_KEY);
            if (BuildingWiring.kind(s) != BuildingWiring.Kind.PANEL) continue;
            BlockPos mount = BuildingWiring.mount(s, panel);
            Direction toward = mount == null ? null : Direction.fromDelta(panel.getX() - mount.getX(), panel.getY() - mount.getY(), panel.getZ() - mount.getZ());
            Direction facing = facing(s);
            if (toward == null || facing == null || !box.isInside(mount)) continue;
            LongArrayList out = new LongArrayList();
            for (int c = 1; c <= WiringNode.CIRCUITS; c++) {
                if (!WiringNode.tapped(BuildingWiring.node(level, mount, c), toward)) continue;
                if ((claimed.get(mount.asLong()) & 1 << c) != 0) { joined++; continue; }   // packed with the panel it joins
                LongOpenHashSet seen = new LongOpenHashSet();
                LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
                seen.add(mount.asLong());
                queue.enqueue(mount.asLong());
                while (!queue.isEmpty()) {
                    if (seen.size() > WiringSupply.MAX_NODES) { capped = true; break; }
                    BlockPos cell = BlockPos.of(queue.dequeueLong());
                    long node = BuildingWiring.node(level, cell, c);
                    if (node == 0) continue;
                    long kept = node;
                    for (Direction d : Direction.values()) {
                        BlockPos o = cell.relative(d);
                        if (WiringNode.linked(node, d)) {
                            if (!box.isInside(o)) { kept = WiringNode.link(kept, d, false); clipped++; }
                            else if (seen.add(o.asLong())) queue.enqueue(o.asLong());
                        }
                        if (WiringNode.tapped(node, d) && !box.isInside(o)) { kept = WiringNode.untap(kept, d); clipped++; }
                    }
                    for (Direction h : Direction.Plane.HORIZONTAL) for (boolean up : UP_DOWN) {
                        if (!WiringNode.slopeLinked(node, h, up)) continue;
                        BlockPos o = BuildingWiring.slopeCell(cell, h, up);
                        if (!box.isInside(o)) { kept = WiringNode.slopeLink(kept, h, up, false); clipped++; }
                        else if (seen.add(o.asLong())) queue.enqueue(o.asLong());
                    }
                    claimed.put(cell.asLong(), claimed.get(cell.asLong()) | 1 << c);
                    out.add(cell.subtract(panel).asLong());
                    out.add(kept);
                    nodes++;
                }
            }
            if (out.isEmpty()) continue;
            nbt.putLongArray(KEY, out.toLongArray());
            nbt.putString(FACING_KEY, facing.getName());
            panels++;
        }
        // the plot's wire no panel reached
        int orphans = 0;
        List<BlockPos> cells = new ArrayList<>();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
            WiringChunk w = BuildingWiring.data(level.getChunkSource().getChunkNow(cx, cz), false);
            if (w == null) continue;
            ChunkPos cp = new ChunkPos(cx, cz);
            for (int key : w.keys()) {
                BlockPos cell = WiringChunk.cellOf(cp, key);
                if (!box.isInside(cell) || (claimed.get(cell.asLong()) & 1 << WiringChunk.circuitOf(key)) != 0) continue;
                orphans++;
                if (cells.size() < SAMPLE && !cells.contains(cell)) cells.add(cell);
            }
        }
        return new Report(nodes, panels, orphans, List.copyOf(cells), joined, clipped, capped);
    }

    @Nullable
    private static Direction facing(BlockState s) {
        for (var p : s.getProperties())
            if (p.getName().equals("facing") && s.getValue(p) instanceof Direction d && d.getAxis().isHorizontal()) return d;
        return null;
    }

    // ----------------------------------------------------------------------------------------------------- transform

    /** A direction as the building turns: mirror, then rotate (StructureTemplate's order); up and down stay. */
    public static Direction turn(Direction d, Mirror mirror, Rotation rotation) {
        return d.getAxis().isVertical() ? d : rotation.rotate(mirror.mirror(d));
    }

    /** A node as its building turns: links, taps with their groups and slope links turn; the circuit stays. */
    public static long transform(long node, Mirror mirror, Rotation rotation) {
        if (mirror == Mirror.NONE && rotation == Rotation.NONE) return node;
        long out = node & 15;
        for (Direction d : Direction.values()) {
            Direction t = turn(d, mirror, rotation);
            if (WiringNode.linked(node, d)) out = WiringNode.link(out, t, true);
            if (WiringNode.tapped(node, d)) out = WiringNode.tap(out, t, WiringNode.group(node, d));
        }
        for (Direction h : Direction.Plane.HORIZONTAL) for (boolean up : UP_DOWN)
            if (WiringNode.slopeLinked(node, h, up)) out = WiringNode.slopeLink(out, turn(h, mirror, rotation), up, true);
        return out;
    }

    /** Packed pairs turned in place: each offset about the panel, each node. */
    public static void transform(long[] packed, Mirror mirror, Rotation rotation) {
        if (mirror == Mirror.NONE && rotation == Rotation.NONE) return;
        for (int i = 0; i + 1 < packed.length; i += 2) {
            packed[i] = StructureTemplate.transform(BlockPos.of(packed[i]), mirror, rotation, BlockPos.ZERO).asLong();
            packed[i + 1] = transform(packed[i + 1], mirror, rotation);
        }
    }

    /** A template block entity's packed wiring (its copy for this placement) as the building turns, the packed facing with it. */
    public static void transform(CompoundTag nbt, Mirror mirror, Rotation rotation) {
        if (!nbt.contains(KEY, Tag.TAG_LONG_ARRAY)) return;
        long[] packed = nbt.getLongArray(KEY).clone();
        transform(packed, mirror, rotation);
        nbt.putLongArray(KEY, packed);
        Direction f = Direction.byName(nbt.getString(FACING_KEY));
        if (f != null && f.getAxis().isHorizontal()) nbt.putString(FACING_KEY, turn(f, mirror, rotation).getName());
    }

    /** The rotation that turns one horizontal facing into another (NONE if there is none). */
    public static Rotation between(Direction from, Direction to) {
        for (Rotation r : Rotation.values()) if (r.rotate(from) == to) return r;
        return Rotation.NONE;
    }

    // -------------------------------------------------------------------------------------------------------- unpack

    /** One unpacking pass: what is left (in unloaded chunks; null when nothing is), how many nodes were written and dropped. */
    public record Unpacked(@Nullable long[] left, int written, int dropped) {}

    /**
     * Writes a panel's packed wiring into the chunks as far as they are loaded (design draft 4): a cell that holds wire gets
     * the node, merged with the circuit's node already there; a cell in an unloaded chunk is kept for a later pass; a cell
     * that cannot hold wire (air, glass, water where the building now stands) loses it, and its neighbours' links to it go.
     */
    public static Unpacked unpack(ServerLevel level, BlockPos panel, long[] packed) {
        LongArrayList left = new LongArrayList();
        List<BlockPos> droppedCells = new ArrayList<>();
        LongArrayList droppedNodes = new LongArrayList();
        int written = 0, dropped = 0;
        for (int i = 0; i + 1 < packed.length; i += 2) {
            long node = packed[i + 1];
            int c = WiringNode.circuit(node);
            if (c < 1 || c > WiringNode.CIRCUITS) { dropped++; continue; }   // damaged data
            BlockPos cell = panel.offset(BlockPos.of(packed[i]));
            if (BuildingWiring.chunk(level, cell) == null) { left.add(packed[i]); left.add(node); continue; }
            if (!BuildingWiring.wireable(level, cell)) { droppedCells.add(cell); droppedNodes.add(node); dropped++; continue; }
            long there = BuildingWiring.node(level, cell, c);
            if (BuildingWiring.restore(level, cell, c, there == 0 ? node : WiringNode.merge(there, node))) written++;
        }
        for (int i = 0; i < droppedCells.size(); i++) unlinkAround(level, panel, droppedCells.get(i), droppedNodes.getLong(i), left);
        if (!droppedCells.isEmpty())
            ApocalypseFirstLight.LOGGER.warn("[AFL wiring] template at panel {}: {} cells cannot hold wire here and lost it, first {}",
                    panel.toShortString(), droppedCells.size(), droppedCells.get(0).toShortString());
        return new Unpacked(left.isEmpty() ? null : left.toLongArray(), written, dropped);
    }

    /** The links of a dropped node off its neighbours: written ones in loaded chunks, and those still packed. */
    private static void unlinkAround(ServerLevel level, BlockPos panel, BlockPos cell, long node, LongArrayList left) {
        int c = WiringNode.circuit(node);
        for (Direction d : Direction.values()) {
            if (!WiringNode.linked(node, d)) continue;
            Direction back = d.getOpposite();
            strip(level, panel, cell.relative(d), c, n -> WiringNode.link(n, back, false), left);
        }
        for (Direction h : Direction.Plane.HORIZONTAL) for (boolean up : UP_DOWN) {
            if (!WiringNode.slopeLinked(node, h, up)) continue;
            Direction back = h.getOpposite();
            boolean down = !up;
            strip(level, panel, BuildingWiring.slopeCell(cell, h, up), c, n -> WiringNode.slopeLink(n, back, down, false), left);
        }
    }

    private static void strip(ServerLevel level, BlockPos panel, BlockPos cell, int c, LongUnaryOperator off, LongArrayList left) {
        long b = BuildingWiring.node(level, cell, c);
        if (b != 0 && off.applyAsLong(b) != b) BuildingWiring.restore(level, cell, c, off.applyAsLong(b));
        long offset = cell.subtract(panel).asLong();
        for (int i = 0; i + 1 < left.size(); i += 2)
            if (left.getLong(i) == offset && WiringNode.circuit(left.getLong(i + 1)) == c) left.set(i + 1, off.applyAsLong(left.getLong(i + 1)));
    }
}
