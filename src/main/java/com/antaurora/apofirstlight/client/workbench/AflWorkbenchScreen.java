package com.antaurora.apofirstlight.client.workbench;

import com.antaurora.apofirstlight.building.BuildingPackets;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.client.workbench.WorkbenchRecipeView.Category;
import com.antaurora.apofirstlight.inventory.grid.ItemSizes;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.weight.StackMassCalculator;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The workbench's crafting page, version B (user pick 2026-10-21; docs/gameplay/afl_workbench_v1_design.md 16, concept
 * design/ui/afl_workbench_ui_v1/workbench_ui_b.png). Tabs "背包 / 制作" at the top (the inventory key switches); on the
 * left the learned recipes folded by category, each category with how many are learned (greyed when none), each recipe
 * with whether it can be made now and a "新" mark until looked at; in the middle the selected item turning on drag, its
 * name, category, source, weight, size, crafting time, how many the player carries, and the item's own description; on
 * the right the materials (have / need, what is short), the tool ("无" when none), the quantity and the craft button
 * (one click starts it and the ring fills by itself, item after item; a second click, another recipe or closing the
 * page stops it; user 2026-10-21, no holding), and the to-do preview (F adds the recipe to the HUD's
 * to-do panel). No separate knowledge page (user 2026-10-21): learned recipes simply appear here. Recipes the survivor
 * has not learned never show. Where materials are taken from and where to find them belongs in the wiki, not here.
 * <p>
 * Drawn on its own 480 x 270 canvas at the largest whole scale the window takes, like the inventory page. The real recipes are
 * building hammer B0's test recipes (WorkbenchRecipes); the other categories show greyed.
 */
public final class AflWorkbenchScreen extends Screen {
    private static final float W = 480, H = 270;
    private static final float L_X = 16, L_Y = 34, L_W = 126, L_H = 222;
    private static final float C_X = 150, C_W = 180;
    private static final float R_X = 337, R_W = 125, R_Y = 34;
    private static final float LIST_TOP = 56, LIST_BOTTOM = 244, CAT_H = 14, ROW_H = 13.5F;
    private static final float PREVIEW_CX = C_X + C_W / 2, PREVIEW_CY = 113, PREVIEW_SIZE = 74;
    private static final long STATUS_NANOS = 3_000_000_000L;
    /** Key cap scales: the header's (beside 6-7 high text) and the key bar's (beside 5.4). */
    private static final float HEADER_CAP = 0.62F, BAR_CAP = 0.56F;
    /** What is short under the materials: its size and at most this many lines, wrapped to the panel. */
    private static final float MISSING_SIZE = 5;
    private static final int MISSING_LINES = 3;

    private enum Kind { TAB_INVENTORY, CLOSE, SEARCH, CATEGORY, RECIPE, MINUS, PLUS, CRAFT, MATERIAL, TOOL, PREVIEW, LIST }
    private record Hit(Kind kind, float x, float y, float w, float h, int index, @Nullable Object data) {}

    /** Kept between openings: the selection, the open categories, the model's turn. */
    @Nullable private static ResourceLocation selected;
    private static final Set<Category> open = EnumSet.noneOf(Category.class);
    private static float yaw = -30;

    private final BlockPos bench;
    private final List<Hit> hits = new ArrayList<>();
    private float scale = 1, originX, originY;
    private String search = "";
    private boolean searchFocused;
    private float listScroll, listScrollMax;
    private int quantity = 1;
    /** Crafting: the current item started at (nanos), items done in this run; 0 = not crafting. */
    private long craftStart;
    private int craftDone;
    private boolean dragging;
    /** The materials popup (right-click on a material line): its index, or -1. */
    private int optionsOf = -1;
    @Nullable private Component status;
    private long statusUntil;

    public AflWorkbenchScreen(BlockPos bench) {
        super(Component.translatable("block.apocalypse_firstlight.wooden_workbench"));
        this.bench = bench;
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
        if (minecraft.player == null || !WorkbenchClient.atBench(minecraft.player, bench)) onClose();
    }

    @Override
    public void onClose() {
        var recipe = current();
        if (recipe != null) WorkbenchRecipes.seen(recipe);
        WorkbenchClient.unlink();
        super.onClose();
    }

    private float vx(double mouseX) { return (float) ((mouseX - originX) / scale); }
    private float vy(double mouseY) { return (float) ((mouseY - originY) / scale); }

    // ---------------------------------------------------------------- data

    private List<WorkbenchRecipeView> known() {
        return WorkbenchRecipes.known(minecraft.player);
    }

    private List<WorkbenchRecipeView> inCategory(Category category) {
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<WorkbenchRecipeView> list = new ArrayList<>();
        for (var r : known()) {
            if (r.category() != category) continue;
            if (!q.isEmpty() && !r.result().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) continue;
            list.add(r);
        }
        list.sort((a, b) -> a.result().getHoverName().getString().compareTo(b.result().getHoverName().getString()));
        return list;
    }

    @Nullable
    private WorkbenchRecipeView current() {
        return WorkbenchRecipes.find(minecraft.player, selected);
    }

