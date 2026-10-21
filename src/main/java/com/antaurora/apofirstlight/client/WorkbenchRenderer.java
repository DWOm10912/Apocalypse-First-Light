package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile;
import com.antaurora.apofirstlight.cabin.Workbench;
import com.antaurora.apofirstlight.cabin.WorkbenchBlockEntity;
import com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * AFL Workbench V1 (docs/gameplay/afl_workbench_v1_design.md 14): the bench through the generic animated mesh renderer
 * (the drawers slide; the resting bench drawn by the chunk), then the tools on its pegs in the bench's north frame turned
 * to its facing. A tool hangs flat against the board, head up: the claw hammer as its mesh, its head on the peg pair
 * ({@link #clawHammer}); anything else as the tool rack's (the item sprite's diagonal plumb, the handle's top between the
 * pegs). The resting tools are drawn by the chunk ({@link WoodGunRackChunking}, a recording of {@link #slot}): a tool is
 * drawn here only until the chunk shows it, plus whatever the chunk cannot take.
 */
public final class WorkbenchRenderer implements BlockEntityRenderer<WorkbenchBlockEntity> {
    /** A sprite tool's model square (smaller than the tool rack's 0.58: the board is lower) and its middle under the pegs. */
    private static final double SPRITE_SCALE = 0.5, SPRITE_DROP = 0.29;
    private final AflAnimatedBlockMeshRenderer<WorkbenchBlockEntity> bench;

    public WorkbenchRenderer(BlockEntityRendererProvider.Context context) {
        bench = new AflAnimatedBlockMeshRenderer<>(context);
    }

    @Override
    public boolean shouldRenderOffScreen(WorkbenchBlockEntity entity) { return true; }

    @Override
    public int getViewDistance() { return AflAnimatedBlockMeshRenderer.VIEW_DISTANCE; }

    @Override
    public void render(WorkbenchBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (entity.getLevel() == null) return;
        bench.render(entity, partialTick, pose, buffers, light, overlay);
        boolean shadowPass = AflShaderCompat.activeShadowPass();
        WoodGunRackChunking.Copy chunk = WoodGunRackChunking.shown(entity, shadowPass);   // main pass: also keeps the chunk's copy current
        if (entity.noTools()) return;
        BlockState state = entity.getBlockState();
        int boardLight = LevelRenderer.getLightColor(entity.getLevel(), entity.getBlockPos().above());
        for (int k = 0; k < Workbench.PEGS; k++) {
            ItemStack s = entity.tool(k);
            if (s.isEmpty()) continue;
            if (chunk != null && chunk.has(k, s, state)) {
                chunk.replay(k, pose, buffers, boardLight, shadowPass);
                continue;
            }
            pose.pushPose();
            slot(k, s, state, pose, buffers, boardLight, entity.getBlockPos().hashCode() + k);
            pose.popPose();
        }
    }

    /** Peg k's tool in block space (pose at the master cell's origin), the bench turned to its facing; also the chunk's recording. */
    static void slot(int k, ItemStack s, BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int seed) {
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(AflBlockMeshProfile.facingDegrees(state.getValue(Workbench.FACING))));
        pose.translate(-0.5, 0, -0.5);
        if (s.is(AflItems.CLAW_HAMMER.get())) clawHammer(Workbench.SLOT_X[k], Workbench.PEG_TOP, Workbench.TOOL_Z, false, pose, buffers, light);
        else sprite(s, Workbench.SLOT_X[k], pose, buffers, light, seed);
    }

    private static void sprite(ItemStack s, double x, PoseStack pose, MultiBufferSource buffers, int light, int seed) {
        pose.translate(x, Workbench.PEG_TOP - SPRITE_DROP, Workbench.TOOL_Z);
        pose.mulPose(Axis.YP.rotationDegrees(180));   // the sprite's face to the room (north, -z)
        pose.mulPose(Axis.ZP.rotationDegrees(-45));
        float scale = (float) SPRITE_SCALE;
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(s, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, null, seed);
    }

    /** Claw hammer (equipment meshes, tools/build-equipment-meshes-v1.mjs HAMMER): 33 cm overall, its real size. */
    private static final double HAMMER_SCALE = 0.508;
    /** In the baked mesh (blocks): the handle's axis, and the head's underside over the claw-side peg (bench pegs 3.5 cm, rack 5 cm from the middle). */
    private static final double HAMMER_AXIS_X = -0.2226 / 16, HAMMER_REST_BENCH = 9.5769 / 16, HAMMER_REST_RACK = 9.4977 / 16;
    private static final ResourceLocation HAMMER_GEO = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/claw_hammer.geo.json"),
            HAMMER_TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/claw_hammer_mesh.png");

    /**
     * The claw hammer hung by its head on a peg pair at x (pose in the furniture's north frame): the handle plumb between
     * the pegs, the head across them (face toward -x, claw toward +x), the claw side on its peg's top at pegTop, the
     * hammer's flat side to the board in the plane z. On the tool rack (pegs wider apart) the head sits a little lower.
     */
    static void clawHammer(double x, double pegTop, double z, boolean rack, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.translate(x, pegTop, z);
        float scale = (float) HAMMER_SCALE;
        pose.scale(scale, scale, scale);
        pose.translate(-HAMMER_AXIS_X, -(rack ? HAMMER_REST_RACK : HAMMER_REST_BENCH), 0);
        AflStaticMeshItemRenderer.drawMesh(HAMMER_GEO, HAMMER_TEXTURE, pose, buffers, light, OverlayTexture.NO_OVERLAY);
    }
}
