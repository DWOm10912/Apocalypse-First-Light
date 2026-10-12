package com.antaurora.apofirstlight.terrainsmooth;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline walking check of the terrain smoothing P1 collision (docs/worldgen/terrain_smooth_p1_interaction.md): the
 * real Minecraft 1.20.1 VoxelShape code (Shapes.collide, the shapes SmoothShapes builds for the game) and line-for-line
 * copies of the vanilla code around it that needs a Level (decompiled Forge 47.4.22 sources):
 * BlockCollisions#computeNext (cursor, border layer only for large shapes, the suffocation filter),
 * Entity#collide (step up 0.6) and #collideWithShapes, LocalPlayer#moveTowardsClosestSpace / #suffocatesAt
 * (the push out of blocks, every tick at four corners), LivingEntity ground movement (walk speed 0.1, friction 0.6).
 * <p>
 * A player walks or sprints uphill, downhill and across synthetic terrain (SmoothCheck.terrain) in mode B with the
 * smoothed tops either suffocating (P1 as first tested: the per-state cache says full cube) or not (the fix).
 * Run by tools/terrain-smooth/physics.mjs.
 */
final class PhysicsCheck {
    static int failures;

    static void require(boolean ok, String what) { if (!ok) { failures++; System.out.println("FAIL " + what); } }

    /** Blocks as the shape rules read them, plus what the walking check needs: the ground top of a column and water. */
    interface Terrain extends SmoothRules.Cells {
        /** y of the lowest open cell above the column's ground, or Integer.MIN_VALUE outside the data */
        int top(int x, int z);

        /** water surface above the floor of cell (x, y, z), 0..1, or 0 without water (FluidState#getHeight) */
        default double water(int x, int y, int z) { return 0; }

        /** natural ground block of a cell (GROUND, export-area.mjs): 1 grass block, 2 dirt, 3 coarse dirt, 4 sand, 5 gravel, 6 stone, 7 podzol, 8 mud, 9 clay */
        default int ground(int x, int y, int z) { return 1; }
    }

    /** The synthetic columns of SmoothCheck.World, read live (tests edit them). */
    static Terrain synth(SmoothCheck.World w) {
        return new Terrain() {
            @Override public int cell(int x, int y, int z) { return w.cell(x, y, z); }
            @Override public int surface(int x, int z) { return w.surface(x, z); }
            @Override public int top(int x, int z) { int k = w.i(x, z); return k < 0 ? Integer.MIN_VALUE : w.h[k]; }
        };
    }

    /** The world as the game's collision code sees it. */
    static final class Ground {
        final Terrain t;
        final int mode;
        final int[] area;
        final boolean smoothSuffocates;
        /** geometry per position for static terrain (the real-world check); null: computed every time (tests edit blocks) */
        it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap cache;

        Ground(SmoothCheck.World w, int mode, int[] area, boolean smoothSuffocates) { this(synth(w), mode, area, smoothSuffocates); }

        Ground(Terrain t, int mode, int[] area, boolean smoothSuffocates) {
            this.t = t;
            this.mode = mode;
            this.area = area;
            this.smoothSuffocates = smoothSuffocates;
        }

        boolean solid(int x, int y, int z) {
            int c = t.cell(x, y, z);
            return c == SmoothRules.NATURAL || c == SmoothRules.OTHER || c == SmoothRules.TREE;
        }

        long geometry(int x, int y, int z) {
            if (t.cell(x, y, z) != SmoothRules.NATURAL) return -1;
            if (cache == null) return SmoothRules.geometry(t, mode, x, y, z);
            long key = net.minecraft.core.BlockPos.asLong(x, y, z), v = cache.get(key);
            if (v != Long.MIN_VALUE) return v;
            v = SmoothRules.geometry(t, mode, x, y, z);
            cache.put(key, v);
            return v;
        }

        /** BlockStateBase#getCollisionShape with the P1 hook (SmoothWorld#hook). */
        VoxelShape collision(int x, int y, int z) {
            if (!solid(x, y, z)) return Shapes.empty();
            long g = geometry(x, y, z);
            if (t.ground(x, y, z) == MUD) return g < 0 ? MUD_BLOCK : SmoothShapes.sunk(g);
            return g < 0 ? Shapes.block() : SmoothShapes.shape(g, true);
        }

        /** BlockCollisions, Terrain Smooth V1 (TerrainSmooth#risesIntoBox): the ground blocks count as large in the layer under the box only. */
        boolean large(int x, int y, int z, int bottomLayer) {
            return mode != SmoothRules.MODE_OFF && t.cell(x, y, z) == SmoothRules.NATURAL && y == bottomLayer;
        }

        /** BlockStateBase#isSuffocating: blocks motion and a full cube (cached per state), or false on a smoothed top (fix). */
        boolean suffocating(int x, int y, int z) {
            if (!solid(x, y, z)) return false;
            return smoothSuffocates || geometry(x, y, z) < 0;
        }

