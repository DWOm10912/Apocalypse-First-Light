package com.antaurora.apofirstlight.dev;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.UndergroundFuelTankBlock;
import com.antaurora.apofirstlight.blockentity.UndergroundFuelTankBlockEntity;
import com.antaurora.apofirstlight.energy.PlugSocketHost;
import com.antaurora.apofirstlight.fluid.FuelFill;
import com.antaurora.apofirstlight.registry.AflFluids;
import com.antaurora.apofirstlight.worldgen.structure.AflBlockEntityProcessor;
import com.antaurora.apofirstlight.worldgen.structure.StructureDefinition;
import com.antaurora.apofirstlight.worldgen.structure.StructureDefinitionLoader;
import com.antaurora.apofirstlight.worldgen.structure.StructureNbtReader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Fuel Stop A1 as the formal asset gas_station_01 (docs/worldgen/fuel_stop_a1_design_v1.md "冻结前", 2026-10-10):
 * <ul>
 * <li>Definition: data/apocalypse_firstlight/afl_worldgen/structures/gas_station_01.json validates against the shipped
 * NBT (StructureDefinitionLoader.validate: sockets on the edge, facing out, in air, inside every declared rotation).</li>
 * <li>Placement: the NBT placed at all four rotations with worldgen/structure/AflBlockEntityProcessor, then after 40 ticks
 * (neighbour updates, the tanks' integrity ticks, the fill rolls):
 *   every plugged appliance still points at its own socket and that block is a socket host;
 *   every tank master is whole, rolled (no marker) and within its rule; the portable generator rolled;
 *   both entrance sockets are air over asphalt;
 *   no block of the NBT turned to air (an attachment that broke), and every state is still the NBT state turned.</li>
 * </ul>
 * Run with {@code src/dev/gas-station-01-gametest.init.gradle}.
 */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class GasStation01GameTests {
    private static final ResourceLocation ID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "gas_station_01");
    private static final String NS = ApocalypseFirstLight.MOD_ID + ":";

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)) return;
        var level = event.getServer().overworld();
        var tag = TagParser.parseTag("{size:[4,2,4],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        var blocks = new ListTag();
        for (int x = 0; x < 4; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 4; z++) {
            var block = new CompoundTag();
            var position = new ListTag();
            for (int coordinate : new int[]{x, y, z}) position.add(IntTag.valueOf(coordinate));
            block.put("pos", position);
            block.putInt("state", 0);
            blocks.add(block);
        }
        tag.put("blocks", blocks);
        level.getStructureManager().getOrCreate(new ResourceLocation("afl_a1_tests", "a1_empty"))
                .load(level.holderLookup(Registries.BLOCK), tag);
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("gas_station_01", "afl_a1_tests:gas_station_01", "afl_a1_tests:a1_empty", 1200, 0L, true,
                GasStation01GameTests::run));
    }

    private record Placed(Rotation rotation, BlockPos at, StructurePlaceSettings settings) {
        BlockPos world(BlockPos local) {
            return at.offset(StructureTemplate.calculateRelativePosition(settings, local));
        }
    }

    private static void run(GameTestHelper helper) {
        var level = helper.getLevel();
        var resources = level.getServer().getResourceManager();

        // ---- the definition against the NBT ----
        CompoundTag root;
        String json;
        try (var in = resources.getResourceOrThrow(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "structures/gas_station_01.nbt")).open();
             Reader reader = resources.getResourceOrThrow(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_worldgen/structures/gas_station_01.json")).openAsReader()) {
            root = NbtIo.readCompressed(in);
            var text = new StringBuilder();
            char[] chunk = new char[4096];
            for (int n; (n = reader.read(chunk)) > 0; ) text.append(chunk, 0, n);
            json = text.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        var loaded = StructureDefinitionLoader.validate(ID, json, StructureNbtReader.inspect(root));
        helper.assertTrue(loaded.validation().valid(), "definition valid: " + loaded.validation().issues());
        StructureDefinition definition = loaded.definition().orElseThrow();

        // the NBT's own cells: names, plugged appliances (offset), tank masters, the generator
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        List<BlockPos> solid = new ArrayList<>();
        Map<BlockPos, BlockPos> plugs = new java.util.HashMap<>();   // appliance -> its socket, both local
        List<BlockPos> masters = new ArrayList<>();
        BlockPos generator = null;
        for (Tag t : root.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag b = (CompoundTag) t;
            ListTag p = b.getList("pos", Tag.TAG_INT);
            BlockPos local = new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2));
            String name = palette.getCompound(b.getInt("state")).getString("Name");
            if (!name.endsWith(":air")) solid.add(local);
            if (!b.contains("nbt")) continue;
            CompoundTag nbt = b.getCompound("nbt");
            if (nbt.contains("PlugHost", Tag.TAG_LONG)) plugs.put(local, local.offset(BlockPos.of(nbt.getLong("PlugHost"))));
            if (nbt.getString("id").equals(NS + "underground_fuel_tank")
                    && UndergroundFuelTankBlock.isMaster(NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), palette.getCompound(b.getInt("state")))))
                masters.add(local);
            if (nbt.getString("id").equals(NS + "portable_diesel_generator")) generator = local;
        }
        helper.assertTrue(plugs.size() == 5 && masters.size() == 3 && generator != null, "NBT holds 5 plugs, 3 tanks, the generator");

        // every AFL state of the NBT turns with it: a horizontal facing a quarter clockwise, an axis X <-> Z, side flags with
        // their sides. A block without its own rotate keeps its old orientation in a turned building (2026-10-10: cables, pipes)
        java.util.Set<String> stuck = new java.util.TreeSet<>();
        for (int i = 0; i < palette.size(); i++) {
            BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), palette.getCompound(i));
            String block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock()) + "";
            if (!block.startsWith(NS)) continue;
            BlockState turned = state.rotate(Rotation.CLOCKWISE_90);
            for (var property : state.getProperties()) {
                Object value = state.getValue(property), now = turned.getValue(property);
                if (value instanceof net.minecraft.core.Direction d) {
                    if (d.getAxis().isHorizontal() && now != d.getClockWise()) stuck.add(block + " " + property.getName());
                } else if (value instanceof net.minecraft.core.Direction.Axis axis && axis.isHorizontal()) {
                    if (now == axis) stuck.add(block + " " + property.getName());
                } else if (List.of("north", "east", "south", "west").contains(property.getName()) && turned.hasProperty(property)) {
                    var side = net.minecraft.core.Direction.byName(property.getName());
                    var to = state.getBlock().getStateDefinition().getProperty(side.getClockWise().getName());
                    if (to != null && !turned.getValue(to).equals(value)) stuck.add(block + " " + property.getName());
                }
            }
        }
        helper.assertTrue(stuck.isEmpty(), "states that do not turn with the building: " + stuck);

        // ---- four placements, 160 blocks apart, far from the test's own cells ----
        StructureTemplate template = level.getStructureManager().get(ID).orElseThrow();
        BlockPos base = helper.absolutePos(new BlockPos(300, 0, 300));
        List<Placed> placed = new ArrayList<>();
        int slot = 0;
        for (Rotation rotation : Rotation.values()) {
            var settings = new StructurePlaceSettings().setRotation(rotation).addProcessor(AflBlockEntityProcessor.INSTANCE);
            BlockPos at = base.offset((slot % 2) * 160, 0, (slot / 2) * 160);
            slot++;
            template.placeInWorld(level, at, at, settings, level.getRandom(), Block.UPDATE_ALL);
            placed.add(new Placed(rotation, at, settings));
        }
        final BlockPos generatorLocal = generator;
        helper.runAfterDelay(40, () -> {
            Map<String, Integer> changed = new TreeMap<>();
            int checks = 0;
            for (Placed p : placed) {
                String turn = " (" + p.rotation() + ")";
                // plugs
                for (var e : plugs.entrySet()) {
                    BlockPos appliance = p.world(e.getKey()), socket = p.world(e.getValue());
                    var be = level.getBlockEntity(appliance);
                    helper.assertTrue(be != null, "appliance at " + appliance.toShortString() + turn);
                    CompoundTag saved = be.saveWithoutMetadata();
                    helper.assertTrue(saved.contains("PlugHost", Tag.TAG_LONG), "still plugged " + appliance.toShortString() + turn);
                    BlockPos host = appliance.offset(BlockPos.of(saved.getLong("PlugHost")));
                    helper.assertTrue(host.equals(socket), "plug at its own socket " + host.toShortString() + " vs " + socket.toShortString() + turn);
                    Block hostBlock = level.getBlockState(host).getBlock();
                    helper.assertTrue(hostBlock instanceof PlugSocketHost || hostBlock instanceof com.antaurora.apofirstlight.block.WallOutletBlock
                            || hostBlock instanceof com.antaurora.apofirstlight.block.PowerStripBlock, "socket host there " + host.toShortString() + turn);
                    checks++;
                }
                // tanks
                for (BlockPos local : masters) {
                    BlockPos w = p.world(local);
                    BlockState state = level.getBlockState(w);
                    helper.assertTrue(state.getBlock() instanceof UndergroundFuelTankBlock && UndergroundFuelTankBlock.isMaster(state), "tank master kept " + w.toShortString() + turn);
                    var tank = (UndergroundFuelTankBlockEntity) level.getBlockEntity(w);
                    helper.assertTrue(!tank.saveWithoutMetadata().contains(FuelFill.KEY), "tank rolled " + w.toShortString() + turn);
                    int amount = tank.tank().getFluidAmount();
                    helper.assertTrue(amount == 0 || amount >= 1500 && amount <= 12000
                            && tank.tank().getFluid().getFluid().isSame(((UndergroundFuelTankBlock) state.getBlock()).fuel()), "tank fill in rule " + amount + turn);
                    checks++;
                }
                // generator
                var gen = level.getBlockEntity(p.world(generatorLocal));
                CompoundTag g = gen.saveWithoutMetadata();
                FluidStack fuel = FluidStack.loadFluidStackFromNBT(g.getCompound("Fuel"));
                helper.assertTrue(!g.contains(FuelFill.KEY) && (fuel.isEmpty() || fuel.getFluid().isSame(AflFluids.DIESEL.get())
                        && fuel.getAmount() >= 1 && fuel.getAmount() <= 8), "generator rolled " + fuel.getAmount() + turn);
                // entrances
                for (var socket : definition.sockets()) {
                    BlockPos w = p.world(socket.localPosition());
                    helper.assertTrue(level.getBlockState(w).isAir(), "socket " + socket.name() + " air" + turn);
                    helper.assertTrue(level.getBlockState(w.below()).is(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new ResourceLocation(NS + "asphalt"))),
                            "socket " + socket.name() + " over asphalt" + turn);
                    checks++;
                }
                // nothing broke away
                for (BlockPos local : solid) {
                    BlockState now = level.getBlockState(p.world(local));
                    helper.assertTrue(!now.isAir(), "block kept at " + local.toShortString() + turn);
                }
                checks += solid.size();
            }
            // after the neighbour updates every state is still exactly the NBT state turned (2026-10-10: cables and pipes were not)
            for (Placed p : placed) {
                StructurePlaceSettings s = p.settings();
                for (Tag t : root.getList("blocks", Tag.TAG_COMPOUND)) {
                    CompoundTag b = (CompoundTag) t;
                    ListTag pos = b.getList("pos", Tag.TAG_INT);
                    BlockPos local = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
                    BlockState expected = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), palette.getCompound(b.getInt("state"))).rotate(s.getRotation());
                    BlockState now = level.getBlockState(p.world(local));
                    if (!expected.isAir() && !now.equals(expected)) changed.merge(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(now.getBlock()) + "", 1, Integer::sum);
                }
            }
            helper.assertTrue(changed.isEmpty(), "states changed by neighbour updates (a block that does not turn right): " + changed);
            ApocalypseFirstLight.LOGGER.info("[AFL A1 ASSET TEST] PASS 4 rotations, {} checks, {} palette states turn; states changed by neighbour updates: {}",
                    checks, palette.size(), changed);
            helper.succeed();
        });
    }
}
