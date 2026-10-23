package com.antaurora.apofirstlight.client.inventory;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.MaintenanceGunRendering;
import com.antaurora.apofirstlight.client.sling.SlingStraps;
import com.antaurora.apofirstlight.client.ClientEquipmentSlots;
import com.antaurora.apofirstlight.client.WoodGunRackRenderer;
import com.antaurora.apofirstlight.client.skeleton.SkeletonGunStance;
import com.antaurora.apofirstlight.inventory.grid.CarriedWeaponsSync;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.antaurora.apofirstlight.skeleton.GunStance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;

/**
 * Weapons carried on the body (docs/ui/afl_inventory_grid_v0_plan.md 10): the slung primary (key 1) diagonally across the
 * back, muzzle up over the left shoulder, its side against the back (over the harness straps); a backpack worn goes on over
 * it, so the pack hides the gun's middle and the muzzle and butt show past it (the user's choice, 2026-10-15, after the gun
 * on the pack's outside and then hanging from the left shoulder both looked wrong); its sling
 * across the body from the left shoulder to the right hip (mesh gun_sling_worn, drawn while the primary carries a sling,
 * also with the gun in the hand); the holstered pistol (key 2, as large as in the hand in third person) muzzle down in the holster's shell on the outside of the
 * right thigh, slide to the front, grip up and to the rear, moving with the leg (the holster is drawn by its Curios
 * renderer, ClientEquipmentSlots). Neither gun is drawn while it is in the hand. Guns are drawn at their real length in
 * the gun bench's bind pose (as on the gun rack), a long gun the skeleton carries at its body size
 * (GunStance.Carried#bodyScale, BR51 1.25 x, as in the hands). A gun that clips gets its own offset in {@link #BACK_OFFSET} (like the
 * rack's per-gun lengths). The own player's weapons come from its inventory, the other players' from the server
 * (inventory/grid/CarriedWeaponsSync, same rules, 2026-10-15). The melee weapon is not drawn yet.
 * <p>
 * Player skeleton (docs/dev/player_skeleton_v1_design.md 13): {@link #backFrame} / {@link #thighFrame} are where a drawn
 * gun starts and a gun put away ends (client/skeleton/SkeletonGunStance); while it is on its way the gun is not drawn
 * here. The holstered pistol keeps its holster size (the hand's third-person size, which the holster shell was built for);
 * a skeleton draw takes it to real size along the path (GunStance#blend scales).
 */
