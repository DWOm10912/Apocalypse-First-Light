package com.antaurora.apofirstlight.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;

import java.util.List;

/** Added to FontSet by AflFontSetMixin (docs/ui/afl_font_v1.md). */
public interface AflFontSetAccess {
    List<GlyphProvider> afl$providers();

    /** Drops cached glyph metrics, baked glyphs and atlas textures; the providers stay open. */
    void afl$flush();

    /**
     * The baked glyph for magnified text at {@code raster} screen pixels per GUI unit (AflSmoothFonts#rasterFor). AFL
     * font glyphs are rasterised at that size and cached per raster; characters from the vanilla fallback fonts and
     * sets without the AFL font get the normal glyph.
     */
    BakedGlyph afl$glyph(int codepoint, int raster);
}
