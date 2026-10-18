package com.antaurora.apofirstlight.client.rain;

import com.antaurora.apofirstlight.tree.TreeBlocks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * The rain's column map around the camera (docs/rendering/rain_v1.md): 128 x 128 columns in an RGBA8 texture, wrapped
 * (a column's texel is its world x & 127, z & 127). Per column: the height the rain stops at (the drawn surface at the
 * column's centre under its MOTION_BLOCKING top, RainGround; or the top of a mesh tree's highest crown cell above that,
 * which MOTION_BLOCKING passes through; in 1/64 blocks
 * + 512 * 64, low byte R, high byte G), the light just above it (B = block light * 16 + sky light) and whether it rains there
 * (A 255; 0 where the biome snows or stays dry, or the chunk is not loaded). The drop program reads it to keep rain out
 * from under cover and to light it; a shader pack that draws the rain reads a copy (RainShaderPack).
 * <p>Kept up per client tick: columns that come into the window as the camera moves at once, every column again over 32
 * ticks (light, and changes that come without a block update), a changed block's column at once. The biome's answer (rain
 * here, may it snow) is kept per column and asked again only for a new column, a changed block or a new top: the
 * round-robin pass spent half its time on biome and temperature lookups that never change (2026-10-17, JFR: the whole
 * fill was 5 % of the render thread on a rainy frame with the shader pack).
 */
final class RainColumns {
    static final int SIZE = 128, HALF = SIZE / 2;
    private static final int REFRESH = SIZE * SIZE / 32;

    private final ByteBuffer data = MemoryUtil.memCalloc(SIZE * SIZE * 4);
    /** Columns whose rain stops in a mesh tree's crown (no splashes there: it runs off into the leaves). */
    private final boolean[] crown = new boolean[SIZE * SIZE];
    /**
     * Per column: it may snow there (a biome with precipitation and a base temperature under 0.6, so cold enough for snow
     * somewhere up to the build limit; or the column not known). Vanilla's snow is only drawn near such columns (snowNear).
     */
    private final boolean[] snow = new boolean[SIZE * SIZE];
    /** The biome's answer per column, valid while the column holds world (knownX, knownZ) with its MOTION_BLOCKING top at knownH. */
    private final int[] knownX = new int[SIZE * SIZE], knownZ = new int[SIZE * SIZE], knownH = new int[SIZE * SIZE];
    private final boolean[] knownRain = new boolean[SIZE * SIZE], knownSnow = new boolean[SIZE * SIZE];
    {
        java.util.Arrays.fill(knownX, Integer.MIN_VALUE);
    }
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private ClientLevel level;
    private int minX = Integer.MIN_VALUE, minZ, cursor, texture, allocated = -1;
    private boolean dirty;

    int texture() { return texture; }

    /** Client tick: the window follows the camera; new columns, a share of the rest, a changed one now. */
    void update(ClientLevel level, double camX, double camZ) {
        int nx = Mth.floor(camX) - HALF, nz = Mth.floor(camZ) - HALF;
        if (level != this.level || minX == Integer.MIN_VALUE || Math.abs(nx - minX) >= SIZE || Math.abs(nz - minZ) >= SIZE) {
            this.level = level;
            minX = nx;
            minZ = nz;
            for (int z = nz; z < nz + SIZE; z++) for (int x = nx; x < nx + SIZE; x++) fill(x, z, true);
        } else if (nx != minX || nz != minZ) {
            int ox = minX, oz = minZ;
            minX = nx;
            minZ = nz;
            for (int z = nz; z < nz + SIZE; z++)
                for (int x = nx; x < nx + SIZE; x++)
                    if (x < ox || x >= ox + SIZE || z < oz || z >= oz + SIZE) fill(x, z, true);
        }
        for (int i = 0; i < REFRESH; i++) {
            int k = cursor++ & (SIZE * SIZE - 1);
            fill(minX + Math.floorMod((k & 127) - minX, SIZE), minZ + Math.floorMod((k >> 7) - minZ, SIZE), false);
        }
    }

    /** A block changed (render thread): its column now, if in the window. */
    void changed(BlockPos at) {
        if (level == null || minX == Integer.MIN_VALUE) return;
        int x = at.getX(), z = at.getZ();
        if (x >= minX && x < minX + SIZE && z >= minZ && z < minZ + SIZE) fill(x, z, true);
    }

    /** The rain's top at a column (world y, 1/64 steps), or NaN where it does not rain or the column is not known. */
    double top(int x, int z) {
        if (minX == Integer.MIN_VALUE || x < minX || x >= minX + SIZE || z < minZ || z >= minZ + SIZE) return Double.NaN;
        int i = ((z & 127) * SIZE + (x & 127)) * 4;
        if ((data.get(i + 3) & 255) == 0) return Double.NaN;
        return ((data.get(i) & 255) + ((data.get(i + 1) & 255) << 8)) / UNITS - 512;
    }

