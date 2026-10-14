package com.antaurora.apofirstlight.wiring.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflKeyHintPanel;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.wiring.BuildingWiring;
import com.antaurora.apofirstlight.wiring.WiringNode;
import com.antaurora.apofirstlight.wiring.WiringPackets;
import com.antaurora.apofirstlight.wiring.WiringTool;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The wiring wrench's input and key panel (Building Wiring V1, docs/gameplay/building_wiring_v1.md):
 * <ul>
 *   <li>attack never mines with the wrench: a click (or the held attack sweeping over blocks) sends the aimed block to the
 *   server as a cut, once per block;</li>
 *   <li>sneak + scroll steps the circuit instead of the hotbar; the pick key (middle click) takes another circuit that has
 *   wire in the aimed cell (the view shows only the selected circuit, so this is how a wire of another one is found);</li>
 *   <li>top right, the circuit with its metres, the actions, and the pick row while the aimed cell holds another circuit
 *   (the shared key hint panel); aimed at a Distribution Panel, its supply: 范围供电 · 暗线不生效 (LEGACY_AREA), or the
 *   circuits joined to another panel (phase 2).</li>
 * </ul>
 * Laying is the wrench's own use (WiringWrenchItem), repeated by the held use key.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class WiringClient {
    private static final AflKeyHintPanel PANEL = new AflKeyHintPanel();
    private static BlockPos lastCut;

    private WiringClient() {}

    @SubscribeEvent
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getHand() != InteractionHand.MAIN_HAND || mc.player == null || !WiringView.holdsWrench(mc.player)) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;   // entities: an ordinary hit
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
        BlockPos pos = hit.getBlockPos().immutable();
        if (pos.equals(lastCut)) return;
        lastCut = pos;
        mc.player.swing(InteractionHand.MAIN_HAND);
        AflNetwork.wiringToServer(new WiringPackets.Cut(pos));
    }

    /** The cell the wrench works on: the aimed block, or the mount cell of an aimed device (as WiringTool#use). */
    @Nullable
    static BlockPos aimedCell(Minecraft mc) {
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        BlockState s = mc.level.getBlockState(pos);
        return BuildingWiring.kind(s) != null ? BuildingWiring.mount(s, pos) : pos;
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

    @SubscribeEvent
    public static void hud(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.options.keyAttack.isDown()) lastCut = null;
        boolean visible = mc.player != null && mc.screen == null && !mc.options.hideGui && WiringView.holdsWrench(mc.player);
        List<AflKeyHintPanel.Row> rows = new ArrayList<>();
        if (mc.player != null) {
            int circuit = WiringView.circuit();
            rows.add(new AflKeyHintPanel.Row(null, null, Component.translatable("message.apocalypse_firstlight.wiring.length",
                    WiringTool.circuitName(circuit), WiringView.length(circuit))));
            rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(mc.options.keyUse), Component.translatable("hint.apocalypse_firstlight.wiring.lay")));
            rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(mc.options.keyAttack), Component.translatable("hint.apocalypse_firstlight.wiring.cut")));
            rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(mc.options.keyShift), AflKeyHint.MOUSE_SCROLL, Component.translatable("hint.apocalypse_firstlight.wiring.select")));
            if (visible) panelRows(mc, rows);
            int pick = visible ? pickable(mc) : 0;
            if (pick > 0) rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(mc.options.keyPickItem), pick == WiringNode.FEEDER
                    ? Component.translatable("hint.apocalypse_firstlight.wiring.pick_feeder") : Component.translatable("hint.apocalypse_firstlight.wiring.pick", pick)));
        }
        PANEL.render(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(), rows, visible);
    }
}
