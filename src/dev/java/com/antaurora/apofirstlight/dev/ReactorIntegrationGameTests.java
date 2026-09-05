package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.ChemicalReactorBlock;
import com.antaurora.apofirstlight.block.FluidPipeBlock;
import com.antaurora.apofirstlight.blockentity.ChemicalReactorBlockEntity;
import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.antaurora.apofirstlight.compat.jade.MachineJadeServerDataProvider;
import com.antaurora.apofirstlight.fluid.*;
import com.antaurora.apofirstlight.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReactorIntegrationGameTests {
    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void northWasteUpward(GameTestHelper h) { checkRoute(h, Direction.NORTH, true); }
    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void southWasteUpward(GameTestHelper h) { checkRoute(h, Direction.SOUTH, true); }
    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void eastWasteUpward(GameTestHelper h) { checkRoute(h, Direction.EAST, true); }
    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void westWasteUpward(GameTestHelper h) { checkRoute(h, Direction.WEST, true); }
    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void horizontalWaste(GameTestHelper h) { checkRoute(h, Direction.NORTH, false); }

    private static void checkRoute(GameTestHelper h, Direction facing, boolean upward) {
        Fixture f = fixture(h, facing, upward);
        Direction waste = facing.getCounterClockWise();
        Direction input = facing.getClockWise();
        IFluidHandler out = handler(f.reactor, waste);
        h.assertTrue(out.getFluidInTank(0).getAmount() == 1_000
                && out.drain(25, IFluidHandler.FluidAction.SIMULATE).getAmount() == 25,
                "Right port must expose and simulate 25 mB of waste");
        h.assertTrue(out.fill(waste(25), IFluidHandler.FluidAction.EXECUTE) == 0, "Waste port accepted external fill");
        IFluidHandler in = handler(f.reactor, input);
        h.assertTrue(in.fill(new FluidStack(Fluids.WATER, 50), IFluidHandler.FluidAction.EXECUTE) == 25
                && in.drain(25, IFluidHandler.FluidAction.SIMULATE).isEmpty(), "Input fill-only port/budget failed");
        for (Direction direction : List.of(facing, facing.getOpposite(), Direction.UP, Direction.DOWN)) {
            h.assertTrue(!f.reactor.getCapability(ForgeCapabilities.FLUID_HANDLER, direction).isPresent(),
                    "Unexpected fluid capability on " + direction);
        }
        int transferred = FluidPipeTransfer.transferFrom(h.getLevel(), f.reactor, waste, f.reactor::restoreWasteFluid);
        h.assertTrue(transferred == 25 && f.target.getFluidAmount() == 25
                && f.reactor.getWasteFluid().getAmount() == 975, "First waste route transfer failed: " + transferred);
        h.assertTrue(FluidPipeTransfer.transferFrom(h.getLevel(), f.reactor, waste, f.reactor::restoreWasteFluid) == 0,
                "Repeated same-tick drain exceeded independent output budget");
        h.runAfterDelay(40, () -> {
            h.assertTrue(f.reactor.getWasteFluid().isEmpty() && f.target.getFluidAmount() == 1_000,
                    "40 ticks failed to move 1000 mB through " + facing + ", upward=" + upward);
            ApocalypseFirstLight.LOGGER.info("[AFL REACTOR TEST] facing={}, upward={}, source=0 target=1000", facing, upward);
            h.succeed();
        });
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void wasteBackpressureAndResume(GameTestHelper h) {
        Fixture f = fixture(h, Direction.NORTH, true);
        f.target.restoreControllerFluid(waste(20_000));
        h.runAfterDelay(2, () -> {
            h.assertTrue(f.reactor.getWasteFluid().getAmount() == 1_000
                    && f.target.getFluidAmount() == 20_000, "Full target drained source");
            h.assertTrue(FluidPipeTransfer.transferFrom(h.getLevel(), f.reactor, Direction.WEST,
                    f.reactor::restoreWasteFluid) == 0, "Full target transfer must be zero");
            handler(f.target, Direction.DOWN).drain(25, IFluidHandler.FluidAction.EXECUTE);
            h.runAfterDelay(2, () -> {
                h.assertTrue(f.target.getFluidAmount() == 20_000 && f.reactor.getWasteFluid().getAmount() == 975,
                        "Waste output did not resume exactly 25 mB after clearing space");
                h.succeed();
            });
        });
    }

    @GameTest(template = "network_empty")
    public static void jadeDataAndSavedItemTooltips(GameTestHelper h) {
        BlockPos pos = new BlockPos(7, 2, 7);
        h.setBlock(pos, AflBlocks.CHEMICAL_REACTOR.get());
        ChemicalReactorBlockEntity reactor = (ChemicalReactorBlockEntity) h.getBlockEntity(pos);
        CompoundTag empty = new CompoundTag();
        MachineJadeServerDataProvider.appendChemicalData(empty, reactor);
        h.assertTrue(empty.contains(MachineJadeServerDataProvider.ENERGY_STORED)
                && !empty.contains(MachineJadeServerDataProvider.INPUT_FLUID)
                && !empty.contains(MachineJadeServerDataProvider.WASTE_FLUID)
                && !empty.contains(MachineJadeServerDataProvider.PROCESSING_PROGRESS), "Empty Jade sections leaked");
        for (int mask = 0; mask < 4; mask++) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("EnergyStored", 60_000);
            if ((mask & 1) != 0) tag.put("InputTank", new FluidStack(Fluids.WATER, 1_000).writeToNBT(new CompoundTag()));
            if ((mask & 2) != 0) tag.put("WasteTank", waste(1_000).writeToNBT(new CompoundTag()));
            reactor.load(tag);
            ItemStack drop = new ItemStack(AflItems.CHEMICAL_REACTOR.get());
            reactor.writeDropData(drop);
            CompoundTag before = drop.getTag().copy();
            List<Component> lines = new ArrayList<>();
            StoredFluidTooltip.append(drop, lines);
            h.assertTrue(lines.size() == 1 + Integer.bitCount(mask) * 2 && before.equals(drop.getTag()),
                    "Reactor tooltip sections or read-only contract failed for mask " + mask);
            reactor.load(BlockItem.getBlockEntityData(drop));
            h.assertTrue(reactor.getStoredEnergy() == 60_000
                    && reactor.getInputFluid().getAmount() == ((mask & 1) != 0 ? 1_000 : 0)
                    && reactor.getWasteFluid().getAmount() == ((mask & 2) != 0 ? 1_000 : 0), "Reactor saved state roundtrip failed");
            CompoundTag jade = new CompoundTag();
            MachineJadeServerDataProvider.appendChemicalData(jade, reactor);
            h.assertTrue(jade.contains(MachineJadeServerDataProvider.INPUT_FLUID) == ((mask & 1) != 0)
                    && jade.contains(MachineJadeServerDataProvider.WASTE_FLUID) == ((mask & 2) != 0), "Jade fluid conditions failed");
        }
        for (FluidStack fluid : List.of(new FluidStack(Fluids.WATER, 1_000), waste(1_000))) {
            ItemStack tankItem = new ItemStack(AflItems.FLUID_TANK.get());
            FluidTankStoredFluid.write(tankItem, fluid);
            CompoundTag before = tankItem.getTag().copy();
            List<Component> lines = new ArrayList<>();
            StoredFluidTooltip.append(tankItem, lines);
            h.assertTrue(lines.size() == 2 && before.equals(tankItem.getTag()), "Tank tooltip missing or mutated NBT");
            h.setBlock(new BlockPos(3, 2, 3), AflBlocks.FLUID_TANK.get());
            FluidTankBlockEntity tank = (FluidTankBlockEntity) h.getBlockEntity(new BlockPos(3, 2, 3));
            tank.load(BlockItem.getBlockEntityData(tankItem));
            h.assertTrue(tank.getFluid().isFluidEqual(fluid) && tank.getFluidAmount() == 1_000, "Tank replacement load mismatch");
        }
        CompoundTag malformed = new CompoundTag();
        CompoundTag badFluid = new CompoundTag();
        badFluid.putString("FluidName", "INVALID ID");
        badFluid.putInt("Amount", 1_000);
        malformed.put("Fluid", badFluid);
        h.assertTrue(StoredFluidTooltip.read(malformed, "Fluid", 20_000).isEmpty(), "Malformed fluid should be hidden");
        badFluid.putString("FluidName", "missing:unknown_fluid");
        h.assertTrue(StoredFluidTooltip.read(malformed, "Fluid", 20_000).isEmpty(), "Unknown fluid should be hidden");
        badFluid.putString("FluidName", "minecraft:water");
        badFluid.putInt("Amount", Integer.MAX_VALUE);
        h.assertTrue(StoredFluidTooltip.read(malformed, "Fluid", 20_000).getAmount() == 20_000, "Hover amount was not clamped");
        badFluid.putInt("Amount", -1);
        h.assertTrue(StoredFluidTooltip.read(malformed, "Fluid", 20_000).isEmpty(), "Negative amount should be hidden");

        CompoundTag work = new CompoundTag();
        work.putInt("EnergyStored", 32);
        work.put("InputTank", new FluidStack(Fluids.WATER, 1_000).writeToNBT(new CompoundTag()));
        reactor.load(work);
        reactor.setItem(ChemicalReactorBlockEntity.INPUT_SLOT, new ItemStack(AflItems.WOLFRAMITE.get()));
        ChemicalReactorBlockEntity.serverTick(h.getLevel(), reactor.getBlockPos(), reactor.getBlockState(), reactor);
        ChemicalReactorBlockEntity.serverTick(h.getLevel(), reactor.getBlockPos(), reactor.getBlockState(), reactor);
        CompoundTag paused = new CompoundTag();
        MachineJadeServerDataProvider.appendChemicalData(paused, reactor);
        h.assertTrue(paused.getInt(MachineJadeServerDataProvider.PROCESSING_PROGRESS) == 1
                && paused.getInt(MachineJadeServerDataProvider.PROCESSING_TIME) == 300
                && paused.contains(MachineJadeServerDataProvider.INPUT), "Jade must retain paused real progress");
        h.succeed();
    }

    @GameTest(template = "network_empty")
    public static void survivalBreakTooltipAndBlockItemReplacement(GameTestHelper h) {
        var player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        for (int variant = 0; variant < 3; variant++) {
            BlockPos local = new BlockPos(3 + variant * 4, 2, 3);
            var block = variant == 2 ? AflBlocks.CHEMICAL_REACTOR.get() : AflBlocks.FLUID_TANK.get();
            h.setBlock(local.below(), net.minecraft.world.level.block.Blocks.STONE);
            h.setBlock(local, block);
            FluidStack fluid = variant == 0 ? new FluidStack(Fluids.WATER, 1_000) : waste(1_000);
            if (h.getBlockEntity(local) instanceof FluidTankBlockEntity tank) {
                tank.restoreControllerFluid(fluid);
            } else {
                CompoundTag data = new CompoundTag();
                data.putInt("EnergyStored", 60_000);
                data.put("InputTank", new FluidStack(Fluids.WATER, 1_000).writeToNBT(new CompoundTag()));
                data.put("WasteTank", waste(1_000).writeToNBT(new CompoundTag()));
                h.getBlockEntity(local).load(data);
            }
            BlockPos world = h.absolutePos(local);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
            h.assertTrue(player.gameMode.destroyBlock(world), "Survival diamond-pickaxe break failed");
            var drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(world).inflate(1), e -> e.getItem().is(block.asItem()));
            h.assertTrue(drops.size() == 1, "Expected one saved machine drop, got " + drops.size());
            ItemStack drop = drops.get(0).getItem().copy();
            drops.get(0).discard();
            var before = drop.getTag().copy();
            List<Component> lines = new ArrayList<>();
            StoredFluidTooltip.append(drop, lines);
            h.assertTrue(lines.size() == (variant == 2 ? 5 : 2) && before.equals(drop.getTag()),
                    "Actual survival drop tooltip mismatch");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, drop);
            var hit = new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atBottomCenterOf(world), Direction.UP, world.below(), false);
            var context = new net.minecraft.world.item.context.UseOnContext(player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            h.assertTrue(drop.useOn(context).consumesAction(), "BlockItem placement failed");
            if (h.getBlockEntity(local) instanceof FluidTankBlockEntity tank) {
                h.assertTrue(tank.getFluid().isFluidEqual(fluid) && tank.getFluidAmount() == 1_000,
                        "Actual tank break/place changed fluid");
            } else if (h.getBlockEntity(local) instanceof ChemicalReactorBlockEntity reactor) {
                h.assertTrue(reactor.getStoredEnergy() == 60_000 && reactor.getInputFluid().getAmount() == 1_000
                        && reactor.getWasteFluid().getAmount() == 1_000, "Actual reactor break/place changed saved contents");
            } else {
                h.fail("Replacement machine missing");
            }
        }
        h.succeed();
    }

    private static Fixture fixture(GameTestHelper h, Direction facing, boolean upward) {
        BlockPos pos = new BlockPos(7, 2, 7);
        h.setBlock(pos, AflBlocks.CHEMICAL_REACTOR.get().defaultBlockState().setValue(ChemicalReactorBlock.FACING, facing));
        ChemicalReactorBlockEntity reactor = (ChemicalReactorBlockEntity) h.getBlockEntity(pos);
        reactor.restoreWasteFluid(waste(1_000));
        Direction out = facing.getCounterClockWise();
        List<BlockPos> path = new ArrayList<>();
        BlockPos first = pos.relative(out);
        if (upward) {
            for (int y = 0; y <= 4; y++) path.add(first.above(y));
            for (int i = 2; i <= 4; i++) path.add(pos.relative(out, i).above(4));
        } else {
            for (int i = 1; i <= 4; i++) path.add(pos.relative(out, i));
        }
        BlockPos targetPos = path.get(path.size() - 1).below();
        h.setBlock(targetPos, AflBlocks.FLUID_TANK.get());
        for (BlockPos pipe : path) h.setBlock(pipe, AflBlocks.FLUID_PIPE.get());
        for (BlockPos pipe : path) {
            BlockPos worldPos = h.absolutePos(pipe);
            h.setBlock(pipe, FluidPipeBlock.withStructuralConnections(h.getLevel(), worldPos,
                    AflBlocks.FLUID_PIPE.get().defaultBlockState()));
        }
        return new Fixture(reactor, (FluidTankBlockEntity) h.getBlockEntity(targetPos));
    }

    private static IFluidHandler handler(net.minecraft.world.level.block.entity.BlockEntity be, Direction face) {
        return be.getCapability(ForgeCapabilities.FLUID_HANDLER, face).resolve().orElseThrow();
    }
    private static FluidStack waste(int amount) { return new FluidStack(AflFluids.INDUSTRIAL_WASTE.get(), amount); }
    private record Fixture(ChemicalReactorBlockEntity reactor, FluidTankBlockEntity target) {}
}
