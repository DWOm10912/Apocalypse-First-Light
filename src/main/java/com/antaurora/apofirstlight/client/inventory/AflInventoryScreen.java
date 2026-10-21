package com.antaurora.apofirstlight.client.inventory;

import com.antaurora.apofirstlight.client.survivor.AflCharacterScreen;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.health.BodyPart;
import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import com.antaurora.apofirstlight.inventory.grid.GridActions;
import com.antaurora.apofirstlight.inventory.grid.GridPackets;
import com.antaurora.apofirstlight.inventory.grid.GridStorage;
import com.antaurora.apofirstlight.inventory.grid.GroundItems;
import com.antaurora.apofirstlight.inventory.grid.GunSling;
import com.antaurora.apofirstlight.inventory.grid.Loadout;
import com.antaurora.apofirstlight.inventory.grid.ItemSizes;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.antaurora.apofirstlight.temperature.ClientTemperature;
import com.antaurora.apofirstlight.weight.ClientWeightState;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The AFL inventory page (docs/ui/afl_inventory_grid_v0_plan.md 5, concept docs/ui/afl_inventory_grid_v0_concept.md):
 * gear, the weapon positions, the hand and the holster on the left; pockets, rig and backpack grids in the middle with the
 * quick bindings (4-9) under them; 2x2 crafting, the off hand, anything that overflows and what lies on the ground
 * nearby on the right (plan 10, 12). Drawn on its own 480 x 270 canvas at the largest whole scale the window takes (4 at
 * 1080p, 5 at 1440p), independent of the GUI scale. Vanilla slots still click through the menu; grid and ground clicks go
 * to the server as GridPackets.Action and come back through the pockets packet and Curios' slot sync. A click picks up
 * and the next click puts down; pressing on an item and letting go elsewhere does both (drag and drop).
 */
public final class AflInventoryScreen extends AbstractContainerScreen<InventoryMenu> {
    private static final float W = 480, H = 270, CELL = GridDrawing.CELL, BOX = 24, GEAR_BOX = 28;
    private static final float A_X = 10, A_Y = 20, A_W = 140, A_H = 242;
    private static final float B_X = 156, B_Y = 20, B_W = 150, B_H = 210, BX = 161, GX = BX + 34;
    private static final float Q_Y = 234, Q_H = 28;
    private static final float C_X = 312, C_Y = 20, C_W = 158, C_H = 70;
    private static final float O_Y = 96;
    /** The nearby panel: under the overflow (or where it would be) to the bottom of the gear column. */
    private static final float N_BOTTOM = 262, N_ROW = 20, N_MIN_H = 40;
    /** Canvas units the mouse must move between press and release for a drag. */
    private static final float DRAG = 4;
    /** How long a drag's drop waits for the picked stack to reach the cursor (the server's answer). */
    private static final long DRAG_WAIT_NANOS = 600_000_000L;
    private static final float LABEL = 5.2F;
    private static final int PLACEHOLDER = -1;
    /** Menu indices: 2x2 crafting result and grid, armour (head, chest, legs, feet), the off hand (never drawn: NoOffHand empties it; overflow shows a stack still in it), the AFL Curios slots. */
    private static final int RESULT = 0, CRAFT = 1, HEAD = 5, CHEST = 6, LEGS = 7, FEET = 8, OFFHAND = 45;
    private static final int BACK_SLOT = 46, WRIST_SLOT = 47, EARS_SLOT = 48, RIG_SLOT = 49, HOLSTER_SLOT = 50, CLIP_SLOT = 51;
    /** Menu indices of the weapon positions and the hand (hotbar 0..3, HotbarRoles). */
    private static final int PRIMARY_MENU = 36, PISTOL_MENU = 37, MELEE_MENU = 38, HAND_MENU = 39;
    /** The slot id vanilla uses for a click outside the window: drops the cursor stack. */
    private static final int OUTSIDE = -999;
    private static final int FREE = GridDrawing.FREE, BLOCKED = GridDrawing.BLOCKED, SWAP = GridDrawing.SWAP;

    private interface Hit { float x(); float y(); float w(); float h(); }
    private record SlotHit(Slot slot, float x, float y, float w, float h) implements Hit {}
    private record PlaceholderHit(Component label, float x, float y, float w, float h) implements Hit {}
    private record CellHit(PlayerGrids.Target target, GridStorage storage, int section, float x, float y, float w, float h) implements Hit {}
    private record OverflowHit(PlayerGrids.Target target, int index, ItemStack stack, float x, float y, float w, float h) implements Hit {}
    /** A quick binding cell (keys 4-9): shows what the key takes; right-click unbinds. */
    private record BindHit(int index, float x, float y, float w, float h) implements Hit {}
    /** The "sling" tag on a gun in a weapon box: click to take the sling off. */
    private record SlingHit(int hotbarIndex, float x, float y, float w, float h) implements Hit {}
    /** One item lying nearby (its entity id): click takes it onto the cursor, shift-click straight in. */
    private record GroundHit(int entityId, ItemStack stack, float x, float y, float w, float h) implements Hit {}
    /** A part of the body map (BodySilhouette): hover shows its injuries and what helps; a click does nothing. */
    private record BodyHit(BodyPart part, float x, float y, float w, float h) implements Hit {}

    private final Player player;
    private final List<Hit> hits = new ArrayList<>();
    private float scale = 1, originX, originY;
    /** The body map's badges this frame, drawn at GUI coordinates once the canvas is popped. */
    private List<Object[]> bodyBadges = List.of();
    private float scroll, scrollMax;
    private boolean rotated;
    private float overflowH;
    private float nearbyY, nearbyH, nearbyScroll;
    private int nearbyRows;
    /** A press that picked something up, for a drag's drop on release. */
    private boolean pressPicked;
    private double pressX, pressY;
    /** A drop waiting for the dragged stack to arrive on the cursor. */
    private long pendingUntil;
    private double pendingX, pendingY;
    /** The character page button after the name in the title row (survivor plan section 9, P2c). */
    private static final float CHAR_Y = 4.5F, CHAR_H = 12;
    private float charX, charW;
    /** Set when leaving for the character page: the menu stays open (no removed(), which clears the craft result). */
    private boolean toCharacter;

    public AflInventoryScreen(Player player) {
        super(player.inventoryMenu, player.getInventory(), Component.translatable("container.crafting"));
        this.player = player;
    }

