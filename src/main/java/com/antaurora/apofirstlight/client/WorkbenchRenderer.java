package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile;
import com.antaurora.apofirstlight.cabin.Workbench;
import com.antaurora.apofirstlight.cabin.WorkbenchBlockEntity;
import com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer;
import com.antaurora.apofirstlight.client.workbench.WorkbenchWork;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.core.Direction;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * AFL Workbench V1 (docs/gameplay/afl_workbench_v1_design.md 14): the bench through the generic animated mesh renderer
 * (the drawers slide; the resting bench drawn by the chunk), then the tools on its pegs in the bench's north frame turned
 * to its facing. A tool hangs flat against the board, head up: the claw hammer as its mesh, its head on the peg pair
 * ({@link #clawHammer}); the hand saw through its hand hole on both pegs ({@link #handSaw}); anything else as the tool rack's (the item sprite's diagonal plumb, the handle's top between the
 * pegs). The resting tools are drawn by the chunk ({@link WoodGunRackChunking}, a recording of {@link #slot}): a tool is
 * drawn here only until the chunk shows it, plus whatever the chunk cannot take. While a player works at the bench
 * (client/workbench/WorkbenchWork), the log on the top and its kerf ({@link #workpiece}).
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
        BlockState state = entity.getBlockState();
        float work = WorkbenchWork.workpiece(entity.getBlockPos(), partialTick);
        if (work >= 0) {
            pose.pushPose();
            workpiece(work, WorkbenchWork.workLog(entity.getBlockPos(), partialTick), state, pose, buffers, LevelRenderer.getLightColor(entity.getLevel(), entity.getBlockPos().above()));
            pose.popPose();
        }
        if (entity.noTools()) return;
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

    /**
     * The work on the top (WorkbenchWork, the bench's north frame): the recipe's log (WorkbenchWork#workLog) along the bench's depth over the drawers,
     * its front end over the edge, and the kerf sawn into it so far, a dark line down its top from the front end and down
     * the end grain as deep as the saw's slope has gone (the toe 20 deg down).
     */
    private static void workpiece(float progress, @org.jetbrains.annotations.Nullable BlockState log, BlockState bench, PoseStack pose, MultiBufferSource buffers, int light) {
        if (log == null) return;
        log = log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(AflBlockMeshProfile.facingDegrees(bench.getValue(Workbench.FACING))));
        pose.translate(-0.5, 0, -0.5);
        double r = WorkbenchWork.LOG_R, top = WorkbenchWork.TOP + 2 * r, end = WorkbenchWork.LOG_END;
        pose.pushPose();
        pose.translate(WorkbenchWork.LOG_X - r, WorkbenchWork.TOP, end);
        pose.scale((float) (2 * r), (float) (2 * r), (float) (WorkbenchWork.LOG_FAR - end));
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(log, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
        double kerf = WorkbenchWork.kerfZ(progress);
        if (kerf <= end + 0.005) return;
        TextureAtlasSprite bark = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(log);
        float u = (bark.getU0() + bark.getU1()) / 2, v = (bark.getV0() + bark.getV1()) / 2;
        VertexConsumer out = buffers.getBuffer(RenderType.entitySolid(InventoryMenu.BLOCK_ATLAS));
        float x0 = (float) (WorkbenchWork.LOG_X - 0.006), x1 = (float) (WorkbenchWork.LOG_X + 0.006);
        float y = (float) (top + 0.0015), zEnd = (float) (end - 0.0015);
        float deep = (float) Math.min(2 * r - 0.01, (kerf - end) * Math.tan(Math.toRadians(20)) + 0.03);
        kerfQuad(out, pose, x0, y, zEnd, x1, y, (float) kerf, 0, 1, 0, u, v, light);   // down the top
        kerfQuad(out, pose, x0, (float) top - deep, zEnd, x1, (float) top, zEnd, 0, 0, -1, u, v, light);   // down the end grain
    }

    /** A dark kerf strip from corner a to corner b (flat on one axis), both sides, the given normal. */
    private static void kerfQuad(VertexConsumer out, PoseStack pose, float ax, float ay, float az, float bx, float by, float bz,
                                 float nx, float ny, float nz, float u, float v, int light) {
        float[][] corners = ay == by
                ? new float[][]{{ax, ay, az}, {ax, ay, bz}, {bx, ay, bz}, {bx, ay, az}}
                : new float[][]{{ax, ay, az}, {ax, by, az}, {bx, by, az}, {bx, ay, az}};
        var last = pose.last();
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 4; i++) {
                float[] c = corners[side == 0 ? i : 3 - i];
                out.vertex(last.pose(), c[0], c[1], c[2]).color(46, 36, 26, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(light).normal(last.normal(), side == 0 ? nx : -nx, side == 0 ? ny : -ny, side == 0 ? nz : -nz).endVertex();
            }
        }
    }

    /** Peg k's tool in block space (pose at the master cell's origin), the bench turned to its facing; also the chunk's recording. */
    static void slot(int k, ItemStack s, BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int seed) {
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(AflBlockMeshProfile.facingDegrees(state.getValue(Workbench.FACING))));
        pose.translate(-0.5, 0, -0.5);
        if (s.is(AflItems.CLAW_HAMMER.get())) clawHammer(Workbench.SLOT_X[k], Workbench.PEG_TOP, Workbench.TOOL_Z, false, pose, buffers, light);
        else if (s.is(AflItems.BUILDING_HAMMER.get())) buildingHammer(Workbench.SLOT_X[k], Workbench.PEG_TOP, Workbench.TOOL_Z, false, pose, buffers, light);
        else if (s.is(AflItems.HAND_SAW.get())) handSaw(Workbench.SLOT_X[k], Workbench.PEG_TOP - BENCH_PEG_RADIUS, Workbench.TOOL_Z, false, pose, buffers, light);
        else if (s.is(AflItems.CROWBAR.get())) crowbar(Workbench.SLOT_X[k], Workbench.PEG_TOP - BENCH_PEG_RADIUS, Workbench.TOOL_Z, false, pose, buffers, light);
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

    /**
     * Building hammer (tools/build-equipment-meshes-v1.mjs buildingHammer, docs/gameplay/building_hammer_b0.md): the claw
     * hammer's scale (34 cm overall). In the baked mesh (blocks): the handle on x = 0, the head's flat underside over the
     * pegs (bench 9.36 px, rack 9.33 px; the head is 11.8 cm long, both pegs under it), its half depth 0.63 px.
     */
    private static final double CLUB_REST_BENCH = 9.36 / 16, CLUB_REST_RACK = 9.33 / 16;
    /** Its head is deeper than the claw hammer's (0.63 against 0.46 px half depth): out from the board by the difference, the backs in one plane. */
    private static final double CLUB_OUT = (0.63 - 0.46) / 16 * HAMMER_SCALE;
    private static final ResourceLocation CLUB_GEO = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/building_hammer.geo.json"),
            CLUB_TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/building_hammer_mesh.png");

    /** The building hammer hung by its head on a peg pair at x, as the claw hammer: handle plumb between the pegs, head across them. */
    static void buildingHammer(double x, double pegTop, double z, boolean rack, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.translate(x, pegTop, z - CLUB_OUT);
        float scale = (float) HAMMER_SCALE;
        pose.scale(scale, scale, scale);
        pose.translate(0, -(rack ? CLUB_REST_RACK : CLUB_REST_BENCH), 0);
        AflStaticMeshItemRenderer.drawMesh(CLUB_GEO, CLUB_TEXTURE, pose, buffers, light, OverlayTexture.NO_OVERLAY);
    }

    /** The bench pegs' radius (tools/build-afl-workbench-v1.mjs PEG_R): their centres are this far under PEG_TOP. */
    private static final double BENCH_PEG_RADIUS = 0.01;
    /**
     * Hand saw (tools/build-equipment-meshes-v1.mjs handSaw, 2026-10-21): real size (0.78 m overall), worked out offline
     * from its outline and masses (steel 0.49 kg, apple 0.37 kg). On the bench both pegs (7 cm apart, 2 cm across) pass
     * through the 9.9 cm hand hole and the saw settles where its centre of mass is lowest: turned 160.5 deg, the toe down
     * and 20 deg aside, the centre of mass 3.4 cm off the slot's middle (between the pegs), the toe 70 cm under them. On the
     * tool rack the pegs are 10 cm apart, too wide for the hole: it hangs from the -x peg alone, its centre of mass plumb
     * under the peg's centre (turned 178.2 deg), the +x peg clear of it. Each: the mesh point (baked px) that sits on the
     * slot's middle at the pegs' centre height.
     */
    private static final float SAW_TURN_BENCH = 160.5F, SAW_TURN_RACK = 178.22F;
    private static final double[] SAW_AT_BENCH = {0.2642 / 16, 2.6246 / 16}, SAW_AT_RACK = {-2.1565 / 16, 2.1352 / 16};
    private static final ResourceLocation SAW_GEO = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/hand_saw.geo.json"),
            SAW_TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/hand_saw_mesh.png");

    /** The hand saw hung by its hand hole on the peg pair at x (pegs' centres at pegY), its flat side to the board in the plane z. */
    static void handSaw(double x, double pegY, double z, boolean rack, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.translate(x, pegY, z);
        pose.mulPose(Axis.ZP.rotationDegrees(rack ? SAW_TURN_RACK : SAW_TURN_BENCH));
        float scale = (float) HAMMER_SCALE;
        pose.scale(scale, scale, scale);
        double[] at = rack ? SAW_AT_RACK : SAW_AT_BENCH;
        pose.translate(-at[0], -at[1], 0);
        AflStaticMeshItemRenderer.drawMesh(SAW_GEO, SAW_TEXTURE, pose, buffers, light, OverlayTexture.NO_OVERLAY);
    }

    /**
     * Crowbar (tools/build-equipment-meshes-v1.mjs crowbar, 2026-10-21): real size (86 cm, 5.1 kg of steel), hung by its
     * gooseneck, worked out offline from its centreline and volume. The hook is 7.9 cm across inside, too small for a peg
     * pair: it hangs on one peg, the other clear. On the tool rack it hangs free from the -x peg (turned -8 deg, its centre
     * of mass plumb under the hook's centre, 84 cm down). On the bench the pegs are only 72 cm over the top: it hangs from
     * the +x peg with its chisel standing on the top, leaning toward -x (turned -37.75 deg, the tip 38 cm aside, under the
     * next slot). Each: the mesh point (baked px) that sits on the slot's middle at the pegs' centre height.
     */
    private static final float BAR_TURN_BENCH = -37.75F, BAR_TURN_RACK = -8F;
    private static final double[] BAR_AT_BENCH = {-1.8002 / 16, 25.3175 / 16}, BAR_AT_RACK = {1.1419 / 16, 26.4254 / 16};
    /**
     * Out from the board: on the bench 3.3 cm further out on its pegs, so its lower part passes in front of a saw on the
     * next slot (the hook still 0.8 cm on the pegs' ends); on the rack by its depth over the claw hammer's (0.56 against 0.46 px).
     */
    private static final double BAR_OUT_BENCH = 0.033, BAR_OUT_RACK = (0.56 - 0.46) / 16 * HAMMER_SCALE;
    private static final ResourceLocation BAR_GEO = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/crowbar.geo.json"),
            BAR_TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/crowbar_mesh.png");

    /** The crowbar hung by its gooseneck on the peg pair at x (pegs' centres at pegY), its flat side to the board in the plane z. */
    static void crowbar(double x, double pegY, double z, boolean rack, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.translate(x, pegY, z - (rack ? BAR_OUT_RACK : BAR_OUT_BENCH));
        pose.mulPose(Axis.ZP.rotationDegrees(rack ? BAR_TURN_RACK : BAR_TURN_BENCH));
        float scale = (float) HAMMER_SCALE;
        pose.scale(scale, scale, scale);
        double[] at = rack ? BAR_AT_RACK : BAR_AT_BENCH;
        pose.translate(-at[0], -at[1], 0);
        AflStaticMeshItemRenderer.drawMesh(BAR_GEO, BAR_TEXTURE, pose, buffers, light, OverlayTexture.NO_OVERLAY);
    }
}
