package com.antaurora.apofirstlight.client.inventory;

import com.antaurora.apofirstlight.client.survivor.AflCharacterScreen;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.client.workbench.WorkbenchClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

/**
 * The AFL inventory page (docs/ui/afl_inventory_grid_v0_plan.md 5, concept docs/ui/afl_inventory_grid_v0_concept.md) over
 * the player's own menu: the page (AflPageScreen) with the character page button and, opened from a workbench, the
 * "背包 / 制作" tabs in the title row, and 2x2 crafting at the head of the right column.
 */
public final class AflInventoryScreen extends AflPageScreen<InventoryMenu> {
    /** The character page button after the name in the title row (survivor plan section 9, P2c). */
    private static final float CHAR_Y = 4.5F, CHAR_H = 12;
    private float charX, charW;
    /** Set when leaving for the character page: the menu stays open (no removed(), which clears the craft result). */
    private boolean toCharacter;
    /** Opened from a workbench (WorkbenchClient): the "背包 / 制作" tabs in the title row; set when leaving for 制作. */
    private float craftTabX, craftTabW;
    private boolean toCrafting;

    public AflInventoryScreen(Player player) {
        super(player.inventoryMenu, player, Component.translatable("container.crafting"));
    }

    @Override
    Slot pageSlot(int role) {
        return menu.getSlot(role);
    }

    @Override
    boolean keepsMenu() {
        return toCharacter || toCrafting;
    }

    @Override
    boolean inventoryKey() {
        return toCrafting();
    }

    @Override
    public void onClose() {
        WorkbenchClient.unlink();
        super.onClose();
    }

    /** Back to the workbench's crafting page (its tab, or the inventory key while linked); not with a stack on the cursor. */
    private boolean toCrafting() {
        if (!menu.getCarried().isEmpty() || WorkbenchClient.linked() == null) return false;
        toCrafting = true;
        if (WorkbenchClient.toCrafting()) return true;
        toCrafting = false;
        return false;
    }

    @Override
    void titleExtras(GuiGraphics g, float mx, float my, long now) {
        Component character = Component.translatable("survivor.apocalypse_firstlight.character.open");
        charX = A_X + width(player.getDisplayName(), 9) + 6;
        charW = width(character, 7) + 10;
        boolean hover = menu.getCarried().isEmpty() && in(mx, my, charX, CHAR_Y, charW, CHAR_H);
        AflUiDraw.button(g, charX, CHAR_Y, charW, CHAR_H, hover, false, menu.getCarried().isEmpty(), 1);
        text(g, character, charX + 5, CHAR_Y + 2.8F, 7, menu.getCarried().isEmpty() ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1);
        if (WorkbenchClient.linked() != null) workbenchTabs(g, mx, my, now);
    }

    /** Opened from a workbench: "背包" (this page, the accent underline) and "制作" with the inventory key's cap after it. */
    private void workbenchTabs(GuiGraphics g, float mx, float my, long now) {
        Component inv = Component.translatable("screen.apocalypse_firstlight.workbench.tab.inventory");
        Component craft = Component.translatable("screen.apocalypse_firstlight.workbench.tab.craft");
        float invW = width(inv, 8), invX = 222 - invW / 2;
        craftTabW = width(craft, 8);
        craftTabX = 262 - craftTabW / 2;
        text(g, inv, invX, 7, 8, AflUiStyle.TEXT, 1);
        AflUiShapes.fill(g, invX - 1, 16.5F, invW + 2, 1.2F, 0.5F, AflUiStyle.ACCENT, 1);
        boolean hover = menu.getCarried().isEmpty() && in(mx, my, craftTabX - 4, 3, craftTabW + 8, 16);
        text(g, craft, craftTabX, 7, 8, hover ? AflUiStyle.TEXT : AflUiStyle.TEXT_DIM, 1);
        var cap = AflKeyHint.of(minecraft.options.keyInventory);
        g.pose().pushPose();
        g.pose().translate(craftTabX + craftTabW + 4, 6.4F, 0);
        g.pose().scale(0.8F, 0.8F, 1);
        cap.draw(g, font, 0, 0, 1, now);
        g.pose().popPose();
    }

    @Override
    float rightHead(GuiGraphics g, float mx, float my, float partialTick) {
        panel(g, C_X, C_Y, C_W, C_H);
        label(g, "crafting", C_X + 6, C_Y + 11);
        for (int i = 0; i < 4; i++) {
            float x = C_X + 6 + (i % 2) * 20, y = C_Y + 11 + (i / 2) * 20;
            slotBox(g, x, y, CELL, CELL);
            hits.add(slotHit(CRAFT + i, x, y, CELL, CELL));
        }
        text(g, Component.literal("→"), C_X + 55, C_Y + 26, 9, AflUiStyle.TEXT_DIM, 1);
        slotBox(g, C_X + 72, C_Y + 18, BOX, BOX);
        hits.add(slotHit(RESULT, C_X + 72, C_Y + 18, BOX, BOX));
        // no off-hand box: AFL has no off hand (2026-10-16); a stack still in it shows under overflow
        return O_Y;
    }

    @Override
    boolean headClicked(float mx, float my) {
        if (WorkbenchClient.linked() != null && in(mx, my, craftTabX - 4, 3, craftTabW + 8, 16)) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.35F));
            toCrafting();
            return true;
        }
        if (in(mx, my, charX, CHAR_Y, charW, CHAR_H)) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            toCharacter = true;
            minecraft.setScreen(new AflCharacterScreen());
            return true;
        }
        return false;
    }
}