    private void select(WorkbenchRecipeView recipe) {
        var old = current();
        if (old != null && !old.id().equals(recipe.id())) WorkbenchRecipes.seen(old);
        if (!recipe.id().equals(selected)) {
            selected = recipe.id();
            quantity = 1;
            stopCrafting();
            optionsOf = -1;
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        hits.clear();
        float mx = vx(mouseX), my = vy(mouseY);
        var recipe = current();
        updateCrafting(recipe);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(originX, originY, 0);
        pose.scale(scale, scale, 1);
        header(g, mx, my);
        list(g, mx, my, recipe);
        if (recipe != null) {
            details(g, recipe, mx, my);
            materials(g, recipe, mx, my);
        } else {
            empty(g);
        }
        keyBar(g, recipe);
        if (WorkbenchRecipes.preview())
            text(g, Component.translatable("screen.apocalypse_firstlight.workbench.sample.banner"), L_X, 260, 4.6F, AflUiStyle.ACCENT, 0.9F);
        if (recipe != null && optionsOf >= 0 && optionsOf < recipe.materials().size()) optionsPopup(g, recipe);
        pose.popPose();

        Hit hover = hitAt(mx, my);
        if (hover != null && recipe != null && optionsOf < 0) {
            if (hover.kind == Kind.MATERIAL) g.renderTooltip(font, recipe.materials().get(hover.index).shown(net.minecraft.Util.getMillis()), mouseX, mouseY);
            else if (hover.kind == Kind.TOOL && hover.index < recipe.tools().size()) g.renderTooltip(font, recipe.tools().get(hover.index), mouseX, mouseY);
        }
    }

    private void header(GuiGraphics g, float mx, float my) {
        text(g, title, L_X + 2, 10, 9, AflUiStyle.TEXT, 1);
        AflUiShapes.fill(g, L_X, 28, W - 2 * L_X, 0.4F, 0, AflUiStyle.LINE, AflUiStyle.LINE_ALPHA);
        long now = System.nanoTime();
        // tabs: 背包 (with the inventory key's cap before it) and 制作, the active one bright with the accent underline
        Component inv = Component.translatable("screen.apocalypse_firstlight.workbench.tab.inventory");
        Component craft = Component.translatable("screen.apocalypse_firstlight.workbench.tab.craft");
        float size = 7, invW = width(inv, size), craftW = width(craft, size);
        float invX = 222 - invW / 2, craftX = 262 - craftW / 2;
        boolean invHover = in(mx, my, invX - 4, 8, invW + 8, 16);
        text(g, inv, invX, 12, size, invHover ? AflUiStyle.TEXT : AflUiStyle.TEXT_DIM, 1);
        hits.add(new Hit(Kind.TAB_INVENTORY, invX - 4, 8, invW + 8, 16, 0, null));
        var cap = AflKeyHint.of(minecraft.options.keyInventory);
        cap(g, cap, invX - 5 - cap.width(font) * HEADER_CAP, 12.2F, HEADER_CAP, true, now);
        text(g, craft, craftX, 12, size, AflUiStyle.TEXT, 1);
        AflUiShapes.fill(g, craftX - 1, 22.5F, craftW + 2, 1.2F, 0.5F, AflUiStyle.ACCENT, 1);
        // close: Esc
        Component close = Component.translatable("screen.apocalypse_firstlight.workbench.close");
        float closeW = width(close, 6), right = W - L_X - 2;
        boolean closeHover = in(mx, my, right - closeW - 30, 8, closeW + 30, 16);
        text(g, close, right - closeW, 12.5F, 6, closeHover ? AflUiStyle.TEXT : AflUiStyle.TEXT_DIM, 1);
        plainCap(g, "Esc", right - closeW - 4, 12.2F);
        hits.add(new Hit(Kind.CLOSE, right - closeW - 30, 8, closeW + 30, 16, 0, null));
    }

    private void list(GuiGraphics g, float mx, float my, @Nullable WorkbenchRecipeView recipe) {
        panel(g, L_X, L_Y, L_W, L_H);
        // search box
        float sx = L_X + 6, sy = L_Y + 6, sw = L_W - 12, sh = 11;
        AflUiShapes.box(g, sx, sy, sw, sh, AflUiStyle.RADIUS_TILE, 0x000000, 0.25F, AflUiStyle.LINE,
                searchFocused ? AflUiStyle.LINE_HOVER_ALPHA : 0.18F, 0, 0);
        hits.add(new Hit(Kind.SEARCH, sx, sy, sw, sh, 0, null));
        if (search.isEmpty() && !searchFocused)
            text(g, Component.translatable("screen.apocalypse_firstlight.workbench.search"), sx + 4, sy + 3.2F, 5.2F, AflUiStyle.TEXT_DISABLED, 1);
        else {
            Component typed = Component.literal(search);
            text(g, typed, sx + 4, sy + 3.2F, 5.2F, AflUiStyle.TEXT, 1);
            if (searchFocused && System.currentTimeMillis() / 500 % 2 == 0)
                AflUiShapes.fill(g, sx + 4.5F + width(typed, 5.2F), sy + 2.5F, 0.5F, 6, 0, AflUiStyle.TEXT, 0.9F);
        }

        boolean searching = !search.isBlank();
        // rows, scrolled and clipped to the list area
        float y = LIST_TOP - listScroll;
        g.enableScissor(sx(L_X), sy(LIST_TOP), sx(L_X + L_W), sy(LIST_BOTTOM));
        hits.add(new Hit(Kind.LIST, L_X, LIST_TOP, L_W, LIST_BOTTOM - LIST_TOP, 0, null));
        for (Category category : Category.values()) {
            var recipes = inCategory(category);
            int learned = 0;
            for (var r : known()) if (r.category() == category) learned++;
            if (searching && recipes.isEmpty()) continue;
            boolean expanded = searching || open.contains(category);
            boolean visible = y + CAT_H > LIST_TOP && y < LIST_BOTTOM;
            if (visible) {
                boolean hover = learned > 0 && in(mx, my, L_X + 4, y, L_W - 8, CAT_H) && my >= LIST_TOP && my < LIST_BOTTOM;
                int rgb = learned == 0 ? AflUiStyle.TEXT_DISABLED : hover ? AflUiStyle.TEXT : 0xDCD8D0;
                arrow(g, L_X + 9, y + 7, expanded && learned > 0, rgb);
                text(g, Component.translatable(category.key()), L_X + 14, y + 4, 6.4F, rgb, 1);
                Component count = learned > 0
                        ? Component.translatable("screen.apocalypse_firstlight.workbench.category.known", learned)
                        : Component.translatable("screen.apocalypse_firstlight.workbench.category.none");
                text(g, count, L_X + L_W - 8 - width(count, 4.8F), y + 5, 4.8F, learned > 0 ? AflUiStyle.TEXT_DIM : AflUiStyle.TEXT_DISABLED, 1);
                AflUiShapes.fill(g, L_X + 6, y + CAT_H - 0.6F, L_W - 12, 0.4F, 0, AflUiStyle.LINE, 0.14F);
                if (learned > 0) hits.add(new Hit(Kind.CATEGORY, L_X + 4, y, L_W - 8, CAT_H, category.ordinal(), category));
            }
            y += CAT_H;
            if (!expanded) continue;
            for (var r : recipes) {
                if (y + ROW_H > LIST_TOP && y < LIST_BOTTOM) recipeRow(g, r, y, mx, my, recipe != null && recipe.id().equals(r.id()));
                y += ROW_H;
            }
        }
        g.disableScissor();
        listScrollMax = Math.max(0, y + listScroll - LIST_BOTTOM);
        listScroll = Math.min(listScroll, listScrollMax);
        if (listScrollMax > 0) {
            float track = LIST_BOTTOM - LIST_TOP, thumb = Math.max(12, track * track / (track + listScrollMax));
            float ty = LIST_TOP + (track - thumb) * listScroll / listScrollMax;
            AflUiShapes.fill(g, L_X + L_W - 3.2F, ty, 1.2F, thumb, 0.6F, AflUiStyle.LINE, 0.35F);
        }
        text(g, Component.translatable("screen.apocalypse_firstlight.workbench.hidden"), L_X + 6, L_Y + L_H - 9, 4.6F, AflUiStyle.TEXT_DISABLED, 1);
    }

    private void recipeRow(GuiGraphics g, WorkbenchRecipeView r, float y, float mx, float my, boolean chosen) {
        float x = L_X + 4, w = L_W - 8;
        boolean hover = in(mx, my, x, y, w, ROW_H) && my >= LIST_TOP && my < LIST_BOTTOM;
        if (chosen) {
            AflUiShapes.box(g, x, y + 0.5F, w, ROW_H - 1, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK_HOVER, AflUiStyle.BACK_HOVER_ALPHA,
                    AflUiStyle.LINE, AflUiStyle.LINE_HOVER_ALPHA, 0, 0);
            AflUiShapes.fill(g, x, y + 1.5F, 1.2F, ROW_H - 3, 0.5F, AflUiStyle.ACCENT, 1);
        } else if (hover) {
            AflUiShapes.fill(g, x, y + 0.5F, w, ROW_H - 1, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK_HOVER, 0.5F);
        }
        icon(g, r.result(), x + 4, y + 1.75F, 10);
        Component name = r.result().getHoverName();
        text(g, name, x + 18, y + 4.3F, 5.6F, AflUiStyle.TEXT, 1);
        if (WorkbenchRecipes.isFresh(r))
            text(g, Component.translatable("screen.apocalypse_firstlight.workbench.new"), x + 20 + width(name, 5.6F), y + 4.8F, 4.4F, AflUiStyle.ACCENT, 1);
        boolean can = WorkbenchRecipes.craftable(minecraft.player, r) > 0 && WorkbenchRecipes.toolPresent(minecraft.player, r);
        Component state = Component.translatable("screen.apocalypse_firstlight.workbench." + (can ? "craftable" : "short"));
        text(g, state, x + w - 4 - width(state, 4.6F), y + 5, 4.6F, can ? AflUiStyle.TEXT_DIM : AflUiStyle.WARN, 1);
        hits.add(new Hit(Kind.RECIPE, x, y, w, ROW_H, 0, r));
    }

    /** Nothing selected: one quiet line in the middle (none learned, or pick one on the left). */
    private void empty(GuiGraphics g) {
        boolean none = known().isEmpty();
        Component line = Component.translatable("screen.apocalypse_firstlight.workbench." + (none ? "none" : "select"));
        text(g, line, PREVIEW_CX - width(line, 6.4F) / 2, PREVIEW_CY - 3, 6.4F, AflUiStyle.TEXT_DISABLED, 1);
    }

    private void details(GuiGraphics g, WorkbenchRecipeView r, float mx, float my) {
        Component name = r.result().getCount() > 1
                ? Component.translatable("screen.apocalypse_firstlight.workbench.result_count", r.result().getHoverName(), r.result().getCount())
                : r.result().getHoverName();
        text(g, name, C_X + 2, 37, 12, AflUiStyle.TEXT, 1);
        text(g, Component.translatable(r.category().key()), C_X + 2, 52, 5.4F, AflUiStyle.TEXT_DIM, 1);
        boolean fresh = WorkbenchRecipes.isFresh(r);
        Component source = Component.translatable("screen.apocalypse_firstlight.workbench.source" + (fresh ? ".fresh" : ""), r.source());
        text(g, source, C_X + 2, 60, 4.8F, fresh ? AflUiStyle.ACCENT : AflUiStyle.TEXT_DIM, 1);
        // a furniture piece left half done on this bench (WorkbenchBlockEntity's job): which, and how far
        var half = WorkbenchWork.benchJob(bench);
        if (half != null) {
            Component line = Component.translatable("screen.apocalypse_firstlight.workbench.half_built", WorkbenchWork.jobName(half),
                    WorkbenchWork.stepLine(half, WorkbenchWork.carpentry()));
            boolean other = r.furniture() && !half.recipe().equals(r.id());
            text(g, fit(line, 4.8F, C_W - 4), C_X + 2, 67, 4.8F, other ? AflUiStyle.WARN : AflUiStyle.ACCENT, 1);
        }
        // a finished piece still on the top: no furniture is started until it is taken off (doc 12.3)
        var done = WorkbenchWork.benchFinished(bench);
        if (half == null && !done.isEmpty() && r.furniture()) {
            Component line = Component.translatable("screen.apocalypse_firstlight.workbench.finished", done.getHoverName());
            text(g, fit(line, 4.8F, C_W - 4), C_X + 2, 67, 4.8F, AflUiStyle.WARN, 1);
        }

        // the model, turned by dragging
        hits.add(new Hit(Kind.PREVIEW, C_X, 68, C_W, 90, 0, null));
        model(g, r.result());

        AflUiShapes.fill(g, C_X + 2, 162, C_W - 4, 0.4F, 0, AflUiStyle.LINE, AflUiStyle.LINE_ALPHA);
        var size = ItemSizes.of(r.result(), true);
        long grams = StackMassCalculator.mass(r.result().copyWithCount(1)).grams();
        String[][] props = {
                {"weight", String.format(Locale.ROOT, "%.2f kg", grams / 1000.0)},
                {"size", I18n.get("screen.apocalypse_firstlight.workbench.prop.size_value", size.w(), size.h())},
                {"time", I18n.get("screen.apocalypse_firstlight.workbench.prop.time_value", seconds(r.furniture()
                        ? com.antaurora.apofirstlight.cabin.FurnitureWork.estimate(r.steps(), WorkbenchWork.carpentry()) : r.ticks()))},
                {"owned", Integer.toString(WorkbenchRecipes.carried(minecraft.player, r.result().getItem()))}};
        for (int i = 0; i < props.length; i++) {
            float x = C_X + 2 + i * 44;
            text(g, Component.translatable("screen.apocalypse_firstlight.workbench.prop." + props[i][0]), x, 167, 4.6F, AflUiStyle.TEXT_DIM, 1);
            text(g, Component.literal(props[i][1]), x, 174, 6.4F, AflUiStyle.TEXT, 1);
        }
        Component description = description(r.result());
        if (description != null) wrap(g, description, C_X + 2, 189, C_W - 4, 5.6F, AflUiStyle.TEXT, 4);
    }

    /** The item's own description, the same line its tooltip shows (tooltip.<ns>.<id>.description), when it has one. */
    @Nullable
    private static Component description(ItemStack stack) {
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return null;
        String key = "tooltip." + id.getNamespace() + "." + id.getPath() + ".description";
        return I18n.exists(key) ? Component.translatable(key) : null;
    }

    private static String seconds(int ticks) {
        float s = ticks / 20F;
        return s == Math.floor(s) ? Integer.toString((int) s) : String.format(Locale.ROOT, "%.1f", s);
    }

    /**
     * The result as a large 3D item: its GUI pose, turned about the model's own upright axis by {@code yaw} (the GUI
     * pose's x tilt is undone around the turn, so a block spins like on a turntable instead of wobbling). A Mesh item's
     * inventory picture is its 64 px icon (forge:separate_transforms, gui = item/generated), which blown up to this size
     * reads as pixels: its base model is the mesh, carrying the icon's view as its gui transform
     * (tools/build-material-meshes-v1.mjs), so that one is drawn instead, front-lit like the icon.
     */
    private void model(GuiGraphics g, ItemStack stack) {
        var renderer = minecraft.getItemRenderer();
        BakedModel model = renderer.getModel(stack, minecraft.level, minecraft.player, 0);
        BakedModel base = model.applyTransform(ItemDisplayContext.NONE, new PoseStack(), false);
        boolean mesh = base != model && base.isCustomRenderer() && base.getTransforms().gui != ItemTransform.NO_TRANSFORM;
        if (mesh) model = base;
        float tilt = model.getTransforms().gui.rotation.x();
        float k = PREVIEW_SIZE / 16;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(PREVIEW_CX, PREVIEW_CY, 200);
        pose.mulPose(Axis.XP.rotationDegrees(-tilt));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(tilt));
        pose.scale(k, k, k);
        pose.translate(0, 0, -150);   // renderItem pushes 150 forward: keep the turn about the centre
        if (mesh) {
            // GuiGraphics.renderItem(stack, -8, -8) with the base model in place of the icon
            pose.translate(0, 0, 150);
            pose.mulPoseMatrix(new Matrix4f().scaling(1, -1, 1));
            pose.scale(16, 16, 16);
            Lighting.setupForFlatItems();
            renderer.render(stack, ItemDisplayContext.GUI, false, pose, g.bufferSource(), 0xF000F0, OverlayTexture.NO_OVERLAY, model);
            g.flush();
            Lighting.setupFor3DItems();
        } else {
            g.renderItem(stack, -8, -8);
        }
        pose.popPose();
    }

