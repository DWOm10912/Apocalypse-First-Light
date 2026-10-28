package com.antaurora.apofirstlight.terrainsmooth;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The box shapes of the smoothed tops: only VoxelShape / Shapes, no registries, so the offline physics check builds
 * exactly the shapes the game uses. Collision 8 x 8 (SmoothRules.GRID), selection 4 x 4; one shape per corner combination
 * (at most 65 536 each, built once, shared by both sides: shapes are immutable, a racing build stores an equal shape).
 */
public final class SmoothShapes {
    /** Selection boxes: 4 x 4 is close enough to aim (the outline itself is drawn from the slope) and keeps the
     *  break particles (vanilla spawns them per box) near the vanilla count. */
    public static final int SELECT_GRID = 4;
    /** Mud collides this many sixteenths below its top, smoothed or not (vanilla MudBlock: 14 / 16; 2026-10-12). */
    public static final int MUD_SINK = 2;
    private static final VoxelShape[] COLLISION = new VoxelShape[1 << 16], SELECTION = new VoxelShape[1 << 16], SUNK = new VoxelShape[1 << 16];

    private SmoothShapes() {}

    /** grid x grid columns from the floor up to the drawn top at their centres (1/16 steps). */
    public static VoxelShape shape(long g, boolean collision) {
        return collision ? build(g, COLLISION, SmoothRules.GRID, 0) : build(g, SELECTION, SELECT_GRID, 0);
    }

    /** A mud top's collision: the collision columns MUD_SINK sixteenths lower (a column that low has none). */
    public static VoxelShape sunk(long g) {
        return build(g, SUNK, SmoothRules.GRID, MUD_SINK);
    }

    /** R2b: a drawn top's shapes (SmoothTop#top: nine points), by SmoothTop#pack, per cache. */
    private static final Map<VoxelShape[], Map<Long, VoxelShape>> TOPS = new IdentityHashMap<>();

    static {
        for (VoxelShape[] c : new VoxelShape[][]{COLLISION, SELECTION, SUNK}) TOPS.put(c, new ConcurrentHashMap<>());
    }

    /** The same for a drawn top (SmoothTop#top, nine points). */
    public static VoxelShape shape(float[] top, boolean collision) {
        return collision ? build(top, COLLISION, SmoothRules.GRID, 0) : build(top, SELECTION, SELECT_GRID, 0);
    }

    /** #sunk for a drawn top. */
    public static VoxelShape sunk(float[] top) {
        return build(top, SUNK, SmoothRules.GRID, MUD_SINK);
    }

    private static VoxelShape build(float[] top, VoxelShape[] cache, int grid, int sink) {
        Map<Long, VoxelShape> m = TOPS.get(cache);
        if (m.size() > 200_000) m.clear();   // distinct tops near the players are far fewer; bound it anyway
        return m.computeIfAbsent(SmoothTop.pack(top), k -> boxes(SmoothRules.boxes(top, grid), grid, sink));
    }

    /** Tops with a centre (dig R1: a dug hole, a trench, a placed block's mound), by corners and centre bits, per cache. */
    private static final Map<VoxelShape[], Map<Long, VoxelShape>> FANS = new IdentityHashMap<>();

    static {
        for (VoxelShape[] c : new VoxelShape[][]{COLLISION, SELECTION, SUNK}) FANS.put(c, new ConcurrentHashMap<>());
    }

    private static VoxelShape build(long g, VoxelShape[] cache, int grid, int sink) {
        if ((g & SmoothRules.CENTRE) != 0)
            return FANS.get(cache).computeIfAbsent(g & (0xFFFFL | SmoothRules.FAN), k -> boxes(k, grid, sink));
        int key = (int) (g & 0xFFFF);
        VoxelShape s = cache[key];
        if (s != null) return s;
        s = boxes(g, grid, sink);
        cache[key] = s;
        return s;
    }

    private static VoxelShape boxes(long g, int grid, int sink) {
        return boxes(SmoothRules.boxes(g, grid), grid, sink);
    }

    private static VoxelShape boxes(int[] h, int grid, int sink) {
        VoxelShape out = Shapes.empty();
        double w = 1.0 / grid;
        for (int j = 0; j < grid; j++) for (int i = 0; i < grid; i++) {
            int top = h[j * grid + i] - sink;
            if (top <= 0) continue;
            out = Shapes.or(out, Shapes.box(i * w, 0, j * w, (i + 1) * w, top / 16.0, (j + 1) * w));
        }
        return out.optimize();
    }

    /** Lifts above this are not a slope rising under the feet (the box is in the ground some other way): left alone. */
    public static final double MAX_LIFT = 1.0;

    /**
     * How far to lift an entity box whose bottom is inside smoothed tops (positioned shapes), or 0. Smoothed tops do not
     * suffocate, so vanilla's push out of blocks skips them; an edit next to a standing player can raise the slope under the
     * feet (a placed block pins the shared corners), measured up to 0.38 blocks deep (tools/terrain-smooth/PhysicsCheck).
     * Every box in the footprint starts at its block's floor, so lifting to the highest one that cuts in clears them all.
     */
    public static double lift(AABB box, Iterable<VoxelShape> tops) {
        AABB inner = box.deflate(1.0E-7D);
        double lift = 0;
        for (VoxelShape s : tops) for (AABB b : s.toAabbs())
            if (b.intersects(inner)) lift = Math.max(lift, b.maxY - box.minY);
        return lift > MAX_LIFT ? 0 : lift;
    }
}
