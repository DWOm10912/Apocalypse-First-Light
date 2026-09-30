package com.antaurora.apofirstlight.meshshape;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One Mesh Shape asset: per discrete state, simplified physical and selection boxes, named interaction regions with a
 * prompt anchor each, and named anchors. Authored once in the mesh's px model frame (block bottom centre, y up, front
 * toward -Z = NORTH); every (state, facing, cell) combination is resolved once at load into VoxelShapes and block-local
 * boxes, so queries never allocate shapes or parse data.
 *
 * <p>Facing uses the Animated Block Mesh Runtime convention (NORTH 0, EAST -90, SOUTH 180, WEST 90 degrees about the
 * block centre), so shapes stay aligned with the rendered mesh. Multi-block assets set {@code cells_y}: physical and
 * selection boxes are sliced per cell; interaction regions and anchors are kept whole (world-space ray tests).
 */
public final class AflMeshShapeProfile {
    public static final String DEFAULT_STATE = "default";
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final int MAX_BOXES = 64, MAX_REGIONS = 16, MAX_CELLS = 4;

    /** A named interaction region resolved to block-local boxes (relative to the queried cell) and its prompt anchor. */
    public record Region(String name, List<AABB> boxes, @Nullable Vec3 anchor) {}

    /** Region under the player's view ray: world hit location and world prompt anchor (the hit point if none). */
    public record RegionHit(String region, Vec3 location, Vec3 anchor, double distance) {}

    /** Everything one block state needs; built once. */
    public record Resolved(VoxelShape physical, VoxelShape selection, VoxelShape interactionSurface,
                           List<Region> regions, boolean selectionOutOfCell) {
        /**
         * Nearest interaction region along the ray, accepted only where it lies on or in front of this block's own
         * selection surface (a region behind the visible geometry is never "aimed at"). Null when the ray misses.
         */
        @Nullable
        public RegionHit raycast(BlockPos pos, Vec3 from, Vec3 to) {
            var surface = interactionSurface.clip(from, to, pos);
            if (surface == null || regions.isEmpty()) return null;
            double limit = from.distanceTo(surface.getLocation()) + 0.03;
            RegionHit best = null;
            for (Region region : regions) for (AABB box : region.boxes()) {
                var hit = box.move(pos).clip(from, to);
                if (hit.isEmpty()) continue;
                double d = from.distanceTo(hit.get());
                if (d <= limit && (best == null || d < best.distance())) {
                    Vec3 anchor = region.anchor() == null ? hit.get() : region.anchor().add(pos.getX(), pos.getY(), pos.getZ());
                    best = new RegionHit(region.name(), hit.get(), anchor, d);
                }
            }
            return best;
        }
    }

    private final List<String> stateNames;
    private final int cells;
    private final Resolved[][][] resolved;   // [state][canonical NORTH/EAST/SOUTH/WEST index][cell]
    private final int pickRadius;

    private AflMeshShapeProfile(List<String> stateNames, int cells, Resolved[][][] resolved) {
        this.stateNames = List.copyOf(stateNames);
        this.cells = cells;
        this.resolved = resolved;
        int radius = 0;
        for (var state : resolved) for (var facing : state) for (var cell : facing) {
            if (cell.selection().isEmpty()) continue;
            AABB b = cell.selection().bounds();
            radius = Math.max(radius, (int)Math.ceil(Math.max(Math.max(-b.minX, b.maxX - 1),
                    Math.max(-b.minZ, b.maxZ - 1))));
        }
        this.pickRadius = radius;
    }

    /** Unknown states fall back to the first state; cells are clamped. Horizontal facings only (others read as NORTH). */
    public Resolved resolve(String state, Direction facing, int cell) {
        int s = Math.max(0, stateNames.indexOf(state));
        int f = AflMeshShapeTransform.index(facing);
        return resolved[s][f][Math.max(0, Math.min(cells - 1, cell))];
    }

    public int cells() {
        return cells;
    }

    /** Maximum horizontal cell overhang, derived from actual selection bounds, including diagonal cells. */
    public int pickRadius() { return pickRadius; }

    /** Full-cube physical / selection, no regions: what a missing or invalid profile degrades to. */
    static AflMeshShapeProfile fallback() {
        var r = new Resolved(Shapes.block(), Shapes.block(), Shapes.block(), List.of(), false);
        var all = new Resolved[1][4][1];
        for (int f = 0; f < 4; f++) all[0][f][0] = r;
        return new AflMeshShapeProfile(List.of(DEFAULT_STATE), 1, all);
    }

    // ---------------- parsing and resolution (load time only) ----------------

