package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.inventory.grid.RigClips;
import com.antaurora.apofirstlight.registry.AflItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * A worn chest rig with what hangs on its clips (docs/gameplay/equipment_slots_v1.md "挂点"; user 2026-10-21: "如果挂在任意
 * 胸挂上，就会好看很多，并且自动贴合"): the rig's mesh, then each clipped item on its shoulder strap, in the body frame of
 * {@link ClientEquipmentSlots.WornMeshRenderer} (+X the player's left, +Y up, +Z out, origin at the neck). Each rig names
 * its clip spots: where the strap is flat on the upper chest, its front face (measured from the baked mesh).
 * <ul>
 * <li>Pocket compass: hangs face out from a steel carabiner on a webbing tab (mesh clip_mount), its ring swung up over
 * the carabiner's bottom bar (CompassItemRenderer draws ring_up in display context NONE); its card keeps pointing north.
 * It hangs plumb: crouching tips the body, not the compass.</li>
 * <li>Geiger counter: clipped upright on the strap by the clip on its back, face out, top end up; the local player's own
 * shows its live reading (GeigerCounterItemRenderer).</li>
 * </ul>
 * Over a backpack's shoulder strap (front z 2.82, |x| up to 2.45, under the inner edge of the items) they rest that much
 * further out. Sizes keep them clear of the sleeves (inner face |x| 3.75): compass 0.28, Geiger counter 0.19 of the item.
 */
public final class ClipRenderer implements ICurioRenderer {
    private static final ResourceLocation MOUNT_GEO = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/clip_mount.geo.json"),
            MOUNT_TEX = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/clip_mount_mesh.png");
    /** clip_mount: the bake's origin shift (the build prints it), the carabiner's bottom bar in the mount frame (px). */
    static final float MOUNT_OY = 1.205F, MOUNT_OZ = -0.1025F, BAR_Y = -1.16F, BAR_Z = 0.16F, WIRE_R = 0.045F;
    /** Compass: draw scale, and in the baked mesh (px) the swung-up ring's inside top from the centre and its offset in depth. */
    static final float COMPASS_SCALE = 0.28F, RING_TOP = 3.36F, RING_DEPTH = 0.0925F;
    /** Geiger counter: draw scale, its baked length (top to bottom) and thickness (px). */
    static final float GEIGER_SCALE = 0.19F, GEIGER_LENGTH = 15.7F, GEIGER_THICK = 3.12F;
    /** Gap off the strap, and how much further out over a backpack's shoulder strap (its front 2.82). */
    static final float GAP = 0.02F, OVER_PACK_STRAP = 0.04F;

    /**
     * One clip on a rig, body frame px: the compass tab's centre x (the Geiger counter's is the strap's middle), the top of
     * the flat strap where tab and Geiger counter start, the strap's front face.
     */
    public record Spot(float tabX, float strapX, float top, float front) {}

    /** The field chest rig: its straps at x -+2.9, 0.9 wide, flat on the chest from y -0.6 down to the panel at -4.0, front z 2.79. */
    public static final Spot[] FIELD_CHEST_RIG = {new Spot(-2.85F, -2.9F, -0.9F, 2.79F), new Spot(2.85F, 2.9F, -0.9F, 2.79F)};

    private final ClientEquipmentSlots.WornMeshRenderer rig;
    private final Spot[] spots;

    public ClipRenderer(ClientEquipmentSlots.WornMeshRenderer rig, Spot[] spots) {
        this.rig = rig;
        this.spots = spots;
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext, PoseStack pose,
            RenderLayerParent<T, M> parent, MultiBufferSource buffer, int light, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        rig.render(stack, slotContext, pose, parent, buffer, light, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
        if (!(parent.getModel() instanceof HumanoidModel<?> humanoid) || !humanoid.body.visible) return;
        LivingEntity entity = slotContext.entity();
        float lift = AflEquipmentSlots.get(entity, AflEquipmentSlots.BACK).isEmpty() ? 0 : OVER_PACK_STRAP;
        for (int i = 0; i < Math.min(spots.length, RigClips.count(stack)); i++) {
            var clipped = RigClips.get(stack, i);
            if (clipped.isEmpty()) continue;
            pose.pushPose();
            ClientEquipmentSlots.WornMeshRenderer.apply(humanoid, ClientEquipmentSlots.WornMeshRenderer.Part.BODY, pose);
            if (clipped.is(AflItems.COMPASS.get())) compass(entity, humanoid, clipped, spots[i], lift, pose, buffer, light);
            else if (clipped.is(AflItems.GEIGER_COUNTER.get())) geiger(entity, clipped, spots[i], lift, pose, buffer, light);
            pose.popPose();
        }
    }

    private static void compass(LivingEntity entity, HumanoidModel<?> humanoid, ItemStack stack, Spot spot, float lift,
            PoseStack pose, MultiBufferSource buffer, int light) {
        pose.translate(spot.tabX() / 16F, spot.top() / 16F, (spot.front() + GAP) / 16F);
        pose.pushPose();
        pose.translate(0, -MOUNT_OY / 16F, -MOUNT_OZ / 16F);
        AflStaticMeshItemRenderer.drawMesh(MOUNT_GEO, MOUNT_TEX, pose, buffer, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        // hang from the bar, plumb against the body's lean
        float swing = Math.max(0, humanoid.body.xRot);
        pose.translate(0, BAR_Y / 16F, BAR_Z / 16F);
        pose.mulPose(Axis.XP.rotation(-swing));
        pose.translate(0, (WIRE_R - RING_TOP * COMPASS_SCALE) / 16F, (RING_DEPTH * COMPASS_SCALE + lift) / 16F);
        pose.mulPose(Axis.XP.rotationDegrees(90));                      // face out, the ring up
        pose.scale(COMPASS_SCALE, COMPASS_SCALE, COMPASS_SCALE);
        item(entity, stack, pose, buffer, light);
    }

    private static void geiger(LivingEntity entity, ItemStack stack, Spot spot, float lift, PoseStack pose, MultiBufferSource buffer, int light) {
        float length = GEIGER_LENGTH * GEIGER_SCALE, thick = GEIGER_THICK * GEIGER_SCALE;
        pose.translate(spot.strapX() / 16F, (spot.top() - length / 2) / 16F, (spot.front() + GAP + lift + thick / 2) / 16F);
        pose.mulPose(Axis.XP.rotationDegrees(90));                      // face out, top end up
        pose.scale(GEIGER_SCALE, GEIGER_SCALE, GEIGER_SCALE);
        item(entity, stack, pose, buffer, light);
    }

    /** The item at its own size, centred on the pose (display context NONE has no transform). */
    private static void item(LivingEntity entity, ItemStack stack, PoseStack pose, MultiBufferSource buffer, int light) {
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer()
                .renderItem(entity, stack, ItemDisplayContext.NONE, false, pose, buffer, light);
    }
}