    private void materials(GuiGraphics g, WorkbenchRecipeView r, float mx, float my) {
        var player = minecraft.player;
        float x = R_X + 6, right = R_X + R_W - 6;
        int lines = r.materials().size();
        float matsH = Math.max(1, lines) * 19;
        // the panel as tall as what it holds: the materials, what is short (wrapped), a row per tool, the quantity, the button
        List<Component> missing = new ArrayList<>();
        for (var m : r.materials()) {
            int have = WorkbenchRecipes.carried(player, m);
            if (have < m.count()) missing.add(Component.translatable("screen.apocalypse_firstlight.workbench."
                    + (m.unit() == com.antaurora.apofirstlight.cabin.WorkbenchCrafting.Unit.COUNT ? "missing_item" : "missing_amount"), m.name(), m.amount(m.count() - have)));
        }
        Component short_ = null;
        int shortLines = 0;
        if (!missing.isEmpty()) {
            Component joined = Component.empty();
            for (int i = 0; i < missing.size(); i++) {
                if (i > 0) joined = joined.copy().append(Component.translatable("screen.apocalypse_firstlight.workbench.list_sep"));
                joined = joined.copy().append(missing.get(i));
            }
            short_ = Component.translatable("screen.apocalypse_firstlight.workbench.missing", joined);
            shortLines = Math.min(MISSING_LINES, font.split(short_, (int) ((R_W - 12) / (MISSING_SIZE / font.lineHeight))).size());
        }
        float shortH = short_ == null ? 2 : 3 + shortLines * MISSING_SIZE * 1.45F;
        float toolsH = 13 + 13 * Math.max(1, r.tools().size());
        float h = 18 + matsH + shortH + 5 + toolsH + 4 + 18 + 15 + 6;
        panel(g, R_X, R_Y, R_W, h);
        text(g, Component.translatable("screen.apocalypse_firstlight.workbench.materials"), x, R_Y + 6, 7, AflUiStyle.TEXT, 1);
        Component haveNeed = Component.translatable("screen.apocalypse_firstlight.workbench.have_need");
        text(g, haveNeed, right - width(haveNeed, 4.8F), R_Y + 7.5F, 4.8F, AflUiStyle.TEXT_DIM, 1);
        float y = R_Y + 18;
        for (int i = 0; i < lines; i++) {
            var m = r.materials().get(i);
            int have = WorkbenchRecipes.carried(player, m), need = m.count() * Math.max(1, quantity);
            boolean ok = have >= need;
            boolean hover = in(mx, my, x - 2, y - 1, R_W - 8, 18);
            if (hover) AflUiShapes.fill(g, x - 2, y - 1, R_W - 8, 18, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK_HOVER, 0.5F);
            slot(g, x, y, 16);
            icon(g, m.shown(net.minecraft.Util.getMillis()), x + 2, y + 2, 12);
            // a metered line in its unit, the need times the quantity: "0.65 / 0.3 L", "172 / 48" (furniture bench work 8)
            String needText = m.amount(need);
            Component count = Component.literal(m.amount(have).replace(" L", "") + " / " + needText);
            float countW = width(count, 6.4F);
            text(g, fit(m.name(), 5.6F, right - countW - 3 - (x + 21)), x + 21, y + 4, 5.6F, AflUiStyle.TEXT, 1);
            if (m.options().size() > 1)
                text(g, Component.translatable("screen.apocalypse_firstlight.workbench.options_count", m.options().size()), x + 21, y + 10.5F, 4.2F, AflUiStyle.TEXT_DISABLED, 1);
            text(g, count, right - countW, y + 4.5F, 6.4F, ok ? AflUiStyle.TEXT : AflUiStyle.WARN, 1);
            hits.add(new Hit(Kind.MATERIAL, x - 2, y - 1, R_W - 8, 18, i, null));
            y += 19;
        }
        if (short_ != null) wrap(g, short_, x, y + 1, R_W - 12, MISSING_SIZE, AflUiStyle.WARN, MISSING_LINES);
        y += shortH;
        AflUiShapes.fill(g, x - 2, y + 1, R_W - 8, 0.4F, 0, AflUiStyle.LINE, 0.2F);
        y += 5;
        // tool
        text(g, Component.translatable("screen.apocalypse_firstlight.workbench.tool"), x, y, 6.4F, AflUiStyle.TEXT, 1);
        if (r.tools().isEmpty())
            text(g, Component.translatable("screen.apocalypse_firstlight.workbench.tool.none"), x, y + 10, 5.2F, AflUiStyle.TEXT_DIM, 1);
        for (int i = 0; i < r.tools().size(); i++) {   // a row each, every one needed
            ItemStack tool = r.tools().get(i);
            boolean have = WorkbenchRecipes.carried(player, tool.getItem()) > 0;
            float ty = y + 13 * i;
            icon(g, tool, x, ty + 8.5F, 10);
            text(g, fit(tool.getHoverName(), 5.2F, right - (x + 13)), x + 13, ty + 11, 5.2F, have ? AflUiStyle.TEXT_DIM : AflUiStyle.WARN, 1);
            hits.add(new Hit(Kind.TOOL, x - 2, ty + 7, R_W - 8, 13, i, null));
        }
        y += toolsH;
        AflUiShapes.fill(g, x - 2, y - 1, R_W - 8, 0.4F, 0, AflUiStyle.LINE, 0.2F);
        y += 4;
        // quantity (furniture: one at a time, it stays on the top, doc 12.3)
        int max = WorkbenchRecipes.craftable(player, r), count = count(r);
        text(g, Component.translatable("screen.apocalypse_firstlight.workbench.quantity"), x, y + 4, 5.6F, AflUiStyle.TEXT_DIM, 1);
        float bx = x + 30;
        boolean minusHover = in(mx, my, bx, y, 12, 12), plusHover = in(mx, my, bx + 34, y, 12, 12), more = count < 99 && !r.furniture();
        AflUiDraw.button(g, bx, y, 12, 12, minusHover, false, count > 1, 1);
        AflUiShapes.fill(g, bx + 3.5F, y + 5.6F, 5, 0.8F, 0.3F, count > 1 ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1);
        hits.add(new Hit(Kind.MINUS, bx, y, 12, 12, 0, null));
        Component q = Component.literal(Integer.toString(count));
        text(g, q, bx + 23 - width(q, 7) / 2, y + 2.6F, 7, AflUiStyle.TEXT, 1);
        AflUiDraw.button(g, bx + 34, y, 12, 12, plusHover && more, false, more, 1);
        AflUiShapes.fill(g, bx + 37.5F, y + 5.6F, 5, 0.8F, 0.3F, more ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1);
        AflUiShapes.fill(g, bx + 39.6F, y + 3.5F, 0.8F, 5, 0.3F, more ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1);
        hits.add(new Hit(Kind.PLUS, bx + 34, y, 12, 12, 0, null));
        Component canMake = Component.translatable("screen.apocalypse_firstlight.workbench.max", max);
        text(g, canMake, right - width(canMake, 4.8F), y + 4, 4.8F, max > 0 ? AflUiStyle.TEXT_DIM : AflUiStyle.TEXT_DISABLED, 1);
        y += 18;
        // the craft button: one click starts, the ring fills per item; clicking again stops
        boolean able = canCraft(r);
        boolean craftHover = in(mx, my, x - 1, y, R_W - 10, 15);
        AflUiDraw.button(g, x - 1, y, R_W - 10, 15, craftHover && able, craftStart != 0, able, 1);
        float progress = craftProgress(r);
        ring(g, x + 8, y + 7.5F, 4.2F, progress, able);
        Component label = Component.translatable("screen.apocalypse_firstlight.workbench." + (craftStart != 0 ? "crafting"
                        : occupied(r) ? "occupied" : discardArmed(r) ? "craft_discard" : "craft"), craftDone + 1, count);
        text(g, label, x + 17, y + 4.8F, 5.6F, able ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1);
        hits.add(new Hit(Kind.CRAFT, x - 1, y, R_W - 10, 15, 0, null));

        todoPreview(g, r, R_Y + h + 6);
    }

