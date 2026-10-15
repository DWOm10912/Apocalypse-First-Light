package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.font.AflFontSetAccess;
import com.antaurora.apofirstlight.client.font.AflSmoothFonts;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Game-wide smooth font (docs/ui/afl_font_v1.md): magnified GUI text draws glyphs rasterised at its own size instead
 * of stretching the GUI-scale ones. Only the drawn glyph changes; advances and shadow / bold offsets still come from
 * the normal glyph metrics, so text measures and lays out exactly as before.
 */
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class AflFontStringOutputMixin {
    @Shadow
    @Final
    private Matrix4f pose;

    @Redirect(method = "accept", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/font/FontSet;getGlyph(I)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;"))
    private BakedGlyph afl$scaledGlyph(FontSet set, int codepoint) {
        int raster = AflSmoothFonts.rasterFor(pose);
        return raster == 0 ? set.getGlyph(codepoint) : ((AflFontSetAccess) set).afl$glyph(codepoint, raster);
    }
}
