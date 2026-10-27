package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile;
import com.antaurora.apofirstlight.cabin.FurnitureWork;
import com.antaurora.apofirstlight.cabin.Workbench;
import com.antaurora.apofirstlight.client.workbench.FurnitureBench;
import com.antaurora.apofirstlight.client.workbench.FurnitureWorkClient;
import com.antaurora.vextris.client.MeshRenderer;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshModel;
import com.antaurora.vextris.mesh.MeshPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.antaurora.apofirstlight.cabin.FurnitureWork.CAN_CLOSE;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.CAN_OPEN;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.CROSSCUT;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.WIPE;
import static com.antaurora.apofirstlight.client.workbench.FurnitureBench.*;

/**
 * The furniture work on the bench top (docs/gameplay/furniture_bench_work_v1.md 3.6, 4.4), drawn by WorkbenchRenderer each
 * frame from FurnitureWorkClient#scene, in the bench's north frame: the board being crosscut (as long as the pieces still to
 * cut, slid over the end, the kerf down its top, the off-cut split off and carried onto the pile), then the piece (its
 * boards in the wood's planks, the grain along each board; the stain brushed on band by band across its depth), its nails
 * and screws as far as they are in, its hardware; while someone works: the can, its lid, the stick and the brush where the
 * actions leave them, the box of nails or screws. The half-built piece stays drawn when the work stops (the bench's job),
 * so a stop and a go-on later show the same thing; a finished piece stands on the top until someone takes it (doc 12.3).
 */
public final class FurnitureWorkRenderer {
    /** The props' scale on the bench (as the actions draw them: real size, the player model's 0.9375). */
    private static final float SCALE = (float) K;
    /** The stain over the wood (a walnut stain, multiplied over the planks), and the kerf's dark. */
    private static final int[] STAIN = {140, 100, 69}, KERF = {46, 36, 26};
    /** The heaps' height in their box (mesh units: the box's body 0.213 for the nails, 0.154 for the screws). */
    private static final double NAIL_HEAP_Y = 0.15, SCREW_HEAP_Y = 0.10;
    private static final Map<String, ResourceLocation> GEO = new HashMap<>(), TEX = new HashMap<>();

    private FurnitureWorkRenderer() {}

