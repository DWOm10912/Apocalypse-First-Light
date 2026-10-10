package com.antaurora.apofirstlight.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A block with sockets of its own that plugs go into (Power Outlets V1, docs/models/power_outlets_v1.md), besides the
 * wall outlet and the power strips that {@link PowerPlugs} knows by type: 2026-10-09 the portable diesel generator's two
 * NEMA 5-15R duplexes (docs/machines/portable_diesel_generator_v1.md), the same socket as the wall outlet's (the game has
 * one plug). PowerPlugs asks the block first; everything else about cords (carrying, reach, the renderer) is unchanged.
 */
public interface PlugSocketHost {
    int sockets(Level level, BlockPos pos, BlockState state);

    boolean socketUsed(Level level, BlockPos pos, int socket);

    void setSocketUsed(Level level, BlockPos pos, int socket, boolean used);

    /** The centre of the socket's face, world. */
    Vec3 socketPoint(BlockPos pos, BlockState state, int socket);

    /** Out of the socket (the plug's axis). */
    Vec3 socketAxis(BlockState state, int socket);

    /** Toward the socket's slots, away from its ground hole (the plug's up). */
    Vec3 socketUp(BlockState state, int socket);

    /** The socket nearest the hit point. */
    int aimedSocket(BlockPos pos, BlockState state, Vec3 hit);

    /** Whether this cord's plug may go in (a strip's cord, an appliance's). */
    boolean acceptsPlug(Level level, BlockPos pos, PlugCord cord);

    /** Draws up to fe through the socket host (what its sockets give this tick). */
    int draw(Level level, BlockPos pos, int fe, boolean simulate);

    /** Whether its sockets have power now (a strip plugged in here is live). */
    boolean live(Level level, BlockPos pos);
}
