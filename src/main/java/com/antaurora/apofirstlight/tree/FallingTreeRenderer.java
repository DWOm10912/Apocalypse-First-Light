package com.antaurora.apofirstlight.tree;

import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Mesh Trees P2 (docs/rendering/mesh_trees_p2_felling.md, H/N): draws a falling tree as one rigid body turning round
 * its pivot (the cut, on the trunk's axis), about the horizontal axis across the fall direction.
 * <ul>
 * <li>Geometry: the tree's own quads at one level chosen when this client first draws it (distance then: LOD0 under 64,
 * LOD1 under 160 blocks, else LOD2) and kept for the whole fall, cut above the stump with an end-grain cap under it
 * (TreeCut#fallen). Trunk, limbs, twigs and leaves move together; no level change and no other tree is touched.</li>
 * <li>Layers: bark in solid, leaves in cutout_mipped, both two-sided where the chunk model is (the chunk's block
 * layers, so a shader pack draws them as it draws falling blocks: lit by their normals, in its shadow pass too, without
 * the leaf wave).</li>
 * <li>Colour: the quads' baked occlusion, the leaf tint (oak: the biome's foliage colour at the root; pine: its own),
 * without a shader pack vanilla's direction shading by the turned normal. Light: eight samples along the tree's axis
 * in its current pose, each quad takes the one nearest its height on the tree.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class FallingTreeRenderer extends EntityRenderer<FallingTree> {
    static final int SAMPLES = 8;

    /** Frozen per fall: the quads, each quad's height along the tree (0..1), the level drawn. */
    record Mesh(List<TreeShape.Quad> quads, float[] along, int level, float height) {}

    public FallingTreeRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(FallingTree entity) { return TextureAtlas.LOCATION_BLOCKS; }

    static Mesh mesh(FallingTree e) {
        if (e.mesh instanceof Mesh m) return m;
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        int level = TreeClient.level(Math.hypot(e.getX() - cam.x, e.getZ() - cam.z), -1);
        var layout = e.layout();
        var all = new java.util.ArrayList<TreeShape.Quad>();
        for (var c : TreeShape.ordered(layout)) all.addAll(c.quads[level]);
        var quads = TreeCut.fallen(all, e.cut, e.species.ordinal());
        float height = Math.max(1, layout.height - e.cut);
        float[] along = new float[quads.size()];
        for (int i = 0; i < quads.size(); i++) along[i] = Math.max(0, Math.min(1, (quads.get(i).cy() - e.cut) / height));
        var m = new Mesh(List.copyOf(quads), along, level, height);
        e.mesh = m;
        return m;
    }

    @Override
    public void render(FallingTree e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !TreeBlocks.ready()) return;
        Mesh m = mesh(e);
        float angle = e.angle(partial);
        float dx = (float) Math.cos(e.direction), dz = (float) Math.sin(e.direction);
        var turn = new Quaternionf().rotationAxis((float) Math.toRadians(angle), dz, 0, -dx);
        // light along the tree in this pose
        int[] light = new int[SAMPLES];
        var up = new Vector3f();
        var pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < SAMPLES; i++) {
            up.set(0, m.height * (i + 0.5F) / SAMPLES, 0).rotate(turn);
            pos.set(e.getX() + up.x, e.getY() + up.y, e.getZ() + up.z);
            light[i] = LevelRenderer.getLightColor(mc.level, pos);
        }
        int foliage = BiomeColors.getAverageFoliageColor(mc.level, e.root);
        boolean shade = !AflShaderCompat.shaderPackInUse();
        TextureAtlasSprite[] sprites = TreeCellModel.sprites();
        pose.pushPose();
        pose.mulPose(turn);
        pose.translate(-0.5F, -e.cut, -0.5F);
        Matrix4f mat = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        VertexConsumer solid = buffers.getBuffer(RenderType.solid()), leaves = buffers.getBuffer(RenderType.cutoutMipped());
        var n = new Vector3f();
        for (int i = 0; i < m.quads.size(); i++) {
            var q = m.quads.get(i);
            boolean leaf = q.kind == TreeShape.FOLIAGE;
            int tint = !leaf || q.tint < 0 ? 0xFFFFFF : q.tint >= 1 ? TreeModels.ownColor(q.tint) : foliage;
            float tr = (tint >> 16 & 255) / 255F, tg = (tint >> 8 & 255) / 255F, tb = (tint & 255) / 255F;
            int l = light[Math.min(SAMPLES - 1, (int) (m.along[i] * SAMPLES))];
            TextureAtlasSprite sp = sprites[q.sprite];
            VertexConsumer vc = leaf ? leaves : solid;
            for (int side = 0; side < (q.twoSided ? 2 : 1); side++) {
                float sign = side == 0 ? 1 : -1;
                for (int k = 0; k < 4; k++) {
                    int c = side == 0 ? k : (4 - k) & 3;
                    float nx = q.n[c * 3] * sign, ny = q.n[c * 3 + 1] * sign, nz = q.n[c * 3 + 2] * sign;
                    float bright = q.ao[c];
                    if (shade) {
                        n.set(nx, ny, nz).rotate(turn);
                        bright *= leaf ? 0.82F + 0.18F * Math.abs(n.y) : n.x * n.x * 0.6F + n.z * n.z * 0.8F + n.y * n.y * (n.y >= 0 ? 1.0F : 0.5F);
                    }
                    vc.vertex(mat, q.p[c * 3], q.p[c * 3 + 1], q.p[c * 3 + 2])
                            .color(tr * bright, tg * bright, tb * bright, 1F)
                            .uv(sp.getU0() + (sp.getU1() - sp.getU0()) * q.uv[c * 2], sp.getV0() + (sp.getV1() - sp.getV0()) * q.uv[c * 2 + 1])
                            .uv2(l)
                            .normal(nm, nx, ny, nz)
                            .endVertex();
                }
            }
        }
        pose.popPose();
    }
}