    public static void render(BlockPos master, BlockState bench, ItemStack finished, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        FurnitureWorkClient.Scene sc = FurnitureWorkClient.scene(master, partial);
        // the finished piece once its run is over (the can shut: it would stand in the can's way)
        boolean laid = !finished.isEmpty() && (sc == null || sc.action() == null);
        if (sc == null && !laid) return;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(AflBlockMeshProfile.facingDegrees(bench.getValue(Workbench.FACING))));
        pose.translate(-0.5, 0, -0.5);
        if (laid) finished(finished, pose, buffers, light);
        if (sc == null) {
            pose.popPose();
            return;
        }
        String action = sc.action();
        double t = sc.seconds();
        FurnitureWork.Job job = sc.job();
        if (job != null && !job.steps().isEmpty()) drawPiece(job, action, t, sc.level(), pose, buffers, light);
        if (action != null) {
            if (sc.can()) can(action, t, pose, buffers, light);
            FurnitureWork.Step step = job == null ? null : job.current();
            FurnitureWork.Kind kind = step == null ? null : FurnitureWork.kind(step.step());
            if (kind == FurnitureWork.Kind.NAIL) box("nail_box", "nail_heap", NAIL_HEAP_Y, pose, buffers, light);
            else if (kind == FurnitureWork.Kind.FIT) box("screw_box", "screw_heap", SCREW_HEAP_Y, pose, buffers, light);
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------------------------------------ the wood

    private static void drawPiece(FurnitureWork.Job job, @Nullable String action, double t, int level, PoseStack pose, MultiBufferSource buffers, int light) {
        BlockState planks = FurnitureWorkClient.planks(job);
        if (planks == null) return;
        TextureAtlasSprite sprite = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(planks);
        VertexConsumer out = buffers.getBuffer(RenderType.entitySolid(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose last = pose.last();
        String id = pieceAt(job, Math.min(job.step(), job.steps().size() - 1));
        if (id.equals(FurnitureWork.BOARD)) {
            board(job, action, t, out, last, sprite, light);
            return;
        }
        Piece p = piece(id);
        if (p == null) return;
        int painted = painted(job, action, t);
        for (Board b : p.boards()) wood(out, last, sprite, b, p.z0(), p.z1(), painted, light);
        // the nails and the hardware of the steps so far on this piece
        for (int i = 0; i < Math.min(job.step() + 1, job.steps().size()); i++) {
            FurnitureWork.Step st = job.steps().get(i);
            FurnitureWork.Kind kind = FurnitureWork.kind(st.step());
            boolean current = i == job.step();
            if (kind == FurnitureWork.Kind.NAIL && pieceAt(job, i).equals(id)) {
                int before = nailsBefore(job, i);
                for (int r = 0; r < job.doneOf(i); r++) nail(p.nail(before + r), 1, pose, buffers, light);
                if (current && FurnitureWorkClient.nailShown(action, t))
                    nail(p.nail(before + job.rep()), FurnitureWorkClient.nailDepth(action, t, job.hits(), level), pose, buffers, light);
            } else if (kind == FurnitureWork.Kind.FIT) {
                for (int r = 0; r < job.doneOf(i); r++) {
                    Hardware h = hardware(job, i, r);
                    if (h == null) continue;
                    hardwareMesh(h, pose, buffers, light);
                    for (int k = 0; k < h.screws().length; k++) screw(h.screws()[k], 1, seed(i, r, k), pose, buffers, light);
                }
                if (!current) continue;
                Hardware h = hardware(job, i, job.rep());
                if (h == null || !FurnitureWorkClient.hardwareShown(action, t, job)) continue;
                hardwareMesh(h, pose, buffers, light);
                for (int k = 0; k < Math.min(job.sub(), h.screws().length); k++) screw(h.screws()[k], 1, seed(i, job.rep(), k), pose, buffers, light);
                if (job.sub() < h.screws().length && FurnitureWorkClient.screwShown(action, t)) {
                    double[] d = FurnitureWorkClient.screwDepth(action, t, job.hits(), level);
                    screw(h.screws()[job.sub()], d[0], seed(i, job.rep(), job.sub()) + 90 * d[1], pose, buffers, light);
                }
            }
        }
    }

    /** The stained bands (of BANDS, from the front): the brush's strokes so far of all of them; all once the coats are done. */
    private static int painted(FurnitureWork.Job job, @Nullable String action, double t) {
        int[] w = job.count(WIPE);
        if (w[1] == 0) return 0;
        if (w[0] >= w[1]) return BANDS;
        int k = w[0] * STROKES;
        if (WIPE.equals(action)) for (double end : STROKE_ENDS) if (t >= end) k++;
        return (int) Math.round(BANDS * k / (double) (w[1] * STROKES));
    }

    /**
     * The board on the bench while it is crosscut: as long as the pieces still to cut and BOARD_OVER, slid over the bench's
     * end by a piece, the kerf down its top as the saw goes, the off-cut split off at the cut and carried onto the pile.
     */
    private static void board(FurnitureWork.Job job, @Nullable String action, double t, VertexConsumer out, PoseStack.Pose last,
                              TextureAtlasSprite sprite, int light) {
        int[] c = job.count(CROSSCUT);
        int left = Math.max(0, c[1] - c[0]);
        boolean cutting = CROSSCUT.equals(action);
        double len = CUT_PIECE * left + BOARD_OVER, x0 = -CUT_PIECE * (cutting ? slide(t) : 0), y0 = CUT_TOP - CUT_THICK;
        if (cutting && t >= THROUGH) {
            wood(out, last, sprite, new Board(CUT_X, y0, CUT_Z0, x0 + len, CUT_TOP, CUT_Z1, true), CUT_Z0, CUT_Z1, 0, light);
            double[] m = t < CARRY_FROM ? new double[]{CUT_X - CUT_PIECE / 2, CUT_TOP, (CUT_Z0 + CUT_Z1) / 2} : carried(t, c[0]);
            offcut(out, last, sprite, m, light);
        } else {
            wood(out, last, sprite, new Board(x0, y0, CUT_Z0, x0 + len, CUT_TOP, CUT_Z1, true), CUT_Z0, CUT_Z1, 0, light);
            if (cutting && t >= SAW_FROM) {
                double kz = kerfZ(t);
                float u = (sprite.getU0() + sprite.getU1()) / 2, v = (sprite.getV0() + sprite.getV1()) / 2;
                flat(out, last, CUT_X - 0.004, CUT_TOP + 0.0015, CUT_Z0 - 0.0015, CUT_X + 0.004, kz, u, v, light);
                front(out, last, CUT_X - 0.004, y0, CUT_X + 0.004, CUT_TOP + 0.0015, CUT_Z0 - 0.0015, u, v, light);
            }
        }
        for (int k = 0; k < c[0]; k++) offcut(out, last, sprite, new double[]{PILE_X, 0.9 + CUT_THICK * (k + 1), PILE_Z}, light);
    }

    /** An off-cut by its top middle. */
    private static void offcut(VertexConsumer out, PoseStack.Pose last, TextureAtlasSprite sprite, double[] m, int light) {
        double hz = (CUT_Z1 - CUT_Z0) / 2;
        wood(out, last, sprite, new Board(m[0] - CUT_PIECE / 2, m[1] - CUT_THICK, m[2] - hz, m[0] + CUT_PIECE / 2, m[1], m[2] + hz, true),
                m[2] - hz, m[2] + hz, 0, light);
    }

    /**
     * A board in the planks: each face split at whole blocks (the texture's tiles) and at the stain's bands, the grain (the
     * texture's u) along the board, the bands from the front stained; the face on the bench top left out.
     */
    private static void wood(VertexConsumer out, PoseStack.Pose last, TextureAtlasSprite sprite, Board b, double bz0, double bz1, int painted, int light) {
        double[] lo = {b.x0(), b.y0(), b.z0()}, hi = {b.x1(), b.y1(), b.z1()};
        int grain = b.alongX() ? 0 : 2;
        for (int n = 0; n < 3; n++) for (int sign = -1; sign <= 1; sign += 2) {
            if (n == 1 && sign < 0 && lo[1] <= 0.9 + 1e-4) continue;
            int a = (n + 1) % 3, c = (n + 2) % 3;
            int uAxis = a == grain || c == grain ? grain : a, vAxis = uAxis == a ? c : a;
            double plane = sign > 0 ? hi[n] : lo[n];
            List<Double> as = cuts(lo[a], hi[a], a == 2, bz0, bz1), cs = cuts(lo[c], hi[c], c == 2, bz0, bz1);
            for (int i = 0; i + 1 < as.size(); i++) for (int j = 0; j + 1 < cs.size(); j++) {
                double a0 = as.get(i), a1 = as.get(i + 1), c0 = cs.get(j), c1 = cs.get(j + 1);
                double z = n == 2 ? plane : a == 2 ? (a0 + a1) / 2 : (c0 + c1) / 2;
                int band = (int) Math.floor((z - bz0) / Math.max(1e-6, bz1 - bz0) * BANDS - 1e-6);
                boolean stained = band < painted;
                double[][] q = sign > 0 ? new double[][]{{a0, c0}, {a1, c0}, {a1, c1}, {a0, c1}} : new double[][]{{a0, c0}, {a0, c1}, {a1, c1}, {a1, c0}};
                double[] base = new double[3];
                base[a] = Math.floor(a0 + 1e-6);
                base[c] = Math.floor(c0 + 1e-6);
                for (double[] corner : q) {
                    double[] p = new double[3];
                    p[n] = plane;
                    p[a] = corner[0];
                    p[c] = corner[1];
                    float u = sprite.getU((float) ((p[uAxis] - base[uAxis]) * 16)), v = sprite.getV((float) ((1 - (p[vAxis] - base[vAxis])) * 16));
                    out.vertex(last.pose(), (float) p[0], (float) p[1], (float) p[2])
                            .color(stained ? STAIN[0] : 255, stained ? STAIN[1] : 255, stained ? STAIN[2] : 255, 255)
                            .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                            .normal(last.normal(), n == 0 ? sign : 0, n == 1 ? sign : 0, n == 2 ? sign : 0).endVertex();
                }
            }
        }
    }

    /** A face's splits along one axis: its ends, the whole blocks between, and (along z) the stain's band lines. */
    private static List<Double> cuts(double lo, double hi, boolean z, double bz0, double bz1) {
        List<Double> out = new ArrayList<>();
        out.add(lo);
        for (double k = Math.floor(lo) + 1; k < hi - 1e-6; k++) if (k > lo + 1e-6) out.add(k);
        if (z) for (int i = 1; i < BANDS; i++) {
            double at = bz0 + (bz1 - bz0) * i / BANDS;
            if (at > lo + 1e-6 && at < hi - 1e-6) out.add(at);
        }
        out.add(hi);
        out.sort(Double::compare);
        return out;
    }

    /** The kerf down the board's top (x0..x1 across, z0..z1 along). */
    private static void flat(VertexConsumer out, PoseStack.Pose last, double x0, double y, double z0, double x1, double z1, float u, float v, int light) {
        double[][] q = {{x0, y, z0}, {x0, y, z1}, {x1, y, z1}, {x1, y, z0}};
        for (double[] p : q) dark(out, last, p, 0, 1, 0, u, v, light);
    }

    /** The kerf down the board's front edge (y0..y1 down it). */
    private static void front(VertexConsumer out, PoseStack.Pose last, double x0, double y0, double x1, double y1, double z, float u, float v, int light) {
        double[][] q = {{x0, y0, z}, {x0, y1, z}, {x1, y1, z}, {x1, y0, z}};
        for (double[] p : q) dark(out, last, p, 0, 0, -1, u, v, light);
    }

    private static void dark(VertexConsumer out, PoseStack.Pose last, double[] p, float nx, float ny, float nz, float u, float v, int light) {
        out.vertex(last.pose(), (float) p[0], (float) p[1], (float) p[2]).color(KERF[0], KERF[1], KERF[2], 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(last.normal(), nx, ny, nz).endVertex();
    }

    // ------------------------------------------------------------------------------------------------ nails, screws, hardware

    /** A nail at its slot (x, the piece's top, z), {@code depth} of it in (1: its head on the wood). */
    private static void nail(double[] at, double depth, PoseStack pose, MultiBufferSource buffers, int light) {
        mesh("furniture_work", Set.of("nail"), new double[]{at[0], at[1] + 0.001 - NAIL_LEN * depth, at[2]}, null, 0, SCALE, pose, buffers, light);
    }

    /** A screw by its head when flush, {@code depth} of it in, its slot turned {@code degrees}. */
    private static void screw(double[] head, double depth, double degrees, PoseStack pose, MultiBufferSource buffers, int light) {
        mesh("furniture_work", Set.of("screw", "screw_slot"), new double[]{head[0], head[1] - SCREW_LEN * depth, head[2]}, null, degrees, SCALE,
                pose, buffers, light);
    }

    /** Each screw's slot a little otherwise. */
    private static double seed(int step, int rep, int screw) {
        return Math.floorMod(step * 53 + rep * 31 + screw * 17, 90);
    }

    private static void hardwareMesh(Hardware h, PoseStack pose, MultiBufferSource buffers, int light) {
        String model = switch (h.kind()) {
            case HINGE -> "cabinet_hinge";
            case KNOB -> "iron_knob";
            case WOODEN_KNOB -> "wooden_knob";
            case ESCUTCHEON -> "keyhole_escutcheon";
        };
        mesh(model, null, h.base(), turn(h.ex(), h.ey()), 0, SCALE, pose, buffers, light);
    }

    // ------------------------------------------------------------------------------------------------ the can, its things, the boxes

    /**
     * The can at the bench's right, as the can's actions leave its things: the lid on it until it is pried off (2.4 s of the
     * opening) and from when it is pressed back on (2.1 s of the closing), face down at its rest in between (except while it
     * is in the hand); the stick lying right of the can while it is shut, leaning in it while it stands open (in the hand
     * while stirring and taken out); the brush flat on the top until it is taken up (6.2 s of the opening) and from when it
     * is laid back (0.8 s of the closing), across the can's mouth in between but while it is in the hand; the stain inside
     * while the lid is off.
     */
    private static void can(String action, double t, PoseStack pose, MultiBufferSource buffers, int light) {
        boolean opening = CAN_OPEN.equals(action), closing = CAN_CLOSE.equals(action);
        boolean lidOn = opening ? t < 2.4 : closing && t >= 2.1;
        boolean lidRest = opening ? t >= 3.1 : !closing || t < 1.4;
        boolean lying = opening ? t < 3.45 : closing && t >= 1.45;
        boolean leaning = opening ? t >= 6.2 : !closing || t < 0.85;
        boolean brushRest = opening ? t < 6.2 : closing && t >= 0.8;
        boolean brushCan = opening ? t >= 6.85 : WIPE.equals(action) ? t >= 4.15 : !closing;
        mesh("furniture_work", lidOn ? Set.of("can_open", "can_label", "can_bail", "can_ear_0", "can_ear_1")
                : Set.of("can_open", "can_label", "can_bail", "can_ear_0", "can_ear_1", "can_stain"), CAN, null, 0, SCALE, pose, buffers, light);
        double[] mouth = mouth();
        if (lidOn) mesh("furniture_work", Set.of("lid"), mouth, null, 0, SCALE, pose, buffers, light);
        else if (lidRest) mesh("furniture_work", Set.of("lid"), LID, turn(new double[]{1, 0, 0}, new double[]{0, -1, 0}), 0, SCALE, pose, buffers, light);
        Set<String> stick = Set.of("stick", "stick_wet");
        if (lying) {
            // on its edge along z, its middle (mesh y 0.256) at the stick's rest
            mesh("furniture_work", stick, new double[]{STICK[0], STICK[1], STICK[2] - 0.256 * K}, turn(new double[]{0, 1, 0}, new double[]{0, 0, 1}), 0,
                    SCALE, pose, buffers, light);
        } else if (leaning) {
            double[] h = norm(STICK_LEAN), b = {0, 0, 1};
            mesh("furniture_work", stick, new double[]{CAN[0] - 0.02, CAN[1] + 0.02, CAN[2]}, turn(orth(b, h), h), 0, SCALE, pose, buffers, light);
        }
        Set<String> brush = Set.of("brush_handle", "brush_ferrule", "brush_bristle", "brush_wet");
        if (brushRest) {
            // flat, the handle to the back, the bristles' width along x; the mesh's origin is the bristles' tip
            mesh("furniture_work", brush, new double[]{BRUSH_REST[0], BRUSH_REST[1] + BRUSH_LIFT, BRUSH_REST[2] - BRUSH_MID},
                    turn(new double[]{1, 0, 0}, new double[]{0, 0, 1}), 0, SCALE, pose, buffers, light);
        } else if (brushCan) {
            // across the mouth, the handle to the left (+x), resting on the rim
            mesh("furniture_work", brush, new double[]{mouth[0] - BRUSH_MID, mouth[1] + BRUSH_LIFT, mouth[2]},
                    turn(new double[]{0, 0, -1}, new double[]{1, 0, 0}), 0, SCALE, pose, buffers, light);
        }
    }

    // ------------------------------------------------------------------------------------------------ the finished piece

    /** How a piece lies on the top (doc 12.3): as it stands, on its top (the table, its legs up), on its back (its front up). */
    private enum Lie { UPRIGHT, FLIP, BACK }

    /**
     * A finished piece's closed bounds as its block stands facing north (blocks, from the master cell's bottom centre; the
     * meshes and cabin pieces measured, doc 12.3's table) and how it lies: what fits under the shelf (0.96 over the top)
     * stands, the table lies on its top, the tall and the wall pieces on their backs (the chair's 0.98 too).
     */
    private record Laid(double[] lo, double[] hi, Lie lie) {}

    private static final Map<String, Laid> LAID = Map.of(
            "wooden_table", new Laid(new double[]{-0.48, 0, -0.48}, new double[]{0.48, 0.76, 0.48}, Lie.FLIP),
            "wooden_chair", new Laid(new double[]{-0.29, 0, -0.21}, new double[]{0.29, 0.98, 0.24}, Lie.BACK),
            "wooden_gun_rack", new Laid(new double[]{-0.45, 0.04, 0.2}, new double[]{0.45, 0.97, 0.5}, Lie.BACK),
            "wooden_tool_rack", new Laid(new double[]{-0.47, 0.68, 0.3}, new double[]{0.47, 0.896, 0.5}, Lie.BACK),
            "wooden_wardrobe", new Laid(new double[]{-0.5, 0, -0.165}, new double[]{0.5, 1.96, 0.425}, Lie.BACK),
            "wooden_nightstand", new Laid(new double[]{-0.27, 0, -0.003}, new double[]{0.27, 0.66, 0.425}, Lie.UPRIGHT),
            "wooden_storage_chest", new Laid(new double[]{-0.144, 0, -0.264}, new double[]{1.144, 0.598, 0.262}, Lie.UPRIGHT));
    private static final Laid OTHER = new Laid(new double[]{-0.5, 0, -0.5}, new double[]{0.5, 1, 0.5}, Lie.UPRIGHT);
    /** The top's middle along x, its depth (from the front edge), how far the shelf's way stays clear of the pegboard. */
    private static final double TOP_MID_X = 1.0, TOP_DEPTH = 0.72;

    /**
     * The piece made here on the top (doc 12.3), drawn by its item (the block's own mesh or cabin pieces, closed) as it lies:
     * on the top, in the middle of its length, in the middle of its depth, or, deeper than the top, its back at the back
     * edge (the overhang to the front, clear of the pegboard).
     */
    private static void finished(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        Laid l = id == null ? OTHER : LAID.getOrDefault(id.getPath(), OTHER);
        Quaternionf turn = switch (l.lie()) {
            case UPRIGHT -> new Quaternionf();
            case FLIP -> turn(new double[]{-1, 0, 0}, new double[]{0, -1, 0});
            // the top to +x, the back down, the front up
            case BACK -> turn(new double[]{0, 0, -1}, new double[]{1, 0, 0});
        };
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int c = 0; c < 8; c++) {
            Vector3f v = new Vector3f((float) ((c & 1) == 0 ? l.lo()[0] : l.hi()[0]), (float) ((c & 2) == 0 ? l.lo()[1] : l.hi()[1]),
                    (float) ((c & 4) == 0 ? l.lo()[2] : l.hi()[2])).rotate(turn);
            double[] p = {v.x, v.y, v.z};
            for (int k = 0; k < 3; k++) {
                lo[k] = Math.min(lo[k], p[k]);
                hi[k] = Math.max(hi[k], p[k]);
            }
        }
        double z = hi[2] - lo[2] <= TOP_DEPTH ? TOP_DEPTH / 2 - (lo[2] + hi[2]) / 2 : TOP_DEPTH - hi[2];
        pose.pushPose();
        pose.translate(TOP_MID_X - (lo[0] + hi[0]) / 2, 0.9005 - lo[1], z);
        pose.mulPose(turn);
        // the item is drawn with the master cell's bottom centre at (0, -0.5, 0)
        pose.translate(0, 0.5, 0);
        var mc = Minecraft.getInstance();
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
        pose.popPose();
    }

    /** The open box of nails or screws at the bench's left end, its label to the body, its heap inside. */
    private static void box(String model, String heap, double heapY, PoseStack pose, MultiBufferSource buffers, int light) {
        double[] x = {-1, 0, 0}, y = {0, 1, 0};
        mesh(model, Set.of("body", "label", "flap"), BOX, turn(x, y), 0, SCALE, pose, buffers, light);
        mesh("furniture_work", Set.of(heap), new double[]{BOX[0], BOX[1] + heapY * K, BOX[2]}, turn(x, y), 0, SCALE, pose, buffers, light);
    }

    // ------------------------------------------------------------------------------------------------ drawing a mesh

    /**
     * Some parts (null: all) of a mesh (geo/{model}, its texture textures/item/{model}_mesh) with its origin at {@code at},
     * turned ({@code turn}: its x and y axes in the bench; null: upright), then {@code degrees} about its own y, at {@code scale}.
     */
    private static void mesh(String model, @Nullable Set<String> parts, double[] at, @Nullable Quaternionf turn, double degrees, float scale,
                             PoseStack pose, MultiBufferSource buffers, int light) {
        MeshModel m = VextrisMeshes.byGeometry(GEO.computeIfAbsent(model, k -> new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/" + k + ".geo.json")));
        if (m == null) return;
        List<MeshPart> list = new ArrayList<>();
        for (MeshPart p : m.allParts()) if (parts == null || parts.contains(p.name())) list.add(p);
        if (list.isEmpty()) return;
        pose.pushPose();
        pose.translate(at[0], at[1], at[2]);
        if (turn != null) pose.mulPose(turn);
        if (degrees != 0) pose.mulPose(Axis.YP.rotationDegrees((float) degrees));
        pose.scale(scale, scale, scale);
        ResourceLocation tex = TEX.computeIfAbsent(model, k -> new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/" + k + "_mesh.png"));
        MeshRenderer.renderParts(list, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        pose.popPose();
    }

    /** The turn that takes a mesh's x and y axes to {@code ex} and {@code ey} (unit, at right angles) of the bench. */
    private static Quaternionf turn(double[] ex, double[] ey) {
        Vector3f x = new Vector3f((float) ex[0], (float) ex[1], (float) ex[2]), y = new Vector3f((float) ey[0], (float) ey[1], (float) ey[2]);
        Vector3f z = new Vector3f(x).cross(y);
        return new Quaternionf().setFromNormalized(new Matrix3f(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z));
    }

    private static double[] norm(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    /** {@code b} at right angles to the unit {@code h}, unit. */
    private static double[] orth(double[] b, double[] h) {
        double d = b[0] * h[0] + b[1] * h[1] + b[2] * h[2];
        return norm(new double[]{b[0] - d * h[0], b[1] - d * h[1], b[2] - d * h[2]});
    }
}
