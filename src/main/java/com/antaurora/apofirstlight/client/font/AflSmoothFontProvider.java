package com.antaurora.apofirstlight.client.font;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.function.Function;

/**
 * The game-wide smooth font (docs/ui/afl_font_v1.md): a TrueType provider rasterised at the real screen resolution.
 * One GUI unit is {@code scale} screen pixels, so each glyph is rasterised at {@code em * scale} pixels per em and
 * drawn back 1:1; the scale follows the GUI scale (AflSmoothFonts rescales and flushes the font sets). Advances and the
 * baseline are snapped to whole screen pixels, so text drawn at whole pixels lands exactly on the pixel grid. The
 * shadow and bold offsets are about half a GUI unit in whole pixels instead of vanilla's full unit, which smears
 * an anti-aliased glyph.
 *
 * <p>Magnified GUI text (titles, big headings) asks for its glyphs at a higher raster ({@link #glyph(int, int)}), so it
 * is drawn near 1:1 too instead of being stretched. Every glyph bitmap carries a transparent border: the atlas pages are
 * sampled linearly, and a magnified glyph's edge reads the texel next to it.
 *
 * <p>Vanilla's TrueTypeGlyphProvider is not reused: its oversample is fixed by the JSON, and it adds the shift and the
 * left side bearing twice.
 */
public final class AflSmoothFontProvider implements GlyphProvider {
    private static final int BMP = 0x10000;
    /** Transparent texels around each glyph bitmap. */
    private static final int PAD = 1;
    /** Highest raster for magnified text, screen pixels per GUI unit: two CJK glyphs still fit across a 256 atlas page. */
    static final int MAX_RASTER = 16;

    @Nullable
    private ByteBuffer fontMemory;
    @Nullable
    private STBTTFontinfo font;
    /** Em size and baseline (below the line top), in GUI units. */
    private final float em, baseline;
    private final IntSet supported = new IntOpenHashSet();
    private volatile int scale;
    private volatile float pointScale;

    AflSmoothFontProvider(ByteBuffer fontMemory, STBTTFontinfo font, float em, float baseline) {
        this.fontMemory = fontMemory;
        this.font = font;
        this.em = em;
        this.baseline = baseline;
        for (int cp = 0x20; cp < BMP; cp++) {
            if (STBTruetype.stbtt_FindGlyphIndex(font, cp) != 0) supported.add(cp);
        }
        rescale(AflSmoothFonts.guiScale());
    }

    int scale() {
        return scale;
    }

    /** Screen pixels per GUI unit; the owning font sets must drop their glyphs afterwards. */
    void rescale(int scale) {
        this.pointScale = STBTruetype.stbtt_ScaleForMappingEmToPixels(open(), em * scale);
        this.scale = scale;
    }

    /** Shadow and bold offset: half a GUI unit, at least one screen pixel, always whole pixels. */
    private static float offset(int scale) {
        return Math.max(1, Math.round(scale * 0.5F)) / (float) scale;
    }

    @Nullable
    @Override
    public GlyphInfo getGlyph(int codepoint) {
        return glyph(codepoint, 0);
    }

    /**
     * The glyph rasterised at {@code raster} screen pixels per GUI unit (magnified GUI text, AflFontSetMixin); 0 or
     * anything up to the GUI scale gives the normal glyph. Advance and offsets always stay those of the GUI scale, so
     * the layout (measured with the normal glyphs) does not change.
     */
    @Nullable
    public GlyphInfo glyph(int codepoint, int raster) {
        if (!supported.contains(codepoint)) return null;
        STBTTFontinfo font = open();
        int scale = this.scale;
        float pointScale = this.pointScale;
        int drawScale = Math.max(scale, raster);
        float drawPointScale = drawScale == scale ? pointScale : STBTruetype.stbtt_ScaleForMappingEmToPixels(font, em * drawScale);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int index = STBTruetype.stbtt_FindGlyphIndex(font, codepoint);
            if (index == 0) return null;
            IntBuffer advance = stack.mallocInt(1), bearing = stack.mallocInt(1);
            STBTruetype.stbtt_GetGlyphHMetrics(font, index, advance, bearing);
            IntBuffer x0 = stack.mallocInt(1), y0 = stack.mallocInt(1), x1 = stack.mallocInt(1), y1 = stack.mallocInt(1);
            STBTruetype.stbtt_GetGlyphBitmapBox(font, index, drawPointScale, drawPointScale, x0, y0, x1, y1);
            float units = Math.round(advance.get(0) * pointScale) / (float) scale;
            float offset = offset(scale);
            int width = x1.get(0) - x0.get(0), height = y1.get(0) - y0.get(0);
            if (width <= 0 || height <= 0) return new Space(units, offset);
            int top = Math.round(baseline * drawScale) + y0.get(0);
            return new Glyph(index, drawScale, drawPointScale, x0.get(0), top, width, height, units, offset);
        }
    }

    @Override
    public IntSet getSupportedGlyphs() {
        return supported;
    }

    @Override
    public void close() {
        if (font != null) {
            font.free();
            font = null;
        }
        MemoryUtil.memFree(fontMemory);
        fontMemory = null;
    }

    private STBTTFontinfo open() {
        if (fontMemory == null || font == null) throw new IllegalStateException("AFL font provider already closed");
        return font;
    }

    private record Space(float advance, float offset) implements GlyphInfo.SpaceGlyphInfo {
        @Override
        public float getAdvance() {
            return advance;
        }

        @Override
        public float getBoldOffset() {
            return offset;
        }

        @Override
        public float getShadowOffset() {
            return offset;
        }
    }

    /**
     * One glyph at one raster ({@code scale} pixels per GUI unit): left and top are those pixels from the pen position
     * and the line top. The sheet adds {@link #PAD} transparent texels on every side.
     */
    private final class Glyph implements GlyphInfo {
        private final int index, scale, left, top, width, height;
        private final float pointScale, advance, offset;

        Glyph(int index, int scale, float pointScale, int left, int top, int width, int height, float advance, float offset) {
            this.index = index;
            this.scale = scale;
            this.pointScale = pointScale;
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.advance = advance;
            this.offset = offset;
        }

        @Override
        public float getAdvance() {
            return advance;
        }

        @Override
        public float getBoldOffset() {
            return offset;
        }

        @Override
        public float getShadowOffset() {
            return offset;
        }

        @Override
        public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> stitch) {
            return stitch.apply(new SheetGlyphInfo() {
                @Override
                public int getPixelWidth() {
                    return width + 2 * PAD;
                }

                @Override
                public int getPixelHeight() {
                    return height + 2 * PAD;
                }

                @Override
                public float getOversample() {
                    return scale;
                }

                @Override
                public float getBearingX() {
                    return (left - PAD) / (float) scale;
                }

                /** BakedGlyph draws at y + up - 3. */
                @Override
                public float getBearingY() {
                    return 3.0F + (top - PAD) / (float) scale;
                }

                @Override
                public void upload(int x, int y) {
                    NativeImage image = new NativeImage(NativeImage.Format.LUMINANCE, width + 2 * PAD, height + 2 * PAD, true);
                    image.copyFromFont(open(), index, width, height, pointScale, pointScale, 0.0F, 0.0F, PAD, PAD);
                    image.upload(0, x, y, 0, 0, image.getWidth(), image.getHeight(), false, true);
                }

                @Override
                public boolean isColored() {
                    return false;
                }
            });
        }
    }
}