    @Override
    protected void init() {
        super.init();
        var window = minecraft.getWindow();
        int whole = Math.max(1, (int) Math.min(window.getWidth() / W, window.getHeight() / H));
        scale = (float) (whole / window.getGuiScale());
        originX = (width - W * scale) / 2;
        originY = (height - H * scale) / 2;
        // the whole canvas is the screen's area, so JEI and other overlays keep out of it
        leftPos = (int) Math.floor(originX);
        topPos = (int) Math.floor(originY);
        imageWidth = (int) Math.ceil(W * scale);
        imageHeight = (int) Math.ceil(H * scale);
    }

    private float vx(double mouseX) { return (float) ((mouseX - originX) / scale); }
    private float vy(double mouseY) { return (float) ((mouseY - originY) / scale); }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        if (pendingUntil != 0) {
            if (!menu.getCarried().isEmpty()) {
                pendingUntil = 0;
                mouseClicked(pendingX, pendingY, 0);
            } else if (System.nanoTime() > pendingUntil) pendingUntil = 0;
        }
        var carried = menu.getCarried();
        if (carried.isEmpty()) rotated = false;   // a stack picked from a vanilla slot starts unrotated; grid picks take the entry's
        float mx = vx(mouseX), my = vy(mouseY);
        hits.clear();
        var view = PlayerGrids.view(player);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(originX, originY, 0);
        pose.scale(scale, scale, 1);

        title(g, mx, my);
        gearColumn(g, mx, my);
        gridColumn(g, view, mx, my);
        bindPanel(g);
        craftingPanel(g);
        overflowPanel(g, view);
        nearbyPanel(g, carried, mx, my);
        drawSlots(g, mx, my);

        Hit hover = hitAt(mx, my);
        hoveredSlot = hover instanceof SlotHit s ? s.slot() : null;
        drawHover(g, hover, carried, mx, my);
        if (!carried.isEmpty()) drawCarried(g, carried, mx, my);
        pose.popPose();
        BodySilhouette.badges(g, bodyBadges, originX, originY, scale);

        if (carried.isEmpty()) tooltip(g, hover, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {}

    @Override
    public void removed() {
        if (!toCharacter) super.removed();
    }

    private void title(GuiGraphics g, float mx, float my) {
        text(g, player.getDisplayName(), A_X, 6.5F, 9, AflUiStyle.TEXT, 1);
        Component character = Component.translatable("survivor.apocalypse_firstlight.character.open");
        charX = A_X + width(player.getDisplayName(), 9) + 6;
        charW = width(character, 7) + 10;
        boolean hover = menu.getCarried().isEmpty() && in(mx, my, charX, CHAR_Y, charW, CHAR_H);
        AflUiDraw.button(g, charX, CHAR_Y, charW, CHAR_H, hover, false, menu.getCarried().isEmpty(), 1);
        text(g, character, charX + 5, CHAR_Y + 2.8F, 7, menu.getCarried().isEmpty() ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1);
        long now = System.nanoTime();
        float right = 470;
        right = keyCap(g, AflKeyHint.of(minecraft.options.keyInventory), Component.translatable("screen.apocalypse_firstlight.inventory.close"), right, now);
        if (!menu.getCarried().isEmpty())
            keyCap(g, AflKeyHint.of(GridClient.ROTATE), Component.translatable("screen.apocalypse_firstlight.inventory.rotate"), right - 8, now);
        else
            plainCap(g, "4–9", Component.translatable("screen.apocalypse_firstlight.inventory.bind"), right - 8);
    }

    /** A key cap with fixed text (a key range) and its action word, right-aligned at {@code right}. */
    private void plainCap(GuiGraphics g, String key, Component label, float right) {
        float labelW = font.width(label);
        text(g, label, right - labelW, 7.5F, 9, AflUiStyle.TEXT, 1);
        float cap = Math.max(AflKeyHint.HEIGHT, font.width(key) + 6), x = right - labelW - 4 - cap;
        AflUiShapes.fill(g, x, 6, cap, AflKeyHint.HEIGHT, AflUiStyle.RADIUS_KEY, AflUiStyle.KEY_BACK, AflUiStyle.KEY_BACK_ALPHA);
        g.drawString(font, key, (int) (x + (cap - font.width(key)) / 2F), 8, AflUiStyle.argb(AflUiStyle.KEY_TEXT, 1), false);
    }

    /** A key cap and its action word, right-aligned at {@code right}; returns where the next pair ends. */
    private float keyCap(GuiGraphics g, AflKeyHint key, Component label, float right, long now) {
        float labelW = font.width(label);
        text(g, label, right - labelW, 7.5F, 9, AflUiStyle.TEXT, 1);
        float cap = key.width(font);
        key.draw(g, font, right - labelW - 4 - cap, 6, 1, now);
        return right - labelW - 4 - cap;
    }

    private void gearColumn(GuiGraphics g, float mx, float my) {
        panel(g, A_X, A_Y, A_W, A_H);
        bodyMap(g, mx, my);
        String[] left = {"head", "face", "ears", "watch", "legs"};
        int[] leftSlots = {HEAD, PLACEHOLDER, EARS_SLOT, WRIST_SLOT, LEGS};
        String[] right = {"base", "coat", "armor", "gloves", "feet"};
        int[] rightSlots = {PLACEHOLDER, PLACEHOLDER, CHEST, PLACEHOLDER, FEET};
        for (int i = 0; i < 5; i++) {
            float y = 30 + i * 31;
            gearBox(g, left[i], leftSlots[i], 15, y, BOX, BOX);
            gearBox(g, right[i], rightSlots[i], 121, y, BOX, BOX);
        }
        // weapon positions (keys 1-3), the hand, and the holster that the pistol needs (plan 10)
        label(g, "primary", 15, 188);
        weaponBox(g, PRIMARY_MENU, 15, 188, 63, 28, "1");
        label(g, "hands", 82, 188);
        handsBox(g, 82, 188, 63, 28);
        label(g, "pistol", 15, 227);
        weaponBox(g, PISTOL_MENU, 15, 227, 41, 24, "2");
        label(g, "holster", 60, 227);
        slotBox(g, 60, 227, BOX, BOX);
        hits.add(new SlotHit(menu.getSlot(HOLSTER_SLOT), 60, 227, BOX, BOX));
        label(g, "melee", 105, 227);
        weaponBox(g, MELEE_MENU, 105, 227, 40, 24, "3");
        slingTag(g, 0, 15, 188, 28);
        slingTag(g, HotbarRoles.HAND_SLOT, 82, 188, 28);

        var weight = ClientWeightState.state();
        if (weight != null) {
            var line = Component.translatable("screen.apocalypse_firstlight.inventory.weight",
                    kg(weight.carriedMassGrams()), kg(weight.comfortCapacityGrams()));
            text(g, line, 15, 255.8F, 6, AflUiStyle.TEXT_DIM, 1);
        }
        var temperature = ClientTemperature.state();
        if (temperature != null) {
            var line = Component.translatable("screen.apocalypse_firstlight.inventory.comfort",
                    String.format(Locale.ROOT, "%.0f", temperature.comfortMin()));
            text(g, line, 145 - width(line, 6), 255.8F, 6, AflUiStyle.TEXT_DIM, 1);
        }
    }

    /**
     * The body map in place of the 3D model (docs/ui/body_silhouette_v1_plan.md): the player's skin front-on, dimmed, the
     * injured parts washed and badged; each part a hover target.
     */
    private void bodyMap(GuiGraphics g, float mx, float my) {
        bodyBadges = List.of();
        if (!(player instanceof AbstractClientPlayer client)) return;
        BodyPart hovered = null;
        for (var p : BodySilhouette.layout("slim".equals(client.getModelName()))) {
            hits.add(new BodyHit(p.part(), p.x(), p.y(), p.w(), p.h()));
            if (in(mx, my, p.x(), p.y(), p.w(), p.h())) hovered = p.part();
        }
        bodyBadges = BodySilhouette.draw(g, font, client, menu.getCarried().isEmpty() ? hovered : null);
    }

    private static String kg(long grams) {
        return String.format(Locale.ROOT, "%.1f", grams / 1000.0);
    }

    private void gearBox(GuiGraphics g, String name, int menuIndex, float x, float y, float w, float h) {
        label(g, "slot." + name, x, y);
        var labelText = Component.translatable("screen.apocalypse_firstlight.inventory.slot." + name);
        if (menuIndex == PLACEHOLDER) {
            AflUiShapes.box(g, x, y, w, h, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, 0.25F, AflUiStyle.LINE, 0.12F, 0, 0);
            hits.add(new PlaceholderHit(labelText, x, y, w, h));
            return;
        }
        slotBox(g, x, y, w, h);
        hits.add(new SlotHit(menu.getSlot(menuIndex), x, y, w, h));
    }

    private void weaponBox(GuiGraphics g, int menuIndex, float x, float y, float w, float h, String key) {
        slotBox(g, x, y, w, h);
        text(g, Component.literal(key), x + 2, y + 1.5F, 5, AflUiStyle.TEXT_DIM, 0.8F);
        hits.add(new SlotHit(menu.getSlot(menuIndex), x, y, w, h));
    }

    /** The hand: a fainter box; what is put here is held once the page closes (Loadout). */
    private void handsBox(GuiGraphics g, float x, float y, float w, float h) {
        AflUiShapes.box(g, x, y, w, h, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, 0.30F, AflUiStyle.LINE, 0.16F, 0, 0);
        if (!menu.getSlot(HAND_MENU).hasItem()) {
            var hint = Component.translatable("screen.apocalypse_firstlight.inventory.hands_hint");
            text(g, hint, x + (w - width(hint, 4.8F)) / 2, y + h / 2 - 2.4F, 4.8F, AflUiStyle.TEXT_DISABLED, 1);
        }
        hits.add(new SlotHit(menu.getSlot(HAND_MENU), x, y, w, h));
    }

    /** The "sling" tag in the bottom-left corner of a box whose gun has one; clicking it takes the sling off. */
    private void slingTag(GuiGraphics g, int hotbarIndex, float x, float y, float h) {
        if (!GunSling.has(player.getInventory().getItem(hotbarIndex))) return;
        var tag = Component.translatable("screen.apocalypse_firstlight.inventory.sling");
        float w = width(tag, 4.2F) + 3, tx = x + 1.5F, ty = y + h - 7;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 220);
        AflUiShapes.fill(g, tx, ty, w, 5.6F, 2, AflUiStyle.LINE, 0.14F);
        text(g, tag, tx + 1.5F, ty + 0.7F, 4.2F, 0xC9C4BA, 1);
        pose.popPose();
        hits.add(new SlingHit(hotbarIndex, tx, ty, w, 5.6F));
    }

