package com.antaurora.apofirstlight.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fuel stains: where sprayed fuel lies (2026-10-05, docs/models/fuel_dispenser_v1.md "滋油"). A stain sits on the face of a
 * block that the jet actually hit (floor, wall, ceiling, any shape), with a size that grows as it is wetted again, and
 * evaporates {@link #life} ticks after its last wetting: gasoline 2 minutes, diesel 10 minutes (user, 2026-10-05). The
 * server's set (fluid/FuelSpills, saved with the level) is the truth; each client keeps a synced copy ({@link #CLIENT},
 * filled by client/ClientFuelStains) to draw and to slip on. Entities standing on a floor stain slip (LivingEntityFuelSlipMixin,
 * gasoline {@link #GASOLINE_FRICTION}, diesel {@link #DIESEL_FRICTION}, ice is 0.98) and get soaked (FuelSoakedEffect).
 * Indexed by chunk (sync) and by the air cell the stain lies in (lookups: an entity checks only the cells round its feet).
 * <p>
 * Each stain knows how far its surface reaches round it ({@link Stain#u0} .. {@link Stain#v1}, in the face plane, see
 * {@link #axisU} / {@link #axisV}; probed by the server when it starts), so its decal stays on the surface.
 * <p>
 * Walls and ceilings (2026-10-05, after the user asked how fuel really behaves there): neither fuel clings. Gasoline is
 * thinner than water and spreads as a film that runs straight down; diesel is thicker and runs slowly, leaving an oily film.
 * So a wall stain's fuel runs down at {@link Stain#runSpeed} all the way to the lower edge of its surface
 * ({@link Stain#runLength}), drips off there while it still flows ({@link Stain#flowTicks} after its last wetting; the
 * server wets the floor below, FuelSpills), and the wet film it leaves dries sooner than a pool on the floor
 * ({@link #WALL_GASOLINE_LIFE}, {@link #WALL_DIESEL_LIFE}).
 */
public final class FuelStainIndex {
    /** Lives in ticks after the last wetting: a pool on the floor (user, 2026-10-05), a film on a wall or a ceiling. */
    public static final int GASOLINE_LIFE = 2400, DIESEL_LIFE = 12000, WALL_GASOLINE_LIFE = 600, WALL_DIESEL_LIFE = 3600;
    public static final float GASOLINE_FRICTION = 0.85F, DIESEL_FRICTION = 0.92F;
    public static final float FIRST = 0.16F, GROW = 0.025F, FLOOR_MAX = 0.55F, WALL_MAX = 0.32F;
    /** How fast fuel runs down a wall (blocks a tick: gasoline 0.6, diesel 0.15 a second), and how long it keeps flowing (dripping) after a wetting. */
    public static final float GASOLINE_RUN_SPEED = 0.03F, DIESEL_RUN_SPEED = 0.0075F;
    public static final int GASOLINE_FLOW_TICKS = 60, DIESEL_FLOW_TICKS = 160;
    /** Surface extents before they are probed (old saves): unbounded enough. */
    public static final float UNPROBED = 2.0F;
    private static final int MAX = 4096;

    /** The synced copy on this client (client side only; empty on a dedicated server). */
    public static final FuelStainIndex CLIENT = new FuelStainIndex();

    public static final class Stain {
        public final long id;
        public final Vec3 pos;
        public final Direction face;
        public final boolean diesel;
        public float size;
        public long wet;
        /** The tick it started (its run runs from then). */
        public long born;
        /** How far the surface reaches from the hit along axisU (u0 .. u1) and axisV (v0 .. v1), blocks. */
        public float u0 = -UNPROBED, u1 = UNPROBED, v0 = -UNPROBED, v1 = UNPROBED;
        /** Server: what the clients last heard (sync throttling). */
        float syncedSize;
        long syncedWet;

        public Stain(long id, Vec3 pos, Direction face, boolean diesel, float size, long wet) {
            this.id = id;
            this.pos = pos;
            this.face = face;
            this.diesel = diesel;
            this.size = size;
            this.wet = wet;
            this.born = wet;
        }

        public boolean wall() {
            return face.getAxis().isHorizontal();
        }

        /** Ticks after its last wetting this stain lives: a floor pool, or a wall / ceiling film (shorter). */
        public int life() {
            return floor() ? (diesel ? DIESEL_LIFE : GASOLINE_LIFE) : (diesel ? WALL_DIESEL_LIFE : WALL_GASOLINE_LIFE);
        }

        public float runSpeed() {
            return diesel ? DIESEL_RUN_SPEED : GASOLINE_RUN_SPEED;
        }

        public int flowTicks() {
            return diesel ? DIESEL_FLOW_TICKS : GASOLINE_FLOW_TICKS;
        }

        /** Still running (and dripping where it reaches an edge): within flowTicks of its last wetting. */
        public boolean flowing(double now) {
            return now - wet <= flowTicks();
        }

        /** The run's length now (0 off walls): down at runSpeed from the hit until the lower edge of its surface. */
        public float runLength(double now) {
            return wall() ? (float) Math.min(v1, runSpeed() * Math.max(0.0, now - born)) : 0.0F;
        }

        /** True when the run has reached the lower edge of its (probed) surface: it drips off there while it flows. */
        public boolean runAtEdge(double now) {
            return wall() && v1 < UNPROBED - 1e-3F && runLength(now) >= v1 - 0.02F;
        }

        public boolean floor() {
            return face == Direction.UP;
        }

        public float maxSize() {
            return face.getAxis().isVertical() ? FLOOR_MAX : WALL_MAX;
        }

        public long chunk() {
            return ChunkPos.asLong(BlockPos.containing(pos));
        }

        public long cell() {
            return cellOf(pos, face);
        }
    }

    /** The face plane axes a stain measures its surface in: floors and ceilings x and z; walls across and down. */
    public static Vec3 axisU(Direction face) {
        return face.getAxis().isVertical() ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0).cross(Vec3.atLowerCornerOf(face.getNormal())).normalize();
    }

    public static Vec3 axisV(Direction face) {
        return face.getAxis().isVertical() ? new Vec3(0, 0, 1) : new Vec3(0, -1, 0);
    }

    /** The air cell a stain at {@code pos} on {@code face} lies in (just off the surface). */
    public static long cellOf(Vec3 pos, Direction face) {
        return BlockPos.containing(pos.add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.01))).asLong();
    }

    private final Map<Long, List<Stain>> byChunk = new HashMap<>(), byCell = new HashMap<>();
    private final Map<Long, Stain> byId = new HashMap<>();

    public void put(Stain stain) {
        Stain old = byId.put(stain.id, stain);
        if (old != null) unlink(old);
        byChunk.computeIfAbsent(stain.chunk(), k -> new ArrayList<>()).add(stain);
        byCell.computeIfAbsent(stain.cell(), k -> new ArrayList<>()).add(stain);
    }

    private void unlink(Stain stain) {
        unlink(byChunk, stain.chunk(), stain);
        unlink(byCell, stain.cell(), stain);
    }

    private static void unlink(Map<Long, List<Stain>> map, long key, Stain stain) {
        List<Stain> list = map.get(key);
        if (list != null && list.remove(stain) && list.isEmpty()) map.remove(key);
    }

    @Nullable
    public Stain remove(long id) {
        Stain stain = byId.remove(id);
        if (stain != null) unlink(stain);
        return stain;
    }

    public void clear() {
        byChunk.clear();
        byCell.clear();
        byId.clear();
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    public int size() {
        return byId.size();
    }

    public Iterable<Stain> all() {
        return byId.values();
    }

    public List<Stain> inChunk(long chunk) {
        return byChunk.getOrDefault(chunk, List.of());
    }

    /** The stains lying in this air cell (BlockPos#asLong of Stain#cell). */
    public List<Stain> inCell(long cell) {
        return byCell.getOrDefault(cell, List.of());
    }

    /** A stain of this fuel on the same face close enough to {@code at} to be the same one, or null. */
    @Nullable
    public Stain near(Vec3 at, Direction face, boolean diesel) {
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        BlockPos cell = BlockPos.of(cellOf(at, face));
        Stain best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            for (Stain s : byCell.getOrDefault(cell.offset(dx, dy, dz).asLong(), List.of())) {
                if (s.face != face || s.diesel != diesel) continue;
                Vec3 off = at.subtract(s.pos);
                double d = off.lengthSqr();
                if (Math.abs(off.dot(n)) < 0.05 && d < s.size * s.size * 0.36 && d < bestDistance) {
                    best = s;
                    bestDistance = d;
                }
            }
        }
        return best;
    }

    /** True if any stain on this face lies within {@code distance} of {@code at} (spreading avoids piling stains up). */
    public boolean crowded(Vec3 at, Direction face, double distance) {
        BlockPos cell = BlockPos.of(cellOf(at, face));
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 1; dy++) {
            for (Stain s : byCell.getOrDefault(cell.offset(dx, dy, dz).asLong(), List.of())) {
                if (s.face == face && s.pos.distanceToSqr(at) < distance * distance) return true;
            }
        }
        return false;
    }

    /** Removes stains dry for longer than their life; each removed one goes to {@code removed}. */
    public void expire(long now, Consumer<Stain> removed) {
        List<Stain> dry = new ArrayList<>();
        for (Stain s : byId.values()) if (now - s.wet > s.life()) dry.add(s);
        for (Stain s : dry) {
            remove(s.id);
            removed.accept(s);
        }
    }

    /** Over the cap: the stains wetted longest ago go first (each to {@code removed}). */
    public void trim(Consumer<Stain> removed) {
        while (byId.size() > MAX) {
            Stain oldest = null;
            for (Stain s : byId.values()) if (oldest == null || s.wet < oldest.wet) oldest = s;
            if (oldest == null) return;
            remove(oldest.id);
            removed.accept(oldest);
        }
    }

    /**
     * The floor stain under this entity's feet, or null: a stain on a block's top face at the height of the feet, whose
     * blob reaches the entity's footprint. Diesel (the more slippery) wins over gasoline. Looks only at the 3 x 3 cells round
     * the feet.
     */
    @Nullable
    public Stain under(Entity entity) {
        if (byId.isEmpty()) return null;
        Vec3 feet = entity.position();
        BlockPos cell = BlockPos.containing(feet.x, feet.y + 0.01, feet.z);
        double reach = entity.getBbWidth() * 0.3;
        Stain found = null;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            for (Stain s : byCell.getOrDefault(cell.offset(dx, 0, dz).asLong(), List.of())) {
                if (!s.floor() || Math.abs(s.pos.y - feet.y) > 0.15) continue;
                double hx = s.pos.x - feet.x, hz = s.pos.z - feet.z, r = s.size / 2 + reach;
                if (hx * hx + hz * hz > r * r) continue;
                if (found == null || s.diesel && !found.diesel) found = s;
            }
        }
        return found;
    }
}
