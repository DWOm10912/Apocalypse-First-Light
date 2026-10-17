package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.vehicle.HeadlightBeam;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * The T15 headlamps as dynamic block light on the client (docs/vehicles/first_pickup_v0_design.md 8.10): each tick the
 * lit trucks near the camera cast their beams (HeadlightBeam) into one field, cell -> block light, published as an
 * immutable map that the light reads take as max(stored, field) (mixin client.HeadlightLightColorMixin: vanilla and
 * Embeddium chunk meshing, entities, particles, the road mesh; client.HeadlightWorldSliceMixin: Embeddium's smooth light).
 * The chunk sections whose cells (or a neighbour cell: smooth light reads one cell around) changed are marked dirty, so
 * only those rebuild. Cast again at most every {@link #MIN_TICKS} ticks while a truck moves or its lamps change, and every
 * {@link #IDLE_TICKS} ticks anyway (the world may have changed under the beam). Nothing on the server: other players see
 * the light from their own clients' casts of the same synced lamps.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HeadlightField {
    /** Trucks this close to the camera cast (blocks), at most this many (nearest first). */
    private static final double RANGE = 128;
    private static final int MAX_TRUCKS = 4;
    /** Cast again at most this often while moving, and this often while still (ticks). */
    private static final int MIN_TICKS = 2, IDLE_TICKS = 20;
    /** A move or turn this big casts again (blocks, deg). */
    private static final double MOVE = 0.2, TURN = 1.5;
    /** A chunk the beam lit counts as lit this long (ticks; RoadClient refreshes its mesh light there as near the player). */
    private static final long CHUNK_LIT_TICKS = 40;
    /** The beam's middle (forward, right, up from the truck's origin): between the headlamps, just in front of the lenses. */
    private static final double LAMP_F = 2.32, LAMP_Y = 0.81;

    private static final Long2ByteOpenHashMap EMPTY = new Long2ByteOpenHashMap(0);
    /** The published field (never changed after publishing; read from chunk build threads). */
    private static volatile Long2ByteOpenHashMap field = EMPTY;
    /** Chunk (ChunkPos#asLong) -> the last game time the beam lit it. */
    private static final Long2LongOpenHashMap LIT_CHUNKS = new Long2LongOpenHashMap();
    private static ClientLevel lastLevel;
    private static long lastCast = Long.MIN_VALUE;
    /** Each casting truck's pose and lamps at the last cast: id, x, y, z, yaw, pitch, lamps. */
    private static List<double[]> lastPoses = List.of();
    /** Diagnostics: the last cast's cells, rays' time (us). */
    public static int lastCells;
    public static long lastMicros;

    private HeadlightField() {
    }

    /** The headlamp block light at a cell (0: none). Any thread. */
    public static int level(BlockPos pos) {
        Long2ByteOpenHashMap f = field;
        return f.isEmpty() ? 0 : f.get(pos.asLong());
    }

    public static int level(long pos) {
        Long2ByteOpenHashMap f = field;
        return f.isEmpty() ? 0 : f.get(pos);
    }

    /** A packed light (LightTexture) with the headlamps' block light taken in. */
    public static int lightColor(int packed, BlockPos pos) {
        Long2ByteOpenHashMap f = field;
        if (f.isEmpty()) return packed;
        int d = f.get(pos.asLong());
        if (d <= 0) return packed;
        int block = packed >> 4 & 0xF;
        return d > block ? packed & ~0xF0 | d << 4 : packed;
    }

    /** Render thread: the beam lit this chunk in the last CHUNK_LIT_TICKS ticks. */
    public static boolean litChunk(int cx, int cz, long now) {
        if (LIT_CHUNKS.isEmpty()) return false;
        long t = LIT_CHUNKS.getOrDefault(ChunkPos.asLong(cx, cz), Long.MIN_VALUE);
        return t != Long.MIN_VALUE && now - t <= CHUNK_LIT_TICKS;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            // a new world or dimension: the old field's sections are gone with it
            field = EMPTY;
            LIT_CHUNKS.clear();
            lastPoses = List.of();
            lastCast = Long.MIN_VALUE;
            lastLevel = level;
        }
        if (level == null || mc.isPaused()) return;
        long now = level.getGameTime();
        Entity camera = mc.getCameraEntity();
        if (camera == null) return;
        List<PickupTruckEntity> lit = new ArrayList<>();
        for (Entity e : level.entitiesForRendering())
            if (e instanceof PickupTruckEntity truck && truck.headlights() && truck.distanceToSqr(camera) < RANGE * RANGE) lit.add(truck);
        lit.sort(java.util.Comparator.comparingDouble(t -> t.distanceToSqr(camera)));
        if (lit.size() > MAX_TRUCKS) lit = lit.subList(0, MAX_TRUCKS);
        List<double[]> poses = new ArrayList<>(lit.size());
        for (PickupTruckEntity t : lit) poses.add(new double[]{t.getId(), t.getX(), t.getY(), t.getZ(), t.getYRot(), t.getXRot(),
                t.lamps() & (PickupTruckEntity.LAMP_HEAD | PickupTruckEntity.LAMP_HIGH)});
        boolean changed = changed(lastPoses, poses);
        // never cast yet: cast now (now - Long.MIN_VALUE overflows to a negative and read as "cast a moment ago", so the
        // field was never cast at all: user in game 2026-10-16, measured with dev/HeadlightProbe)
        if (lastCast != Long.MIN_VALUE && now >= lastCast) {
            if (!changed && now - lastCast < IDLE_TICKS) return;
            if (changed && now - lastCast < MIN_TICKS) return;
        }
        if (lit.isEmpty() && field.isEmpty()) {
            lastPoses = poses;
            return;
        }
        long t0 = System.nanoTime();
        Long2ByteOpenHashMap next = new Long2ByteOpenHashMap();
        Long2ObjectOpenHashMap<HeadlightBeam.Cell> cache = new Long2ObjectOpenHashMap<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        HeadlightBeam.World world = new HeadlightBeam.World() {
            @Override
            public HeadlightBeam.Cell cell(int x, int y, int z) { return HeadlightField.cell(level, p.set(x, y, z)); }

            @Override
            public double surfaceTop(int x, int y, int z) {
                BlockPos at = p.set(x, y, z);
                return Math.min(1.0, level.getBlockState(at).getCollisionShape(level, at).max(net.minecraft.core.Direction.Axis.Y));
            }
        };
        for (PickupTruckEntity t : lit) {
            Vec3 o = t.framePoint(LAMP_F, 0, LAMP_Y, 1.0F), f = t.frameDirection(1, 0, 0, 1.0F), u = t.frameDirection(0, 0, 1, 1.0F);
            double[] fwd = {f.x, f.y, f.z}, up = {u.x, u.y, u.z};
            HeadlightBeam.cast(o.x, o.y, o.z, fwd, up, HeadlightBeam.LOW, world, next, cache);
            if (t.highBeam()) HeadlightBeam.cast(o.x, o.y, o.z, fwd, up, HeadlightBeam.HIGH, world, next, cache);
        }
        lastMicros = (System.nanoTime() - t0) / 1000;
        lastCells = next.size();
        Long2ByteOpenHashMap old = field;
        field = next.isEmpty() ? EMPTY : next;
        lastCast = now;
        lastPoses = poses;
        // the sections to rebuild: every cell whose light changed, and the sections its neighbours sit in
        LongOpenHashSet sections = new LongOpenHashSet();
        for (Long2ByteMap.Entry e : next.long2ByteEntrySet())
            if (old.get(e.getLongKey()) != e.getByteValue()) dirty(e.getLongKey(), sections);
        for (Long2ByteMap.Entry e : old.long2ByteEntrySet())
            if (!next.containsKey(e.getLongKey())) dirty(e.getLongKey(), sections);
        for (long s : sections) mc.levelRenderer.setSectionDirty(SectionPos.x(s), SectionPos.y(s), SectionPos.z(s));
        for (long key : next.keySet()) LIT_CHUNKS.put(ChunkPos.asLong(BlockPos.getX(key) >> 4, BlockPos.getZ(key) >> 4), now);
        LIT_CHUNKS.long2LongEntrySet().removeIf(e -> now - e.getLongValue() > CHUNK_LIT_TICKS * 4 || e.getLongValue() > now);
    }

    private static HeadlightBeam.Cell cell(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return HeadlightBeam.Cell.OPEN;
        if (state.isSolidRender(level, pos)) return HeadlightBeam.Cell.OPAQUE;
        return state.getCollisionShape(level, pos).isEmpty() ? HeadlightBeam.Cell.OPEN : HeadlightBeam.Cell.SURFACE;
    }

    private static void dirty(long key, LongOpenHashSet sections) {
        int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
        for (int sx = (x - 1) >> 4; sx <= (x + 1) >> 4; sx++)
            for (int sy = (y - 1) >> 4; sy <= (y + 1) >> 4; sy++)
                for (int sz = (z - 1) >> 4; sz <= (z + 1) >> 4; sz++) sections.add(SectionPos.asLong(sx, sy, sz));
    }

    private static boolean changed(List<double[]> a, List<double[]> b) {
        if (a.size() != b.size()) return true;
        for (int i = 0; i < a.size(); i++) {
            double[] p = a.get(i), q = b.get(i);
            if (p[0] != q[0] || p[6] != q[6]) return true;
            double dx = p[1] - q[1], dy = p[2] - q[2], dz = p[3] - q[3];
            if (dx * dx + dy * dy + dz * dz > MOVE * MOVE) return true;
            if (Math.abs(net.minecraft.util.Mth.wrapDegrees(p[4] - q[4])) > TURN || Math.abs(p[5] - q[5]) > TURN) return true;
        }
        return false;
    }
}