    private void gridColumn(GuiGraphics g, PlayerGrids.View view, float mx, float my) {
        panel(g, B_X, B_Y, B_W, B_H);
        int x0 = Math.round(originX + B_X * scale), y0 = Math.round(originY + (B_Y + 1) * scale);
        int x1 = Math.round(originX + (B_X + B_W) * scale), y1 = Math.round(originY + (B_Y + B_H - 1) * scale);
        g.enableScissor(x0, y0, x1, y1);
        float top = B_Y + 11 - scroll;
        label(g, "pockets", BX, top);
        float y = top;
        var pockets = view.get(PlayerGrids.Target.POCKETS);
        if (pockets != null) y += grid(g, PlayerGrids.Target.POCKETS, pockets, BX, top) + 14;
        else y += CELL + 14;

        label(g, "rig", BX, y);
        slotBox(g, BX, y, GEAR_BOX, GEAR_BOX);
        addVisible(new SlotHit(menu.getSlot(RIG_SLOT), BX, y, GEAR_BOX, GEAR_BOX));
        var rig = view.get(PlayerGrids.Target.RIG);
        float rigH = rig == null ? 0 : grid(g, PlayerGrids.Target.RIG, rig, GX, y);
        y += Math.max(GEAR_BOX, rigH) + 14;

        // the worn rig's clips (user 2026-10-21): only while a rig with clips is worn, side by side
        boolean clips = false;
        for (int i = 0; i < com.antaurora.apofirstlight.equipment.AflEquipmentSlots.CLIPS; i++) {
            var slot = menu.getSlot(CLIP_SLOT + i);
            if (!slot.isActive()) continue;
            if (!clips) label(g, "clips", BX, y);
            clips = true;
            float cx = BX + i * (BOX + 4);
            slotBox(g, cx, y, BOX, BOX);
            addVisible(new SlotHit(slot, cx, y, BOX, BOX));
        }
        if (clips) y += BOX + 14;

        label(g, "backpack", BX, y);
        slotBox(g, BX, y, GEAR_BOX, GEAR_BOX);
        addVisible(new SlotHit(menu.getSlot(BACK_SLOT), BX, y, GEAR_BOX, GEAR_BOX));
        var backpack = view.get(PlayerGrids.Target.BACKPACK);
        float packH = backpack == null ? 0 : grid(g, PlayerGrids.Target.BACKPACK, backpack, GX, y);
        y += Math.max(GEAR_BOX, packH) + 6;
        g.disableScissor();

        scrollMax = Math.max(0, y + scroll - (B_Y + B_H));
        scroll = Math.min(scroll, scrollMax);
        if (scrollMax > 0) {
            float track = B_H - 8, thumb = Math.max(12, track * B_H / (B_H + scrollMax));
            float at = B_Y + 4 + (track - thumb) * (scroll / scrollMax);
            AflUiShapes.fill(g, B_X + B_W - 4, at, 2, thumb, 1, AflUiStyle.LINE, 0.35F);
        }
    }

