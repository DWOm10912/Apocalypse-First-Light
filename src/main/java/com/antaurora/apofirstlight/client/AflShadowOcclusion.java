package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.energy.PlugCord;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lossless shadow-pass culling for AFL block entities (docs/dev/render_performance_v1.md, step 1).
 * <p>
 * A shader pack's shadow map keeps, per texel, the depth of the surface nearest to the shadow light (the sun by day, the
 * moon by night). A block entity whose every point is hidden from that light by full opaque blocks never wins that test:
 * the blocks in front of it are nearer to the light on every ray that reaches it, so leaving it out of the shadow pass
 * changes no texel, and every surface it could shade is already shaded by the same blocks. This class proves that per
 * block entity, conservatively, for every ray, not just sampled ones (2026-10-08 review: a sun beam through a one-block
 * opening in a one-block-thick wall can be thinner than any sample spacing):
 * <ul>
 * <li>the faces of its render bounding box that face the light (every ray from inside the box toward the light leaves
 * through one of them) are cut into cells of at most {@link #CELL} blocks;</li>
 * <li>each cell is swept toward the light as a prism, layer by layer along the light's dominant axis. A cell is blocked
 * when one whole layer of its prism's footprint is full opaque cubes ({@code isSolidRender}): every ray of the prism
 * crosses that layer inside the footprint. Glass, panes, doors, slabs, leaves and every other partial or transparent block
 * let light through (windows, curtain walls, doorways);</li>
 * <li>a cube counts only while it is in the shadow map: within the setup's depth toward the light from the camera (the
 * shadow camera has no depth clamp) and in a chunk section Oculus draws ({@link AflShadowLight.Setup});</li>
 * <li>{@link #MAX_DISTANCE} blocks without a blocking layer, the build height, or one unblocked cell keeps the whole block
 * entity in the shadow pass.</li>
 * </ul>
 * Results are cached per block entity and recomputed when the light has turned by more than {@link #ANGLE_DEGREES} (also
 * the sun / moon switch), the camera has moved {@link #CAMERA_MOVE} blocks, the render box changed (a cord picked up), or
 * after {@link #REFRESH_TICKS} ticks (a roof broken above it shows its shadow within about two seconds). At most
 * {@link #BUDGET} block entities and {@link #LOOKUP_BUDGET} block lookups are spent per frame; one still waiting is drawn.
 * A block entity whose plug is carried is never left out (the plug follows the hand, outside the box).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AflShadowOcclusion {
    static final double CELL = 0.5, MAX_DISTANCE = 48.0, CAMERA_MOVE = 8.0, ANGLE_DEGREES = 1.0;
    static final int REFRESH_TICKS = 40, BUDGET = 8, LOOKUP_BUDGET = 12000, MAX_CELLS = 16;
    private static final float ANGLE_COS = (float) Math.cos(Math.toRadians(ANGLE_DEGREES));
    /** below this height of the light above the horizon (sin of the angle) the walk is too long and nothing is skipped */
    private static final float MIN_LIGHT_HEIGHT = 0.03F;
    /** Embeddium tests a chunk section against the shadow box with this margin around its 16 blocks */
    private static final double SECTION_MARGIN = 1.125 + 0.01;

    private static final class Entry {
        boolean occluded;
        float x, y, z;
        double cx, cy, cz;
        AABB box;
        long tick;
    }

    private static final Map<BlockEntity, Entry> CACHE = new WeakHashMap<>();
    private static int budget = BUDGET, lookups;
    private static boolean setupRead;
    private static AflShadowLight.Setup setup;

    private AflShadowOcclusion() {}

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        budget = BUDGET;
        lookups = 0;
        setupRead = false;
    }

    /**
     * Shadow pass only: true when the block entity cannot be lit by the shadow light, so leaving it out of the shadow map
     * changes nothing. {@code toLight}: world-space unit vector from the scene toward the light, or null when unknown.
     */
    public static boolean skip(BlockEntity entity, Vector3f toLight) {
        Level level = entity.getLevel();
        if (level == null || toLight == null || toLight.y < MIN_LIGHT_HEIGHT) return false;
        if (entity instanceof PlugCord.Owner owner && owner.plugCord() != null && owner.plugCord().carrierId() >= 0) return false;
        if (!setupRead) { setupRead = true; setup = AflShadowLight.setup(); }
        if (setup == null) return false;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        AABB box = entity.getRenderBoundingBox();
        long now = level.getGameTime();
        Entry e = CACHE.get(entity);
        boolean stale = e == null || now - e.tick >= REFRESH_TICKS + (entity.getBlockPos().hashCode() & 7)
                || e.x * toLight.x + e.y * toLight.y + e.z * toLight.z < ANGLE_COS
                || camera.distanceToSqr(e.cx, e.cy, e.cz) > CAMERA_MOVE * CAMERA_MOVE || !box.equals(e.box);
        if (stale) {
            if (budget <= 0 || lookups >= LOOKUP_BUDGET) {   // this frame's checks are used up: draw it, check it next frame
                if (e != null) e.occluded = false;
                return false;
            }
            budget--;
            if (e == null) { e = new Entry(); CACHE.put(entity, e); }
            e.occluded = occluded(level, box, toLight, camera, setup);
            e.x = toLight.x; e.y = toLight.y; e.z = toLight.z;
            e.cx = camera.x; e.cy = camera.y; e.cz = camera.z;
            e.box = box;
            e.tick = now;
        }
        return e.occluded;
    }

    /** For the debug outline: the last result (true = left out of the shadow pass), or null before the first check. */
    public static Boolean lastResult(BlockEntity entity) {
        Entry e = CACHE.get(entity);
        return e == null ? null : e.occluded;
    }

    /** Every ray from the light-facing faces of the box is stopped by a full layer of opaque cubes in the shadow map. */
    static boolean occluded(Level level, AABB box, Vector3f l, Vec3 camera, AflShadowLight.Setup setup) {
        if (l.y < MIN_LIGHT_HEIGHT || box.getXsize() > 48 || box.getYsize() > 48 || box.getZsize() > 48) return false;
        AABB b = box.deflate(0.01);
        double[] lo = {b.minX, b.minY, b.minZ}, hi = {b.maxX, b.maxY, b.maxZ};
        double[] d = {l.x, l.y, l.z};
        int k = Math.abs(d[0]) >= Math.abs(d[1]) && Math.abs(d[0]) >= Math.abs(d[2]) ? 0 : Math.abs(d[1]) >= Math.abs(d[2]) ? 1 : 2;
        var cursor = new BlockPos.MutableBlockPos();
        double[] cellLo = new double[3], cellHi = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(d[axis]) < 1e-4) continue;   // faces parallel to the light take no light directly
            int u = (axis + 1) % 3, v = (axis + 2) % 3;
            double plane = d[axis] > 0 ? hi[axis] : lo[axis];
            int nu = cells(hi[u] - lo[u]), nv = cells(hi[v] - lo[v]);
            for (int i = 0; i < nu; i++) for (int j = 0; j < nv; j++) {
                cellLo[axis] = cellHi[axis] = plane;
                cellLo[u] = lo[u] + (hi[u] - lo[u]) * i / nu; cellHi[u] = lo[u] + (hi[u] - lo[u]) * (i + 1) / nu;
                cellLo[v] = lo[v] + (hi[v] - lo[v]) * j / nv; cellHi[v] = lo[v] + (hi[v] - lo[v]) * (j + 1) / nv;
                if (!blocked(level, cursor, cellLo, cellHi, d, k, camera, setup)) return false;
            }
        }
        return true;
    }

    /** Cells along one face edge: at most {@link #CELL} blocks, at most {@link #MAX_CELLS} (larger cells only test more cubes). */
    static int cells(double extent) {
        return Math.max(1, Math.min(MAX_CELLS, Mth.ceil(extent / CELL)));
    }

    /**
     * The prism swept from the cell [lo, hi] along d is stopped: walking the integer layers along the dominant axis k toward
     * the light, some layer has every cube its footprint touches opaque and inside the shadow map.
     */
    static boolean blocked(Level level, BlockPos.MutableBlockPos cursor, double[] lo, double[] hi, double[] d, int k,
                           Vec3 camera, AflShadowLight.Setup setup) {
        int u = (k + 1) % 3, v = (k + 2) % 3;
        double dk = d[k];
        int step = dk > 0 ? 1 : -1;
        // the first layer every ray of the prism crosses: the one holding the cell's far end along k (a ray starting
        // lower along k would cross earlier layers, one starting at the far end does not)
        int first = dk > 0 ? Mth.floor(hi[k]) : Mth.floor(lo[k] - 1e-9);
        int layers = Mth.ceil(MAX_DISTANCE * Math.abs(dk)) + 1;
        int maxY = level.getMaxBuildHeight(), minY = level.getMinBuildHeight();
        int[] c = new int[3];
        for (int n = 0, layer = first; n <= layers; n++, layer += step) {
            if (k == 1 && (layer >= maxY || layer < minY)) return false;   // open sky above the world
            // the part of the rays' parameter range inside this layer
            double t0, t1;
            if (dk > 0) { t0 = (layer - hi[k]) / dk; t1 = (layer + 1 - lo[k]) / dk; }
            else { t0 = (layer + 1 - lo[k]) / dk; t1 = (layer - hi[k]) / dk; }
            t0 = Math.max(0, t0);
            if (t1 < 0 || t0 > MAX_DISTANCE) continue;
            int u0 = Mth.floor(lo[u] + Math.min(t0 * d[u], t1 * d[u])), u1 = Mth.floor(hi[u] + Math.max(t0 * d[u], t1 * d[u]));
            int v0 = Mth.floor(lo[v] + Math.min(t0 * d[v], t1 * d[v])), v1 = Mth.floor(hi[v] + Math.max(t0 * d[v], t1 * d[v]));
            if (full(level, cursor, c, k, layer, u, u0, u1, v, v0, v1, d, camera, setup)) return true;
            if (n > 0 && depthOf(c, d, camera, layer, k, u0, v0) > setup.occluderDepth() + 2) return false;   // only further from here
        }
        return false;
    }

    /** Every cube of the layer's footprint is opaque, in a loaded chunk, and in the shadow map. */
    private static boolean full(Level level, BlockPos.MutableBlockPos cursor, int[] c, int k, int layer, int u, int u0, int u1,
                                int v, int v0, int v1, double[] d, Vec3 camera, AflShadowLight.Setup setup) {
        for (int a = u0; a <= u1; a++) for (int b = v0; b <= v1; b++) {
            c[k] = layer; c[u] = a; c[v] = b;
            lookups++;
            if (!level.hasChunk(c[0] >> 4, c[2] >> 4)) return false;
            if ((c[0] + 0.5 - camera.x) * d[0] + (c[1] + 0.5 - camera.y) * d[1] + (c[2] + 0.5 - camera.z) * d[2] + 0.87 > setup.occluderDepth())
                return false;
            if (boxCulled(c, camera, setup.boxDistance())) return false;
            cursor.set(c[0], c[1], c[2]);
            if (!level.getBlockState(cursor).isSolidRender(level, cursor)) return false;
        }
        return true;
    }

    private static double depthOf(int[] c, double[] d, Vec3 camera, int layer, int k, int u0, int v0) {
        c[k] = layer; c[(k + 1) % 3] = u0; c[(k + 2) % 3] = v0;
        return (c[0] + 0.5 - camera.x) * d[0] + (c[1] + 0.5 - camera.y) * d[1] + (c[2] + 0.5 - camera.z) * d[2] - 2;
    }

    /** The cube's chunk section lies outside Oculus' shadow box (BoxCuller): not drawn into the shadow map. */
    private static boolean boxCulled(int[] c, Vec3 camera, double distance) {
        if (Double.isInfinite(distance)) return false;
        double sx = c[0] & ~15, sy = c[1] & ~15, sz = c[2] & ~15, m = SECTION_MARGIN;
        return sx - m - camera.x > distance || sx + 16 + m - camera.x < -distance
                || sy - m - camera.y > distance || sy + 16 + m - camera.y < -distance
                || sz - m - camera.z > distance || sz + 16 + m - camera.z < -distance;
    }
}
