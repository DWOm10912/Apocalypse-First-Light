package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile;
import com.antaurora.apofirstlight.cabin.WoodFurniture;
import com.antaurora.apofirstlight.block.LogMount;
import com.antaurora.apofirstlight.cabin.WoodFurnitureV2;
import com.antaurora.apofirstlight.cabin.WoodGunRackBlockEntity;
import com.antaurora.apofirstlight.cabin.WoodToolRackBlockEntity;
import com.antaurora.apofirstlight.item.ShovelItem;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;

/**
 * Wood Furniture V1 (docs/models/wood_furniture_v1.md): draws what hangs on a wooden gun rack, in the rack's north frame
 * (tools/build-wood-furniture-v1.mjs) turned to its facing. Guns are drawn at their real length in the bind pose the gun
 * bench uses ({@link MaintenanceGunRendering#renderBindPose}: magazine, sight and attachments as fitted).
 * <ul>
 * <li>Notches (slots 0..2): the long gun across the rack, muzzle toward +x (the viewer's left), its left side out; it rests
 * on both brackets (x 0.1 and 0.9): the lowest geometry over each bracket sits on the notch level, the gun tilting the
 * little it takes (a stock is deeper than a barrel).</li>
 * <li>Shelf (3, 4): a pistol lies on its left side, muzzle +x, grip toward the wall; anything else lies flat (its
 * display-frame model at 25 cm).</li>
 * </ul>
 * <p>Wood Furniture V2 (docs/models/wood_furniture_v2.md, 2026-10-15): the wooden tool rack's block entity is this one's
 * ({@link WoodToolRackBlockEntity}), drawn here too in its own frame (tools/build-wood-furniture-v2.mjs, moved out onto its
 * pad on round logs): pegs (0..2) hang a tool by its handle, head up, the item sprite's diagonal plumb (its model at
 * {@link WoodFurnitureV2.ToolRack#TOOL_SCALE}); the shelf (3, 4) as the gun rack's.
 * The felling axe hangs as itself instead: its mesh, head on the pegs (2026-10-20); so does the claw hammer (2026-10-21); the
 * round point shovel by its D grip from one peg, blade down (2026-10-22).
 * <p>Resting items are drawn by the chunk ({@link WoodGunRackChunking}, a recording of this drawing): a slot is drawn here
 * only until the chunk shows it, plus whatever the chunk cannot take.
 */
public final class WoodGunRackRenderer implements BlockEntityRenderer<WoodGunRackBlockEntity> {
    /** Overall length (m) per gun; the models are not drawn to scale. Others: 0.21 (pistols) or 1.0. */
    private static final Map<String, Double> LENGTH = Map.of("silverwood_12", 1.0, "br51_01", 1.05, "cat", 0.9, "hr55", 1.4,
            "p9_01", 0.2, "blackridge_50", 0.27);
    private static final double BRACKET = 0.4, BRACKET_HALF = 0.02, MAX_TILT = Math.toRadians(12);
    public WoodGunRackRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(WoodGunRackBlockEntity rack, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        boolean shadowPass = AflShaderCompat.activeShadowPass();
        WoodGunRackChunking.Copy chunk = WoodGunRackChunking.shown(rack, shadowPass);   // main pass: also keeps the chunk's copy current
        if (rack.isEmpty()) return;
        BlockState state = rack.getBlockState();
        for (int k = 0; k < WoodGunRackBlockEntity.SLOTS; k++) {
            ItemStack s = rack.item(k);
            if (s.isEmpty()) continue;
            if (chunk != null && chunk.has(k, s, state)) {
                chunk.replay(k, pose, buffers, light, shadowPass);
                continue;
            }
            pose.pushPose();
            slot(k, s, state, pose, buffers, light, rack.getBlockPos().hashCode() + k);
            pose.popPose();
        }
    }

