package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.PowerCableBlock;
import com.antaurora.apofirstlight.blockentity.*;
import com.antaurora.apofirstlight.energy.*;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.*;

@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class PowerNetworkGameTests {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void createTemplate(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)) return;
        var level = event.getServer().overworld();
        var tag = net.minecraft.nbt.TagParser.parseTag("{size:[16,10,16],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        var blocks = new net.minecraft.nbt.ListTag();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 10; y++) for (int z = 0; z < 16; z++) {
            var block = new CompoundTag();
            var pos = new net.minecraft.nbt.ListTag();
            pos.add(net.minecraft.nbt.IntTag.valueOf(x));
            pos.add(net.minecraft.nbt.IntTag.valueOf(y));
            pos.add(net.minecraft.nbt.IntTag.valueOf(z));
            block.put("pos", pos);
            block.putInt("state", 0);
            blocks.add(block);
        }
        tag.put("blocks", blocks);
        level.getStructureManager().getOrCreate(new net.minecraft.resources.ResourceLocation(
                ApocalypseFirstLight.MOD_ID, "network_empty")).load(
                level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag);
    }
    @GameTest(template = "network_empty")
    public static void oneThermalRemainderRotatesAndNetworkSettlesOnce(GameTestHelper h) {
        Network n = network(h, 1, 4, false);
        settle(h, n);
        int[] initial = n.consumers.stream().mapToInt(IEnergyStorage::getEnergyStored).toArray();
        h.assertTrue(Arrays.stream(initial).sum() == 16 && spread(initial) == 1, "Expected 6/5/5 in any rotation");
        settle(h, n);
        h.assertTrue(n.consumers.stream().mapToInt(IEnergyStorage::getEnergyStored).sum() == 16,
                "Repeated producer scheduling distributed twice in one gameTime");
        // Observe real successive server ticks; exactly one remainder must rotate per tick.
        h.runAfterDelay(3, () -> {
            int[] delta = new int[3];
            for (int i = 0; i < 3; i++) delta[i] = n.consumers.get(i).getEnergyStored() - initial[i];
            h.assertTrue(Arrays.equals(delta, new int[]{16, 16, 16}),
                    "Three real ticks must distribute exactly 16 FE to each receiver: " + Arrays.toString(delta));
            ApocalypseFirstLight.LOGGER.info("[AFL POWER V2 TEST] 1 Thermal: initial={}, next 3 ticks={}", initial, delta);
            h.succeed();
        });
    }

    @GameTest(template = "network_empty")
    public static void threeThermalsAggregate(GameTestHelper h) {
        Network n = network(h, 3, 4, false);
        settle(h, n);
        assertAmounts(h, n, 16, 16, 16);
        assertConservation(h, n, 48);
        h.succeed();
    }

    @GameTest(template = "network_empty")
    public static void sixThermalsReachCrusherLimits(GameTestHelper h) {
        Network n = network(h, 6, 4, false);
        settle(h, n);
        assertAmounts(h, n, 32, 32, 32);
        assertConservation(h, n, 96);
        for (IEnergyStorage consumer : n.consumers) {
            h.assertTrue(consumer.receiveEnergy(1, true) == 0, "Receive budget exceeded 32");
        }
        h.succeed();
    }

    @GameTest(template = "network_empty")
    public static void leftoverAndMixedReceiveLimits(GameTestHelper h) {
        Network n = network(h, 4, 4, true);
        CompoundTag tag = new CompoundTag();
        tag.putInt("EnergyStored", 19_996);
        h.getBlockEntity(new BlockPos(2, 2, 5)).load(tag);
        settle(h, n);
        assertAmounts(h, n, 20_000, 30, 30);
        assertConservation(h, n, 64);
        h.succeed();
    }

    @GameTest(template = "network_empty")
    public static void energyCellModesAndDisconnectedComponents(GameTestHelper h) {
        Network n = network(h, 1, 4, true);
        BlockPos cellPos = new BlockPos(6, 2, 3);
        h.setBlock(cellPos, AflBlocks.ENERGY_CELL.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        EnergyCellBlockEntity cell = (EnergyCellBlockEntity) h.getBlockEntity(cellPos);
        CompoundTag tag = new CompoundTag();
        tag.putInt("EnergyStored", 1_000);
        cell.load(tag);
        settle(h, n);
        h.assertTrue(cell.getStoredEnergy() == 1_004, "Charge-mode cell must be one of four consumers");
        h.assertTrue(energy(cell).extractEnergy(128, true) == 0, "Charge cell cannot produce");

        // A separate component gets its own discharge cell; it must not draw from the first grid.
        Network isolated = network(h, 0, 10, true);
        BlockPos dischargePos = new BlockPos(2, 2, 9);
        h.setBlock(dischargePos, AflBlocks.ENERGY_CELL.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        EnergyCellBlockEntity discharge = (EnergyCellBlockEntity) h.getBlockEntity(dischargePos);
        discharge.load(tag);
        discharge.setMode(EnergyCellMode.DISCHARGE);
        h.assertTrue(energy(discharge).receiveEnergy(128, true) == 0, "Discharge cell cannot consume");
        EnergyCellBlockEntity.serverTick(h.getLevel(), discharge.getBlockPos(), discharge.getBlockState(), discharge);
        PowerCableTransfer.distributePending(h.getLevel());
        assertAmounts(h, isolated, 32, 48, 48);
        h.assertTrue(discharge.getStoredEnergy() == 872 && cell.getStoredEnergy() == 1_004,
                "Discharge output must be 128 and separate networks must stay isolated");
        h.succeed();
    }

    private static Network network(GameTestHelper h, int producers, int z, boolean mixed) {
        List<ThermalGeneratorBlockEntity> generators = new ArrayList<>();
        List<IEnergyStorage> consumers = new ArrayList<>();
        for (int i = 0; i < producers; i++) {
            BlockPos pos = new BlockPos(2 + i * 2, 2, z - 1);
            h.setBlock(pos, AflBlocks.THERMAL_GENERATOR.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            ThermalGeneratorBlockEntity generator = (ThermalGeneratorBlockEntity) h.getBlockEntity(pos);
            CompoundTag tag = new CompoundTag();
            tag.putInt("EnergyStored", 1_000);
            generator.load(tag);
            generators.add(generator);
        }
        for (int i = 0; i < 3; i++) {
            Block block = !mixed || i == 0 ? AflBlocks.CRUSHER.get()
                    : i == 1 ? AflBlocks.CHEMICAL_REACTOR.get() : AflBlocks.INDUSTRIAL_FURNACE.get();
            BlockPos pos = new BlockPos(2 + i * 4, 2, z + 1);
            h.setBlock(pos, block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
            consumers.add(energy(h.getBlockEntity(pos)));
        }
        for (int x = 2; x <= 12; x++) h.setBlock(new BlockPos(x, 2, z), AflBlocks.POWER_CABLE.get());
        for (int x = 2; x <= 12; x++) {
            BlockPos local = new BlockPos(x, 2, z);
            BlockPos world = h.absolutePos(local);
            var state = h.getBlockState(local);
            for (Direction direction : Direction.values()) {
                var neighbor = h.getLevel().getBlockState(world.relative(direction));
                state = state.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(direction),
                        neighbor.is(AflBlocks.POWER_CABLE.get())
                                || PowerCableBlock.isUtilityPortFace(neighbor, direction.getOpposite()));
            }
            h.setBlock(local, state);
        }
        return new Network(generators, consumers);
    }

    private static IEnergyStorage energy(BlockEntity be) {
        return be.getCapability(ForgeCapabilities.ENERGY, PowerCableBlock.utilityPortFace(be.getBlockState()))
                .resolve().orElseThrow();
    }

    private static void settle(GameTestHelper h, Network n) {
        for (ThermalGeneratorBlockEntity generator : n.generators) {
            ThermalGeneratorBlockEntity.serverTick(h.getLevel(), generator.getBlockPos(), generator.getBlockState(), generator);
        }
        PowerCableTransfer.distributePending(h.getLevel());
    }

    private static void assertAmounts(GameTestHelper h, Network n, int... expected) {
        int[] actual = n.consumers.stream().mapToInt(IEnergyStorage::getEnergyStored).toArray();
        h.assertTrue(Arrays.equals(actual, expected), "Allocation " + Arrays.toString(actual) + " expected " + Arrays.toString(expected));
        ApocalypseFirstLight.LOGGER.info("[AFL POWER V2 TEST] {} Thermal, allocation={}", n.generators.size(), actual);
    }

    private static void assertConservation(GameTestHelper h, Network n, int transferred) {
        int removed = n.generators.size() * 1_000 - n.generators.stream().mapToInt(ThermalGeneratorBlockEntity::getStoredEnergy).sum();
        h.assertTrue(removed == transferred, "Producer FE removal differs from consumer gains");
    }

    private static int spread(int[] values) {
        return Arrays.stream(values).max().orElseThrow() - Arrays.stream(values).min().orElseThrow();
    }
    private record Network(List<ThermalGeneratorBlockEntity> generators, List<IEnergyStorage> consumers) {}
}
