package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.item.Battery9vItem;
import com.antaurora.apofirstlight.item.GeigerCounterItem;
import com.antaurora.vextris.client.MeshRenderer;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshBone;
import com.antaurora.vextris.mesh.MeshLayer;
import com.antaurora.vextris.mesh.MeshModel;
import com.antaurora.vextris.mesh.MeshPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Geiger counter V2 (tools/build-equipment-meshes-v1.mjs geiger_counter): the Pure Mesh with a live LCD. The LCD's
 * seven-segment parts (seg_<cell>_<a..g>, dp_<cell>) are drawn only where GeigerReadout lights them, and led_on (full
 * bright) replaces led while a count flashes. Only the local player's own held counter has a reading (first person, or
 * its hand stacks in third person); every other counter (other players, on the ground, in a frame) shows a blank LCD.
 * <p>Item inspect V1 (docs/gameplay/item_inspect_v1_design.md 5): the power slider, the bay cover, the battery (the 9 V
 * battery item's own mesh, at the build's points.battery), its snap and the LCD's battery icon follow the stack's tag
 * everywhere; the LCD lights only while the counter is powered (a 1 s self-test with every segment first). The inspect
 * screen marks its stack ({@link #inspect}): it reads live, the cover and battery move with the screen's animations, and
 * the pose the mesh is drawn at is kept for the hotspots.
 */
public final class GeigerCounterItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation GEOMETRY = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/geiger_counter.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/geiger_counter_mesh.png");
    /** 0.5 - mesh height 3.12 / 32 (the build's verticalOffset). */
    private static final double VERTICAL_OFFSET = 0.4025;
    /**
     * The fitted battery: the geiger's points.battery (-0.025, 1.1, 2.4) minus the battery bake's centre (0, 0.875, 0.15),
     * in block units; the slider's travel toward the top end when on, the cover's slide toward the bottom end and lift off
     * the back, a battery's lift out of the bay (px / 16).
     */
    private static final float BATTERY_X = -0.025F / 16, BATTERY_Y = 0.225F / 16, BATTERY_Z = 2.25F / 16,
            SWITCH_TRAVEL = -0.45F / 16, COVER_SLIDE = 2.2F / 16, COVER_LIFT = -1.6F / 16, BATTERY_LIFT = -3.2F / 16;
    /** Share of a battery's removal spent pulling off the snap (the rest lifts it out); the cover slides before it lifts. */
    public static final float UNSNAP = 0.35F, SLIDE = 0.6F;
    private static final Pattern SEGMENT = Pattern.compile("seg_(\\d)_([a-g])"), POINT = Pattern.compile("dp_(\\d)"),
            BAR = Pattern.compile("bat_bar_(\\d)");

    /** The mesh's parts sorted for the LCD and the bay; rebuilt when F3+T replaces the mesh. */
    private record Parts(MeshModel mesh, List<MeshPart> fixed, MeshPart[][] segments, MeshPart[] points, MeshPart led, MeshPart ledOn,
                         MeshPart powerSwitch, MeshPart cover, MeshPart bay, MeshPart snapOn, MeshPart snapOff, MeshPart frame, MeshPart[] bars) {}
    private static Parts parts;

    /**
     * The inspect screen's view of its stack: the stack (by identity), the cover's slide (0 shut .. 1 off, NaN: as the tag
     * says), a battery's lift (0 seated .. 1 out, NaN: as the tag says) and the kind lifted, and the pose the mesh was last
     * drawn at.
     */
    public static final class Inspect {
        @Nullable public static ItemStack stack;
        public static float cover = Float.NaN, battery = Float.NaN;
        @Nullable public static Battery9vItem.Kind batteryKind;
        public static final Matrix4f POSE = new Matrix4f();
        public static boolean posed;

        private Inspect() {}

        public static void clear() { stack = null; cover = battery = Float.NaN; batteryKind = null; posed = false; }
    }

    public GeigerCounterItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mesh = VextrisMeshes.byGeometry(GEOMETRY);
        if (mesh == null) return;
        Parts p = parts(mesh);
        long now = GeigerCounterItem.now();
        boolean inspected = stack == Inspect.stack, powered = GeigerCounterItem.powered(stack, now);
        boolean selfTest = powered && GeigerCounterItem.selfTest(stack, now);
        GeigerReadout.Display shown = null;
        boolean led = false;
        if (powered && (inspected || live(stack, context))) {
            if (!selfTest) shown = GeigerReadout.display();
            led = !selfTest && GeigerReadout.ledLit();
        } else powered = false;
        VertexConsumer cutout = ItemRenderer.getFoilBufferDirect(buffers, RenderType.entityCutoutNoCull(TEXTURE), false, stack.hasFoil());
        pose.pushPose();
        try {
            // ItemRenderer already applied the display transform and the -0.5 shift; the mesh is centred on X / Z, rests on y = 0
            pose.translate(0.5, VERTICAL_OFFSET, 0.5);
            if (inspected) { Inspect.POSE.set(pose.last().pose()); Inspect.posed = true; }
            List<MeshPart> lit = new ArrayList<>();
            if (selfTest) {
                for (MeshPart[] cell : p.segments) for (MeshPart s : cell) if (s != null) lit.add(s);
                for (MeshPart d : p.points) if (d != null) lit.add(d);
            } else if (shown != null) {
                for (int cell = 0; cell < p.segments.length; cell++)
                    for (int bit = 0; bit < 7; bit++) if (p.segments[cell][bit] != null && shown.segment(cell, bit)) lit.add(p.segments[cell][bit]);
                if (shown.dp() >= 0 && shown.dp() < p.points.length && p.points[shown.dp()] != null) lit.add(p.points[shown.dp()]);
            }
            if (powered) {
                int bars = selfTest ? 3 : batteryBars(stack, now);
                if (p.frame != null && (bars > 0 || lowBlink())) lit.add(p.frame);
                for (int i = 0; i < Math.min(bars, p.bars.length); i++) if (p.bars[i] != null) lit.add(p.bars[i]);
            }
            MeshRenderer.renderParts(p.fixed, pose, cutout, light, overlay, 1, 1, 1, 1, null);
            if (!lit.isEmpty()) MeshRenderer.renderParts(lit, pose, cutout, light, overlay, 1, 1, 1, 1, null);
            MeshPart lamp = led ? p.ledOn : p.led;
            if (lamp != null) MeshRenderer.renderParts(List.of(lamp), pose, cutout, led ? LightTexture.FULL_BRIGHT : light, overlay, 1, 1, 1, 1, null);
            renderPower(stack, p, inspected, pose, buffers, cutout, light, overlay);
            if (mesh.hasTranslucent()) {
                VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
                for (MeshBone bone : mesh.roots()) {
                    if (bone.neverRender()) continue;
                    pose.pushPose();
                    pose.translate(bone.pivotX(), bone.pivotY(), bone.pivotZ());
                    MeshRenderer.renderParts(mesh.parts(bone.name(), MeshLayer.TRANSLUCENT), pose, glass, light, overlay, 1, 1, 1, 1, null);
                    pose.popPose();
                }
            }
        } finally {
            pose.popPose();
        }
    }

    /** The slider, the cover, the bay with its snap and the battery, as the tag (and the inspect screen's animations) say. */
    private static void renderPower(ItemStack stack, Parts p, boolean inspected, PoseStack pose, MultiBufferSource buffers,
                                    VertexConsumer cutout, int light, int overlay) {
        if (p.powerSwitch != null) {
            pose.pushPose();
            if (GeigerCounterItem.on(stack)) pose.translate(0, 0, SWITCH_TRAVEL);
            MeshRenderer.renderParts(List.of(p.powerSwitch), pose, cutout, light, overlay, 1, 1, 1, 1, null);
            pose.popPose();
        }
        float cover = inspected ? Inspect.cover : Float.NaN, lift = inspected ? Inspect.battery : Float.NaN;
        boolean shut = Float.isNaN(cover) ? !GeigerCounterItem.coverOpen(stack) : cover <= 0;
        if (p.cover != null && (Float.isNaN(cover) ? shut : cover < 1)) {
            pose.pushPose();
            if (!Float.isNaN(cover)) pose.translate(0, COVER_LIFT * Math.max(0, (cover - SLIDE) / (1 - SLIDE)), COVER_SLIDE * Math.min(1, cover / SLIDE));
            MeshRenderer.renderParts(List.of(p.cover), pose, cutout, light, overlay, 1, 1, 1, 1, null);
            pose.popPose();
        }
        if (shut) return;   // the bay, the snap and the battery are inside
        if (p.bay != null) MeshRenderer.renderParts(List.of(p.bay), pose, cutout, light, overlay, 1, 1, 1, 1, null);
        Battery9vItem.Kind kind = Float.isNaN(lift) ? GeigerCounterItem.batteryKind(stack) : Inspect.batteryKind;
        boolean snapped = kind != null && (Float.isNaN(lift) || lift < UNSNAP);
        MeshPart snap = snapped ? p.snapOn : p.snapOff;
        if (snap != null) MeshRenderer.renderParts(List.of(snap), pose, cutout, light, overlay, 1, 1, 1, 1, null);
        if (kind == null || (!Float.isNaN(lift) && lift >= 1)) return;
        pose.pushPose();
        float up = Float.isNaN(lift) ? 0 : Math.max(0, (lift - UNSNAP) / (1 - UNSNAP));
        pose.translate(BATTERY_X, BATTERY_Y + BATTERY_LIFT * up, BATTERY_Z);
        String model = kind == Battery9vItem.Kind.NIMH ? "battery_9v_nimh" : "battery_9v_alkaline";
        AflStaticMeshItemRenderer.drawMesh(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/" + model + ".geo.json"),
                new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/" + model + "_mesh.png"), pose, buffers, light, overlay);
        pose.popPose();
    }

    /** The LCD's battery icon (design doc 5.3): 3 bars above 66%, 2 above 33%, 1 above 15%, else the frame alone, blinking. */
    public static int batteryBars(ItemStack stack, long now) {
        double share = (double) GeigerCounterItem.energy(stack, now) / GeigerCounterItem.capacity(stack);
        return share > 0.66 ? 3 : share > 0.33 ? 2 : share > 0.15 ? 1 : 0;
    }

    /** The low-battery frame's 1 Hz blink: lit for the first half of each second. */
    public static boolean lowBlink() {
        return System.currentTimeMillis() % 1000 < 500;
    }

    /** The local player's own counter: any first-person draw, one of its hand stacks in third person, or on one of its rig's clips. */
    private static boolean live(ItemStack stack, ItemDisplayContext context) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        if (context.firstPerson()) return true;
        if (context == ItemDisplayContext.NONE) {
            for (int i = 0; i < com.antaurora.apofirstlight.equipment.AflEquipmentSlots.CLIPS; i++)
                if (com.antaurora.apofirstlight.equipment.AflEquipmentSlots.clip(player, i) == stack) return true;
            return false;
        }
        return (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                && (stack == player.getMainHandItem() || stack == player.getOffhandItem());
    }

    /**
     * A part's bounds in the drawn mesh frame (block units, after the vertical offset): the inspect screen's hotspots. The
     * slider is where the tag puts it; null if the mesh is not loaded or has no such part.
     */
    @Nullable
    public static MeshPart.Bounds bounds(String name, ItemStack stack) {
        MeshModel mesh = VextrisMeshes.byGeometry(GEOMETRY);
        if (mesh == null) return null;
        Parts p = parts(mesh);
        if (name.equals("battery")) {
            // the battery's body in the bay: 26.5 x 17.5 x 48.5 mm round points.battery
            float x = -0.025F / 16, y = 1.1F / 16, z = 2.4F / 16;
            return new MeshPart.Bounds(x - 1.325 / 16, y - 0.875 / 16, z - 2.425 / 16, x + 1.325 / 16, y + 0.875 / 16, z + 2.425 / 16);
        }
        MeshPart part = switch (name) {
            case "power_switch" -> p.powerSwitch;
            case "cover" -> p.cover;
            case "bay" -> p.bay;
            default -> null;
        };
        if (part == null) return null;
        MeshPart.Bounds b = part.bounds();
        double dz = name.equals("power_switch") && GeigerCounterItem.on(stack) ? SWITCH_TRAVEL : 0;
        return new MeshPart.Bounds(b.minX(), b.minY(), b.minZ() + dz, b.maxX(), b.maxY(), b.maxZ() + dz);
    }

    /** One bone with its pivot at the origin (the equipment bake): parts are drawn without a bone transform. */
    private static Parts parts(MeshModel mesh) {
        if (parts != null && parts.mesh == mesh) return parts;
        List<MeshPart> fixed = new ArrayList<>();
        MeshPart[][] segments = new MeshPart[4][7];
        MeshPart[] points = new MeshPart[3], bars = new MeshPart[3];
        MeshPart led = null, ledOn = null, powerSwitch = null, cover = null, bay = null, snapOn = null, snapOff = null, frame = null;
        for (MeshBone bone : mesh.roots()) {
            if (bone.neverRender()) continue;
            for (MeshPart part : mesh.parts(bone.name(), MeshLayer.CUTOUT)) {
                String name = part.name();
                Matcher s = SEGMENT.matcher(name), d = POINT.matcher(name), b = BAR.matcher(name);
                if (s.matches()) { int cell = Integer.parseInt(s.group(1)); if (cell < 4) segments[cell][s.group(2).charAt(0) - 'a'] = part; }
                else if (d.matches()) { int cell = Integer.parseInt(d.group(1)); if (cell < 3) points[cell] = part; }
                else if (b.matches()) { int i = Integer.parseInt(b.group(1)); if (i < 3) bars[i] = part; }
                else switch (name) {
                    case "led" -> led = part;
                    case "led_on" -> ledOn = part;
                    case "power_switch" -> powerSwitch = part;
                    case "cover" -> cover = part;
                    case "bay" -> bay = part;
                    case "snap_on" -> snapOn = part;
                    case "snap_off" -> snapOff = part;
                    case "bat_frame" -> frame = part;
                    default -> fixed.add(part);
                }
            }
        }
        parts = new Parts(mesh, List.copyOf(fixed), segments, points, led, ledOn, powerSwitch, cover, bay, snapOn, snapOff, frame, bars);
        return parts;
    }
}
