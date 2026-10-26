package com.antaurora.apofirstlight.terrainsmooth.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.GradedTurfBlock;
import com.antaurora.apofirstlight.earthwork.EarthChunk;
import com.antaurora.apofirstlight.earthwork.Earthwork;
import com.antaurora.apofirstlight.terrainsmooth.SmoothChunkData;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.atomic.LongAdder;

/**
 * Terrain Smooth V1, the client half (docs/worldgen/terrain_smooth_v1.md): follows the server's state, re-meshes what a
 * block change re-shapes, lifts the local player out of a slope an edit raised under the feet, and answers the chunk
 * models (SmoothSurfaceModel, SinkPlantModel, SnowSkinModel) from the render region plus the chunks' natural surfaces.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class TerrainSmoothClient {
    /** development switches: smooth lighting on sloped tops (off: flat, the approved look) */
    public static volatile boolean aoOnSlopes;
    static volatile boolean shaderPack;
    /** the local player's last tick: > 0 lifted by that much, < 0 a lift something above blocked (diagnostics) */
    public static volatile double lastLift;
    public static long lifts, sectionsMarked;
    // chunk-builder counters (worker threads), natural surface tops only
    public static final LongAdder candidates = new LongAdder(), candidateNanos = new LongAdder(), sloped = new LongAdder(),
            quadBuilds = new LongAdder(), quadNanos = new LongAdder(), plantsMoved = new LongAdder(), snowSkins = new LongAdder();

    static {
        TerrainSmooth.clientHooks = TerrainSmoothClient::blockChanged;
        Earthwork.clientHooks = TerrainSmoothClient::earthChanged;
        com.antaurora.apofirstlight.earthwork.RockCarve.clientHooks = RockCarveMesh::changed;
        com.antaurora.apofirstlight.earthwork.RockCarve.clientChanged = RockCarveMesh::around;
    }

    private TerrainSmoothClient() {}

    /** The server's state (login, development change). Main thread. */
    public static void apply(boolean enabled, boolean shapes) {
        var mc = Minecraft.getInstance();
        TerrainSmooth.clientThread = Thread.currentThread();
        boolean was = TerrainSmooth.CLIENT.enabled();
        TerrainSmooth.CLIENT.set(enabled, shapes);
        if (enabled && mc.level != null) makeLoaded(mc.level);
        if (was != enabled && mc.levelRenderer != null) mc.levelRenderer.allChanged();   // every mesh again: nothing of the other look stays
    }

    /** Natural surfaces of every loaded chunk (they were not kept while off). Main thread. */
    public static void makeLoaded(ClientLevel level) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !TerrainSmooth.applies(level)) return;
        int r = mc.options.getEffectiveRenderDistance() + 2, px = mc.player.getBlockX() >> 4, pz = mc.player.getBlockZ() >> 4;
        for (int x = px - r; x <= px + r; x++) for (int z = pz - r; z <= pz + r; z++) {
            LevelChunk chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
            if (chunk != null) TerrainSmooth.data(level, chunk);
        }
    }

    /**
     * First person, vanilla's in-wall screen (the block's texture at a tenth of its brightness, read as black) comes on
     * when any of 8 points round the eye is in a view-blocking cell; a smoothed or dug top's cell still is one, though its
     * ground lies lower (Earthwork A2, user 2026-10-21: standing in a dug pit the screen went black now and then). Such a
     * cell blocks the view only where a point is inside its real shape; every other block keeps the vanilla answer.
     */
    @SubscribeEvent
    public static void blockScreen(RenderBlockScreenEffectEvent e) {
        if (e.getOverlayType() != RenderBlockScreenEffectEvent.OverlayType.BLOCK) return;
        var p = e.getPlayer();
        var level = p.level();
        if (!TerrainSmooth.notSuffocating(e.getBlockState().getBlock(), level, e.getBlockPos())) return;
        var pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 8; i++) {   // ScreenEffectRenderer#getViewBlockingState's points
            double x = p.getX() + ((i & 1) - 0.5) * p.getBbWidth() * 0.8, y = p.getEyeY() + (((i >> 1) & 1) - 0.5) * 0.1,
                    z = p.getZ() + (((i >> 2) & 1) - 0.5) * p.getBbWidth() * 0.8;
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.getRenderShape() == RenderShape.INVISIBLE || !state.isViewBlocking(level, pos)) continue;
            if (!TerrainSmooth.notSuffocating(state.getBlock(), level, pos)) return;   // a real full block
            var shape = TerrainSmooth.shape(state.getBlock(), level, pos, true);
            if (shape == null) return;
            for (var box : shape.toAabbs())
                if (box.move(pos).contains(x, y, z)) return;   // the eye really is in the ground
        }
        e.setCanceled(true);
    }

    /** Development refresh: make all data again and re-mesh. */
    public static void refresh() {
        var mc = Minecraft.getInstance();
        TerrainSmooth.CLIENT.invalidate();
        if (mc.level != null && TerrainSmooth.CLIENT.enabled()) makeLoaded(mc.level);
        if (mc.levelRenderer != null) mc.levelRenderer.allChanged();
    }

    /** TerrainSmooth.ClientHooks: re-mesh the blocks whose slopes read the changed column, if any slope near it can change. */
    static void blockChanged(int x, int y, int z, int oldSurface, int newSurface) {
        var mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.level == null) return;
        if (oldSurface == newSurface && !surfaceNear(mc.level, x, z, y - 2, y + 2)) return;   // deep under or high above every top around
        int[] r = SmoothRules.affected(y, oldSurface, newSurface);
        int R = SmoothRules.REACH;
        markDirty(x - R, z - R, x + R, z + R, r[0], r[1] + 2);   // + plants standing on the tops, tall ones two high
    }

    private static boolean surfaceNear(ClientLevel level, int x, int z, int lo, int hi) {
        for (int dx = -SmoothRules.REACH; dx <= SmoothRules.REACH; dx++) for (int dz = -SmoothRules.REACH; dz <= SmoothRules.REACH; dz++) {
            LevelChunk c = level.getChunkSource().getChunk((x + dx) >> 4, (z + dz) >> 4, ChunkStatus.FULL, false);
            if (c == null) continue;
            SmoothChunkData d = TerrainSmooth.peek(c, TerrainSmooth.CLIENT);
            if (d == null) continue;
            int s = d.surface(x + dx, z + dz);
            if (s != SmoothRules.NO_SURFACE && s >= lo && s <= hi) return true;
        }
        return false;
    }

    private static void markDirty(int minX, int minZ, int maxX, int maxZ, int minY, int maxY) {
        var renderer = Minecraft.getInstance().levelRenderer;
        for (int sx = Math.floorDiv(minX, 16); sx <= Math.floorDiv(maxX, 16); sx++)
            for (int sz = Math.floorDiv(minZ, 16); sz <= Math.floorDiv(maxZ, 16); sz++)
                for (int sy = Math.floorDiv(minY, 16); sy <= Math.floorDiv(maxY, 16); sy++) {
                    renderer.setSectionDirty(sx, sy, sz);
                    sectionsMarked++;
                }
    }

    // ------------------------------------------------------------------------------------------------- chunk models

    /** Render-region cells (any thread): blocks from the region, natural surfaces from the client level's chunks. */
    static final class RegionCells implements SmoothRules.Cells {
        BlockAndTintGetter region;
        ClientLevel level;
        /** the state of the block whose model data this thread took last, if its top is sloped (for useAmbientOcclusion) */
        BlockState slopedState;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        @Override
        public int cell(int x, int y, int z) {
            int t = Earthwork.editedTop(level, x, z);   // an edited column reads as it was (Earthwork)
            if (t != Integer.MIN_VALUE) return y <= t ? SmoothRules.NATURAL : SmoothRules.AIR;
            return TerrainSmooth.cell(region.getBlockState(pos.set(x, y, z)));
        }

        @Override
        public float turf(int x, int y, int z, int k) {
            if (Earthwork.editedTop(level, x, z) != Integer.MIN_VALUE) return Float.NaN;
            net.minecraft.world.level.block.state.BlockState s = region.getBlockState(pos.set(x, y, z));
            return s != null && s.getBlock() instanceof com.antaurora.apofirstlight.block.GradedTurfBlock ? y + com.antaurora.apofirstlight.block.GradedTurfBlock.corners(s)[k] : Float.NaN;
        }

        @Override
        public int surface(int x, int z) {
            int t = Earthwork.editedTop(level, x, z);
            if (t != Integer.MIN_VALUE) return t;
            LevelChunk c = level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
            if (c == null) return SmoothRules.NO_SURFACE;
            SmoothChunkData d = TerrainSmooth.peek(c, TerrainSmooth.CLIENT);
            return d == null ? SmoothRules.NO_SURFACE : d.surface(x, z);
        }
    }

    private static final ThreadLocal<RegionCells> REGION = ThreadLocal.withInitial(RegionCells::new);

    /**
     * Geometry of the ground block at pos for a chunk mesh (chunk-builder threads), -1 = the vanilla model. Buried blocks
     * end at the first read (the block above in the region is not open). Remembers for #slopedNow which state was sloped.
     */
    static long renderGeometry(BlockAndTintGetter region, BlockPos pos, BlockState state) {
        RegionCells c = REGION.get();
        c.slopedState = null;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !TerrainSmooth.CLIENT.enabled() || !TerrainSmooth.applies(level)) return -1;
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (Earthwork.editedTop(level, x, z) != Integer.MIN_VALUE) return -1;   // the earthwork's skin (EarthSkin), never sloped
        c.region = region;
        c.level = level;
        try {
            int above = c.cell(x, y + 1, z);
            if (!SmoothRules.openOver(c.cell(x, y, z), above)) return -1;
            if (c.surface(x, z) != y) return -1;
            long begin = System.nanoTime();
            long g = SmoothRules.geometry(c, TerrainSmooth.MODE, x, y, z);
            candidates.increment();
            candidateNanos.add(System.nanoTime() - begin);
            if (g >= 0) {
                sloped.increment();
                c.slopedState = state;
            }
            return g;
        } finally {
            c.region = null;
            c.level = null;
        }
    }

    /** per side N, S, W, E of SmoothSurfaceModel: the neighbour's direction and the corner indices at the band's ends a, b */
    private static final Direction[] SIDE_DIRS = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final int[][] SIDE_CORNERS = {{1, 0}, {2, 3}, {0, 2}, {3, 1}};

    /**
     * The sides of a sloped block whose part below the band SmoothSurfaceModel must draw itself (bits 0-3, N S W E), with
     * the light it bakes into that part (bits 4-11: sky << 4 | block, of the cell above the top), 0 for none (nearly every
     * block). docs/worldgen/terrain_smooth_v1.md S: below its band a side is left to the neighbour cell at the block's level
     * whenever the rules count that cell solid. Mesh trunks, roots, stumps, the AFL logs, glass, slabs, torches... are
     * solid for the rules but hide nothing (no full occlusion shape), so that part was drawn by nobody: a see-through
     * window. The light: such a neighbour may block light itself (the AFL logs do), and the flat path lights a culled face
     * from the neighbour cell; the baked light is merged by maximum (Forge and Embeddium), so the part is never darker
     * than the slope's own top.
     */
    static int underSides(BlockAndTintGetter region, BlockPos pos, long g) {
        float[] c = SmoothRules.corners(g);
        int mask = 0;
        boolean above = false;
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (int s = 0; s < 4; s++) {
            float ua = c[SIDE_CORNERS[s][0]], ub = c[SIDE_CORNERS[s][1]];
            float la = Math.min((g >>> (16 + 8 * s) & 15) / 4F, ua), lb = Math.min((g >>> (20 + 8 * s) & 15) / 4F, ub);
            // a band above the block's cell (SmoothSurfaceModel draws it unculled) takes the light baked here
            if ((ua > 1 + 1e-4F || ub > 1 + 1e-4F) && (ua > la + 1e-4F || ub > lb + 1e-4F)) above = true;
            la = Math.min(la, 1);
            lb = Math.min(lb, 1);
            if (la <= 1e-4F && lb <= 1e-4F) continue;   // the whole side is a band
            n.setWithOffset(pos, SIDE_DIRS[s]);
            BlockState neighbour = region.getBlockState(n);
            int k = TerrainSmooth.cell(neighbour);
            if ((k == SmoothRules.NATURAL || k == SmoothRules.OTHER || k == SmoothRules.TREE || k == SmoothRules.ROCK || k == SmoothRules.WET) && !neighbour.isSolidRender(region, n)) mask |= 1 << s;
            // a graded_turf column one level down: its top line may cross this block's floor along the edge, where the
            // band's straight foot and the turf's edge part; the part below the band closes that (2026-10-15, C3.3a seams)
            else if ((k == SmoothRules.AIR || k == SmoothRules.COVER) && region.getBlockState(n.move(Direction.DOWN)).getBlock() instanceof GradedTurfBlock)
                mask |= 1 << s;
        }
        if (mask == 0 && !above) return 0;
        int light = LevelRenderer.getLightColor(region, pos.above());
        return mask | (LightTexture.sky(light) << 4 | LightTexture.block(light)) << 4;
    }

    /**
     * Rock R1b loose stones (boulder/LooseStonesModel, render threads): the drawn top of their support block above its floor
     * as SmoothRules#corners, the road's graded turf's corners, or null where the top is the flat block top.
     */
    public static float[] groundCorners(BlockAndTintGetter region, BlockPos support) {
        BlockState ground = region.getBlockState(support);
        if (ground.getBlock() instanceof GradedTurfBlock) return GradedTurfBlock.corners(ground);
        long g = supportGeometry(region, support);
        return g < 0 ? null : SmoothRules.corners(g);
    }

    /** Geometry of a plant's or a snow layer's support block (render threads), -1 = none or flat; does not touch the AO hand-over. */
    static long supportGeometry(BlockAndTintGetter region, BlockPos support) {
        RegionCells c = REGION.get();
        BlockState kept = c.slopedState;
        try {
            return renderGeometry(region, support, null);
        } finally {
            c.slopedState = kept;
        }
    }

    /**
     * For SmoothSurfaceModel#useAmbientOcclusion: Forge 47 decides smooth lighting per model and state only, so the
     * decision for the block being meshed comes from its getModelData, which vanilla's and Embeddium's chunk builders call
     * right before, on the same thread, for the same block (checked in both, 2026-10-11).
     */
    static boolean slopedNow(BlockState state) { return REGION.get().slopedState == state; }

    /** The same hand-over for an edited column's skin cell (EarthSkin; null: the block is not one). */
    static void markSloped(BlockState state) { REGION.get().slopedState = state; }

    /** Earthwork.ClientHooks: a chunk's new copy. Re-mesh what reads it (its columns and the next ones), drop slopes cached around it. */
    static void earthChanged(int cx, int cz, EarthChunk before, EarthChunk after) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (EarthChunk e : new EarthChunk[]{before, after}) {
            int[] r = e == null ? null : e.yRange();
            if (r == null) continue;
            lo = Math.min(lo, r[0]);
            hi = Math.max(hi, r[1]);
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) TerrainSmooth.forget(mc.level, cx + dx, cz + dz);
        if (lo > hi) return;
        markDirty(cx * 16 - 1, cz * 16 - 1, cx * 16 + 16, cz * 16 + 16, lo, hi + 2);   // + plants standing on the tops
    }

    // ------------------------------------------------------------------------------------------------- events

    /**
     * After the local player's movement (before the position goes to the server): feet cut into a smoothed top means an
     * edit next to the player raised the slope under them; lift them onto it. Vanilla's push out of blocks would have
     * moved them, but smoothed tops do not suffocate. Walking never cuts in (collision).
     */
    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.side.isClient()) return;
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (event.player != player || mc.level == null) return;
        lastLift = 0;
        if (!TerrainSmooth.CLIENT.collides() || player.noPhysics || player.isPassenger() || player.getAbilities().flying) return;
        var box = player.getBoundingBox();
        double up = TerrainSmooth.lift(mc.level, box);
        if (up <= 0) return;
        if (!mc.level.noCollision(player, box.move(0, up, 0).deflate(1.0E-7D))) {
            lastLift = -up;
            return;
        }
        player.setPos(player.getX(), player.getY() + up, player.getZ());
        lifts++;
        lastLift = up;
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        TerrainSmooth.clientThread = Thread.currentThread();
        shaderPack = AflShaderCompat.shaderPackInUse();
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TerrainSmooth.CLIENT.set(false, true);
    }
}
