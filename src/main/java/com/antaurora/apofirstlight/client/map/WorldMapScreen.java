package com.antaurora.apofirstlight.client.map;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.map.MapGround;
import com.antaurora.apofirstlight.map.MapStyle;
import com.antaurora.apofirstlight.map.MapTowns;
import com.antaurora.apofirstlight.worldgen.network.road.CrossSection;
import com.antaurora.apofirstlight.worldgen.network.road.RoadDesign;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNames;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import com.antaurora.apofirstlight.worldgen.network.town.TownBuildings;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The world map (AFL Map V1, docs/ui/afl_map_v1_plan.md 4, 7): the carried map's record, north up, over the fog of what
 * was never seen. It opens by using the map in hand (AflMapItem, #unfold): the folded sheet comes up from below, its cover
 * turns open like a book's, and the open sheet grows to the screen while the map comes up on it (user 2026-10-21); closing
 * plays that back. Drag to move, the wheel zooms about the cursor, the jump key (Space) goes back to the player, right
 * click or Esc folds it away. Top bar: the player's position, biome and town; the cursor shows the place under it (position and biome)
 * or, over a town, the town's card: its name as on the signs and, under it, its name in the game language when that is
 * different (user 2026-10-20). Names drawn on the map are the signs' English. Opens only with a map (MapKeys).
 */
public final class WorldMapScreen extends Screen {
    static final String LANG = "gui.apocalypse_firstlight.map.";
    /** Metres per real pixel, nearest and farthest. */
    static final double NEAR = 0.25, FAR = 16;
    private double cx, cz, mpp = 1.0;
    private boolean dragging;
    private final Map<Integer, Integer> legend = new HashMap<>();
    private int legendIn;
    static final ResourceLocation COVER = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/map/sheet_cover.png"),
            INSIDE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/map/sheet_inside.png");
    /** Seconds to unfold and to fold away. */
    static final double UNFOLD = 0.8, FOLD = 0.5;
    /** 0 folded below the screen .. 1 the world map; the phases: up from below, the cover turning, the sheet growing. */
    private double open;
    private boolean folding;
    private long lastNanos;
    private static boolean filtered;

    /** Unfold the map (AflMapItem, used in hand). */
    public static void unfold() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        mc.setScreen(new WorldMapScreen());
        rustle();
    }

    static void rustle() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 0.9F, 0.8F));
    }

    private void fold() {
        if (folding) return;
        folding = true;
        rustle();
    }

    private boolean ready() { return open >= 1 && !folding; }

    public WorldMapScreen() {
        super(Component.translatable(LANG + "title"));
    }

    @Override
    protected void init() {
        if (minecraft != null && minecraft.player != null && cx == 0 && cz == 0) home();
    }

    private void home() {
        cx = minecraft.player.getX();
        cz = minecraft.player.getZ();
    }

    private double mPerGui() { return mpp * minecraft.getWindow().getGuiScale(); }

    double worldX(double sx) { return cx + (sx - width / 2.0) * mPerGui(); }

    double worldZ(double sy) { return cz + (sy - height / 2.0) * mPerGui(); }

    float screenX(double x) { return (float) (width / 2.0 + (x - cx) / mPerGui()); }

    float screenY(double z) { return (float) (height / 2.0 + (z - cz) / mPerGui()); }

    static boolean known(double x, double z) {
        return MapClient.RECORD.chunk(Math.floorDiv((int) Math.floor(x), 16), Math.floorDiv((int) Math.floor(z), 16)) != null;
    }

    static int sample(double x, double z) {
        int gx = (int) Math.floor(x / MapGround.CELL), gz = (int) Math.floor(z / MapGround.CELL);
        int[] c = MapClient.RECORD.chunk(Math.floorDiv(gx, MapGround.CELLS), Math.floorDiv(gz, MapGround.CELLS));
        return c == null ? 0 : c[Math.floorMod(gz, MapGround.CELLS) * MapGround.CELLS + Math.floorMod(gx, MapGround.CELLS)];
    }

    @Override
    public void tick() {
        if (!MapClient.carrying()) fold();
    }

    @Override
    public void onClose() { fold(); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (folding) {
            open -= dt / FOLD;
            if (open <= 0) { minecraft.setScreen(null); return; }
        } else open = Math.min(1, open + dt / UNFOLD);
        double mpg = mPerGui();
        if (open < 1) { sheet(g, mpg, partialTick); return; }
        map(g, mpg, partialTick, 0, 0, width, height, 0);
        topBar(g);
        if (--legendIn <= 0) { legendIn = 10; countLegend(); }
        legend(g);
        scaleBar(g, mpg);
        keys(g);
        hover(g, mouseX, mouseY);
    }

    /** The map itself (fog, grid, tiles, names, the player) within (x0, y0)-(x1, y1), corners rounded by radius. */
    private void map(GuiGraphics g, double mpg, float partialTick, float x0, float y0, float x1, float y1, float radius) {
        boolean clip = x0 > 0 || y0 > 0 || x1 < width || y1 < height;
        if (clip) g.enableScissor((int) Math.floor(x0), (int) Math.floor(y0), (int) Math.ceil(x1), (int) Math.ceil(y1));
        g.fill(0, 0, width, height, AflUiStyle.argb(MapStyle.FOG, 1));
        grid(g, mpg);
        MapTiles tiles = MapClient.RECORD.tiles();
        tiles.frame();
        MapProgram.Look look = new MapProgram.Look(x0, y0, x1, y1, radius, false, 0, 0, 0, 1, 1);
        MapDraw.tiles(g, tiles, cx, cz, mpg, width / 2F, height / 2F, 0, (float) Math.hypot(width, height) / 2 + 4, look);
        labels(g);
        var player = minecraft.player;
        MapDraw.arrow(g, screenX(player.getX(partialTick)), screenY(player.getZ(partialTick)), minecraft.gameRenderer.getMainCamera().getYRot() + 180, 6);
        if (clip) g.disableScissor();
    }

    static double clamp01(double t) { return Math.max(0, Math.min(1, t)); }

    static double smooth(double t) { t = clamp01(t); return t * t * (3 - 2 * t); }

    static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    /**
     * The sheet while it unfolds or folds: open 0..0.25 the folded sheet comes up from below the screen (the hand lifting
     * it), 0.25..0.65 its cover turns open about its left edge like a book's page, in perspective, 0.65..1 the open sheet
     * grows to the screen while the map comes up on it and the paper fades.
     */
    private void sheet(GuiGraphics g, double mpg, float partialTick) {
        double up = clamp01(open / 0.25), turn = clamp01((open - 0.25) / 0.4), grow = clamp01((open - 0.65) / 0.35);
        float w = width, h = height;
        double fh = h * 0.62, fw = fh * 5 / 7;
        g.fill(0, 0, width, height, AflUiStyle.argb(0x000000, (float) (0.5 * smooth(open / 0.5))));
        if (grow > 0) {
            double e = smooth(grow);
            float x0 = (float) lerp(w / 2 - fw, 0, e), x1 = (float) lerp(w / 2 + fw, w, e);
            float y0 = (float) lerp(h / 2 - fh / 2, 0, e), y1 = (float) lerp(h / 2 + fh / 2, h, e);
            map(g, mpg, partialTick, x0, y0, x1, y1, (float) lerp(3, 0, e));
            float paper = (float) (1 - smooth((grow - 0.1) / 0.75));
            if (paper > 0) panel(g, INSIDE, new float[]{x0, y0, x0, y1, x1, y1, x1, y0}, 0, 1, 1, paper);
            return;
        }
        double rise = 1 - Math.pow(1 - up, 3);
        double cy = lerp(h + fh * 0.6, h / 2, rise);
        double th = Math.PI * smooth(turn);
        double hinge = w / 2 - fw / 2 * (1 - th / Math.PI);
        float top = (float) (cy - fh / 2), bottom = (float) (cy + fh / 2);
        // a soft shadow under the sheet, the inside's right half lying open under the cover
        float sx0 = (float) Math.min(hinge, hinge + fw * Math.cos(th)), sx1 = (float) (hinge + fw);
        AflUiShapes.box(g, sx0, top, sx1 - sx0, bottom - top, 2, 0x000000, 0, 0, 0, 0.45F, 0);
        panel(g, INSIDE, new float[]{(float) hinge, top, (float) hinge, bottom, (float) (hinge + fw), bottom, (float) (hinge + fw), top}, 0.5F, 1, 1, 1);
        // the cover turning about the hinge: its front until it stands up, then the inside's left half; strips in perspective
        boolean front = th < Math.PI / 2;
        double focal = fh * 2.4;
        int strips = 16;
        float shade = (float) (0.72 + 0.28 * Math.abs(Math.cos(th)));
        for (int i = 0; i < strips; i++) {
            double d0 = fw * i / strips, d1 = fw * (i + 1) / strips;
            float[] a = edge(hinge, cy, fh, d0, th, focal, w / 2.0), b = edge(hinge, cy, fh, d1, th, focal, w / 2.0);
            float u0 = front ? (float) i / strips : 0.5F - 0.5F * i / strips, u1 = front ? (float) (i + 1) / strips : 0.5F - 0.5F * (i + 1) / strips;
            panel(g, front ? COVER : INSIDE, new float[]{a[0], a[1], a[0], a[2], b[0], b[2], b[0], b[1]}, u0, u1, shade, 1);
        }
    }

    /** A line across the turning cover d from the hinge: {x, top, bottom} on the screen (perspective about (cx, cy)). */
    private static float[] edge(double hinge, double cy, double fh, double d, double th, double focal, double cx) {
        double x = hinge + d * Math.cos(th), z = d * Math.sin(th), k = focal / (focal - z);
        return new float[]{(float) (cx + (x - cx) * k), (float) (cy - fh / 2 * k), (float) (cy + fh / 2 * k)};
    }

    /**
     * A textured quad: corners {x, y} top left, bottom left, bottom right, top right; u from u0 (left) to u1 (right), the
     * full height of the texture; darkened to shade, faded to alpha.
     */
    private void panel(GuiGraphics g, ResourceLocation tex, float[] q, float u0, float u1, float shade, float alpha) {
        g.flush();
        if (!filtered) {
            minecraft.getTextureManager().getTexture(COVER).setFilter(true, false);
            minecraft.getTextureManager().getTexture(INSIDE).setFilter(true, false);
            filtered = true;
        }
        AbstractTexture t = minecraft.getTextureManager().getTexture(tex);
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, t.getId());
        int c = Math.round(shade * 255), a = Math.round(alpha * 255);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.vertex(m, q[0], q[1], 0).uv(u0, 0).color(c, c, c, a).endVertex();
        b.vertex(m, q[2], q[3], 0).uv(u0, 1).color(c, c, c, a).endVertex();
        b.vertex(m, q[4], q[5], 0).uv(u1, 1).color(c, c, c, a).endVertex();
        b.vertex(m, q[6], q[7], 0).uv(u1, 0).color(c, c, c, a).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    /** A faint world grid on the fog: 64 m, or 512 m / 4096 m when the lines would crowd. */
    private void grid(GuiGraphics g, double mpg) {
        int step = 64;
        while (step / mpg < 12 && step < 65536) step *= 8;
        int colour = AflUiStyle.argb(MapStyle.FOG_GRID, 1);
        double x0 = worldX(0), x1 = worldX(width), z0 = worldZ(0), z1 = worldZ(height);
        for (double x = Math.ceil(x0 / step) * step; x <= x1; x += step) {
            int sx = Math.round(screenX(x));
            g.fill(sx, 0, sx + 1, height, colour);
        }
        for (double z = Math.ceil(z0 / step) * step; z <= z1; z += step) {
            int sy = Math.round(screenY(z));
            g.fill(0, sy, width, sy + 1, colour);
        }
    }

    /** Town names, street names (zoomed in), route shields, fuel stops: only where the map knows the place. */
    private void labels(GuiGraphics g) {
        MapTowns towns = MapClient.towns();
        boolean streets = mpp <= 0.8;
        for (MapTowns.Town t : towns.towns) {
            if (screenX(t.x1()) < 0 || screenX(t.x0()) > width || screenY(t.z1()) < 0 || screenY(t.z0()) > height) continue;
            if (streets) {
                java.util.Set<String> named = new java.util.HashSet<>();
                for (MapTowns.Street st : t.streets()) {
                    double len = st.length();
                    if (len / mPerGui() < font.width(st.name()) * 0.8 + 20) continue;
                    double mx = (st.ax() + st.bx()) / 2, mz = (st.az() + st.bz()) / 2;
                    if (!known(mx, mz) || !named.add(st.name() + Math.round(mx / 200) + ":" + Math.round(mz / 200))) continue;
                    double ang = Math.atan2(st.bz() - st.az(), st.bx() - st.ax());
                    if (ang > Math.PI / 2) ang -= Math.PI; else if (ang < -Math.PI / 2) ang += Math.PI;
                    g.pose().pushPose();
                    g.pose().translate(screenX(mx), screenY(mz), 0);
                    g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) ang));
                    g.pose().scale(0.75F, 0.75F, 1);
                    g.drawString(font, st.name(), -font.width(st.name()) / 2, -4, AflUiStyle.argb(0x5B5650, 1), false);
                    g.pose().popPose();
                }
            }
            for (TownBuildings.Site site : t.sites()) {
                if (!TownBuildings.FUEL_STOP.equals(site.kind())) continue;
                double x = (site.x0() + site.x1() + 1) / 2.0, z = (site.z0() + site.z1() + 1) / 2.0;
                if (known(x, z)) MapDraw.icon(g, MapDraw.FUEL, screenX(x), screenY(z), mpp <= 1 ? 14 : 10);
            }
            if (known(t.labelX(), t.labelZ()) && mpp <= 6) {
                String name = RoadNames.town(t.id());
                if (name == null) continue;
                float sx = screenX(t.labelX()), sy = screenY(t.labelZ());
                g.pose().pushPose();
                g.pose().translate(sx, sy, 0);
                g.pose().scale(1.5F, 1.5F, 1);
                // a halo round the letters, drawn once as the glowing signs draw theirs (eight translucent copies of the
                // text over each other showed their overlaps as stripes, user 2026-10-21)
                font.drawInBatch8xOutline(Component.literal(name).getVisualOrderText(), -font.width(name) / 2F, -4, 0xFF2B2A27, 0xFFF4F1EA,
                        g.pose().last().pose(), g.bufferSource(), net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
                g.flush();
                g.pose().popPose();
            }
        }
        RoadWorks works = MapClient.works();
        if (works == null) return;
        double every = Math.max(600, 220 * mPerGui());
        for (RoadDesign d : works.roads()) {
            if (d.section != CrossSection.R4 && d.section != CrossSection.R2) continue;
            RoadNames.Route route = RoadNames.route(d.id);
            if (route == null) continue;
            for (double s = every / 2; s < d.length; s += every) {
                double[] p = d.point(s, 0);
                float sx = screenX(p[0]), sy = screenY(p[1]);
                if (sx < 0 || sy < 0 || sx > width || sy > height || !known(p[0], p[1])) continue;
                shield(g, route.label(), sx, sy - 9);
            }
        }
    }

    private void shield(GuiGraphics g, String text, float x, float y) {
        float w = font.width(text) * 0.75F + 7, h = 9;
        AflUiShapes.box(g, x - w / 2, y - h / 2, w, h, 2.5F, 0x1F4E8C, 1, 0xFFFFFF, 0.9F, 0, 0);
        MinimapHud.text(g, font, Component.literal(text), x - font.width(text) * 0.75F / 2, y - 3, 0xFFFFFF);
    }

    private void topBar(GuiGraphics g) {
        float x = 8, y = 6, w = width - 16, h = 18;
        AflUiDraw.panel(g, x, y, w, h, 1);
        AflUiDraw.text(g, font, title, x + 8, y + 5, AflUiStyle.TEXT, 1);
        var player = minecraft.player;
        BlockPos at = player.blockPosition();
        Holder<Biome> biome = minecraft.level.getBiome(at);
        String id = biome.unwrapKey().map(b -> b.location().toString()).orElse("minecraft:plains");
        int idx = MapStyle.index(id, biome.value().getGrassColor(at.getX(), at.getZ()));
        Component pos = Component.literal("X " + at.getX() + "   Y " + at.getY() + "   Z " + at.getZ());
        Component bname = Component.translatable("biome." + id.replace(':', '.'));
        float total = font.width(pos) + 14 + 10 + font.width(bname), px = width / 2F - total / 2;
        AflUiDraw.text(g, font, pos, px, y + 5, AflUiStyle.TEXT, 1);
        px += font.width(pos) + 14;
        AflUiShapes.fill(g, px, y + 5, 8, 8, 1.5F, MapStyle.mapColour(idx), 1);
        AflUiDraw.text(g, font, bname, px + 11, y + 5, AflUiStyle.TEXT, 1);
        MapTowns.Town town = MapClient.towns().at(player.getX(), player.getZ());
        String name = town == null ? null : RoadNames.town(town.id());
        if (name != null) {
            Component c = Component.translatable(LANG + "in_town", name);
            AflUiDraw.text(g, font, c, x + w - 8 - font.width(c), y + 5, AflUiStyle.TEXT_DIM, 1);
        }
    }

    /** The biomes in view, most first, sampled from the record over a grid of the screen. */
    private void countLegend() {
        legend.clear();
        for (int j = 0; j < 18; j++)
            for (int i = 0; i < 28; i++) {
                int s = sample(worldX((i + 0.5) * width / 28.0), worldZ((j + 0.5) * height / 18.0));
                if (s == 0 || MapGround.water(s)) continue;
                legend.merge(MapGround.biome(s), 1, Integer::sum);
            }
    }

    private void legend(GuiGraphics g) {
        List<Map.Entry<Integer, Integer>> es = new ArrayList<>(legend.entrySet());
        es.sort((a, b) -> b.getValue() - a.getValue());
        int rows = Math.min(6, es.size());
        float x = 8, y = 30, w = 104, row = 11, h = 8 + (rows > 0 ? 12 + rows * row + 6 : 0) + 12 + 5 * row + 4;
        AflUiDraw.panel(g, x, y, w, h, 1);
        float ty = y + 6;
        if (rows > 0) {
            MinimapHud.text(g, font, Component.translatable(LANG + "legend.biomes"), x + 7, ty, AflUiStyle.TEXT_DIM);
            ty += 12;
            for (int i = 0; i < rows; i++) {
                int b = es.get(i).getKey();
                AflUiShapes.fill(g, x + 7, ty, 7, 7, 1.5F, MapStyle.mapColour(b), 1);
                String rid = MapStyle.registryId(b);
                Component n = rid == null ? Component.literal("?") : Component.translatable("biome." + rid.replace(':', '.'));
                MinimapHud.text(g, font, n, x + 18, ty + 0.5F, AflUiStyle.TEXT);
                ty += row;
            }
            ty += 6;
        }
        MinimapHud.text(g, font, Component.translatable(LANG + "legend.roads"), x + 7, ty, AflUiStyle.TEXT_DIM);
        ty += 12;
        int[][] sw = {{MapStyle.HWY, MapStyle.HWY_EDGE}, {MapStyle.ROAD, MapStyle.ROAD_EDGE}, {MapStyle.DRIVE, MapStyle.DRIVE_EDGE}, {MapStyle.BUILDING, MapStyle.BUILDING_EDGE}, {-1, -1}};
        String[] keys = {"highway", "road", "drive", "building", "fuel"};
        for (int i = 0; i < keys.length; i++) {
            if (sw[i][0] < 0) MapDraw.icon(g, MapDraw.FUEL, x + 10.5F, ty + 3.5F, 9);
            else AflUiShapes.box(g, x + 7, ty + 0.5F, 8, 6, 1, sw[i][0], 1, sw[i][1], 1, 0, 0);
            MinimapHud.text(g, font, Component.translatable(LANG + "legend." + keys[i]), x + 18, ty + 0.5F, AflUiStyle.TEXT);
            ty += row;
        }
    }

    private void scaleBar(GuiGraphics g, double mpg) {
        int[] nice = {10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000};
        int metres = nice[0];
        for (int n : nice) if (n / mpg <= 90) metres = n;
        float len = (float) (metres / mpg), x = width - 8 - len - 46, y = height - 22;
        AflUiDraw.panel(g, x - 6, y - 6, len + 52, 16, 1);
        AflUiShapes.fill(g, x, y + 1, len, 1.5F, 0.5F, AflUiStyle.TEXT, 1);
        AflUiShapes.fill(g, x, y - 2, 1.2F, 4, 0.5F, AflUiStyle.TEXT, 1);
        AflUiShapes.fill(g, x + len - 1.2F, y - 2, 1.2F, 4, 0.5F, AflUiStyle.TEXT, 1);
        String t = metres >= 1000 ? (metres / 1000) + " km" : metres + " m";
        AflUiDraw.text(g, font, Component.literal(t), x + len + 6, y - 2, AflUiStyle.TEXT, 1);
    }

    private void keys(GuiGraphics g) {
        long now = System.nanoTime();
        Object[][] rows = {
                {AflKeyHint.MOUSE_RIGHT, "close"}, {AflKeyHint.MOUSE_SCROLL, "zoom"}, {AflKeyHint.MOUSE_LEFT, "move"},
                {AflKeyHint.of(minecraft.options.keyJump), "home"}};
        float w = 8;
        for (Object[] r : rows) w += ((AflKeyHint) r[0]).width(font) + 4 + font.width(I18n.get(LANG + "key." + r[1])) + 12;
        float x = 8, y = height - 26;
        AflUiDraw.panel(g, x, y, w, 18, 1);
        float kx = x + 8;
        for (Object[] r : rows) {
            AflKeyHint k = (AflKeyHint) r[0];
            k.draw(g, font, kx, y + 3.5F, 1, now);
            kx += k.width(font) + 4;
            Component label = Component.translatable(LANG + "key." + r[1]);
            AflUiDraw.text(g, font, label, kx, y + 5, AflUiStyle.TEXT, 1);
            kx += font.width(label) + 12;
        }
    }

    /** Under the cursor: a town's card, or the place's position and biome (or that it was never seen). */
    private void hover(GuiGraphics g, int mouseX, int mouseY) {
        if (mouseY < 26 || dragging) return;
        double x = worldX(mouseX), z = worldZ(mouseY);
        MapTowns.Town town = known(x, z) ? MapClient.towns().at(x, z) : null;
        List<Component> lines = new ArrayList<>();
        int swatch = -1;
        if (town != null && RoadNames.town(town.id()) != null) {
            String en = RoadNames.town(town.id()), key = "map.apocalypse_firstlight.town." + town.id();
            lines.add(Component.literal(en));
            if (I18n.exists(key) && !I18n.get(key).equals(en)) lines.add(Component.translatable(key));
        } else {
            int s = sample(x, z);
            lines.add(Component.literal("X " + (int) Math.floor(x) + "   Z " + (int) Math.floor(z)));
            if (s == 0) lines.add(Component.translatable(LANG + "unknown"));
            else {
                int b = MapGround.biome(s);
                String rid = MapStyle.registryId(b);
                lines.add(rid == null ? Component.literal("?") : Component.translatable("biome." + rid.replace(':', '.')));
                swatch = MapGround.water(s) ? MapStyle.WATER : MapStyle.mapColour(b);
            }
        }
        float w = 0;
        for (Component c : lines) w = Math.max(w, font.width(c));
        if (swatch >= 0) w = Math.max(w, font.width(lines.get(1)) + 11);
        float px = mouseX + 10, py = mouseY + 8, ph = 6 + lines.size() * 11;
        if (px + w + 12 > width) px = mouseX - w - 22;
        if (py + ph > height) py = mouseY - ph - 4;
        AflUiDraw.label(g, px, py, w + 12, ph, 1);
        float ty = py + 4;
        for (int i = 0; i < lines.size(); i++) {
            float tx = px + 6;
            if (i == 1 && swatch >= 0) {
                AflUiShapes.fill(g, tx, ty, 8, 8, 1.5F, swatch, 1);
                tx += 11;
            }
            AflUiDraw.text(g, font, lines.get(i), tx, ty, i == 0 ? AflUiStyle.TEXT : AflUiStyle.TEXT_DIM, 1);
            ty += 11;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!ready()) return true;
        if (button == 1) { fold(); return true; }
        if (button == 0) { dragging = true; return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button != 0 || !ready()) return false;
        cx -= dx * mPerGui();
        cz -= dy * mPerGui();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!ready()) return true;
        AflKeyHint.scrolled();
        double wx = worldX(mouseX), wz = worldZ(mouseY);
        mpp = Math.max(NEAR, Math.min(FAR, mpp * Math.pow(0.8, delta)));
        // keep the point under the cursor where it is
        cx = wx - (mouseX - width / 2.0) * mPerGui();
        cz = wz - (mouseY - height / 2.0) * mPerGui();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft.options.keyJump.matches(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_SPACE) { home(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
