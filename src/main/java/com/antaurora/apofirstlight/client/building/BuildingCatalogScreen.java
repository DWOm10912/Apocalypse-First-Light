package com.antaurora.apofirstlight.client.building;

import com.antaurora.apofirstlight.building.BuildingCatalog;
import com.antaurora.apofirstlight.building.BuildingActions;
import com.antaurora.apofirstlight.client.workbench.WorkbenchRecipes;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.inventory.CarriedItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The building piece catalog (building hammer B0, docs/gameplay/building_hammer_b0.md): what the hammer can install, not
 * a second recipe book. A piece shows when the player carries one (learned or not), knows a workbench recipe that makes
 * it, or is in Creative; each tile has the piece's model, name and how many are on the body. A click chooses the piece
 * and closes; a click on the chosen one puts it away. Drawn on the 480 x 270 canvas at the largest whole scale, like the
 * workbench page.
 */
public final class BuildingCatalogScreen extends Screen {
    private static final float W = 480, H = 270;
    private static final float P_W = 300, P_H = 190, P_X = (W - P_W) / 2, P_Y = (H - P_H) / 2;
    private static final float TILE = 64, GAP = 6, COLS = 4;
    private static final float CAP = 0.56F;

    private record Tile(BuildingCatalog.Piece piece, ItemStack stack, int carried, float x, float y) {}

    private final List<Tile> tiles = new ArrayList<>();
    private float scale = 1, originX, originY;

    public BuildingCatalogScreen() {
        super(Component.translatable("screen.apocalypse_firstlight.building_catalog.title"));
    }

    @Override
    protected void init() {
        var window = minecraft.getWindow();
        int whole = Math.max(1, (int) Math.min(window.getWidth() / W, window.getHeight() / H));
        scale = (float) (whole / window.getGuiScale());
        originX = (width - W * scale) / 2;
        originY = (height - H * scale) / 2;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        if (minecraft.player == null || !BuildingActions.holdingHammer(minecraft.player)) onClose();
    }

    private float vx(double mouseX) { return (float) ((mouseX - originX) / scale); }
    private float vy(double mouseY) { return (float) ((mouseY - originY) / scale); }

