package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.thirst.ClientThirstHud;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The wiki's status icons (wiki/public/img/status/&lt;id&gt;_&lt;stage&gt;.png, StatusTable.vue): each HUD status badge
 * drawn offline with the HUD's own bands, glyphs and colours (StatusBadgeHud#badgeBands / #badgeGlyph, AflGauge#raster),
 * without the time-left arc or the flash, on a transparent background. Run: gradlew wikiStatusIcons.
 * <p>
 * Which glyph and kind each status stage shows follows StatusBadgeHud#collect; keep the two in step (the wiki's list of
 * statuses and stages is wiki/.vitepress/lib/survivor.mjs).
 */
public final class StatusBadgeIcons {
    /** Real pixels per GUI pixel: the badge (radius 5.4 GUI px) comes out about 100 px across. */
    private static final int SCALE = 8;

    private StatusBadgeIcons() {}

    public static void main(String[] args) throws IOException {
        File out = new File(args.length > 0 ? args[0] : "wiki/public/img/status");
        if (!out.isDirectory() && !out.mkdirs()) throw new IOException("cannot create " + out);
        var B = StatusBadgeHud.Kind.BAD; var D = StatusBadgeHud.Kind.DANGER; var C = StatusBadgeHud.Kind.COLD; var G = StatusBadgeHud.Kind.GOOD;
        Map<String, Object[]> icons = new LinkedHashMap<>();
        icons.put("cold", new Object[] { StatusBadgeHud.SNOWFLAKE, C, C, D });
        icons.put("hot", new Object[] { StatusBadgeHud.SUN, B, B, D });
        icons.put("wet", new Object[] { StatusBadgeHud.WET, C, C });
        icons.put("thirst", new Object[] { ClientThirstHud.WATER_DROP, B, D, D });
        icons.put("hunger", new Object[] { SurvivalVitalsHud.DRUMSTICK, B, D });
        icons.put("load", new Object[] { SurvivalVitalsHud.KETTLEBELL, B, B, D });
        icons.put("radiation", new Object[] { StatusBadgeHud.TREFOIL, B, B, B, D });
        icons.put("gastro", new Object[] { StatusBadgeHud.GERM, B });
        icons.put("gasoline", new Object[] { StatusBadgeHud.FUEL_CAN, B });
        icons.put("diesel", new Object[] { StatusBadgeHud.FUEL_CAN, B });
        icons.put("fed", new Object[] { StatusBadgeHud.FORK_KNIFE, G });
        icons.put("warming", new Object[] { StatusBadgeHud.FLAME, G });

        double r = SurvivalHudLayout.BADGE_R, reach = r + 1.2;
        int size = (int) Math.ceil(2 * reach * SCALE);
        double c = size / 2.0;
        int written = 0;
        for (var e : icons.entrySet()) {
            Object[] v = e.getValue();
            AflRingIcon.Glyph glyph = (AflRingIcon.Glyph) v[0];
            for (int stage = 1; stage < v.length; stage++) {
                BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                AflGauge.raster(size, SCALE, c, c, StatusBadgeHud.badgeBands((StatusBadgeHud.Kind) v[stage], r, Double.NaN, 0, 1),
                        StatusBadgeHud.badgeGlyph(glyph, r, 1),
                        (x, y, abgr) -> image.setRGB(x, y, abgr & 0xFF00FF00 | (abgr & 0xFF) << 16 | (abgr >> 16) & 0xFF));
                ImageIO.write(image, "png", new File(out, e.getKey() + "_" + stage + ".png"));
                written++;
            }
        }
        System.out.println("status icons: " + written + " x " + size + " px -> " + out.getAbsolutePath());
    }
}
