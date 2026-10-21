package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.worldgen.building.BuildingCanvas;
import com.antaurora.apofirstlight.worldgen.building.TownBuildingPass;
import com.antaurora.apofirstlight.worldgen.building.WhiteBoxKit;
import com.antaurora.apofirstlight.worldgen.network.town.TownBuildings;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Collection;
import java.util.List;

/**
 * Town buildings V1 (docs/worldgen/town_buildings_v1.md): a placeholder house's site drawn by its kit (BuildingKits) and
 * written into the level the way TownBuildingPass writes a chunk, once facing each world direction: every cell inside the
 * footprint, the doorway in the middle of the front (the side the site faces), the walls round it, the roof over it, the
 * floor and footing under it.
 */
@GameTestHolder("afl_town_building_tests")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class TownBuildingGameTests {
    private static final String NAMESPACE = "afl_town_building_tests";
    private static final int SX = 40, SY = 16, SZ = 40;

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof GameTestServer)) return;
        var level = event.getServer().overworld();
        var tag = net.minecraft.nbt.TagParser.parseTag("{size:[" + SX + "," + SY + "," + SZ + "],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        var blocks = new net.minecraft.nbt.ListTag();
        for (int x = 0; x < SX; x++) for (int y = 0; y < SY; y++) for (int z = 0; z < SZ; z++) {
            var block = new net.minecraft.nbt.CompoundTag();
            var position = new net.minecraft.nbt.ListTag();
            for (int coordinate : new int[]{x, y, z}) position.add(net.minecraft.nbt.IntTag.valueOf(coordinate));
            block.put("pos", position);
            block.putInt("state", 0);
            blocks.add(block);
        }
        tag.put("blocks", blocks);
        level.getStructureManager().getOrCreate(new ResourceLocation(NAMESPACE, "empty"))
                .load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag);
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("town_building", NAMESPACE + ":integration",
                NAMESPACE + ":empty", 100, 0L, true, TownBuildingGameTests::run));
    }

    private static void run(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // the ground: stone under a dirt layer at y 1 (the floor goes in at y 2, the footing at y 1)
        for (int x = 0; x < SX; x++) for (int z = 0; z < SZ; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            h.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
        }
        int[][] faces = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        String[] names = {"north", "east", "south", "west"};
        StringBuilder report = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            int fx = faces[i][0], fz = faces[i][1];
            // a 9 wide, 7 deep house in its own quarter of the test area: 9 along the front
            BlockPos base = h.absolutePos(new BlockPos(4 + 20 * (i % 2), 3, 4 + 20 * (i / 2)));
            int w = 9, d = 7, ex = fx != 0 ? d : w, ez = fx != 0 ? w : d;
            TownBuildings.Site s = new TownBuildings.Site("test", i, "res", "house_small", base.getX(), base.getZ(), base.getX() + ex - 1,
                    base.getZ() + ez - 1, base.getY(), fx, fz, 7L);
            BuildingCanvas c = TownBuildingPass.canvas(s);
            h.assertTrue(c != null, names[i] + ": the house kind has a kit");
            h.assertTrue(c.width == w && c.depth == d, names[i] + ": canvas " + c.width + " x " + c.depth + ", want " + w + " x " + d);
            for (int ww = 0; ww < c.depth; ww++)
                for (int u = 0; u < c.width; u++) {
                    int x = c.x(u, ww), z = c.z(u, ww);
                    h.assertTrue(x >= s.x0() && x <= s.x1() && z >= s.z0() && z <= s.z1(), names[i] + ": cell " + u + " " + ww + " outside the footprint");
                    for (int y = -BuildingCanvas.BELOW; y < c.height; y++) {
                        BlockState st = c.get(u, y, ww);
                        if (st != null) level.setBlock(new BlockPos(x, c.worldY(y), z), st, 2);
                    }
                }
            // the front: the footprint's edge toward (fx, fz); the doorway in its middle, walls at its ends
            int mid = (w - 2) / 2;
            BlockPos door = new BlockPos(c.x(mid, 0), s.floorY(), c.z(mid, 0));
            BlockPos ahead = door.offset(fx, 0, fz);
            h.assertTrue(level.getBlockState(door).isAir() && level.getBlockState(door.above()).isAir(), names[i] + ": doorway open at " + door);
            h.assertTrue(ahead.getX() < s.x0() || ahead.getX() > s.x1() || ahead.getZ() < s.z0() || ahead.getZ() > s.z1(),
                    names[i] + ": the doorway's outside " + ahead + " lies beyond the footprint (the front faces " + fx + " " + fz + ")");
            BlockPos corner = new BlockPos(c.x(0, 0), s.floorY(), c.z(0, 0)), back = new BlockPos(c.x(mid, d - 1), s.floorY(), c.z(mid, d - 1));
            BlockState wall = level.getBlockState(corner);
            h.assertTrue(!wall.isAir() && wall.equals(level.getBlockState(back)), names[i] + ": walls at the front corner and the back middle");
            h.assertTrue(level.getBlockState(new BlockPos(c.x(mid, d / 2), s.floorY(), c.z(mid, d / 2))).isAir(), names[i] + ": inside empty");
            int roofY = c.worldY(c.height - 1);
            h.assertTrue(level.getBlockState(new BlockPos(c.x(mid, d / 2), roofY, c.z(mid, d / 2))).is(AflBlocks.ROOF_TPO.get()), names[i] + ": roof_tpo on top");
            h.assertTrue(level.getBlockState(new BlockPos(c.x(mid, d / 2), s.floorY() - 1, c.z(mid, d / 2))).is(AflBlocks.SEALED_CONCRETE_FLOOR.get()), names[i] + ": floor");
            h.assertTrue(level.getBlockState(new BlockPos(c.x(mid, d / 2), s.floorY() - 2, c.z(mid, d / 2))).is(AflBlocks.REINFORCED_CONCRETE.get()), names[i] + ": footing");
            h.assertTrue(c.height == WhiteBoxKit.STOREY + 1, names[i] + ": one storey and the roof");
            report.append(String.format(java.util.Locale.ROOT, " %s: footprint %d..%d x %d..%d, door %s;", names[i], s.x0(), s.x1(), s.z0(), s.z1(), door.subtract(base)));
        }
        ApocalypseFirstLight.LOGGER.info("[AFL TOWN BUILDING TEST] PASS white-box house drawn facing each way:{}", report);
        h.succeed();
    }
}