    /** Draws a storage's sections side by side from (x, y); returns their height. */
    private float grid(GuiGraphics g, PlayerGrids.Target target, GridStorage storage, float x, float y) {
        float sx = x, height = 0;
        for (int i = 0; i < storage.layout().size(); i++) {
            var section = storage.layout().section(i);
            GridDrawing.cells(g, section.w(), section.h(), sx, y);
            addVisible(new CellHit(target, storage, i, sx, y, section.w() * CELL, section.h() * CELL));
            GridDrawing.reserved(g, storage, i, sx, y);
            for (var entry : storage.entries()) {
                if (entry.section() != i) continue;
                var size = storage.footprint(entry);
                drawEntry(g, entry.stack(), sx + entry.x() * CELL, y + entry.y() * CELL, size, entry.rotated(), false);
                if (target != PlayerGrids.Target.BACKPACK)
                    GridDrawing.bindBadge(g, font, entry.stack(), sx + (entry.x() + size.w()) * CELL, y + entry.y() * CELL);
            }
            height = Math.max(height, section.h() * CELL);
            sx += section.w() * CELL + 2;
        }
        return height;
    }

    private void drawEntry(GuiGraphics g, ItemStack stack, float x, float y, ItemSizes.Size size, boolean turned, boolean hover) {
        GridDrawing.entry(g, font, stack, x, y, size, turned, hover);
    }

    private void item(GuiGraphics g, ItemStack stack, float x, float y, float w, float h, boolean turned) {
        GridDrawing.item(g, font, stack, x, y, w, h, turned);
    }

    /** Quick bindings 4-9 (Loadout): what each key takes and how many are left in the pockets and rig; they store nothing. */
    private void bindPanel(GuiGraphics g) {
        panel(g, B_X, Q_Y, B_W, Q_H);
        text(g, Component.translatable("screen.apocalypse_firstlight.inventory.binds"), 160, 244, 6, AflUiStyle.TEXT_DIM, 1);
        var state = Loadout.client();
        var totals = LoadoutHud.totals(player, state.binds());
        int active = player.getInventory().selected == HotbarRoles.HAND_SLOT ? state.handBind() : -1;
        for (int i = 0; i < Loadout.BINDS; i++) {
            float x = 180 + i * 20.5F, y = 237, w = 19, h = 22;
            var kind = state.binds()[i];
            if (kind == null) AflUiShapes.box(g, x, y, w, h, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, 0.2F, AflUiStyle.LINE, 0.12F, 0, 0);
            else slotBox(g, x, y, w, h);
            if (i == active) AflUiShapes.fill(g, x + w / 2 - 4, y + h - 2, 8, 1.2F, 0.6F, AflUiStyle.ACCENT, 1);
            text(g, Component.literal(Integer.toString(4 + i)), x + 1.5F, y + 1.2F, 4.6F, AflUiStyle.TEXT_DISABLED, 1);
            if (kind != null) {
                int total = totals.getOrDefault(kind, 0);
                item(g, new ItemStack(kind), x, y + 1, w, h - 2, false);
                if (total == 0) AflUiShapes.fill(g, x, y, w, h, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, 0.55F);
                if (total != 1) {
                    var pose = g.pose();
                    pose.pushPose();
                    pose.translate(0, 0, 220);
                    var n = Component.literal(Integer.toString(total));
                    text(g, n, x + w - 1.2F - width(n, 5.2F), y + h - 6.4F, 5.2F, total == 0 ? AflUiStyle.TEXT_DISABLED : AflUiStyle.TEXT, 1);
                    pose.popPose();
                }
            }
            hits.add(new BindHit(i, x, y, w, h));
        }
    }

    private void craftingPanel(GuiGraphics g) {
        panel(g, C_X, C_Y, C_W, C_H);
        label(g, "crafting", C_X + 6, C_Y + 11);
        for (int i = 0; i < 4; i++) {
            float x = C_X + 6 + (i % 2) * 20, y = C_Y + 11 + (i / 2) * 20;
            slotBox(g, x, y, CELL, CELL);
            hits.add(new SlotHit(menu.getSlot(CRAFT + i), x, y, CELL, CELL));
        }
        text(g, Component.literal("→"), C_X + 55, C_Y + 26, 9, AflUiStyle.TEXT_DIM, 1);
        slotBox(g, C_X + 72, C_Y + 18, BOX, BOX);
        hits.add(new SlotHit(menu.getSlot(RESULT), C_X + 72, C_Y + 18, BOX, BOX));
        // no off-hand box: AFL has no off hand (2026-10-16); a stack still in it shows under overflow
    }

    /** Whatever lies where it cannot be put now: the unused vanilla slots (the off hand too), and grid stacks that fit nowhere. */
    private void overflowPanel(GuiGraphics g, PlayerGrids.View view) {
        List<Hit> items = new ArrayList<>();
        for (int i = HotbarRoles.USED; i < Inventory.INVENTORY_SIZE; i++) {
            var slot = menu.getSlot(i);
            if (slot.hasItem()) items.add(new SlotHit(slot, 0, 0, CELL, CELL));
        }
        if (menu.getSlot(OFFHAND).hasItem()) items.add(new SlotHit(menu.getSlot(OFFHAND), 0, 0, CELL, CELL));
        for (var target : PlayerGrids.Target.values()) {
            var storage = view.get(target);
            if (storage == null) continue;
            for (int i = 0; i < storage.overflow().size(); i++)
                items.add(new OverflowHit(target, i, storage.overflow().get(i), 0, 0, CELL, CELL));
        }
        overflowH = 0;
        if (items.isEmpty()) return;
        int columns = 7, rows = (items.size() + columns - 1) / columns;
        overflowH = 17 + rows * 20;
        panel(g, C_X, O_Y, C_W, overflowH);
        label(g, "overflow", C_X + 6, O_Y + 11);
        for (int i = 0; i < items.size(); i++) {
            float x = C_X + 6 + (i % columns) * 20, y = O_Y + 11 + (i / columns) * 20;
            AflUiShapes.box(g, x, y, CELL, CELL, 1.5F, BLOCKED, 0.18F, BLOCKED, 0.35F, 0, 0);
            Hit hit = items.get(i);
            if (hit instanceof SlotHit s) hits.add(new SlotHit(s.slot(), x, y, CELL, CELL));
            else if (hit instanceof OverflowHit o) {
                hits.add(new OverflowHit(o.target(), o.index(), o.stack(), x, y, CELL, CELL));
                item(g, o.stack(), x, y, CELL, CELL, false);
            }
        }
    }

