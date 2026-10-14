package com.antaurora.apofirstlight.wiring;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.DistributionPanelBlock;
import com.antaurora.apofirstlight.block.GeneratorInletBoxBlock;
import com.antaurora.apofirstlight.block.ServiceMeterBoxBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * Building Wiring V1, the data core (docs/gameplay/building_wiring_v1_design.md 3-4): hidden wires inside a building's
 * blocks, stored per chunk ({@link WiringChunk}). This class answers which cells can hold wire and which blocks are wiring
 * devices, and makes every edit (add, link, tap, cut, clear) so that links stay two-way and the chunks are saved.
 * Server side only. Supplying power through the wires comes in phase 2 (the panel's WIRED mode).
 */
public final class BuildingWiring {
    /** Blocks no wire may pass through although they collide: doors, gates, fences, glass, ice, leaves, AFL windows, furniture. */
    public static final TagKey<Block> NO_WIRING = BlockTags.create(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "no_wiring"));
    /** The building's fixed electrical devices (panel, entry points, fixed lights, outlets): a tap from their mount cell feeds them. */
    public static final TagKey<Block> DEVICES = BlockTags.create(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "wiring_devices"));
    /** Blocks laid as a slope (AFL roofs, stairs): their wire also runs diagonally to the next cell up or down the slope. */
    public static final TagKey<Block> SLOPES = BlockTags.create(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "wiring_slopes"));
    private static final boolean[] UP_DOWN = {true, false};

    /** What a device is to the wiring: the panel (one tap per circuit), a service entry (inlet box, meter box: the feeder), a load. */
    public enum Kind { PANEL, ENTRY, LOAD }

    private BuildingWiring() {}

    // ------------------------------------------------------------------------------------------------------- cells

    public static boolean wireable(BlockGetter level, BlockPos pos) {
        return wireable(level, pos, level.getBlockState(pos));
    }

    /**
     * A cell can hold wire (design 4): not air, no fluid (waterlogged blocks neither), no block entity (machines, containers,
     * devices), not a wiring device, not tagged no_wiring, and something to collide with (slabs, stairs and roofs count).
     * Natural ground counts too, for wire buried to an outbuilding.
     */
    public static boolean wireable(BlockGetter level, BlockPos pos, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity() || s.is(NO_WIRING) || s.is(DEVICES)) return false;
        return !s.getCollisionShape(level, pos).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------------ slopes

    /** The cell one toward h and one up (or down): the next cell along a slope. */
    public static BlockPos slopeCell(BlockPos cell, Direction h, boolean up) {
        return cell.relative(h).relative(up ? Direction.UP : Direction.DOWN);
    }

    /**
     * A wire may run straight from cell to slopeCell(cell, h, up) (design 4.1, 2026-10-13: a 1:2 roof's cells and a flight
     * of stairs step up one cell and across one, their corners are air): both are slope blocks laid along h (their facing
     * on h's axis), so the step follows the slope and does not jump between two slopes side by side.
     */
    public static boolean slope(BlockGetter level, BlockPos cell, Direction h, boolean up) {
        return h.getAxis().isHorizontal() && along(level.getBlockState(cell), h) && along(level.getBlockState(slopeCell(cell, h, up)), h);
    }

    private static boolean along(BlockState s, Direction h) {
        if (!s.is(SLOPES)) return false;
        for (Property<?> p : s.getProperties())
            if (p.getName().equals("facing") && s.getValue(p) instanceof Direction f) return f.getAxis() == h.getAxis();
        return false;
    }

    // ------------------------------------------------------------------------------------------------------ devices

    @Nullable
    public static Kind kind(BlockState s) {
        if (!s.is(DEVICES)) return null;
        Block b = s.getBlock();
        if (b instanceof DistributionPanelBlock) return Kind.PANEL;
        if (b instanceof GeneratorInletBoxBlock || b instanceof ServiceMeterBoxBlock) return Kind.ENTRY;
        return Kind.LOAD;
    }

    /**
     * The cell a device is mounted on, where its tap comes from (design 4): the block behind its "facing" (a wall fixture's
     * wall, a ceiling fixture facing down: the ceiling); a device without a facing hangs from the ceiling.
     */
    @Nullable
    public static BlockPos mount(BlockState s, BlockPos device) {
        if (!s.is(DEVICES)) return null;
        for (Property<?> p : s.getProperties())
            if (p.getName().equals("facing") && s.getValue(p) instanceof Direction d) return device.relative(d.getOpposite());
        return device.above();
    }

    /** The feeder taps only the panel and the service entries; circuits 1-4 the panel and the loads. */
    public static boolean accepts(Kind kind, int circuit) {
        return kind == Kind.PANEL || (kind == Kind.ENTRY) == (circuit == WiringNode.FEEDER);
    }

    // ------------------------------------------------------------------------------------------------- chunk access

    @Nullable
    static LevelChunk chunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Nullable
    static WiringChunk data(@Nullable LevelChunk chunk, boolean create) {
        if (chunk == null) return null;
        WiringChunkHolder h = (WiringChunkHolder) chunk;
        WiringChunk w = h.afl$wiring();
        if (w == null && create) h.afl$setWiring(w = new WiringChunk());
        return w;
    }

    /** The circuit's node in the cell, 0 if none (or its chunk is not loaded). */
    public static long node(ServerLevel level, BlockPos pos, int circuit) {
        WiringChunk w = data(chunk(level, pos), false);
        return w == null ? 0 : w.get(pos, circuit);
    }

    /** Some circuit has a node in the cell. */
    public static boolean any(ServerLevel level, BlockPos pos) {
        WiringChunk w = data(chunk(level, pos), false);
        return w != null && w.any(pos);
    }

    /** The circuits with a node in the cell, ascending. */
    public static int[] circuits(ServerLevel level, BlockPos pos) {
        WiringChunk w = data(chunk(level, pos), false);
        if (w == null || !w.any(pos)) return new int[0];
        int[] out = new int[WiringNode.CIRCUITS];
        int n = 0;
        for (int c = 1; c <= WiringNode.CIRCUITS; c++) if (w.get(pos, c) != 0) out[n++] = c;
        return java.util.Arrays.copyOf(out, n);
    }

    /** Nodes in a loaded chunk (0 when it has none or is not loaded). */
    public static int count(ServerLevel level, net.minecraft.world.level.ChunkPos cp) {
        WiringChunk w = data(level.getChunkSource().getChunkNow(cp.x, cp.z), false);
        return w == null ? 0 : w.size();
    }

    /** Writes (node != 0) or removes (0) one node; the chunk must be loaded. */
    private static boolean write(ServerLevel level, BlockPos pos, int circuit, long node) {
        LevelChunk c = chunk(level, pos);
        WiringChunk w = data(c, node != 0);
        if (w == null) return false;
        if (node == 0) {
            if (w.remove(pos, circuit) == 0) return false;
        } else if (w.get(pos, circuit) != node) w.put(pos, circuit, node);
        else return true;
        c.setUnsaved(true);
        return true;
    }

    // -------------------------------------------------------------------------------------------------------- edits

    /** Adds the circuit's node in a wireable cell of a loaded chunk; true when it is new (that is one metre of wire). */
    public static boolean add(ServerLevel level, BlockPos cell, int circuit) {
        if (node(level, cell, circuit) != 0 || chunk(level, cell) == null || !wireable(level, cell)) return false;
        return write(level, cell.immutable(), circuit, WiringNode.of(circuit));
    }

    /** Links the circuit's nodes in cell and its neighbour toward d, both ways; false if either is missing. */
    public static boolean link(ServerLevel level, BlockPos cell, Direction d, int circuit) {
        BlockPos o = cell.relative(d);
        long a = node(level, cell, circuit), b = node(level, o, circuit);
        if (a == 0 || b == 0) return false;
        write(level, cell, circuit, WiringNode.link(a, d, true));
        write(level, o, circuit, WiringNode.link(b, d.getOpposite(), true));
        return true;
    }

    /**
     * Links the circuit's nodes in cell and the next cell along a slope (toward h, up or down), both ways: false if either
     * is missing, the two do not form a slope, or the circuit already turns the corner through one of the two corner cells
     * (straight links do that, no second way round).
     */
    public static boolean slopeLink(ServerLevel level, BlockPos cell, Direction h, boolean up, int circuit) {
        BlockPos o = slopeCell(cell, h, up);
        long a = node(level, cell, circuit), b = node(level, o, circuit);
        if (a == 0 || b == 0 || !slope(level, cell, h, up)) return false;
        if (node(level, cell.relative(h), circuit) != 0 || node(level, cell.relative(up ? Direction.UP : Direction.DOWN), circuit) != 0) return false;
        write(level, cell, circuit, WiringNode.slopeLink(a, h, up, true));
        write(level, o, circuit, WiringNode.slopeLink(b, h.getOpposite(), !up, true));
        return true;
    }

    /**
     * Links the cell's node to every neighbour holding the same circuit (wires of one circuit that touch join, as in
     * Terraria), and along a slope to the next slope cell up and down.
     */
    public static int linkAround(ServerLevel level, BlockPos cell, int circuit) {
        int n = 0;
        for (Direction d : Direction.values()) if (link(level, cell, d, circuit)) n++;
        for (Direction h : Direction.Plane.HORIZONTAL) for (boolean up : UP_DOWN) if (slopeLink(level, cell, h, up, circuit)) n++;
        return n;
    }

    /**
     * Drops the slope links of the cell's nodes that no longer follow a slope (a roof or stair turned or replaced by a block
     * that still holds wire), on both ends. Returns how many.
     */
    public static int checkSlopes(ServerLevel level, BlockPos cell) {
        if (!any(level, cell)) return 0;
        int n = 0;
        for (int c = 1; c <= WiringNode.CIRCUITS; c++) {
            long a = node(level, cell, c);
            if (WiringNode.slopes(a) == 0) continue;
            for (Direction h : Direction.Plane.HORIZONTAL) for (boolean up : UP_DOWN) {
                BlockPos o = slopeCell(cell, h, up);
                // the other end's chunk not loaded: checked when it is (never load a chunk for this)
                if (!WiringNode.slopeLinked(a, h, up) || chunk(level, o) == null || slope(level, cell, h, up)) continue;
                a = WiringNode.slopeLink(a, h, up, false);
                write(level, cell, c, a);
                long b = node(level, o, c);
                if (WiringNode.slopeLinked(b, h.getOpposite(), !up)) write(level, o, c, WiringNode.slopeLink(b, h.getOpposite(), !up, false));
                n++;
            }
        }
        return n;
    }

    /**
     * Joins two ends of the circuit across an edge (design 7.3, 2026-10-13: a wire up a wall and one along the floor, laid
     * with separate clicks, meet only diagonally): when the circuit's node in cell has at most one wire leaving it and an end
     * like it lies one step away on each of two axes, neither corner cell between them holds the circuit and they are not
     * on one slope, the circuit goes into the first corner cell that can hold wire (one more metre) and joins both. Returns
     * that corner cell, or null.
     */
    @Nullable
    public static BlockPos cornerJoin(ServerLevel level, BlockPos cell, int circuit) {
        long a = node(level, cell, circuit);
        if (a == 0 || WiringNode.linkCount(a) > 1) return null;
        for (Direction d1 : Direction.values()) for (Direction d2 : Direction.values()) {
            if (d2.getAxis().ordinal() <= d1.getAxis().ordinal()) continue;   // each pair of axes once (X-Y, X-Z, Y-Z)
            BlockPos o = cell.relative(d1).relative(d2), k1 = cell.relative(d1), k2 = cell.relative(d2);
            long b = node(level, o, circuit);
            if (b == 0 || WiringNode.linkCount(b) > 1 || node(level, k1, circuit) != 0 || node(level, k2, circuit) != 0) continue;
            Direction v = d1.getAxis() == Direction.Axis.Y ? d1 : d2.getAxis() == Direction.Axis.Y ? d2 : null, h = v == d1 ? d2 : d1;
            if (v != null && WiringNode.slopeLinked(a, h, v == Direction.UP)) continue;
            for (BlockPos k : new BlockPos[]{k1, k2}) {
                if (chunk(level, k) == null || !wireable(level, k) || !add(level, k, circuit)) continue;
                linkAround(level, k, circuit);
                autoTap(level, k, circuit);
                return k.immutable();
            }
        }
        return null;
    }

    /** The device at devicePos has a tap (a panel: from this circuit's node; anything else: from any node). */
    public static boolean tapped(ServerLevel level, BlockPos devicePos, BlockState device, int circuit) {
        BlockPos m = mount(device, devicePos);
        Kind kind = kind(device);
        if (m == null || kind == null) return false;
        Direction toward = Direction.fromDelta(devicePos.getX() - m.getX(), devicePos.getY() - m.getY(), devicePos.getZ() - m.getZ());
        if (toward == null) return false;
        if (kind == Kind.PANEL) return WiringNode.tapped(node(level, m, circuit), toward);
        for (int c = 1; c <= WiringNode.CIRCUITS; c++) if (WiringNode.tapped(node(level, m, c), toward)) return true;
        return false;
    }

    /**
     * Taps every device mounted on this cell that the circuit may feed and that has no tap yet (one tap per device; the
     * panel one per circuit), in group 0 (always on). Returns how many.
     */
    public static int autoTap(ServerLevel level, BlockPos cell, int circuit) {
        long node = node(level, cell, circuit);
        if (node == 0) return 0;
        int n = 0;
        for (Direction d : Direction.values()) {
            BlockPos dev = cell.relative(d);
            if (chunk(level, dev) == null) continue;
            BlockState s = level.getBlockState(dev);
            Kind kind = kind(s);
            if (kind == null || !accepts(kind, circuit) || !cell.equals(mount(s, dev)) || tapped(level, dev, s, circuit)) continue;
            node = WiringNode.tap(node, d, WiringNode.ALWAYS);
            n++;
        }
        if (n > 0) write(level, cell, circuit, node);
        return n;
    }

    /** Removes every tap toward the device (its mount cell's taps; the wire stays). Returns how many. */
    public static int untap(ServerLevel level, BlockPos devicePos, BlockState device) {
        BlockPos m = mount(device, devicePos);
        if (m == null) return 0;
        Direction toward = Direction.fromDelta(devicePos.getX() - m.getX(), devicePos.getY() - m.getY(), devicePos.getZ() - m.getZ());
        if (toward == null) return 0;
        int n = 0;
        for (int c = 1; c <= WiringNode.CIRCUITS; c++) {
            long node = node(level, m, c);
            if (!WiringNode.tapped(node, toward)) continue;
            write(level, m, c, WiringNode.untap(node, toward));
            n++;
        }
        return n;
    }

    /** Removes the circuit's node in the cell and every link to it (straight and along a slope); 1 if there was one (one metre of wire). */
    public static int cut(ServerLevel level, BlockPos cell, int circuit) {
        long node = node(level, cell, circuit);
        if (node == 0) return 0;
        write(level, cell, circuit, 0);
        for (Direction d : Direction.values()) {
            BlockPos o = cell.relative(d);
            long b = node(level, o, circuit);
            if (WiringNode.linked(b, d.getOpposite())) write(level, o, circuit, WiringNode.link(b, d.getOpposite(), false));
        }
        for (Direction h : Direction.Plane.HORIZONTAL) for (boolean up : UP_DOWN) {
            BlockPos o = slopeCell(cell, h, up);
            long b = node(level, o, circuit);
            if (WiringNode.slopeLinked(b, h.getOpposite(), !up)) write(level, o, circuit, WiringNode.slopeLink(b, h.getOpposite(), !up, false));
        }
        return 1;
    }

    /** Removes every node in the cell and the links to them; how many there were. */
    public static int clear(ServerLevel level, BlockPos cell) {
        if (!any(level, cell)) return 0;
        int n = 0;
        for (int c = 1; c <= WiringNode.CIRCUITS; c++) n += cut(level, cell, c);
        return n;
    }

    /** Sets one tap's group (light groups and photocell are phase 3; the tap must exist). */
    public static boolean setGroup(ServerLevel level, BlockPos cell, int circuit, Direction d, int group) {
        long node = node(level, cell, circuit);
        if (!WiringNode.tapped(node, d)) return false;
        return write(level, cell, circuit, WiringNode.tap(node, d, group));
    }
}
