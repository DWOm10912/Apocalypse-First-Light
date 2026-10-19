package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.AflRingIcon;
import com.antaurora.apofirstlight.survivor.SurvivorGrowth;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The growth page's icons (docs/gameplay/survivor_background_skills_v1_plan.md 9, P3): strength (a dumbbell) and fitness
 * (lungs) as HUD-style glyphs (AflRingIcon distance fields), the gun classes as the guns' own HUD silhouettes (P9,
 * BR51, Silverwood 12), cold / heat tolerance as a snowflake and a sun, and the node marks inside the level pills (each
 * perk its own glyph: a magazine for the novice reload, one and two chevrons for the experienced and practised reloads; a
 * lock on levels this survivor cannot reach). Glyphs are baked once into small white textures and tinted when drawn, so the
 * page draws quads instead of filling pixels each frame. Positions and sizes in the caller's (canvas) units.
 */
final class GrowthIcons {
    private static final int BAKE = 64;
    private static final Map<AflRingIcon.Glyph, ResourceLocation> BAKED = new IdentityHashMap<>();

    static final AflRingIcon.Glyph DUMBBELL = (x, y) -> {
        double d = AflRingIcon.segment(x, y, 0.2, 0.5, 0.8, 0.5) - 0.045;
        d = Math.min(d, box(x, y, 0.25, 0.5, 0.065, 0.25, 0.03));
        d = Math.min(d, box(x, y, 0.75, 0.5, 0.065, 0.25, 0.03));
        d = Math.min(d, box(x, y, 0.12, 0.5, 0.045, 0.15, 0.025));
        return Math.min(d, box(x, y, 0.88, 0.5, 0.045, 0.15, 0.025));
    };
    static final AflRingIcon.Glyph LUNGS = (x, y) -> {
        double left = ellipse(x, y, 0.3, 0.6, 0.17, 0.3), right = ellipse(x, y, 0.7, 0.6, 0.17, 0.3);
        double lobes = Math.max(Math.min(left, right), -(Math.abs(x - 0.5) - 0.06));
        double airway = AflRingIcon.segment(x, y, 0.5, 0.08, 0.5, 0.4) - 0.045;
        airway = Math.min(airway, AflRingIcon.segment(x, y, 0.5, 0.4, 0.37, 0.5) - 0.035);
        airway = Math.min(airway, AflRingIcon.segment(x, y, 0.5, 0.4, 0.63, 0.5) - 0.035);
        return Math.min(Math.max(lobes, -(airway - 0.04)), airway);
    };
    static final AflRingIcon.Glyph CHEVRON = (x, y) -> chevron(x, y, 0.5);
    static final AflRingIcon.Glyph CHEVRON_2 = (x, y) -> Math.min(chevron(x, y, 0.36), chevron(x, y, 0.64));
    static final AflRingIcon.Glyph SNOWFLAKE = (x, y) -> {
        double d = 1;
        for (int i = 0; i < 3; i++) {
            double a = Math.PI / 3 * i, cx = Math.cos(a) * 0.36, cy = Math.sin(a) * 0.36;
            d = Math.min(d, AflRingIcon.segment(x, y, 0.5 - cx, 0.5 - cy, 0.5 + cx, 0.5 + cy) - 0.045);
        }
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3 * i, ex = 0.5 + Math.cos(a) * 0.25, ey = 0.5 + Math.sin(a) * 0.25;
            for (int side = -1; side <= 1; side += 2) {
                double b = a + side * 0.75;
                d = Math.min(d, AflRingIcon.segment(x, y, ex, ey, ex + Math.cos(b) * 0.11, ey + Math.sin(b) * 0.11) - 0.032);
            }
        }
        return d;
    };
    static final AflRingIcon.Glyph SUN = (x, y) -> {
        double d = Math.hypot(x - 0.5, y - 0.5) - 0.17;
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i, c = Math.cos(a), s = Math.sin(a);
            d = Math.min(d, AflRingIcon.segment(x, y, 0.5 + c * 0.27, 0.5 + s * 0.27, 0.5 + c * 0.4, 0.5 + s * 0.4) - 0.04);
        }
        return d;
    };
    static final AflRingIcon.Glyph MAGAZINE = (x, y) -> {
        // a pistol magazine leaning back: a slanted rounded bar with a base plate
        double u = x - 0.5 + (y - 0.5) * 0.25;
        double body = box(u + 0.5, y, 0.5, 0.45, 0.12, 0.3, 0.04);
        return Math.min(body, box(u + 0.5, y, 0.5, 0.8, 0.17, 0.06, 0.03));
    };
    static final AflRingIcon.Glyph DIAMOND = (x, y) -> (Math.abs(x - 0.5) + Math.abs(y - 0.5) - 0.3) * 0.7;
    static final AflRingIcon.Glyph LOCK = (x, y) -> Math.min(box(x, y, 0.5, 0.66, 0.26, 0.2, 0.05),
            Math.max(Math.abs(Math.hypot(x - 0.5, y - 0.42) - 0.15) - 0.05, y - 0.46));

    private GrowthIcons() {
    }

    private static double box(double x, double y, double cx, double cy, double hw, double hh, double r) {
        double qx = Math.abs(x - cx) - hw + r, qy = Math.abs(y - cy) - hh + r;
        return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r;
    }

    private static double ellipse(double x, double y, double cx, double cy, double rx, double ry) {
        return (Math.hypot((x - cx) / rx, (y - cy) / ry) - 1) * Math.min(rx, ry);
    }

    private static double chevron(double x, double y, double cy) {
        return Math.min(AflRingIcon.segment(x, y, 0.2, cy + 0.14, 0.5, cy - 0.14), AflRingIcon.segment(x, y, 0.5, cy - 0.14, 0.8, cy + 0.14)) - 0.075;
    }

    /** A glyph in a square of {@code size} with its top-left corner at (x, y). */
    static final AflRingIcon.Glyph DOT = (x, y) -> Math.hypot(x - 0.5, y - 0.5) - 0.3;

    static void glyph(GuiGraphics g, AflRingIcon.Glyph glyph, float x, float y, float size, int rgb, float alpha) {
        quad(g, baked(glyph), x, y, size, size, rgb, alpha);
    }

    /** A gun class's silhouette fitted into the box (w × h), centred; false for an item that is not a gun class. */
    static boolean gun(GuiGraphics g, String id, float x, float y, float w, float h, int rgb, float alpha) {
        String name;
        float aspect;
        switch (id) {
            case SurvivorGrowth.PISTOL -> { name = "p9_01"; aspect = 512F / 317F; }
            case SurvivorGrowth.SHOTGUN -> { name = "silverwood_12"; aspect = 1263F / 240F; }
            case SurvivorGrowth.LONG -> { name = "br51_01"; aspect = 1183F / 248F; }
            default -> { return false; }
        }
        float dw = Math.min(w, h * aspect), dh = dw / aspect;
        quad(g, new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/gun/" + name + "_hud.png"),
                x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, rgb, alpha);
        return true;
    }

    /** A perk's node mark (a diamond for a perk without its own glyph yet). */
    static AflRingIcon.Glyph perk(String perk) {
        return switch (perk) {
            case "reload_novice" -> MAGAZINE;
            case "reload_trained" -> CHEVRON;
            case "reload_practised" -> CHEVRON_2;
            default -> DIAMOND;
        };
    }

    /** The row icon of a body stat or growth item (a plain dot for items that have no icon yet). */
    static void item(GuiGraphics g, String id, boolean body, float x, float y, float w, float h, int rgb, float alpha) {
        if (!body && gun(g, id, x, y, w, h, rgb, alpha)) return;
        AflRingIcon.Glyph glyph = switch (id) {
            case SurvivorGrowth.FITNESS -> LUNGS;
            case SurvivorGrowth.STRENGTH -> DUMBBELL;
            case SurvivorGrowth.COLD -> SNOWFLAKE;
            case SurvivorGrowth.HEAT -> SUN;
            default -> DOT;
        };
        float s = Math.min(w, h) * 1.15F;
        glyph(g, glyph, x + (w - s) / 2, y + (h - s) / 2, s, rgb, alpha);
    }

    private static void quad(GuiGraphics g, ResourceLocation texture, float x, float y, float w, float h, int rgb, float alpha) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(w, h, 1);
        RenderSystem.enableBlend();
        g.setColor((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F, alpha);
        g.blit(texture, 0, 0, 1, 1, 0, 0, 1, 1, 1, 1);
        g.setColor(1, 1, 1, 1);
        pose.popPose();
    }

    private static ResourceLocation baked(AflRingIcon.Glyph glyph) {
        return BAKED.computeIfAbsent(glyph, gl -> {
            NativeImage image = new NativeImage(BAKE, BAKE, true);
            for (int py = 0; py < BAKE; py++) {
                for (int px = 0; px < BAKE; px++) {
                    double cover = 0.5 - gl.distance((px + 0.5) / BAKE, (py + 0.5) / BAKE) * BAKE;
                    int a = (int) Math.round(Math.max(0, Math.min(1, cover)) * 255);
                    image.setPixelRGBA(px, py, a << 24 | 0xFFFFFF);
                }
            }
            DynamicTexture texture = new DynamicTexture(image);
            texture.setFilter(true, false);
            return Minecraft.getInstance().getTextureManager().register("afl_growth_icon", texture);
        });
    }
}