    /**
     * What lies on the ground nearby (GroundItems.nearby, nearest first), one row each: icon, name, count. A stack on the
     * cursor goes down at the feet when clicked or dropped here.
     */
    private void nearbyPanel(GuiGraphics g, ItemStack carried, float mx, float my) {
        nearbyY = overflowH > 0 ? O_Y + overflowH + 7 : O_Y;
        nearbyH = Math.max(N_MIN_H, N_BOTTOM - nearbyY);
        panel(g, C_X, nearbyY, C_W, nearbyH);
        if (!carried.isEmpty() && in(mx, my, C_X, nearbyY, C_W, nearbyH))
            AflUiShapes.box(g, C_X + 1, nearbyY + 1, C_W - 2, nearbyH - 2, AflUiStyle.RADIUS_PANEL, FREE, 0.08F, FREE, 0.6F, 0, 0);
        label(g, "nearby", C_X + 6, nearbyY + 11);
        var items = GroundItems.nearby(player);
        int visible = Math.max(1, (int) ((nearbyH - 13) / N_ROW));
        nearbyRows = items.size();
        nearbyScroll = Math.max(0, Math.min(nearbyScroll, Math.max(0, items.size() - visible)));
        if (items.isEmpty()) {
            text(g, Component.translatable("screen.apocalypse_firstlight.inventory.nearby_empty"), C_X + 8, nearbyY + 16, 6,
                    AflUiStyle.TEXT_DISABLED, 1);
            return;
        }
        int first = (int) nearbyScroll;
        for (int i = first; i < Math.min(items.size(), first + visible); i++) {
            var entity = items.get(i);
            var stack = entity.getItem();
            float y = nearbyY + 11 + (i - first) * N_ROW, x = C_X + 4, w = C_W - 8;
            boolean hover = carried.isEmpty() && in(mx, my, x, y, w, N_ROW - 1);
            AflUiShapes.box(g, x, y, w, N_ROW - 1, AflUiStyle.RADIUS_TILE, hover ? AflUiStyle.BACK_HOVER : AflUiStyle.BACK,
                    hover ? 0.6F : 0.3F, AflUiStyle.LINE, hover ? AflUiStyle.LINE_HOVER_ALPHA : 0.12F, 0, 0);
            float icon = x + 1;
            item(g, stack.copyWithCount(1), icon, y + 0.5F, CELL, CELL, false);
            var count = stack.getCount() > 1 ? Component.literal("×" + stack.getCount()) : Component.empty();
            float countW = width(count, 5.6F);
            float room = w - CELL - 8 - countW;
            String name = font.plainSubstrByWidth(stack.getHoverName().getString(), (int) (room * font.lineHeight / 6));
            text(g, Component.literal(name), icon + CELL + 4, y + 6.6F, 6, AflUiStyle.TEXT, 1);
            if (stack.getCount() > 1) text(g, count, x + w - 4 - countW, y + 7, 5.6F, AflUiStyle.TEXT_DIM, 1);
            hits.add(new GroundHit(entity.getId(), stack, x, y, w, N_ROW - 1));
        }
        if (items.size() > visible) {
            float track = nearbyH - 15, bar = Math.max(8, track * visible / items.size());
            float at = nearbyY + 11 + (track - bar) * nearbyScroll / (items.size() - visible);
            AflUiShapes.fill(g, C_X + C_W - 3, at, 1.2F, bar, 0.6F, AflUiStyle.LINE, 0.5F);
        }
    }

    /** Every vanilla slot's item in its box (after the boxes, so icons sit above them). */
    private void drawSlots(GuiGraphics g, float mx, float my) {
        for (Hit hit : hits) {
            if (!(hit instanceof SlotHit s) || !s.slot().hasItem()) continue;
            if (!visible(hit)) continue;
            boolean scrolled = s.slot().index == RIG_SLOT || s.slot().index == BACK_SLOT;
            if (scrolled) scissorGrid(g);
            var stack = s.slot().getItem();
            if (s.slot().container instanceof Inventory inventory && PlayerStorageCapacity.isOverflow(inventory, s.slot().getContainerSlot())
                    && s.slot().getContainerSlot() < Inventory.getSelectionSize())
                AflUiShapes.box(g, s.x(), s.y(), s.w(), s.h(), AflUiStyle.RADIUS_TILE, BLOCKED, 0.22F, 0, 0, 0, 0);
            item(g, stack, s.x(), s.y(), s.w(), s.h(), false);
            if (scrolled) g.disableScissor();
        }
    }

    private void scissorGrid(GuiGraphics g) {
        g.enableScissor(Math.round(originX + B_X * scale), Math.round(originY + (B_Y + 1) * scale),
                Math.round(originX + (B_X + B_W) * scale), Math.round(originY + (B_Y + B_H - 1) * scale));
    }

