package com.antaurora.apofirstlight.dev;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.UndergroundFuelTankBlock;
import com.antaurora.apofirstlight.blockentity.UndergroundFuelTankBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Underground fuel tank orientation (docs/models/underground_fuel_tank_v1.md, "朝向和旋转", 2026-10-08): a structure
 * turned or mirrored keeps every tank whole, with its fill end where the turn takes it.
 * <ul>
 * <li>Pure: for each of the four orientations, every mirror and rotation, the turned cells (StructureTemplate's own
 * position transform, BlockState mirror then rotate) are exactly the cells of one tank at the turned root.</li>
 * <li>World: a tank saved into a template and placed turned 90 / 180 / 270 degrees and mirrored stays whole after its
 * integrity ticks, with the master's block entity.</li>
 * </ul>
 * Run with {@code src/dev/underground-fuel-tank-gametest.init.gradle}.
 */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class UndergroundFuelTankRotationGameTests {
    private static final int SIZE = 32, HEIGHT = 12;

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)) return;
        var level = event.getServer().overworld();
        var tag = net.minecraft.nbt.TagParser.parseTag(
                "{size:[" + SIZE + "," + HEIGHT + "," + SIZE + "],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        var blocks = new net.minecraft.nbt.ListTag();
        for (int x = 0; x < SIZE; x++) for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < SIZE; z++) {
            var block = new net.minecraft.nbt.CompoundTag();
            var position = new net.minecraft.nbt.ListTag();
            for (int coordinate : new int[]{x, y, z}) position.add(net.minecraft.nbt.IntTag.valueOf(coordinate));
            block.put("pos", position);
            block.putInt("state", 0);
            blocks.add(block);
        }
        tag.put("blocks", blocks);
        level.getStructureManager().getOrCreate(new net.minecraft.resources.ResourceLocation("afl_tank_tests", "tank_empty"))
                .load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag);
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("tank_rotation", "afl_tank_tests:tank_rotation", "afl_tank_tests:tank_empty", 400, 0L, true,
                UndergroundFuelTankRotationGameTests::run));
    }

    private static void run(GameTestHelper helper) {
        var block = (UndergroundFuelTankBlock) AflBlocks.UNDERGROUND_FUEL_TANK_GASOLINE.get();
        int pure = 0;
        for (Direction along : Direction.Plane.HORIZONTAL) for (Mirror mirror : Mirror.values()) for (Rotation rotation : Rotation.values()) {
            var settings = new StructurePlaceSettings().setMirror(mirror).setRotation(rotation);
            Map<BlockPos, BlockState> turned = new HashMap<>();
            block.cells(BlockPos.ZERO, along).forEach((position, state) ->
                    turned.put(StructureTemplate.calculateRelativePosition(settings, position), state.mirror(mirror).rotate(rotation)));
            BlockPos root = StructureTemplate.calculateRelativePosition(settings, BlockPos.ZERO);
            BlockState any = turned.get(root);
            helper.assertTrue(any != null && any.is(block), "root cell kept " + along + " " + mirror + " " + rotation);
            Direction turnedAlong = UndergroundFuelTankBlock.alongDir(any);
            helper.assertTrue(turned.equals(block.cells(root, turnedAlong)), "one whole tank " + along + " " + mirror + " " + rotation);
            BlockPos fill = StructureTemplate.calculateRelativePosition(settings, UndergroundFuelTankBlock.cellPosition(BlockPos.ZERO, along,
                    UndergroundFuelTankBlock.FILL_ALONG, UndergroundFuelTankBlock.PORT_ACROSS, UndergroundFuelTankBlock.PORT_LEVEL));
            helper.assertTrue(UndergroundFuelTankBlock.isFill(turned.get(fill)), "fill end follows the turn " + along + " " + mirror + " " + rotation);
            pure++;
        }

        // world: one tank, saved into a template, placed turned / mirrored at the test's centre
        var level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(16, 1, 4));
        Map<BlockPos, BlockState> cells = block.cells(source, Direction.EAST);
        cells.forEach((position, state) -> level.setBlock(position, state, Block.UPDATE_ALL));
        BlockPos min = cells.keySet().stream().reduce((a, b) -> new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()))).orElseThrow();
        var template = new StructureTemplate();
        template.fillFromWorld(level, min, new Vec3i(7, 3, 3), false, Blocks.STRUCTURE_VOID);
        cells.keySet().forEach(position -> level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        BlockPos sourceRoot = source.subtract(min);

        List<StructurePlaceSettings> turns = List.of(
                new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90),
                new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_180),
                new StructurePlaceSettings().setRotation(Rotation.COUNTERCLOCKWISE_90),
                new StructurePlaceSettings().setMirror(Mirror.LEFT_RIGHT));
        // a turned tank reaches at most 7 cells from its placement point: the four points are 16 apart
        List<BlockPos> points = List.of(new BlockPos(8, 1, 8), new BlockPos(24, 1, 8), new BlockPos(8, 1, 24), new BlockPos(24, 1, 24));
        Map<BlockPos, Direction> placed = new HashMap<>();
        int slot = 0;
        for (var settings : turns) {
            BlockPos at = helper.absolutePos(points.get(slot++));
            template.placeInWorld(level, at, at, settings, level.getRandom(), Block.UPDATE_ALL);
            BlockPos root = at.offset(StructureTemplate.calculateRelativePosition(settings, sourceRoot));
            helper.assertTrue(level.getBlockState(root).is(block), "turned root placed " + settings.getRotation() + " " + settings.getMirror());
            placed.put(root, UndergroundFuelTankBlock.alongDir(level.getBlockState(root)));
        }
        int world = turns.size();
        int checks = pure;
        helper.runAfterDelay(10, () -> {
            placed.forEach((root, along) -> {
                block.cells(root, along).forEach((position, state) ->
                        helper.assertTrue(level.getBlockState(position).equals(state), "cell kept after the integrity tick at " + position.toShortString()));
                BlockPos master = UndergroundFuelTankBlock.cellPosition(root, along, UndergroundFuelTankBlock.PORT_ALONG,
                        UndergroundFuelTankBlock.PORT_ACROSS, UndergroundFuelTankBlock.PORT_LEVEL);
                helper.assertTrue(level.getBlockEntity(master) instanceof UndergroundFuelTankBlockEntity, "master block entity at " + master.toShortString());
            });
            ApocalypseFirstLight.LOGGER.info("[AFL TANK ROTATION TEST] PASS {} pure orientation / mirror / rotation cases, {} turned template placements", checks, world);
            helper.succeed();
        });
    }
}
