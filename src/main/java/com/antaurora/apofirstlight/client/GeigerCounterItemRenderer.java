package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Geiger counter V2 (tools/build-equipment-meshes-v1.mjs geiger_counter): the Pure Mesh with a live LCD. The LCD's
 * seven-segment parts (seg_<cell>_<a..g>, dp_<cell>) are drawn only where GeigerReadout lights them, and led_on (full
 * bright) replaces led while a count flashes. Only the local player's own held counter has a reading (first person, or
 * its hand stacks in third person); every other counter (other players, on the ground, in a frame) shows a blank LCD.
 */
public final class GeigerCounterItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation GEOMETRY = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/geiger_counter.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/geiger_counter_mesh.png");
    /** 0.5 - mesh height 3.12 / 32 (the build's verticalOffset). */
    private static final double VERTICAL_OFFSET = 0.4025;
    private static final Pattern SEGMENT = Pattern.compile("seg_(\\d)_([a-g])"), POINT = Pattern.compile("dp_(\\d)");

    /** The mesh's parts sorted for the LCD; rebuilt when F3+T replaces the mesh. */
    private record Parts(MeshModel mesh, List<MeshPart> fixed, MeshPart[][] segments, MeshPart[] points, MeshPart led, MeshPart ledOn) {}
    private Parts parts;

    public GeigerCounterItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mesh = VextrisMeshes.byGeometry(GEOMETRY);
        if (mesh == null) return;
        Parts p = parts(mesh);
        GeigerReadout.Display shown = null;
        boolean led = false;
        if (live(stack, context)) {
            shown = GeigerReadout.display();
            led = GeigerReadout.ledLit();
        }
        VertexConsumer cutout = ItemRenderer.getFoilBufferDirect(buffers, RenderType.entityCutoutNoCull(TEXTURE), false, stack.hasFoil());
        pose.pushPose();
        try {
            // ItemRenderer already applied the display transform and the -0.5 shift; the mesh is centred on X / Z, rests on y = 0
            pose.translate(0.5, VERTICAL_OFFSET, 0.5);
            List<MeshPart> lit = new ArrayList<>();
            if (shown != null) {
                for (int cell = 0; cell < p.segments.length; cell++)
                    for (int bit = 0; bit < 7; bit++) if (p.segments[cell][bit] != null && shown.segment(cell, bit)) lit.add(p.segments[cell][bit]);
                if (shown.dp() >= 0 && shown.dp() < p.points.length && p.points[shown.dp()] != null) lit.add(p.points[shown.dp()]);
            }
            MeshRenderer.renderParts(p.fixed, pose, cutout, light, overlay, 1, 1, 1, 1, null);
            if (!lit.isEmpty()) MeshRenderer.renderParts(lit, pose, cutout, light, overlay, 1, 1, 1, 1, null);
            MeshPart lamp = led ? p.ledOn : p.led;
            if (lamp != null) MeshRenderer.renderParts(List.of(lamp), pose, cutout, led ? LightTexture.FULL_BRIGHT : light, overlay, 1, 1, 1, 1, null);
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

    /** The local player's own counter: any first-person draw, or one of its hand stacks in third person. */
    private static boolean live(ItemStack stack, ItemDisplayContext context) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        if (context.firstPerson()) return true;
        return (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                && (stack == player.getMainHandItem() || stack == player.getOffhandItem());
    }

    /** One bone with its pivot at the origin (the equipment bake): parts are drawn without a bone transform. */
    private Parts parts(MeshModel mesh) {
        if (parts != null && parts.mesh == mesh) return parts;
        List<MeshPart> fixed = new ArrayList<>();
        MeshPart[][] segments = new MeshPart[4][7];
        MeshPart[] points = new MeshPart[3];
        MeshPart led = null, ledOn = null;
        for (MeshBone bone : mesh.roots()) {
            if (bone.neverRender()) continue;
            for (MeshPart part : mesh.parts(bone.name(), MeshLayer.CUTOUT)) {
                String name = part.name();
                Matcher s = SEGMENT.matcher(name), d = POINT.matcher(name);
                if (s.matches()) { int cell = Integer.parseInt(s.group(1)); if (cell < 4) segments[cell][s.group(2).charAt(0) - 'a'] = part; }
                else if (d.matches()) { int cell = Integer.parseInt(d.group(1)); if (cell < 3) points[cell] = part; }
                else if (name.equals("led")) led = part;
                else if (name.equals("led_on")) ledOn = part;
                else fixed.add(part);
            }
        }
        parts = new Parts(mesh, List.copyOf(fixed), segments, points, led, ledOn);
        return parts;
    }
}