    private void drawHover(GuiGraphics g, @Nullable Hit hover, ItemStack carried, float mx, float my) {
        if (hover == null) return;
        if (hover instanceof CellHit cell) {
            scissorGrid(g);
            if (carried.isEmpty()) {
                var entry = entryAt(cell, mx, my);
                if (entry != null) drawEntry(g, entry.stack(), cell.x() + entry.x() * CELL, cell.y() + entry.y() * CELL,
                        cell.storage().footprint(entry), entry.rotated(), true);
                else AflUiShapes.box(g, cell.x() + col(cell, mx) * CELL, cell.y() + row(cell, my) * CELL, CELL, CELL, 1.5F,
                        AflUiStyle.BACK_HOVER, 0.6F, AflUiStyle.LINE, AflUiStyle.LINE_HOVER_ALPHA, 0, 0);
            } else if (GunSling.isSling(carried)) {
                var entry = entryAt(cell, mx, my);
                if (entry != null) {
                    int colour = GunSling.canFit(entry.stack()) ? FREE : BLOCKED;
                    var size = cell.storage().footprint(entry);
                    AflUiShapes.box(g, cell.x() + entry.x() * CELL, cell.y() + entry.y() * CELL, size.w() * CELL, size.h() * CELL,
                            2, colour, 0.28F, colour, 0.7F, 0, 0);
                }
            } else {
                var plan = plan(cell, carried, mx, my);
                int colour = plan.kind() == GridActions.Kind.MERGE || plan.free() ? FREE : plan.swap() ? SWAP : BLOCKED;
                var size = ItemSizes.of(carried, true).turned(rotated);
                float x = cell.x() + plan.x() * CELL, y = cell.y() + plan.y() * CELL;
                if (plan.kind() == GridActions.Kind.MERGE) {
                    var entry = entryAt(cell, mx, my);
                    size = cell.storage().footprint(entry);
                    x = cell.x() + entry.x() * CELL;
                    y = cell.y() + entry.y() * CELL;
                }
                AflUiShapes.box(g, x, y, size.w() * CELL, size.h() * CELL, 2, colour, 0.28F, colour, 0.7F, 0, 0);
            }
            g.disableScissor();
        } else if (hover instanceof BindHit || hover instanceof SlingHit) {
            AflUiShapes.box(g, hover.x(), hover.y(), hover.w(), hover.h(), AflUiStyle.RADIUS_TILE, AflUiStyle.BACK_HOVER, 0.25F,
                    AflUiStyle.LINE, AflUiStyle.LINE_HOVER_ALPHA, 0, 0);
        } else if (hover instanceof SlotHit || hover instanceof OverflowHit) {
            boolean scrolled = hover instanceof SlotHit s && (s.slot().index == RIG_SLOT || s.slot().index == BACK_SLOT);
            if (scrolled) scissorGrid(g);
            int colour = AflUiStyle.LINE;
            if (hover instanceof SlotHit s && !carried.isEmpty())
                colour = GunSling.isSling(carried) && slingTarget(s.slot()) >= 0 || s.slot().mayPlace(carried) ? FREE : BLOCKED;
            AflUiShapes.box(g, hover.x(), hover.y(), hover.w(), hover.h(), AflUiStyle.RADIUS_TILE, AflUiStyle.BACK_HOVER, 0.25F,
                    colour, AflUiStyle.LINE_HOVER_ALPHA, 0, 0);
            if (scrolled) g.disableScissor();
        }
    }