    /** What F does: the HUD panel as it will look, under a short caption (or that it is already there). */
    private void todoPreview(GuiGraphics g, WorkbenchRecipeView r, float top) {
        boolean pinned = r.id().equals(WorkbenchRecipes.pinned());
        float caption = 5;
        Component line = Component.translatable("screen.apocalypse_firstlight.workbench.todo." + (pinned ? "added" : "preview"));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(R_X, top, 0);
        float s = 0.62F;
        pose.scale(s, s, 1);
        WorkbenchClient.drawTodo(g, minecraft.player, r, R_W / s, (caption + 4) / s, pinned ? 1 : 0.75F);
        pose.popPose();
        text(g, line, R_X + 2, top, caption, pinned ? AflUiStyle.ACCENT : AflUiStyle.TEXT_DIM, 1);
    }

    private void keyBar(GuiGraphics g, @Nullable WorkbenchRecipeView r) {
        float y = 240, h = 16;
        AflUiShapes.box(g, C_X, y, C_W, h, AflUiStyle.RADIUS_PANEL, AflUiStyle.BACK, AflUiStyle.BACK_ALPHA, AflUiStyle.LINE,
                AflUiStyle.LINE_ALPHA, AflUiStyle.SHADOW_ALPHA, 0);
        long now = System.nanoTime();
        if (status != null && now < statusUntil) {
            text(g, status, C_X + 8, y + 5.2F, 5.4F, AflUiStyle.ACCENT, 1);
            return;
        }
        boolean on = r != null;
        float x = C_X + 7;
        x = keyPair(g, AflKeyHint.MOUSE_LEFT, craftStart != 0 ? "key.cancel" : "key.craft", x, y + 2.5F,
                on && (craftStart != 0 || canCraft(r)), now) + 8;
        boolean pinned = r != null && r.id().equals(WorkbenchRecipes.pinned());
        x = keyPair(g, AflKeyHint.of(WorkbenchClient.TODO), pinned ? "key.todo_remove" : "key.todo", x, y + 2.5F, on, now) + 8;
        keyPair(g, AflKeyHint.MOUSE_RIGHT, "key.options", x, y + 2.5F, on && !r.materials().isEmpty(), now);
    }

