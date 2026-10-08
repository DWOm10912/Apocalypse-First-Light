package com.antaurora.apofirstlight.energy;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.PowerStripBlock;
import com.antaurora.apofirstlight.block.WallOutletBlock;
import com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity;
import com.antaurora.apofirstlight.blockentity.PowerStripBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Plugs and sockets (Power Outlets V1, docs/models/power_outlets_v1.md). Socket hosts: the wall outlet (2 sockets, flags
 * in its block state, live over the building's hidden wiring: in a Distribution Panel's building, main on, the outlet
 * circuit on, something in the buffer) and the power strips (3 / 6, flags in the block entity, live while switched on
 * and plugged into a live outlet). Cords ({@link PlugCord}) belong to the strips and the plug-in appliances; a strip's
 * cord only goes into a wall outlet. The player carries a plug by hand (sneak + empty hand on the device), then
 * right-clicks a socket with an empty hand; the plug goes into the aimed socket, or the other free one.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PowerPlugs {
    private record Carried(ResourceKey<Level> level, BlockPos owner) {}
    private static final Map<UUID, Carried> CARRIED = new HashMap<>();

    private PowerPlugs() {}

    // ---- power ----

    @Nullable
    private static DistributionPanelBlockEntity panelFor(Level level, BlockPos outlet) {
        BlockPos panel = BuildingPowerZone.panelServing(level, outlet);
        if (panel == null || !level.isLoaded(panel)) return null;
        return level.getBlockEntity(panel) instanceof DistributionPanelBlockEntity p ? p : null;
    }

    /** The wall outlet at {@code outlet} has power (its building's panel feeds the outlet circuit). */
    public static boolean outletLive(Level level, BlockPos outlet) {
        DistributionPanelBlockEntity panel = panelFor(level, outlet);
        return panel != null && panel.outletsLive();
    }

    /** Draws up to {@code fe} through the socket host at {@code host}: a wall outlet (its panel) or a power strip (its switch and plug). */
    public static int draw(Level level, BlockPos host, int fe, boolean simulate) {
        BlockState state = level.getBlockState(host);
        if (state.getBlock() instanceof WallOutletBlock) {
            DistributionPanelBlockEntity panel = panelFor(level, host);
            return panel == null ? 0 : panel.drawOutlets(fe, simulate);
        }
        if (state.getBlock() instanceof PowerStripBlock && level.getBlockEntity(host) instanceof PowerStripBlockEntity strip) return strip.draw(fe, simulate);
        return 0;
    }

    // ---- socket hosts ----

    public static int sockets(Level level, BlockPos host) {
        BlockState state = level.getBlockState(host);
        if (state.getBlock() instanceof WallOutletBlock) return 2;
        return state.getBlock() instanceof PowerStripBlock strip ? strip.outlets() : 0;
    }

    public static boolean used(Level level, BlockPos host, int socket) {
        BlockState state = level.getBlockState(host);
        if (state.getBlock() instanceof WallOutletBlock) return WallOutletBlock.used(state, socket);
        return level.getBlockEntity(host) instanceof PowerStripBlockEntity strip && strip.socketUsed(socket);
    }

    public static void setUsed(Level level, BlockPos host, int socket, boolean used) {
        BlockState state = level.getBlockState(host);
        if (state.getBlock() instanceof WallOutletBlock) level.setBlock(host, WallOutletBlock.withUsed(state, socket, used), 3);
        else if (level.getBlockEntity(host) instanceof PowerStripBlockEntity strip) strip.setSocketUsed(socket, used);
    }

    /** The centre of the socket's face, world coordinates. */
    public static Vec3 socketPoint(Level level, BlockPos host, int socket) {
        BlockState state = level.getBlockState(host);
        if (state.getBlock() instanceof PowerStripBlock strip) return strip.socketPoint(host, state, socket);
        return state.getBlock() instanceof WallOutletBlock ? WallOutletBlock.socket(host, state, socket) : Vec3.atCenterOf(host);
    }

    /** Out of the socket (the plug's axis). */
    public static Vec3 socketAxis(Level level, BlockPos host, int socket) {
        BlockState state = level.getBlockState(host);
        if (state.getBlock() instanceof WallOutletBlock) return Vec3.atLowerCornerOf(state.getValue(WallOutletBlock.FACING).getNormal());
        return new Vec3(0, 1, 0);
    }

    /** Toward the socket's slots, away from its ground hole (the plug's up). */
    public static Vec3 socketUp(Level level, BlockPos host, int socket) {
        BlockState state = level.getBlockState(host);
        return state.getBlock() instanceof PowerStripBlock strip ? strip.socketUp(state, socket) : new Vec3(0, 1, 0);
    }

    /** The socket aimed at: on an outlet the upper or lower one by height, on a strip the nearest one. */
    public static int aimedSocket(Level level, BlockPos host, BlockHitResult hit) {
        BlockState state = level.getBlockState(host);
        if (state.getBlock() instanceof PowerStripBlock strip) {
            int best = 0; double bestD = Double.MAX_VALUE;
            for (int i = 0; i < strip.outlets(); i++) { double d = strip.socketPoint(host, state, i).distanceToSqr(hit.getLocation()); if (d < bestD) { bestD = d; best = i; } }
            return best;
        }
        return hit.getLocation().y - host.getY() >= WallOutletBlock.CENTRE_Y / 16.0 ? 0 : 1;
    }

    /** A strip's cord goes into wall outlets only. */
    public static boolean accepts(Level level, BlockPos host, PlugCord cord) {
        BlockState state = level.getBlockState(host);
        if (state.getBlock() instanceof WallOutletBlock) return true;
        return state.getBlock() instanceof PowerStripBlock && !cord.strip() && !host.equals(cord.ownerPos());
    }

    @Nullable
    public static PlugCord owner(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PlugCord.Owner o ? o.plugCord() : null;
    }

    /** The cord whose plug is in this socket (searched within the longest cord's reach). */
    @Nullable
    public static PlugCord pluggedInto(Level level, BlockPos host, int socket) {
        int r = (int) Math.ceil(PlugCord.APPLIANCE_LENGTH) + 2;
        for (BlockPos p : BlockPos.betweenClosed(host.offset(-r, -r, -r), host.offset(r, r, r)))
            if (level.getBlockEntity(p) instanceof PlugCord.Owner o && host.equals(o.plugCord().host()) && o.plugCord().socket() == socket) return o.plugCord();
        return null;
    }

    // ---- carrying ----

    @Nullable
    public static BlockPos carried(Player player) {
        Carried c = CARRIED.get(player.getUUID());
        return c != null && c.level() == player.level().dimension() ? c.owner() : null;
    }

    /** Takes the cord's plug into the player's hand; a plug already in hand goes back beside its own device first. */
    public static void carry(ServerPlayer player, PlugCord cord) {
        dropCarried(player);
        CARRIED.put(player.getUUID(), new Carried(player.level().dimension(), cord.ownerPos()));
        cord.setCarrier(player);
    }

    /** Forgets the record (the cord has already let go of the plug, or is gone). */
    public static void forget(UUID player, BlockPos owner) {
        Carried c = CARRIED.get(player);
        if (c != null && c.owner().equals(owner)) CARRIED.remove(player);
    }

    /** The plug in the player's hand goes back beside its device. */
    public static void dropCarried(Player player) {
        Carried c = CARRIED.remove(player.getUUID());
        if (c == null || player.getServer() == null) return;
        Level level = player.getServer().getLevel(c.level());
        PlugCord cord = level == null ? null : owner(level, c.owner());
        if (cord != null) cord.setCarrier(null);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        dropCarried(event.getEntity());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        Carried c = CARRIED.remove(event.getEntity().getUUID());
        if (c == null || event.getEntity().getServer() == null) return;
        Level level = event.getEntity().getServer().getLevel(c.level());
        PlugCord cord = level == null ? null : owner(level, c.owner());
        if (cord != null) cord.setCarrier(null);
    }

    // ---- interactions (server side; callers check the empty hand) ----

    /** Sneak + empty hand on a device: its plug into the hand, out of its socket into the hand, or back beside it. */
    public static InteractionResult useDevice(Level level, BlockPos ownerPos, ServerPlayer player) {
        PlugCord cord = owner(level, ownerPos);
        if (cord == null) return InteractionResult.PASS;
        if (ownerPos.equals(carried(player))) dropCarried(player);
        else {
            if (cord.host() != null) cord.unplug();
            carry(player, cord);
        }
        level.playSound(null, ownerPos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.4F, 0.7F);
        return InteractionResult.CONSUME;
    }

    /** With a plug in hand, a socket host: into the aimed socket, or the other free one, if the cord reaches. */
    public static InteractionResult plugCarried(Level level, BlockPos host, BlockHitResult hit, ServerPlayer player) {
        BlockPos from = carried(player);
        PlugCord cord = from == null ? null : owner(level, from);
        if (cord == null) { if (from != null) CARRIED.remove(player.getUUID()); return InteractionResult.PASS; }
        if (!accepts(level, host, cord)) { tell(player, "hint.apocalypse_firstlight.plug.wrong_socket"); return InteractionResult.CONSUME; }
        int n = sockets(level, host), aimed = aimedSocket(level, host, hit), socket = -1;
        for (int k = 0; k < n && socket < 0; k++) {
            int i = (aimed + k) % n;
            if (!used(level, host, i)) socket = i;
            else if (pluggedInto(level, host, i) == null) { setUsed(level, host, i, false); socket = i; }   // stale flag
        }
        if (socket < 0) { tell(player, "hint.apocalypse_firstlight.plug.full"); return InteractionResult.CONSUME; }
        if (socketPoint(level, host, socket).distanceTo(cord.geometry().exit()) > cord.length()) { tell(player, "hint.apocalypse_firstlight.plug.too_far"); return InteractionResult.CONSUME; }
        CARRIED.remove(player.getUUID());
        cord.plugInto(host, socket);
        setUsed(level, host, socket, true);
        level.playSound(null, host, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, 0.8F);
        return InteractionResult.CONSUME;
    }

    /** Right-click on a wall outlet: plug the carried plug in, or (empty hand) pull out the one in the aimed socket. */
    public static InteractionResult useOutlet(Level level, BlockPos pos, BlockState state, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer server) || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (carried(player) != null) return plugCarried(level, pos, hit, server);
        int aimed = aimedSocket(level, pos, hit);
        if (!WallOutletBlock.used(state, aimed)) return InteractionResult.PASS;
        PlugCord cord = pluggedInto(level, pos, aimed);
        if (cord != null) {
            cord.unplug();
            carry(server, cord);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.5F, 0.7F);
        } else level.setBlock(pos, WallOutletBlock.withUsed(state, aimed, false), 3);   // stale flag
        return InteractionResult.CONSUME;
    }

    public static void tell(Player player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }
}
