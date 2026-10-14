package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.GeneratorInletBoxBlock;
import com.antaurora.apofirstlight.block.PorcelainLampholderBlock;
import com.antaurora.apofirstlight.cabin.CabinBlocks;
import com.antaurora.apofirstlight.dev.wiring.WiringCommands;
import com.antaurora.apofirstlight.meshhit.MeshBlockHitResult;
import com.antaurora.apofirstlight.meshhit.MeshHitModels;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.wiring.BuildingWiring;
import com.antaurora.apofirstlight.wiring.WiringChunk;
import com.antaurora.apofirstlight.wiring.WiringChunkHolder;
import com.antaurora.apofirstlight.wiring.WiringNode;
import com.antaurora.apofirstlight.wiring.WiringRouter;
import com.antaurora.apofirstlight.wiring.WiringStorage;
import com.antaurora.apofirstlight.wiring.WiringTool;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Building Wiring V1 phase 1 (docs/gameplay/building_wiring_v1.md 9): the data core and removal, on the real server code.
 * <ul>
 *   <li>nodes: packing, the wireable rules and the no_wiring tag, two-way links, cut and clear, circuits independent,
 *   taps (panel per circuit, loads on circuits 1-4, service entries on the feeder only, one per device), the router;</li>
 *   <li>save: wiring across a chunk boundary through the real chunk save (ServerChunkCache#save, the region file) read back
 *   and loaded into empty chunks; damaged entries dropped; a version 1 save (twelve circuits) merged into four by colour; a chunk without wire writes no entry;</li>
 *   <li>survival: a player breaking a wired block gets one wire per node dropped there, once (a wrench cut before it
 *   refunds its own node only); the wrench lays with wire from the inventory and fills a skipped gap;</li>
 *   <li>creative: breaking drops nothing, laying costs nothing;</li>
 *   <li>cleanup: an explosion, a piston and /setblock-style changes clear the cells without any wire item; a cell turned
 *   into another block that holds wire keeps it.</li>
 *   <li>slopes (2026-10-13): the slope bits; a 1:2 roof of real AFL roof cells and a flight of stairs link along the
 *   slope, the router steps along it, cut and a replaced cell drop the slope links, a corner cell with the circuit turns it
 *   with straight links instead;</li>
 *   <li>corner: a floor wire laid beside a wall wire's end joins it through the corner cell for one more wire, skipped
 *   without one;</li>
 *   <li>shapes: the cabin blocks' outlines and collision follow the mesh while the log wall stays a full block for light,
 *   support and mobs (never suffocating: the next cell is free to stand in), the hit meshes follow the drawn pieces (not the
 *   gun rack), and the roof still holds a ceiling fixture.</li>
 * </ul>
 * Run with {@code src/dev/wiring-gametest.init.gradle} (and -PaflWithoutShaders).
 */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class WiringGameTests {
    private static final String NS = "afl_wiring_tests", TOKEN = "[AFL WIRING TEST] PASS ";
    static final String[] NAMES = {"nodes", "save", "survival", "creative", "cleanup", "slopes", "corner", "shapes"};

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)) return;
        var level = event.getServer().overworld();
        // 20 x 8 x 20 of air: wide enough that every placement crosses a chunk boundary along x and z
        var tag = TagParser.parseTag("{size:[20,8,20],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        var blocks = new ListTag();
        for (int x = 0; x < 20; x++) for (int y = 0; y < 8; y++) for (int z = 0; z < 20; z++) {
            var block = new CompoundTag();
            var position = new ListTag();
            for (int c : new int[]{x, y, z}) position.add(IntTag.valueOf(c));
            block.put("pos", position);
            block.putInt("state", 0);
            blocks.add(block);
        }
        tag.put("blocks", blocks);
        level.getStructureManager().getOrCreate(new ResourceLocation(NS, "wiring_empty")).load(level.holderLookup(Registries.BLOCK), tag);
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        List<TestFunction> out = new ArrayList<>();
        for (String name : NAMES) out.add(new TestFunction("afl_wiring", NS + ":" + name, NS + ":wiring_empty", 200, 0L, true, h -> run(name, h)));
        return out;
    }

    private static void run(String name, GameTestHelper h) {
        switch (name) {
            case "nodes" -> nodes(h);
            case "save" -> save(h);
            case "survival" -> survival(h);
            case "creative" -> creative(h);
            case "cleanup" -> cleanup(h);
            case "slopes" -> slopes(h);
            case "corner" -> corner(h);
            case "shapes" -> shapes(h);
            default -> throw new IllegalArgumentException(name);
        }
    }

    private static void pass(GameTestHelper h, String name, String detail) {
        ApocalypseFirstLight.LOGGER.info(TOKEN + name + " " + detail);
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------------- nodes

    private static void nodes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // packing: every field round trips and leaves the others alone
        long n = WiringNode.of(3);
        n = WiringNode.link(n, Direction.EAST, true);
        n = WiringNode.link(n, Direction.DOWN, true);
        n = WiringNode.tap(n, Direction.NORTH, 5);
        n = WiringNode.tap(n, Direction.UP, WiringNode.PHOTOCELL);
        h.assertTrue(WiringNode.circuit(n) == 3 && WiringNode.colour(n) == WiringNode.GREEN, "circuit packed, its colour green");
        h.assertTrue(WiringNode.linked(n, Direction.EAST) && WiringNode.linked(n, Direction.DOWN) && !WiringNode.linked(n, Direction.WEST), "links packed");
        h.assertTrue(WiringNode.group(n, Direction.NORTH) == 5 && WiringNode.group(n, Direction.UP) == WiringNode.PHOTOCELL && !WiringNode.tapped(n, Direction.SOUTH), "taps packed");
        n = WiringNode.untap(n, Direction.NORTH);
        h.assertTrue(!WiringNode.tapped(n, Direction.NORTH) && WiringNode.group(n, Direction.NORTH) == 0 && WiringNode.tapped(n, Direction.UP), "untap clears one tap");
        h.assertTrue(n >>> 50 == 0, "node fits 50 bits");
        h.assertTrue(WiringNode.colourOf(1) == WiringNode.RED && WiringNode.colourOf(2) == WiringNode.BLUE && WiringNode.colourOf(4) == WiringNode.YELLOW
                && WiringNode.colourOf(WiringNode.FEEDER) == WiringNode.BLACK && WiringNode.FEEDER == WiringNode.BREAKERS + 1, "four circuits, colour = circuit, the feeder black");

        // the wireable rules
        h.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        h.setBlock(new BlockPos(2, 1, 1), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        h.setBlock(new BlockPos(3, 1, 1), Blocks.GLASS);
        h.setBlock(new BlockPos(4, 1, 1), Blocks.CHEST);
        h.setBlock(new BlockPos(5, 1, 1), Blocks.OAK_DOOR);
        h.setBlock(new BlockPos(6, 1, 1), Blocks.ICE);
        h.setBlock(new BlockPos(7, 1, 1), Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        h.setBlock(new BlockPos(8, 1, 1), Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.WATERLOGGED, true));
        h.setBlock(new BlockPos(9, 1, 1), Blocks.DIRT);
        boolean[] want = {true, true, false, false, false, false, false, false, true};
        for (int i = 0; i < want.length; i++)
            h.assertTrue(BuildingWiring.wireable(level, h.absolutePos(new BlockPos(1 + i, 1, 1))) == want[i], "wireable rule at x " + (1 + i));
        h.assertTrue(!BuildingWiring.wireable(level, h.absolutePos(new BlockPos(1, 2, 1))), "air holds no wire");
        h.setBlock(new BlockPos(8, 1, 1), Blocks.AIR);   // before its water flows out (fluid ticks come later)

        // a straight run of five: links both ways, ends without outward links
        List<BlockPos> run = new ArrayList<>();
        for (int x = 1; x <= 5; x++) { h.setBlock(new BlockPos(x, 1, 4), Blocks.STONE); run.add(h.absolutePos(new BlockPos(x, 1, 4))); }
        h.assertTrue(WiringCommands.layAll(level, run, 1) == 5 && WiringCommands.layAll(level, run, 1) == 0, "five new nodes, then none");
        WiringCommands.layAll(level, run, 2);
        for (int i = 0; i < 5; i++) {
            long a = BuildingWiring.node(level, run.get(i), 1);
            h.assertTrue(WiringNode.linked(a, Direction.EAST) == (i < 4) && WiringNode.linked(a, Direction.WEST) == (i > 0), "run links at " + i);
        }
        h.assertTrue(BuildingWiring.cut(level, run.get(2), 1) == 1 && BuildingWiring.cut(level, run.get(2), 1) == 0, "cut once");
        h.assertTrue(!WiringNode.linked(BuildingWiring.node(level, run.get(1), 1), Direction.EAST)
                && !WiringNode.linked(BuildingWiring.node(level, run.get(3), 1), Direction.WEST), "cut removes the neighbours' links");
        h.assertTrue(WiringNode.linked(BuildingWiring.node(level, run.get(2), 2), Direction.EAST), "the other circuit untouched");
        h.assertTrue(BuildingWiring.clear(level, run.get(4)) == 2 && BuildingWiring.circuits(level, run.get(4)).length == 0, "clear takes every circuit");

        // taps: panel on a wall (each circuit and the feeder), lamp under a ceiling (circuits only), inlet (feeder only)
        BlockPos wall = new BlockPos(10, 2, 10);
        h.setBlock(wall, Blocks.STONE);
        h.setBlock(wall.east(), AflBlocks.LOAD_CENTER.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        BlockPos panel = h.absolutePos(wall.east()), w = h.absolutePos(wall);
        WiringCommands.layAll(level, List.of(w), 1);
        WiringCommands.layAll(level, List.of(w), 2);
        WiringCommands.layAll(level, List.of(w), WiringNode.FEEDER);
        var panelState = level.getBlockState(panel);
        h.assertTrue(BuildingWiring.tapped(level, panel, panelState, 1) && BuildingWiring.tapped(level, panel, panelState, 2)
                && BuildingWiring.tapped(level, panel, panelState, WiringNode.FEEDER), "the panel takes one tap per circuit and the feeder");

        BlockPos ceiling = new BlockPos(14, 4, 10);
        h.setBlock(ceiling, Blocks.STONE);
        h.setBlock(ceiling.below(), AflBlocks.PORCELAIN_LAMPHOLDER.get().defaultBlockState().setValue(PorcelainLampholderBlock.FACING, Direction.DOWN));
        BlockPos lamp = h.absolutePos(ceiling.below()), c = h.absolutePos(ceiling);
        var lampState = level.getBlockState(lamp);
        h.assertTrue(lampState.is(AflBlocks.PORCELAIN_LAMPHOLDER.get()), "lamp placed");
        WiringCommands.layAll(level, List.of(c), WiringNode.FEEDER);
        h.assertTrue(!BuildingWiring.tapped(level, lamp, lampState, WiringNode.FEEDER), "the feeder does not tap a load");
        WiringCommands.layAll(level, List.of(c), 3);
        WiringCommands.layAll(level, List.of(c), 4);
        h.assertTrue(WiringNode.tapped(BuildingWiring.node(level, c, 3), Direction.DOWN) && !WiringNode.tapped(BuildingWiring.node(level, c, 4), Direction.DOWN),
                "a load takes one tap, from the first circuit");
        h.assertTrue(BuildingWiring.untap(level, lamp, lampState) == 1 && !BuildingWiring.tapped(level, lamp, lampState, 3), "untap frees the device");
        h.assertTrue(BuildingWiring.autoTap(level, c, 4) == 1, "then another circuit may tap it");

        BlockPos outside = new BlockPos(17, 2, 10);
        h.setBlock(outside, Blocks.STONE);
        h.setBlock(outside.south(), AflBlocks.GENERATOR_INLET_BOX.get().defaultBlockState().setValue(GeneratorInletBoxBlock.FACING, Direction.SOUTH));
        BlockPos inlet = h.absolutePos(outside.south()), o = h.absolutePos(outside);
        WiringCommands.layAll(level, List.of(o), 4);
        h.assertTrue(!BuildingWiring.tapped(level, inlet, level.getBlockState(inlet), 4), "a circuit does not tap a service entry");
        WiringCommands.layAll(level, List.of(o), WiringNode.FEEDER);
        h.assertTrue(BuildingWiring.tapped(level, inlet, level.getBlockState(inlet), WiringNode.FEEDER), "the feeder taps the inlet");
        h.assertTrue(!BuildingWiring.wireable(level, inlet) && !BuildingWiring.wireable(level, lamp), "devices hold no wire");

        // the router: around a glass pane in a stone wall, every step one cell into a wireable cell; none through glass only
        for (int x = 1; x <= 7; x++) for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, 14), Blocks.STONE);
        h.setBlock(new BlockPos(4, 1, 14), Blocks.GLASS);
        h.setBlock(new BlockPos(4, 2, 14), Blocks.GLASS);
        BlockPos from = h.absolutePos(new BlockPos(1, 1, 14)), to = h.absolutePos(new BlockPos(7, 1, 14));
        List<BlockPos> path = WiringRouter.route(level, from, to, 6, 2, 4000, 40);
        h.assertTrue(path != null && path.get(0).equals(from) && path.get(path.size() - 1).equals(to), "a route around the glass");
        for (int i = 0; i < path.size(); i++) {
            h.assertTrue(BuildingWiring.wireable(level, path.get(i)), "route cell " + i + " holds wire");
            if (i > 0) h.assertTrue(path.get(i).distManhattan(path.get(i - 1)) == 1, "route steps one cell");
        }
        h.assertTrue(path.size() == 11, "shortest way over the glass: 6 along + 2 up + 2 down + 1, got " + path.size());
        h.setBlock(new BlockPos(4, 3, 14), Blocks.GLASS);
        h.assertTrue(WiringRouter.route(level, from, to, 6, 0, 4000, 40) == null, "no route when glass closes the wall (margin 0)");
        pass(h, "nodes", "packing, rules, links, taps, router");
    }

    // -------------------------------------------------------------------------------------------------------- save

    private static void save(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        List<BlockPos> run = new ArrayList<>();
        for (int x = 0; x < 20; x++) { h.setBlock(new BlockPos(x, 1, 2), Blocks.DIRT); run.add(h.absolutePos(new BlockPos(x, 1, 2))); }
        WiringCommands.layAll(level, run, 1);
        WiringCommands.layAll(level, run.subList(3, 9), WiringNode.FEEDER);
        Set<ChunkPos> chunks = new HashSet<>();
        for (BlockPos p : run) chunks.add(new ChunkPos(p));
        h.assertTrue(chunks.size() >= 2, "the run crosses a chunk boundary");

        // the real save: ChunkMap posts ChunkDataEvent.Save, the region file gets the entry
        level.getChunkSource().save(true);
        for (ChunkPos cp : chunks) {
            LevelChunk chunk = level.getChunk(cp.x, cp.z);
            WiringChunk before = ((WiringChunkHolder) chunk).afl$wiring();
            h.assertTrue(before != null && !before.isEmpty(), "chunk " + cp + " holds wire");
            CompoundTag stored = level.getChunkSource().chunkMap.read(cp).join().orElseThrow();
            h.assertTrue(stored.contains(WiringStorage.TAG, Tag.TAG_COMPOUND), "chunk " + cp + " saved its wiring");
            // loaded into the chunk again (the handler ChunkSerializer#read fires): the same nodes
            ((WiringChunkHolder) chunk).afl$setWiring(null);
            WiringStorage.load(new ChunkDataEvent.Load(chunk, stored, ChunkStatus.ChunkType.LEVELCHUNK));
            WiringChunk after = ((WiringChunkHolder) chunk).afl$wiring();
            h.assertTrue(after != null && after.sameAs(before), "chunk " + cp + " loads the same nodes");
        }

        // damaged entries go, good ones stay; empty wiring writes nothing
        ChunkPos first = new ChunkPos(run.get(0));
        LevelChunk chunk = level.getChunk(first.x, first.z);
        WiringChunk good = ((WiringChunkHolder) chunk).afl$wiring();
        int[] keys = good.keys();
        long[] nodes = good.values(keys);
        CompoundTag bad = new CompoundTag();
        bad.putInt("v", 2);
        int[] k2 = java.util.Arrays.copyOf(keys, keys.length + 1);
        long[] n2 = java.util.Arrays.copyOf(nodes, nodes.length + 1);
        k2[keys.length] = keys[0] & ~(15 << 20) | 5 << 20;     // claims circuit 5, holds a circuit 1 node
        n2[keys.length] = nodes[0];
        bad.putIntArray("keys", k2);
        bad.putLongArray("nodes", n2);
        CompoundTag holder = new CompoundTag();
        holder.put(WiringStorage.TAG, bad);
        WiringStorage.load(new ChunkDataEvent.Load(chunk, holder, ChunkStatus.ChunkType.LEVELCHUNK));
        h.assertTrue(((WiringChunkHolder) chunk).afl$wiring().sameAs(good), "the damaged entry is dropped, the rest loads");

        // a version 1 save (circuits 1-12, feeder 13): each circuit to the one of its colour, two landing in a cell merged
        int base = keys[0] & 0xFFFFF;
        long red1 = WiringNode.link(1L, Direction.EAST, true);
        long red5 = WiringNode.tap(WiringNode.link(5L, Direction.WEST, true), Direction.NORTH, 3);
        long blue10 = WiringNode.link(10L | 1L << 4, Direction.UP, true);
        CompoundTag v1 = new CompoundTag();
        v1.putInt("v", 1);
        v1.putIntArray("keys", new int[]{base | 1 << 20, base | 5 << 20, base | 10 << 20, base | 13 << 20});
        v1.putLongArray("nodes", new long[]{red1, red5, blue10, 13L});
        CompoundTag oldHolder = new CompoundTag();
        oldHolder.put(WiringStorage.TAG, v1);
        WiringStorage.load(new ChunkDataEvent.Load(chunk, oldHolder, ChunkStatus.ChunkType.LEVELCHUNK));
        WiringChunk merged = ((WiringChunkHolder) chunk).afl$wiring();
        int[] mk = merged.keys();
        long[] mn = merged.values(mk);
        long c1 = 0, c2 = 0, feeder = 0;
        for (int i = 0; i < mk.length; i++) {
            int c = WiringChunk.circuitOf(mk[i]);
            if (c == 1) c1 = mn[i];
            else if (c == 2) c2 = mn[i];
            else if (c == WiringNode.FEEDER) feeder = mn[i];
        }
        h.assertTrue(mk.length == 3 && WiringNode.circuit(c1) == 1 && WiringNode.linked(c1, Direction.EAST) && WiringNode.linked(c1, Direction.WEST)
                        && WiringNode.group(c1, Direction.NORTH) == 3 && c2 == WiringNode.link(2L, Direction.UP, true) && feeder == WiringNode.FEEDER,
                "a version 1 save: red 1 and 5 merge into 1, blue 10 to 2, the feeder stays");
        ((WiringChunkHolder) chunk).afl$setWiring(good);
        for (BlockPos p : run) BuildingWiring.clear(level, p);
        for (BlockPos p : run) h.assertTrue(!BuildingWiring.any(level, p), "cleared " + p.toShortString());
        // the empty rule on its own (the chunk may also hold a neighbouring test's wire): no data and empty data write nothing
        WiringChunk kept = ((WiringChunkHolder) chunk).afl$wiring();
        for (WiringChunk none : new WiringChunk[]{null, new WiringChunk()}) {
            ((WiringChunkHolder) chunk).afl$setWiring(none);
            CompoundTag empty = new CompoundTag();
            WiringStorage.save(new ChunkDataEvent.Save(chunk, level, empty));
            h.assertTrue(!empty.contains(WiringStorage.TAG), "a chunk without wire saves no entry");
        }
        ((WiringChunkHolder) chunk).afl$setWiring(kept);
        pass(h, "save", chunks.size() + " chunks through the region file");
    }

    // ---------------------------------------------------------------------------------------------------- survival

    private static ServerPlayer player(GameTestHelper h, String name, GameType mode) {
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("afl-wiring-" + name).getBytes()), "afl_wiring_" + name));
        p.setGameMode(mode);
        p.getInventory().clearContent();
        return p;
    }

    private static int wireItems(GameTestHelper h, BlockPos abs) {
        int n = 0;
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(3)))
            if (e.getItem().is(AflItems.INSULATED_COPPER_WIRE.get())) n += e.getItem().getCount();
        return n;
    }

    private static void survival(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = player(h, "survival", GameType.SURVIVAL);
        for (int x = 2; x <= 9; x++) h.setBlock(new BlockPos(x, 1, 3), Blocks.DIRT);
        BlockPos a = h.absolutePos(new BlockPos(2, 1, 3));
        p.moveTo(a.getX() + 3.5, a.getY() + 1, a.getZ() + 2.5, 0, 30);

        // the wrench lays from the inventory: no wire, nothing laid; then one per new cell, a skipped gap filled
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AflItems.WIRING_WRENCH.get()));
        h.assertTrue(WiringTool.use(p, a) == net.minecraft.world.InteractionResult.FAIL && BuildingWiring.node(level, a, 1) == 0, "no wire, no node");
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AflItems.INSULATED_COPPER_WIRE.get(), 10));
        WiringTool.use(p, a);
        WiringTool.use(p, a.east(3));                       // same stroke (same tick): x 3 and 4 are filled in
        for (int i = 0; i <= 3; i++) h.assertTrue(BuildingWiring.node(level, a.east(i), 1) != 0, "stroke cell " + i);
        h.assertTrue(WiringNode.linked(BuildingWiring.node(level, a.east(1), 1), Direction.EAST), "the filled cells are joined");
        h.assertTrue(WiringTool.wires(p) == 6, "four cells cost four wires, left " + WiringTool.wires(p));

        // a second circuit in two of the cells
        WiringTool.select(p, 1);
        h.assertTrue(WiringTool.circuit(p) == 2, "sneak-scroll selects circuit 2");
        WiringTool.use(p, a.east(1));
        WiringTool.use(p, a.east(2));
        h.assertTrue(WiringTool.wires(p) == 4, "two more, left " + WiringTool.wires(p));

        // wrench cut: the selected circuit's node back into the inventory
        WiringTool.cut(p, a.east(1));
        h.assertTrue(BuildingWiring.node(level, a.east(1), 2) == 0 && BuildingWiring.node(level, a.east(1), 1) != 0 && WiringTool.wires(p) == 5, "cut refunds one");

        // breaking by hand: one wire per node left in the cell, once
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos b = a.east(2);   // circuits 1 and 2
        h.assertTrue(p.gameMode.destroyBlock(b), "the player breaks the block");
        h.assertTrue(BuildingWiring.circuits(level, b).length == 0, "the broken cell holds no wire");
        h.assertTrue(!WiringNode.linked(BuildingWiring.node(level, a.east(1), 1), Direction.EAST), "the neighbour lost its link");
        BlockPos c = a.east(1);   // circuit 1 only
        p.gameMode.destroyBlock(c);
        h.runAfterDelay(2, () -> {
            int dropped = wireItems(h, b);
            h.assertTrue(dropped == 3, "2 + 1 wires dropped, got " + dropped);
            pass(h, "survival", "lay 6, cut refund 1, breaks drop 3");
        });
    }

    // ---------------------------------------------------------------------------------------------------- creative

    private static void creative(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = player(h, "creative", GameType.CREATIVE);
        for (int x = 2; x <= 6; x++) h.setBlock(new BlockPos(x, 1, 5), Blocks.DIRT);
        BlockPos a = h.absolutePos(new BlockPos(2, 1, 5));
        p.moveTo(a.getX() + 2.5, a.getY() + 1, a.getZ() + 2.5, 0, 30);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AflItems.WIRING_WRENCH.get()));
        WiringTool.use(p, a);
        WiringTool.use(p, a.east(4));
        for (int i = 0; i <= 4; i++) h.assertTrue(BuildingWiring.node(level, a.east(i), 1) != 0, "creative stroke cell " + i);
        WiringTool.cut(p, a.east(4));
        h.assertTrue(WiringTool.wires(p) == 0, "creative laying costs nothing and cutting gives nothing");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.gameMode.destroyBlock(a.east(1));
        h.assertTrue(BuildingWiring.circuits(level, a.east(1)).length == 0, "creative break clears the cell");
        h.runAfterDelay(2, () -> {
            h.assertTrue(wireItems(h, a.east(1)) == 0, "creative break drops no wire");
            pass(h, "creative", "no cost, no drop");
        });
    }

    // ----------------------------------------------------------------------------------------------------- cleanup

    private static void cleanup(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // explosion: a 3 x 3 pad of dirt, all wired
        List<BlockPos> pad = new ArrayList<>();
        for (int x = 3; x <= 5; x++) for (int z = 3; z <= 5; z++) { h.setBlock(new BlockPos(x, 1, z), Blocks.DIRT); pad.add(h.absolutePos(new BlockPos(x, 1, z))); }
        WiringCommands.layAll(level, pad, 1);
        BlockPos centre = h.absolutePos(new BlockPos(4, 1, 4));
        level.explode(null, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, 2.5F, Level.ExplosionInteraction.TNT);
        int left = 0, gone = 0;
        for (BlockPos p : pad) {
            if (level.getBlockState(p).isAir()) { gone++; if (BuildingWiring.any(level, p)) left++; }
            else if (BuildingWiring.node(level, p, 1) == 0) left += 100;   // a surviving block keeps its wire
        }
        h.assertTrue(gone > 0 && left == 0, "the blasted cells lost their wire (" + gone + " blasted), the rest kept it");
        int blasted = gone;

        // piston: the pushed block leaves its wire behind (the cell is a moving piston, then the piston head)
        BlockPos piston = new BlockPos(12, 1, 3);
        h.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.EAST));
        h.setBlock(piston.east(), Blocks.DIRT);
        BlockPos pushed = h.absolutePos(piston.east());
        WiringCommands.layAll(level, List.of(pushed), 2);
        h.setBlock(piston.west(), Blocks.REDSTONE_BLOCK);

        // command-style changes: to air and to glass lose the wire, to another solid block keeps it
        BlockPos s1 = new BlockPos(3, 1, 12), s2 = new BlockPos(4, 1, 12), s3 = new BlockPos(5, 1, 12);
        for (BlockPos s : List.of(s1, s2, s3)) h.setBlock(s, Blocks.STONE);
        WiringCommands.layAll(level, List.of(h.absolutePos(s1), h.absolutePos(s2), h.absolutePos(s3)), 3);
        level.setBlock(h.absolutePos(s1), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(h.absolutePos(s2), Blocks.GLASS.defaultBlockState(), 2 | 16);
        level.setBlock(h.absolutePos(s3), Blocks.OAK_PLANKS.defaultBlockState(), 2);
        h.assertTrue(!BuildingWiring.any(level, h.absolutePos(s1)) && !BuildingWiring.any(level, h.absolutePos(s2)), "set to air / glass: wire gone");
        h.assertTrue(BuildingWiring.node(level, h.absolutePos(s3), 3) != 0, "set to planks: wire kept");
        h.assertTrue(!WiringNode.linked(BuildingWiring.node(level, h.absolutePos(s3), 3), Direction.WEST), "the kept node lost its link to the cleared cell");

        h.runAfterDelay(6, () -> {
            h.assertTrue(level.getBlockState(pushed).is(Blocks.PISTON_HEAD), "the piston extended");
            h.assertTrue(level.getBlockState(pushed.east()).is(Blocks.DIRT), "the dirt moved");
            h.assertTrue(!BuildingWiring.any(level, pushed) && !BuildingWiring.any(level, pushed.east()), "the wire stayed behind and went: nothing in either cell");
            int items = wireItems(h, centre) + wireItems(h, pushed) + wireItems(h, h.absolutePos(s2));
            h.assertTrue(items == 0, "no wire item anywhere, got " + items);
            pass(h, "cleanup", "explosion " + blasted + " cells, piston, set blocks");
        });
    }

    // ------------------------------------------------------------------------------------------------------ slopes

    private static Block block(String id) {
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(ApocalypseFirstLight.MOD_ID, id));
    }

    /** A green roof cell sloping down to the south. */
    private static BlockState roof(CabinBlocks.RoofShape shape) {
        return block("green_standing_seam_roof").defaultBlockState().setValue(CabinBlocks.Roof.FACING, Direction.SOUTH).setValue(CabinBlocks.Roof.SHAPE, shape);
    }

    private static void slopes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // packing: each slope bit round trips, counts as a wire leaving, leaves the rest alone; all eight stay inside 50 bits
        long n = WiringNode.link(WiringNode.of(3), Direction.SOUTH, true), all = n;
        for (Direction d : Direction.Plane.HORIZONTAL) for (boolean up : new boolean[]{true, false}) {
            long s = WiringNode.slopeLink(n, d, up, true);
            h.assertTrue(WiringNode.slopeLinked(s, d, up) && WiringNode.slopes(s) == 1 << WiringNode.slopeBit(d, up) && WiringNode.linkCount(s) == 2
                    && WiringNode.circuit(s) == 3 && WiringNode.linked(s, Direction.SOUTH), "slope bit " + d + (up ? " up" : " down"));
            h.assertTrue(WiringNode.slopeLink(s, d, up, false) == n, "slope bit " + d + " clears");
            all = WiringNode.slopeLink(all, d, up, true);
        }
        h.assertTrue(WiringNode.slopes(all) == 255 && all >>> 50 == 0, "eight slope bits in 50 bits");

        // a 1:2 roof: lower, upper, (one up) lower, upper; upper to the next lower meets only across an edge
        BlockPos[] rel = {new BlockPos(2, 1, 12), new BlockPos(2, 1, 11), new BlockPos(2, 2, 10), new BlockPos(2, 2, 9)};
        CabinBlocks.RoofShape[] shapes = {CabinBlocks.RoofShape.LOWER, CabinBlocks.RoofShape.UPPER, CabinBlocks.RoofShape.LOWER, CabinBlocks.RoofShape.UPPER};
        List<BlockPos> roof = new ArrayList<>();
        for (int i = 0; i < 4; i++) { h.setBlock(rel[i], roof(shapes[i])); roof.add(h.absolutePos(rel[i])); }
        for (BlockPos p : roof) h.assertTrue(BuildingWiring.wireable(level, p), "a roof cell holds wire");
        h.assertTrue(BuildingWiring.slope(level, roof.get(1), Direction.NORTH, true) && BuildingWiring.slope(level, roof.get(2), Direction.SOUTH, false), "upper to the next lower is a slope");
        h.assertTrue(!BuildingWiring.slope(level, roof.get(1), Direction.EAST, true), "no slope across the roof");
        List<BlockPos> path = WiringRouter.route(level, roof.get(0), roof.get(3), 2, 0, 4000, 10);
        h.assertTrue(path != null && path.equals(roof), "the router steps along the slope, got " + path);

        WiringCommands.layAll(level, roof, 1);
        long l0 = BuildingWiring.node(level, roof.get(0), 1), u0 = BuildingWiring.node(level, roof.get(1), 1);
        long l1 = BuildingWiring.node(level, roof.get(2), 1), u1 = BuildingWiring.node(level, roof.get(3), 1);
        h.assertTrue(WiringNode.linked(l0, Direction.NORTH) && WiringNode.linked(u0, Direction.SOUTH) && WiringNode.linked(l1, Direction.NORTH)
                && WiringNode.linked(u1, Direction.SOUTH), "straight links lower to upper");
        h.assertTrue(WiringNode.slopeLinked(u0, Direction.NORTH, true) && WiringNode.slopeLinked(l1, Direction.SOUTH, false)
                && WiringNode.linkCount(u0) == 2 && WiringNode.linkCount(l0) == 1, "a slope link upper to the next lower, both ways");

        // cut takes the slope link off the other end too; laid again it comes back
        h.assertTrue(BuildingWiring.cut(level, roof.get(1), 1) == 1, "cut the upper cell");
        h.assertTrue(!WiringNode.linked(BuildingWiring.node(level, roof.get(0), 1), Direction.NORTH)
                && WiringNode.slopes(BuildingWiring.node(level, roof.get(2), 1)) == 0, "cut removes both neighbours' links");
        WiringCommands.layAll(level, List.of(roof.get(1)), 1);
        h.assertTrue(WiringNode.slopeLinked(BuildingWiring.node(level, roof.get(2), 1), Direction.SOUTH, false), "laid again: slope link back");

        // the lower cell replaced by stone: it keeps the wire and its straight link, the slope link goes on both ends
        h.setBlock(rel[2], Blocks.STONE);
        long stone = BuildingWiring.node(level, roof.get(2), 1);
        h.assertTrue(stone != 0 && WiringNode.slopes(stone) == 0 && WiringNode.linked(stone, Direction.NORTH), "a replaced cell keeps its wire, not the slope");
        h.assertTrue(!WiringNode.slopeLinked(BuildingWiring.node(level, roof.get(1), 1), Direction.NORTH, true), "and the other end lost it too");

        // stairs: a flight links along the slope; with the circuit in a corner cell the straight links turn it instead
        BlockState stair = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        List<BlockPos> flight = new ArrayList<>();
        for (int i = 0; i < 3; i++) { h.setBlock(new BlockPos(8, 1 + i, 12 - i), stair); flight.add(h.absolutePos(new BlockPos(8, 1 + i, 12 - i))); }
        WiringCommands.layAll(level, flight, 4);
        long mid = BuildingWiring.node(level, flight.get(1), 4);
        h.assertTrue(WiringNode.slopeLinked(mid, Direction.NORTH, true) && WiringNode.slopeLinked(mid, Direction.SOUTH, false) && WiringNode.links(mid) == 0,
                "a flight of stairs: slope links up and down");
        BlockPos s0 = new BlockPos(12, 1, 12), corner = new BlockPos(12, 2, 12), s1 = new BlockPos(12, 2, 11);
        h.setBlock(s0, stair);
        h.setBlock(s1, stair);
        h.setBlock(corner, Blocks.STONE);
        WiringCommands.layAll(level, List.of(h.absolutePos(s0), h.absolutePos(corner), h.absolutePos(s1)), 4);
        long bottom = BuildingWiring.node(level, h.absolutePos(s0), 4);
        h.assertTrue(WiringNode.slopes(bottom) == 0 && WiringNode.linked(bottom, Direction.UP)
                && WiringNode.linked(BuildingWiring.node(level, h.absolutePos(corner), 4), Direction.NORTH), "a corner cell with the circuit: no slope link");
        pass(h, "slopes", "bits, roof, router, cut, replace, stairs");
    }

    // ------------------------------------------------------------------------------------------------------ corner

    private static void corner(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = player(h, "corner", GameType.SURVIVAL);
        // a wall on a foundation (y 1) and a floor beside it; the wall's wire comes down to its bottom (y 2)
        for (int x : new int[]{3, 8}) {
            for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(x, y, 10), Blocks.STONE);
            for (int z = 11; z <= 13; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            List<BlockPos> wall = new ArrayList<>();
            for (int y = 4; y >= 2; y--) wall.add(h.absolutePos(new BlockPos(x, y, 10)));
            WiringCommands.layAll(level, wall, 1);
        }
        BlockPos a = h.absolutePos(new BlockPos(3, 1, 11)), k = h.absolutePos(new BlockPos(3, 1, 10));
        p.moveTo(a.getX() + 0.5, a.getY() + 1, a.getZ() + 2.5, 0, 30);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AflItems.WIRING_WRENCH.get()));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AflItems.INSULATED_COPPER_WIRE.get(), 5));
        WiringTool.use(p, a);   // the floor's first cell: the wall's end is one up and one north
        long kn = BuildingWiring.node(level, k, 1);
        h.assertTrue(kn != 0 && WiringNode.linked(kn, Direction.UP) && WiringNode.linked(kn, Direction.SOUTH), "the corner cell joins the wall's end and the floor's");
        h.assertTrue(WiringTool.wires(p) == 3, "the floor cell and the corner cost two wires, left " + WiringTool.wires(p));

        // the same with one wire only: the cell is laid, the corner skipped
        WiringTool.choose(p, 1);   // a new stroke: no route from the first floor cell
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AflItems.INSULATED_COPPER_WIRE.get(), 1));
        BlockPos b = h.absolutePos(new BlockPos(8, 1, 11));
        WiringTool.use(p, b);
        h.assertTrue(BuildingWiring.node(level, b, 1) != 0 && BuildingWiring.node(level, h.absolutePos(new BlockPos(8, 1, 10)), 1) == 0
                && WiringTool.wires(p) == 0, "without a spare wire the corner is skipped");
        pass(h, "corner", "join costs one wire, skipped without");
    }

    // ------------------------------------------------------------------------------------------------------ shapes

    private static void shapes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // a log wall run: outline and collision out to the crests, a full block for light, support, redstone and mobs; never
        // suffocating, or the next cell (the crests reach 0.07 into it) would push a player standing there a cell away
        BlockState log = block("white_oak_log_wall").defaultBlockState();
        h.setBlock(new BlockPos(2, 1, 2), log);
        BlockPos lp = h.absolutePos(new BlockPos(2, 1, 2));
        BlockState ls = level.getBlockState(lp);
        AABB b = ls.getShape(level, lp).bounds();
        h.assertTrue(b.minZ < 0 && b.maxZ > 1 && b.minX == 0 && b.maxX == 1 && b.minY == 0 && b.maxY == 1, "the run's outline reaches the crests: " + b);
        h.assertTrue(!ls.getCollisionShape(level, lp).isEmpty() && ls.isFaceSturdy(level, lp, Direction.NORTH) && ls.isFaceSturdy(level, lp, Direction.UP)
                && ls.isViewBlocking(level, lp) && ls.isRedstoneConductor(level, lp) && ls.canOcclude(),
                "the run is solid for support, view, redstone, light");
        h.setBlock(new BlockPos(2, 1, 6), Blocks.STONE.defaultBlockState());   // the check itself: a stone's cell does suffocate
        BlockPos stone = h.absolutePos(new BlockPos(2, 1, 6));
        h.assertTrue(!ls.isSuffocating(level, lp) && !level.collidesWithSuffocatingBlock(null, new AABB(lp.north()).deflate(1.0E-7))
                        && level.collidesWithSuffocatingBlock(null, new AABB(stone).deflate(1.0E-7)),
                "the cell beside a log wall is free to stand in (LocalPlayer#moveTowardsClosestSpace)");
        h.assertTrue(!ls.isPathfindable(level, lp, PathComputationType.LAND) && BuildingWiring.wireable(level, lp), "mobs see a wall; it holds wire");
        // a corner: both runs, the round log ends out on its open sides (west and north here)
        h.setBlock(new BlockPos(6, 1, 2), log);
        h.setBlock(new BlockPos(7, 1, 2), log);
        h.setBlock(new BlockPos(6, 1, 3), log.setValue(CabinBlocks.LogWall.AXIS, Direction.Axis.Z));
        BlockPos cp = h.absolutePos(new BlockPos(6, 1, 2));
        AABB c = level.getBlockState(cp).getShape(level, cp).bounds();
        h.assertTrue(c.minX < -0.2 && c.minZ < -0.2 && c.maxX < 1.1 && c.maxZ < 1.1, "a corner's log ends stand out on its open sides only: " + c);

        // roofs: the slab (a lower cell dips below the cell at the eave), down to the cell's bottom over a wall; a ceiling fixture still holds
        h.setBlock(new BlockPos(2, 3, 8), roof(CabinBlocks.RoofShape.LOWER));
        h.setBlock(new BlockPos(4, 3, 8), roof(CabinBlocks.RoofShape.UPPER));
        h.setBlock(new BlockPos(6, 2, 8), log);
        h.setBlock(new BlockPos(6, 3, 8), roof(CabinBlocks.RoofShape.UPPER));
        BlockPos rl = h.absolutePos(new BlockPos(2, 3, 8)), ru = h.absolutePos(new BlockPos(4, 3, 8)), rf = h.absolutePos(new BlockPos(6, 3, 8));
        AABB lower = level.getBlockState(rl).getShape(level, rl).bounds(), upper = level.getBlockState(ru).getShape(level, ru).bounds();
        AABB filled = level.getBlockState(rf).getShape(level, rf).bounds();
        h.assertTrue(lower.minY < 0 && lower.maxY <= 0.5 + 1.0E-6, "a lower cell is its slab: " + lower);
        h.assertTrue(upper.minY > 0.2 && upper.maxY <= 1 + 1.0E-6 && filled.minY == 0, "an upper cell's slab, filled down over a wall: " + upper + " / " + filled);
        h.assertTrue(level.getBlockState(rl).isFaceSturdy(level, rl, Direction.DOWN) && level.getBlockState(ru).isFaceSturdy(level, ru, Direction.DOWN),
                "a ceiling fixture holds under a roof cell");

        // hit meshes (the crosshair and the outline follow the drawn pieces, user 2026-10-13): a ray down onto a lower roof cell
        // stops on its sloped surface, a ray at a log run on its crest; the gun rack keeps its boxes (its guns are no pieces)
        BlockState lowerState = level.getBlockState(rl);
        MeshHitModels.Shape roofMesh = MeshHitModels.shape(level, rl, lowerState);
        h.assertTrue(roofMesh != null, "a roof cell has a hit mesh");
        MeshBlockHitResult down = roofMesh.clip(Vec3.atLowerCornerOf(rl).add(0.5, 2, 0.5), Vec3.atLowerCornerOf(rl).add(0.5, -1, 0.5), rl);
        h.assertTrue(down != null && Math.abs(down.normal().z) > 0.1 && down.normal().y > 0.3
                        && down.getLocation().y - rl.getY() > lower.minY && down.getLocation().y - rl.getY() < lower.maxY + 1.0E-3,
                "a ray down lands on the slope: " + (down == null ? "miss" : down.getLocation() + " n " + down.normal()));
        int[] roofEdges = {0};
        roofMesh.forEachEdge((x0, y0, z0, x1, y1, z1) -> roofEdges[0]++);
        h.assertTrue(roofEdges[0] > 0, "a roof cell's outline has edges");
        MeshHitModels.Shape logMesh = MeshHitModels.shape(level, lp, ls);
        MeshBlockHitResult side = logMesh == null ? null
                : logMesh.clip(Vec3.atLowerCornerOf(lp).add(0.5, 0.5, -1), Vec3.atLowerCornerOf(lp).add(0.5, 0.5, 0.5), lp);
        h.assertTrue(side != null && side.getLocation().z - lp.getZ() < 0, "a ray at a log run stops on the crest: " + (side == null ? "miss" : side.getLocation()));
        h.setBlock(new BlockPos(10, 3, 8), block("wooden_gun_rack").defaultBlockState());
        BlockPos gp = h.absolutePos(new BlockPos(10, 3, 8));
        h.assertTrue(MeshHitModels.shape(level, gp, level.getBlockState(gp)) == null, "the gun rack keeps its boxes");

        // a door: frame and panel, the panel swung when open; a window: frame and sashes, not a full block, not walkable
        BlockState door = block("white_oak_cabin_door").defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        h.setBlock(new BlockPos(10, 1, 2), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(new BlockPos(10, 2, 2), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        BlockPos dp = h.absolutePos(new BlockPos(10, 1, 2));
        BlockState closed = level.getBlockState(dp);
        // the casing spans the cell either way: the doorway's middle (in the closed panel) tells shut from open
        Vec3 doorway = new Vec3(0.5, 0.5, 15.08 / 16);
        boolean shut = closed.getCollisionShape(level, dp).toAabbs().stream().anyMatch(x -> x.contains(doorway));
        boolean open = closed.setValue(DoorBlock.OPEN, true).getCollisionShape(level, dp).toAabbs().stream().anyMatch(x -> x.contains(doorway));
        h.assertTrue(!Block.isShapeFullBlock(closed.getShape(level, dp)) && shut && !open, "a door is frame and panel, the doorway clear when open");
        h.setBlock(new BlockPos(13, 1, 2), block("white_oak_double_hung_window").defaultBlockState());
        BlockPos wp = h.absolutePos(new BlockPos(13, 1, 2));
        BlockState window = level.getBlockState(wp);
        h.assertTrue(!Block.isShapeFullBlock(window.getShape(level, wp)) && !window.isPathfindable(level, wp, PathComputationType.LAND)
                && !window.isSuffocating(level, wp), "a window: frame and sashes, mobs do not walk through it");

        // a porch railing: the outline is the rails, the collision a fence's 1.5 high
        h.setBlock(new BlockPos(16, 1, 2), block("white_oak_porch_railing").defaultBlockState());
        BlockPos pp = h.absolutePos(new BlockPos(16, 1, 2));
        BlockState rail = level.getBlockState(pp);
        h.assertTrue(rail.getShape(level, pp).bounds().maxY < 1 && rail.getCollisionShape(level, pp).bounds().maxY > 1.4, "a railing stops a jump");
        pass(h, "shapes", "log wall (no suffocation), corner, roofs, hit meshes, door, window, railing");
    }
}
