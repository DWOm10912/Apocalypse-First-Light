package com.antaurora.apofirstlight.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A block a jerry can pours into besides the fuel fill cover (2026-10-09, the diesel generator's fill box,
 * docs/machines/diesel_standby_generator_v1.md): item/FuelCanItem asks it whether it takes a pour now and where the fuel
 * goes, the client streams (client/FuelCanPourJets) and the pour view (client/JerryCanPourView) aim at its opening.
 */
public interface FuelPourTarget {
    /** Whether a can aimed at this cell starts / keeps pouring (the fill opening open). */
    boolean takesPour(BlockGetter level, BlockPos pos, BlockState state);

    /** The fill opening (world): the stream falls into it and the pour range is measured to it. */
    Vec3 pourOpening(BlockGetter level, BlockPos pos, BlockState state);

    /** Where the fuel goes (server), or null. */
    @Nullable
    IFluidHandler pourHandler(ServerLevel level, BlockPos pos, BlockState state);

    /** The message key (message.apocalypse_firstlight.fuel_can.*) when the handler refuses the fuel itself. */
    default String refusal() {
        return "wont_take";
    }
}