public final class CarriedWeaponsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final float PX = 1 / 16F;
    /** Per gun (id path): {dx, dy, tilt degrees} added to the back pose, body pixels. */
    private static final Map<String, float[]> BACK_OFFSET = Map.of();
    /**
     * Back surface (px behind the body's centre, over the harness straps). A pack's body starts 2.35 px behind the centre and
     * is 2.65 (canvas) / 4.0 (hiking) deep, so a gun on this surface (flank 1-1.5 px thick) stays inside the pack where they cross.
     */
    private static final float BACK_TILT = 38, BACK_Y = -6.5F, BACK_SURFACE = 3.05F;
    /** The holster's pocket in the leg frame (tools/build-equipment-meshes-v1.mjs HOLSTER): centre x, floor + 0.25, front wall. */
    private static final float HOLSTER_X = 3.25F, HOLSTER_MUZZLE_Y = -7.15F, HOLSTER_FRONT_Z = -0.98F;

    public CarriedWeaponsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        // the own player from its inventory; the others as the server sent them (their inventories are not synced)
        var shown = player == Minecraft.getInstance().player ? CarriedWeaponsSync.of(player) : CarriedWeaponsSync.received(player.getId());
        if (shown == null) return;
        Matrix4f back = shown.back().getItem() instanceof NativeGunItem && !SkeletonGunStance.hidesCarried(player, shown.back())
                ? backFrame(getParentModel(), shown.back()) : null;
        draw(pose, buffers, light, shown.back(), back);
        // the sling's strap, from the gun's swivels round the body: the gun in the hand or on the back (client/sling)
        SlingStraps.carried(player, getParentModel(), pose, buffers, light, shown.back(), back);
        if (shown.thigh().getItem() instanceof NativeGunItem && !SkeletonGunStance.hidesCarried(player, shown.thigh()))
            draw(pose, buffers, light, shown.thigh(), thighFrame(getParentModel(), shown.thigh()));
    }

    /** A carried gun in its bind pose at a model-space frame (blocks); its strap is drawn round the body (SlingStraps#carried). */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int light, ItemStack gun, Matrix4f frame) {
        if (frame == null) return;
        pose.pushPose();
        pose.mulPoseMatrix(frame);
        MaintenanceGunRendering.renderBindPose(gun, pose, buffers, light, false);
        pose.popPose();
    }

    /** The slung gun on the back: its model (bind pose, blocks) to the player model's frame (blocks), or null. */
    public static Matrix4f backFrame(PlayerModel<?> model, ItemStack gun) {
        float surface = BACK_SURFACE;
        var e = extent(gun);
        if (e == null) return null;
        String id = ((NativeGunItem) gun.getItem()).definition().id().getPath();
        // as large as in the hands (a long gun is larger than real on the body: GunStance.Carried#bodyScale)
        double scale = WoodGunRackRenderer.length(gun, false) / (e[5] - e[2]) * GunStance.bodyScale(id);
        float half = (float) ((e[3] - e[0]) * scale / 2);
        float[] o = BACK_OFFSET.getOrDefault(id, new float[3]);
        PoseStack pose = new PoseStack();
        model.body.translateAndRotate(pose);
        pose.mulPose(Axis.ZP.rotationDegrees(180));   // body frame: y up, x the player's right, z toward the back
        pose.translate((o[0]) * PX, (BACK_Y + o[1]) * PX, surface * PX + half + 0.01F);
        pose.mulPose(Axis.ZP.rotationDegrees(BACK_TILT + o[2]));   // muzzle toward the left shoulder
        pose.mulPose(Axis.YP.rotationDegrees(90));    // the gun's top to the side, its flank against the back
        pose.mulPose(Axis.XP.rotationDegrees(90));    // muzzle (model -z) up
        pose.scale((float) scale, (float) scale, (float) scale);
        pose.translate(-(e[0] + e[3]) / 2, -(e[1] + e[4]) / 2, -(e[2] + e[5]) / 2);
        return new Matrix4f(pose.last().pose());
    }

    /** The holstered pistol on the right thigh: its model (bind pose, blocks) to the player model's frame (blocks), or null. */
    public static Matrix4f thighFrame(PlayerModel<?> model, ItemStack gun) {
        var e = extent(gun);
        if (e == null) return null;
        // the holster was sized for this (user PASS); a skeleton draw grows / shrinks it to real size along its path
        float scale = handScale(gun);
        if (!(scale > 0)) scale = (float) (WoodGunRackRenderer.length(gun, true) / (e[5] - e[2]));
        PoseStack pose = new PoseStack();
        ClientEquipmentSlots.WornMeshRenderer.apply(model, ClientEquipmentSlots.WornMeshRenderer.Part.RIGHT_LEG, pose);
        // the muzzle's top edge (model: min z, max y, mid x) to the pocket's floor at its front wall
        pose.translate(HOLSTER_X * PX, HOLSTER_MUZZLE_Y * PX, HOLSTER_FRONT_Z * PX);
        pose.mulPose(Axis.XP.rotationDegrees(-90));   // model (x, y, z) -> (x, z, -y): muzzle down, top to the front, grip to the rear
        pose.scale(scale, scale, scale);
        pose.translate(-(e[0] + e[3]) / 2, -e[4], -e[2]);
        return new Matrix4f(pose.last().pose());
    }

    /**
     * The gun's scale in the hand in third person (its item model's thirdperson_righthand), so a holstered pistol is as large
     * as the one drawn from it (P9 about 1.9x its real length; at the real length it was hardly visible), or 0.
     */
    private static float handScale(ItemStack gun) {
        var model = Minecraft.getInstance().getItemRenderer().getModel(gun, null, null, 0);
        var probe = new PoseStack();
        model.applyTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, probe, false);
        return probe.last().pose().getScale(new org.joml.Vector3f()).x();
    }

    /** Model-space bounds of the whole gun {minX, minY, minZ, maxX, maxY, maxZ}, or null. */
    private static double[] extent(ItemStack gun) {
        List<AABB> boxes = MaintenanceGunRendering.bounds(gun);
        if (boxes.isEmpty()) return null;
        double[] e = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (AABB b : boxes) {
            e[0] = Math.min(e[0], b.minX); e[1] = Math.min(e[1], b.minY); e[2] = Math.min(e[2], b.minZ);
            e[3] = Math.max(e[3], b.maxX); e[4] = Math.max(e[4], b.maxY); e[5] = Math.max(e[5], b.maxZ);
        }
        return e[3] > e[0] && e[5] > e[2] ? e : null;
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void layers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new CarriedWeaponsLayer(renderer));
            }
        }
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
    public static final class Session {
        private Session() {}

        @SubscribeEvent
        public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            CarriedWeaponsSync.clearReceived();
        }
    }
}
