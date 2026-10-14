package com.antaurora.apofirstlight.wiring;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The wiring wrench, server side (Building Wiring V1 phase 1, docs/gameplay/building_wiring_v1.md):
 * <ul>
 *   <li>lay: a use on a block puts the selected circuit's wire into it, or into a device's mount cell when the block is a
 *   device (and taps the device). Uses that follow each other within {@link #STROKE_GAP} ticks are one stroke: a cell that
 *   is not next to the last one is reached by a short route ({@link WiringRouter}, at most {@link #GAP_CELLS} cells), so
 *   a quick sweep with the use key held leaves no gaps. Every new cell joins the same circuit's wire around it (also along
 *   a roof slope or stairs) and taps the devices mounted on it; a new end that meets another end of the circuit only
 *   across an edge is joined through the corner cell (BuildingWiring#cornerJoin, one more wire). Survival: one Insulated
 *   Copper Wire per new cell from the inventory, none laid without enough.</li>
 *   <li>cut: an attack on a block removes the selected circuit's node there (survival: the wire back into the inventory, or
 *   dropped at the feet when it is full); on a device it removes the device's tap (no wire, no refund).</li>
 *   <li>select: sneak + scroll steps through circuits 1-4 and the feeder, the pick key takes another circuit from the aimed
 *   cell ({@link #choose}); the choice is kept on the player (not in the item, which stays stackable with other wrenches)
 *   and shown on the action bar. The colour is the circuit's (WiringNode#colourOf).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class WiringTool {
    public static final int STROKE_GAP = 8, GAP_CELLS = 24, GAP_MARGIN = 2, GAP_VISITS = 4000;
    private static final String KEY = "afl_wiring_circuit";

    private static final class Session {
        int circuit = 1;
        BlockPos last;
        int lastCircuit;
        long lastTick;
        ResourceKey<Level> lastLevel;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private WiringTool() {}

    private static Session session(ServerPlayer p) {
        return SESSIONS.computeIfAbsent(p.getUUID(), id -> {
            Session s = new Session();
            int saved = p.getPersistentData().getInt(KEY);
            if (saved >= 1 && saved <= WiringNode.CIRCUITS) s.circuit = saved;
            return s;
        });
    }

    public static int circuit(ServerPlayer p) {
        return session(p).circuit;
    }

    /** The player holds the wrench or the wire in either hand: wiring mode (the client shows the wires). */
    public static boolean holdsWiring(Player p) {
        return holds(p.getMainHandItem()) || holds(p.getOffhandItem());
    }

    private static boolean holds(ItemStack s) {
        return s.is(AflItems.WIRING_WRENCH.get()) || s.is(AflItems.INSULATED_COPPER_WIRE.get());
    }

    public static boolean holdsWrench(Player p) {
        return p.getMainHandItem().is(AflItems.WIRING_WRENCH.get());
    }

    // ------------------------------------------------------------------------------------------------------ select

    /** Steps the selection by delta (wrapping through 1-4 and the feeder). */
    public static void select(ServerPlayer p, int delta) {
        if (!holdsWrench(p) || delta == 0) return;
        choose(p, Math.floorMod(circuit(p) - 1 + Integer.signum(delta), WiringNode.CIRCUITS) + 1);
    }

    /** Selects this circuit (the pick key on a wire of it). */
    public static void choose(ServerPlayer p, int circuit) {
        if (!holdsWrench(p) || circuit < 1 || circuit > WiringNode.CIRCUITS) return;
        Session s = session(p);
        s.circuit = circuit;
        s.last = null;
        p.getPersistentData().putInt(KEY, s.circuit);
        WiringSync.selection(p);
        p.displayClientMessage(circuitName(s.circuit), true);
    }

    /** "第 3 路 · 绿" / "进线 · 黑". */
    public static Component circuitName(int circuit) {
        Component colour = Component.translatable("message.apocalypse_firstlight.wiring.colour." + WiringNode.colourName(WiringNode.colourOf(circuit)));
        return circuit == WiringNode.FEEDER ? Component.translatable("message.apocalypse_firstlight.wiring.feeder", colour)
                : Component.translatable("message.apocalypse_firstlight.wiring.circuit", circuit, colour);
    }

    // --------------------------------------------------------------------------------------------------------- lay

    public static InteractionResult use(ServerPlayer p, BlockPos hit) {
        ServerLevel level = p.serverLevel();
        if (!holdsWrench(p) || !level.isLoaded(hit) || !p.mayBuild() || !level.mayInteract(p, hit)) return InteractionResult.FAIL;
        Session s = session(p);
        int circuit = s.circuit;
        BlockState state = level.getBlockState(hit);
        BlockPos cell = hit.immutable();
        if (BuildingWiring.kind(state) != null) {
            BlockPos mount = BuildingWiring.mount(state, hit);
            if (mount == null) return InteractionResult.FAIL;
            cell = mount.immutable();
        }
        if (!level.isLoaded(cell) || !BuildingWiring.wireable(level, cell)) {
            p.displayClientMessage(Component.translatable("message.apocalypse_firstlight.wiring.no_wiring"), true);
            s.last = null;
            return InteractionResult.FAIL;
        }
        long now = level.getGameTime();
        boolean stroke = s.last != null && s.lastCircuit == circuit && level.dimension() == s.lastLevel && now >= s.lastTick && now - s.lastTick <= STROKE_GAP;
        List<BlockPos> path = List.of(cell);
        if (stroke && s.last.distManhattan(cell) > 1) {
            List<BlockPos> r = WiringRouter.route(level, s.last, cell, circuit, GAP_MARGIN, GAP_VISITS, GAP_CELLS + 1);
            if (r != null) path = r.subList(1, r.size());
        }
        int need = 0;
        for (BlockPos c : path) if (BuildingWiring.node(level, c, circuit) == 0) need++;
        if (need > 0 && !p.isCreative() && wires(p) < need) {
            p.displayClientMessage(Component.translatable("message.apocalypse_firstlight.wiring.no_wire"), true);
            s.last = null;
            return InteractionResult.FAIL;
        }
        int made = 0;
        for (BlockPos c : path) {
            if (BuildingWiring.add(level, c, circuit)) made++;
            BuildingWiring.linkAround(level, c, circuit);
            BuildingWiring.autoTap(level, c, circuit);
        }
        if (made > 0 && !p.isCreative()) take(p, made);
        // an end that meets another end only across an edge (a wall wire and a floor wire): one more metre turns the corner
        for (BlockPos c : path) {
            if (!p.isCreative() && wires(p) < 1) break;
            if (BuildingWiring.cornerJoin(level, c, circuit) != null && !p.isCreative()) take(p, 1);
        }
        s.last = cell;
        s.lastCircuit = circuit;
        s.lastTick = now;
        s.lastLevel = level.dimension();
        return InteractionResult.SUCCESS;
    }

    // --------------------------------------------------------------------------------------------------------- cut

    /** An attack on a block with the wrench (the client never mines with it). */
    public static void cut(ServerPlayer p, BlockPos hit) {
        ServerLevel level = p.serverLevel();
        if (!holdsWrench(p) || !level.isLoaded(hit) || !p.mayBuild() || !level.mayInteract(p, hit) || !p.canReach(hit, 1.0)) return;
        BlockState state = level.getBlockState(hit);
        if (BuildingWiring.kind(state) != null) {
            BuildingWiring.untap(level, hit, state);
            return;
        }
        int n = BuildingWiring.cut(level, hit, session(p).circuit);
        if (n > 0 && !p.isCreative()) give(p, n);
    }

    // ------------------------------------------------------------------------------------------------------- wire

    /** Insulated Copper Wire in the player's inventory (both hands included). */
    public static int wires(Player p) {
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(AflItems.INSULATED_COPPER_WIRE.get())) n += s.getCount();
        }
        return n;
    }

    /** Takes n wires (the off hand first, then the inventory in order). */
    static void take(Player p, int n) {
        Inventory inv = p.getInventory();
        ItemStack off = p.getOffhandItem();
        if (off.is(AflItems.INSULATED_COPPER_WIRE.get())) {
            int k = Math.min(n, off.getCount());
            off.shrink(k);
            n -= k;
        }
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(AflItems.INSULATED_COPPER_WIRE.get())) continue;
            int k = Math.min(n, s.getCount());
            s.shrink(k);
            n -= k;
        }
        inv.setChanged();
    }

    /** Gives n wires back: into the inventory, the rest dropped at the player's feet. */
    static void give(Player p, int n) {
        ItemStack s = new ItemStack(AflItems.INSULATED_COPPER_WIRE.get(), n);
        p.getInventory().add(s);   // what does not fit stays in s
        if (!s.isEmpty()) p.drop(s, false);
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }
}
