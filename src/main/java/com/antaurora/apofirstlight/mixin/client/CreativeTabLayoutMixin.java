package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.CreativeTabLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.common.CreativeModeTabRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Creative tab layout (client/CreativeTabLayout): AFL first, no gaps, 15 tabs a page with a column down the left side. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeTabLayoutMixin {
    @Shadow private static CreativeModeTab selectedTab;

    @Redirect(method = "init", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraftforge/common/CreativeModeTabRegistry;getSortedCreativeModeTabs()Ljava/util/List;"))
    private List<CreativeModeTab> afl$orderTabs() {
        return CreativeTabLayout.order(CreativeModeTabRegistry.getSortedCreativeModeTabs());
    }

    /** Forge starts a new page every 10 tabs. */
    @ModifyConstant(method = "init", constant = @Constant(intValue = 10))
    private int afl$tabsPerPage(int forge) {
        return CreativeTabLayout.PER_PAGE;
    }

    @Inject(method = "checkTabClicked", at = @At("HEAD"), cancellable = true)
    private void afl$tabClicked(CreativeModeTab tab, double x, double y, CallbackInfoReturnable<Boolean> cir) {
        int slot = CreativeTabLayout.slot(tab);
        if (slot < 0) return;
        int tx = CreativeTabLayout.x(slot), ty = CreativeTabLayout.y(slot, self().getYSize());
        cir.setReturnValue(x >= tx && x <= tx + CreativeTabLayout.width(slot) && y >= ty && y <= ty + CreativeTabLayout.height(slot));
    }

    @Inject(method = "checkTabHovering", at = @At("HEAD"), cancellable = true)
    private void afl$tabHovering(GuiGraphics g, CreativeModeTab tab, int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        int slot = CreativeTabLayout.slot(tab);
        if (slot < 0) return;
        var screen = self();
        // vanilla's box: 3 px in from the tab's corner, 5 px smaller
        int tx = screen.getGuiLeft() + CreativeTabLayout.x(slot) + 3, ty = screen.getGuiTop() + CreativeTabLayout.y(slot, screen.getYSize()) + 3;
        int w = CreativeTabLayout.width(slot) - 5, h = CreativeTabLayout.height(slot) - 5;
        boolean hovering = mouseX >= tx - 1 && mouseX < tx + w + 1 && mouseY >= ty - 1 && mouseY < ty + h + 1;
        if (hovering) g.renderTooltip(Minecraft.getInstance().font, tab.getDisplayName(), mouseX, mouseY);
        cir.setReturnValue(hovering);
    }

    @Inject(method = "renderTabButton", at = @At("HEAD"), cancellable = true)
    private void afl$renderTab(GuiGraphics g, CreativeModeTab tab, CallbackInfo ci) {
        int slot = CreativeTabLayout.slot(tab);
        if (slot < 0) return;
        var screen = self();
        CreativeTabLayout.render(g, Minecraft.getInstance().font, tab, slot, tab == selectedTab, screen.getGuiLeft(), screen.getGuiTop(), screen.getYSize());
        ci.cancel();
    }

    private CreativeModeInventoryScreen self() {
        return (CreativeModeInventoryScreen) (Object) this;
    }
}
