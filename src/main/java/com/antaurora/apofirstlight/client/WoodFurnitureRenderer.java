package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile;
import com.antaurora.apofirstlight.cabin.WoodCounterBlockEntity;
import com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The wood furniture's storage (counters, wardrobe, nightstand: Furniture Mesh V1) through the generic animated mesh
 * renderer; the wardrobe also shows the clothes it holds (2026-10-27, Main's meshes, docs/dev/old_body_clothes_v1.md
 * "衣柜里挂着的样子"): in the hanging space shirts, jackets and jeans on hangers on the rod, shoes in pairs on the floor
 * board; on the top shelf (its own section) folded, side by side and then in piles. Only revealed clothes
 * (WoodCounterBlockEntity#hungClothes), drawn while the doors are not shut.
 */
public final class WoodFurnitureRenderer implements BlockEntityRenderer<WoodCounterBlockEntity> {
    private static final String NS = "apocalypse_firstlight";
    private static final String[] HUNG = {"clothing_shirt_hung", "clothing_jacket_hung", "clothing_jeans_hung"};
    /** Each hung mesh's hook point (Main's generator, px): put on the rod. */
    private static final float[][] HOOK = {{0.42F, 13.4F, -0.125F}, {0.42F, 12.24F, -0.155F}, {0.42F, 9.6F, -0.025F}};
    /**
     * The rod (tools/build-wood-furniture-v2.mjs wardrobeBody, model blocks, front at -z): y 1.55, z 0.64. The inside is
     * 0.525 deep and a shirt 0.54 wide, its hook off its middle: hung square it would poke out of the back, so the
     * hangers turn 50 deg off the rod (as in a shallow wardrobe): a jacket 0.44..0.90 in z, -0.21..0.19 in x.
     */
    private static final float ROD_Y = 1.55F, ROD_Z = 0.64F, TURN = 50, SPAN_X0 = 0.26F, SPAN_X1 = 0.76F, STEP = 0.09F;
    private static final int MAX_HUNG = 8, MAX_PAIRS = 3;
    /**
     * The clothes' item meshes (folded, the shoes) are at the item scale, 1 px = 1.25 inch (tools/build-equipment-meshes-v1.mjs):
     * 0.508 brings them to real size in blocks of 1 m (a folded shirt 35 x 28 cm, a shoe 34 cm).
     */
    private static final float ITEM_SCALE = 0.508F;
    /** The floor board's top (y 0.362): the pairs toes to the doors. */
    private static final float FLOOR_Y = 0.362F, SHOE_Z = 0.62F, PAIR_STEP = 0.26F;
    /** The hat shelf (wardrobeBody: 1.62..1.64, z 0.40..0.905) under the top board (1.82): x 0.06..0.94, piles to 0.17 high. */
    private static final float SHELF_Y = 1.64F, SHELF_Z = 0.65F, SHELF_X0 = 0.06F, SHELF_X1 = 0.94F, SHELF_GAP = 0.02F, PILE_H = 0.17F;
    private static final String[] FOLDED = {"clothing_shirt", "clothing_jacket", "clothing_jeans", "clothing_shoes"};
    /** Each folded item mesh's width (x) and height in item-scale blocks (the meshes' bounds). */
    private static final float[] FOLDED_W = {0.688F, 0.724F, 0.624F, 0.444F}, FOLDED_H = {0.122F, 0.178F, 0.159F, 0.191F};

    private final AflAnimatedBlockMeshRenderer<WoodCounterBlockEntity> cabinet;

    public WoodFurnitureRenderer(BlockEntityRendererProvider.Context context) {
        cabinet = new AflAnimatedBlockMeshRenderer<>(context);
    }

    @Override
    public boolean shouldRenderOffScreen(WoodCounterBlockEntity be) { return cabinet.shouldRenderOffScreen(be); }

    @Override
    public int getViewDistance() { return cabinet.getViewDistance(); }

    @Override
    public void render(WoodCounterBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        cabinet.render(be, partialTick, pose, buffers, light, overlay);
        byte[] hung = be.hungClothes();
        if (hung.length == 0 || be.getLevel() == null) return;
        if (be.meshAnimation().sample("open", be.getLevel().getGameTime() + partialTick) <= 0) return;   // shut: the doors hide them
        int garments = 0, pairs = 0;
        for (byte b : hung) {
            if ((b & WoodCounterBlockEntity.ON_SHELF) != 0) continue;
            if (b == WoodCounterBlockEntity.SHOES) pairs++;
            else garments++;
        }
        garments = Math.min(garments, MAX_HUNG);
        pairs = Math.min(pairs, MAX_PAIRS);
        int upper = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(AflBlockMeshProfile.facingDegrees(be.meshFacing())));
        pose.translate(-0.5, 0, -0.5);
        float step = garments > 1 ? Math.min(STEP, (SPAN_X1 - SPAN_X0) / (garments - 1)) : 0;
        int g = 0, p = 0;
        for (byte b : hung) {
            if ((b & WoodCounterBlockEntity.ON_SHELF) != 0) continue;
            if (b == WoodCounterBlockEntity.SHOES) {
                if (p >= pairs) continue;
                pose.pushPose();
                pose.translate(0.5 + (p++ - (pairs - 1) / 2F) * PAIR_STEP, FLOOR_Y, SHOE_Z);
                pose.mulPose(Axis.YP.rotationDegrees(180));
                pose.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
                draw("clothing_shoes", pose, buffers, light, overlay);
                pose.popPose();
            } else if (b >= WoodCounterBlockEntity.SHIRT && b <= WoodCounterBlockEntity.JEANS && g < garments) {
                float[] hook = HOOK[b - 1];
                pose.pushPose();
                pose.translate(0.5 + (g++ - (garments - 1) / 2F) * step, ROD_Y, ROD_Z);
                pose.mulPose(Axis.YP.rotationDegrees(TURN));
                pose.translate(-hook[0] / 16F, -hook[1] / 16F, -hook[2] / 16F);
                draw(HUNG[b - 1], pose, buffers, upper, overlay);
                pose.popPose();
            }
        }
        shelf(hung, pose, buffers, upper, overlay);
        pose.popPose();
    }

    /**
     * The top shelf's clothes folded, collars to the doors (the shoes' toes): side by side from the left while the row has
     * room, then each on the lowest pile it fits on; the row centred on the shelf.
     */
    private static void shelf(byte[] hung, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float[] pileX = new float[com.antaurora.apofirstlight.cabin.WoodFurnitureV2.Wardrobe.SHELF_SLOTS], pileH = new float[pileX.length];
        int[][] pile = new int[hung.length][];
        int piles = 0;
        float row = 0;
        for (int i = 0; i < hung.length; i++) {
            if ((hung[i] & WoodCounterBlockEntity.ON_SHELF) == 0) continue;
            int k = (hung[i] & ~WoodCounterBlockEntity.ON_SHELF) - 1;
            if (k < 0 || k >= FOLDED.length) continue;
            float w = FOLDED_W[k] * ITEM_SCALE, h = FOLDED_H[k] * ITEM_SCALE;
            int at = -1;
            if (piles < pileX.length && row + (piles > 0 ? SHELF_GAP : 0) + w <= SHELF_X1 - SHELF_X0) {
                at = piles++;
                row += (at > 0 ? SHELF_GAP : 0);
                pileX[at] = row + w / 2;
                row += w;
            } else {
                for (int q = 0; q < piles; q++)
                    if (pileH[q] + h <= PILE_H && (at < 0 || pileH[q] < pileH[at])) at = q;
                if (at < 0) continue;
            }
            pile[i] = new int[]{at, Math.round(pileH[at] * 10000)};
            pileH[at] += h;
        }
        float left = SHELF_X0 + (SHELF_X1 - SHELF_X0 - row) / 2;
        for (int i = 0; i < hung.length; i++) {
            if (pile[i] == null) continue;
            int k = (hung[i] & ~WoodCounterBlockEntity.ON_SHELF) - 1;
            pose.pushPose();
            pose.translate(left + pileX[pile[i][0]], SHELF_Y + pile[i][1] / 10000F, SHELF_Z);
            if (k == WoodCounterBlockEntity.SHOES - 1) pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            draw(FOLDED[k], pose, buffers, light, overlay);
            pose.popPose();
        }
    }

    private static void draw(String id, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        AflStaticMeshItemRenderer.drawMesh(new ResourceLocation(NS, "geo/" + id + ".geo.json"),
                new ResourceLocation(NS, "textures/item/" + id + "_mesh.png"), pose, buffers, light, overlay);
    }
}
