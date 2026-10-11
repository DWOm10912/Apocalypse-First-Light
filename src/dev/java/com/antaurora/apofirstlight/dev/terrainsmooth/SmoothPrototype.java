package com.antaurora.apofirstlight.dev.terrainsmooth;

import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

/**
 * Terrain smoothing prototype (docs/worldgen/terrain_smooth_visual_prototype_p0.md), the client half, development only.
 * <ul>
 *   <li>P0: the drawn slopes (SmoothSurfaceModel, SinkPlantModel) inside one square test area; modes A (45-degree
 *   single-block slopes) and B (gentle two-block slopes, about 27 degrees).</li>
 *   <li>P1: the same slopes collide and are selected (SmoothWorld, the dev mixins) on both sides, from the same rules
 *   (SmoothRules) read live from the current blocks; the server owns the settings (SmoothCommands) and sends them here.</li>
 * </ul>
 * Since P1 nothing is measured ahead: every chunk build, collision and selection reads the blocks around it, so digging and
 * placing re-shape the slopes at once and nothing stale can stay (P0 left edited columns vanilla instead).
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT)
public final class SmoothPrototype {
    static volatile boolean aoOnSlopes, shaderPack;
    private static long sectionsMarked, lifts;

    // chunk-builder counters (worker threads)
    static final LongAdder dataCalls = new LongAdder(), dataNanos = new LongAdder(), sloped = new LongAdder(),
            quadBuilds = new LongAdder(), quadNanos = new LongAdder(), plantCalls = new LongAdder(), plantMoved = new LongAdder();

    private SmoothPrototype() {}

    static String modeName(int mode) {
        return switch (mode) { case SmoothRules.MODE_A -> "A"; case SmoothRules.MODE_B -> "B"; default -> "OFF"; };
    }

    // ---------------------------------------------------------------- messages from the server command

    static void handle(SmoothCommands.Msg msg) {
        if (FMLEnvironment.production) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        switch (msg.action()) {
            case SmoothCommands.CONFIG -> {
                var c = SmoothWorld.CLIENT;
                boolean was = c.active();
                int oldMinX = c.minX, oldMinZ = c.minZ, oldSize = c.size;
                c.set(msg.mode(), msg.x(), msg.z(), msg.size(), msg.flag(), mc.level.dimension());
                resetCounters();
                if (was) markArea(mc.level, oldMinX, oldMinZ, oldSize);
                if (c.active()) markArea(mc.level, c.minX, c.minZ, c.size);
                if (msg.report()) message(describe(c));
            }
            case SmoothCommands.AO -> {
                aoOnSlopes = msg.flag();
                var c = SmoothWorld.CLIENT;
                if (c.active()) markArea(mc.level, c.minX, c.minZ, c.size);
                message("sloped tops are lit " + (aoOnSlopes ? "with smooth lighting (AO; may darken them)" : "flat (no AO, the default)"));
            }
            case SmoothCommands.REFRESH -> {
                var c = SmoothWorld.CLIENT;
                c.clear();
                if (c.active()) markArea(mc.level, c.minX, c.minZ, c.size);
            }
            case SmoothCommands.STATUS -> status(mc.level);
            case SmoothCommands.PROBE -> SmoothProbe.dump(mc.player, "requested (probe command)");
            default -> {}
        }
    }

    static String describe(SmoothWorld.Config c) {
        if (!c.active()) return "OFF: vanilla terrain, collision and selection";
        return String.format(Locale.ROOT, "mode %s (%s); area x %d..%d, z %d..%d (%d x %d); collision and selection %s",
                modeName(c.mode), c.mode == SmoothRules.MODE_A ? "45-degree single-block slopes" : "gentle two-block slopes, about 27 degrees",
                c.minX, c.minX + c.size - 1, c.minZ, c.minZ + c.size - 1, c.size, c.size,
                c.shapes ? "follow the slopes (" + SmoothRules.GRID + " x " + SmoothRules.GRID + " collision, " + SmoothWorld.SELECT_GRID + " x "
                        + SmoothWorld.SELECT_GRID + " selection boxes per block, outline drawn from the slope)" : "vanilla (visual only)");
    }

    private static void resetCounters() {
        sectionsMarked = 0;
        lifts = 0;
        for (LongAdder a : new LongAdder[]{dataCalls, dataNanos, sloped, quadBuilds, quadNanos, plantCalls, plantMoved}) a.reset();
        for (SmoothWorld.Config c : new SmoothWorld.Config[]{SmoothWorld.CLIENT}) {
            c.queries.reset(); c.hits.reset(); c.computeNanos.reset(); c.clears.reset();
        }
    }

    /** Re-mesh the area's sections between its lowest and highest surface (from the client's heightmaps). */
    private static void markArea(ClientLevel level, int minX, int minZ, int size) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int z = minZ - 1; z <= minZ + size; z++) for (int x = minX - 1; x <= minX + size; x++) {
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
        }
        if (lo == Integer.MAX_VALUE) return;
        // trees lift WORLD_SURFACE: reach down a little further than the lowest top
        markDirty(minX - 2, minZ - 2, minX + size + 1, minZ + size + 1, lo - 40, hi + 3);
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

    /** A block near the area changed on the client: its neighbours' slopes read it from 2 below to 1 above (SmoothRules). */
    static void blockChanged(BlockPos pos) {
        if (!Minecraft.getInstance().isSameThread()) return;
        markDirty(pos.getX() - 1, pos.getZ() - 1, pos.getX() + 1, pos.getZ() + 1, pos.getY() - 2, pos.getY() + 2);
    }

    // ---------------------------------------------------------------- status

    private static boolean cover(BlockState s) { return s.getBlock() instanceof BushBlock || s.getBlock() instanceof SnowLayerBlock; }

    private static void status(ClientLevel level) {
        var c = SmoothWorld.CLIENT;
        message(describe(c) + "; visual + interaction prototype (mining, drops, placement and world data are vanilla); sloped tops lit "
                + (aoOnSlopes ? "with AO" : "flat") + ", shader pack " + (shaderPack ? "on" : "off")
                + "; collision hooks " + (SmoothWorld.hooksSeen() ? "loaded" : "NOT loaded (start the game with gradlew runClient so the dev mixin config is applied)"));
        if (!c.active()) return;
        long begin = System.nanoTime();
        int natural = 0, other = 0, water = 0, unknown = 0, smooth = 0, plants = 0, raised = 0;
        var pos = new BlockPos.MutableBlockPos();
        for (int z = c.minZ; z < c.minZ + c.size; z++) for (int x = c.minX; x < c.minX + c.size; x++) {
            if (!level.hasChunk(x >> 4, z >> 4)) { unknown++; continue; }
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, floor = y - 64;
            BlockState s = null;
            for (; y > floor; y--) {
                s = level.getBlockState(pos.set(x, y, z));
                if (s.isAir() || cover(s) || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) continue;
                break;
            }
            if (s == null || y <= floor) { unknown++; continue; }
            if (!s.getFluidState().isEmpty()) { water++; continue; }
            if (!SmoothWorld.surface(s.getBlock())) { other++; continue; }
            natural++;
            long g = SmoothWorld.geometry(level, c, x, y, z);
            if (g < 0) continue;
            smooth++;
            float[] k = SmoothRules.corners(g);
            if (Math.max(Math.max(k[0], k[1]), Math.max(k[2], k[3])) > 1.001F) raised++;
            if (cover(level.getBlockState(pos.set(x, y + 1, z)))) plants++;
        }
        double ms = (System.nanoTime() - begin) / 1e6;
        message(String.format(Locale.ROOT, "columns %d: natural ground %d, other blocks %d, water %d, not loaded %d; smoothed tops %d (%.1f %% of natural ground), of which rising above their cell %d; plants on them %d (counted in %.0f ms)",
                c.size * c.size, natural, other, water, unknown, smooth, natural == 0 ? 0 : 100.0 * smooth / natural, raised, plants, ms));
        long calls = dataCalls.sum();
        message(String.format(Locale.ROOT, "client chunk builds: %d lookups in the area %.1f ms (%.2f us each), %d sloped tops emitted, %d top shapes built %.1f ms; plants %d checked %d moved; sections marked %d; player lifted out of a raised slope %d times",
                calls, dataNanos.sum() / 1e6, calls == 0 ? 0 : dataNanos.sum() / 1e3 / calls, sloped.sum(), quadBuilds.sum(), quadNanos.sum() / 1e6, plantCalls.sum(), plantMoved.sum(), sectionsMarked, lifts));
        message(c.summary());
    }

    static void message(String text) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(Component.literal("Terrain smooth: " + text), false);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FMLEnvironment.production) return;
        shaderPack = AflShaderCompat.shaderPackInUse();
    }

    /**
     * After the local player's movement (before the position goes to the server): feet cut into a smoothed top means an
     * edit next to the player raised the slope under them; lift them onto it. Vanilla's push out of blocks would have
     * moved them, but smoothed tops do not suffocate (SmoothWorld#notSuffocating). Walking never cuts in (collision).
     */
    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (!event.side.isClient() || FMLEnvironment.production) return;
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var c = SmoothWorld.CLIENT;
        if (event.player != player) return;
        if (mc.level == null || !c.active() || !c.shapes || mc.level.dimension() != c.dimension || !c.near(player.getBlockX(), player.getBlockZ())) {
            SmoothProbe.reset();
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            SmoothProbe.start(player);
            return;
        }
        double lift = 0;   // for the probe: > 0 lifted, < 0 a lift that something above blocked
        if (!player.noPhysics && !player.isPassenger() && !player.getAbilities().flying) {
            var box = player.getBoundingBox();
            double up = SmoothWorld.lift(mc.level, box);
            if (up > 0 && mc.level.noCollision(player, box.move(0, up, 0).deflate(1.0E-7D))) {
                player.setPos(player.getX(), player.getY() + up, player.getZ());
                lifts++;
                lift = up;
            } else if (up > 0) lift = -up;
        }
        SmoothProbe.end(player, lift);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        SmoothWorld.CLIENT.set(SmoothRules.MODE_OFF, 0, 0, 0, true, net.minecraft.world.level.Level.OVERWORLD);
    }
}
