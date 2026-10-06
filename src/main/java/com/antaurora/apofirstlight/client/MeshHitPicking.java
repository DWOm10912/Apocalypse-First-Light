package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.meshhit.MeshBlockHitResult;
import com.antaurora.apofirstlight.meshhit.MeshHitClip;
import com.antaurora.apofirstlight.meshhit.MeshHitModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The crosshair on hit meshes (docs/rendering/mesh_hit_runtime_v1.md, P2, 2026-10-06):
 * <ul>
 *   <li>Picking: the block part of the crosshair pick (GameRenderer#pick's Entity#pick, mixin/client/MeshHitPickMixin) runs
 *   through MeshHitClip, so a block with a hit mesh is picked on its model's surface, and the view passes through the air
 *   around and inside it (between a drum's round side and its cell's corner, between handles) to whatever is behind;
 *   vanilla then limits the entity pick and the reach to that, as before. A point on a model reaching out of its cell is
 *   brought back into the cell (the server refuses a use more than a block from the block's centre), as
 *   AflMeshShapePicking does.</li>
 *   <li>Outline: such a block's selection outline is its model's feature edges (meshhit/MeshHitModel#edges: folds over 35
 *   degrees and open edges), drawn as vanilla's box is (lines, black at 0.4), instead of its shape's boxes.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MeshHitPicking {
    private MeshHitPicking() {
    }

    /** Entity#pick (the block part of the crosshair pick) with hit meshes. */
    public static HitResult pick(Entity entity, double range, float partialTick, boolean fluids) {
        Vec3 eye = entity.getEyePosition(partialTick), end = eye.add(entity.getViewVector(partialTick).scale(range));
        BlockHitResult hit = MeshHitClip.clip(entity.level(), new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                fluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, entity));
        if (!(hit instanceof MeshBlockHitResult mesh)) return hit;
        BlockPos pos = mesh.getBlockPos();
        Vec3 at = mesh.getLocation();
        Vec3 kept = new Vec3(Mth.clamp(at.x, pos.getX(), pos.getX() + 1.0), Mth.clamp(at.y, pos.getY(), pos.getY() + 1.0), Mth.clamp(at.z, pos.getZ(), pos.getZ() + 1.0));
        return kept.equals(at) ? mesh : new MeshBlockHitResult(kept, mesh.normal(), pos);
    }

    @SubscribeEvent
    public static void outline(RenderHighlightEvent.Block event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        BlockPos pos = event.getTarget().getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !level.getWorldBorder().isWithinBounds(pos)) return;
        MeshHitModels.Shape shape = MeshHitModels.shape(level, pos, state);
        if (shape == null) return;
        event.setCanceled(true);
        Vec3 camera = event.getCamera().getPosition();
        float ox = (float) (pos.getX() - camera.x), oy = (float) (pos.getY() - camera.y), oz = (float) (pos.getZ() - camera.z);
        VertexConsumer out = event.getMultiBufferSource().getBuffer(RenderType.lines());
        PoseStack.Pose pose = event.getPoseStack().last();
        shape.forEachEdge((x0, y0, z0, x1, y1, z1) -> {
            float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0, len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-6F) return;
            dx /= len;
            dy /= len;
            dz /= len;
            out.vertex(pose.pose(), x0 + ox, y0 + oy, z0 + oz).color(0.0F, 0.0F, 0.0F, 0.4F).normal(pose.normal(), dx, dy, dz).endVertex();
            out.vertex(pose.pose(), x1 + ox, y1 + oy, z1 + oz).color(0.0F, 0.0F, 0.0F, 0.4F).normal(pose.normal(), dx, dy, dz).endVertex();
        });
    }
}