    private void drawCarried(GuiGraphics g, ItemStack carried, float mx, float my) {
        var size = ItemSizes.of(carried, true).turned(rotated);
        float w = size.w() * CELL, h = size.h() * CELL;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 232);
        item(g, carried, mx - w / 2, my - h / 2, w, h, rotated);
        pose.popPose();
    }

    private void tooltip(GuiGraphics g, @Nullable Hit hover, int mouseX, int mouseY) {
        if (hover instanceof SlotHit s && s.slot().hasItem()) {
            g.renderTooltip(font, s.slot().getItem(), mouseX, mouseY);
        } else if (hover instanceof SlotHit s) {
            List<Component> lines = emptyHint(s.slot().index);
            if (lines != null) hint(g, lines, mouseX, mouseY);
        } else if (hover instanceof OverflowHit o) {
            g.renderTooltip(font, o.stack(), mouseX, mouseY);
        } else if (hover instanceof GroundHit ground) {
            g.renderTooltip(font, ground.stack(), mouseX, mouseY);
        } else if (hover instanceof CellHit cell) {
            var entry = entryAt(cell, vx(mouseX), vy(mouseY));
            if (entry != null) g.renderTooltip(font, entry.stack(), mouseX, mouseY);
        } else if (hover instanceof BindHit b) {
            var kind = Loadout.client().binds()[b.index()];
            if (kind == null) hint(g, List.of(Component.translatable("screen.apocalypse_firstlight.inventory.bind_empty")), mouseX, mouseY);
            else hint(g, List.of(new ItemStack(kind).getHoverName(),
                    Component.translatable("screen.apocalypse_firstlight.inventory.bind_unbind")), mouseX, mouseY);
        } else if (hover instanceof SlingHit) {
            hint(g, List.of(Component.translatable("screen.apocalypse_firstlight.inventory.sling_off")), mouseX, mouseY);
        } else if (hover instanceof BodyHit b) {
            BodySilhouette.tooltip(g, font, player, b.part(), mouseX, mouseY, width, height);
        } else if (hover instanceof PlaceholderHit p) {
            hint(g, List.of(p.label(), Component.translatable("screen.apocalypse_firstlight.inventory.wear_hint")), mouseX, mouseY);
        }
    }

    /**
     * An empty gear, weapon or hand box: its name and what goes in it. Crafting cells and grid cells say nothing (they are
     * plainly cells).
     */
    @Nullable
    private static List<Component> emptyHint(int menuIndex) {
        String name, line;
        switch (menuIndex) {
            case HEAD -> { name = "slot.head"; line = "wear_hint"; }
            case EARS_SLOT -> { name = "slot.ears"; line = "wear_hint"; }
            case WRIST_SLOT -> { name = "slot.watch"; line = "wear_hint"; }
            case LEGS -> { name = "slot.legs"; line = "wear_hint"; }
            case CHEST -> { name = "slot.armor"; line = "wear_hint"; }
            case FEET -> { name = "slot.feet"; line = "wear_hint"; }
            case RIG_SLOT -> { name = "rig"; line = "wear_hint"; }
            case BACK_SLOT -> { name = "backpack"; line = "wear_hint"; }
            case HOLSTER_SLOT -> { name = "holster"; line = "wear_hint"; }
            case CLIP_SLOT, CLIP_SLOT + 1 -> { name = "clips"; line = "clip_hint"; }
            case PRIMARY_MENU -> { name = "primary"; line = "weapon_hint"; }
            case PISTOL_MENU -> { name = "pistol"; line = "weapon_hint"; }
            case MELEE_MENU -> { name = "melee"; line = "weapon_hint"; }
            case HAND_MENU -> { name = "hands"; line = "hands_hint"; }
            default -> { return null; }
        }
        return List.of(Component.translatable("screen.apocalypse_firstlight.inventory." + name),
                Component.translatable("screen.apocalypse_firstlight.inventory." + line));
    }

    /**
     * A text hint in the page's own style (panel colours, round corners) at the vanilla tooltip's size and place, so it
     * reads like the item tooltips next to it (those keep vanilla's layout; GridClient#tooltipColour gives them the same
     * colours). First line bright, the rest dim.
     */
    private void hint(GuiGraphics g, List<Component> lines, int mouseX, int mouseY) {
        int w = 0;
        for (Component line : lines) w = Math.max(w, font.width(line));
        int h = lines.size() * 10 - 2, pad = 4;
        int x = mouseX + 12, y = mouseY - 12;
        if (x + w + pad > width) x = Math.max(pad, mouseX - 16 - w);
        if (y + h + pad > height) y = height - h - pad;
        y = Math.max(pad, y);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 400);
        AflUiShapes.box(g, x - pad, y - pad, w + 2 * pad, h + 2 * pad, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, 0.94F,
                AflUiStyle.LINE, AflUiStyle.LINE_ALPHA, AflUiStyle.SHADOW_ALPHA, 0);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x, y + i * 10, AflUiStyle.argb(i == 0 ? AflUiStyle.TEXT : AflUiStyle.TEXT_DIM, 1), false);
        }
        pose.popPose();
    }

    // ---------------------------------------------------------------- small drawing helpers

    private void panel(GuiGraphics g, float x, float y, float w, float h) {
        AflUiShapes.box(g, x, y, w, h, AflUiStyle.RADIUS_PANEL, AflUiStyle.BACK, AflUiStyle.BACK_ALPHA, AflUiStyle.LINE,
                AflUiStyle.LINE_ALPHA, AflUiStyle.SHADOW_ALPHA, 0);
    }

    private void slotBox(GuiGraphics g, float x, float y, float w, float h) {
        AflUiShapes.box(g, x, y, w, h, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, 0.45F, AflUiStyle.LINE, 0.22F, 0, 0);
    }

    /** A small caption just above a box. */
    private void label(GuiGraphics g, String key, float x, float boxY) {
        text(g, Component.translatable("screen.apocalypse_firstlight.inventory." + key), x, boxY - 6.2F, LABEL, AflUiStyle.TEXT_DIM, 1);
    }

    private void text(GuiGraphics g, Component text, float x, float y, float size, int rgb, float alpha) {
        GridDrawing.text(g, font, text, x, y, size, rgb, alpha);
    }

    private float width(Component text, float size) {
        return GridDrawing.width(font, text, size);
    }

    // ---------------------------------------------------------------- hit testing

    private void addVisible(Hit hit) {
        hits.add(hit);
    }

    /** The gear slots in the scrolling grid column: rig, its clips, backpack. */
    private static boolean scrolls(int index) {
        return index == RIG_SLOT || index == BACK_SLOT || index == CLIP_SLOT || index == CLIP_SLOT + 1;
    }

    /** Grid column hits are clipped to its scroll area. */
    private boolean visible(Hit hit) {
        boolean inGrid = hit instanceof CellHit || hit instanceof SlotHit s && scrolls(s.slot().index);
        if (!inGrid) return true;
        return hit.y() + hit.h() > B_Y && hit.y() < B_Y + B_H;
    }

    @Nullable
    private Hit hitAt(float mx, float my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (mx < hit.x() || my < hit.y() || mx >= hit.x() + hit.w() || my >= hit.y() + hit.h()) continue;
            boolean inGrid = hit instanceof CellHit || hit instanceof SlotHit s && scrolls(s.slot().index);
            if (inGrid && (my < B_Y || my >= B_Y + B_H)) continue;
            return hit;
        }
        return null;
    }

    private static int col(CellHit cell, float mx) { return (int) Math.floor((mx - cell.x()) / CELL); }
    private static int row(CellHit cell, float my) { return (int) Math.floor((my - cell.y()) / CELL); }

    @Nullable
    private static GridStorage.Entry entryAt(CellHit cell, float mx, float my) {
        return cell.storage().at(cell.section(), col(cell, mx), row(cell, my));
    }

    /** What a click with the carried stack would do here: merge onto the entry under the mouse, or place / swap. */
    private GridDrawing.Plan plan(CellHit cell, ItemStack carried, float mx, float my) {
        return GridDrawing.plan(cell.storage(), cell.section(), cell.x(), cell.y(), carried, rotated, mx, my);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) return true;
        float mx = vx(mouseX), my = vy(mouseY);
        Hit hit = hitAt(mx, my);
        if (hit instanceof BodyHit) return true;
        var carried = menu.getCarried();
        if (button == 0 && carried.isEmpty() && in(mx, my, charX, CHAR_Y, charW, CHAR_H)) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            toCharacter = true;
            minecraft.setScreen(new AflCharacterScreen());
            return true;
        }
        boolean shift = hasShiftDown();
        pendingUntil = 0;
        pressX = mouseX;
        pressY = mouseY;
        pressPicked = carried.isEmpty() && !shift && picksUp(hit, mx, my);
        if (!carried.isEmpty() && in(mx, my, C_X, nearbyY, C_W, nearbyH)) {
            send(GridActions.Kind.GROUND_DROP, 0, 0, button, 0, false, 0);
            return true;
        }
        if (hit instanceof GroundHit ground) {
            if (shift) send(GridActions.Kind.GROUND_QUICK, 0, 0, GroundItems.AUTO, 0, false, ground.entityId());
            else send(GridActions.Kind.GROUND_PICK, 0, 0, button, 0, false, ground.entityId());
            return true;
        }
        if (hit instanceof SlingHit sling) {
            if (carried.isEmpty()) send(GridActions.Kind.SLING, GridActions.SLOT_TARGET, 0, 0, 0, false, sling.hotbarIndex());
            return true;
        }
        if (hit instanceof BindHit bind) {
            if (button == 1) send(GridActions.Kind.UNBIND, 0, 0, 0, 0, false, bind.index());
            return true;
        }
        if (hit instanceof CellHit cell && GunSling.isSling(carried)) {
            var entry = entryAt(cell, mx, my);
            if (entry != null && GunSling.canFit(entry.stack())) {
                send(GridActions.Kind.SLING, cell.target().ordinal(), cell.section(), entry.x(), entry.y(), false, 0);
                return true;
            }
        }
        if (hit instanceof SlotHit s && GunSling.isSling(carried) && slingTarget(s.slot()) >= 0) {
            send(GridActions.Kind.SLING, GridActions.SLOT_TARGET, 0, 0, 0, false, slingTarget(s.slot()));
            return true;
        }
        if (hit instanceof CellHit cell) {
            int target = cell.target().ordinal();
            if (carried.isEmpty()) {
                var entry = entryAt(cell, mx, my);
                if (entry == null) return true;
                var kind = shift ? GridActions.Kind.QUICK : GridActions.Kind.PICK;
                if (kind == GridActions.Kind.PICK) rotated = entry.rotated();
                send(kind, target, cell.section(), entry.x(), entry.y(), false, button);
            } else {
                var plan = plan(cell, carried, mx, my);
                if (plan.kind() == GridActions.Kind.MERGE || plan.free() || plan.swap() && button == 0)
                    send(plan.kind(), target, cell.section(), plan.x(), plan.y(), rotated, button);
            }
            return true;
        }
        if (hit instanceof OverflowHit o) {
            if (carried.isEmpty()) send(GridActions.Kind.OVERFLOW_TAKE, o.target().ordinal(), 0, 0, 0, false, o.index());
            return true;
        }
        if (hit instanceof SlotHit s) {
            var slot = s.slot();
            if (shift && carried.isEmpty() && slot.container instanceof Inventory && slot.hasItem()) {
                send(GridActions.Kind.QUICK_FROM_SLOT, 0, 0, 0, 0, false, slot.getContainerSlot());
            } else {
                slotClicked(slot, slot.index, button, shift ? ClickType.QUICK_MOVE : ClickType.PICKUP);
            }
            return true;
        }
        if (hit instanceof PlaceholderHit) return true;
        if (!carried.isEmpty() && !insidePanels(mx, my)) slotClicked(null, OUTSIDE, button, ClickType.PICKUP);
        return true;
    }

    /** A plain click here with an empty cursor takes something onto it (so letting go elsewhere is a drag). */
    private boolean picksUp(@Nullable Hit hit, float mx, float my) {
        if (hit instanceof CellHit cell) return entryAt(cell, mx, my) != null;
        if (hit instanceof SlotHit s) return s.slot().hasItem();
        return hit instanceof OverflowHit || hit instanceof GroundHit;
    }

    /** The hotbar index of a weapon box / hands box holding a long gun a sling fits, or -1. */
    private int slingTarget(Slot slot) {
        if (!(slot.container instanceof Inventory) || slot.getContainerSlot() >= HotbarRoles.USED) return -1;
        return GunSling.canFit(slot.getItem()) ? slot.getContainerSlot() : -1;
    }

    private boolean insidePanels(float mx, float my) {
        return in(mx, my, A_X, A_Y, A_W, A_H) || in(mx, my, B_X, B_Y, B_W, B_H) || in(mx, my, B_X, Q_Y, B_W, Q_H)
                || in(mx, my, C_X, C_Y, C_W, C_H) || overflowH > 0 && in(mx, my, C_X, O_Y, C_W, overflowH)
                || in(mx, my, C_X, nearbyY, C_W, nearbyH);
    }

    private static boolean in(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private void send(GridActions.Kind kind, int target, int section, int x, int y, boolean turned, int extra) {
        GridPackets.send(new GridPackets.Action(kind.ordinal(), target, section, x, y, turned, extra));
    }

    /** Drag and drop: a press that picked something up and a release elsewhere puts it down there (all of it). */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!pressPicked) return true;
        pressPicked = false;
        if (Math.hypot(vx(mouseX) - vx(pressX), vy(mouseY) - vy(pressY)) < DRAG) return true;
        if (!menu.getCarried().isEmpty()) mouseClicked(mouseX, mouseY, 0);
        else {
            pendingUntil = System.nanoTime() + DRAG_WAIT_NANOS;
            pendingX = mouseX;
            pendingY = mouseY;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        float mx = vx(mouseX), my = vy(mouseY);
        if (in(mx, my, B_X, B_Y, B_W, B_H) && scrollMax > 0) {
            scroll = Math.max(0, Math.min(scrollMax, scroll - (float) delta * CELL));
            return true;
        }
        if (in(mx, my, C_X, nearbyY, C_W, nearbyH) && nearbyRows > 0) {
            nearbyScroll = Math.max(0, nearbyScroll - (float) Math.signum(delta));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        var key = InputConstants.getKey(keyCode, scanCode);
        if (GridClient.ROTATE.isActiveAndMatches(key) && !menu.getCarried().isEmpty()) {
            rotated = !rotated;
            return true;
        }
        var mouse = minecraft.mouseHandler;
        double mouseX = mouse.xpos() * width / minecraft.getWindow().getScreenWidth();
        double mouseY = mouse.ypos() * height / minecraft.getWindow().getScreenHeight();
        float mx = vx(mouseX), my = vy(mouseY);
        var hover = hitAt(mx, my);
        int number = -1;
        for (int i = 0; i < Inventory.getSelectionSize(); i++)
            if (minecraft.options.keyHotbarSlots[i].isActiveAndMatches(key)) number = i;
        if (hover instanceof CellHit cell && menu.getCarried().isEmpty()) {
            var entry = entryAt(cell, mx, my);
            if (entry != null) {
                // keys 1-3: into that weapon position; 4-9: bind the kind (pockets and rig only)
                if (number >= 0 && number < HotbarRoles.HAND_SLOT) {
                    send(GridActions.Kind.HOTBAR_KEY, cell.target().ordinal(), cell.section(), entry.x(), entry.y(), false, number);
                    return true;
                }
                if (number >= HotbarRoles.HAND_SLOT) {
                    if (cell.target() != PlayerGrids.Target.BACKPACK)
                        send(GridActions.Kind.BIND, cell.target().ordinal(), cell.section(), entry.x(), entry.y(), false,
                                number - HotbarRoles.HAND_SLOT);
                    return true;
                }
                if (minecraft.options.keyDrop.isActiveAndMatches(key)) {
                    send(GridActions.Kind.DROP, cell.target().ordinal(), cell.section(), entry.x(), entry.y(), false,
                            hasControlDown() ? 1 : 0);
                    return true;
                }
            }
        }
        // keys 4-9 over vanilla slots would swap into the hand or the unused hotbar indices: not on this page
        if (number >= HotbarRoles.HAND_SLOT) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
