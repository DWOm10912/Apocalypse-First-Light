package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.block.FuelCanBlock;
import com.antaurora.apofirstlight.blockentity.FuelCanBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The fuel nozzle put into a fuel opening (2026-10-10, docs/models/fuel_dispenser_v1.md "插枪加油"): a jerry can's spout, a
 * drum's 2" bung (FuelCanBlock#opening), or an open generator fill (FuelPourTarget, the same openings a jerry can pours
 * into). Common to the server (FuelDispenserBlockEntity: what it takes, where the fuel goes) and the client (where the
 * nozzle is drawn, item/FuelNozzleItem and client/NozzleFillView).
 * <p>
 * Every opening is on top of its block: the spout goes in pointing down, tilted {@link #TILT} degrees away from the holder
 * (a real nozzle's bent spout enters at an angle), its tip {@link #DEPTH} inside. The nozzle model's own frame
 * (tools/build-fuel-dispenser-v1.mjs, item frame, block units): the spout straight along -Z with its tip at {@link #SPOUT},
 * the hood up (+Y), the grip at {@link #GRIP}, the hose swivel at {@link #SWIVEL}.
 */
public final class NozzleFill {
    /** From the eyes to the opening (blocks), to put the nozzle in and to keep it in (plus 1). */
    public static final double REACH = 2.5;
    public static final double TILT = 35, DEPTH = 1.5 / 16;
    public static final Vec3 SPOUT = new Vec3(0.5, 0.5625, 0.288), GRIP = new Vec3(0.5, 0.5125, 0.616), SWIVEL = new Vec3(0.5, 0.5, 0.704);

    private NozzleFill() {
    }

    /** The opening of a block the nozzle goes into (world), or null: a fuel container with its cap off, or a pour target that is open now. */
    @Nullable
    public static Vec3 opening(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FuelCanBlock) return state.getValue(FuelCanBlock.OPEN) ? FuelCanBlock.opening(pos, state) : null;   // the cap off
        if (state.getBlock() instanceof FuelPourTarget target && target.takesPour(level, pos, state)) return target.pourOpening(level, pos, state);
        return null;
    }

    /** Where the fuel goes (server), or null. */
    @Nullable
    public static IFluidHandler handler(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FuelCanBlock && level.getBlockEntity(pos) instanceof FuelCanBlockEntity can) return can.tank();
        if (state.getBlock() instanceof FuelPourTarget target) return target.pourHandler(level, pos, state);
        return null;
    }

    /**
     * Why this fuel cannot go in, as a full message key, or null when it can: "...fuel_nozzle.full", "...fuel_nozzle.other_fuel"
     * (a container holding the other fuel), or the pour target's own refusal ("...fuel_can.diesel_only").
     */
    @Nullable
    public static String refusal(ServerLevel level, BlockPos pos, Fluid fuel) {
        IFluidHandler handler = handler(level, pos);
        if (handler == null) return "message.apocalypse_firstlight.fuel_can.wont_take";
        if (handler.fill(new FluidStack(fuel, 1), IFluidHandler.FluidAction.SIMULATE) > 0) return null;
        FluidStack held = handler.getTanks() > 0 ? handler.getFluidInTank(0) : FluidStack.EMPTY;
        if (!held.isEmpty() && held.getAmount() >= handler.getTankCapacity(0)) return "message.apocalypse_firstlight.fuel_nozzle.full";
        if (level.getBlockState(pos).getBlock() instanceof FuelPourTarget target) return "message.apocalypse_firstlight.fuel_can." + target.refusal();
        if (!held.isEmpty() && !held.getFluid().isSame(fuel)) return "message.apocalypse_firstlight.fuel_nozzle.other_fuel";
        return "message.apocalypse_firstlight.fuel_can.wont_take";
    }

    /** How full the opening's tank is, 0..1 (the fill sound rises with it). */
    public static float share(IFluidHandler handler) {
        if (handler.getTanks() == 0 || handler.getTankCapacity(0) <= 0) return 0;
        return Mth.clamp(handler.getFluidInTank(0).getAmount() / (float) handler.getTankCapacity(0), 0, 1);
    }

    /** The horizontal heading (degrees, the game's yaw) from the holder's eyes to the opening: the spout tilts along it. */
    public static float heading(Vec3 eye, Vec3 opening) {
        return (float) Math.toDegrees(Math.atan2(-(opening.x - eye.x), opening.z - eye.z));
    }

    /** The spout's direction into the opening: down, tilted away from the holder (heading) by TILT. */
    public static Vec3 axis(float heading) {
        double h = Math.toRadians(heading), t = Math.toRadians(TILT);
        return new Vec3(-Math.sin(h) * Math.sin(t), -Math.cos(t), Math.cos(h) * Math.sin(t));
    }

    /**
     * The nozzle put in: the world point of a nozzle model point (item frame, block units) for an opening and heading. The
     * model's -Z runs along the spout's axis, its +Y as near world up as the axis allows, the spout tip DEPTH inside.
     */
    public static Vec3[] frame(Vec3 opening, float heading) {
        Vec3 z = axis(heading).scale(-1);
        Vec3 up = new Vec3(0, 1, 0), y = up.subtract(z.scale(up.dot(z))).normalize(), x = y.cross(z);
        Vec3 tip = opening.add(z.scale(-DEPTH));
        Vec3 origin = tip.subtract(x.scale(SPOUT.x)).subtract(y.scale(SPOUT.y)).subtract(z.scale(SPOUT.z));
        return new Vec3[]{origin, x, y, z};
    }
}
