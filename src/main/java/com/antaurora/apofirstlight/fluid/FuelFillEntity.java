package com.antaurora.apofirstlight.fluid;

import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * An entity with a fuel fill opening (2026-10-16, the T15 pickup's filler, docs/vehicles/first_pickup_v0_design.md
 * section 8.2): a jerry can pours into it and a dispenser nozzle goes into it as into a block's opening ({@link FuelOpening}).
 */
public interface FuelFillEntity {
    /** Whether the opening is open now (a can starts / keeps pouring, a nozzle goes in / stays in). */
    boolean fuelOpen();

    /** The opening (world): the stream falls into it, the nozzle goes into it, reach is measured to it. */
    Vec3 fuelOpening();

    /** Where the fuel goes (server), or null. */
    @Nullable
    IFluidHandler fuelHandler();

    /** The message key (message.apocalypse_firstlight.fuel_can.*) when the handler refuses the fuel itself. */
    default String fuelRefusal() {
        return "wont_take";
    }
}
