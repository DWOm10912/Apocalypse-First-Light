package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.font.CjkLineBreak;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CJK line breaking for every wrapped text (tooltips, screens, books; docs/ui/afl_font_v1.md section 4, item 6). Vanilla's
 * finder remembers only spaces as break points; this also records the positions {@link CjkLineBreak} allows, so the
 * latest legal point wins instead of a space far back on the line. A position is recorded only after a visible
 * character on the current line, so a line is never empty. Text without CJK wraps exactly as before.
 */
@Mixin(targets = "net.minecraft.client.StringSplitter$LineBreakFinder")
public abstract class AflCjkLineBreakMixin {
    @Shadow
    private int lastSpace;
    @Shadow
    private Style lastSpaceStyle;
    @Shadow
    private boolean hadNonZeroWidthChar;
    @Shadow
    private int offset;

    @Unique
    private int afl$prev = -1;

    @Inject(method = "accept", at = @At("HEAD"))
    private void afl$cjkBreak(int index, Style style, int codepoint, CallbackInfoReturnable<Boolean> cir) {
        if (hadNonZeroWidthChar && CjkLineBreak.breakBefore(afl$prev, codepoint)) {
            lastSpace = index + offset;
            lastSpaceStyle = style;
        }
        afl$prev = codepoint;
    }
}
