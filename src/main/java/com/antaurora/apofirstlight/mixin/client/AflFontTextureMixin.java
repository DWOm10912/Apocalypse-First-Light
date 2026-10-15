package com.antaurora.apofirstlight.mixin.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Game-wide smooth font (docs/ui/afl_font_v1.md): clear a new grey-scale atlas page. Vanilla only allocates it, so the
 * one-texel gaps it leaves between glyphs hold whatever the driver had there (often an earlier page's glyphs after a GUI
 * scale change). Vanilla samples nearest and never reads them; AFL samples linearly, and a magnified glyph's edge mixes
 * that texel in as a faint frame. Colour pages (bitmap and Unifont glyphs) stay nearest and are left alone.
 */
@Mixin(FontTexture.class)
public abstract class AflFontTextureMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void afl$clear(GlyphRenderTypes renderTypes, boolean colored, CallbackInfo ci) {
        if (colored) return;
        ((AbstractTexture) (Object) this).bind();
        NativeImage blank = new NativeImage(NativeImage.Format.LUMINANCE, 256, 256, true);
        blank.upload(0, 0, 0, 0, 0, 256, 256, false, true);
    }
}
