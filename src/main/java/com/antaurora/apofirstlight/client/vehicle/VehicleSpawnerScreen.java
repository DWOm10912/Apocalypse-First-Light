package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.registry.AflEntities;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.VehiclePackets;
import com.antaurora.apofirstlight.vehicle.VehicleSpawnerItem;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The vehicle spawner (VehicleSpawnerItem, user 2026-10-21), in the overlay style (docs/ui/afl_overlay_ui_style_v1.md):
 * a turning preview of the truck as chosen, its factory colour, wear (slider and new / used / worn out), litres in the
 * tank and where its key waits (the driver's visor by default). Spawn sends {@link VehiclePackets.Spawn}; the server sets
 * it down at the clicked point facing the player's look. The last choices are kept for the next truck.
 */
public final class VehicleSpawnerScreen extends Screen {
    private static final String LANG = "screen." + ApocalypseFirstLight.MOD_ID + ".vehicle_spawner.";
    private static final float PANEL_W = 384, PANEL_H = 236, PAD = 12, PREVIEW_W = 196, BUTTON_H = 16, SWATCH = 14, SLIDER_H = 6;
    private static final float[] WEAR_PRESETS = {0.0F, 0.45F, 0.85F};
    private static final String[] WEAR_NAMES = {"new", "used", "worn"};
    private static final String[] KEY_NAMES = {"visor", "glovebox", "ignition", "random"};
    /** The preview turns this fast (degrees a second) while not dragged. */
    private static final float SPIN = 18.0F;
    /** The preview looks down on the truck this far (degrees). */
    private static final float PITCH = 20.0F;
    /** Lighting#setupForEntityInInventory's two lights (GUI space). */
    private static final Vector3f INVENTORY_LIGHT_0 = new Vector3f(0.2F, -1.0F, -1.0F).normalize(), INVENTORY_LIGHT_1 = new Vector3f(-0.2F, -1.0F, 0.0F).normalize();
    /** One growing builder per render type, kept for the next bake (their memory is native). */
    private static final Map<RenderType, BufferBuilder> BUILDERS = new HashMap<>();

    private record Baked(RenderType type, VertexBuffer buffer) {}

    /** The last choices (this session). */
    private static int paint = 2, key = 0, fuel = 45;
    private static float wear = 0.3F;

    private final Vec3 at;
    private final boolean floor;
    private PickupTruckEntity truck;
    private boolean previewFailed;
    private final List<Baked> baked = new ArrayList<>();
    private int bakedPaint = -1;
    private float bakedWear;
    private float yaw = 210, panelX, panelY;
    private long lastFrame = Util.getMillis();
    /** What the mouse holds: 0 none, 1 wear slider, 2 fuel slider, 3 the preview. */
    private int dragging;

    public VehicleSpawnerScreen(Vec3 at, boolean floor) {
        super(Component.translatable("entity.apocalypse_firstlight.pickup_t15"));
        this.at = at;
        this.floor = floor;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---- layout (GUI px, from the panel's corner) ----

    private float colX() { return panelX + PAD + PREVIEW_W + PAD; }
    private float colW() { return PANEL_W - PREVIEW_W - 3 * PAD; }
    private float swatchY() { return panelY + PAD + 26; }
    private float wearY() { return swatchY() + SWATCH + 22; }
    private float wearPresetY() { return wearY() + SLIDER_H + 6; }
    private float fuelY() { return wearPresetY() + BUTTON_H + 20; }
    private float keyY() { return fuelY() + SLIDER_H + 20; }
    private float actionY() { return panelY + PANEL_H - PAD - BUTTON_H - 2; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        if (dragging != 3) yaw += SPIN * (now - lastFrame) / 1000F;
        lastFrame = now;
        g.fillGradient(0, 0, width, height, AflUiStyle.argb(0x0A0B0C, 0.45F), AflUiStyle.argb(0x0A0B0C, 0.70F));
        panelX = (width - PANEL_W) / 2;
        panelY = Math.max(8, (height - PANEL_H) / 2);
        AflUiDraw.panel(g, panelX, panelY, PANEL_W, PANEL_H, 1);
        AflUiDraw.text(g, font, title, panelX + PAD, panelY + PAD, AflUiStyle.TEXT, 1);

        float px = panelX + PAD, py = panelY + PAD + font.lineHeight + 8, ph = PANEL_H - 2 * PAD - font.lineHeight - 8;
        AflUiShapes.fill(g, px, py, PREVIEW_W, ph, AflUiStyle.RADIUS_TILE, 0x000000, 0.28F);
        preview(g, px, py, PREVIEW_W, ph);
        AflUiDraw.centred(g, font, t("drag"), px + PREVIEW_W / 2, py + ph - font.lineHeight - 4, AflUiStyle.TEXT_DIM, 0.8F);

        float x = colX(), w = colW();
        // colour
        VehicleSpawnerItem.Paint chosen = VehicleSpawnerItem.PAINTS.get(paint);
        section(g, t("colour"), t("paint." + chosen.id()), x, swatchY() - font.lineHeight - 5, w);
        float step = (w - SWATCH) / (VehicleSpawnerItem.PAINTS.size() - 1);
        for (int i = 0; i < VehicleSpawnerItem.PAINTS.size(); i++) {
            VehicleSpawnerItem.Paint p = VehicleSpawnerItem.PAINTS.get(i);
            float sx = x + i * step, sy = swatchY();
            boolean hover = inside(mouseX, mouseY, sx, sy, SWATCH, SWATCH);
            AflUiShapes.fill(g, sx, sy, SWATCH, SWATCH, 2, p.main(), 1);
            // the lower band under the side moulding (two-tone)
            if (p.second() != p.main()) AflUiShapes.fill(g, sx, sy + SWATCH * 0.62F, SWATCH, SWATCH * 0.38F, 1, p.second(), 1);
            if (i == paint || hover)
                AflUiShapes.box(g, sx - 2, sy - 2, SWATCH + 4, SWATCH + 4, 3, 0, 0, i == paint ? AflUiStyle.ACCENT : AflUiStyle.LINE,
                        i == paint ? 1 : AflUiStyle.LINE_HOVER_ALPHA, 0, 0);
        }
        // wear
        section(g, t("wear"), Component.literal(Math.round(wear * 100) + "%"), x, wearY() - font.lineHeight - 5, w);
        slider(g, x, wearY(), w, wear);
        float bw = (w - 2 * 4) / 3;
        for (int i = 0; i < 3; i++)
            choice(g, x + i * (bw + 4), wearPresetY(), bw, t("wear." + WEAR_NAMES[i]), Math.abs(wear - WEAR_PRESETS[i]) < 0.005F, mouseX, mouseY);
        // fuel
        section(g, t("fuel"), Component.translatable(LANG + "litres", fuel, PickupTruckEntity.TANK), x, fuelY() - font.lineHeight - 5, w);
        slider(g, x, fuelY(), w, fuel / (float) PickupTruckEntity.TANK);
        // key
        AflUiDraw.text(g, font, t("key"), x, keyY() - font.lineHeight - 5, AflUiStyle.TEXT_DIM, 1);
        float kw = (w - 4) / 2;
        for (int i = 0; i < KEY_NAMES.length; i++)
            choice(g, x + i % 2 * (kw + 4), keyY() + i / 2 * (BUTTON_H + 4), kw, t("key." + KEY_NAMES[i]), key == i, mouseX, mouseY);
        // spawn / cancel
        float aw = (w - 4) / 2;
        button(g, x, actionY(), aw, t("cancel"), false, mouseX, mouseY);
        button(g, x + aw + 4, actionY(), aw, t("spawn"), true, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void section(GuiGraphics g, Component name, Component value, float x, float y, float w) {
        AflUiDraw.text(g, font, name, x, y, AflUiStyle.TEXT_DIM, 1);
        AflUiDraw.text(g, font, value, x + w - font.width(value), y, AflUiStyle.TEXT, 1);
    }

    private void slider(GuiGraphics g, float x, float y, float w, float value) {
        AflUiShapes.fill(g, x, y + 1, w, SLIDER_H - 2, 2, AflUiStyle.LINE, 0.18F);
        AflUiShapes.fill(g, x, y + 1, Math.max(SLIDER_H - 2, w * value), SLIDER_H - 2, 2, AflUiStyle.ACCENT, 0.85F);
        AflUiShapes.fill(g, x + w * value - 2, y - 2, 4, SLIDER_H + 4, 2, AflUiStyle.TEXT, 1);
    }

    private void choice(GuiGraphics g, float x, float y, float w, Component text, boolean on, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, w, BUTTON_H);
        float sink = AflUiDraw.button(g, x, y, w, BUTTON_H, hover, false, true, 1);
        AflUiDraw.centred(g, font, text, x + w / 2, y + sink + (BUTTON_H - font.lineHeight) / 2 + 1, on ? AflUiStyle.ACCENT : AflUiStyle.TEXT, 1);
    }

    private void button(GuiGraphics g, float x, float y, float w, Component text, boolean accent, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, w, BUTTON_H + 2);
        float sink = AflUiDraw.button(g, x, y, w, BUTTON_H + 2, hover, false, true, 1);
        AflUiDraw.centred(g, font, text, x + w / 2, y + sink + (BUTTON_H + 2 - font.lineHeight) / 2 + 1, accent ? AflUiStyle.ACCENT : AflUiStyle.TEXT, 1);
    }

    /**
     * The truck as chosen, turning, seen from above (user 2026-10-21: not the chassis). Its own renderer
     * (PickupTruckRenderer#preview: on the CPU, no world passes) bakes it into vertex buffers once per paint and wear;
     * each frame only draws them (drawing the whole truck on the CPU every frame made the screen stutter, user 2026-10-21).
     */
    private void preview(GuiGraphics g, float x, float y, float w, float h) {
        if (previewFailed || minecraft == null || minecraft.level == null) return;
        try {
            if (truck == null) truck = new PickupTruckEntity(AflEntities.PICKUP_T15.get(), minecraft.level);
            if (paint != bakedPaint || wear != bakedWear) bake();
        } catch (RuntimeException e) {
            // a preview is not worth a crash: the screen works without it
            previewFailed = true;
            release();
            ApocalypseFirstLight.LOGGER.warn("Vehicle spawner preview failed", e);
            return;
        }
        g.flush();
        float scale = w / 6.4F;
        g.enableScissor((int) x, (int) y, (int) (x + w), (int) (y + h));
        g.pose().pushPose();
        g.pose().translate(x + w / 2, y + h * 0.5F, 150);
        g.pose().mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
        Quaternionf turn = Axis.ZP.rotation((float) Math.PI).mul(Axis.XP.rotationDegrees(-PITCH)).mul(Axis.YP.rotationDegrees(yaw));
        g.pose().mulPose(turn);
        g.pose().translate(0, -0.9F, 0);
        // the baked normals are the truck's own: turn the inventory lights into the truck's frame instead
        Quaternionf back = turn.conjugate(new Quaternionf());
        RenderSystem.setShaderLights(back.transform(new Vector3f(INVENTORY_LIGHT_0)), back.transform(new Vector3f(INVENTORY_LIGHT_1)));
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(g.pose().last().pose());
        for (Baked b : baked) {
            b.type().setupRenderState();
            b.buffer().bind();
            b.buffer().drawWithShader(view, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
            b.type().clearRenderState();
        }
        VertexBuffer.unbind();
        Lighting.setupFor3DItems();
        g.pose().popPose();
        g.disableScissor();
    }

    /** The preview's vertex buffers for the current paint and wear (in the truck's frame, cutout first, then glass). */
    private void bake() {
        VehicleSpawnerItem.Paint p = VehicleSpawnerItem.PAINTS.get(paint);
        truck.setPaint(p.main(), p.second());
        truck.setWear(wear);
        @SuppressWarnings("unchecked")
        EntityRenderer<PickupTruckEntity> renderer = (EntityRenderer<PickupTruckEntity>) minecraft.getEntityRenderDispatcher().getRenderer(truck);
        release();
        Map<RenderType, BufferBuilder> used = new LinkedHashMap<>();
        MultiBufferSource capture = type -> used.computeIfAbsent(type, t -> {
            BufferBuilder builder = BUILDERS.computeIfAbsent(t, n -> new BufferBuilder(n.bufferSize()));
            builder.begin(t.mode(), t.format());
            return builder;
        });
        PickupTruckRenderer.preview = true;
        try {
            renderer.render(truck, 0, 1F, new PoseStack(), capture, LightTexture.FULL_BRIGHT);
        } finally {
            PickupTruckRenderer.preview = false;
            for (Map.Entry<RenderType, BufferBuilder> e : used.entrySet()) {
                BufferBuilder.RenderedBuffer done = e.getValue().end();
                if (done.isEmpty()) {
                    done.release();
                    continue;
                }
                VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                buffer.bind();
                buffer.upload(done);
                baked.add(new Baked(e.getKey(), buffer));
            }
            VertexBuffer.unbind();
        }
        bakedPaint = paint;
        bakedWear = wear;
    }

    private void release() {
        for (Baked b : baked) b.buffer().close();
        baked.clear();
        bakedPaint = -1;
    }

    @Override
    public void removed() {
        release();
        super.removed();
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        float x = colX(), w = colW();
        float step = (w - SWATCH) / (VehicleSpawnerItem.PAINTS.size() - 1);
        for (int i = 0; i < VehicleSpawnerItem.PAINTS.size(); i++) {
            int n = i;
            if (inside(mouseX, mouseY, x + i * step, swatchY(), SWATCH, SWATCH)) return click(() -> paint = n);
        }
        if (inside(mouseX, mouseY, x - 3, wearY() - 4, w + 6, SLIDER_H + 8)) {
            dragging = 1;
            return mouseDragged(mouseX, mouseY, button, 0, 0);
        }
        if (inside(mouseX, mouseY, x - 3, fuelY() - 4, w + 6, SLIDER_H + 8)) {
            dragging = 2;
            return mouseDragged(mouseX, mouseY, button, 0, 0);
        }
        float bw = (w - 2 * 4) / 3;
        for (int i = 0; i < 3; i++) {
            float preset = WEAR_PRESETS[i];
            if (inside(mouseX, mouseY, x + i * (bw + 4), wearPresetY(), bw, BUTTON_H)) return click(() -> wear = preset);
        }
        float kw = (w - 4) / 2;
        for (int i = 0; i < KEY_NAMES.length; i++) {
            int n = i;
            if (inside(mouseX, mouseY, x + i % 2 * (kw + 4), keyY() + i / 2 * (BUTTON_H + 4), kw, BUTTON_H)) return click(() -> key = n);
        }
        float aw = (w - 4) / 2;
        if (inside(mouseX, mouseY, x, actionY(), aw, BUTTON_H + 2)) return click(this::onClose);
        if (inside(mouseX, mouseY, x + aw + 4, actionY(), aw, BUTTON_H + 2)) return click(this::spawn);
        if (inside(mouseX, mouseY, panelX + PAD, panelY + PAD, PREVIEW_W, PANEL_H - 2 * PAD)) {
            dragging = 3;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        float value = (float) Mth.clamp((mouseX - colX()) / colW(), 0, 1);
        switch (dragging) {
            case 1 -> wear = Math.round(value * 100) / 100F;
            case 2 -> fuel = Math.round(value * PickupTruckEntity.TANK);
            case 3 -> yaw += (float) dx * 1.2F;
            default -> {
                return super.mouseDragged(mouseX, mouseY, button, dx, dy);
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = 0;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            spawn();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void spawn() {
        AflNetwork.vehicleToServer(new VehiclePackets.Spawn(at.x, at.y, at.z, floor, paint, wear, fuel, key));
        onClose();
    }

    private boolean click(Runnable action) {
        action.run();
        if (minecraft != null)
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static Component t(String key) { return Component.translatable(LANG + key); }
}
