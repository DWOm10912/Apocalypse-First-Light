package com.antaurora.apofirstlight.energy;

import com.antaurora.apofirstlight.block.WallOutletBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * A power cord ending in a NEMA 5-15P plug (Power Outlets V1, docs/models/power_outlets_v1.md): the power strips' and the
 * plug-in appliances' (Beverage Cooler, Chest Freezer, Vending Machine, Water Dispenser) way to power. The plug is in a
 * socket of a wall outlet or a power strip ({@link #host()} / {@link #socket()}), in a player's hand (not saved: it falls
 * back beside the device on reload, logout, dimension change, or when the carrier walks too far), or lying beside the
 * device. Strips plug into wall outlets only. Checked every second: the socket gone, its flag cleared, or out of the
 * cord's reach unplugs it. The owner block entity keeps the cord, ticks it on the server, saves and syncs it.
 */
public final class PlugCord {
    /** Block entities with a cord. */
    public interface Owner {
        PlugCord plugCord();
    }

    /**
     * Where the cord leaves the device, world coordinates: {@code exit} and the direction it leaves in ({@code out}). For an
     * appliance that may stand against a wall, also the way along its back: the back plane point behind the exit at plug
     * height ({@code backExit}) and on the floor ({@code backFloor}), and the two floor points just past its back corners
     * ({@code corners}) with the directions the cord then leaves in ({@code sides}). The renderer takes the way behind when
     * the cell behind the exit is solid. An appliance's cord is detachable: {@code inlet} is the face of its IEC C14 inlet,
     * where the renderer seats the cord's C13 connector (pointing along {@code out}); the exit is the connector's tail.
     */
    public record Geometry(Vec3 exit, Vec3 out, @Nullable Vec3 backExit, @Nullable Vec3 backFloor, @Nullable Vec3[] corners, @Nullable Vec3[] sides,
                           @Nullable Vec3 inlet) {
        public static Geometry hardWired(Vec3 exit, Vec3 out) { return new Geometry(exit, out, null, null, null, null, null); }
    }

    /** Cord lengths, blocks (straight line from the exit to the socket). Appliances: user 2026-10-08 asked for a longer cord. */
    public static final double STRIP_LENGTH = 2.5, APPLIANCE_LENGTH = 4.0;
    /** Cord radius, px (1.5 x an 8 mm cord), for the routing points kept above the floor. */
    public static final double RADIUS_PX = 0.096;
    /** The C14 inlet's housing out of the appliance's back, and the C13 connector's length (px; tools/afl-iec-inlet.mjs, build-power-outlets-v1.mjs). */
    public static final double INLET_PROUD = 0.22, CONNECTOR_LENGTH = 1.2;
    private static final String HOST_KEY = "PlugHost", SOCKET_KEY = "PlugSocket", CARRIER_KEY = "PlugCarrier";

    private final BlockEntity owner;
    private final boolean strip;
    private final double length;
    private final Supplier<Geometry> geometry;
    @Nullable private BlockPos host;
    private int socket;
    @Nullable private UUID carrier;
    private int carrierId = -1;
    private long nextCheck;

    public PlugCord(BlockEntity owner, boolean strip, double length, Supplier<Geometry> geometry) {
        this.owner = owner;
        this.strip = strip;
        this.length = length;
        this.geometry = geometry;
    }

    public boolean strip() { return strip; }
    public double length() { return length; }
    public Geometry geometry() { return geometry.get(); }
    @Nullable public BlockPos host() { return host; }
    public int socket() { return socket; }
    public int carrierId() { return carrierId; }
    @Nullable public UUID carrier() { return carrier; }
    public BlockPos ownerPos() { return owner.getBlockPos(); }

    /** What the socket's host gives (nothing while unplugged). */
    public int draw(int fe, boolean simulate) {
        Level level = owner.getLevel();
        return level == null || host == null || fe <= 0 ? 0 : PowerPlugs.draw(level, host, fe, simulate);
    }

    // ---- server: plug, unplug, carry ----

    /** Plugs into the socket; the host's flag is set by the caller (PowerPlugs). */
    public void plugInto(BlockPos hostPos, int socketIndex) {
        host = hostPos.immutable(); socket = socketIndex;
        carrier = null; carrierId = -1;
        nextCheck = 0;
        sync();
    }

    /** Pulls the plug out (the socket's flag is cleared); it then lies beside the device. */
    public void unplug() {
        freeSocket();
        host = null;
        sync();
    }

    public void setCarrier(@Nullable ServerPlayer player) {
        carrier = player == null ? null : player.getUUID();
        carrierId = player == null ? -1 : player.getId();
        sync();
    }

    /** The owner is going away: free the socket and the carrier's hand, without touching the owner's own cell. */
    public void release() {
        if (carrier != null) PowerPlugs.forget(carrier, owner.getBlockPos());
        carrier = null;
        freeSocket();
        host = null;
    }

    private void freeSocket() {
        Level level = owner.getLevel();
        if (level != null && host != null && level.isLoaded(host) && PowerPlugs.used(level, host, socket)) PowerPlugs.setUsed(level, host, socket, false);
    }

    /** Server, every tick. */
    public void serverTick() {
        Level level = owner.getLevel();
        if (level == null || level.isClientSide()) return;
        if (carrier != null && level.getGameTime() % 5 == 0) {
            Player player = level.getPlayerByUUID(carrier);
            if (player == null || player.position().add(0, 1, 0).distanceTo(geometry.get().exit()) > length + 1.5) {
                if (player != null) PowerPlugs.tell(player, "hint.apocalypse_firstlight.plug.dropped");
                PowerPlugs.forget(carrier, owner.getBlockPos());
                setCarrier(null);
            }
        }
        if (level.getGameTime() < nextCheck) return;
        nextCheck = level.getGameTime() + 20;
        if (host != null && level.isLoaded(host)) {
            boolean ok = socket < PowerPlugs.sockets(level, host) && PowerPlugs.used(level, host, socket) && PowerPlugs.accepts(level, host, this)
                    && PowerPlugs.socketPoint(level, host, socket).distanceTo(geometry.get().exit()) <= length + 0.05;
            if (!ok) {
                if (PowerPlugs.sockets(level, host) > socket && PowerPlugs.used(level, host, socket)
                        && PowerPlugs.pluggedInto(level, host, socket) == this) PowerPlugs.setUsed(level, host, socket, false);
                host = null;
                sync();
            }
        }
    }

    /** The owner's render box, grown to where the cord can reach. */
    public AABB renderBounds(AABB base) {
        Vec3 exit = geometry.get().exit();
        double reach = host != null || carrierId >= 0 ? length + 1.5 : 0.8;
        return base.minmax(new AABB(exit, exit).inflate(reach));
    }

    // ---- save / sync (the carrier only while carried, never across a reload) ----

    public void save(CompoundTag tag) {
        if (host != null) { tag.putLong(HOST_KEY, host.subtract(owner.getBlockPos()).asLong()); tag.putInt(SOCKET_KEY, socket); }
        if (carrier != null) tag.putInt(CARRIER_KEY, carrierId);
    }

    /**
     * For an update tag: the cord's state with the carrier always written (-1 when nobody carries the plug). An empty update
     * tag is never sent (ClientboundBlockEntityDataPacket drops it), so an idle strip's "plug dropped" never reached the
     * client and its cord kept following the player (2026-10-08, user video).
     */
    public void writeSync(CompoundTag tag) {
        save(tag);
        tag.putInt(CARRIER_KEY, carrier != null ? carrierId : -1);
    }

    public void load(CompoundTag tag) {
        host = tag.contains(HOST_KEY) ? owner.getBlockPos().offset(BlockPos.of(tag.getLong(HOST_KEY))) : null;
        socket = tag.getInt(SOCKET_KEY);
        carrierId = tag.contains(CARRIER_KEY) ? tag.getInt(CARRIER_KEY) : -1;
    }

    /** The power strip's V1 keys (2026-10-08, before the shared cord). */
    public void loadLegacy(CompoundTag tag, String hostKey, String socketKey) {
        if (!tag.contains(HOST_KEY) && tag.contains(hostKey)) { host = owner.getBlockPos().offset(BlockPos.of(tag.getLong(hostKey))); socket = tag.getInt(socketKey); }
    }

    private void sync() {
        owner.setChanged();
        Level level = owner.getLevel();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(owner.getBlockPos(), owner.getBlockState(), owner.getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ---- geometry helpers (px, the generators' facing-north frame: front -Z, +X the viewer's left) ----

    public static Vec3 at(BlockPos origin, Direction facing, double x, double y, double z) {
        return WallOutletBlock.local(origin, facing, x, y, z);
    }

    public static Vec3 dir(Direction facing, double x, double y, double z) {
        return at(BlockPos.ZERO, facing, x * 16, y * 16, z * 16).subtract(at(BlockPos.ZERO, facing, 0, 0, 0)).normalize();
    }

    /**
     * An appliance's cord (px, the generator's frame): its C14 inlet centred at (x, y) on the back plane {@code backZ}, the
     * connector pointing backward, the cord leaving its tail; the footprint across {@code xMin..xMax}; the floor at
     * {@code floorY} (the origin cell's bottom = 0). Behind a wall the cord drops at the tail and runs along the back.
     */
    public static Geometry appliance(BlockPos origin, Direction facing, double x, double y, double backZ, double xMin, double xMax, double floorY) {
        double r = floorY + RADIUS_PX + 0.02, face = backZ + INLET_PROUD, z = face + CONNECTOR_LENGTH;
        Vec3 exit = at(origin, facing, x, y, z);
        return new Geometry(exit, dir(facing, 0, 0, 1), exit, at(origin, facing, x, r, z),
                new Vec3[]{at(origin, facing, xMax + 0.35, r, z), at(origin, facing, xMin - 0.35, r, z)},
                new Vec3[]{dir(facing, 1, 0, 0), dir(facing, -1, 0, 0)}, at(origin, facing, x, y, face));
    }
}
