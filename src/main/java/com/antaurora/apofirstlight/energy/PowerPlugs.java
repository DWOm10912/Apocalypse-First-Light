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
 * Plugs and outlets (Power Outlets V1, docs/models/power_outlets_v1.md). A wall outlet is live while it lies in a
 * Distribution Panel's building (the hidden wiring), that panel's main is on and its outlet circuit is not switched off;
 * what is plugged in draws from the panel's buffer through {@link #draw}. A power strip's cord ends in a plug the player
 * carries by hand (sneak + right-click the strip, then right-click an outlet); the cord reaches {@link #CORD} blocks from
 * the strip's cord exit. Strips plug into wall outlets only (no daisy chains). Server side; the carried plug is also
 * recorded on the strip (synced, the renderer draws the cord to the carrier's hand).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PowerPlugs {
    /** Cord length of a strip, blocks (straight line from the cord exit to the socket). */
    public static final double CORD = 2.5;
    /** A carried plug drops back beside its strip when the carrier walks this far from the cord exit. */
    public static final double CARRY_LIMIT = CORD + 1.5;

    private record Carried(ResourceKey<Level> level, BlockPos strip) {}
    private static final Map<UUID, Carried> CARRIED = new HashMap<>();

    private PowerPlugs() {}

    // ---- the outlet's power: the panel serving its cell ----

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

    /**
     * Draws up to {@code fe} from what feeds the socket host at {@code host} (a wall outlet, or a power strip: through its
     * switch and its own plug). Returns what was given. For the plug-in appliances of Building Power V1 step 2b.
     */
    public static int draw(Level level, BlockPos host, int fe, boolean simulate) {
        BlockState state = level.getBlockState(host);
        if (state.getBlock() instanceof WallOutletBlock) {
            DistributionPanelBlockEntity panel = panelFor(level, host);
            return panel == null ? 0 : panel.drawOutlets(fe, simulate);
        }
        if (state.getBlock() instanceof PowerStripBlock && level.getBlockEntity(host) instanceof PowerStripBlockEntity strip) return strip.draw(fe, simulate);
        return 0;
    }

    // ---- carrying a strip's plug ----

    @Nullable
    public static BlockPos carried(Player player) {
        Carried c = CARRIED.get(player.getUUID());
        return c != null && c.level() == player.level().dimension() ? c.strip() : null;
    }

    /** Takes the strip's plug into the player's hand; a plug already in hand goes back beside its own strip first. */
    public static void carry(ServerPlayer player, PowerStripBlockEntity strip) {
        dropCarried(player);
        CARRIED.put(player.getUUID(), new Carried(player.level().dimension(), strip.getBlockPos()));
        strip.setCarrier(player);
    }

    /** Forgets the record (the strip has already let go of the plug, or is gone). */
    public static void forget(UUID player, BlockPos strip) {
        Carried c = CARRIED.get(player);
        if (c != null && c.strip().equals(strip)) CARRIED.remove(player);
    }

    /** The plug in the player's hand goes back beside its strip. */
    public static void dropCarried(Player player) {
        Carried c = CARRIED.remove(player.getUUID());
        if (c == null || player.getServer() == null) return;
        Level level = player.getServer().getLevel(c.level());
        if (level != null && level.isLoaded(c.strip()) && level.getBlockEntity(c.strip()) instanceof PowerStripBlockEntity strip) strip.setCarrier(null);
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
        if (level != null && level.isLoaded(c.strip()) && level.getBlockEntity(c.strip()) instanceof PowerStripBlockEntity strip) strip.setCarrier(null);
    }

    // ---- the outlet's right-click ----

    /** Which socket the player aims at: 0 upper, 1 lower (by the hit's height against the plate centre). */
    public static int aimedSocket(BlockPos pos, BlockHitResult hit) {
        return hit.getLocation().y - pos.getY() >= WallOutletBlock.CENTRE_Y / 16.0 ? 0 : 1;
    }

    /**
     * Right-click on a wall outlet: with a strip's plug in hand, plugs it into the aimed socket (or the other one when that
     * is taken), if the cord reaches; with nothing in hand on a taken socket, pulls that plug out into the hand.
     */
    public static InteractionResult useOutlet(Level level, BlockPos pos, BlockState state, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer server)) return InteractionResult.PASS;
        int aimed = aimedSocket(pos, hit);
        BlockPos carried = carried(player);
        if (carried != null && level.getBlockEntity(carried) instanceof PowerStripBlockEntity strip) {
            int socket = WallOutletBlock.used(state, aimed) ? 1 - aimed : aimed;
            if (WallOutletBlock.used(state, socket)) { tell(player, "hint.apocalypse_firstlight.power_strip.outlet_full"); return InteractionResult.CONSUME; }
            Vec3 at = WallOutletBlock.socket(pos, state, socket);
            if (at.distanceTo(strip.cordExit()) > CORD) { tell(player, "hint.apocalypse_firstlight.power_strip.too_far"); return InteractionResult.CONSUME; }
            CARRIED.remove(player.getUUID());
            strip.plugInto(pos, socket);
            level.setBlock(pos, WallOutletBlock.withUsed(state, socket, true), 3);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, 0.8F);
            return InteractionResult.CONSUME;
        }
        if (WallOutletBlock.used(state, aimed)) {
            PowerStripBlockEntity strip = pluggedInto(level, pos, aimed);
            if (strip != null) {
                strip.unplug();
                carry(server, strip);
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.5F, 0.7F);
            } else level.setBlock(pos, WallOutletBlock.withUsed(state, aimed, false), 3);   // stale flag
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** The strip whose plug is in this socket (searched within the cord's reach). */
    @Nullable
    public static PowerStripBlockEntity pluggedInto(Level level, BlockPos outlet, int socket) {
        int r = (int) Math.ceil(CORD) + 1;
        for (BlockPos p : BlockPos.betweenClosed(outlet.offset(-r, -r, -r), outlet.offset(r, r, r)))
            if (level.getBlockEntity(p) instanceof PowerStripBlockEntity strip && outlet.equals(strip.outlet()) && strip.socket() == socket) return strip;
        return null;
    }

    public static void tell(Player player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }
}
