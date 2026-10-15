package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.inventory.ContainerGridPanel;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The grid side panel (ContainerGridPanel) is part of the window: a click or release on it never drops the cursor stack. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenGridMixin {
    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void afl$gridPanelIsInside(double mouseX, double mouseY, int left, int top, int button, CallbackInfoReturnable<Boolean> cir) {
        if (ContainerGridPanel.contains((AbstractContainerScreen<?>) (Object) this, mouseX, mouseY)) cir.setReturnValue(false);
    }
}
