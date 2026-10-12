package com.antaurora.apofirstlight.dev.meshtree;

import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Species;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * Mesh Trees P2.1 (docs/rendering/mesh_trees_p2_felling.md, U): offline previews of the felling assets on their real
 * geometry (TreeCut over the layouts' root cells), with the generated textures. Not a check: pictures for review.
 * Arguments: textures folder (block), output folder. Writes felling_assets.png: per species (P3: all six) a stump from
 * the side and from above, and the notch of stages 1 to 4.
 */
public final class AssetPreview {
    static BufferedImage[] tex;

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]), out = new File(args[1]);
        out.mkdirs();
        // the sprite numbers of TreeCellModel: per species bark, leaves, mid, far; then cut wood, then end grain per species
        int n = Species.values().length;
        tex = new BufferedImage[TreeCut.END_SPRITE + n];
        for (Species s : Species.values()) {
            int i = s.ordinal() * TreeShape.SLOTS;
            tex[i] = ImageIO.read(new File(dir, s.id + "_bark.png"));
            tex[i + 1] = ImageIO.read(new File(dir, s.leavesId + ".png"));
            tex[i + 2] = ImageIO.read(new File(dir, s.leavesId + "_mid.png"));
            tex[i + 3] = ImageIO.read(new File(dir, s.leavesId + "_far.png"));
            tex[TreeCut.CUT_SPRITE + s.ordinal()] = ImageIO.read(new File(dir, s.id + "_cut.png"));
            tex[TreeCut.END_SPRITE + s.ordinal()] = ImageIO.read(new File(dir, s.id + "_end_grain.png"));
        }
        int W = 420, H = 420, cols = 6;
        var sheet = new BufferedImage(W * cols, H * n, BufferedImage.TYPE_INT_RGB);
        for (Species sp : Species.values()) {
            int row = sp.ordinal(), v = sp == Species.WHITE_OAK ? 0 : 2;
            var layout = TreeShape.layout(sp, v, 0);
            var root = layout.cell(0, 0, 0).quads[0];
            float cut = 0.6F;
            var stump = TreeCut.stump(root, cut, sp.ordinal());
            draw(sheet, stump, 0, row, W, H, 0, 300, 0.3F);
            // from straight above: the cap
            draw(sheet, stump, 1, row, W, H, 2, 300, 0.3F);
            for (int stage = 1; stage <= 4; stage++)
                draw(sheet, TreeCut.notch(root, cut, stage, -Math.PI / 2, sp.ordinal()), 1 + stage, row, W, H, 1, 300, 0.5F);
        }
        ImageIO.write(sheet, "png", new File(out, "felling_assets.png"));
        System.out.println("preview: " + new File(out, "felling_assets.png"));
    }

    /** view 0: 3/4 from above, 1: from the side (looking along -z, the notch at +z faces us), 2: from above. */
    static void draw(BufferedImage sheet, List<TreeShape.Quad> quads, int col, int row, int W, int H, int view, float scale, float mid) {
        var r = new TreeCheck.Raster(W, H, view == 2 ? 0 : view == 1 ? 1 : 0, scale, mid);
        if (view == 2) {
            // straight down: swap y and z before the 3/4 raster (its pitch is small); simple orthographic top view instead
            top(sheet, quads, col, row, W, H, scale);
            return;
        }
        for (var q : quads) r.quad(q, tex[q.sprite], -1);
        r.blit(sheet, col * W, row * H);
    }

    static void top(BufferedImage sheet, List<TreeShape.Quad> quads, int col, int row, int W, int H, float scale) {
        int ox = col * W, oy = row * H;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) sheet.setRGB(ox + x, oy + y, 0xB8C8D8);
        for (var q : quads) {
            if (q.n[1] < 0.9F) continue;
            var t = tex[q.sprite];
            for (int tri = 0; tri < 2; tri++) {
                int a = 0, b = tri == 0 ? 1 : 2, c = tri == 0 ? 2 : 3;
                float ax = q.p[a * 3], az = q.p[a * 3 + 2], bx = q.p[b * 3], bz = q.p[b * 3 + 2], cx = q.p[c * 3], cz = q.p[c * 3 + 2];
                float area = (bx - ax) * (cz - az) - (bz - az) * (cx - ax);
                if (Math.abs(area) < 1e-7F) continue;
                for (int py = 0; py < H; py++) for (int px = 0; px < W; px++) {
                    float wx = 0.5F + (px + 0.5F - W / 2F) / scale, wz = 0.5F + (py + 0.5F - H / 2F) / scale;
                    float wa = ((bx - wx) * (cz - wz) - (bz - wz) * (cx - wx)) / area, wb = ((cx - wx) * (az - wz) - (cz - wz) * (ax - wx)) / area, wc = 1 - wa - wb;
                    if (wa < 0 || wb < 0 || wc < 0) continue;
                    float u = wa * q.uv[a * 2] + wb * q.uv[b * 2] + wc * q.uv[c * 2], v = wa * q.uv[a * 2 + 1] + wb * q.uv[b * 2 + 1] + wc * q.uv[c * 2 + 1];
                    int tx = Math.min(t.getWidth() - 1, Math.max(0, (int) (u * t.getWidth()))), ty = Math.min(t.getHeight() - 1, Math.max(0, (int) (v * t.getHeight())));
                    sheet.setRGB(ox + px, oy + py, t.getRGB(tx, ty) & 0xFFFFFF);
                }
            }
        }
    }

    private AssetPreview() {}
}
