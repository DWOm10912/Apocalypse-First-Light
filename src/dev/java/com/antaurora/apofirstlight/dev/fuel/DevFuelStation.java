package com.antaurora.apofirstlight.dev.fuel;

import com.antaurora.apofirstlight.block.FuelDispenserBlock;
import com.antaurora.apofirstlight.block.FuelDispenserSumpBlock;
import com.antaurora.apofirstlight.block.FuelSumpCoverBlock;
import com.antaurora.apofirstlight.block.UndergroundFuelTankBlock;
import com.antaurora.apofirstlight.blockentity.EnergyCellBlockEntity;
import com.antaurora.apofirstlight.blockentity.UndergroundFuelTankBlockEntity;
import com.antaurora.apofirstlight.block.FluidPipeBlock;
import com.antaurora.apofirstlight.block.PowerCableBlock;
import com.antaurora.apofirstlight.energy.EnergyCellStoredMode;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * DEVELOPMENT ONLY: {@code /dev fuel station} builds a cut-open demo fuel station just east of the player
 * (docs/models/fuel_station_sump_v1.md, "演示结构"): two underground tanks (gasoline west, diesel east) buried two deep, each
 * with its submersible pump, open pump manhole cover and fill riser + fill cover; the two product lines in the pipe layer to
 * the ends of a dispenser sump; a fuel dispenser on a short island over the sump; power cables from a full, discharging
 * energy cell to both pumps and the sump. The pit is left open (no backfill) so every part is visible. The tanks get
 * 20,000 mB of their fuel each.
 * <p>
 * Frame: x east, z south, y the forecourt layer (the block under the player, raised when the tanks would reach the world's
 * bottom). The area x+1..x+11, y-4..y+4 and z-2..z+12 around the player is cleared first.
 */
final class DevFuelStation {
    private static final int FILL_MB = 20_000;

    private DevFuelStation() {
    }