    /** The pieces this player may choose: carried, made by a known workbench recipe, or anything in Creative. */
    private void collect() {
        tiles.clear();
        var player = minecraft.player;
        if (player == null) return;
        int i = 0;
        for (var piece : BuildingCatalog.PIECES) {
            var item = piece.item();
            if (item == null) continue;
            int carried = CarriedItems.count(player, item);
            boolean known = WorkbenchRecipes.makes(item);
            if (carried <= 0 && !known && !player.getAbilities().instabuild) continue;
            float x = P_X + 12 + (i % (int) COLS) * (TILE + GAP), y = P_Y + 34 + (i / (int) COLS) * (TILE + GAP);
            tiles.add(new Tile(piece, new ItemStack(item), carried, x, y));
            i++;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        collect();
        float mx = vx(mouseX), my = vy(mouseY);
        var chosen = BuildingHammerClient.selected();
        long now = System.nanoTime();
        var pose = g.pose();
        pose.pushPose();
        pose.translate(originX, originY, 0);
        pose.scale(scale, scale, 1);
        AflUiShapes.box(g, P_X, P_Y, P_W, P_H, AflUiStyle.RADIUS_PANEL, AflUiStyle.BACK, AflUiStyle.BACK_ALPHA, AflUiStyle.LINE,
                AflUiStyle.LINE_ALPHA, AflUiStyle.SHADOW_ALPHA, 0);
        text(g, title, P_X + 12, P_Y + 10, 8, AflUiStyle.TEXT, 1);
        Component close = Component.translatable("screen.apocalypse_firstlight.building_catalog.close");
        float closeW = width(close, 6), right = P_X + P_W - 12;
        text(g, close, right - closeW, P_Y + 11, 6, AflUiStyle.TEXT_DIM, 1);
        plainCap(g, "Esc", right - closeW - 4, P_Y + 10.7F);
        AflUiShapes.fill(g, P_X + 12, P_Y + 26, P_W - 24, 0.4F, 0, AflUiStyle.LINE, AflUiStyle.LINE_ALPHA);

        if (tiles.isEmpty()) {
            Component none = Component.translatable("screen.apocalypse_firstlight.building_catalog.empty");
            text(g, none, P_X + (P_W - width(none, 6)) / 2, P_Y + P_H / 2 - 3, 6, AflUiStyle.TEXT_DIM, 1);
        }
        for (Tile t : tiles) {
            boolean hover = in(mx, my, t.x, t.y, TILE, TILE);
            boolean picked = chosen != null && chosen.id().equals(t.piece.id());
            AflUiShapes.box(g, t.x, t.y, TILE, TILE, AflUiStyle.RADIUS_TILE, hover ? AflUiStyle.BACK_HOVER : AflUiStyle.BACK, 0.55F,
                    picked ? AflUiStyle.ACCENT : AflUiStyle.LINE, picked ? 1 : hover ? AflUiStyle.LINE_HOVER_ALPHA : 0.22F, 0, 0);
            icon(g, t.stack, t.x + (TILE - 30) / 2, t.y + 6, 30);
            Component name = t.stack.getHoverName();
            float size = width(name, 5.4F) > TILE - 6 ? 4.6F : 5.4F;
            text(g, name, t.x + (TILE - width(name, size)) / 2, t.y + 42, size, AflUiStyle.TEXT, 1);
            Component have = player().getAbilities().instabuild && t.carried == 0
                    ? Component.translatable("screen.apocalypse_firstlight.building_catalog.creative")
                    : Component.translatable("screen.apocalypse_firstlight.building_catalog.carried", t.carried);
            text(g, have, t.x + (TILE - width(have, 4.8F)) / 2, t.y + 52, 4.8F,
                    t.carried > 0 || player().getAbilities().instabuild ? AflUiStyle.TEXT_DIM : AflUiStyle.WARN, 1);
        }

        // key bar: left click chooses (or puts the chosen piece away)
        float y = P_Y + P_H - 16;
        Tile hovered = hovered(mx, my);
        boolean away = hovered != null && chosen != null && chosen.id().equals(hovered.piece.id());
        float w = cap(g, AflKeyHint.MOUSE_LEFT, P_X + 12, y + 1.5F, now);
        Component label = Component.translatable("screen.apocalypse_firstlight.building_catalog." + (away ? "deselect" : "select"));
        text(g, label, P_X + 12 + w + 3, y + 2.8F, 5.4F, hovered != null ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1);
        pose.popPose();
    }

    private net.minecraft.world.entity.player.Player player() { return minecraft.player; }

    private Tile hovered(float mx, float my) {
        for (Tile t : tiles) if (in(mx, my, t.x, t.y, TILE, TILE)) return t;
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Tile t = hovered(vx(mouseX), vy(mouseY));
        if (t == null) return super.mouseClicked(mouseX, mouseY, button);
        var chosen = BuildingHammerClient.selected();
        BuildingHammerClient.select(chosen != null && chosen.id().equals(t.piece.id()) ? null : t.piece);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.35F));
        onClose();
        return true;
    }

    // ---------------------------------------------------------------- small helpers (as on the workbench page)

    private static boolean in(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private float cap(GuiGraphics g, AflKeyHint key, float x, float y, long now) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(CAP, CAP, 1);
        key.draw(g, font, 0, 0, 1, now);
        pose.popPose();
        return key.width(font) * CAP;
    }

    private void plainCap(GuiGraphics g, String key, float right, float y) {
        float s = 0.62F, w = Math.max(AflKeyHint.HEIGHT, font.width(key) + 6) * s, x = right - w;
        AflUiShapes.fill(g, x, y, w, AflKeyHint.HEIGHT * s, AflUiStyle.RADIUS_KEY, AflUiStyle.KEY_BACK, AflUiStyle.KEY_BACK_ALPHA);
        text(g, Component.literal(key), x + (w - width(Component.literal(key), 9 * s)) / 2, y + 2 * s, 9 * s, AflUiStyle.KEY_TEXT, 1);
    }

    private void icon(GuiGraphics g, ItemStack stack, float x, float y, float size) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(size / 16, size / 16, 1);
        g.renderItem(stack, 0, 0);
        pose.popPose();
    }

    private void text(GuiGraphics g, Component text, float x, float y, float size, int rgb, float alpha) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        float s = size / font.lineHeight;
        pose.scale(s, s, 1);
        g.drawString(font, text, 0, 0, AflUiStyle.argb(rgb, alpha), false);
        pose.popPose();
    }

    private float width(Component text, float size) {
        return font.width(text) * size / font.lineHeight;
    }
}
