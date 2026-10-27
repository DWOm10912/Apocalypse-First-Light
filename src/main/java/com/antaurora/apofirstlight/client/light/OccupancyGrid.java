package com.antaurora.apofirstlight.client.light;

import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import com.mojang.blaze3d.platform.GlStateManager;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The blocks around the camera for the lights' shadow rays (docs/rendering/dynamic_lights_v1.md): a 128 x 128 x 128 R8
 * 3D texture, wrapped (a block's texel is its world position & 127), filled 16 x 16 x 16 sections at a time. The window is
 * the 8 x 8 x 8 sections around the camera's; a section that comes into it is cleared at once (open) and queued, and the
 * queue is worked through in a time budget per frame, nearest first; changed blocks (mixin
 * client.DynamicLightBlockChangeMixin) and loaded chunks queue their sections again ahead of the rest.
 * <p>A block's value: 0 open (air, plants, fluids, anything without collision or with a thin or partial shape, and
 * see-through blocks: no visual shape, as glass or the cabin's window), 255 a full block (a solid render block or a full
 * collision cube), 1-254 solid from the bottom up to (v - 1) / 253 of the block (a bottom slab, a road layer: a shape over
 * the whole footprint, counted up to its lowest column, {@link #bottomUp}). A smoothed natural ground top (terrainsmooth)
 * counts as solid up to its lowest corner, so the lit slope itself is never inside its own block.
 * <p>Two layouts of the same values: the 3D texture for AFL's own programs (no shader pack), or laid flat in a 2048 x 1024
 * R8 texture for a shader pack ({@link ShaderPackLights}: g = world block & 127 at (g.x * 16 + (g.z & 15),
 * g.y + 128 * (g.z >> 4)), so one section is one 256 x 16 rectangle). Switching the layout fills the window again.
 */
final class OccupancyGrid {
    static final int SIZE = 128, SECTIONS = 8, UNIT = GL13.GL_TEXTURE15;
    /** Building time per frame (ns) and when a whole new window is being filled. */
    private static final long BUDGET = 1_000_000L;

    private int texture;
    private ClientLevel level;
    /** The flat (shader pack) layout is the one being filled. */
    private boolean flat;
    /** The window's lowest section (section coordinates); slot (x & 7, y & 7, z & 7) holds the section at slotKey. */
    private int minX = Integer.MIN_VALUE, minY, minZ;
    private final long[] slotKey = new long[SECTIONS * SECTIONS * SECTIONS];
    private final LongLinkedOpenHashSet urgent = new LongLinkedOpenHashSet(), queue = new LongLinkedOpenHashSet();
    private final ByteBuffer buffer = MemoryUtil.memAlloc(16 * 16 * 16), zeros = MemoryUtil.memCalloc(16 * 16 * 16);
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    /** Partial shapes' values ({@link #bottomUp}); blocks hand out the same shape objects, so by identity (render thread). */
    private static final Map<VoxelShape, Integer> SHAPE_VALUES = new IdentityHashMap<>();
    /** Diagnostics: sections built since start, the last frame's build time (us). */
    int built;
    long lastMicros;

    OccupancyGrid() {
        Arrays.fill(slotKey, Long.MIN_VALUE);
    }

    int texture() { return texture; }

    /** The window's lowest block (world). */
    int minBlockX() { return minX << 4; }

    int minBlockY() { return minY << 4; }

    int minBlockZ() { return minZ << 4; }

    /** The window is placed (its sections cleared and queued). */
    boolean placed() { return minX != Integer.MIN_VALUE; }

    /** Render thread: follows the camera and builds queued sections within the budget, in the 3D or the flat layout. */
    void update(ClientLevel level, double camX, double camY, double camZ, boolean flat) {
        long begin = System.nanoTime();
        boolean lost = flat ? ShaderPackLights.prepareOccupancy() : texture == 0;
        if (!flat && texture == 0) create();
        if (level != this.level || flat != this.flat || lost) {
            this.level = level;
            this.flat = flat;
            Arrays.fill(slotKey, Long.MIN_VALUE);
            urgent.clear();
            queue.clear();
            minX = Integer.MIN_VALUE;
        }
        int cx = SectionPos.blockToSectionCoord(Mth.floor(camX)), cy = SectionPos.blockToSectionCoord(Mth.floor(camY)),
                cz = SectionPos.blockToSectionCoord(Mth.floor(camZ));
        int nx = cx - SECTIONS / 2, ny = cy - SECTIONS / 2, nz = cz - SECTIONS / 2;
        // flat: bound on the active unit and put back with plain GL (GlStateManager's cached 2D binding stays true)
        int prev = flat ? GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) : GlStateManager._getActiveTexture();
        if (flat) GL11.glBindTexture(GL11.GL_TEXTURE_2D, ShaderPackLights.occupancyId());
        else {
            GL13.glActiveTexture(UNIT);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
        }
        unpackDefaults();
        try {
            if (nx != minX || ny != minY || nz != minZ) move(nx, ny, nz, cx, cy, cz);
            while (System.nanoTime() - begin < BUDGET) {
                long key;
                if (!urgent.isEmpty()) key = urgent.removeFirstLong();
                else if (!queue.isEmpty()) key = queue.removeFirstLong();
                else break;
                build(key);
            }
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            if (flat) GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
            else GL13.glActiveTexture(prev);
        }
        lastMicros = (System.nanoTime() - begin) / 1000;
    }

    /** A block changed (any thread that the level's own changes come on: the render thread): its sections again. */
    void changed(BlockPos at) {
        // a smoothed ground top reads its neighbours' columns two blocks down and one up (SmoothRules#affected)
        int x0 = (at.getX() - 1) >> 4, x1 = (at.getX() + 1) >> 4, y0 = (at.getY() - 2) >> 4, y1 = (at.getY() + 1) >> 4,
                z0 = (at.getZ() - 1) >> 4, z1 = (at.getZ() + 1) >> 4;
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) again(SectionPos.asLong(x, y, z));
    }

    /** A chunk (and its smoothed borders: the columns beside it) was loaded: its column again. */
    void chunkLoaded(int chunkX, int chunkZ) {
        if (minX == Integer.MIN_VALUE) return;
        for (int x = chunkX - 1; x <= chunkX + 1; x++)
            for (int z = chunkZ - 1; z <= chunkZ + 1; z++)
                for (int y = minY; y < minY + SECTIONS; y++) {
                    long key = SectionPos.asLong(x, y, z);
                    if (x == chunkX && z == chunkZ) again(key);
                    else if (holds(key)) queue.add(key);
                }
    }

    private void again(long key) {
        if (holds(key)) {
            queue.remove(key);
            urgent.add(key);
        }
    }

    private boolean holds(long key) {
        int x = SectionPos.x(key), y = SectionPos.y(key), z = SectionPos.z(key);
        return x >= minX && x < minX + SECTIONS && y >= minY && y < minY + SECTIONS && z >= minZ && z < minZ + SECTIONS
                && slotKey[slot(x, y, z)] == key;
    }

    private static int slot(int x, int y, int z) {
        return (x & 7) | (y & 7) << 3 | (z & 7) << 6;
    }

    /** The window moved: every slot that now holds another section is cleared and queued, nearest the camera first. */
    private void move(int nx, int ny, int nz, int cx, int cy, int cz) {
        minX = nx;
        minY = ny;
        minZ = nz;
        LongArrayList fresh = new LongArrayList();
        for (int x = nx; x < nx + SECTIONS; x++)
            for (int y = ny; y < ny + SECTIONS; y++)
                for (int z = nz; z < nz + SECTIONS; z++) {
                    long key = SectionPos.asLong(x, y, z);
                    int s = slot(x, y, z);
                    if (slotKey[s] == key) continue;
                    slotKey[s] = key;
                    upload(x, y, z, zeros);
                    fresh.add(key);
                }
        fresh.sort((a, b) -> Integer.compare(distance(a, cx, cy, cz), distance(b, cx, cy, cz)));
        // what was queued for the old window and is still in the new one stays queued, behind the nearer fresh sections
        LongLinkedOpenHashSet rest = new LongLinkedOpenHashSet(queue);
        queue.clear();
        for (long k : fresh) queue.add(k);
        for (long k : rest) if (holds(k)) queue.add(k);
        urgent.removeIf(k -> !holds(k));
    }

    private static int distance(long key, int cx, int cy, int cz) {
        int dx = SectionPos.x(key) - cx, dy = SectionPos.y(key) - cy, dz = SectionPos.z(key) - cz;
        return dx * dx + dy * dy * 2 + dz * dz;
    }

    private void build(long key) {
        if (!holds(key)) return;
        int sx = SectionPos.x(key), sy = SectionPos.y(key), sz = SectionPos.z(key);
        LevelChunk chunk = level.getChunkSource().getChunk(sx, sz, false);
        if (chunk == null) {
            upload(sx, sy, sz, zeros);   // not loaded: open until the chunk comes (chunkLoaded queues it again)
            return;
        }
        int index = chunk.getSectionIndexFromSectionY(sy);
        if (index < 0 || index >= chunk.getSectionsCount() || chunk.getSection(index).hasOnlyAir()) {
            upload(sx, sy, sz, zeros);
            built++;
            return;
        }
        LevelChunkSection section = chunk.getSection(index);
        boolean smooth = TerrainSmooth.CLIENT.enabled() && TerrainSmooth.applies(level);
        int bx = sx << 4, by = sy << 4, bz = sz << 4;
        ByteBuffer b = buffer;
        b.clear();
        // the upload's order: 3D x fastest, then y, then z; flat z fastest, then x, then y (rows of the 256 x 16 rectangle)
        for (int i = 0; i < 4096; i++) {
            int x, y, z;
            if (flat) { z = i & 15; x = i >> 4 & 15; y = i >> 8; }
            else { x = i & 15; y = i >> 4 & 15; z = i >> 8; }
            BlockState state = section.getBlockState(x, y, z);
            b.put(i, (byte) (state.isAir() ? 0 : value(chunk, state, bx + x, by + y, bz + z, smooth)));
        }
        b.position(0).limit(4096);
        upload(sx, sy, sz, b);
        built++;
    }

    private int value(LevelChunk chunk, BlockState state, int x, int y, int z, boolean smooth) {
        Block block = state.getBlock();
        if (smooth && TerrainSmooth.ground(block)) {
            long g = TerrainSmooth.geometry(level, chunk, x, y, z);
            if (g >= 0) {
                float[] c = SmoothRules.corners(g);
                float low = Math.min(Math.min(c[0], c[1]), Math.min(c[2], c[3]));
                return low >= 1 ? 255 : height(low);
            }
        }
        pos.set(x, y, z);
        if (state.isSolidRender(level, pos)) return 255;
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return 0;
        // see-through (glass, the cabin's window: frame, sill and sashes, the recess open): the light passes
        if (state.getVisualShape(level, pos, CollisionContext.empty()).isEmpty()) return 0;
        if (Block.isShapeFullBlock(shape)) return 255;
        AABB box = shape.bounds();
        if (box.minY > 0.01 || box.minX > 0.01 || box.minZ > 0.01 || box.maxX < 0.99 || box.maxZ < 0.99) return 0;
        Integer v = SHAPE_VALUES.get(shape);
        if (v == null) {
            if (SHAPE_VALUES.size() >= 4096) SHAPE_VALUES.clear();   // a block that makes a new shape per call
            SHAPE_VALUES.put(shape, v = bottomUp(shape));
        }
        return v;
    }

    /**
     * A shape whose bounds fill the block's footprint, as solid from the bottom up to its lowest column (user 2026-10-26:
     * patches of shadow in the cabin). The bounds alone took a door frame or a window (posts and head round an opening) for
     * a full block and a roof's sloped 20 cm slab for solid from the cell's bottom up to half way, which is the room under
     * it: the rays from the ceiling and through the doorway stopped in it. A column is solid from the bottom as far as its
     * boxes join up from y 0; 16 x 16 columns.
     */
    private static int bottomUp(VoxelShape shape) {
        List<AABB> boxes = shape.toAabbs();
        double low = 1;
        for (int i = 0; i < 16 && low > 0; i++) for (int k = 0; k < 16 && low > 0; k++) {
            double cx = (i + 0.5) / 16, cz = (k + 0.5) / 16, top = 0;
            for (boolean grew = true; grew && top < 1; ) {
                grew = false;
                for (AABB b : boxes) {
                    if (b.minX <= cx && cx <= b.maxX && b.minZ <= cz && cz <= b.maxZ && b.minY <= top + 1e-4 && b.maxY > top + 1e-4) {
                        top = b.maxY;
                        grew = true;
                    }
                }
            }
            low = Math.min(low, top);
        }
        return low < 1 / 32.0 ? 0 : low >= 0.99 ? 255 : height(low);
    }

    private static int height(double h) {
        return 1 + (int) Math.round(Mth.clamp(h, 0, 1) * 253);
    }

    private void upload(int sx, int sy, int sz, ByteBuffer data) {
        data.position(0);
        if (flat) {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, (sx & 7) << 8, ((sy & 7) << 4) + ((sz & 7) << 7), 256, 16, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, data);
            return;
        }
        GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, (sx & 7) << 4, (sy & 7) << 4, (sz & 7) << 4, 16, 16, 16, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, data);
    }

    private void create() {
        texture = GL11.glGenTextures();
        int prev = GlStateManager._getActiveTexture();
        GL13.glActiveTexture(UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        unpackDefaults();
        ByteBuffer all = MemoryUtil.memCalloc(SIZE * SIZE * SIZE);
        try {
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, org.lwjgl.opengl.GL30.GL_R8, SIZE, SIZE, SIZE, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, all);
        } finally {
            MemoryUtil.memFree(all);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL13.glActiveTexture(prev);
        }
    }

    /** Vanilla's texture uploads leave row length and skips set: tightly packed bytes here. */
    static void unpackDefaults() {
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
    }
}
