package com.antaurora.apofirstlight.dev;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity;
import com.antaurora.apofirstlight.dev.wiring.WiringCommands;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.wiring.BuildingWiring;
import com.antaurora.apofirstlight.wiring.WiringChunk;
import com.antaurora.apofirstlight.wiring.WiringChunkHolder;
import com.antaurora.apofirstlight.wiring.WiringNode;
import com.antaurora.apofirstlight.wiring.WiringRemoval;
import com.antaurora.apofirstlight.wiring.WiringRouter;
import com.antaurora.apofirstlight.wiring.WiringSupply;
import com.antaurora.apofirstlight.wiring.WiringTool;
import com.antaurora.apofirstlight.worldgen.structure.AflTemplatePlacer;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Building Wiring V1 phase 7 (docs/gameplay/building_wiring_v1.md 11, design 11 "性能"): timings on the server thread.
 * <ul>
 * <li>house: the real exported forest_home_b_m1 (system property {@code afl.perf.template}, set by the init script): the
 * panel's solve, a device's feed lookup, a route as the wrench previews it, the block-change hook (a chunk without wiring,
 * a wired chunk's empty cell, a wired cell kept), the x-ray sync bytes of its chunks.</li>
 * <li>worst: one circuit of 4,096 nodes (WiringSupply.MAX_NODES, a 16 x 16 x 16 stone cube walked end to end) solved; a route
 * the wrench gives up on after 20,000 cells (a 28 x 28 x 28 stone cube to a block it cannot reach); the cube's sync bytes.</li>
 * </ul>
 * Means after a warm-up (JIT); a GameTest server, not the client's integrated one, so the numbers are a guide.
 * Run with {@code src/dev/wiring-perf-gametest.init.gradle}.
 */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class WiringPerfGameTests {
    private static final String NS = "afl_wiring_perf";

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void templates(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)) return;
        empty(event.getServer().overworld(), "house_empty", 40, 15, 29);
        empty(event.getServer().overworld(), "worst_empty", 34, 34, 34);
    }

    private static void empty(ServerLevel level, String name, int sx, int sy, int sz) throws Exception {
        var tag = TagParser.parseTag("{size:[" + sx + "," + sy + "," + sz + "],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        var blocks = new ListTag();
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) {
            var block = new CompoundTag();
            var position = new ListTag();
            for (int c : new int[]{x, y, z}) position.add(IntTag.valueOf(c));
            block.put("pos", position);
            block.putInt("state", 0);
            blocks.add(block);
        }
        tag.put("blocks", blocks);
        level.getStructureManager().getOrCreate(new ResourceLocation(NS, name)).load(level.holderLookup(Registries.BLOCK), tag);
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(
                new TestFunction("wiring_perf_house", NS + ":house", NS + ":house_empty", 400, 0L, true, WiringPerfGameTests::house),
                new TestFunction("wiring_perf_worst", NS + ":worst", NS + ":worst_empty", 400, 0L, true, WiringPerfGameTests::worst));
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }

    /** Microseconds per run, the mean after a warm-up. */
    private static double micros(int warm, int runs, Runnable r) {
        for (int i = 0; i < warm; i++) r.run();
        long t = System.nanoTime();
        for (int i = 0; i < runs; i++) r.run();
        return (System.nanoTime() - t) / 1000.0 / runs;
    }

    private static String f(double v) { return String.format(Locale.ROOT, v < 10 ? "%.2f" : "%.0f", v); }

    /** The x-ray sync packet (WiringPackets.ChunkWires) of every chunk the box touches: chunks with wire, nodes, bytes. */
    private static int[] sync(ServerLevel level, BlockPos min, BlockPos max) {
        int chunks = 0, nodes = 0, bytes = 0;
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
            LevelChunk chunk = level.getChunk(cx, cz);
            WiringChunk w = ((WiringChunkHolder) chunk).afl$wiring();
            if (w == null || w.isEmpty()) continue;
            int[] keys = w.keys();
            FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
            b.writeInt(cx);
            b.writeInt(cz);
            b.writeVarIntArray(keys);
            b.writeLongArray(w.values(keys));
            chunks++;
            nodes += keys.length;
            bytes += b.readableBytes();
            b.release();
        }
        return new int[]{chunks, nodes, bytes};
    }

    private static void house(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String file = System.getProperty("afl.perf.template", "");
        check(!file.isEmpty() && Files.isRegularFile(Path.of(file)), "no template at afl.perf.template: " + file);
        StructureTemplate template = new StructureTemplate();
        try (InputStream in = Files.newInputStream(Path.of(file))) {
            template.load(level.holderLookup(Registries.BLOCK), NbtIo.readCompressed(in));
        } catch (Exception e) {
            throw new GameTestAssertException("cannot read " + file + ": " + e);
        }
        BlockPos at = helper.absolutePos(new BlockPos(1, 0, 1));
        check(AflTemplatePlacer.place(level, template, at, Rotation.NONE, Mirror.NONE, false), "the house placed nothing");
        // the panel: the only distribution panel in the house (the export's load center, 26 5 12)
        BlockPos panelPos = null;
        var size = template.getSize();
        for (BlockPos p : BlockPos.betweenClosed(at, at.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1)))
            if (level.getBlockEntity(p) instanceof DistributionPanelBlockEntity) { panelPos = p.immutable(); break; }
        check(panelPos != null, "no panel in the house");
        DistributionPanelBlockEntity panel = (DistributionPanelBlockEntity) level.getBlockEntity(panelPos);
        panel.serverTick();   // unpacks the wiring, solves
        BlockState panelState = level.getBlockState(panelPos);
        WiringSupply.Result r = panel.wiring();
        check(r != null, "the house panel did not solve");
        int devices = 0;
        for (int c = 1; c <= WiringNode.CIRCUITS; c++) devices += r.devices[c];
        final BlockPos pp = panelPos;
        double solve = micros(200, 2000, () -> WiringSupply.solve(level, pp, panelState));
        panel.serverTick();

        // every wired cell of the house, and the devices on it
        BlockPos max = at.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(at, max)) if (BuildingWiring.any(level, p)) cells.add(p.immutable());
        List<BlockPos> fed = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(at, max)) if (WiringSupply.feed(level, p) != null) fed.add(p.immutable());
        check(!fed.isEmpty(), "no device on the house's wire");
        int[] k = {0};
        double feed = micros(20_000, 200_000, () -> WiringSupply.feed(level, fed.get(k[0]++ % fed.size()))) * 1000;

        // a route the wrench would preview: from the panel's mount to the wired cell farthest from it within the route cap
        BlockPos mount = BuildingWiring.mount(panelState, panelPos);
        BlockPos far = mount;
        for (BlockPos p : cells) if (p.distManhattan(mount) > far.distManhattan(mount) && p.distManhattan(mount) < WiringTool.ROUTE_CELLS / 2) far = p;
        final BlockPos to = far;
        List<BlockPos> path = WiringRouter.route(level, mount, to, 2, WiringTool.ROUTE_MARGIN, WiringTool.ROUTE_VISITS, WiringTool.ROUTE_CELLS);
        double route = micros(20, 200, () -> WiringRouter.route(level, mount, to, 2, WiringTool.ROUTE_MARGIN, WiringTool.ROUTE_VISITS, WiringTool.ROUTE_CELLS));

        // the block-change hook (WiringRemoval.changed, from every ServerLevel#onBlockStateChange)
        BlockPos wired = cells.get(cells.size() / 2), empty = null, bare = null;
        for (BlockPos p : BlockPos.betweenClosed(at, max)) {
            if (empty == null && level.getBlockState(p).isAir() && !BuildingWiring.any(level, p)) {
                WiringChunk w = ((WiringChunkHolder) level.getChunkAt(p)).afl$wiring();
                if (w != null && !w.isEmpty()) empty = p.immutable();
            }
        }
        check(empty != null, "no empty cell in a wired chunk");
        for (int dx = 0; bare == null && dx < 64; dx += 16) {
            BlockPos p = at.offset(-32 - dx, 0, 0);
            WiringChunk w = ((WiringChunkHolder) level.getChunkAt(p)).afl$wiring();
            if (w == null || w.isEmpty()) bare = p;
        }
        check(bare != null, "no chunk without wiring");
        final BlockPos e = empty, b = bare;
        BlockState wiredState = level.getBlockState(wired), air = Blocks.AIR.defaultBlockState();
        double hookBare = micros(50_000, 500_000, () -> WiringRemoval.changed(level, b, air)) * 1000;
        double hookEmpty = micros(50_000, 500_000, () -> WiringRemoval.changed(level, e, air)) * 1000;
        double hookKept = micros(50_000, 500_000, () -> WiringRemoval.changed(level, wired, wiredState)) * 1000;
        check(BuildingWiring.any(level, wired), "the kept cell lost its wire");

        int[] s = sync(level, at, max);
        ApocalypseFirstLight.LOGGER.info("[AFL WIRING PERF] PASS house: {} wired cells, {} devices; solve {} us; feed lookup {} ns; "
                        + "route {} cells ({} -> {}) {} us; block-change hook: no wiring {} ns, empty cell {} ns, wire kept {} ns; "
                        + "x-ray sync {} chunks {} nodes {} bytes",
                cells.size(), devices, f(solve), f(feed), path == null ? "none" : path.size(), mount.toShortString(), to.toShortString(), f(route),
                f(hookBare), f(hookEmpty), f(hookKept), s[0], s[1], s[2]);
        helper.succeed();
    }

    private static void worst(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // a 28 x 28 x 28 stone cube (21,952 cells that hold wire) at 1..28
        for (int x = 1; x <= 28; x++) for (int y = 1; y <= 28; y++) for (int z = 1; z <= 28; z++)
            level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState(), 2);
        // one circuit through its 16 x 16 x 16 corner, end to end (rows, then layers, each turning back): 4,096 nodes
        List<BlockPos> snake = new ArrayList<>();
        int row = 0;
        for (int y = 0; y < 16; y++) for (int zi = 0; zi < 16; zi++, row++) {
            int z = y % 2 == 0 ? zi : 15 - zi;
            for (int xi = 0; xi < 16; xi++) snake.add(helper.absolutePos(new BlockPos(1 + (row % 2 == 0 ? xi : 15 - xi), 1 + y, 1 + z)));
        }
        // the load center on the cube's north face, its mount the walk's first cell
        BlockPos panelPos = helper.absolutePos(new BlockPos(1, 1, 0));
        level.setBlock(panelPos, AflBlocks.LOAD_CENTER.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
        check(snake.get(0).equals(BuildingWiring.mount(level.getBlockState(panelPos), panelPos)), "the walk starts at the panel's mount");
        long t0 = System.nanoTime();
        WiringCommands.layAll(level, snake, 1);
        double lay = (System.nanoTime() - t0) / 1e6;
        BlockState panelState = level.getBlockState(panelPos);
        WiringSupply.Result r = WiringSupply.solve(level, panelPos, panelState);
        check(r.wired[1], "the panel takes the circuit");
        double solve = micros(20, 200, () -> WiringSupply.solve(level, panelPos, panelState));

        // a route the wrench gives up on: corner to a block it cannot reach (air round it), the whole cube in its box
        BlockPos from = helper.absolutePos(new BlockPos(1, 1, 1)), to = helper.absolutePos(new BlockPos(31, 31, 31));
        level.setBlock(to, Blocks.STONE.defaultBlockState(), 2);
        List<BlockPos> none = WiringRouter.route(level, from, to, 2, WiringTool.ROUTE_MARGIN, WiringTool.ROUTE_VISITS, WiringTool.ROUTE_CELLS);
        check(none == null, "the walled-off block was reached");
        double giveUp = micros(3, 20, () -> WiringRouter.route(level, from, to, 2, WiringTool.ROUTE_MARGIN, WiringTool.ROUTE_VISITS, WiringTool.ROUTE_CELLS));
        // and a long route that succeeds: across the cube's far side, about 60 cells
        BlockPos a = helper.absolutePos(new BlockPos(28, 1, 28)), end = helper.absolutePos(new BlockPos(28, 28, 1));
        List<BlockPos> longPath = WiringRouter.route(level, a, end, 2, WiringTool.ROUTE_MARGIN, WiringTool.ROUTE_VISITS, WiringTool.ROUTE_CELLS);
        double longRoute = micros(5, 50, () -> WiringRouter.route(level, a, end, 2, WiringTool.ROUTE_MARGIN, WiringTool.ROUTE_VISITS, WiringTool.ROUTE_CELLS));

        int[] s = sync(level, helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(33, 33, 33)));
        ChunkPos cp = new ChunkPos(snake.get(0));
        ApocalypseFirstLight.LOGGER.info("[AFL WIRING PERF] PASS worst: {} nodes laid in {} ms; solve {} us (partial {}); route given up after {} cells {} us; "
                        + "route {} cells across the cube {} us; x-ray sync {} chunks {} nodes {} bytes (first chunk {})",
                snake.size(), f(lay), f(solve), r.partial, WiringTool.ROUTE_VISITS, f(giveUp), longPath == null ? "none" : longPath.size(), f(longRoute),
                s[0], s[1], s[2], cp);
        helper.succeed();
    }
}
