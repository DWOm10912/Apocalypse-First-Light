package com.antaurora.apofirstlight.client.map;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.FirstPersonBodyConfig;
import com.antaurora.apofirstlight.client.ui.AflKeyHintPanel;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.map.MapStyle;
import com.antaurora.apofirstlight.map.MapTowns;
import com.antaurora.apofirstlight.worldgen.network.town.TownBuildings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The minimap (AFL Map V1, docs/ui/afl_map_v1_plan.md 6): top right, a rounded square that turns with the view (user
 * 2026-10-20; north up in the settings), the player's arrow in the middle, N on the frame. Carrying a map it shows the
 * map's record; without one only the live ground within MapClient.RADIUS, faded to fog past it (user 2026-10-20). It
 * zooms out with speed (driving) and back in when slow. Under it: the block position, the biome (its map colour and
 * name) and, in a town, the town's name as on its signs. The driving key hints move down below it.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MinimapHud {
    private MinimapHud() {}

    static final float MARGIN = 8, RADIUS = 6, TEXT = 0.75F;
    /** Metres across at walking pace, and the most it zooms out to when driving fast. */
    static final double SPAN = 120, SPAN_MAX = 360;
    private static double span = SPAN;
    private static long lastNanos;

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerBelow(VanillaGuiOverlay.POTION_ICONS.id(), "minimap", OVERLAY);
        event.registerAbove(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(), "compass", CompassHud.OVERLAY);
    }

    static final IGuiOverlay OVERLAY = (gui, g, partialTick, width, height) -> {
        AflKeyHintPanel.topInset = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.options.renderDebug
                || !FirstPersonBodyConfig.minimap() || mc.level.dimension() != Level.OVERWORLD) return;
        draw(g, mc, width, height, partialTick);
    };

    private static void draw(GuiGraphics g, Minecraft mc, int width, int height, float partialTick) {
        float s = Math.max(64, Math.min(110, Math.round(height * 0.2F)));
        float x = width - MARGIN - s, y = MARGIN, cx = x + s / 2, cy = y + s / 2;
        Entity moving = mc.player.getVehicle() != null ? mc.player.getVehicle() : mc.player;
        double speed = Math.hypot(moving.getX() - moving.xo, moving.getZ() - moving.zo) * 20;
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.25, (now - lastNanos) / 1e9);
        lastNanos = now;
        double target = Math.min(SPAN_MAX, SPAN + Math.max(0, speed - 6) * 9);
        span += (target - span) * (1 - Math.exp(-dt * (target > span ? 1.5 : 0.6)));
        double mPerGui = span / s;
        net.minecraft.world.phys.Vec3 pos = moving.getPosition(partialTick);
        double px = pos.x, pz = pos.z;
        float yaw = mc.gameRenderer.getMainCamera().getYRot();
        boolean turn = FirstPersonBodyConfig.minimapTurn() == FirstPersonBodyConfig.MinimapTurn.VIEW;
        float rot = turn ? 180 - yaw : 0;
        boolean carrying = MapClient.carrying();
        MapClient.Record record = carrying ? MapClient.RECORD : MapClient.LIVE;
        MapTiles tiles = record.tiles();
        tiles.frame();

        AflUiShapes.box(g, x, y, s, s, RADIUS, MapStyle.FOG, 0.92F, 0, 0, AflUiStyle.SHADOW_ALPHA, 0);
        float live = carrying ? 0 : (float) (MapClient.RADIUS / mPerGui);
        MapProgram.Look look = new MapProgram.Look(x, y, x + s, y + s, RADIUS, true, cx, cy, live, (float) (10 / mPerGui), 1);
        MapDraw.tiles(g, tiles, px, pz, mPerGui, cx, cy, rot, s * 0.75F, look);

        // the fuel stops in view, upright
        double rad = Math.toRadians(rot), cos = Math.cos(rad), sin = Math.sin(rad);
        for (MapTowns.Town t : MapClient.towns().towns)
            for (TownBuildings.Site site : t.sites()) {
                if (!TownBuildings.FUEL_STOP.equals(site.kind())) continue;
                double dx = ((site.x0() + site.x1() + 1) / 2.0 - px) / mPerGui, dz = ((site.z0() + site.z1() + 1) / 2.0 - pz) / mPerGui;
                if (!carrying && Math.hypot(dx, dz) * mPerGui > MapClient.RADIUS) continue;
                if (carrying && MapClient.RECORD.chunk(Math.floorDiv(site.x0() + 32, 16), Math.floorDiv(site.z0() + 32, 16)) == null) continue;
                float ix = (float) (cx + dx * cos - dz * sin), iy = (float) (cy + dx * sin + dz * cos);
                if (ix < x + 5 || ix > x + s - 5 || iy < y + 5 || iy > y + s - 5) continue;
                MapDraw.icon(g, MapDraw.FUEL, ix, iy, 10);
            }

        AflUiShapes.box(g, x, y, s, s, RADIUS, 0, 0, AflUiStyle.LINE, 0.45F, 0, 0);
        // N where north is on the frame
        double nx = Math.sin(rad), ny = -Math.cos(rad), k = (s / 2.0 - 1) / Math.max(Math.abs(nx), Math.abs(ny));
        float ncx = (float) (cx + nx * k), ncy = (float) (cy + ny * k);
        MapDraw.disc(g, ncx, ncy, 4.5F, AflUiStyle.ACCENT, 1);
        Font font = mc.font;
        text(g, font, Component.literal("N"), ncx - font.width("N") * TEXT / 2, ncy - 3.4F, 0x15181C);
        MapDraw.arrow(g, cx, cy, turn ? 0 : yaw + 180, 5.5F);

        // under it: position, biome, town
        BlockPos at = mc.player.blockPosition();
        Holder<Biome> biome = mc.level.getBiome(at);
        String id = biome.unwrapKey().map(b -> b.location().toString()).orElse("minecraft:plains");
        int idx = MapStyle.index(id, biome.value().getGrassColor(at.getX(), at.getZ()));
        Component biomeName = Component.translatable("biome." + id.replace(':', '.'));
        MapTowns.Town town = MapClient.towns().at(mc.player.getX(), mc.player.getZ());
        float line = font.lineHeight * TEXT, top = y + s + 3, ph = 4 + line * (town != null ? 3 : 2) + 2 * (town != null ? 2 : 1);
        AflUiShapes.box(g, x, top, s, ph, AflUiStyle.RADIUS_LABEL, AflUiStyle.BACK, AflUiStyle.BACK_ALPHA, AflUiStyle.LINE, AflUiStyle.LINE_ALPHA, 0, 0);
        float ty = top + 2.5F;
        text(g, font, Component.literal(at.getX() + "  " + at.getY() + "  " + at.getZ()), x + 4, ty, AflUiStyle.TEXT);
        ty += line + 2;
        AflUiShapes.fill(g, x + 4, ty, line - 1, line - 1, 1, MapStyle.mapColour(idx), 1);
        text(g, font, biomeName, x + 4 + line + 2, ty, AflUiStyle.TEXT);
        if (town != null) {
            ty += line + 2;
            String name = com.antaurora.apofirstlight.worldgen.network.road.RoadNames.town(town.id());
            if (name != null) text(g, font, Component.literal(name), x + 4, ty, AflUiStyle.TEXT_DIM);
        }
        AflKeyHintPanel.topInset = top + ph + 2 - MARGIN;
    }

    static void text(GuiGraphics g, Font font, Component c, float x, float y, int rgb) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(TEXT, TEXT, 1);
        g.drawString(font, c, 0, 0, AflUiStyle.argb(rgb, 1), false);
        g.pose().popPose();
    }
}
