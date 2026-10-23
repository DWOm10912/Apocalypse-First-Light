package com.antaurora.apofirstlight.client.inspect;

import com.antaurora.apofirstlight.client.GeigerCounterItemRenderer;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflKeyHintPanel;
import com.antaurora.apofirstlight.client.ui.AflItemStrip;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.client.ui.AflUiTween;
import com.antaurora.apofirstlight.inspect.InspectActions;
import com.antaurora.apofirstlight.inspect.InspectActions.Action;
import com.antaurora.apofirstlight.inspect.InspectPackets;
import com.antaurora.apofirstlight.inventory.grid.GridSource;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.antaurora.apofirstlight.item.Battery9vItem;
import com.antaurora.apofirstlight.item.GeigerCounterItem;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.weapon.client.NativeGunInput;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Item inspect V1 (docs/gameplay/item_inspect_v1_design.md 2-4): any item, large in the middle of the game (no pause),
 * drawn in the first-person hand pass so the world's light and the shader light it as they light the gun in its field
 * attachment view (user 2026-10-22; third person falls back to the screen's own lights); it flies in from its cell (or
 * the hand) in 0.25 s and back out on
 * closing. Left drag turns it, middle drag pans, the wheel zooms, the reset key squares it up; top right the key hints,
 * under them the item's name and its one-line description (user 2026-10-22: Resident Evil style, no stats). Hotspots (InspectProfile) light up under the mouse when they face the camera;
 * a click plays the action while the server decides (InspectPackets), and the item keeps the new state only when the
 * server agrees. Being hurt or the item leaving its place shuts the screen; shutting it with the battery cover off puts
 * the cover back first.
 */
public final class ItemInspectScreen extends Screen {
    private static final float FOV = 30, DISTANCE = 3.0F, FLY_SECONDS = 0.25F, RESET_SECONDS = 0.25F;
    private static final double MOTION_RESPONSE = .10, ZOOM_RESPONSE = .15, DEGREES_PER_PIXEL = .55, PITCH_LIMIT = 80,
            DRAG_THRESHOLD = 4, MIN_ZOOM = .60, MAX_ZOOM = 2.50, ZOOM_STEP = 1.12;
    /** Share of the screen height a unit cube fills at zoom 1 (the fly-in starts at the cell's height against it). */
    private static final float VIEW_SHARE = (float) (1 / (2 * DISTANCE * Math.tan(Math.toRadians(FOV / 2))));
    /** After the server agrees, how long the screen keeps its own picture while the item's new tag is on its way (ns). */
    private static final long SYNC_GRACE = 1_000_000_000L;
    private static final long NOTICE_NS = 2_500_000_000L;
    /** How far in front of the eye the item's centre is in the hand pass (the gun's field view holds it at 1.15). */
    private static final float HAND_DEPTH = 1.15F;
    /** The name and description panel: its size against the GUI, its text's wrap width (unscaled), its gap under the hints. */
    private static final float INFO_SCALE = 0.7F, INFO_WRAP = 220, INFO_GAP = 4;

    @Nullable private static ItemInspectScreen current;
    private static int requests;
    /** Counts frames (RenderTickEvent START): the hand pass and the screen in one frame share the view's update. */
    private static long frames;
    private long viewFrame = -1, handFrame = -1;

    @Nullable private final Screen parent;
    private final int source;
    private final Item item;
    /** Where it flies in from: centre and height in GUI px, or NaN for the hand (bottom right). */
    private final float fromX, fromY, fromH;

    private final AflKeyHintPanel hints = new AflKeyHintPanel();
    private final AflItemStrip strip = new AflItemStrip();
    private long opened, closing;
    private float health = -1;

    private double yaw, pitch, zoom = 1, panX, panY, targetYaw, targetPitch, targetZoom = 1, targetPanX, targetPanY;
    private long resetStart, lastFrame;
    private double resetYaw, resetPitch, resetZoom, resetX, resetY, resetEndYaw;
    private int dragButton = -1;
    private double startX, startY, lastX, lastY;
    private boolean dragging;

    private final Matrix4f projection = new Matrix4f();
    private List<Projected> spots = List.of();
    @Nullable private Projected hovered;

    /** The action in progress: what, when it started, its request, the server's answer (null until it arrives). */
    @Nullable private Action busy;
    private long busyStart, answered;
    private int request;
    @Nullable private Boolean ok;
    @Nullable private Battery9vItem.Kind busyKind;
    private boolean closeAfter;

