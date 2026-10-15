package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.font.AflFontRenderTypes;
import com.antaurora.apofirstlight.client.font.AflFontSetAccess;
import com.antaurora.apofirstlight.client.font.AflSmoothFontProvider;
import com.antaurora.apofirstlight.client.font.AflSmoothFonts;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.gui.font.CodepointMap;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Game-wide smooth font (docs/ui/afl_font_v1.md): a font set that holds the AFL smooth font registers for GUI scale
 * changes, can drop its glyphs without closing its providers, and draws its grey-scale (TrueType) atlas pages with
 * linear filtering. Bitmap and Unifont glyphs live on separate colour pages and keep vanilla's nearest filtering.
 * Magnified GUI text gets AFL glyphs rasterised at its own size from a per-raster cache (AflFontStringOutputMixin).
 */
@Mixin(FontSet.class)
public abstract class AflFontSetMixin implements AflFontSetAccess {
    @Shadow
    @Final
    private List<GlyphProvider> providers;
    @Shadow
    @Final
    private CodepointMap<BakedGlyph> glyphs;
    @Shadow
    @Final
    private CodepointMap<?> glyphInfos;
    @Shadow
    private BakedGlyph missingGlyph;
    @Shadow
    private BakedGlyph whiteGlyph;

    @Unique
    private boolean afl$smooth;
    /** Magnified-text glyphs: raster (screen pixels per GUI unit) to codepoint to glyph. Same atlas pages as the rest. */
    @Unique
    private final Int2ObjectMap<Int2ObjectMap<BakedGlyph>> afl$scaled = new Int2ObjectOpenHashMap<>();

    @Shadow
    protected abstract void closeTextures();

    @Shadow
    protected abstract BakedGlyph stitch(SheetGlyphInfo glyph);

    @Shadow
    public abstract BakedGlyph getGlyph(int codepoint);

    @Inject(method = "reload", at = @At("TAIL"))
    private void afl$track(List<GlyphProvider> list, CallbackInfo ci) {
        afl$scaled.clear();
        afl$smooth = providers.stream().anyMatch(p -> p instanceof AflSmoothFontProvider);
        if (afl$smooth) AflSmoothFonts.track((FontSet) (Object) this);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void afl$untrack(CallbackInfo ci) {
        afl$scaled.clear();
        AflSmoothFonts.untrack((FontSet) (Object) this);
    }

    @Redirect(method = "stitch", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/font/GlyphRenderTypes;createForIntensityTexture(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/gui/font/GlyphRenderTypes;"))
    private GlyphRenderTypes afl$linearPages(ResourceLocation texture) {
        return afl$smooth ? AflFontRenderTypes.linear(texture) : GlyphRenderTypes.createForIntensityTexture(texture);
    }

    @Override
    public List<GlyphProvider> afl$providers() {
        return providers;
    }

    @Override
    public void afl$flush() {
        if (providers.isEmpty()) return;
        closeTextures();
        glyphs.clear();
        glyphInfos.clear();
        afl$scaled.clear();
        missingGlyph = SpecialGlyphs.MISSING.bake(this::stitch);
        whiteGlyph = SpecialGlyphs.WHITE.bake(this::stitch);
    }

    @Override
    public BakedGlyph afl$glyph(int codepoint, int raster) {
        if (!afl$smooth) return getGlyph(codepoint);
        Int2ObjectMap<BakedGlyph> cache = afl$scaled.computeIfAbsent(raster, r -> new Int2ObjectOpenHashMap<>());
        BakedGlyph glyph = cache.get(codepoint);
        if (glyph == null) {
            glyph = afl$bakeScaled(codepoint, raster);
            cache.put(codepoint, glyph);
        }
        return glyph;
    }

    /** The first provider that has the character decides, as in FontSet#computeBakedGlyph. */
    @Unique
    private BakedGlyph afl$bakeScaled(int codepoint, int raster) {
        for (GlyphProvider provider : providers) {
            if (provider instanceof AflSmoothFontProvider smooth) {
                GlyphInfo info = smooth.glyph(codepoint, raster);
                if (info != null) return info.bake(this::stitch);
            } else if (provider.getGlyph(codepoint) != null) {
                break;
            }
        }
        return getGlyph(codepoint);
    }
}
