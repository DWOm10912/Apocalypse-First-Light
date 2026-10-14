package com.antaurora.apofirstlight.wiring.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cabin.CabinBlocks;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.antaurora.apofirstlight.wiring.BuildingWiring;
import com.antaurora.apofirstlight.wiring.WiringChunk;
import com.antaurora.apofirstlight.wiring.WiringNode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Building Wiring V1, the wiring view (docs/gameplay/building_wiring_v1.md): while the wrench or the wire is in hand, the
 * selected circuit's wire is drawn through the blocks as a thin round cable in its colour (real coloured rubber-insulated
 * copper wire), everywhere it is loaded on this client ({@link WiringView}). Other circuits are not drawn (2026-10-13: one
 * circuit's wire beside fragments of others under the crosshair read as a broken run); the pick key takes another circuit
 * whose wire is in the aimed cell (WiringClient).
 * <p>
 * A node's wire runs through its anchor: the cell centre, in a roof cell the middle of the slab ({@link CabinBlocks.Roof#wireHeight}),
 * so a roof's wire lies along the slope. From the anchor a cable runs halfway to each linked neighbour's anchor (straight
 * links and slope links; the neighbour draws the other half) and to each device it taps, ending in a small terminal there;
 * a node with neither is a stub. Several circuits in one cell would run side by side: each circuit has its own offset from
 * the anchor, the same in every cell, so its halves meet. Normal play draws nothing.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class WiringXray {
    /** Cable radius (2 cm across: thinner than the visible wires of a real cable would look odd from a room away). */
    static final float R = 0.01F, JOINT = R * 1.15F, TERMINAL = 0.022F, STUB = 0.028F, TAP_REACH = 0.62F, MAX_DISTANCE = 48;
    /** Per-circuit offsets from the anchor: (a, b, (a + b) mod 4) over these four values, so any two circuits differ on two axes. */
    private static final float[] OFF = {-0.045F, -0.015F, 0.015F, 0.045F};
    /** Red, blue, green, yellow insulation (circuits 1-4) and the feeder's black service cable (base colours, shaded per face). */
    private static final int[][] RGB = {{196, 52, 42}, {44, 96, 196}, {52, 150, 72}, {222, 184, 40}, {52, 52, 56}};
    private static final int[] BRASS = {204, 186, 140};
    private static final float[] COS = new float[9], SIN = new float[9];
    private static final float LX = 0.30F, LY = 0.90F, LZ = 0.32F, ALPHA = 0.95F;
    private static final Direction[] HORIZONTAL = new Direction[8];
    private static final boolean[] UP = new boolean[8];

    static {
        for (int k = 0; k <= 8; k++) {
            double a = Math.PI / 4 * k + Math.PI / 8;
            COS[k] = (float) Math.cos(a);
            SIN[k] = (float) Math.sin(a);
        }
        for (Direction h : Direction.Plane.HORIZONTAL) for (boolean up : new boolean[]{true, false}) {
            int b = WiringNode.slopeBit(h, up);
            HORIZONTAL[b] = h;
            UP[b] = up;
        }
    }

    private WiringXray() {}

    @SubscribeEvent
    public static void stage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !WiringView.showing() || WiringView.CHUNKS.isEmpty()
                || AflShaderCompat.activeShadowPass()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        int selected = WiringView.circuit();
        Matrix4f m = event.getPoseStack().last().pose();
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(WiringRenderTypes.XRAY);
        double max2 = MAX_DISTANCE * MAX_DISTANCE;
        for (WiringView.Chunk c : WiringView.CHUNKS.values()) {
            for (int i = 0; i < c.keys().length; i++) {
                long node = c.nodes()[i];
                if (WiringNode.circuit(node) != selected) continue;
                BlockPos cell = WiringChunk.cellOf(c.pos(), c.keys()[i]);
                if (cell.distToCenterSqr(cam) > max2) continue;
                node(out, m, mc.level, cell, cam, node);
            }
        }
        buffers.endBatch(WiringRenderTypes.XRAY);
    }

    /** The wire's height in the cell (block units from its bottom): a roof's slab middle, else the centre. */
    private static float height(ClientLevel level, BlockPos cell) {
        BlockState s = level.getBlockState(cell);
        return s.getBlock() instanceof CabinBlocks.Roof ? (float) CabinBlocks.Roof.wireHeight(s) : 0.5F;
    }

    private static void node(VertexConsumer out, Matrix4f m, ClientLevel level, BlockPos cell, Vec3 cam, long node) {
        int i = WiringNode.circuit(node) - 1;
        float ox = OFF[i % 4], oy = OFF[i / 4 % 4], oz = OFF[(i % 4 + i / 4) % 4];
        float bx = (float) (cell.getX() - cam.x), by = (float) (cell.getY() - cam.y), bz = (float) (cell.getZ() - cam.z);
        float x = bx + 0.5F + ox, y = by + height(level, cell) + oy, z = bz + 0.5F + oz;
        int[] rgb = RGB[WiringNode.colour(node)];
        int links = WiringNode.links(node), taps = WiringNode.taps(node), slopes = WiringNode.slopes(node);
        if (links == 0 && taps == 0 && slopes == 0) {
            cube(out, m, x, y, z, STUB, rgb);
            return;
        }
        cube(out, m, x, y, z, JOINT, rgb);
        for (Direction d : Direction.values()) {
            int bit = 1 << d.get3DDataValue();
            if ((links & bit) != 0) {
                BlockPos n = cell.relative(d);
                halfway(out, m, x, y, z, d.getStepX(), d.getStepY() + height(level, n) - height(level, cell), d.getStepZ(), rgb);
            }
            if ((taps & bit) != 0) {   // straight out of the anchor along d, to TAP_REACH from the centre
                float ex = d.getAxis() == Direction.Axis.X ? bx + 0.5F + ox + d.getStepX() * TAP_REACH : x;
                float ey = d.getAxis() == Direction.Axis.Y ? by + 0.5F + oy + d.getStepY() * TAP_REACH : y;
                float ez = d.getAxis() == Direction.Axis.Z ? bz + 0.5F + oz + d.getStepZ() * TAP_REACH : z;
                segment(out, m, x, y, z, ex, ey, ez, rgb);
                cube(out, m, ex, ey, ez, TERMINAL, BRASS);
            }
        }
        for (int b = 0; b < 8; b++) {
            if ((slopes >> b & 1) == 0) continue;
            Direction h = HORIZONTAL[b];
            BlockPos n = BuildingWiring.slopeCell(cell, h, UP[b]);
            halfway(out, m, x, y, z, h.getStepX(), (UP[b] ? 1 : -1) + height(level, n) - height(level, cell), h.getStepZ(), rgb);
        }
    }

    /** Half of a link: from the anchor toward the neighbour's anchor (dx, dy, dz away), the neighbour draws the rest. */
    private static void halfway(VertexConsumer out, Matrix4f m, float x, float y, float z, float dx, float dy, float dz, int[] rgb) {
        segment(out, m, x, y, z, x + dx / 2, y + dy / 2, z + dz / 2, rgb);
    }

    /** An eight-sided cable of radius R from a to b, any direction. */
    private static void segment(VertexConsumer out, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, int[] rgb) {
        float tx = bx - ax, ty = by - ay, tz = bz - az, len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (len < 1.0E-4F) return;
        tx /= len; ty /= len; tz /= len;
        // u, v across the cable: u = t x helper (a helper not near t), v = t x u
        float hx = Math.abs(ty) < 0.9F ? 0 : 1, hy = Math.abs(ty) < 0.9F ? 1 : 0;
        float ux = ty * 0 - tz * hy, uy = tz * hx - tx * 0, uz = tx * hy - ty * hx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        float vx = ty * uz - tz * uy, vy = tz * ux - tx * uz, vz = tx * uy - ty * ux;
        int a = (int) (ALPHA * 255);
        for (int k = 0; k < 8; k++) {
            float c0 = COS[k], s0 = SIN[k], c1 = COS[k + 1], s1 = SIN[k + 1];
            float p0x = (ux * c0 + vx * s0) * R, p0y = (uy * c0 + vy * s0) * R, p0z = (uz * c0 + vz * s0) * R;
            float p1x = (ux * c1 + vx * s1) * R, p1y = (uy * c1 + vy * s1) * R, p1z = (uz * c1 + vz * s1) * R;
            double mid = Math.PI / 4 * (k + 0.5) + Math.PI / 8;
            float cm = (float) Math.cos(mid), sm = (float) Math.sin(mid);
            float s = shade(ux * cm + vx * sm, uy * cm + vy * sm, uz * cm + vz * sm);
            int r = (int) (rgb[0] * s), g = (int) (rgb[1] * s), bl = (int) (rgb[2] * s);
            vertex(out, m, ax + p0x, ay + p0y, az + p0z, r, g, bl, a);
            vertex(out, m, ax + p1x, ay + p1y, az + p1z, r, g, bl, a);
            vertex(out, m, bx + p1x, by + p1y, bz + p1z, r, g, bl, a);
            vertex(out, m, bx + p0x, by + p0y, bz + p0z, r, g, bl, a);
        }
    }

    /** A point on the unit circle across the axis (0 x, 1 y, 2 z). */
    private static float[] ring(int axis, float c, float s) {
        return switch (axis) {
            case 0 -> new float[]{0, c, s};
            case 1 -> new float[]{s, 0, c};
            default -> new float[]{c, s, 0};
        };
    }

    /** An axis-aligned cube of half size h at (x, y, z). */
    private static void cube(VertexConsumer out, Matrix4f m, float x, float y, float z, float h, int[] rgb) {
        int a = (int) (ALPHA * 255);
        for (Direction d : Direction.values()) {
            float s = shade(d.getStepX(), d.getStepY(), d.getStepZ());
            int r = (int) (rgb[0] * s), g = (int) (rgb[1] * s), b = (int) (rgb[2] * s);
            float cx = x + d.getStepX() * h, cy = y + d.getStepY() * h, cz = z + d.getStepZ() * h;
            float[] u = ring(d.getAxis().ordinal(), 1, 0), v = ring(d.getAxis().ordinal(), 0, 1);
            vertex(out, m, cx + (-u[0] - v[0]) * h, cy + (-u[1] - v[1]) * h, cz + (-u[2] - v[2]) * h, r, g, b, a);
            vertex(out, m, cx + (u[0] - v[0]) * h, cy + (u[1] - v[1]) * h, cz + (u[2] - v[2]) * h, r, g, b, a);
            vertex(out, m, cx + (u[0] + v[0]) * h, cy + (u[1] + v[1]) * h, cz + (u[2] + v[2]) * h, r, g, b, a);
            vertex(out, m, cx + (-u[0] + v[0]) * h, cy + (-u[1] + v[1]) * h, cz + (-u[2] + v[2]) * h, r, g, b, a);
        }
    }

    /** Flat light from above and a little in front: 0.62 in shadow to 1.0 facing the light. */
    private static float shade(float nx, float ny, float nz) {
        float l = (float) Math.sqrt(LX * LX + LY * LY + LZ * LZ);
        return 0.62F + 0.38F * Math.max(0, (nx * LX + ny * LY + nz * LZ) / l);
    }

    private static void vertex(VertexConsumer out, Matrix4f m, float x, float y, float z, int r, int g, int b, int a) {
        out.vertex(m, x, y, z).color(Math.min(255, r), Math.min(255, g), Math.min(255, b), a).endVertex();
    }
}
