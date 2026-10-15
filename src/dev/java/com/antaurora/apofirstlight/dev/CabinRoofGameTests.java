package com.antaurora.apofirstlight.dev;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nullable;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cabin.CabinBlocks.Roof;
import com.antaurora.apofirstlight.cabin.CabinBlocks.RoofShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The cabin roof's fill and occlusion (docs/models/cabin_exterior_v1.md, 2026-10-14):
 * <ul>
 * <li>Fill: an east / west roof over its eave log wall, a gable log wall, a corner and a door's upper half takes the
 * quarter-turned _q log pieces; a north ridge over a gable window fills with logs along the slope; a lone board wall's fill
 * mirrors its battens (none against the opaque neighbour), a window between boards fills with boards, battens on both faces.</li>
 * <li>Pieces: every piece the three roofs give for every shape, facing, edge, fill and battens side exists.</li>
 * <li>Occlusion: the level and a chunk get the steps, a render region (a BlockAndTintGetter that is no LevelReader) none,
 * so the board's top face under a roof and a roof's face against the next cell are drawn; the roof still blocks motion
 * (the motion heightmap's test: rain, snow) and its steps keep the light from above out from under it.</li>
 * </ul>
 * Run with {@code src/dev/cabin-roof-gametest.init.gradle}.
 */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class CabinRoofGameTests {
    private static final int SIZE = 16, HEIGHT = 8;
    /** cabinFlags' layout (CabinBlocks.Roof): edges, the fill kind at bit 4, the battens sides (N, E, S, W) at bit 8, a pipe. */
    private static final int EAVE = 1, TOP = 2, RAKES = 4 | 8, PIPE = 128, INFILL_SHIFT = 4, BATTENS_SHIFT = 8;

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)) return;
        var level = event.getServer().overworld();
        var tag = TagParser.parseTag("{size:[" + SIZE + "," + HEIGHT + "," + SIZE + "],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        var blocks = new ListTag();
        for (int x = 0; x < SIZE; x++) for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < SIZE; z++) {
            var block = new CompoundTag();
            var position = new ListTag();
            for (int coordinate : new int[]{x, y, z}) position.add(IntTag.valueOf(coordinate));
            block.put("pos", position);
            block.putInt("state", 0);
            blocks.add(block);
        }
        tag.put("blocks", blocks);
        level.getStructureManager().getOrCreate(new ResourceLocation("afl_cabin_tests", "cabin_empty"))
                .load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag);
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("cabin_roof", "afl_cabin_tests:cabin_roof", "afl_cabin_tests:cabin_empty", 200, 0L, true,
                CabinRoofGameTests::run));
    }

    private static Block block(String id) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(ApocalypseFirstLight.MOD_ID, id));
        check(b != null && b != Blocks.AIR, "no block " + id);
        return b;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }

    private static BlockState roof(String id, Direction facing, RoofShape shape) {
        return block(id).defaultBlockState().setValue(Roof.FACING, facing).setValue(Roof.SHAPE, shape);
    }

    private static BlockState log(Direction.Axis axis) { return block("white_oak_log_wall").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, axis); }

    private static Set<String> pieces(GameTestHelper helper, BlockPos rel) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(rel);
        BlockState state = level.getBlockState(at);
        Roof roof = (Roof) state.getBlock();
        Set<String> out = new TreeSet<>();
        roof.cabinPieces(state, roof.cabinFlags(state, level, at), (name, turn, dy) -> out.add(name));
        return out;
    }

    private static void expect(GameTestHelper helper, String what, BlockPos roofAt, List<String> want, List<String> not) {
        Set<String> got = pieces(helper, roofAt);
        for (String w : want) check(got.contains(w), what + ": no " + w + " in " + got);
        for (String n : not) check(!got.contains(n), what + ": " + n + " should be gone, got " + got);
    }

    private static void run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        // (A) the addition's eave wall under its east-falling roof: logs across the slope, the quarter-turned piece
        for (int z = 1; z <= 3; z++) helper.setBlock(new BlockPos(1, 1, z), log(Direction.Axis.Z));
        helper.setBlock(new BlockPos(1, 2, 2), roof("green_standing_seam_roof", Direction.EAST, RoofShape.LOWER));
        expect(helper, "east eave wall", new BlockPos(1, 2, 2), List.of("roof/infill_log_across_lower_q"), List.of("roof/infill_log_across_lower"));
        // (B) its gable wall: logs along the slope
        for (int x = 4; x <= 6; x++) helper.setBlock(new BlockPos(x, 1, 1), log(Direction.Axis.X));
        helper.setBlock(new BlockPos(5, 2, 1), roof("galvanized_corrugated_roof", Direction.EAST, RoofShape.UPPER));
        expect(helper, "east gable wall", new BlockPos(5, 2, 1), List.of("roof/infill_log_along_upper_q"), List.of("roof/infill_log_along_upper"));
        // (C) a corner under a west-falling roof
        helper.setBlock(new BlockPos(8, 1, 1), log(Direction.Axis.X));
        helper.setBlock(new BlockPos(9, 1, 1), log(Direction.Axis.X));
        helper.setBlock(new BlockPos(8, 1, 2), log(Direction.Axis.Z));
        helper.setBlock(new BlockPos(8, 2, 1), roof("rusty_corrugated_roof", Direction.WEST, RoofShape.LOWER));
        expect(helper, "west corner", new BlockPos(8, 2, 1), List.of("roof/infill_log_corner_lower_q"), List.of());
        // (D) the gable window under the main roof's ridge (north / south: no quarter turn)
        Block window = block("white_oak_double_hung_window");
        helper.setBlock(new BlockPos(12, 1, 1), log(Direction.Axis.Z));
        helper.setBlock(new BlockPos(12, 1, 2), window.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(12, 1, 3), log(Direction.Axis.Z));
        helper.setBlock(new BlockPos(12, 2, 2), roof("rusty_corrugated_roof", Direction.NORTH, RoofShape.RIDGE));
        expect(helper, "gable window", new BlockPos(12, 2, 2), List.of("roof/infill_log_along_ridge"), List.of("roof/infill_log_along_ridge_q"));
        // (E) a door's upper half in the eave wall under an east-falling roof
        BlockState door = block("white_oak_cabin_door").defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
        helper.setBlock(new BlockPos(1, 0, 7), stone);
        helper.setBlock(new BlockPos(1, 1, 7), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(new BlockPos(1, 2, 7), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        for (int y = 1; y <= 2; y++) for (int z : new int[]{6, 8}) helper.setBlock(new BlockPos(1, y, z), log(Direction.Axis.Z));
        helper.setBlock(new BlockPos(1, 3, 7), roof("green_standing_seam_roof", Direction.EAST, RoofShape.LOWER));
        check(level.getBlockState(helper.absolutePos(new BlockPos(1, 2, 7))).getBlock() instanceof DoorBlock, "the door's upper half fell");
        expect(helper, "door top", new BlockPos(1, 3, 7), List.of("roof/infill_log_across_lower_q"), List.of());
        // (F) a lone grey board wall, stone on its east side, under an east-falling roof: battens north, south, west
        BlockState grey = block("grey_board_and_batten").defaultBlockState(), brown = block("brown_board_and_batten").defaultBlockState();
        helper.setBlock(new BlockPos(5, 1, 5), grey);
        helper.setBlock(new BlockPos(6, 1, 5), stone);
        helper.setBlock(new BlockPos(5, 2, 5), roof("rusty_corrugated_roof", Direction.EAST, RoofShape.LOWER));
        // turned east (r 1): world north is the piece's west, south its east, west its south
        expect(helper, "board wall", new BlockPos(5, 2, 5), List.of("roof/infill_board_grey_lower", "roof/infill_battens_grey_lower_w",
                "roof/infill_battens_grey_lower_e", "roof/infill_battens_grey_lower_s"), List.of("roof/infill_battens_grey_lower_n"));
        // (G) a window between brown boards under a south-falling roof: boards, battens on both faces (r 2: N -> s, S -> n)
        helper.setBlock(new BlockPos(8, 1, 5), brown);
        helper.setBlock(new BlockPos(9, 1, 5), window.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        helper.setBlock(new BlockPos(10, 1, 5), brown);
        helper.setBlock(new BlockPos(9, 2, 5), roof("green_standing_seam_roof", Direction.SOUTH, RoofShape.UPPER));
        expect(helper, "board window", new BlockPos(9, 2, 5), List.of("roof/infill_board_brown_upper", "roof/infill_battens_brown_upper_s",
                "roof/infill_battens_brown_upper_n"), List.of("roof/infill_battens_brown_upper_e", "roof/infill_battens_brown_upper_w"));

        // every piece name the roofs can give exists
        int names = 0;
        List<String> missing = new ArrayList<>();
        var modFile = ModList.get().getModFileById(ApocalypseFirstLight.MOD_ID).getFile();
        Set<String> seen = new TreeSet<>();
        for (String id : new String[]{"green_standing_seam_roof", "galvanized_corrugated_roof", "rusty_corrugated_roof"})
            for (RoofShape shape : RoofShape.values()) for (Direction f : Direction.Plane.HORIZONTAL) for (int infill = 0; infill <= 5; infill++) {
                BlockState s = roof(id, f, shape).setValue(Roof.BEAM, true);
                int edges = shape == RoofShape.RIDGE ? RAKES : EAVE | TOP | RAKES;
                int flags = edges | PIPE | infill << INFILL_SHIFT | (infill >= 4 ? 15 << BATTENS_SHIFT : 0);
                ((Roof) s.getBlock()).cabinPieces(s, flags, (name, turn, dy) -> seen.add(name));
            }
        for (String name : seen) {
            names++;
            if (!Files.exists(modFile.findResource(("assets/" + ApocalypseFirstLight.MOD_ID + "/cabin_mesh/" + name + ".json").split("/")))) missing.add(name);
        }
        check(missing.isEmpty(), "missing pieces " + missing);

        // occlusion: the steps for the level and a chunk (light), nothing for a render region (face culling)
        BlockPos boardAt = helper.absolutePos(new BlockPos(5, 1, 5)), roofAt = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockState roofState = level.getBlockState(roofAt);
        BlockAndTintGetter region = region(level);
        check(!roofState.getFaceOcclusionShape(level, roofAt, Direction.DOWN).isEmpty(), "the level got no steps");
        check(!roofState.getFaceOcclusionShape(level.getChunkAt(roofAt), roofAt, Direction.DOWN).isEmpty(), "the chunk got no steps");
        check(roofState.getFaceOcclusionShape(region, roofAt, Direction.DOWN).isEmpty(), "the render region got the steps");
        check(Block.shouldRenderFace(level.getBlockState(boardAt), region, boardAt, Direction.UP, roofAt), "the board's top face under the roof was culled");
        // a 3 x 3 roof (north-falling, lower cells), open underneath
        for (int x = 11; x <= 13; x++) for (int z = 9; z <= 11; z++)
            helper.setBlock(new BlockPos(x, 4, z), roof("galvanized_corrugated_roof", Direction.NORTH, RoofShape.LOWER));
        BlockPos mid = helper.absolutePos(new BlockPos(12, 4, 10)), east = helper.absolutePos(new BlockPos(13, 4, 10));
        check(Block.shouldRenderFace(level.getBlockState(mid), region, mid, Direction.EAST, east), "the roof's face against the next cell was culled");
        // the heightmap's own test (the test world is real terrain, the test sits in a pocket under it: no column to read)
        check(level.getBlockState(mid).blocksMotion() && Heightmap.Types.MOTION_BLOCKING.isOpaque().test(level.getBlockState(mid)),
                "the roof lets things through (rain, snow, motion heightmap)");
        // light: glowstone two above the middle cell; with the steps shutting the cell's bottom the light under it goes round the
        // roof's edge (8), straight through it would be 12
        helper.setBlock(new BlockPos(12, 6, 10), Blocks.GLOWSTONE);
        BlockPos under = mid.below();
        final int count = names;
        helper.succeedWhen(() -> {
            int light = level.getBrightness(LightLayer.BLOCK, under);
            check(light > 0 && light < 12, "block light under the roof " + light);
            ApocalypseFirstLight.LOGGER.info("[AFL CABIN ROOF TEST] PASS fills, {} pieces, occlusion, block light under the roof {}", count, light);
        });
    }

    /** A chunk-meshing view of the level (as RenderChunkRegion / Embeddium's WorldSlice: a BlockAndTintGetter, no LevelReader). */
    private static BlockAndTintGetter region(ServerLevel level) {
        return new BlockAndTintGetter() {
            @Override public float getShade(Direction direction, boolean shade) { return level.getShade(direction, shade); }
            @Override public LevelLightEngine getLightEngine() { return level.getLightEngine(); }
            @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) { return level.getBlockTint(pos, resolver); }
            @Nullable @Override public BlockEntity getBlockEntity(BlockPos pos) { return level.getBlockEntity(pos); }
            @Override public BlockState getBlockState(BlockPos pos) { return level.getBlockState(pos); }
            @Override public FluidState getFluidState(BlockPos pos) { return level.getFluidState(pos); }
            @Override public int getHeight() { return level.getHeight(); }
            @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
        };
    }
}
