package com.antaurora.apofirstlight.dev.terrainsmooth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Terrain smoothing prototype P1, client: the selection outline of a smoothed natural block follows its drawn top (the
 * four corners and the fold along the nw-se diagonal, down to the floor) instead of the box steps of its selection shape
 * or the vanilla cube. Only while collision and selection follow the slopes; otherwise vanilla draws its outline.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT)
public final class SmoothOutline {
    private SmoothOutline() {}

    @SubscribeEvent
    public static void outline(RenderHighlightEvent.Block event) {
        if (FMLEnvironment.production) return;
        var c = SmoothWorld.CLIENT;
        var level = Minecraft.getInstance().level;
        if (!c.active() || !c.shapes || level == null) return;
        BlockPos pos = event.getTarget().getBlockPos();
        if (!c.contains(pos.getX(), pos.getZ())) return;
        BlockState state = level.getBlockState(pos);
        if (!SmoothWorld.surface(state.getBlock())) return;
        long g = c.cachedGeometry(level, pos);
        if (g < 0) return;
        event.setCanceled(true);
        float[] k = SmoothRules.corners(g);   // nw, ne, sw, se
        float[] nw = {0, k[0], 0}, ne = {1, k[1], 0}, sw = {0, k[2], 1}, se = {1, k[3], 1};
        Vec3 cam = event.getCamera().getPosition();
        double ox = pos.getX() - cam.x, oy = pos.getY() - cam.y, oz = pos.getZ() - cam.z;
        VertexConsumer out = event.getMultiBufferSource().getBuffer(RenderType.lines());
        PoseStack.Pose pose = event.getPoseStack().last();
        float[][][] edges = {
                {nw, ne}, {ne, se}, {se, sw}, {sw, nw}, {nw, se},                                   // top and its fold
                {{0, 0, 0}, nw}, {{1, 0, 0}, ne}, {{0, 0, 1}, sw}, {{1, 0, 1}, se},                 // corners down to the floor
                {{0, 0, 0}, {1, 0, 0}}, {{1, 0, 0}, {1, 0, 1}}, {{1, 0, 1}, {0, 0, 1}}, {{0, 0, 1}, {0, 0, 0}}};
        for (float[][] e : edges) line(out, pose, ox, oy, oz, e[0], e[1]);
    }

    private static void line(VertexConsumer out, PoseStack.Pose pose, double ox, double oy, double oz, float[] a, float[] b) {
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-6F) return;
        dx /= l; dy /= l; dz /= l;
        out.vertex(pose.pose(), (float) (ox + a[0]), (float) (oy + a[1]), (float) (oz + a[2])).color(0, 0, 0, 0.4F).normal(pose.normal(), dx, dy, dz).endVertex();
        out.vertex(pose.pose(), (float) (ox + b[0]), (float) (oy + b[1]), (float) (oz + b[2])).color(0, 0, 0, 0.4F).normal(pose.normal(), dx, dy, dz).endVertex();
    }
}