    private record Candidate(int source, ItemStack stack) {}
    private List<Candidate> candidates = List.of();
    private boolean choosing;
    private int stripHover = -1;

    @Nullable private Component notice;
    private long noticeAt;
    private List<FormattedCharSequence> info = List.of();

    private record Projected(InspectProfile.Hotspot spot, float x0, float y0, float x1, float y1, float cx, float cy) {
        boolean contains(double x, double y) { return x >= x0 - 2 && x <= x1 + 2 && y >= y0 - 2 && y <= y1 + 2; }
        float area() { return (x1 - x0) * (y1 - y0); }
    }

    private ItemInspectScreen(@Nullable Screen parent, int source, Item item, float fromX, float fromY, float fromH) {
        super(item.getDescription());
        this.parent = parent;
        this.source = source;
        this.item = item;
        this.fromX = fromX;
        this.fromY = fromY;
        this.fromH = fromH;
    }

    // ------------------------------------------------------------------------------------------------ opening

    /** True while the inspect screen is the screen shown (the minimap, compass and Geiger window keep out of it). */
    public static boolean open() {
        return current != null && Minecraft.getInstance().screen == current;
    }

    /** The main-hand item (the inspect key with a non-gun in hand). */
    public static void openHand() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getMainHandItem().isEmpty()) return;
        mc.setScreen(new ItemInspectScreen(null, InspectActions.HAND, mc.player.getMainHandItem().getItem(), Float.NaN, Float.NaN, Float.NaN));
    }

    /**
     * An item in the inventory: source as InspectActions names places; (x, y, h) the cell's centre and height in GUI px
     * it flies from. The parent comes back on closing.
     */
    public static void openFrom(Screen parent, int source, float x, float y, float h) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack stack = InspectActions.at(mc.player, source);
        if (stack == null || stack.isEmpty()) return;
        mc.setScreen(new ItemInspectScreen(parent, source, stack.getItem(), x, y, h));
    }

    /** The server's answer to a hotspot click. */
    public static void result(int id, boolean ok) {
        if (current != null && current.busy != null && current.request == id && current.ok == null) {
            current.ok = ok;
            current.answered = System.nanoTime();
        }
    }

    @Override
    protected void init() {
        current = this;
        if (opened == 0) opened = System.nanoTime();
        cancelDrag();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (current == this) current = null;
        GeigerCounterItemRenderer.Inspect.clear();
    }

    /** Esc, the inspect key, the inventory key: the cover back on first if it is off, then the fly back. */
    @Override
    public void onClose() {
        if (closing != 0) return;
        if (busy != null) { closeAfter = true; return; }
        ItemStack stack = live();
        if (stack != null && InspectProfile.has(stack) && GeigerCounterItem.coverOpen(stack)) {
            closeAfter = true;
            start(Action.CLOSE_COVER, 0);
            return;
        }
        choosing = false;
        closing = System.nanoTime();
    }

    private void finish() {
        removed();
        minecraft.setScreen(parent);
    }

    @Nullable
    private ItemStack live() {
        return minecraft.player == null ? null : InspectActions.at(minecraft.player, source);
    }

    // ------------------------------------------------------------------------------------------------ tick

    @Override
    public void tick() {
        var player = minecraft.player;
        ItemStack stack = live();
        if (player == null || !player.isAlive() || stack == null || stack.isEmpty() || stack.getItem() != item
                || health >= 0 && player.getHealth() < health) {
            finish();
            return;
        }
        health = player.getHealth();
        // the same line the hover tooltip shows
        Component description = com.antaurora.apofirstlight.client.ItemDescriptionTooltip.description(stack);
        info = description == null ? List.of() : font.split(description, (int) INFO_WRAP);
    }

    // ------------------------------------------------------------------------------------------------ actions

    private void start(Action action, int candidate) {
        ItemStack stack = live();
        if (stack == null) return;
        busy = action;
        busyStart = System.nanoTime();
        ok = null;
        answered = 0;
        request = ++requests;
        busyKind = switch (action) {
            case REMOVE_BATTERY -> GeigerCounterItem.batteryKind(stack);
            case FIT_BATTERY -> {
                ItemStack battery = InspectActions.at(minecraft.player, candidate);
                yield battery == null ? null : Battery9vItem.kind(battery);
            }
            default -> null;
        };
        choosing = false;
        hovered = null;
        AflNetwork.inspectToServer(new InspectPackets.Request(request, source, action.ordinal(), candidate));
    }

    /** Drives the renderer's overrides for the action in progress; ends it once the item shows the agreed state. */
    private void animate(long now, ItemStack stack) {
        if (busy == null) {
            GeigerCounterItemRenderer.Inspect.cover = GeigerCounterItemRenderer.Inspect.battery = Float.NaN;
            GeigerCounterItemRenderer.Inspect.batteryKind = null;
            return;
        }
        float t = AflUiTween.clamp01((float) ((now - busyStart) / 1e9 / busy.seconds));
        if (Boolean.FALSE.equals(ok)) {
            end(true);
            notice(Component.translatable("gui.apocalypse_firstlight.item_inspect.changed"));
            return;
        }
        float e = AflUiTween.Ease.IN_OUT.apply(t);
        switch (busy) {
            case OPEN_COVER -> GeigerCounterItemRenderer.Inspect.cover = e;
            case CLOSE_COVER -> GeigerCounterItemRenderer.Inspect.cover = 1 - e;
            case REMOVE_BATTERY -> {
                GeigerCounterItemRenderer.Inspect.battery = e;
                GeigerCounterItemRenderer.Inspect.batteryKind = busyKind;
            }
            case FIT_BATTERY -> {
                GeigerCounterItemRenderer.Inspect.battery = 1 - e;
                GeigerCounterItemRenderer.Inspect.batteryKind = busyKind;
            }
            case SWITCH -> {}
        }
        if (t < 1 || ok == null) return;
        boolean synced = switch (busy) {
            case OPEN_COVER -> GeigerCounterItem.coverOpen(stack);
            case CLOSE_COVER -> !GeigerCounterItem.coverOpen(stack);
            case REMOVE_BATTERY -> GeigerCounterItem.battery(stack).isEmpty();
            case FIT_BATTERY -> !GeigerCounterItem.battery(stack).isEmpty();
            case SWITCH -> true;
        };
        if (synced || now - answered > SYNC_GRACE) end(false);
    }

    private void end(boolean refused) {
        Action done = busy;
        busy = null;
        ok = null;
        busyKind = null;
        GeigerCounterItemRenderer.Inspect.cover = GeigerCounterItemRenderer.Inspect.battery = Float.NaN;
        GeigerCounterItemRenderer.Inspect.batteryKind = null;
        if (!closeAfter) return;
        closeAfter = false;
        // after the cover went back on (or anything was refused) the screen shuts as it is
        if (done == Action.CLOSE_COVER || refused) closing = System.nanoTime();
        else onClose();
    }

    private void notice(Component text) {
        notice = text;
        noticeAt = System.nanoTime();
    }

    /** The 9 V batteries the player carries (grids, then inventory slots past the crafting grid), fullest first. */
    private List<Candidate> batteries() {
        var player = minecraft.player;
        List<Candidate> list = new ArrayList<>();
        var view = PlayerGrids.view(player);
        for (var target : PlayerGrids.Target.values()) {
            var storage = view.get(target);
            if (storage == null) continue;
            for (var entry : storage.entries())
                if (Battery9vItem.kind(entry.stack()) != null)
                    list.add(new Candidate(GridSource.encode(target, entry.section(), entry.x(), entry.y()), entry.stack()));
        }
        var slots = player.inventoryMenu.slots;
        for (int i = 5; i < slots.size(); i++) {
            ItemStack stack = slots.get(i).getItem();
            if (i != source && Battery9vItem.kind(stack) != null) list.add(new Candidate(i, stack));
        }
        list.sort(Comparator.comparingDouble(c -> -(double) Battery9vItem.energy(c.stack()) / Battery9vItem.capacity(c.stack())));
        return list.size() > 16 ? list.subList(0, 16) : list;
    }

    private void click(Action action) {
        if (action != InspectProfile.CHOOSE) {
            start(action, 0);
            return;
        }
        candidates = batteries();
        if (candidates.isEmpty()) {
            notice(Component.translatable("gui.apocalypse_firstlight.item_inspect.no_battery"));
            return;
        }
        choosing = true;
        strip.appear();
    }

    // ------------------------------------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (NativeGunInput.inspectKey().matches(keyCode, scanCode) || minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (NativeGunInput.RESET_INSPECTION.matches(keyCode, scanCode)) {
            reset(System.nanoTime());
            return true;
        }
        if (keyCode == InputConstants.KEY_ESCAPE && choosing) {
            choosing = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        cancelDrag();
        if (closing != 0 || busy != null) return true;
        if (choosing) {
            if (button == 0 && stripHover >= 0 && stripHover < candidates.size()) start(Action.FIT_BATTERY, candidates.get(stripHover).source());
            else choosing = false;
            return true;
        }
        if (hovered != null) {
            if (button == 0) { click(hovered.spot().left()); return true; }
            if (button == 1 && hovered.spot().right() != null) { click(hovered.spot().right()); return true; }
        }
        if (button == 0 || button == 2) {
            dragButton = button;
            startX = lastX = x;
            startY = lastY = y;
            dragging = false;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != dragButton || closing != 0) return true;
        if (!dragging && Math.hypot(x - startX, y - startY) < DRAG_THRESHOLD) return true;
        if (!dragging) { interruptReset(); dragging = true; }
        double mx = x - lastX, my = y - lastY;
        lastX = x;
        lastY = y;
        if (button == 0) {
            targetYaw += mx * DEGREES_PER_PIXEL;
            targetPitch = clamp(targetPitch + my * DEGREES_PER_PIXEL, -PITCH_LIMIT, PITCH_LIMIT);
        } else {
            // view units per GUI px at the item's distance, kept within a third of the view either way
            double unit = 2 * DISTANCE * Math.tan(Math.toRadians(FOV / 2)) / height, limitY = DISTANCE * Math.tan(Math.toRadians(FOV / 2)) * 0.66;
            double limitX = limitY * width / height;
            targetPanX = clamp(targetPanX + mx * unit, -limitX, limitX);
            targetPanY = clamp(targetPanY - my * unit, -limitY, limitY);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == dragButton) cancelDrag();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        AflKeyHint.scrolled();
        if (closing != 0 || choosing) return true;
        interruptReset();
        targetZoom = clamp(targetZoom * Math.pow(ZOOM_STEP, delta), MIN_ZOOM, MAX_ZOOM);
        return true;
    }

    private void cancelDrag() {
        dragButton = -1;
        dragging = false;
    }

    private void reset(long now) {
        cancelDrag();
        resetStart = now;
        resetYaw = yaw; resetPitch = pitch; resetZoom = zoom; resetX = panX; resetY = panY;
        resetEndYaw = yaw - Math.IEEEremainder(yaw, 360);
        targetYaw = resetEndYaw; targetPitch = targetPanX = targetPanY = 0; targetZoom = 1;
    }

    private void interruptReset() {
        if (resetStart == 0) return;
        resetStart = 0;
        targetYaw = yaw; targetPitch = pitch; targetZoom = zoom; targetPanX = panX; targetPanY = panY;
    }

    private void view(long now) {
        double dt = lastFrame == 0 ? 0 : Math.max(0, (now - lastFrame) / 1e9);
        lastFrame = now;
        if (resetStart != 0) {
            double t = clamp((now - resetStart) / 1e9 / RESET_SECONDS, 0, 1), e = 1 - Math.pow(1 - t, 3);
            yaw = lerp(resetYaw, resetEndYaw, e); pitch = lerp(resetPitch, 0, e); zoom = lerp(resetZoom, 1, e);
            panX = lerp(resetX, 0, e); panY = lerp(resetY, 0, e);
            if (t >= 1) { resetStart = 0; yaw = targetYaw = 0; }
            return;
        }
        double motion = 1 - Math.exp(-Math.log(20) * dt / MOTION_RESPONSE), magnify = 1 - Math.exp(-Math.log(20) * dt / ZOOM_RESPONSE);
        yaw = lerp(yaw, targetYaw, motion); pitch = lerp(pitch, targetPitch, motion);
        zoom = lerp(zoom, targetZoom, magnify);
        panX = lerp(panX, targetPanX, motion); panY = lerp(panY, targetPanY, motion);
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        ItemStack stack = live();
        if (stack == null || stack.isEmpty()) return;
        if (closing != 0 && now - closing >= FLY_SECONDS * 1e9F) {
            finish();
            return;
        }
        float fly = fly(now);
        frame(now, stack);
        // drawn in the hand pass already (lit like the world, as the gun's field view); in third person or with the
        // hands hidden there is no hand pass, and the item is drawn here with its own lights
        if (handFrame != frames) drawItem(g, stack, fly);
        else spots = GeigerCounterItemRenderer.Inspect.posed ? project(stack) : List.of();
        float ui = AflUiTween.clamp01((fly - 0.5F) * 2);
        boolean idle = busy == null && closing == 0 && !choosing;
        hovered = null;
        if (idle && !dragging) {
            for (Projected p : spots) if (p.contains(mouseX, mouseY) && (hovered == null || p.area() < hovered.area())) hovered = p;
        }
        if (busy == null && closing == 0) drawSpots(g, ui);
        if (choosing) drawStrip(g, mouseX, mouseY, ui);
        else stripHover = -1;
        drawNotice(g, now, ui);
        hints.render(g, width, rows(), closing == 0 && ui > 0.5F);
        drawInfo(g, stack, ui);
    }

    /** The fly: 0 at the cell, 1 in the middle; closing runs it back, then the parent comes back. */
    private float fly(long now) {
        float fly = AflUiTween.Ease.IN_OUT.apply(AflUiTween.clamp01((now - opened) / 1e9F / FLY_SECONDS));
        if (closing != 0) fly = Math.min(fly, 1 - AflUiTween.Ease.IN_OUT.apply(AflUiTween.clamp01((now - closing) / 1e9F / FLY_SECONDS)));
        return fly;
    }

    /** The view's smoothing and the action's animation, once a frame (the hand pass comes before the screen). */
    private void frame(long now, ItemStack stack) {
        if (viewFrame == frames) return;
        viewFrame = frames;
        view(now);
        animate(now, stack);
    }

    /**
     * The item's pose in view space for a perspective with m11 {@code f} and aspect {@code aspect}, the item's centre
     * {@code depth} in front: on screen it is as large and where it would be in the screen's own 30° view at
     * {@link #DISTANCE}. The fly shrinks it toward the cell (or the hand, bottom right) and slides it onto it.
     */
    private Matrix4f itemPose(float f, float aspect, float fly, float depth) {
        float cx, cy, s0;
        if (Float.isNaN(fromX)) { cx = 0.55F; cy = -0.75F; s0 = 0.4F; }
        else {
            cx = 2 * fromX / width - 1;
            cy = 1 - 2 * fromY / height;
            s0 = Math.max(0.05F, fromH / (VIEW_SHARE * height));
        }
        float k = s0 + (1 - s0) * fly, own = (float) (1 / Math.tan(Math.toRadians(FOV / 2)));
        // the centre in normalised device coordinates as the 30° view puts it, then pulled toward the cell
        float nx = k * own / aspect * (float) panX / DISTANCE + cx * (1 - fly), ny = k * own * (float) panY / DISTANCE + cy * (1 - fly);
        float size = k * own * depth / (f * DISTANCE) * (float) zoom;
        return new Matrix4f().translation(nx * aspect * depth / f, ny * depth / f, -depth)
                .rotate(Axis.XP.rotationDegrees((float) pitch)).rotate(Axis.YP.rotationDegrees((float) yaw))
                .scale(size)
                // the item frame's view: FIXED shows the side an item frame shows, which faces away from it
                .rotate(Axis.YP.rotationDegrees(180));
    }

    /**
     * The hand pass (RenderHandEvent): the item where the hands would be, in the world's light and the shader's hand pass,
     * as the gun's field attachment view draws the gun; the held item and the arms stay hidden meanwhile. Once a frame
     * (Oculus runs the hand pass twice); the hotspots are projected with this pass's projection.
     */
    private void drawInHand(net.minecraftforge.client.event.RenderHandEvent event) {
        long now = System.nanoTime();
        ItemStack stack = live();
        if (stack == null || stack.isEmpty() || closing != 0 && now - closing >= FLY_SECONDS * 1e9F) return;
        frame(now, stack);
        handFrame = frames;
        GeigerCounterItemRenderer.Inspect.stack = stack;
        GeigerCounterItemRenderer.Inspect.posed = false;
        projection.set(RenderSystem.getProjectionMatrix());
        float f = projection.m11(), aspect = f / projection.m00();
        PoseStack pose = new PoseStack();
        pose.mulPoseMatrix(itemPose(f, aspect, fly(now), HAND_DEPTH));
        minecraft.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, event.getPackedLight(), OverlayTexture.NO_OVERLAY,
                pose, event.getMultiBufferSource(), minecraft.level, 0);
    }

    /** Third person or the hands hidden: the item in the screen's own 30° view with its own lights (no hand pass). */
    private void drawItem(GuiGraphics g, ItemStack stack, float fly) {
        g.flush();
        GeigerCounterItemRenderer.Inspect.stack = stack;
        GeigerCounterItemRenderer.Inspect.posed = false;
        float aspect = (float) width / height;
        projection.setPerspective((float) Math.toRadians(FOV), aspect, 0.05F, 50F);

        Matrix4f saved = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new Matrix4f(projection), VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        // a key light from the upper left in front, a fill from the lower right behind (view space)
        RenderSystem.setupLevelDiffuseLighting(new Vector3f(-0.45F, 0.8F, 0.55F).normalize(), new Vector3f(0.5F, -0.2F, -0.6F).normalize(), new Matrix4f());

        PoseStack pose = new PoseStack();
        pose.mulPoseMatrix(itemPose(projection.m11(), aspect, fly, DISTANCE));
        var buffers = g.bufferSource();
        minecraft.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                pose, buffers, minecraft.level, 0);
        buffers.endBatch();

        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(saved, sorting);
        com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        spots = GeigerCounterItemRenderer.Inspect.posed ? project(stack) : List.of();
    }

    private List<Projected> project(ItemStack stack) {
        List<Projected> list = new ArrayList<>();
        Matrix4f pose = GeigerCounterItemRenderer.Inspect.POSE;
        Matrix3f normals = new Matrix3f(pose);
        for (var spot : InspectProfile.hotspots(stack)) {
            var b = spot.bounds();
            Vector4f centre = pose.transform(new Vector4f((float) (b.minX() + b.maxX()) / 2, (float) (b.minY() + b.maxY()) / 2,
                    (float) (b.minZ() + b.maxZ()) / 2, 1));
            Vector3f normal = normals.transform(new Vector3f(spot.normal())).normalize();
            Vector3f toCamera = new Vector3f(-centre.x, -centre.y, -centre.z).normalize();
            if (normal.dot(toCamera) < 0.15F) continue;   // turned away: dark and not clickable
            float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
            for (int i = 0; i < 8; i++) {
                Vector4f v = pose.transform(new Vector4f((float) ((i & 1) == 0 ? b.minX() : b.maxX()),
                        (float) ((i & 2) == 0 ? b.minY() : b.maxY()), (float) ((i & 4) == 0 ? b.minZ() : b.maxZ()), 1));
                projection.transform(v);
                if (v.w <= 0) continue;
                float sx = (v.x / v.w + 1) / 2 * width, sy = (1 - v.y / v.w) / 2 * height;
                x0 = Math.min(x0, sx); y0 = Math.min(y0, sy); x1 = Math.max(x1, sx); y1 = Math.max(y1, sy);
            }
            if (x0 > x1) continue;
            projection.transform(centre);
            list.add(new Projected(spot, x0, y0, x1, y1, (centre.x / centre.w + 1) / 2 * width, (1 - centre.y / centre.w) / 2 * height));
        }
        return list;
    }

    private void drawSpots(GuiGraphics g, float alpha) {
        if (alpha <= 0.02F) return;
        for (Projected p : spots) {
            if (p == hovered) {
                AflUiShapes.box(g, p.x0() - 2, p.y0() - 2, p.x1() - p.x0() + 4, p.y1() - p.y0() + 4, AflUiStyle.RADIUS_TILE,
                        AflUiStyle.ACCENT, 0.12F * alpha, AflUiStyle.ACCENT, 0.9F * alpha, 0, 0);
                AflUiShapes.fill(g, p.x1() + 4, p.cy() - 2, 4, 4, 2, AflUiStyle.ACCENT, alpha);
            } else AflUiShapes.fill(g, p.cx() - 1.5F, p.cy() - 1.5F, 3, 3, 1.5F, AflUiStyle.ACCENT, 0.55F * alpha);
        }
    }

    /** Top right under the key hints, smaller: the item's name and its description. */
    private void drawInfo(GuiGraphics g, ItemStack stack, float alpha) {
        if (alpha <= 0.02F) return;
        Component name = stack.getHoverName();
        float w = font.width(name);
        for (var line : info) w = Math.max(w, font.width(line));
        w += 16;
        float h = 8 + 10 + (info.isEmpty() ? 0 : 3 + info.size() * 10) + 4;
        g.pose().pushPose();
        // the hints' right edge (their margin scales with them), and just under their bottom
        g.pose().translate(width - 8 * AflKeyHintPanel.SCALE, AflUiTween.snap(hints.bottom() + INFO_GAP), 0);
        g.pose().scale(INFO_SCALE, INFO_SCALE, 1);
        float x = -w, y = 0;
        AflUiDraw.panel(g, x, y, w, h, alpha);
        AflUiDraw.text(g, font, name, x + 8, y + 8, AflUiStyle.TEXT, alpha);
        float ly = y + 8 + 13;
        for (var line : info) {
            g.drawString(font, line, Math.round(x + 8), Math.round(ly), AflUiStyle.argb(AflUiStyle.TEXT_DIM, alpha), false);
            ly += 10;
        }
        g.pose().popPose();
    }

    private void drawStrip(GuiGraphics g, int mouseX, int mouseY, float alpha) {
        int count = candidates.size();
        float x0 = AflUiTween.snap((width - count * 20) / 2F), y = height - 34;
        stripHover = mouseY >= y && mouseY < y + 20 && mouseX >= x0 && mouseX < x0 + count * 20 ? (int) ((mouseX - x0) / 20) : -1;
        strip.render(g, x0, y, count, stripHover, stripHover, false, alpha, (gg, i, x, cy) -> {
            var c = candidates.get(i);
            gg.renderItem(c.stack(), x, cy);
            gg.renderItemDecorations(font, c.stack(), x, cy);
        });
    }

    private void drawNotice(GuiGraphics g, long now, float alpha) {
        if (notice == null) return;
        float age = (now - noticeAt) / (float) NOTICE_NS;
        if (age >= 1) { notice = null; return; }
        float a = alpha * Math.min(1, (1 - age) * 4);
        float w = font.width(notice) + 16, x = (width - w) / 2, y = height - 64;
        AflUiDraw.label(g, x, y, w, 16, a);
        AflUiDraw.centred(g, font, notice, width / 2F, y + 4.5F, AflUiStyle.TEXT, a);
    }

    /** What each input does right now (docs/ui/afl_overlay_ui_style_v1.md 8). */
    private List<AflKeyHintPanel.Row> rows() {
        var exit = row(AflKeyHint.of(NativeGunInput.inspectKey()), "exit");
        if (busy != null || closing != 0) return List.of(exit);
        if (choosing) return List.of(row(AflKeyHint.MOUSE_LEFT, "fit"), row(AflKeyHint.MOUSE_RIGHT, "back"), exit);
        List<AflKeyHintPanel.Row> rows = new ArrayList<>();
        if (hovered != null) {
            rows.add(row(AflKeyHint.MOUSE_LEFT, hovered.spot().leftLabel()));
            if (hovered.spot().rightLabel() != null) rows.add(row(AflKeyHint.MOUSE_RIGHT, hovered.spot().rightLabel()));
        } else rows.add(row(AflKeyHint.MOUSE_LEFT, "rotate"));
        rows.add(row(AflKeyHint.MOUSE_MIDDLE, "pan"));
        rows.add(row(AflKeyHint.MOUSE_SCROLL, "zoom"));
        rows.add(row(AflKeyHint.of(NativeGunInput.RESET_INSPECTION), "reset"));
        rows.add(exit);
        return rows;
    }

    private static AflKeyHintPanel.Row row(AflKeyHint key, String label) {
        return new AflKeyHintPanel.Row(key, Component.translatable("gui.apocalypse_firstlight.key_hint." + label));
    }

    @Override
    public void renderBackground(GuiGraphics g) {}

    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.antaurora.apofirstlight.ApocalypseFirstLight.MOD_ID,
            value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class Events {
        private Events() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
            if (event.phase == net.minecraftforge.event.TickEvent.Phase.START) frames++;
        }

        /** Before the other hand renderers, and even when one of them cancelled the hands (the item still shows). */
        @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST, receiveCanceled = true)
        public static void hands(net.minecraftforge.client.event.RenderHandEvent event) {
            if (!open()) return;
            event.setCanceled(true);
            if (current.handFrame != frames) current.drawInHand(event);
        }
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