    static int build(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos feet = player.blockPosition();
        int ground = Math.max(feet.getY() - 1, level.getMinBuildHeight() + 5);
        BlockPos origin = new BlockPos(feet.getX() + 3, ground, feet.getZ());
        Frame f = new Frame(origin);

        // clear the pit and the air above it
        for (int x = -2; x <= 8; x++) for (int y = -4; y <= 4; y++) for (int z = -2; z <= 12; z++)
            level.setBlock(f.at(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);

        // the tanks: axis z, the master (port cell) under the pump at z 6, the fill cell at z 9; tank top at y -2
        tank(level, f.at(0, -4, 6), (UndergroundFuelTankBlock) AflBlocks.UNDERGROUND_FUEL_TANK_GASOLINE.get());
        tank(level, f.at(4, -4, 6), (UndergroundFuelTankBlock) AflBlocks.UNDERGROUND_FUEL_TANK_DIESEL.get());

        // pumps (outlet north toward the dispenser, power port south), open manhole covers over them
        for (int x : new int[]{0, 4}) {
            set(level, f.at(x, -1, 6), AflBlocks.SUBMERSIBLE_FUEL_PUMP.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            set(level, f.at(x, 0, 6), AflBlocks.PUMP_MANHOLE_COVER.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(FuelSumpCoverBlock.OPEN, true));
        }
        // fill covers over the fill risers (a pipe between)
        set(level, f.at(0, 0, 9), AflBlocks.FUEL_FILL_COVER_GASOLINE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        set(level, f.at(4, 0, 9), AflBlocks.FUEL_FILL_COVER_DIESEL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));

        // the dispenser sump (lower A at x 2, B at x 3; pipe layer and forecourt layer) and the dispenser on it, facing north
        ((FuelDispenserSumpBlock) AflBlocks.FUEL_DISPENSER_SUMP.get()).placeCells(level, f.at(2, -1, 0), Direction.NORTH);
        FuelDispenserBlock dispenser = (FuelDispenserBlock) AflBlocks.FUEL_DISPENSER.get();
        for (FuelDispenserBlock.Cell cell : FuelDispenserBlock.Cell.values()) {
            set(level, FuelDispenserBlock.cellPosition(f.at(2, 1, 0), Direction.NORTH, cell), dispenser.defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(FuelDispenserBlock.CELL, cell));
        }
        // the island either side of it, on a concrete strip
        BlockState concrete = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        for (int x : new int[]{0, 1, 4, 5}) set(level, f.at(x, 0, 0), concrete);
        set(level, f.at(0, 1, 0), AflBlocks.FUEL_ISLAND_END.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        set(level, f.at(1, 1, 0), AflBlocks.FUEL_ISLAND_CURB.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        set(level, f.at(4, 1, 0), AflBlocks.FUEL_ISLAND_CURB.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        set(level, f.at(5, 1, 0), AflBlocks.FUEL_ISLAND_END.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));

        // the energy cell at the east end of the cable run, its port (west) on the cable
        set(level, f.at(6, -1, 7), AflBlocks.ENERGY_CELL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        if (level.getBlockEntity(f.at(6, -1, 7)) instanceof EnergyCellBlockEntity cell) {
            CompoundTag charge = new CompoundTag();
            charge.putInt("EnergyStored", MachineBalanceManager.energyCell().capacityFe());
            charge.putInt(EnergyCellStoredMode.MODE_KEY, 1);   // discharge
            cell.load(charge);
            cell.setChanged();
        }

        // pipes: gasoline (x 0 north, then east into the sump's A end), diesel (x 4 north into its B end), the fill risers
        Set<BlockPos> pipes = new LinkedHashSet<>();
        for (int z = 0; z <= 5; z++) {
            pipes.add(f.at(0, -1, z));
            pipes.add(f.at(4, -1, z));
        }
        pipes.add(f.at(1, -1, 0));
        pipes.add(f.at(0, -1, 9));
        pipes.add(f.at(4, -1, 9));
        // cables: the cell along z 7 to both pumps, and up x 2 to the sump's power port
        Set<BlockPos> cables = new LinkedHashSet<>();
        for (int x = 0; x <= 5; x++) cables.add(f.at(x, -1, 7));
        for (int z = 1; z <= 6; z++) cables.add(f.at(2, -1, z));

        BlockState pipe = AflBlocks.FLUID_PIPE.get().defaultBlockState(), cable = AflBlocks.POWER_CABLE.get().defaultBlockState();
        for (BlockPos position : pipes) set(level, position, pipe);
        for (BlockPos position : cables) set(level, position, cable);
        for (BlockPos position : pipes) set(level, position, links(level, position, pipe, pipes, false));
        for (BlockPos position : cables) set(level, position, links(level, position, cable, cables, true));

        context.getSource().sendSuccess(() -> Component.literal("Built the demo fuel station at " + origin.toShortString()
                + " (dispenser at " + f.at(2, 1, 0).toShortString() + ")"), false);
        return 1;
    }

    private record Frame(BlockPos origin) {
        BlockPos at(int x, int y, int z) {
            return origin.offset(x, y, z);
        }
    }

    private static void set(ServerLevel level, BlockPos position, BlockState state) {
        level.setBlock(position, state, Block.UPDATE_ALL);
    }

    private static void tank(ServerLevel level, BlockPos root, UndergroundFuelTankBlock block) {
        Direction.Axis axis = Direction.Axis.Z;
        for (int a = 0; a < 7; a++) for (int c = 0; c < 3; c++) for (int l = 0; l < 3; l++) {
            set(level, UndergroundFuelTankBlock.cellPosition(root, axis, a, c, l), block.defaultBlockState()
                    .setValue(UndergroundFuelTankBlock.AXIS, axis).setValue(UndergroundFuelTankBlock.ALONG, a)
                    .setValue(UndergroundFuelTankBlock.ACROSS, c).setValue(UndergroundFuelTankBlock.LEVEL, l));
        }
        BlockPos master = UndergroundFuelTankBlock.cellPosition(root, axis, UndergroundFuelTankBlock.PORT_ALONG,
                UndergroundFuelTankBlock.PORT_ACROSS, UndergroundFuelTankBlock.PORT_LEVEL);
        if (level.getBlockEntity(master) instanceof UndergroundFuelTankBlockEntity tank) {
            tank.tank().fill(new FluidStack(block.fuel(), FILL_MB), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    /** A run's links: to the other members of the same run, and to the ports (fluid or power) next to it. */
    private static BlockState links(ServerLevel level, BlockPos position, BlockState state, Set<BlockPos> run, boolean power) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = position.relative(direction);
            BlockState neighborState = level.getBlockState(neighbor);
            boolean port = power ? PowerCableBlock.isUtilityPortFace(neighborState, direction.getOpposite())
                    : FluidPipeBlock.isFluidPort(neighborState, direction);
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), run.contains(neighbor) || port);
        }
        return state;
    }
}