    /**
     * Whether it may snow in a column within r of (x, z), or a column there is not in the map: vanilla's renderSnowAndRain
     * (RainVanillaMixin) is skipped otherwise. It checks the biome of each of its 21 x 21 columns every frame only to
     * find the rain AFL has taken over (2026-10-17, JFR: its getBiome calls the largest vanilla cost of a rainy frame).
     */
    public boolean snowNear(int x, int z, int r) {
        if (minX == Integer.MIN_VALUE || x - r < minX || x + r >= minX + SIZE || z - r < minZ || z + r >= minZ + SIZE) return true;
        for (int cz = z - r; cz <= z + r; cz++)
            for (int cx = x - r; cx <= x + r; cx++)
                if (snow[(cz & 127) * SIZE + (cx & 127)]) return true;
        return false;
    }

    /** Whether the rain at a column stops in a mesh tree's crown (only meaningful where top() is known). */
    boolean crown(int x, int z) {
        return crown[(z & 127) * SIZE + (x & 127)];
    }

    /** Heights in the map: 1/64 block steps (+ 512 blocks, 16 bits: up to y 511). */
    static final double UNITS = 64.0;

    /** fresh: ask the biome again (a new column, a changed block); else reuse its answer while the column and its top hold. */
    private void fill(int x, int z, boolean fresh) {
        int i = ((z & 127) * SIZE + (x & 127)) * 4, c = i >> 2;
        dirty = true;
        crown[c] = false;
        snow[c] = true;
        if (!level.hasChunk(x >> 4, z >> 4)) {
            data.putInt(i, 0);
            knownX[c] = Integer.MIN_VALUE;
            return;
        }
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        pos.set(x, h, z);
        boolean rain;
        if (!fresh && knownX[c] == x && knownZ[c] == z && knownH[c] == h) {
            rain = knownRain[c];
            snow[c] = knownSnow[c];
        } else {
            Biome biome = level.getBiome(pos).value();
            rain = biome.hasPrecipitation() && biome.getPrecipitationAt(pos) == Biome.Precipitation.RAIN;
            snow[c] = biome.hasPrecipitation() && biome.getBaseTemperature() < 0.6F;
            knownX[c] = x; knownZ[c] = z; knownH[c] = h;
            knownRain[c] = rain; knownSnow[c] = snow[c];
        }
        // the drawn surface at the column's centre (road pavement, a smoothed slope, a slab's top), else the block top
        double s = rain ? RainGround.surface(level, x + 0.5, z + 0.5, h) : Double.NaN;
        if (Double.isNaN(s) || s > h + 2 || s < h - 3) s = h;
        // a mesh tree's crown above it (its cells have no collision, so MOTION_BLOCKING passes through them): the top cell
        int lit = h;
        for (int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1; y >= h; y--) {
            if (level.getBlockState(pos.setY(y)).getBlock() instanceof TreeBlocks.Foliage) {
                s = lit = y + 1;
                crown[c] = true;
                break;
            }
        }
        pos.setY(lit);
        int v = Mth.clamp((int) Math.round((s + 512) * UNITS), 0, 65535);
        data.put(i, (byte) v);
        data.put(i + 1, (byte) (v >> 8));
        data.put(i + 2, (byte) (level.getBrightness(LightLayer.BLOCK, pos) << 4 | level.getBrightness(LightLayer.SKY, pos)));
        data.put(i + 3, (byte) (rain ? 255 : 0));
    }

    /** Render thread, before drawing: the texture made (nearest, one level) and the changed columns uploaded. */
    void upload() {
        if (texture == 0) texture = GL11.glGenTextures();
        boolean fresh = allocated != texture;
        if (!fresh && !dirty && !ownStale) return;
        ownStale = false;
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        data.position(0);
        dirty = false;
        if (!fresh) {
            // every frame it changed: written without a binding (no glGet; com.antaurora.apofirstlight.client.GlTex)
            com.antaurora.apofirstlight.client.GlTex.subImage(texture, 0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
            return;
        }
        int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        allocated = texture;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SIZE, SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
    }

    /** The shader pack's copy made for this GL id (a re-made texture: all of it again); AFL's own copy then reloads too. */
    private int packAllocated = -1;
    private boolean ownStale;
    private final ByteBuffer header = MemoryUtil.memCalloc(2 * 4);

    /**
     * Render thread: the shader pack's copy (RainShaderPack, dynamic/afl_rain_columns), 128 x 129 RGBA8: rows 0-127 the
     * columns as above, row 128 the header: texel 0 "RAIN" (the pack's check that AFL is there), texel 1 (camera block
     * x & 127, z & 127, frame & 255, 255). Nearest, one level.
     */
    void uploadPack(int id, int camBX, int camBZ, int frame) {
        boolean fresh = packAllocated != id;
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        if (fresh) {
            // once: its storage made on a binding (the only glGet here)
            packAllocated = id;
            int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SIZE, SIZE + 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
        }
        if (fresh || dirty) {
            data.position(0);
            com.antaurora.apofirstlight.client.GlTex.subImage(id, 0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
            dirty = false;
            ownStale = true;
        }
        header.put(0, (byte) 0x52).put(1, (byte) 0x41).put(2, (byte) 0x49).put(3, (byte) 0x4E);
        header.put(4, (byte) (camBX & 127)).put(5, (byte) (camBZ & 127)).put(6, (byte) frame).put(7, (byte) 255);
        header.position(0);
        com.antaurora.apofirstlight.client.GlTex.subImage(id, 0, SIZE, 2, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, header);
    }
}
