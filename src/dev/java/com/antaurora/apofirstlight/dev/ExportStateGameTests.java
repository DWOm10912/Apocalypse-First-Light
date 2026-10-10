package com.antaurora.apofirstlight.dev;

import java.util.Collection;
import java.util.List;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.authoring.ExportState;
import com.antaurora.apofirstlight.block.UndergroundFuelTankBlock;
import com.antaurora.apofirstlight.blockentity.FuelCanBlockEntity;
import com.antaurora.apofirstlight.blockentity.UndergroundFuelTankBlockEntity;
import com.antaurora.apofirstlight.fluid.FuelFill;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflFluids;
import com.antaurora.apofirstlight.worldgen.structure.AflBlockEntityProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The exported building state (docs/worldgen/fuel_stop_a1_design_v1.md "导出 NBT 时的状态", 2026-10-09):
 * <ul>
 * <li>Offsets: for every mirror and rotation, the processor turns a strip's PlugHost and Outlet and a meter box's Panel to
 * exactly the difference of the two turned positions, and leaves another block entity's Outlet alone.</li>
 * <li>Export: ExportState swaps the fuel for fill markers, empties the pump and the dispenser lines, stops the generator,
 * zeroes stored energy and drops a plug in hand and a started search; it counts a lit AFL state.</li>
 * <li>Fill: the underground tank rule over 400 positions is empty about 20 % of the time and otherwise holds 5-40 % of the
 * tank; a placed tank, jerry can and portable generator roll their markers on load, within their rules, the same as the
 * rule gives for that position.</li>
 * </ul>
 * Run with {@code src/dev/export-state-gametest.init.gradle}.
 */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class ExportStateGameTests {
    private static final int SIZE = 16, HEIGHT = 6;
    private static final String NS = ApocalypseFirstLight.MOD_ID + ":";

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
        level.getStructureManager().getOrCreate(new ResourceLocation("afl_export_tests", "export_empty"))
                .load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag);
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("export_state", "afl_export_tests:export_state", "afl_export_tests:export_empty", 200, 0L, true,
                ExportStateGameTests::run));
    }

    private static void run(GameTestHelper helper) {
        var level = helper.getLevel();

        // ---- offsets turn with the building ----
        BlockPos[][] pairs = {{new BlockPos(2, 1, 3), new BlockPos(5, 1, 3)}, {new BlockPos(4, 2, 1), new BlockPos(4, 0, 6)},
                {new BlockPos(7, 1, 7), new BlockPos(1, 3, 2)}};
        int turns = 0;
        for (Mirror mirror : Mirror.values()) for (Rotation rotation : Rotation.values()) {
            var settings = new StructurePlaceSettings().setMirror(mirror).setRotation(rotation);
            for (BlockPos[] pair : pairs) {
                long offset = pair[1].subtract(pair[0]).asLong();
                BlockPos expected = StructureTemplate.calculateRelativePosition(settings, pair[1]).subtract(StructureTemplate.calculateRelativePosition(settings, pair[0]));
                var strip = new CompoundTag();
                strip.putString("id", NS + "power_strip");
                strip.putLong("PlugHost", offset);
                strip.putLong("Outlet", offset);
                var meter = new CompoundTag();
                meter.putString("id", NS + "service_meter_box");
                meter.putLong("Panel", offset);
                var other = new CompoundTag();
                other.putString("id", NS + "beverage_cooler");
                other.putLong("Outlet", offset);
                for (CompoundTag nbt : List.of(strip, meter, other)) {
                    var info = new StructureTemplate.StructureBlockInfo(BlockPos.ZERO, Blocks.AIR.defaultBlockState(), nbt);
                    AflBlockEntityProcessor.INSTANCE.processBlock(level, BlockPos.ZERO, BlockPos.ZERO, info, info, settings);
                }
                String turn = mirror + " " + rotation + " " + pair[0].toShortString() + " -> " + pair[1].toShortString();
                helper.assertTrue(BlockPos.of(strip.getLong("PlugHost")).equals(expected), "PlugHost turned " + turn);
                helper.assertTrue(BlockPos.of(strip.getLong("Outlet")).equals(expected), "strip Outlet turned " + turn);
                helper.assertTrue(BlockPos.of(meter.getLong("Panel")).equals(expected), "meter Panel turned " + turn);
                helper.assertTrue(other.getLong("Outlet") == offset, "another block entity's Outlet left alone " + turn);
                turns++;
            }
        }

        // ---- the export state ----
        CompoundTag template;
        try {
            template = TagParser.parseTag("{blocks:["
                    + "{pos:[0,0,0],state:0,nbt:{id:\"" + NS + "underground_fuel_tank\",Tank:{FluidName:\"" + NS + "gasoline\",Amount:5000}}},"
                    + "{pos:[1,0,0],state:0,nbt:{id:\"" + NS + "fuel_can\",Fluid:{FluidName:\"" + NS + "diesel\",Amount:20}}},"
                    + "{pos:[2,0,0],state:0,nbt:{id:\"" + NS + "portable_diesel_generator\",Fuel:{FluidName:\"" + NS + "diesel\",Amount:9},Running:1b,WarmUntil:123L,Gripper:7,Tripped:1b,Hours:12.5d}},"
                    + "{pos:[3,0,0],state:0,nbt:{id:\"" + NS + "fuel_dispenser\",GasolineLine:{FluidName:\"" + NS + "gasoline\",Amount:200},Flow:1b,Holder0:[I;1,2,3,4]}},"
                    + "{pos:[4,0,0],state:0,nbt:{id:\"" + NS + "submersible_fuel_pump\",Buffer:{FluidName:\"" + NS + "gasoline\",Amount:250},EnergyStored:500}},"
                    + "{pos:[5,0,0],state:0,nbt:{id:\"" + NS + "power_strip\",PlugHost:5L,PlugCarrier:3,AflContainerSearch:{Format:1},Powered:1b}}"
                    + "],palette:[{Name:\"" + NS + "linear_light\",Properties:{lit:\"true\"}}]}");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        var summary = ExportState.normalize(template);
        ListTag b = template.getList("blocks", 10);
        CompoundTag tank = b.getCompound(0).getCompound("nbt"), can = b.getCompound(1).getCompound("nbt"), generator = b.getCompound(2).getCompound("nbt"),
                dispenser = b.getCompound(3).getCompound("nbt"), pump = b.getCompound(4).getCompound("nbt"), strip = b.getCompound(5).getCompound("nbt");
        helper.assertTrue(!tank.contains("Tank") && tank.getCompound(FuelFill.KEY).getString(FuelFill.RULE).equals(FuelFill.UNDERGROUND_TANK.toString()), "tank: fuel swapped for its marker");
        helper.assertTrue(!can.contains("Fluid") && can.getCompound(FuelFill.KEY).getString(FuelFill.FLUID).equals(NS + "diesel"), "can: marker keeps the fuel it held");
        helper.assertTrue(!generator.contains("Fuel") && generator.contains(FuelFill.KEY) && !generator.getBoolean("Running") && !generator.contains("WarmUntil")
                && generator.getInt("Gripper") == -1 && !generator.getBoolean("Tripped") && generator.getDouble("Hours") == 12.5, "generator: marker, stopped, hours kept");
        helper.assertTrue(!dispenser.contains("GasolineLine") && dispenser.getByte("Flow") == 0 && !dispenser.contains("Holder0"), "dispenser: lines empty, no holder");
        helper.assertTrue(!pump.contains("Buffer") && pump.getInt("EnergyStored") == 0, "pump: buffer empty, no energy");
        helper.assertTrue(strip.getLong("PlugHost") == 5L && !strip.contains("PlugCarrier") && !strip.contains("AflContainerSearch") && !strip.getBoolean("Powered"),
                "strip: plug kept, none in hand, no search, not powered");
        helper.assertTrue(summary.litStates() == 1, "lit AFL state counted");

        // ---- fills: the rule over many positions, then placed holders rolling on load ----
        int empty = 0, rolls = 400;
        CompoundTag tankMarker = FuelFill.marker(FuelFill.UNDERGROUND_TANK, null);
        for (int i = 0; i < rolls; i++) {
            var probe = new FluidTank(UndergroundFuelTankBlockEntity.CAPACITY_MB);
            helper.assertTrue(FuelFill.fill(level, new BlockPos(i * 37, 64, i * -11), tankMarker, probe, AflFluids.GASOLINE.get()), "rules loaded");
            if (probe.isEmpty()) empty++;
            else helper.assertTrue(probe.getFluidAmount() >= 1500 && probe.getFluidAmount() <= 12000, "tank holds 5-40 %: " + probe.getFluidAmount());
        }
        helper.assertTrue(empty >= 0.12 * rolls && empty <= 0.28 * rolls, "tank empty about 20 %: " + empty + " / " + rolls);

        var tankBlock = (UndergroundFuelTankBlock) AflBlocks.UNDERGROUND_FUEL_TANK_GASOLINE.get();
        BlockPos root = helper.absolutePos(new BlockPos(2, 1, 2));
        tankBlock.cells(root, Direction.EAST).forEach((position, state) -> level.setBlock(position, state, Block.UPDATE_ALL));
        BlockPos master = UndergroundFuelTankBlock.cellPosition(root, Direction.EAST, UndergroundFuelTankBlock.PORT_ALONG,
                UndergroundFuelTankBlock.PORT_ACROSS, UndergroundFuelTankBlock.PORT_LEVEL);
        var placedTank = (UndergroundFuelTankBlockEntity) level.getBlockEntity(master);
        var tankTag = new CompoundTag();
        tankTag.put(FuelFill.KEY, tankMarker.copy());
        placedTank.load(tankTag);
        var expectTank = new FluidTank(UndergroundFuelTankBlockEntity.CAPACITY_MB);
        FuelFill.fill(level, master, tankMarker, expectTank, AflFluids.GASOLINE.get());
        helper.assertTrue(placedTank.tank().getFluidAmount() == expectTank.getFluidAmount()
                && (placedTank.tank().isEmpty() || placedTank.tank().getFluid().getFluid().isSame(AflFluids.GASOLINE.get())), "placed tank rolled its marker");
        var saved = placedTank.saveWithoutMetadata();
        helper.assertTrue(!saved.contains(FuelFill.KEY), "placed tank dropped its marker");

        BlockPos canAt = helper.absolutePos(new BlockPos(12, 1, 2));
        level.setBlock(canAt, AflBlocks.JERRY_CAN.get().defaultBlockState(), Block.UPDATE_ALL);
        var placedCan = (FuelCanBlockEntity) level.getBlockEntity(canAt);
        var canTag = new CompoundTag();
        canTag.put(FuelFill.KEY, FuelFill.marker(FuelFill.FUEL_CONTAINER, new ResourceLocation(NS + "diesel")));
        placedCan.load(canTag);
        helper.assertTrue(placedCan.tank().isEmpty() || placedCan.tank().getFluid().getFluid().isSame(AflFluids.DIESEL.get())
                && placedCan.tank().getFluidAmount() >= 2 && placedCan.tank().getFluidAmount() <= 20, "can: none, or 10-100 % of the diesel it held");

        BlockPos generatorAt = helper.absolutePos(new BlockPos(12, 1, 8));
        level.setBlock(generatorAt, AflBlocks.PORTABLE_DIESEL_GENERATOR.get().defaultBlockState(), Block.UPDATE_ALL);
        var placedGenerator = level.getBlockEntity(generatorAt);
        var generatorTag = new CompoundTag();
        generatorTag.put(FuelFill.KEY, FuelFill.marker(FuelFill.PORTABLE_GENERATOR, null));
        placedGenerator.load(generatorTag);
        var generatorFuel = net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(placedGenerator.saveWithoutMetadata().getCompound("Fuel"));
        helper.assertTrue(generatorFuel.isEmpty() || generatorFuel.getFluid().isSame(AflFluids.DIESEL.get())
                && generatorFuel.getAmount() >= 1 && generatorFuel.getAmount() <= 8, "generator: none, or 1-8 L of diesel");

        ApocalypseFirstLight.LOGGER.info("[AFL EXPORT STATE TEST] PASS {} offset turns, export {}, tank empty {} / {}", turns, summary.text(), empty, rolls);
        helper.succeed();
    }
}
