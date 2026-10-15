package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.font.AflSmoothFonts;
import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Game-wide smooth font (docs/ui/afl_font_v1.md): a "ttf" provider whose file is in the AFL namespace loads as an
 * AflSmoothFontProvider (raster follows the GUI scale). Every other TrueType font loads as in vanilla. A failure is
 * thrown on, and FontManager rejects the provider, so the vanilla fonts stay in use.
 */
@Mixin(TrueTypeGlyphProviderDefinition.class)
public abstract class AflTrueTypeDefinitionMixin {
    @Inject(method = "load", at = @At("HEAD"), cancellable = true)
    private void afl$smoothFont(ResourceManager resources, CallbackInfoReturnable<GlyphProvider> cir) {
        TrueTypeGlyphProviderDefinition self = (TrueTypeGlyphProviderDefinition) (Object) this;
        if (!AflSmoothFonts.claims(self.location())) return;
        cir.setReturnValue(AflSmoothFonts.load(resources, self.location(), self.size(), self.shift().y()));
    }
}
