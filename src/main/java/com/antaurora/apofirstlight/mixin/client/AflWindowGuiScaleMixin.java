package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.font.AflSmoothFonts;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Game-wide smooth font (docs/ui/afl_font_v1.md): re-rasterise the AFL font at the new GUI scale. Minecraft#resizeDisplay
 * sets the scale before the open screen re-lays out, so the layout already measures the new glyphs.
 */
@Mixin(Window.class)
public abstract class AflWindowGuiScaleMixin {
    @Inject(method = "setGuiScale", at = @At("TAIL"))
    private void afl$rescaleFont(double scale, CallbackInfo ci) {
        AflSmoothFonts.onGuiScale();
    }
}
