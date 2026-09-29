package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.LockedInventorySlotRendering;
import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractContainerScreen.class)
public abstract class LockedInventoryTooltipMixin {
    @Shadow protected Slot hoveredSlot;

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void afl$emptyLockedTooltip(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        var screen = (AbstractContainerScreen<?>) (Object) this;
        var player = Minecraft.getInstance().player;
        if (player != null && hoveredSlot != null && hoveredSlot.container == player.getInventory()
                && PlayerStorageCapacity.isLocked(hoveredSlot)
                && (!hoveredSlot.hasItem() || !screen.getMenu().getCarried().isEmpty())) {
            graphics.renderComponentTooltip(Minecraft.getInstance().font,
                    LockedInventorySlotRendering.tooltip(hoveredSlot.hasItem()), mouseX, mouseY);
            ci.cancel();
        }
    }

    /** Preserve the normal item tooltip, including its image, and append the overflow explanation. */
    @Inject(method = "getTooltipFromContainerItem", at = @At("RETURN"), cancellable = true)
    private void afl$overflowTooltip(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        var player = Minecraft.getInstance().player;
        if (player != null && hoveredSlot != null && hoveredSlot.container == player.getInventory()
                && hoveredSlot.getItem() == stack && PlayerStorageCapacity.isLocked(hoveredSlot)) {
            var lines = new ArrayList<>(cir.getReturnValue());
            lines.add(Component.empty());
            lines.addAll(LockedInventorySlotRendering.tooltip(true));
            cir.setReturnValue(lines);
        }
    }
}