    /** Slot k's item in block space (pose at the block's origin), the rack turned to its facing; also the chunk's recording. */
    static void slot(int k, ItemStack s, BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int seed) {
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(AflBlockMeshProfile.facingDegrees(facing)));
        pose.translate(-0.5, 0, -0.5);
        if (state.getBlock() instanceof WoodFurnitureV2.ToolRack) {
            pose.translate(0, 0, -LogMount.depth(state));
            if (k < 3) tool(s, WoodFurnitureV2.ToolRack.TOOL_X[k], pose, buffers, light, seed);
            else flat(s, WoodFurnitureV2.ToolRack.SHELF_X[k - 3], WoodFurnitureV2.ToolRack.SHELF_Y, WoodFurnitureV2.ToolRack.SHELF_Z, pose, buffers, light, seed);
            return;
        }
        if (k < 3) longGun(s, WoodFurniture.GunRack.NOTCH_Y[k], pose, buffers, light);
        else shelf(s, WoodFurniture.GunRack.SHELF_X[k - 3], pose, buffers, light, seed);
    }

    /** Real overall length (m) of a gun stack (also the body-worn guns, client/inventory/CarriedWeaponsLayer). */
    public static double length(ItemStack s, boolean pistol) {
        String id = s.getItem() instanceof NativeGunItem g ? g.definition().id().getPath() : "";
        return LENGTH.getOrDefault(id, pistol ? 0.21 : 1.0);
    }

    /** Model-space bounds of the whole gun: {minX, minY, minZ, maxX, maxY, maxZ}, or null. */
    private static double[] extent(List<AABB> boxes) {
        if (boxes.isEmpty()) return null;
        double[] e = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (AABB b : boxes) {
            e[0] = Math.min(e[0], b.minX); e[1] = Math.min(e[1], b.minY); e[2] = Math.min(e[2], b.minZ);
            e[3] = Math.max(e[3], b.maxX); e[4] = Math.max(e[4], b.maxY); e[5] = Math.max(e[5], b.maxZ);
        }
        return e[3] > e[0] && e[5] > e[2] ? e : null;
    }

    /** The lowest geometry within half-width w of the model-space station z (fallback: the whole gun's). */
    private static double lowestAt(List<AABB> boxes, double z, double w, double fallback) {
        double low = Double.MAX_VALUE;
        for (AABB b : boxes) if (b.minZ <= z + w && b.maxZ >= z - w) low = Math.min(low, b.minY);
        return low == Double.MAX_VALUE ? fallback : low;
    }

    private static void longGun(ItemStack s, double notchY, PoseStack pose, MultiBufferSource buffers, int light) {
        List<AABB> boxes = MaintenanceGunRendering.bounds(s);
        double[] e = extent(boxes);
        if (e == null) return;
        double scale = length(s, false) / (e[5] - e[2]), cx = (e[0] + e[3]) / 2, cz = (e[2] + e[5]) / 2;
        // the stations over the brackets (the muzzle, model -z, toward the bracket at x 0.9), kept on the gun
        double reach = Math.min(BRACKET / scale, (e[5] - e[2]) / 2), w = BRACKET_HALF / scale;
        double yMuzzle = lowestAt(boxes, cz - reach, w, e[1]), yStock = lowestAt(boxes, cz + reach, w, e[1]);
        double tilt = Math.max(-MAX_TILT, Math.min(MAX_TILT, -Math.atan2((yMuzzle - yStock) * scale, 2 * reach * scale)));
        pose.translate(0.5, notchY, WoodFurniture.GunRack.GUN_Z);
        pose.mulPose(Axis.ZP.rotation((float) tilt));
        pose.mulPose(Axis.YP.rotationDegrees(-90));
        pose.scale((float) scale, (float) scale, (float) scale);
        pose.translate(-cx, -(yMuzzle + yStock) / 2, -cz);
        MaintenanceGunRendering.renderBindPose(s, pose, buffers, light);
    }

    private static void shelf(ItemStack s, double x, PoseStack pose, MultiBufferSource buffers, int light, int seed) {
        if (s.getItem() instanceof NativeGunItem) {
            List<AABB> boxes = MaintenanceGunRendering.bounds(s);
            double[] e = extent(boxes);
            if (e == null) return;
            double scale = length(s, true) / (e[5] - e[2]);
            pose.translate(x, WoodFurniture.GunRack.SHELF_Y, WoodFurniture.GunRack.SHELF_Z);
            pose.mulPose(Axis.YP.rotationDegrees(-90));
            pose.mulPose(Axis.ZP.rotationDegrees(90));   // the right side up: model +x -> up
            pose.scale((float) scale, (float) scale, (float) scale);
            pose.translate(-e[0], -(e[1] + e[4]) / 2, -(e[2] + e[5]) / 2);
            MaintenanceGunRendering.renderBindPose(s, pose, buffers, light);
            return;
        }
        flat(s, x, WoodFurniture.GunRack.SHELF_Y, WoodFurniture.GunRack.SHELF_Z, pose, buffers, light, seed);
    }

    /** An item lying flat on a shelf (top at y, its middle at x, z): its display-frame model at 25 cm. */
    private static void flat(ItemStack s, double x, double y, double z, PoseStack pose, MultiBufferSource buffers, int light, int seed) {
        pose.translate(x, y + 0.01, z - 0.02);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(0.25F, 0.25F, 0.25F);
        Minecraft.getInstance().getItemRenderer().renderStatic(s, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, null, seed);
    }

    /**
     * A tool hung on a peg pair at x, flat against the board: the item sprite's handle-to-head diagonal (bottom left to top
     * right) turned plumb, head up, the handle's top resting between the pegs.
     */
    private static void tool(ItemStack s, double x, PoseStack pose, MultiBufferSource buffers, int light, int seed) {
        if (s.is(AflItems.FELLING_AXE.get())) {
            fellingAxe(x, pose, buffers, light);
            return;
        }
        if (s.is(AflItems.CLAW_HAMMER.get())) {   // its mesh, the head on the pegs (AFL Workbench V1, 2026-10-21)
            WorkbenchRenderer.clawHammer(x, WoodFurnitureV2.ToolRack.PEG_Y + WoodFurnitureV2.ToolRack.PEG_RADIUS, WoodFurnitureV2.ToolRack.TOOL_Z, true, pose, buffers, light);
            return;
        }
        if (s.is(AflItems.BUILDING_HAMMER.get())) {   // the same way (building hammer B0, 2026-10-21)
            WorkbenchRenderer.buildingHammer(x, WoodFurnitureV2.ToolRack.PEG_Y + WoodFurnitureV2.ToolRack.PEG_RADIUS, WoodFurnitureV2.ToolRack.TOOL_Z, true, pose, buffers, light);
            return;
        }
        if (s.is(AflItems.HAND_SAW.get())) {
            // by its hand hole from one peg (2026-10-21); its heel reaches 17 cm to -x at the axe heads' height, so it hangs
            // 2.5 cm further out on the pegs (2.4 cm thick, the pegs' ends at z 0.86): in the middle slot it passes in front
            // of the x 0.2 axe's bit (out to x 0.356, its last 3 cm under 0.5 cm thick)
            WorkbenchRenderer.handSaw(x, WoodFurnitureV2.ToolRack.PEG_Y, WoodFurnitureV2.ToolRack.TOOL_Z - 0.025, true, pose, buffers, light);
            return;
        }
        if (s.is(AflItems.CROWBAR.get())) {   // by its gooseneck from the -x peg, hanging free (2026-10-21)
            WorkbenchRenderer.crowbar(x, WoodFurnitureV2.ToolRack.PEG_Y, WoodFurnitureV2.ToolRack.TOOL_Z, true, pose, buffers, light);
            return;
        }
        if (s.getItem() instanceof ShovelItem) {
            shovel(x, pose, buffers, light);
            return;
        }
        pose.translate(x, WoodFurnitureV2.ToolRack.TOOL_MID_Y, WoodFurnitureV2.ToolRack.TOOL_Z);
        pose.mulPose(Axis.YP.rotationDegrees(180));   // the sprite's face to the room (north, -z)
        pose.mulPose(Axis.ZP.rotationDegrees(-45));
        float scale = (float) WoodFurnitureV2.ToolRack.TOOL_SCALE;
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(s, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, null, seed);
    }

    /** The shovel's peg: the inner one of the pair; its D's plane per slot (the middle one further out). */
    private static final double SHOVEL_PEG_DX = 0.05, SHOVEL_Z_OUTER = 0.95, SHOVEL_Z_MIDDLE = 0.878;

    /**
     * Round point shovel on the rack (docs/worldgen/earthwork_a2.md 4.2, user 2026-10-22): hung by the middle of its D grip's
     * crossbar from one peg, plumb, blade down with its load face to the room; 1.48 m, so the blade hangs 67 cm into the cell
     * below. The inner peg of the pair: the D is 16.5 cm across, on an outer peg it would go through the end bracket, on
     * the inner one it clears a neighbour axe's bit by 1.2 cm. The blade is 34 cm wide and neighbouring handles are 20 or
     * 30 cm apart, so the middle shovel hangs 7.2 cm further out on its peg (the crossbar 2 mm inside the peg's end; the
     * outer ones 1 cm off the board): measured on the mesh, 6.8 cm keeps two blades 20 cm apart from meeting. Drawn empty
     * (a loaded shovel is not taken).
     */
    private static void shovel(double x, PoseStack pose, MultiBufferSource buffers, int light) {
        boolean middle = Math.abs(x - 0.5) < 0.01;
        double peg = x < 0.5 ? x + SHOVEL_PEG_DX : x - SHOVEL_PEG_DX;
        pose.translate(peg, WoodFurnitureV2.ToolRack.PEG_Y + WoodFurnitureV2.ToolRack.PEG_RADIUS, middle ? SHOVEL_Z_MIDDLE : SHOVEL_Z_OUTER);
        pose.mulPose(Axis.YP.rotationDegrees(180));   // the blade (mesh +z) to the room, -z
        pose.translate(0, -ShovelItemRenderer.HANG_Y, -ShovelItemRenderer.HANDLE_Z);
        ShovelItemRenderer.drawEmpty(pose, buffers, light, OverlayTexture.NO_OVERLAY);
    }

    /** Felling axe on the rack (docs/gameplay/felling_axe_v1.md 6): its mesh, about 0.96 m overall (a full 36-inch axe). */
    private static final double AXE_SCALE = 0.59;
    /** In the baked mesh (blocks): the handle's middle at the eye, and the head's underside over the bit-side peg. */
    private static final double AXE_EYE_X = 1.3656 / 16, AXE_REST_Y = 23.400 / 16;
    private static final ResourceLocation AXE_GEO = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/felling_axe.geo.json"),
            AXE_TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/felling_axe_mesh.png");

    /**
     * The handle between the pegs, plumb; the head rests on the pegs' tops (the bit's peg touches, the poll's is 3 mm
     * clear), the handle's knob 11 cm below the cell, its top 1 cm under the shelf. The bit (15.6 cm out from the handle)
     * points to the rack's middle (user 2026-10-20: on the x 0.2 pegs, bit toward -x, it went through the end bracket):
     * the outer slots in the plane z TOOL_Z, the middle one (bit toward -x) 2 cm further out on its pegs, so its edge
     * passes in front of the x 0.2 axe's (the two edges overlap 1.2 cm across). Fixed per slot: the chunk's recording is
     * per slot, never per neighbour.
     */
    private static void fellingAxe(double x, PoseStack pose, MultiBufferSource buffers, int light) {
        double pegTop = WoodFurnitureV2.ToolRack.PEG_Y + WoodFurnitureV2.ToolRack.PEG_RADIUS;
        boolean middle = Math.abs(x - 0.5) < 0.01;
        pose.translate(x, pegTop, WoodFurnitureV2.ToolRack.TOOL_Z - (middle ? 0.02 : 0));
        if (x < 0.5 - 0.01) pose.mulPose(Axis.YP.rotationDegrees(180));   // bit toward +x, about the handle
        float scale = (float) AXE_SCALE;
        pose.scale(scale, scale, scale);
        pose.translate(-AXE_EYE_X, -AXE_REST_Y, 0);
        AflStaticMeshItemRenderer.drawMesh(AXE_GEO, AXE_TEXTURE, pose, buffers, light, OverlayTexture.NO_OVERLAY);
    }
}
