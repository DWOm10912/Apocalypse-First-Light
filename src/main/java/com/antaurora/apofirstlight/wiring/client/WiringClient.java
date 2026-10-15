package com.antaurora.apofirstlight.wiring.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflKeyHintPanel;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.wiring.BuildingWiring;
import com.antaurora.apofirstlight.wiring.WiringNode;
import com.antaurora.apofirstlight.wiring.WiringPackets;
import com.antaurora.apofirstlight.wiring.WiringSupply;
import com.antaurora.apofirstlight.wiring.WiringTool;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The wiring wrench's input and key panel (Building Wiring V1, docs/gameplay/building_wiring_v1.md):
 * <ul>
 *   <li>attack never mines with the wrench: a click (or the held attack sweeping over blocks) sends the aimed block to the
 *   server as a cut, once per block; with a route it only cancels the route;</li>
 *   <li>sneak + scroll steps the circuit instead of the hotbar; the pick key (middle click) takes another circuit that has
 *   wire in the aimed cell; sneak + pick toggles "show all" (phase 5);</li>
 *   <li>route (phase 5): a fresh sneak + use sends a route mark (the start, then waypoints), a fresh use while a route is
 *   set a route use (preview, or confirm on the previewed end); the held use key's repeats are swallowed then, so holding
 *   never confirms;</li>
 *   <li>groups (phase 5): a fresh use on a light or switch that already has a tap asks for its group picker instead of
 *   laying; on one without a tap the use lays (and taps) as usual and, when the press stayed on it, the picker is asked
 *   for on release (a sweep that moves on never asks);</li>
 *   <li>top right, the shared key hint panel: the circuit with its metres (and · 全部), the actions for the state (normal,
 *   start, preview), the pick row while the aimed cell holds another circuit; aimed at a Distribution Panel, its supply:
 *   范围供电 · 暗线不生效 (LEGACY_AREA), or the circuits joined to another panel (phase 2).</li>
 * </ul>
 * Laying is the wrench's own use (WiringWrenchItem), repeated by the held use key.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class WiringClient {
    private static final AflKeyHintPanel PANEL = new AflKeyHintPanel();
    private static BlockPos lastCut;
    /** The use key's press: seen, sent as a route or picker click (its repeats swallowed), the device it may open the picker for. */
    private static boolean useHeld, useTaken, attackTaken;
    @Nullable private static BlockPos pressDevice;

    private WiringClient() {}

    @SubscribeEvent
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getHand() != InteractionHand.MAIN_HAND || mc.player == null || !WiringView.holdsWrench(mc.player)) return;
        boolean routing = WiringView.routeStatus() != WiringTool.ROUTE_NONE;
        if (event.isAttack() && (routing || attackTaken)) {   // anywhere, also at the sky: the attack only cancels the route (and cuts nothing until let go)
            event.setCanceled(true);
            event.setSwingHand(false);
            if (!attackTaken) AflNetwork.wiringToServer(new WiringPackets.RouteAction(WiringTool.ROUTE_CANCEL, BlockPos.ZERO));
            attackTaken = true;
            return;
        }
        BlockPos aimed = mc.hitResult instanceof BlockHitResult h && h.getType() == HitResult.Type.BLOCK ? h.getBlockPos().immutable() : null;
        if (event.isUseItem()) {
            use(mc, event, aimed, routing);
            return;
        }
        if (event.isPickBlock() && mc.player.isShiftKeyDown()) {   // aimed at anything
            event.setCanceled(true);
            event.setSwingHand(false);
            WiringView.toggleShowAll();
            return;
        }
        if (aimed == null) return;   // entities: an ordinary hit
        BlockPos pos = aimed;
        if (event.isPickBlock()) {   // never the block into the hotbar in place of the wrench
            event.setCanceled(true);
            event.setSwingHand(false);
            int pick = pickable(mc);
            if (pick > 0) AflNetwork.wiringToServer(new WiringPackets.Choose(pick));
            return;
        }
        if (!event.isAttack()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (pos.equals(lastCut)) return;
        lastCut = pos;
        mc.player.swing(InteractionHand.MAIN_HAND);
        AflNetwork.wiringToServer(new WiringPackets.Cut(pos));
    }

    /** The use key on the aimed block (null: nothing or an entity aimed). */
    private static void use(Minecraft mc, InputEvent.InteractionKeyMappingTriggered event, @Nullable BlockPos pos, boolean routing) {
        boolean fresh = !useHeld;
        useHeld = true;
        if (!fresh || pos == null) {
            if (useTaken || routing) {   // a route or picker click: the held key repeats nothing
                event.setCanceled(true);
                event.setSwingHand(false);
            } else if (pressDevice != null && !pressDevice.equals(pos)) pressDevice = null;   // a sweep: no picker
            return;
        }
        int action = mc.player.isShiftKeyDown() ? WiringTool.ROUTE_MARK : routing ? WiringTool.ROUTE_USE : -1;
        if (action >= 0) {
            take(mc, event);
            AflNetwork.wiringToServer(new WiringPackets.RouteAction(action, pos));
            return;
        }
        if (!groupable(mc.level.getBlockState(pos))) return;
        if (tapped(mc, pos)) {
            take(mc, event);
            AflNetwork.wiringToServer(new WiringPackets.GroupsOpen(pos));
        } else pressDevice = pos;   // lays and taps; the picker on release if the press stays on it
    }

    private static void take(Minecraft mc, InputEvent.InteractionKeyMappingTriggered event) {
        event.setCanceled(true);
        event.setSwingHand(false);
        mc.player.swing(InteractionHand.MAIN_HAND);
        useTaken = true;
    }

    /** A light or a wall switch: the devices with light groups. */
    static boolean groupable(BlockState s) {
        return BuildingWiring.kind(s) == BuildingWiring.Kind.LOAD && WiringSupply.role(s) != WiringSupply.Role.OTHER;
    }

    /** The device at pos has a tap from circuit 1-4, as this client knows the wires. */
    static boolean tapped(Minecraft mc, BlockPos pos) {
        return tapCircuit(mc.level.getBlockState(pos), pos) > 0;
    }

    /** The circuit (1-5) whose wire taps the device at pos, preferring the selected one; 0 if none. */
    static int tapCircuit(BlockState s, BlockPos pos) {
        BlockPos m = BuildingWiring.mount(s, pos);
        Direction toward = m == null ? null : Direction.fromDelta(pos.getX() - m.getX(), pos.getY() - m.getY(), pos.getZ() - m.getZ());
        if (toward == null) return 0;
        int selected = WiringView.circuit();
        if (WiringNode.tapped(WiringView.node(m, selected), toward)) return selected;
        for (int c = 1; c <= WiringNode.CIRCUITS; c++) if (WiringNode.tapped(WiringView.node(m, c), toward)) return c;
        return 0;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!mc.options.keyAttack.isDown()) attackTaken = false;
        if (mc.options.keyUse.isDown()) return;
        // the use key is up: a press that stayed on a light or switch it just wired asks for its picker
        if (useHeld && !useTaken && pressDevice != null && mc.screen == null && WiringView.holdsWrench(mc.player))
            AflNetwork.wiringToServer(new WiringPackets.GroupsOpen(pressDevice));
        useHeld = useTaken = false;
        pressDevice = null;
    }

    /** The cell the wrench works on: the aimed block, or the mount cell of an aimed device (as WiringTool#use). */
    @Nullable
    static BlockPos aimedCell(Minecraft mc) {
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        BlockState s = mc.level.getBlockState(pos);
        return BuildingWiring.kind(s) != null ? BuildingWiring.mount(s, pos) : pos;
    }

    /** The aimed block, if it is a light or switch. */
    @Nullable
    private static BlockPos aimedGroupable(Minecraft mc) {
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        return groupable(mc.level.getBlockState(hit.getBlockPos())) ? hit.getBlockPos() : null;
    }

    /** The circuit the pick key takes from the aimed cell: the next one there after the selected (wrapping), 0 if none other. */
    static int pickable(Minecraft mc) {
        BlockPos cell = aimedCell(mc);
        if (cell == null) return 0;
        int[] there = WiringView.circuitsAt(cell);
        int selected = WiringView.circuit();
        for (int c : there) if (c > selected) return c;
        for (int c : there) if (c != selected) return c;
        return 0;
    }

    /** Aimed at a Distribution Panel: LEGACY_AREA ignores its wires; a WIRED one's circuits joined to another panel. */
    private static void panelRows(Minecraft mc, List<AflKeyHintPanel.Row> rows) {
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof DistributionPanelBlockEntity panel)) return;
        if (!panel.wired()) { rows.add(new AflKeyHintPanel.Row(null, null, Component.translatable("message.apocalypse_firstlight.wiring.legacy_panel"))); return; }
        int conflict = panel.conflictMask();
        for (int c = 1; c <= WiringNode.BREAKERS; c++)
            if ((conflict & 1 << c) != 0) rows.add(new AflKeyHintPanel.Row(null, null, Component.translatable("message.apocalypse_firstlight.wiring.conflict", c)));
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || !mc.player.isShiftKeyDown() || !WiringView.holdsWrench(mc.player) || event.getScrollDelta() == 0) return;
        event.setCanceled(true);
        AflKeyHint.scrolled();
        AflNetwork.wiringToServer(new WiringPackets.Select(event.getScrollDelta() < 0 ? 1 : -1));   // down = next, like the hotbar
    }

    private static Component hint(String key, Object... args) {
        return Component.translatable("hint.apocalypse_firstlight.wiring." + key, args);
    }

    private static Component message(String key, Object... args) {
        return Component.translatable("message.apocalypse_firstlight.wiring." + key, args);
    }

    @SubscribeEvent
    public static void hud(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.options.keyAttack.isDown()) lastCut = null;
        boolean visible = mc.player != null && mc.screen == null && !mc.options.hideGui && WiringView.holdsWrench(mc.player);
        List<AflKeyHintPanel.Row> rows = new ArrayList<>();
        if (mc.player != null) {
            AflKeyHint use = AflKeyHint.of(mc.options.keyUse), attack = AflKeyHint.of(mc.options.keyAttack), sneak = AflKeyHint.of(mc.options.keyShift);
            int circuit = WiringView.circuit(), status = WiringView.routeStatus();
            if (status == WiringTool.ROUTE_STARTED) {
                rows.add(new AflKeyHintPanel.Row(null, null, message("route_start", WiringTool.circuitName(circuit))));
                rows.add(new AflKeyHintPanel.Row(use, hint("preview")));
                rows.add(new AflKeyHintPanel.Row(attack, hint("cancel")));
            } else if (status == WiringTool.ROUTE_PREVIEW) {
                rows.add(new AflKeyHintPanel.Row(null, null, message("route_preview", WiringView.routeTotal(), WiringView.routeFresh())));
                boolean atEnd = visible && WiringView.routeEnd() != null && WiringView.routeEnd().equals(aimedCell(mc));
                rows.add(new AflKeyHintPanel.Row(use, hint(atEnd ? "confirm" : "preview")));
                rows.add(new AflKeyHintPanel.Row(sneak, use, hint("waypoint")));
                rows.add(new AflKeyHintPanel.Row(attack, hint("cancel")));
            } else {
                Component length = message("length", WiringTool.circuitName(circuit), WiringView.length(circuit));
                rows.add(new AflKeyHintPanel.Row(null, null, WiringView.showAll() ? message("all", length) : length));
                BlockPos device = visible ? aimedGroupable(mc) : null;
                rows.add(new AflKeyHintPanel.Row(use, hint(device != null && tapped(mc, device) ? "group" : "lay")));
                rows.add(new AflKeyHintPanel.Row(sneak, use, hint("start")));
                rows.add(new AflKeyHintPanel.Row(attack, hint("cut")));
                rows.add(new AflKeyHintPanel.Row(sneak, AflKeyHint.MOUSE_SCROLL, hint("select")));
                if (visible) panelRows(mc, rows);
                int pick = visible ? pickable(mc) : 0;
                AflKeyHint middle = AflKeyHint.of(mc.options.keyPickItem);
                if (pick > 0) rows.add(new AflKeyHintPanel.Row(middle, pick == WiringNode.FEEDER ? hint("pick_feeder") : hint("pick", pick)));
                rows.add(new AflKeyHintPanel.Row(sneak, middle, hint(WiringView.showAll() ? "show_one" : "show_all")));
            }
        }
        PANEL.render(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(), rows, visible);
    }
}