    private float keyPair(GuiGraphics g, AflKeyHint key, String label, float x, float y, boolean enabled, long now) {
        float w = cap(g, key, x, y + 1.5F, BAR_CAP, enabled, now);
        Component text = Component.translatable("screen.apocalypse_firstlight.workbench." + label);
        text(g, text, x + w + 3, y + 2.8F, 5.4F, enabled ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1);
        return x + w + 3 + width(text, 5.4F);
    }

    /** A key cap scaled to this canvas's small text (caps are drawn 11 high with 9-high text); returns its width. */
    private float cap(GuiGraphics g, AflKeyHint key, float x, float y, float s, boolean enabled, long now) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(s, s, 1);
        key.draw(g, font, 0, 0, enabled ? 1 : 0.45F, now);
        pose.popPose();
        return key.width(font) * s;
    }

    /** Right-click on a material line: the items that count for it, with how many the player carries of each. */
    private void optionsPopup(GuiGraphics g, WorkbenchRecipeView r) {
        var m = r.materials().get(optionsOf);
        float w = 120, rowH = 13, h = 14 + m.options().size() * rowH + 4;
        float x = R_X - w - 4, y = Math.min(R_Y + 18 + optionsOf * 19, H - h - 4);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 300);
        AflUiShapes.box(g, x, y, w, h, AflUiStyle.RADIUS_PANEL, AflUiStyle.BACK, 0.94F, AflUiStyle.LINE, AflUiStyle.LINE_HOVER_ALPHA,
                AflUiStyle.SHADOW_ALPHA, 0);
        text(g, Component.translatable("screen.apocalypse_firstlight.workbench.options"), x + 6, y + 5, 5.4F, AflUiStyle.TEXT, 1);
        float yy = y + 14;
        for (ItemStack option : m.options()) {
            icon(g, option, x + 6, yy + 1, 10);
            text(g, option.getHoverName(), x + 19, yy + 3.6F, 5.2F, AflUiStyle.TEXT, 1);
            Component have = Component.literal(m.unit() == com.antaurora.apofirstlight.cabin.WorkbenchCrafting.Unit.COUNT
                    ? Integer.toString(WorkbenchRecipes.carried(minecraft.player, option.getItem()))
                    : m.amount(com.antaurora.apofirstlight.inventory.CarriedItems.amount(minecraft.player, option.getItem())));
            text(g, have, x + w - 6 - width(have, 5.2F), yy + 3.6F, 5.2F, AflUiStyle.TEXT_DIM, 1);
            yy += rowH;
        }
        pose.popPose();
    }

    // ---------------------------------------------------------------- crafting

    private boolean canCraft(@Nullable WorkbenchRecipeView r) {
        return r != null && !occupied(r) && WorkbenchRecipes.craftable(minecraft.player, r) >= 1 && WorkbenchRecipes.toolPresent(minecraft.player, r);
    }

    /** A furniture recipe while a finished piece stands on the top (doc 12.3: taken off first). */
    private boolean occupied(WorkbenchRecipeView r) {
        return r.furniture() && !WorkbenchWork.benchFinished(bench).isEmpty();
    }

    /** How many to make: the quantity set, a furniture piece one at a time (doc 12.3). */
    private int count(WorkbenchRecipeView r) {
        return r.furniture() ? 1 : quantity;
    }

    private float craftProgress(WorkbenchRecipeView r) {
        if (craftStart == 0) return 0;
        return Math.min(1, (System.nanoTime() - craftStart) / (r.ticks() * 50_000_000F));
    }

    /** Once started: one item per full ring; at the end of the count, or when the materials or the tool run out, it stops. */
    private void updateCrafting(@Nullable WorkbenchRecipeView r) {
        if (craftStart == 0) return;
        if (r == null || !canCraft(r)) {
            stopCrafting();
            return;
        }
        if (craftProgress(r) < 1) return;
        // a real recipe asks the server for one item (cabin/WorkbenchCrafting checks and takes the materials from the
        // body; the drawers and nearby chests come with the recipe rework); a sample recipe only says it made nothing
        if (r.sample()) setStatus(Component.translatable("screen.apocalypse_firstlight.workbench.sample.done"));
        else AflNetwork.buildingToServer(new BuildingPackets.Craft(bench, r.id()));
        craftDone++;
        craftStart = craftDone < quantity ? System.nanoTime() : 0;
        if (craftStart == 0) craftDone = 0;
    }

    /** The craft button asked once to throw away another recipe's half-built piece: a second click in 5 s does. */
    @Nullable private ResourceLocation discardFor;
    private long discardAt;

    private boolean discardArmed(WorkbenchRecipeView r) {
        return r.id().equals(discardFor) && System.nanoTime() - discardAt < 5_000_000_000L;
    }

    private void stopCrafting() {
        craftStart = 0;
        craftDone = 0;
    }

    private void setStatus(Component text) {
        status = text;
        statusUntil = System.nanoTime() + STATUS_NANOS;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float mx = vx(mouseX), my = vy(mouseY);
        Hit hit = hitAt(mx, my);
        var recipe = current();
        if (optionsOf >= 0) {
            optionsOf = -1;
            if (button == 1) return true;
        }
        searchFocused = hit != null && hit.kind == Kind.SEARCH;
        if (hit == null) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 1) {
            if (hit.kind == Kind.MATERIAL && recipe != null) {
                optionsOf = hit.index;
                click();
            }
            return true;
        }
        if (button != 0) return true;
        switch (hit.kind) {
            case TAB_INVENTORY -> { click(); WorkbenchClient.toInventory(); }
            case CLOSE -> { click(); onClose(); }
            case CATEGORY -> {
                Category c = (Category) hit.data;
                if (!open.remove(c)) open.add(c);
                click();
            }
            case RECIPE -> {
                select((WorkbenchRecipeView) hit.data);
                click();
            }
            case MINUS -> { if (quantity > 1) { quantity -= hasShiftDown() ? Math.min(10, quantity - 1) : 1; click(); } }
            case PLUS -> { if (recipe == null || !recipe.furniture()) { quantity = Math.min(99, quantity + (hasShiftDown() ? 10 : 1)); click(); } }
            case CRAFT -> {
                if (craftStart != 0) {
                    stopCrafting();
                    click();
                } else if (canCraft(recipe) && recipe.work() != null && !recipe.sample()) {
                    // made at the bench: the page closes and the player works on the top (WorkbenchWork). Another
                    // recipe's half-built piece on the bench is thrown away only on a second click (the button says so)
                    click();
                    var half = WorkbenchWork.benchJob(bench);
                    boolean other = recipe.furniture() && half != null && !half.recipe().equals(recipe.id());
                    if (other && !discardArmed(recipe)) {
                        discardFor = recipe.id();
                        discardAt = System.nanoTime();
                        setStatus(Component.translatable("screen.apocalypse_firstlight.workbench.discard_ask", WorkbenchWork.jobName(half)));
                    } else {
                        discardFor = null;
                        WorkbenchWork.start(bench, recipe, Math.min(count(recipe), WorkbenchRecipes.craftable(minecraft.player, recipe)), other);
                    }
                } else if (canCraft(recipe)) {
                    craftStart = System.nanoTime();
                    craftDone = 0;
                    click();
                }
            }
            case PREVIEW -> dragging = true;
            default -> {}
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            yaw += (float) (dragX / scale) * 2.2F;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        float mx = vx(mouseX), my = vy(mouseY);
        if (in(mx, my, L_X, LIST_TOP, L_W, LIST_BOTTOM - LIST_TOP)) {
            listScroll = Math.max(0, Math.min(listScrollMax, listScroll - (float) delta * 14));
            return true;
        }
        Hit hit = hitAt(mx, my);
        if (hit != null && (hit.kind == Kind.MINUS || hit.kind == Kind.PLUS) && (current() == null || !current().furniture())) {
            quantity = Math.max(1, Math.min(99, quantity + (delta > 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) search = hasControlDown() ? "" : search.substring(0, search.offsetByCodePoints(search.length(), -1));
                listScroll = 0;
                return true;
            }
            if (Screen.isPaste(keyCode)) {
                search = (search + minecraft.keyboardHandler.getClipboard()).replaceAll("[\\r\\n]", "");
                if (search.length() > 32) search = search.substring(0, 32);
                return true;
            }
            return true;
        }
        var key = InputConstants.getKey(keyCode, scanCode);
        if (minecraft.options.keyInventory.isActiveAndMatches(key)) {
            WorkbenchClient.toInventory();
            return true;
        }
        if (WorkbenchClient.TODO.isActiveAndMatches(key)) {
            var recipe = current();
            if (recipe != null) {
                WorkbenchRecipes.togglePin(recipe);
                click();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (!searchFocused || c < ' ' || search.length() >= 32) return super.charTyped(c, modifiers);
        search += c;
        listScroll = 0;
        return true;
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.35F));
    }

    // ---------------------------------------------------------------- small helpers

    @Nullable
    private Hit hitAt(float mx, float my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!in(mx, my, h.x, h.y, h.w, h.h)) continue;
            if ((h.kind == Kind.RECIPE || h.kind == Kind.CATEGORY) && (my < LIST_TOP || my >= LIST_BOTTOM)) continue;
            if (h.kind == Kind.LIST) continue;
            return h;
        }
        return null;
    }

    private static boolean in(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private int sx(float x) { return (int) Math.floor(originX + x * scale); }
    private int sy(float y) { return (int) Math.floor(originY + y * scale); }

    private void panel(GuiGraphics g, float x, float y, float w, float h) {
        AflUiShapes.box(g, x, y, w, h, AflUiStyle.RADIUS_PANEL, AflUiStyle.BACK, AflUiStyle.BACK_ALPHA, AflUiStyle.LINE,
                AflUiStyle.LINE_ALPHA, AflUiStyle.SHADOW_ALPHA, 0);
    }

    private void slot(GuiGraphics g, float x, float y, float size) {
        AflUiShapes.box(g, x, y, size, size, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, 0.45F, AflUiStyle.LINE, 0.22F, 0, 0);
    }

    private void icon(GuiGraphics g, ItemStack stack, float x, float y, float size) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(size / 16, size / 16, 1);
        g.renderItem(stack, 0, 0);
        pose.popPose();
    }

    /** A plain key cap with fixed text (Esc), its right edge at {@code right}. */
    private void plainCap(GuiGraphics g, String key, float right, float y) {
        float s = HEADER_CAP, w = Math.max(AflKeyHint.HEIGHT, font.width(key) + 6) * s, x = right - w;
        AflUiShapes.fill(g, x, y, w, AflKeyHint.HEIGHT * s, AflUiStyle.RADIUS_KEY, AflUiStyle.KEY_BACK, AflUiStyle.KEY_BACK_ALPHA);
        text(g, Component.literal(key), x + (w - width(Component.literal(key), 9 * s)) / 2, y + 2 * s, 9 * s, AflUiStyle.KEY_TEXT, 1);
    }

    /** A small fold arrow: pointing right when closed, down when open. */
    private void arrow(GuiGraphics g, float cx, float cy, boolean down, int rgb) {
        for (int i = 0; i < 3; i++) {
            float len = 3 - i * 1.2F;
            if (down) AflUiShapes.fill(g, cx - len / 2, cy - 1 + i * 0.8F, len, 0.8F, 0, rgb, 1);
            else AflUiShapes.fill(g, cx - 1 + i * 0.8F, cy - len / 2, 0.8F, len, 0, rgb, 1);
        }
    }

    /** A thin progress ring: the track dim, the done part in the accent. */
    private void ring(GuiGraphics g, float cx, float cy, float r, float progress, boolean enabled) {
        int steps = 40;
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2 * (i + 0.5) / steps - Math.PI / 2;
            boolean done = (i + 0.5F) / steps <= progress;
            float px = (float) (cx + Math.cos(a) * r), py = (float) (cy + Math.sin(a) * r);
            AflUiShapes.fill(g, px - 0.55F, py - 0.55F, 1.1F, 1.1F, 0.55F, done ? AflUiStyle.ACCENT : AflUiStyle.LINE,
                    done ? 1 : enabled ? 0.35F : 0.18F);
        }
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

    /** Wrapped text, at most {@code maxLines} lines. */
    private void wrap(GuiGraphics g, Component text, float x, float y, float w, float size, int rgb, int maxLines) {
        float s = size / font.lineHeight;
        List<FormattedCharSequence> lines = font.split(text, (int) (w / s));
        var pose = g.pose();
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            pose.pushPose();
            pose.translate(x, y + i * size * 1.45F, 0);
            pose.scale(s, s, 1);
            g.drawString(font, lines.get(i), 0, 0, AflUiStyle.argb(rgb, 1), false);
            pose.popPose();
        }
    }

    /** The text, cut to {@code w} wide with an ellipsis when it is wider (a long name beside a count). */
    private Component fit(Component text, float size, float w) {
        if (width(text, size) <= w) return text;
        int px = (int) (w / (size / font.lineHeight)) - font.width("…");
        return Component.literal(font.plainSubstrByWidth(text.getString(), Math.max(0, px)) + "…").withStyle(text.getStyle());
    }

    private float width(Component text, float size) {
        return font.width(text) * size / font.lineHeight;
    }
}
