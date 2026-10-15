package com.antaurora.apofirstlight.wiring.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cabin.CabinBlocks;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.antaurora.apofirstlight.wiring.BuildingWiring;
import com.antaurora.apofirstlight.wiring.WiringChunk;
import com.antaurora.apofirstlight.wiring.WiringNode;
import com.antaurora.apofirstlight.wiring.WiringSupply;
import com.antaurora.apofirstlight.wiring.WiringSync;
import com.antaurora.apofirstlight.wiring.WiringTool;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Building Wiring V1, the wiring view (docs/gameplay/building_wiring_v1.md): while the wrench or the wire is in hand, the
 * selected circuit's wire is drawn through the blocks as a thin round cable in its colour (real coloured rubber-insulated
 * copper wire), everywhere it is loaded on this client ({@link WiringView}); the pick key takes another circuit whose wire
 * is in the aimed cell (WiringClient).
 * <p>
 * A node's wire runs through its anchor: the cell centre, in a roof cell the middle of the slab ({@link CabinBlocks.Roof#wireHeight}),
 * so a roof's wire lies along the slope. From the anchor a cable runs halfway to each linked neighbour's anchor (straight
 * links and slope links; the neighbour draws the other half) and to each device it taps, ending in a small terminal there;
 * a node with neither is a stub. Several circuits in one cell would run side by side: each circuit has its own offset from
 * the anchor, the same in every cell, so its halves meet. Normal play draws nothing.
 * <p>
 * Phase 5 (docs/gameplay/building_wiring_v1_phase5_draft.md 4):
 * <ul>
 *   <li>behind blocks dimmer: the selected circuit is drawn through everything at {@link #BEHIND} of its opacity, then in
 *   full where it is in sight: depth tested, every vertex pulled {@link #PULL} toward the camera along its line of sight
 *   (the picture is the same, only the depth changes), so a wire inside the wall or floor right in front counts as seen and
 *   one behind more blocks stays dim;</li>
 *   <li>show all (sneak + pick key): the other circuits too, half as thick and half as bright, only where in sight;</li>
 *   <li>device markers: a ring facing the camera at every wiring device near by, in the colour of the circuit that taps it
 *   (a dot in the middle), grey and hollow when nothing taps it; panels and service entries have an outer ring. Lights and
 *   switches tapped by the selected circuit (all of them with show all) are labelled with their group (组 N / 常亮 / 光控),
 *   untapped devices 未接线. Power is not shown (Jade has it);</li>
 *   <li>the wrench's route: the previewed cells as a dashed cable through the blocks, a ring at the start, a diamond at
 *   each waypoint;</li>
 *   <li>the cables are built once into cached geometry and only rebuilt when the wires, the selection, show all or the route
 *   change (or the player moves away from where it was built); markers rescan the nearby chunk sections twice a second.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class WiringXray {
    /** Cable radius (2 cm across: thinner than the visible wires of a real cable would look odd from a room away). */
    static final float R = 0.01F, JOINT = R * 1.15F, TERMINAL = 0.022F, STUB = 0.028F, TAP_REACH = 0.62F, MAX_DISTANCE = 48;
    /** Behind blocks: this share of the opacity; in sight: a block face nearer than this in front of a vertex hides it. */
    static final float BEHIND = 0.4F, PULL = 1.0F, NEAR = 0.06F;
    /** Markers within this distance; labels (and markers seen through blocks) within the shorter one. */
    static final float MARKER_DISTANCE = 32, LABEL_DISTANCE = 16, LABEL_SCALE = 0.0125F;
    /** The route's dashed cable: radius, and the two dashes along each step (fractions of it). */
    static final float DASH_R = 0.018F;
    private static final float[][] DASHES = {{0.1F, 0.4F}, {0.6F, 0.9F}};
    /** Per-circuit offsets from the anchor: (a, b, (a + b) mod 4) over these four values, so any two circuits differ on two axes. */
    private static final float[] OFF = {-0.045F, -0.015F, 0.015F, 0.045F};
    /** Red, blue, green, yellow insulation (circuits 1-4) and the feeder's black service cable (base colours, shaded per face). */
    private static final int[][] RGB = {{196, 52, 42}, {44, 96, 196}, {52, 150, 72}, {222, 184, 40}, {52, 52, 56}};
    /** Markers: the feeder's ring lighter than its cable (black on a dark room), untapped devices grey. */
    private static final int[] FEEDER_MARK = {92, 92, 100}, GREY = {150, 148, 144};
    private static final int[] BRASS = {204, 186, 140};
    private static final float[] COS = new float[9], SIN = new float[9], RING_COS = new float[17], RING_SIN = new float[17];
    private static final float LX = 0.30F, LY = 0.90F, LZ = 0.32F;
    private static final Direction[] HORIZONTAL = new Direction[8];
    private static final boolean[] UP = new boolean[8];

    static {
        for (int k = 0; k <= 8; k++) {
            double a = Math.PI / 4 * k + Math.PI / 8;
            COS[k] = (float) Math.cos(a);
            SIN[k] = (float) Math.sin(a);
        }
        for (int k = 0; k <= 16; k++) {
            RING_COS[k] = (float) Math.cos(Math.PI / 8 * k);
            RING_SIN[k] = (float) Math.sin(Math.PI / 8 * k);
        }
        for (Direction h : Direction.Plane.HORIZONTAL) for (boolean up : new boolean[]{true, false}) {
            int b = WiringNode.slopeBit(h, up);
            HORIZONTAL[b] = h;
            UP[b] = up;
        }
    }

    /** Quads as built: positions relative to {@link #origin}, an RGB per vertex (the pass sets the alpha). */
    private static final class Mesh {
        final FloatArrayList pos = new FloatArrayList();
        final IntArrayList rgb = new IntArrayList();

        void clear() {
            pos.clear();
            rgb.clear();
        }

        void add(float x, float y, float z, int r, int g, int b) {
            pos.add(x);
            pos.add(y);
            pos.add(z);
            rgb.add(Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b));
        }
    }

    /** A wiring device near by: its shape's centre, the circuit tapping it (0 none) and that tap's group as it counts. */
    private record Marker(BlockPos pos, double x, double y, double z, BuildingWiring.Kind kind, WiringSupply.Role role, int circuit, int group) {}

    private static final Mesh SELECTED = new Mesh(), OTHERS = new Mesh(), PREVIEW = new Mesh();
    private static final List<Marker> MARKERS = new ArrayList<>();
    private static int builtVersion = Integer.MIN_VALUE, scannedVersion = Integer.MIN_VALUE;
    private static Object builtLevel, scannedLevel;
    private static BlockPos origin = BlockPos.ZERO;
    private static long scannedTick;

    private WiringXray() {}

    @SubscribeEvent
    public static void stage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !WiringView.showing() || AflShaderCompat.activeShadowPass()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ClientLevel level = mc.level;
        BlockPos at = mc.player.blockPosition();
        if (builtVersion != WiringView.version() || builtLevel != level || origin.distManhattan(at) > 16) build(level, at);
        if (scannedVersion != WiringView.version() || scannedLevel != level || level.getGameTime() - scannedTick >= 10 || level.getGameTime() < scannedTick) scan(level, at);

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = new Vector3f(camera.getLeftVector()), up = new Vector3f(camera.getUpVector());
        float dx = (float) (origin.getX() - cam.x), dy = (float) (origin.getY() - cam.y), dz = (float) (origin.getZ() - cam.z);
        Matrix4f m = event.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int selected = WiringView.circuit();

        // 1. through the blocks, dim: the selected circuit, the near markers
        VertexConsumer out = buffers.getBuffer(WiringRenderTypes.XRAY);
        emit(out, m, SELECTED, dx, dy, dz, BEHIND, 0);
        for (Marker k : MARKERS) if (near(k, cam, LABEL_DISTANCE)) marker(out, m, k, cam, left, up, BEHIND, 0);
        buffers.endBatch(WiringRenderTypes.XRAY);
        // 2. in sight, full: the selected circuit, the others (show all), every marker
        out = buffers.getBuffer(WiringRenderTypes.XRAY_IN_SIGHT);
        emit(out, m, SELECTED, dx, dy, dz, 1, PULL);
        emit(out, m, OTHERS, dx, dy, dz, 1, PULL);
        for (Marker k : MARKERS) if (near(k, cam, MARKER_DISTANCE)) marker(out, m, k, cam, left, up, 1, PULL);
        buffers.endBatch(WiringRenderTypes.XRAY_IN_SIGHT);
        // 3. the route, through everything
        if (WiringView.routeStatus() != WiringTool.ROUTE_NONE && WiringView.routeStart() != null) {
            out = buffers.getBuffer(WiringRenderTypes.XRAY);
            emit(out, m, PREVIEW, dx, dy, dz, 1, 0);
            int[] rgb = RGB[WiringNode.colourOf(selected)];
            Vec3 s = anchor(level, WiringView.routeStart(), selected);
            ring(out, m, (float) (s.x - cam.x), (float) (s.y - cam.y), (float) (s.z - cam.z), left, up, 0.12F, 0.16F, rgb, 1, 0);
            for (BlockPos w : WiringView.routeWaypoints()) {
                Vec3 a = anchor(level, w, selected);
                diamond(out, m, (float) (a.x - cam.x), (float) (a.y - cam.y), (float) (a.z - cam.z), left, up, 0.09F, rgb);
            }
            buffers.endBatch(WiringRenderTypes.XRAY);
        }
        // 4. labels: faint through the blocks, full in sight
        labels(event.getPoseStack(), camera, cam, up, buffers, mc.font, selected);
    }

    private static boolean near(Marker k, Vec3 cam, float distance) {
        double x = k.x - cam.x, y = k.y - cam.y, z = k.z - cam.z;
        return x * x + y * y + z * z <= distance * distance;
    }

    // ---------------------------------------------------------------------------------------------- cached geometry

    private static void build(ClientLevel level, BlockPos at) {
        SELECTED.clear();
        OTHERS.clear();
        PREVIEW.clear();
        builtVersion = WiringView.version();
        builtLevel = level;
        origin = at.immutable();
        int selected = WiringView.circuit();
        boolean all = WiringView.showAll();
        double max2 = MAX_DISTANCE * MAX_DISTANCE;
        for (WiringView.Chunk c : WiringView.CHUNKS.values()) {
            for (int i = 0; i < c.keys().length; i++) {
                long node = c.nodes()[i];
                int circuit = WiringNode.circuit(node);
                if (circuit != selected && !all) continue;
                BlockPos cell = WiringChunk.cellOf(c.pos(), c.keys()[i]);
                if (cell.distSqr(at) > max2) continue;
                if (circuit == selected) node(SELECTED, level, cell, node, 1, 1);
                else node(OTHERS, level, cell, node, 0.5F, 0.5F);
            }
        }
        List<BlockPos> path = WiringView.routePath();
        int[] rgb = RGB[WiringNode.colourOf(selected)];
        for (int i = 1; i < path.size(); i++) {
            BlockPos a = path.get(i - 1), b = path.get(i);
            if (a.equals(b)) continue;
            Vec3 pa = anchor(level, a, selected).subtract(Vec3.atLowerCornerOf(origin)), pb = anchor(level, b, selected).subtract(Vec3.atLowerCornerOf(origin));
            for (float[] d : DASHES) {
                segment(PREVIEW, lerp(pa.x, pb.x, d[0]), lerp(pa.y, pb.y, d[0]), lerp(pa.z, pb.z, d[0]),
                        lerp(pa.x, pb.x, d[1]), lerp(pa.y, pb.y, d[1]), lerp(pa.z, pb.z, d[1]), DASH_R, rgb, 1);
            }
        }
    }

    private static float lerp(double a, double b, float t) {
        return (float) (a + (b - a) * t);
    }

    /** The wire's height in the cell (block units from its bottom): a roof's slab middle, else the centre. */
    private static float height(ClientLevel level, BlockPos cell) {
        BlockState s = level.getBlockState(cell);
        return s.getBlock() instanceof CabinBlocks.Roof ? (float) CabinBlocks.Roof.wireHeight(s) : 0.5F;
    }

    /** The circuit's anchor in the cell, world coordinates. */
    private static Vec3 anchor(ClientLevel level, BlockPos cell, int circuit) {
        int i = circuit - 1;
        return new Vec3(cell.getX() + 0.5 + OFF[i % 4], cell.getY() + height(level, cell) + OFF[i / 4 % 4], cell.getZ() + 0.5 + OFF[(i % 4 + i / 4) % 4]);
    }

    /** One node's cables into the mesh; width and brightness scale the cable (show all draws other circuits at half). */
    private static void node(Mesh out, ClientLevel level, BlockPos cell, long node, float width, float bright) {
        int i = WiringNode.circuit(node) - 1;
        float ox = OFF[i % 4], oy = OFF[i / 4 % 4], oz = OFF[(i % 4 + i / 4) % 4];
        float bx = cell.getX() - origin.getX(), by = cell.getY() - origin.getY(), bz = cell.getZ() - origin.getZ();
        float x = bx + 0.5F + ox, y = by + height(level, cell) + oy, z = bz + 0.5F + oz;
        int[] base = RGB[WiringNode.colour(node)];
        int[] rgb = {(int) (base[0] * bright), (int) (base[1] * bright), (int) (base[2] * bright)};
        int links = WiringNode.links(node), taps = WiringNode.taps(node), slopes = WiringNode.slopes(node);
        if (links == 0 && taps == 0 && slopes == 0) {
            cube(out, x, y, z, STUB * width, rgb);
            return;
        }
        cube(out, x, y, z, JOINT * width, rgb);
        float r = R * width;
        for (Direction d : Direction.values()) {
            int bit = 1 << d.get3DDataValue();
            if ((links & bit) != 0) {
                BlockPos n = cell.relative(d);
                float hx = d.getStepX(), hy = d.getStepY() + height(level, n) - height(level, cell), hz = d.getStepZ();
                segment(out, x, y, z, x + hx / 2, y + hy / 2, z + hz / 2, r, rgb, 1);
            }
            if ((taps & bit) != 0) {   // straight out of the anchor along d, to TAP_REACH from the centre
                float ex = d.getAxis() == Direction.Axis.X ? bx + 0.5F + ox + d.getStepX() * TAP_REACH : x;
                float ey = d.getAxis() == Direction.Axis.Y ? by + 0.5F + oy + d.getStepY() * TAP_REACH : y;
                float ez = d.getAxis() == Direction.Axis.Z ? bz + 0.5F + oz + d.getStepZ() * TAP_REACH : z;
                segment(out, x, y, z, ex, ey, ez, r, rgb, 1);
                int[] brass = {(int) (BRASS[0] * bright), (int) (BRASS[1] * bright), (int) (BRASS[2] * bright)};
                cube(out, ex, ey, ez, TERMINAL * width, brass);
            }
        }
        for (int b = 0; b < 8; b++) {
            if ((slopes >> b & 1) == 0) continue;
            Direction h = HORIZONTAL[b];
            BlockPos n = BuildingWiring.slopeCell(cell, h, UP[b]);
            float hy = (UP[b] ? 1 : -1) + height(level, n) - height(level, cell);
            segment(out, x, y, z, x + h.getStepX() / 2F, y + hy / 2, z + h.getStepZ() / 2F, r, rgb, 1);
        }
    }

    /** An eight-sided cable of radius r from a to b, any direction. */
    private static void segment(Mesh out, float ax, float ay, float az, float bx, float by, float bz, float r, int[] rgb, float bright) {
        float tx = bx - ax, ty = by - ay, tz = bz - az, len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (len < 1.0E-4F) return;
        tx /= len; ty /= len; tz /= len;
        // u, v across the cable: u = t x helper (a helper not near t), v = t x u
        float hx = Math.abs(ty) < 0.9F ? 0 : 1, hy = Math.abs(ty) < 0.9F ? 1 : 0;
        float ux = -tz * hy, uy = tz * hx, uz = tx * hy - ty * hx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        float vx = ty * uz - tz * uy, vy = tz * ux - tx * uz, vz = tx * uy - ty * ux;
        for (int k = 0; k < 8; k++) {
            float c0 = COS[k], s0 = SIN[k], c1 = COS[k + 1], s1 = SIN[k + 1];
            float p0x = (ux * c0 + vx * s0) * r, p0y = (uy * c0 + vy * s0) * r, p0z = (uz * c0 + vz * s0) * r;
            float p1x = (ux * c1 + vx * s1) * r, p1y = (uy * c1 + vy * s1) * r, p1z = (uz * c1 + vz * s1) * r;
            double mid = Math.PI / 4 * (k + 0.5) + Math.PI / 8;
            float cm = (float) Math.cos(mid), sm = (float) Math.sin(mid);
            float s = shade(ux * cm + vx * sm, uy * cm + vy * sm, uz * cm + vz * sm) * bright;
            int cr = (int) (rgb[0] * s), cg = (int) (rgb[1] * s), cb = (int) (rgb[2] * s);
            out.add(ax + p0x, ay + p0y, az + p0z, cr, cg, cb);
            out.add(ax + p1x, ay + p1y, az + p1z, cr, cg, cb);
            out.add(bx + p1x, by + p1y, bz + p1z, cr, cg, cb);
            out.add(bx + p0x, by + p0y, bz + p0z, cr, cg, cb);
        }
    }

    /** A point on the unit circle across the axis (0 x, 1 y, 2 z). */
    private static float[] across(int axis, float c, float s) {
        return switch (axis) {
            case 0 -> new float[]{0, c, s};
            case 1 -> new float[]{s, 0, c};
            default -> new float[]{c, s, 0};
        };
    }

    /** An axis-aligned cube of half size h at (x, y, z). */
    private static void cube(Mesh out, float x, float y, float z, float h, int[] rgb) {
        for (Direction d : Direction.values()) {
            float s = shade(d.getStepX(), d.getStepY(), d.getStepZ());
            int r = (int) (rgb[0] * s), g = (int) (rgb[1] * s), b = (int) (rgb[2] * s);
            float cx = x + d.getStepX() * h, cy = y + d.getStepY() * h, cz = z + d.getStepZ() * h;
            float[] u = across(d.getAxis().ordinal(), 1, 0), v = across(d.getAxis().ordinal(), 0, 1);
            out.add(cx + (-u[0] - v[0]) * h, cy + (-u[1] - v[1]) * h, cz + (-u[2] - v[2]) * h, r, g, b);
            out.add(cx + (u[0] - v[0]) * h, cy + (u[1] - v[1]) * h, cz + (u[2] - v[2]) * h, r, g, b);
            out.add(cx + (u[0] + v[0]) * h, cy + (u[1] + v[1]) * h, cz + (u[2] + v[2]) * h, r, g, b);
            out.add(cx + (-u[0] + v[0]) * h, cy + (-u[1] + v[1]) * h, cz + (-u[2] + v[2]) * h, r, g, b);
        }
    }

    /** Flat light from above and a little in front: 0.62 in shadow to 1.0 facing the light. */
    private static float shade(float nx, float ny, float nz) {
        float l = (float) Math.sqrt(LX * LX + LY * LY + LZ * LZ);
        return 0.62F + 0.38F * Math.max(0, (nx * LX + ny * LY + nz * LZ) / l);
    }

    // ----------------------------------------------------------------------------------------------------- drawing

    /** The mesh at the camera (d: origin minus camera), alpha for the pass, pulled toward the camera for the in-sight pass. */
    private static void emit(VertexConsumer out, Matrix4f m, Mesh mesh, float dx, float dy, float dz, float alpha, float pull) {
        int a = (int) (alpha * 255), n = mesh.rgb.size();
        float[] p = mesh.pos.elements();
        int[] c = mesh.rgb.elements();
        for (int i = 0; i < n; i++) {
            int rgb = c[i];
            vertex(out, m, p[i * 3] + dx, p[i * 3 + 1] + dy, p[i * 3 + 2] + dz, rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, a, pull);
        }
    }

    /** A vertex at camera-relative (x, y, z), moved pull toward the camera along its line of sight (the same pixel, nearer). */
    private static void vertex(VertexConsumer out, Matrix4f m, float x, float y, float z, int r, int g, int b, int a, float pull) {
        if (pull > 0) {
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            float f = len > pull + NEAR ? (len - pull) / len : NEAR / Math.max(len, 1.0E-4F);
            x *= f; y *= f; z *= f;
        }
        out.vertex(m, x, y, z).color(r, g, b, a).endVertex();
    }

    /** A ring facing the camera (left, up: the camera's axes) from radius r0 to r1 around camera-relative (x, y, z). */
    private static void ring(VertexConsumer out, Matrix4f m, float x, float y, float z, Vector3f left, Vector3f up, float r0, float r1,
                             int[] rgb, float alpha, float pull) {
        int a = (int) (alpha * 255);
        for (int k = 0; k < 16; k++) {
            float c0 = RING_COS[k], s0 = RING_SIN[k], c1 = RING_COS[k + 1], s1 = RING_SIN[k + 1];
            float ax = left.x() * c0 + up.x() * s0, ay = left.y() * c0 + up.y() * s0, az = left.z() * c0 + up.z() * s0;
            float bx = left.x() * c1 + up.x() * s1, by = left.y() * c1 + up.y() * s1, bz = left.z() * c1 + up.z() * s1;
            vertex(out, m, x + ax * r0, y + ay * r0, z + az * r0, rgb[0], rgb[1], rgb[2], a, pull);
            vertex(out, m, x + ax * r1, y + ay * r1, z + az * r1, rgb[0], rgb[1], rgb[2], a, pull);
            vertex(out, m, x + bx * r1, y + by * r1, z + bz * r1, rgb[0], rgb[1], rgb[2], a, pull);
            vertex(out, m, x + bx * r0, y + by * r0, z + bz * r0, rgb[0], rgb[1], rgb[2], a, pull);
        }
    }

    /** A filled diamond facing the camera (a waypoint). */
    private static void diamond(VertexConsumer out, Matrix4f m, float x, float y, float z, Vector3f left, Vector3f up, float h, int[] rgb) {
        vertex(out, m, x + up.x() * h, y + up.y() * h, z + up.z() * h, rgb[0], rgb[1], rgb[2], 255, 0);
        vertex(out, m, x + left.x() * h, y + left.y() * h, z + left.z() * h, rgb[0], rgb[1], rgb[2], 255, 0);
        vertex(out, m, x - up.x() * h, y - up.y() * h, z - up.z() * h, rgb[0], rgb[1], rgb[2], 255, 0);
        vertex(out, m, x - left.x() * h, y - left.y() * h, z - left.z() * h, rgb[0], rgb[1], rgb[2], 255, 0);
    }

    // ----------------------------------------------------------------------------------------------------- markers

    /** Every wiring device in the chunks the view covers, within 40 blocks up or down (only sections whose palette has one are read). */
    private static void scan(ClientLevel level, BlockPos at) {
        MARKERS.clear();
        scannedVersion = WiringView.version();
        scannedLevel = level;
        scannedTick = level.getGameTime();
        int r = WiringSync.RADIUS;
        for (int cx = (at.getX() >> 4) - r; cx <= (at.getX() >> 4) + r; cx++) for (int cz = (at.getZ() >> 4) - r; cz <= (at.getZ() >> 4) + r; cz++) {
            LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
            if (chunk == null) continue;
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection s = sections[i];
                int y0 = level.getSectionYFromSectionIndex(i) << 4;
                if (s == null || s.hasOnlyAir() || Math.abs(y0 + 8 - at.getY()) > 48 || !s.maybeHas(st -> st.is(BuildingWiring.DEVICES))) continue;
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    BlockState st = s.getBlockState(x, y, z);
                    if (st.is(BuildingWiring.DEVICES)) MARKERS.add(marker(level, new BlockPos((cx << 4) + x, y0 + y, (cz << 4) + z), st));
                }
            }
        }
    }

    private static Marker marker(ClientLevel level, BlockPos pos, BlockState st) {
        VoxelShape shape = st.getShape(level, pos);
        AABB b = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
        BuildingWiring.Kind kind = BuildingWiring.kind(st);
        WiringSupply.Role role = WiringSupply.role(st);
        int circuit = WiringClient.tapCircuit(st, pos), group = 0;
        BlockPos m = BuildingWiring.mount(st, pos);
        if (circuit > 0 && m != null && kind == BuildingWiring.Kind.LOAD) {
            Direction toward = Direction.fromDelta(pos.getX() - m.getX(), pos.getY() - m.getY(), pos.getZ() - m.getZ());
            if (toward != null) group = WiringSupply.effectiveGroup(role, WiringNode.group(WiringView.node(m, circuit), toward));
        }
        return new Marker(pos, pos.getX() + (b.minX + b.maxX) / 2, pos.getY() + (b.minY + b.maxY) / 2, pos.getZ() + (b.minZ + b.maxZ) / 2,
                kind, role, circuit, group);
    }

    private static void marker(VertexConsumer out, Matrix4f m, Marker k, Vec3 cam, Vector3f left, Vector3f up, float alpha, float pull) {
        float x = (float) (k.x - cam.x), y = (float) (k.y - cam.y), z = (float) (k.z - cam.z);
        if (k.circuit == 0) ring(out, m, x, y, z, left, up, 0.085F, 0.1F, GREY, alpha, pull);   // nothing taps it: hollow
        else {
            int[] rgb = k.circuit == WiringNode.FEEDER ? FEEDER_MARK : RGB[WiringNode.colourOf(k.circuit)];
            ring(out, m, x, y, z, left, up, 0.065F, 0.1F, rgb, alpha, pull);
            ring(out, m, x, y, z, left, up, 0, 0.03F, rgb, alpha, pull);
        }
        if (k.kind != BuildingWiring.Kind.LOAD)   // a panel or a service entry: where circuits start
            ring(out, m, x, y, z, left, up, 0.135F, 0.15F, k.circuit == 0 ? GREY : k.circuit == WiringNode.FEEDER ? FEEDER_MARK : RGB[WiringNode.colourOf(k.circuit)], alpha, pull);
    }

    /** The marker's label, or null: 未接线, or a light's or switch's group when its circuit is selected (or show all). */
    private static Component label(Marker k, int selected) {
        if (k.circuit == 0) return Component.translatable("message.apocalypse_firstlight.wiring.marker.untapped");
        if (k.kind != BuildingWiring.Kind.LOAD || k.role == WiringSupply.Role.OTHER || (k.circuit != selected && !WiringView.showAll())) return null;
        if (k.group == WiringNode.ALWAYS) return Component.translatable("message.apocalypse_firstlight.wiring.marker.always");
        if (k.group == WiringNode.PHOTOCELL) return Component.translatable("message.apocalypse_firstlight.wiring.marker.photocell");
        return Component.translatable("message.apocalypse_firstlight.wiring.marker.group", k.group);
    }

    private static void labels(PoseStack pose, Camera camera, Vec3 cam, Vector3f up, MultiBufferSource.BufferSource buffers, Font font, int selected) {
        boolean any = false;
        for (Marker k : MARKERS) {
            if (!near(k, cam, LABEL_DISTANCE)) continue;
            Component text = label(k, selected);
            if (text == null) continue;
            // above the ring, a little toward the camera (clear of the device's own model)
            double x = k.x - cam.x, y = k.y - cam.y, z = k.z - cam.z, len = Math.max(1.0E-3, Math.sqrt(x * x + y * y + z * z));
            double toward = Math.min(0.3, len * 0.5);
            pose.pushPose();
            pose.translate(x - x / len * toward + up.x() * 0.2, y - y / len * toward + up.y() * 0.2, z - z / len * toward + up.z() * 0.2);
            pose.mulPose(camera.rotation());
            pose.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
            Matrix4f m = pose.last().pose();
            float w = -font.width(text) / 2F;
            int rgb = k.circuit == 0 ? AflUiStyle.TEXT_DIM : AflUiStyle.TEXT;
            font.drawInBatch(text, w, -4, AflUiStyle.argb(rgb, BEHIND), false, m, buffers, Font.DisplayMode.SEE_THROUGH,
                    AflUiStyle.argb(AflUiStyle.BACK, 0.35F), LightTexture.FULL_BRIGHT);
            font.drawInBatch(text, w, -4, AflUiStyle.argb(rgb, 1), false, m, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
            any = true;
        }
        if (any) buffers.endBatch();
    }
}
