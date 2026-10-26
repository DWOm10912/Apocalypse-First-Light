package com.antaurora.apofirstlight.cabin;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sitting on a wooden chair, AFL's own (user 2026-10-25: "能不能不用原版的乘坐"; docs/dev/block_mesh_v2_b_ingame.md 12):
 * no seat entity, no riding. The server keeps who sits on which chair and tells the player and everyone tracking it. Sat,
 * the player stands still on the chair's floor at the block's middle, its box {@link #HEIGHT} high, its eye at
 * {@link #EYE} (both where vanilla's rider had them), its body the chair's way; the look within {@link #LOOK} of it, the hands the
 * player's own (items, actions, guns: an ordinary player). The movement keys get it up toward where they point, sneak or
 * jump in front (client/skeleton/ChairSitClient); so do the chair going, a teleport, death, leaving.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChairSit {
    /** Sat: the box's height and the eye (blocks over the chair's floor; vanilla's rider, 0.122 under the floor, less that). */
    public static final float HEIGHT = 1.68F, EYE = 1.5F;
    /**
     * Sat, the look stays in front: at most this far (deg) either side of the chair's way, the first person's and the orbit
     * camera's alike (user 2026-10-25: "坐着只能看前面一片，F5也不能往后"; client/skeleton/ChairSitClient).
     */
    public static final float LOOK = 90F;
    /** Moved off the seat further than this (blocks: pushed, teleported), the player is up. */
    private static final double OFF_SEAT = 0.3;

    /** A sitter: the chair's block and the way the chair faces (where the sitter looks). */
    public record Sit(BlockPos chair, Direction facing) {
        /** The seat point: the block's middle on its floor. */
        public Vec3 point() {
            return new Vec3(chair.getX() + 0.5, chair.getY(), chair.getZ() + 0.5);
        }

        public float yaw() {
            return facing.toYRot();
        }
    }

    private static final Map<UUID, Sit> SERVER = new HashMap<>();
    /** The client's sitters by entity id (Sync). */
    private static final Map<Integer, Sit> CLIENT = new HashMap<>();

    private ChairSit() {
    }

    /** Where this player sits, or null (either side). */
    @Nullable
    public static Sit get(@Nullable Entity e) {
        if (!(e instanceof Player p)) return null;
        return p.level().isClientSide ? CLIENT.get(p.getId()) : SERVER.get(p.getUUID());
    }

    public static boolean sitting(@Nullable Entity e) {
        return get(e) != null;
    }

    /** Someone sits on the chair at pos (server). */
    public static boolean occupied(Level level, BlockPos pos) {
        for (Map.Entry<UUID, Sit> e : SERVER.entrySet()) {
            if (!e.getValue().chair.equals(pos)) continue;
            Player p = level.getPlayerByUUID(e.getKey());
            if (p != null) return true;
        }
        return false;
    }

    /** Server: sits the player on the chair at pos, facing its way. */
    static boolean sit(ServerPlayer p, BlockPos pos, Direction facing) {
        if (!p.isAlive() || p.isSpectator() || p.isSleeping() || p.isPassenger() || sitting(p) || occupied(p.level(), pos)) return false;
        Sit s = new Sit(pos.immutable(), facing);
        SERVER.put(p.getUUID(), s);
        Vec3 at = s.point();
        p.stopUsingItem();
        p.setSprinting(false);
        p.setShiftKeyDown(false);
        p.setDeltaMovement(Vec3.ZERO);
        p.connection.teleport(at.x, at.y, at.z, s.yaw(), p.getXRot());
        p.setYHeadRot(s.yaw());
        p.setYBodyRot(s.yaw());
        p.refreshDimensions();
        AflNetwork.chairToTracking(p, new Sync(p.getId(), s));
        return true;
    }

    /**
     * Server: up and off the chair, toward {@code toward} (the way the movement keys pointed) when there is room there,
     * else in front, to a side, behind; on the chair as the last resort. {@code place} false: where it is (teleported,
     * pushed off, dead).
     */
    static void stand(ServerPlayer p, @Nullable Direction toward, boolean place) {
        Sit s = SERVER.remove(p.getUUID());
        if (s == null) return;
        p.refreshDimensions();
        AflNetwork.chairToTracking(p, new Sync(p.getId(), null));
        if (!place) return;
        Vec3 spot = standSpot(p, s, toward);
        p.connection.teleport(spot.x, spot.y, spot.z, p.getYRot(), p.getXRot());
    }

    private static Vec3 standSpot(ServerPlayer p, Sit s, @Nullable Direction toward) {
        Direction ahead = s.facing;
        Direction[] order = toward == null || toward == ahead
                ? new Direction[]{ahead, ahead.getClockWise(), ahead.getCounterClockWise(), ahead.getOpposite()}
                : new Direction[]{toward, ahead, toward.getOpposite() == ahead ? ahead.getClockWise() : toward.getOpposite(), ahead.getOpposite()};
        for (Direction side : order) {
            Vec3 spot = DismountHelper.findSafeDismountLocation(EntityType.PLAYER, p.level(), s.chair.relative(side), true);
            if (spot != null) return spot;
        }
        return new Vec3(s.chair.getX() + 0.5, s.chair.getY() + 1.0, s.chair.getZ() + 0.5);
    }

    // ---------------------------------------------------------------- holding the sitter

    /** Both sides: no knockback or drift before the tick moves the player; the body the chair's way. */
    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        Player p = event.player;
        Sit s = get(p);
        if (s == null) return;
        if (event.phase == TickEvent.Phase.START) {
            p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
            return;
        }
        Vec3 at = s.point();
        if (!p.level().isClientSide && p instanceof ServerPlayer sp) {
            boolean chair = p.level().getBlockState(s.chair).getBlock() instanceof WoodFurniture.Chair;
            if (!chair || !p.isAlive() || p.isPassenger() || p.isSleeping() || p.isSpectator() || p.position().distanceTo(at) > OFF_SEAT) {
                stand(sp, null, chair && p.isAlive() && !p.isPassenger() && p.position().distanceTo(at) <= OFF_SEAT);
                return;
            }
        }
        if (p.position().distanceToSqr(at) > 1e-8 && (!p.level().isClientSide || p.isLocalPlayer())) p.setPos(at);
        p.setYBodyRot(s.yaw());
        p.yBodyRotO = s.yaw();
    }

    /** Sat: the lower box and eye (both sides; refreshed as the player sits and stands). */
    @SubscribeEvent
    public static void size(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player p) || !sitting(p)) return;
        event.setNewSize(EntityDimensions.scalable(event.getNewSize().width, HEIGHT));
        event.setNewEyeHeight(EYE);
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) stand(p, null, false);
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) stand(p, null, false);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) stand(p, null, false);
    }

    /** A sitter coming into a client's view: that it sits. */
    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer) || !(event.getTarget() instanceof ServerPlayer target)) return;
        Sit s = SERVER.get(target.getUUID());
        if (s != null) AflNetwork.chairTo(viewer, new Sync(target.getId(), s));
    }

    /** Client: the level left, every sitter with it. */
    public static void clearClient() {
        CLIENT.clear();
    }

    // ---------------------------------------------------------------- packets

    /** Server to clients: this player sits on {@code sit} (null: is up). */
    public record Sync(int entity, @Nullable Sit sit) {
        static void encode(Sync p, FriendlyByteBuf b) {
            b.writeVarInt(p.entity);
            b.writeBoolean(p.sit != null);
            if (p.sit != null) {
                b.writeBlockPos(p.sit.chair);
                b.writeByte(p.sit.facing.get2DDataValue());
            }
        }

        static Sync decode(FriendlyByteBuf b) {
            int entity = b.readVarInt();
            return new Sync(entity, b.readBoolean() ? new Sit(b.readBlockPos(), Direction.from2DDataValue(b.readByte())) : null);
        }
    }

    /** Client to server: get up, toward a horizontal way (-1: in front). */
    public record Stand(byte toward) {
        static void encode(Stand p, FriendlyByteBuf b) {
            b.writeByte(p.toward);
        }

        static Stand decode(FriendlyByteBuf b) {
            return new Stand(b.readByte());
        }
    }

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, Sync.class, Sync::encode, Sync::decode, ChairSit::handleSync, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Stand.class, Stand::encode, Stand::decode, ChairSit::handleStand, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        return id;
    }

    private static void handleSync(Sync packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> {
            if (packet.sit != null) CLIENT.put(packet.entity, packet.sit);
            else CLIENT.remove(packet.entity);
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.skeleton.ChairSitClient.synced(packet.entity));
        });
        c.setPacketHandled(true);
    }

    private static void handleStand(Stand packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        ServerPlayer p = c.getSender();
        c.enqueueWork(() -> {
            if (p != null) stand(p, packet.toward < 0 ? null : Direction.from2DDataValue(packet.toward), true);
        });
        c.setPacketHandled(true);
    }
}