        /** BlockCollisions#computeNext over box, collecting every shape (onlySuffocating: Level#collidesWithSuffocatingBlock). */
        List<VoxelShape> collisions(AABB box, boolean onlySuffocating) {
            List<VoxelShape> out = new ArrayList<>();
            VoxelShape entityShape = Shapes.create(box);
            int x0 = Mth.floor(box.minX - 1.0E-7D) - 1, x1 = Mth.floor(box.maxX + 1.0E-7D) + 1;
            int y0 = Mth.floor(box.minY - 1.0E-7D) - 1, y1 = Mth.floor(box.maxY + 1.0E-7D) + 1;
            int z0 = Mth.floor(box.minZ - 1.0E-7D) - 1, z1 = Mth.floor(box.maxZ + 1.0E-7D) + 1;
            for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                int type = (x == x0 || x == x1 ? 1 : 0) + (y == y0 || y == y1 ? 1 : 0) + (z == z0 || z == z1 ? 1 : 0);
                if (type == 3) continue;
                if (onlySuffocating && !suffocating(x, y, z) || type == 1 && !large(x, y, z, y0) || type == 2) continue;   // type 2: moving pistons only
                VoxelShape shape = collision(x, y, z);
                if (shape == Shapes.block()) {
                    if (!box.intersects(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D)) continue;
                    out.add(shape.move(x, y, z));
                    if (onlySuffocating) return out;
                    continue;
                }
                VoxelShape moved = shape.move(x, y, z);
                if (moved.isEmpty() || !Shapes.joinIsNotEmpty(moved, entityShape, BooleanOp.AND)) continue;
                out.add(moved);
                if (onlySuffocating) return out;
            }
            return out;
        }
    }

    /** The local player: position (feet centre), motion, the vanilla movement steps that matter on the ground. */
    static final class Player {
        static final double WIDTH = 0.6, HEIGHT = 1.8, STEP = 0.6;
        final Ground g;
        double x, y, z, dx, dy, dz;
        boolean onGround;
        int pushes;

        Player(Ground g, double x, double y, double z) { this.g = g; this.x = x; this.y = y; this.z = z; }

        AABB box() { return new AABB(x - WIDTH / 2, y, z - WIDTH / 2, x + WIDTH / 2, y + HEIGHT, z + WIDTH / 2); }

        // ------------------------------------------------ LocalPlayer#moveTowardsClosestSpace / #suffocatesAt
        boolean suffocatesAt(int bx, int bz) {
            AABB b = box();
            AABB cell = new AABB(bx, b.minY, bz, bx + 1.0D, b.maxY, bz + 1.0D).deflate(1.0E-7D);
            return !g.collisions(cell, true).isEmpty();
        }

        void moveTowardsClosestSpace(double px, double pz) {
            int bx = Mth.floor(px), bz = Mth.floor(pz);
            if (!suffocatesAt(bx, bz)) return;
            double d0 = px - bx, d1 = pz - bz;
            Direction direction = null;
            double best = Double.MAX_VALUE;
            for (Direction d : new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH}) {
                double d3 = d.getAxis().choose(d0, 0.0D, d1);
                double d4 = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D - d3 : d3;
                if (d4 < best && !suffocatesAt(bx + d.getStepX(), bz + d.getStepZ())) {
                    best = d4;
                    direction = d;
                }
            }
            if (direction == null) return;
            pushes++;
            if (direction.getAxis() == Direction.Axis.X) dx = 0.1D * direction.getStepX();
            else dz = 0.1D * direction.getStepZ();
        }

        // ------------------------------------------------ Entity#collide / #collideBoundingBox / #collideWithShapes
        Vec3 collideBoundingBox(Vec3 v, AABB box) {
            return collideWithShapes(v, box, g.collisions(box.expandTowards(v), false));
        }

        static Vec3 collideWithShapes(Vec3 v, AABB box, List<VoxelShape> shapes) {
            if (shapes.isEmpty()) return v;
            double d0 = v.x, d1 = v.y, d2 = v.z;
            if (d1 != 0.0D) {
                d1 = Shapes.collide(Direction.Axis.Y, box, shapes, d1);
                if (d1 != 0.0D) box = box.move(0.0D, d1, 0.0D);
            }
            boolean zFirst = Math.abs(d0) < Math.abs(d2);
            if (zFirst && d2 != 0.0D) {
                d2 = Shapes.collide(Direction.Axis.Z, box, shapes, d2);
                if (d2 != 0.0D) box = box.move(0.0D, 0.0D, d2);
            }
            if (d0 != 0.0D) {
                d0 = Shapes.collide(Direction.Axis.X, box, shapes, d0);
                if (!zFirst && d0 != 0.0D) box = box.move(d0, 0.0D, 0.0D);
            }
            if (!zFirst && d2 != 0.0D) d2 = Shapes.collide(Direction.Axis.Z, box, shapes, d2);
            return new Vec3(d0, d1, d2);
        }

        Vec3 collide(Vec3 v) {
            AABB box = box();
            Vec3 out = v.lengthSqr() == 0.0D ? v : collideBoundingBox(v, box);
            boolean hx = v.x != out.x, hy = v.y != out.y, hz = v.z != out.z;
            boolean grounded = onGround || hy && v.y < 0.0D;
            if (grounded && (hx || hz)) {
                Vec3 a = collideBoundingBox(new Vec3(v.x, STEP, v.z), box);
                Vec3 up = collideBoundingBox(new Vec3(0.0D, STEP, 0.0D), box.expandTowards(v.x, 0.0D, v.z));
                if (up.y < STEP) {
                    Vec3 b = collideBoundingBox(new Vec3(v.x, 0.0D, v.z), box.move(up)).add(up);
                    if (b.horizontalDistanceSqr() > a.horizontalDistanceSqr()) a = b;
                }
                if (a.horizontalDistanceSqr() > out.horizontalDistanceSqr())
                    return a.add(collideBoundingBox(new Vec3(0.0D, -a.y + v.y, 0.0D), box.move(a)));
            }
            return out;
        }

        /** Entity#move(SELF): position, collision flags, motion zeroed on the blocked axes. */
        void move(Vec3 v) {
            Vec3 m = collide(v);
            x += m.x; y += m.y; z += m.z;
            boolean hx = !Mth.equal(v.x, m.x), hz = !Mth.equal(v.z, m.z), vertical = v.y != m.y;
            onGround = vertical && v.y < 0.0D;
            horizontal = hx || hz;
            if (hx) dx = 0;
            if (hz) dz = 0;
            if (vertical) dy = 0;   // Block#updateEntityAfterFallOn
        }

        boolean horizontal;
        int waterTicks;
        double deepest;

        /** Entity#updateFluidHeightAndDoFluidPushing (Forge 47): any cell in the box with a water surface at or above the feet. */
        double waterHeight() {
            AABB b = box().deflate(0.001D);
            double h = 0;
            boolean in = false;
            for (int bx = Mth.floor(b.minX); bx < Mth.ceil(b.maxX); bx++)
                for (int by = Mth.floor(b.minY); by < Mth.ceil(b.maxY); by++)
                    for (int bz = Mth.floor(b.minZ); bz < Mth.ceil(b.maxZ); bz++) {
                        double surface = g.t.water(bx, by, bz);
                        if (surface <= 0) continue;
                        double top = by + surface;
                        if (top >= b.minY) { in = true; h = Math.max(h, top - b.minY); }
                    }
            return in ? Math.max(h, 1e-9) : 0;
        }

        /** Entity#isFree: no collision and no liquid in the box moved by (mx, my, mz). */
        boolean free(double mx, double my, double mz) {
            AABB b = box().move(mx, my, mz);
            if (!g.collisions(b, false).isEmpty()) return false;
            for (int bx = Mth.floor(b.minX); bx < Mth.ceil(b.maxX); bx++)
                for (int by = Mth.floor(b.minY); by < Mth.ceil(b.maxY); by++)
                    for (int bz = Mth.floor(b.minZ); bz < Mth.ceil(b.maxZ); bz++)
                        if (g.t.water(bx, by, bz) > 0) return false;
            return true;
        }

        /** One client tick: water state (Entity#baseTick), push out (LocalPlayer#aiStep), then walking (LivingEntity#aiStep, #travel). */
        void tick(double yawX, double yawZ, boolean sprint, boolean pushOut) {
            double wet = waterHeight();
            if (wet > 0) { waterTicks++; deepest = Math.max(deepest, wet); }
            if (pushOut) {
                double w = WIDTH * 0.35D;
                moveTowardsClosestSpace(x - w, z + w);
                moveTowardsClosestSpace(x - w, z - w);
                moveTowardsClosestSpace(x + w, z - w);
                moveTowardsClosestSpace(x + w, z + w);
            }
            if (Math.abs(dx) < 0.003D) dx = 0;
            if (Math.abs(dy) < 0.003D) dy = 0;
            if (Math.abs(dz) < 0.003D) dz = 0;
            if (wet > 0) {   // LivingEntity#travel, in water (no fluid current here)
                double y0 = y;
                boolean falling = dy <= 0.0D;
                double slow = sprint ? 0.9 : 0.8;
                dx += yawX * 0.98 * 0.02;
                dz += yawZ * 0.98 * 0.02;
                move(new Vec3(dx, dy, dz));
                dx *= slow;
                dy *= 0.8;
                dz *= slow;
                dy = falling && Math.abs(dy - 0.005D) >= 0.003D && Math.abs(dy - 0.08 / 16.0D) < 0.003D ? -0.003D : dy - 0.08 / 16.0D;
                if (horizontal && free(dx, dy + 0.6 - y + y0, dz)) dy = 0.3;
                if (lift) liftOut();
                return;
            }
            double friction = onGround ? 0.6 : 1.0;
            double speed = onGround ? (sprint ? 0.13 : 0.1) * (0.21600002F / (float) (friction * friction * friction)) : (sprint ? 0.026 : 0.02);
            double input = 0.98;   // forward impulse 1, times 0.98 in LivingEntity#aiStep
            dx += yawX * input * speed;
            dz += yawZ * input * speed;
            move(new Vec3(dx, dy, dz));
            double f1 = friction * 0.91;   // #travel takes the friction from before the move
            dy = (dy - 0.08) * 0.98;
            dx *= f1;
            dz *= f1;
            if (lift) liftOut();
        }

        boolean lift;
        int lifts;

        /** SmoothPrototype#playerTick: feet cut into smoothed tops: lift onto them (SmoothWorld#lift, SmoothShapes#lift). */
        void liftOut() {
            AABB box = box();
            List<VoxelShape> tops = new ArrayList<>();
            int feet = Mth.floor(box.minY);
            for (int bx = Mth.floor(box.minX); bx <= Mth.floor(box.maxX); bx++)
                for (int bz = Mth.floor(box.minZ); bz <= Mth.floor(box.maxZ); bz++)
                    for (int by = feet - 2; by <= feet + 1; by++) {
                        long geo = g.mode == SmoothRules.MODE_OFF ? -1 : g.geometry(bx, by, bz);
                        if (geo >= 0) tops.add((g.t.ground(bx, by, bz) == MUD ? SmoothShapes.sunk(geo) : SmoothShapes.shape(geo, true)).move(bx, by, bz));   // TerrainSmooth#collision
                    }
            double up = tops.isEmpty() ? 0 : SmoothShapes.lift(box, tops);
            if (up <= 0 || !g.collisions(box.move(0, up, 0).deflate(1.0E-7D), false).isEmpty()) return;
            y += up;
            lifts++;
        }

        /** Drop onto the ground from a little above (settles onGround). */
        void settle() {
            for (int i = 0; i < 40 && !onGround; i++) tick(0, 0, false, false);
        }
    }

    // ---------------------------------------------------------------------------------------------- terrains

    /** A straight hill along x: one-block steps every `run` blocks, rising toward +x, flat beyond. */
    static SmoothCheck.World ramp(int run, int steps) {
        SmoothCheck.World t = new SmoothCheck.World(-20, -20, 40 + run * steps);
        for (int z = t.z0; z < t.z0 + t.w; z++) for (int x = t.x0; x < t.x0 + t.w; x++)
            t.h[t.i(x, z)] = 70 + Math.max(0, Math.min(steps, Math.floorDiv(x, run) + 1));
        return t;
    }

    static int[] areaOf(SmoothCheck.World t) { return new int[]{t.x0 + 2, t.z0 + 2, t.w - 4}; }

    /** inside the walked square (walks stop at its border) */
    static boolean inside(int minX, int minZ, int size, int x, int z) { return x >= minX && x < minX + size && z >= minZ && z < minZ + size; }

    /** Highest collision top under the player's footprint near height y (the ground they should stand on). */
    static double surfaceAt(Ground g, double x, double z, double y) {
        AABB foot = new AABB(x - Player.WIDTH / 2, y - 2, z - Player.WIDTH / 2, x + Player.WIDTH / 2, y + 1, z + Player.WIDTH / 2);
        double best = Double.NEGATIVE_INFINITY;
        for (VoxelShape s : g.collisions(foot, false)) for (AABB b : s.toAabbs())
            if (b.intersects(foot) && b.maxY <= y + 1) best = Math.max(best, b.maxY);
        return best;
    }

    record Walk(double rise, double distance, int pushes, int stalls, double jitter, int lifts, int waterTicks, double deepest) {}

    /** Walk ticks in direction (ux, uz) from (sx, sz); stalls: 10-tick windows with < 0.5 block progress. */
    static Walk walk(Ground g, double sx, double sz, double ux, double uz, int ticks, boolean sprint, boolean pushOut) {
        int top = g.t.top(Mth.floor(sx), Mth.floor(sz));
        Player p = new Player(g, sx, top + 1.5, sz);
        p.lift = !g.smoothSuffocates;   // the fixed build lifts out of raised slopes too
        p.settle();
        double y0 = p.y, along0 = p.x * ux + p.z * uz, last = along0, jitter = 0;
        int stalls = 0;
        double prevSide = p.x * -uz + p.z * ux;
        for (int i = 1; i <= ticks; i++) {
            p.tick(ux, uz, sprint, pushOut);
            double side = p.x * -uz + p.z * ux;
            jitter = Math.max(jitter, Math.abs(side - prevSide));
            prevSide = side;
            if (i % 10 == 0) {
                double along = p.x * ux + p.z * uz;
                if (along - last < 0.5) stalls++;
                last = along;
            }
            int cx = Mth.floor(p.x), cz = Mth.floor(p.z);
            if (g.t.top(cx, cz) == Integer.MIN_VALUE || !inside(g.area[0] + 1, g.area[1] + 1, g.area[2] - 2, cx, cz)) break;
        }
        return new Walk(p.y - y0, p.x * ux + p.z * uz - along0, p.pushes, stalls, jitter, p.lifts, p.waterTicks, p.deepest);
    }

    // ------------------------------------------------------------------------------------------------- mobs

    /** A chase result: jumps MoveControl asked for, rise, distance, 10-tick windows with < 0.5 block progress. */
    record Chase(int jumps, double rise, double distance, int stalls) {}

    /**
     * A zombie (0.6 wide, step 0.6, movement speed 0.23) following path nodes straight ahead, with MoveControl#tick's
     * jump test: (wanted Y - y > step and the node is close) or (the block at the feet has a shape whose top is above
     * the feet). The wanted Y comes from PathNavigation#getGroundY = WalkNodeEvaluator#getFloorLevel of the node: the
     * shape's top over the block under it. vanilla: tops are VoxelShape#max(Y) (a slope's highest corner); fixed (Terrain
     * Smooth V1.1): a smoothed top's column top where it is stood on (the node's centre, the mob's own x z).
     */
    static Chase chase(Ground g, double sx, double sz, double ux, double uz, int ticks, boolean fixed) {
        int top = g.t.top(Mth.floor(sx), Mth.floor(sz));
        Player p = new Player(g, sx, top + 1.5, sz);
        p.settle();
        double y0 = p.y, along0 = p.x * ux + p.z * uz, last = along0;
        int jumps = 0, stalls = 0;
        for (int i = 1; i <= ticks; i++) {
            // the next node: the block one step ahead, its feet cell above the ground
            int nx = Mth.floor(p.x + ux * 0.9), nz = Mth.floor(p.z + uz * 0.9);
            int ny = g.t.top(nx, nz);
            if (ny == Integer.MIN_VALUE) break;
            VoxelShape under = g.collision(nx, ny - 1, nz);
            double floor = under.isEmpty() ? 0 : fixed && g.geometry(nx, ny - 1, nz) >= 0 ? under.max(Direction.Axis.Y, 0.5, 0.5) : under.max(Direction.Axis.Y);
            double wantedY = ny - 1 + floor;
            double d0 = nx + 0.5 - p.x, d1 = nz + 0.5 - p.z, d2 = wantedY - p.y;
            int fx = Mth.floor(p.x), fy = Mth.floor(p.y), fz = Mth.floor(p.z);
            VoxelShape feet = g.collision(fx, fy, fz);
            double feetTop = feet.isEmpty() ? Double.NEGATIVE_INFINITY : fixed && g.geometry(fx, fy, fz) >= 0
                    ? feet.max(Direction.Axis.Y, p.z - fz, p.x - fx) : feet.max(Direction.Axis.Y);
            boolean jump = d2 > Player.STEP && d0 * d0 + d1 * d1 < Math.max(1.0, Player.WIDTH) || !feet.isEmpty() && p.y < feetTop + fy;
            if (jump && p.onGround) { p.dy = 0.42; jumps++; }
            // walking toward the node at the zombie's speed (MoveControl#setSpeed, LivingEntity#travel on the ground)
            double len = Math.max(1e-6, Math.hypot(d0, d1)), friction = p.onGround ? 0.6 : 1.0;
            double speed = p.onGround ? 0.23 * (0.21600002F / (float) (friction * friction * friction)) : 0.02;
            p.dx += d0 / len * 0.98 * speed;
            p.dz += d1 / len * 0.98 * speed;
            p.move(new Vec3(p.dx, p.dy, p.dz));
            p.dy = (p.dy - 0.08) * 0.98;
            p.dx *= friction * 0.91;
            p.dz *= friction * 0.91;
            if (i % 10 == 0) {
                double along = p.x * ux + p.z * uz;
                if (along - last < 0.5) stalls++;
                last = along;
            }
            int cx = Mth.floor(p.x), cz = Mth.floor(p.z);
            if (g.t.top(cx, cz) == Integer.MIN_VALUE || !inside(g.area[0] + 2, g.area[1] + 2, g.area[2] - 4, cx, cz)) break;
        }
        return new Chase(jumps, p.y - y0, p.x * ux + p.z * uz - along0, stalls);
    }

    static String fmt(Chase c) {
        return String.format(Locale.ROOT, "jumps %d, rise %+.2f, distance %.1f, stalled windows %d", c.jumps, c.rise, c.distance, c.stalls);
    }

    // ---------------------------------------------------------------------------------------------- real world

    /** Blocks exported from a save by tools/terrain-smooth/export-area.mjs. */
    static final class RealTerrain implements Terrain {
        final int x0, z0, w, d, y0, h;
        final byte[] kinds, wet;

        RealTerrain(java.nio.file.Path file) throws java.io.IOException {
            try (var in = new java.io.DataInputStream(new java.io.BufferedInputStream(java.nio.file.Files.newInputStream(file)))) {
                x0 = in.readInt(); z0 = in.readInt(); w = in.readInt(); d = in.readInt(); y0 = in.readInt(); h = in.readInt();
                kinds = in.readNBytes(w * d * h);
                wet = in.readNBytes(w * d * h);
            }
        }

        int column(int x, int z) { int a = x - x0, b = z - z0; return a < 0 || b < 0 || a >= w || b >= d ? -1 : (b * w + a) * h; }

        @Override public int cell(int x, int y, int z) {
            int c = column(x, z);
            if (c < 0) return SmoothRules.UNKNOWN;
            if (y < y0) return SmoothRules.OTHER;
            if (y >= y0 + h) return SmoothRules.AIR;
            return kinds[c + y - y0] & 7;
        }

        @Override public double water(int x, int y, int z) {
            int c = column(x, z);
            return c < 0 || y < y0 || y >= y0 + h ? 0 : wet[c + y - y0] / 16.0;
        }

        @Override public int ground(int x, int y, int z) {
            int c = column(x, z);
            return c < 0 || y < y0 || y >= y0 + h ? 0 : kinds[c + y - y0] >> 3 & 15;
        }

        /** Above the highest solid block (logs and leaves of trees included). */
        @Override public int top(int x, int z) {
            int c = column(x, z);
            if (c < 0) return Integer.MIN_VALUE;
            for (int y = y0 + h - 1; y >= y0; y--) {
                int k = kinds[c + y - y0] & 7;
                if (k == SmoothRules.NATURAL || k == SmoothRules.OTHER || k == SmoothRules.FLUID || k == SmoothRules.TREE) return y + 1;
            }
            return y0;
        }

        /** the game's rule (TerrainSmooth#columnSurface) from the highest exported cell down */
        @Override public int surface(int x, int z) {
            return column(x, z) < 0 ? SmoothRules.NO_SURFACE : SmoothRules.surface(this, x, z, y0 + h - 1, y0);
        }
    }

    static final String[] GROUND = {"?", "grass block", "dirt", "coarse dirt", "sand", "gravel", "stone", "podzol", "mud", "clay"};
    /** mud (GROUND index) and vanilla mud's collision (MudBlock: 14 / 16) */
    static final int MUD = 8;
    static final VoxelShape MUD_BLOCK = Shapes.box(0, 0, 0, 1, 1 - SmoothShapes.MUD_SINK / 16.0, 1);

    /**
     * java PhysicsCheck world <export file> <minX> <minZ> <size>: from every column of the area whose top is natural ground,
     * walk 40 ticks in 8 directions with vanilla blocks and with mode B; per ground block: distance, stalls, ticks in water.
     */
    static void realWorld(String[] a) throws java.io.IOException {
        RealTerrain t = new RealTerrain(java.nio.file.Path.of(a[1]));
        int[] area = {Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4])};
        String[] modes = {"vanilla", "", "B"};
        java.util.Map<String, double[]> spots = new java.util.TreeMap<>();
        for (int mode : new int[]{SmoothRules.MODE_OFF, SmoothRules.MODE_B}) {
            Ground g = new Ground(t, mode, area, false);
            g.cache = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
            g.cache.defaultReturnValue(Long.MIN_VALUE);
            double[][] sum = new double[GROUND.length][7];   // walks, distance, stalls, water ticks, lifts, pushes, walks touching water
            for (int z = area[1] + 3; z < area[1] + area[2] - 3; z++) for (int x = area[0] + 3; x < area[0] + area[2] - 3; x++) {
                int top = t.top(x, z), k = t.cell(x, top - 1, z);
                if (k != SmoothRules.NATURAL) continue;
                int ground = t.ground(x, top - 1, z);
                if (ground != 4 && ground != 5 && (x + z) % 5 != 0) continue;   // sand and gravel everywhere, the rest sampled
                for (int dir = 0; dir < 8; dir++) {
                    double ang = Math.PI / 4 * dir + 0.2, ux = Math.cos(ang), uz = Math.sin(ang);
                    Walk w = walk(g, x + 0.5, z + 0.5, ux, uz, 40, false, true);
                    double[] s = sum[ground];
                    s[0]++; s[1] += w.distance; s[2] += w.stalls; s[3] += w.waterTicks; s[4] += w.lifts; s[5] += w.pushes; s[6] += w.waterTicks > 0 ? 1 : 0;
                    if (w.waterTicks > 0 && (ground == 4 || ground == 5)) {
                        double[] spot = spots.computeIfAbsent(String.format(Locale.ROOT, "%5d %4d %3d %s", x, z, top - 1, GROUND[ground]), key -> new double[6]);
                        spot[mode == SmoothRules.MODE_OFF ? 0 : 1] += w.waterTicks;
                        spot[mode == SmoothRules.MODE_OFF ? 2 : 3] += w.distance;
                        spot[mode == SmoothRules.MODE_OFF ? 4 : 5] = Math.max(spot[mode == SmoothRules.MODE_OFF ? 4 : 5], w.deepest);
                    }
                }
            }
            for (int ground = 1; ground < GROUND.length; ground++) {
                double[] s = sum[ground];
                if (s[0] == 0) continue;
                System.out.println(String.format(Locale.ROOT, "%-7s %-11s walks %5.0f: distance %.2f per 40 ticks, stalled windows %.2f per walk, ticks in water %.2f per walk (%.1f %% of walks touch water), lifts %.0f, pushes %.0f",
                        modes[mode], GROUND[ground], s[0], s[1] / s[0], s[2] / s[0], s[3] / s[0], 100 * s[6] / s[0], s[4], s[5]));
            }
        }
        // standing still on every sand / gravel top at 9 spots: does the player move up and down (the user's "quicksand")?
        {
            Ground g = new Ground(t, SmoothRules.MODE_B, area, false);
            g.cache = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
            g.cache.defaultReturnValue(Long.MIN_VALUE);
            int stands = 0, moving = 0, lifted = 0, wet = 0;
            double worst = 0;
            String worstAt = "";
            for (int z = area[1] + 3; z < area[1] + area[2] - 3; z++) for (int x = area[0] + 3; x < area[0] + area[2] - 3; x++) {
                int top = t.top(x, z);
                if (t.cell(x, top - 1, z) != SmoothRules.NATURAL) continue;
                int ground = t.ground(x, top - 1, z);
                if (ground != 4 && ground != 5) continue;
                for (int s = 0; s < 9; s++) {
                    Player p = new Player(g, x + 0.2 + 0.3 * (s % 3), top + 1.5, z + 0.2 + 0.3 * (s / 3));
                    p.lift = true;
                    p.settle();
                    p.lifts = 0;   // count standing only, not the drop onto the ground
                    double y0 = p.y, x0 = p.x, z0 = p.z, move = 0;
                    for (int i = 0; i < 40; i++) {
                        p.tick(0, 0, false, true);
                        move = Math.max(move, Math.abs(p.y - y0) + Math.hypot(p.x - x0, p.z - z0));
                    }
                    stands++;
                    if (move > 1e-6) {
                        moving++;
                        if (a.length > 5) System.out.println(String.format(Locale.ROOT, "  moves: start %.2f %.2f %.2f (%s) -> %.4f %.4f %.4f, pushes %d, lifts %d, water ticks %d, on ground %s",
                                x0, y0, z0, GROUND[ground], p.x, p.y, p.z, p.pushes, p.lifts, p.waterTicks, p.onGround));
                    }
                    if (p.lifts > 0) lifted++;
                    if (p.waterTicks > 0) wet++;
                    if (move > worst) { worst = move; worstAt = String.format(Locale.ROOT, "%.2f %.2f %.2f %s", x0, y0, z0, GROUND[ground]); }
                }
            }
            System.out.println(String.format(Locale.ROOT, "standing still on sand / gravel (B): %d spots, %d move at all (worst %.4f at %s), %d lifted, %d in water", stands, moving, worst, worstAt, lifted, wet));
        }
        System.out.println("sand / gravel tops where a walk touches water (water ticks over 8 walks vanilla / B, distance vanilla / B, deepest vanilla / B):");
        int n = 0;
        for (var e : spots.entrySet()) {
            double[] s = e.getValue();
            if (s[1] <= s[0]) continue;
            if (n++ < 60) System.out.println(String.format(Locale.ROOT, "  %s  water %3.0f / %3.0f  distance %5.1f / %5.1f  deepest %.2f / %.2f", e.getKey(), s[0], s[1], s[2], s[3], s[4], s[5]));
        }
        System.out.println("  " + n + " tops get wetter with B");
    }

    static String fmt(Walk w) {
        return String.format(Locale.ROOT, "rise %+.2f, distance %.1f, pushes %d, stalled windows %d, largest sideways move per tick %.3f, lifts %d", w.rise, w.distance, w.pushes, w.stalls, w.jitter, w.lifts);
    }

    public static void main(String[] args) throws java.io.IOException {
        if (args.length >= 2 && args[0].equals("world")) { realWorld(args); return; }
        // 1. A 1:2 hill (mode B's slope), 6 blocks of rise, walking and sprinting up and down, with and without the fix.
        SmoothCheck.World hill = ramp(2, 6);
        int[] ha = areaOf(hill);
        for (boolean sprint : new boolean[]{false, true}) {
            String gait = sprint ? "sprint" : "walk";
            Ground before = new Ground(hill, SmoothRules.MODE_B, ha, true), after = new Ground(hill, SmoothRules.MODE_B, ha, false);
            Walk upBefore = walk(before, -6.5, 0.5, 1, 0, 120, sprint, true);
            Walk upAfter = walk(after, -6.5, 0.5, 1, 0, 120, sprint, true);
            Walk downAfter = walk(after, 14.5, 0.5, -1, 0, 160, sprint, true);
            System.out.println("hill 1:2 up   " + gait + "  smoothed tops suffocate (as tested): " + fmt(upBefore));
            System.out.println("hill 1:2 up   " + gait + "  fixed:                              " + fmt(upAfter));
            System.out.println("hill 1:2 down " + gait + "  fixed:                              " + fmt(downAfter));
            require(upBefore.rise < 5.9 && upBefore.pushes > 0 && upBefore.stalls > 0, "the harness reproduces the user test (suffocating smoothed tops push the player back and stop the climb), " + gait);
            require(upAfter.rise > 5.9 && upAfter.pushes == 0 && upAfter.stalls == 0, "walks up the 1:2 hill without pushes or stalls, " + gait);
            require(downAfter.rise < -5.9 && downAfter.pushes == 0 && downAfter.stalls == 0, "walks down the 1:2 hill, " + gait);
        }
        // vanilla sanity: the same hill without smoothing is a staircase of full blocks, walking can't climb it (needs jumps)
        Walk vanilla = walk(new Ground(hill, SmoothRules.MODE_OFF, ha, true), -6.5, 0.5, 1, 0, 120, false, true);
        System.out.println("hill 1:2 up   walk  smoothing off (vanilla blocks):        " + fmt(vanilla));
        require(vanilla.rise < 0.5, "vanilla staircase blocks a walking player (harness sanity)");

        // 1b. Standing still on the slope (the user's "stuck, keeps twitching"): pushes and drift over 100 ticks at 8 spots.
        for (boolean suffocate : new boolean[]{true, false}) {
            Ground g = new Ground(hill, SmoothRules.MODE_B, ha, suffocate);
            int pushes = 0;
            double drift = 0;
            for (int s = 0; s < 8; s++) {
                double sx = 0.3 + s * 1.2;
                Player p = new Player(g, sx, hill.h[hill.i(Mth.floor(sx), 0)] + 1.5, 0.5);
                p.settle();
                double x0 = p.x, z0 = p.z;
                for (int i = 0; i < 100; i++) p.tick(0, 0, false, true);
                pushes += p.pushes;
                drift = Math.max(drift, Math.hypot(p.x - x0, p.z - z0));
            }
            System.out.println(String.format(Locale.ROOT, "hill 1:2 standing still, 8 spots x 100 ticks, %s: pushes %d, largest drift %.2f blocks", suffocate ? "as tested" : "fixed    ", pushes, drift));
            if (suffocate) require(pushes > 0, "the harness reproduces the twitching (standing on a slope gets pushed)");
            else require(pushes == 0 && drift < 1e-6, "standing on a slope stays put");
        }

        // 1c. An edit next to a standing player re-shapes the slope under the feet: a block of another kind placed next to
        //     them (pins the shared corners at the block's own height), ground piled on a neighbour (raises the shared corners)
        //     or dug out of it. How deep does the player end up inside the new top, with and without the lift?
        String[] edits = {"placed block of another kind", "ground piled on a neighbour", "neighbour dug out"};
        for (int edit = 0; edit < 3; edit++) for (boolean lift : new boolean[]{false, true}) {
            double worstSink = 0;
            int cases = 0, lifted = 0;
            for (int s = 0; s < 10; s++) for (int side = 0; side < 4; side++) {
                SmoothCheck.World t = ramp(2, 6);
                Ground g = new Ground(t, SmoothRules.MODE_B, areaOf(t), false);
                double sx = 0.15 + s * 0.97;
                Player p = new Player(g, sx, t.h[t.i(Mth.floor(sx), 0)] + 1.5, 0.5);
                p.lift = lift;
                p.settle();
                int bx = Mth.floor(p.x) + (side == 0 ? 1 : side == 1 ? -1 : 0), bz = side == 2 ? 1 : side == 3 ? -1 : 0;
                int k = t.i(bx, bz);
                if (edit == 0) t.top[k] = 1; else if (edit == 1) t.h[k]++; else t.h[k]--;
                for (int i = 0; i < 20; i++) p.tick(0, 0, false, true);
                double sink = surfaceAt(g, p.x, p.z, p.y) - p.y;
                cases++;
                lifted += p.lifts;
                worstSink = Math.max(worstSink, sink);
            }
            System.out.println(String.format(Locale.ROOT, "edit next to a standing player (%s), %d cases, %s: deepest inside the ground %.2f blocks, lifted %d times",
                    edits[edit], cases, lift ? "with the lift" : "no lift      ", worstSink, lifted));
            if (lift) require(worstSink < 1e-6, "the lift puts the player back on the slope after an edit next to them (" + edits[edit] + ")");
        }
        // the lift never fires while walking (collision keeps the box out of the slopes)
        {
            Ground g = new Ground(hill, SmoothRules.MODE_B, ha, false);
            int lifts = 0;
            for (int s = 0; s < 4; s++) {
                double a = Math.PI / 2 * s + 0.3, ux = Math.cos(a), uz = Math.sin(a);
                int k = hill.i(-6, 0);
                Player p = new Player(g, -6.5 + 12 * (s % 2), hill.h[k] + 8, 0.5 - 4 * (s / 2));
                p.lift = true;
                p.settle();
                for (int i = 0; i < 160; i++) p.tick(ux, uz, s % 2 == 0, true);
                lifts += p.lifts;
            }
            System.out.println("walking on the hill with the lift on: lifted " + lifts + " times");
            require(lifts == 0, "walking never triggers the lift");
        }

        // 2. A diagonal walk across the hill (both x and z motion, the corner and edge cases of the boxes).
        Ground hillFixed = new Ground(hill, SmoothRules.MODE_B, ha, false);
        double r = Math.sqrt(0.5);
        Walk diag = walk(hillFixed, -6.5, -8.5, r, r, 160, false, true);
        System.out.println("hill 1:2 diagonal walk fixed: " + fmt(diag));
        require(diag.rise > 5.9 && diag.pushes == 0 && diag.stalls == 0, "walks diagonally up the hill");

        // 2b. The same hill in mud (2026-10-12): mud collides MUD_SINK below its top, smoothed too (SmoothShapes#sunk).
        Terrain synthHill = synth(hill);
        Terrain mudHill = new Terrain() {
            @Override public int cell(int x, int y, int z) { return synthHill.cell(x, y, z); }
            @Override public int surface(int x, int z) { return synthHill.surface(x, z); }
            @Override public int top(int x, int z) { return synthHill.top(x, z); }
            @Override public int ground(int x, int y, int z) { return MUD; }
        };
        Ground mudFixed = new Ground(mudHill, SmoothRules.MODE_B, ha, false);
        Walk mudUp = walk(mudFixed, -6.5, 0.5, 1, 0, 120, false, true), mudDown = walk(mudFixed, 14.5, 0.5, -1, 0, 160, false, true);
        System.out.println("mud hill 1:2 up   walk fixed: " + fmt(mudUp) + ", deepest " + String.format(Locale.ROOT, "%.3f", mudUp.deepest));
        System.out.println("mud hill 1:2 down walk fixed: " + fmt(mudDown));
        require(mudUp.rise > 5.9 && mudUp.pushes == 0 && mudUp.stalls == 0 && mudUp.lifts == 0, "walks up a mud hill (sunk collision), no pushes, stalls or lifts");
        require(mudDown.rise < -5.9 && mudDown.pushes == 0 && mudDown.stalls == 0 && mudDown.lifts == 0, "walks down a mud hill");

        // 3. Rugged synthetic terrain (the offline check's terrain, mode B): many walks in 16 headings, with and without the fix.
        SmoothCheck.World land = SmoothCheck.terrain(9, -40, -40, 96, false);
        int[] la = areaOf(land);
        int[] stats = new int[6];
        double worstJerkBefore = 0, worstJerkAfter = 0;
        for (int s = 0; s < 64; s++) {
            double a = Math.PI * 2 * (s % 16) / 16, ux = Math.cos(a), uz = Math.sin(a);
            double sx = -20.5 + (s / 16) * 12, sz = -20.5 + (s % 4) * 10;
            Walk b = walk(new Ground(land, SmoothRules.MODE_B, la, true), sx, sz, ux, uz, 200, false, true);
            Walk f = walk(new Ground(land, SmoothRules.MODE_B, la, false), sx, sz, ux, uz, 200, false, true);
            stats[0] += b.pushes; stats[1] += b.stalls; stats[2] += b.distance > 15 ? 1 : 0;
            stats[3] += f.pushes + f.lifts; stats[4] += f.stalls; stats[5] += f.distance > 15 ? 1 : 0;
            worstJerkBefore = Math.max(worstJerkBefore, b.jitter);
            worstJerkAfter = Math.max(worstJerkAfter, f.jitter);
        }
        System.out.println(String.format(Locale.ROOT, "rugged terrain, 64 walks x 200 ticks, as tested: pushes %d, stalled windows %d, walks past 15 blocks %d/64, largest sideways move per tick %.3f", stats[0], stats[1], stats[2], worstJerkBefore));
        System.out.println(String.format(Locale.ROOT, "rugged terrain, 64 walks x 200 ticks, fixed:     pushes + lifts %d, stalled windows %d, walks past 15 blocks %d/64, largest sideways move per tick %.3f", stats[3], stats[4], stats[5], worstJerkAfter));
        require(stats[3] == 0, "no push out of blocks anywhere on smoothed ground");
        require(stats[5] == 64 && stats[4] == 0, "every walk across rugged smoothed ground keeps going");

        // 4. Mobs (V1.1, the user's test: "zombies jump at every slope"): a zombie chasing up and down the 1:2 hill and across
        //    the rugged terrain, MoveControl's jump test as vanilla computes it on smoothed tops, then fixed.
        Ground mobHill = new Ground(hill, SmoothRules.MODE_B, ha, false);
        Chase upV = chase(mobHill, -6.5, 0.5, 1, 0, 160, false), upF = chase(mobHill, -6.5, 0.5, 1, 0, 160, true);
        Chase downV = chase(mobHill, 14.5, 0.5, -1, 0, 160, false), downF = chase(mobHill, 14.5, 0.5, -1, 0, 160, true);
        System.out.println("zombie up the 1:2 hill,   vanilla jump test: " + fmt(upV));
        System.out.println("zombie up the 1:2 hill,   fixed:             " + fmt(upF));
        System.out.println("zombie down the 1:2 hill, vanilla jump test: " + fmt(downV));
        System.out.println("zombie down the 1:2 hill, fixed:             " + fmt(downF));
        require(upF.jumps == 0 && upF.rise > 5.9 && upF.stalls == 0, "a zombie walks up the 1:2 hill without jumping");
        require(downF.jumps == 0 && downF.rise < -5.9 && downF.stalls == 0, "a zombie walks down the 1:2 hill without jumping");
        Ground mobVanilla = new Ground(hill, SmoothRules.MODE_OFF, ha, true);
        Chase stairs = chase(mobVanilla, -6.5, 0.5, 1, 0, 160, true);
        System.out.println("zombie up the same hill as vanilla blocks, fixed jump test: " + fmt(stairs));
        require(stairs.jumps >= 5 && stairs.rise > 5.9, "real one-block steps still make a zombie jump (vanilla blocks keep vanilla)");
        int[] mob = new int[4];
        for (int s = 0; s < 64; s++) {
            double a = Math.PI * 2 * (s % 16) / 16, ux = Math.cos(a), uz = Math.sin(a);
            double sx = -20.5 + (s / 16) * 12, sz = -20.5 + (s % 4) * 10;
            Ground lg = new Ground(land, SmoothRules.MODE_B, la, false);
            Chase v = chase(lg, sx, sz, ux, uz, 200, false), f = chase(lg, sx, sz, ux, uz, 200, true);
            mob[0] += v.jumps; mob[1] += f.jumps; mob[2] += f.stalls; mob[3] += f.distance > 15 ? 1 : 0;
        }
        System.out.println(String.format(Locale.ROOT, "rugged terrain, 64 zombie chases x 200 ticks: jumps vanilla %d, fixed %d; fixed: stalled windows %d, chases past 15 blocks %d/64",
                mob[0], mob[1], mob[2], mob[3]));
        require(upV.jumps >= 3 && downV.jumps >= 1 && mob[0] > 100, "the harness reproduces the user test (zombies jump on smoothed slopes)");
        require(mob[1] * 4 < mob[0] && mob[2] == 0, "the fix removes the slope jumps on rugged smoothed ground, nothing stalls");

        System.out.println(failures == 0 ? "TERRAIN SMOOTH PHYSICS CHECK OK" : "TERRAIN SMOOTH PHYSICS CHECK FAILED: " + failures);
        if (failures != 0) System.exit(1);
    }
}
