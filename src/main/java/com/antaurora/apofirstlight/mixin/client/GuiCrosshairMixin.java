package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.AflCrosshair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crosshair V1 (client/AflCrosshair, docs/ui/crosshair_v1.md): vanilla's cross and its attack indicator are not drawn;
 * AFL's dot is. Done here rather than by cancelling the CROSSHAIR overlay so the overlay's Post listeners (the world
 * prompts, the wiring panel, the generator's timing bar) still run. The F3 screen keeps vanilla's axes.
 */
@Mixin(Gui.class)
public abstract class GuiCrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void afl$crosshair(GuiGraphics graphics, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.renderDebug && mc.player != null && !mc.player.isReducedDebugInfo() && !mc.options.reducedDebugInfo().get()) return;
        ci.cancel();
        AflCrosshair.render(graphics);
    }
}