    static AflMeshShapeProfile parse(JsonObject root) {
        keys(root, "format_version", "units", "cells_y", "states");
        require(root.has("format_version") && root.get("format_version").getAsInt() == 1, "format_version must be 1");
        require(!root.has("units") || "px".equals(root.get("units").getAsString()), "units must be px");
        int cells = root.has("cells_y") ? root.get("cells_y").getAsInt() : 1;
        require(cells >= 1 && cells <= MAX_CELLS, "cells_y must be 1.." + MAX_CELLS);
        var states = object(root.get("states"), "states");
        require(!states.keySet().isEmpty(), "at least one state");
        List<String> names = new ArrayList<>(states.keySet());
        var resolved = new Resolved[names.size()][4][cells];
        for (int s = 0; s < names.size(); s++) {
            var st = object(states.get(names.get(s)), names.get(s));
            keys(st, "physical", "selection", "interaction", "anchors");
            List<double[]> physical = boxes(st.get("physical"), "physical");
            List<double[]> selection = st.has("selection") ? boxes(st.get("selection"), "selection") : physical;
            Map<String, double[]> anchors = new LinkedHashMap<>();
            if (st.has("anchors")) for (var e : object(st.get("anchors"), "anchors").entrySet()) anchors.put(e.getKey(), point(e.getValue()));
            Map<String, List<double[]>> regionBoxes = new LinkedHashMap<>();
            Map<String, double[]> regionAnchor = new LinkedHashMap<>();
            if (st.has("interaction")) {
                var regions = object(st.get("interaction"), "interaction");
                require(regions.size() <= MAX_REGIONS, "too many interaction regions");
                for (var e : regions.entrySet()) {
                    var r = object(e.getValue(), e.getKey());
                    keys(r, "boxes", "anchor");
                    regionBoxes.put(e.getKey(), boxes(r.get("boxes"), e.getKey()));
                    if (r.has("anchor")) {
                        String a = r.get("anchor").getAsString();
                        require(anchors.containsKey(a), "region " + e.getKey() + " names unknown anchor " + a);
                        regionAnchor.put(e.getKey(), anchors.get(a));
                    }
                }
            }
            for (int f = 0; f < 4; f++) for (int c = 0; c < cells; c++) {
                Direction facing = FACINGS[f];
                VoxelShape phys = shape(physical, facing, c, true), sel = shape(selection, facing, c, true);
                VoxelShape surface = shape(selection, facing, c, false);
                List<Region> regions = new ArrayList<>();
                for (var e : regionBoxes.entrySet()) {
                    List<AABB> list = new ArrayList<>();
                    for (double[] b : e.getValue()) list.add(AflMeshShapeTransform.box(b, c, facing));
                    double[] a = regionAnchor.get(e.getKey());
                    regions.add(new Region(e.getKey(), List.copyOf(list), a == null ? null
                            : AflMeshShapeTransform.point(a[0], a[1], a[2], c, facing)));
                }
                var bounds = sel.isEmpty() ? null : sel.bounds();
                boolean out = bounds != null && (bounds.minX < -1e-6 || bounds.minZ < -1e-6 || bounds.maxX > 1 + 1e-6 || bounds.maxZ > 1 + 1e-6);
                resolved[s][f][c] = new Resolved(phys, sel, surface, List.copyOf(regions), out);
            }
        }
        return new AflMeshShapeProfile(names, cells, resolved);
    }

    /** Physical / selection: boxes sliced to the cell's height; horizontal overhang (open doors) is kept. */
    private static VoxelShape shape(List<double[]> boxes, Direction facing, int cell, boolean slice) {
        double yShift = cell * 16.0;
        VoxelShape out = Shapes.empty();
        for (double[] b : boxes) {
            double y0 = slice ? Math.max(b[1], yShift) : b[1], y1 = slice ? Math.min(b[4], yShift + 16) : b[4];
            if (y1 - y0 < 1e-6) continue;
            AABB box = AflMeshShapeTransform.box(new double[]{b[0], y0, b[2], b[3], y1, b[5]}, cell, facing);
            out = Shapes.or(out, Shapes.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
        }
        return out.optimize();
    }

    private static List<double[]> boxes(JsonElement e, String what) {
        require(e != null && e.isJsonArray(), what + " must be an array of boxes");
        JsonArray a = e.getAsJsonArray();
        require(a.size() <= MAX_BOXES, what + ": too many boxes");
        List<double[]> out = new ArrayList<>();
        for (var b : a) {
            require(b.isJsonArray() && b.getAsJsonArray().size() == 6, what + ": box must be [x0,y0,z0,x1,y1,z1]");
            double[] v = new double[6];
            for (int i = 0; i < 6; i++) {
                v[i] = b.getAsJsonArray().get(i).getAsDouble();
                require(Double.isFinite(v[i]) && Math.abs(v[i]) <= 64, what + ": coordinate out of range");
            }
            require(v[0] < v[3] && v[1] < v[4] && v[2] < v[5], what + ": empty or reversed box");
            out.add(v);
        }
        return out;
    }

    private static double[] point(JsonElement e) {
        require(e != null && e.isJsonArray() && e.getAsJsonArray().size() == 3, "anchor must be [x,y,z]");
        double[] v = new double[3];
        for (int i = 0; i < 3; i++) v[i] = e.getAsJsonArray().get(i).getAsDouble();
        return v;
    }

    private static JsonObject object(JsonElement e, String what) {
        require(e != null && e.isJsonObject(), what + " must be an object");
        return e.getAsJsonObject();
    }

    private static void keys(JsonObject o, String... allowed) {
        require(Set.of(allowed).containsAll(o.keySet()), "unknown fields " + o.keySet());
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }
}
