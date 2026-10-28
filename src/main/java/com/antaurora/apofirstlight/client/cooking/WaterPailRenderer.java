package com.antaurora.apofirstlight.client.cooking;

import com.antaurora.apofirstlight.cooking.PailHeat;
import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cooking.PailSlosh;
import com.antaurora.apofirstlight.cooking.WaterPailItem;
import com.antaurora.vextris.client.MeshRenderer;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshModel;
import com.antaurora.vextris.mesh.MeshPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The enamel water pail (Cooking V1 K1a; tools/build-equipment-meshes-v1.mjs enamel_water_pail, 1 px = 1 cm): set down
 * (on the ground, in a frame) as the mesh is baked, its bail laid back; in a hand it hangs plumb from the grip, the bail
 * stood up, whatever the arm's pose, and the water in it stays level with the world, tilted by its slosh
 * (client/cooking/PailClient). Pouring, it is lifted and tipped over the lip toward where the player faces.
 * <p>
 * The world's directions in the item's frame come from the pose stack (item to view) and the camera (world to view), so
 * the same code serves first and third person.
 */
public final class WaterPailRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation GEOMETRY = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/enamel_water_pail.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/enamel_water_pail_mesh.png");
    private static final ResourceLocation WATER = new ResourceLocation("minecraft", "block/water_still");
    /** 0.5 - mesh height 25.65 / 32 (the build's verticalOffset). */
    public static final double VERTICAL_OFFSET = -0.301551;
    /** The pail's axis (mesh px: the mesh is centred on its bounding box, the laid bail behind), the ears' height. */
    private static final float AXIS_Z = 0.1763F, EAR_Y = 20.2F;
    /** The bail laid back (deg, the generator's lay), and the grip's centre once it is stood up (mesh px). */
    private static final float BAIL_LAY = 72F, GRIP_Y = 34.83F;
    /** The pour: the tip (deg) and the lift (px) at full tilt. */
    private static final float POUR_TILT = 70F, POUR_LIFT = 14F;
    /** The water: its colour, and how many points around its edge. */
    private static final float WR = 0.30F, WG = 0.42F, WB = 0.50F, WA = 0.82F;
    private static final int EDGE = 28;

    private record Parts(MeshModel mesh, List<MeshPart> body, List<MeshPart> bail) {}
    private Parts parts;

    public WaterPailRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    private Parts parts(MeshModel mesh) {
        if (parts != null && parts.mesh == mesh) return parts;
        List<MeshPart> body = new ArrayList<>(), bail = new ArrayList<>();
        for (MeshPart p : mesh.allParts()) (p.name().startsWith("bail") || p.name().startsWith("grip") ? bail : body).add(p);
        return parts = new Parts(mesh, body, bail);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mesh = VextrisMeshes.byGeometry(GEOMETRY);
        if (mesh == null) return;
        Parts p = parts(mesh);
        Minecraft mc = Minecraft.getInstance();
        float partial = mc.getFrameTime();
        boolean held = context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        // the skeleton's prop (player_props/enamel_water_pail, placed by item/water_pail and item/water_pail_pour)
        boolean prop = context == ItemDisplayContext.NONE;
        Player holder = held || prop ? holder(mc, stack, context) : null;
        boolean world = context != ItemDisplayContext.GUI;   // a view whose camera is the world's
        VertexConsumer cutout = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        pose.pushPose();
        try {
            if (held) {
                pose.translate(0.5, 0.5, 0.5);   // the display's origin: the hand
                // plumb: the pail's up turned to the world's up, about the grip
                Vector3f up = local(pose, 0, 1, 0);
                pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, up.x, up.y, up.z));
                int ticks = holder == null ? -1 : WaterPailItem.pourTicks(holder, stack);
                if (ticks >= 0) {
                    float u = Mth.clamp((ticks + partial) / WaterPailItem.POUR_TIP, 0, 1);
                    u = u * u * (3 - 2 * u);
                    float yaw = (float) Math.toRadians(holder.getViewYRot(partial));
                    Vector3f ahead = local(pose, -Mth.sin(yaw), 0, Mth.cos(yaw));
                    float al = Mth.sqrt(ahead.x * ahead.x + ahead.z * ahead.z);
                    if (al > 1e-4F) {
                        float fx = ahead.x / al, fz = ahead.z / al, rim = (float) (PailSlosh.radius(PailSlosh.H) * 100) + 0.2F;
                        pose.translate(0, POUR_LIFT * u / 16F, 0);
                        // about the lip ahead: the pail's up toward the way it pours
                        float lx = fx * rim / 16F, ly = (float) (PailSlosh.H * 100 - GRIP_Y) / 16F, lz = fz * rim / 16F;
                        pose.translate(lx, ly, lz);
                        pose.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(POUR_TILT * u), fz, 0, -fx));
                        pose.translate(-lx, -ly, -lz);
                    }
                }
                pose.translate(0, -GRIP_Y / 16F, -AXIS_Z / 16F);   // the grip to the hand
                MeshRenderer.renderParts(p.body, pose, cutout, light, overlay, 1, 1, 1, 1);
                pose.pushPose();
                pose.translate(0, EAR_Y / 16F, AXIS_Z / 16F);
                pose.mulPose(Axis.XP.rotationDegrees(BAIL_LAY));   // stood up from its laid-back bake
                pose.translate(0, -EAR_Y / 16F, -AXIS_Z / 16F);
                MeshRenderer.renderParts(p.bail, pose, cutout, light, overlay, 1, 1, 1, 1);
                pose.popPose();
            } else {
                // ItemRenderer already applied the display transform and the -0.5 shift; the mesh is centred on X / Z, rests on y = 0
                pose.translate(0.5, VERTICAL_OFFSET, 0.5);
                MeshRenderer.renderParts(p.body, pose, cutout, light, overlay, 1, 1, 1, 1);
                // a prop being carried has its bail up in the hand; poured, it falls back as baked
                if (prop && holder != null && WaterPailItem.pourTicks(holder, stack) < 0) {
                    pose.pushPose();
                    pose.translate(0, EAR_Y / 16F, AXIS_Z / 16F);
                    pose.mulPose(Axis.XP.rotationDegrees(BAIL_LAY));
                    pose.translate(0, -EAR_Y / 16F, -AXIS_Z / 16F);
                    MeshRenderer.renderParts(p.bail, pose, cutout, light, overlay, 1, 1, 1, 1);
                    pose.popPose();
                } else MeshRenderer.renderParts(p.bail, pose, cutout, light, overlay, 1, 1, 1, 1);
            }
            long ml = WaterPailItem.water(stack);
            if (ml > 0) {
                double[] g = holder == null ? new double[2] : PailClient.gradient(holder, partial);
                Vector3f n = world ? local(pose, (float) -g[0], 1, (float) -g[1]) : new Vector3f(0, 1, 0);
                water(pose, buffers, light, overlay, (float) (PailSlosh.level(ml) * 100), n);
            }
        } finally {
            pose.popPose();
        }
    }

    /**
     * The water on a stove lid by how hot it is (Cooking V1 K1b, user 2026-10-27: "现在滚水还是一样的"): from
     * {@link #NUCLEATE_C} bubbles leave the bottom and rise (a few at first, then streams); from {@link #SIMMER_C} they reach
     * the top as small domes and the surface shivers; from {@link #ROLL_C} it rolls: big domes that heave up white with foam,
     * burst into a crater and spit droplets, the whole surface churning.
     */
    private static final double NUCLEATE_C = 70, SIMMER_C = 85, ROLL_C = 96;
    private static final int RINGS = 8, SMALL = 8, BIG = 11, BUBBLES = 22, DROPS = 3;
    /** Simmer domes and rolling domes: radius, height, life (mesh px, ticks). */
    private static final float SMALL_R = 1.5F, SMALL_H = 0.35F, SMALL_LIFE = 13F, BIG_R = 3.2F, BIG_H = 1.6F, BIG_LIFE = 9F;
    /** Foam on a rolling dome's top, and a bubble (rgba). */
    private static final float FR = 0.88F, FG = 0.92F, FB = 0.94F, BUBBLE_A = 0.6F;

    private static float unit(double v) {
        return (float) Math.max(0, Math.min(1, v));
    }

    private static long mix(long seed, int i, long n) {
        long h = seed * 6364136223846793005L + i * 1442695040888963407L + n * 2862933555777941757L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ h >>> 32;
    }

    /** A dome now: centre (px), radius, height, phase 0..1 (it rises to its top at 0.7, then bursts). */
    private record Dome(float x, float z, float r, float h, float phase) {
        float at(float px, float pz) {
            float dx = px - x, dz = pz - z, q = (dx * dx + dz * dz) / (r * r);
            if (q >= 1) return 0;
            float k = (1 - q) * (1 - q);
            if (phase < 0.7F) return h * Mth.sin(Mth.HALF_PI * phase / 0.7F) * k;
            float u = (phase - 0.7F) / 0.3F;   // burst: down through a shallow crater, flat again
            return h * (1 - 2.2F * u) * (1 - u) * k;
        }
    }

    /** The domes on the surface at game tick {@code time}: {@code count} of them, each its own cycle, scattered by {@code seed}. */
    private static void domes(List<Dome> out, int count, float r, float h, float life, float span, float time, long seed) {
        for (int i = 0; i < count; i++) {
            float cycle = time / life + i / (float) count;
            long n = (long) Math.floor(cycle), m = mix(seed, i, n);
            float a = (m & 0xFFFF) / 65535F * Mth.TWO_PI, d = Mth.sqrt((m >>> 16 & 0xFFFF) / 65535F) * span;
            float size = 0.75F + 0.5F * (m >>> 32 & 0xFF) / 255F;
            out.add(new Dome(Mth.cos(a) * d, Mth.sin(a) * d, r * size, h * size, cycle - n));
        }
    }

    /**
     * Standing on a stove lid (client BoxStoveRenderer; the pose at the lid's centre, level, the bail to lie toward -z): the
     * pail at real size as baked and its water at {@code celsius}, at game tick {@code time}; {@code seed} keeps two pails apart.
     * It bubbles only while the fire under it burns ({@code heated}): taken off the heat, a boil stops in moments, the
     * water only steams (client/cooking/PailClient#steam).
     */
    public static void onStove(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, int overlay, double celsius, boolean heated,
                               float time, long seed) {
        MeshModel mesh = VextrisMeshes.byGeometry(GEOMETRY);
        if (mesh == null) return;
        pose.pushPose();
        pose.scale(0.16F, 0.16F, 0.16F);   // 1 mesh px = 1 cm
        pose.translate(0, 0, -AXIS_Z / 16F);
        MeshRenderer.renderParts(mesh.allParts(), pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay, 1, 1, 1, 1);
        long ml = WaterPailItem.water(stack);
        if (ml > 0) boiling(pose, buffers, light, overlay, (float) (PailSlosh.level(ml) * 100), heated ? celsius : PailHeat.AMBIENT, time, seed);
        pose.popPose();
    }

    private static void boiling(PoseStack pose, MultiBufferSource buffers, int light, int overlay, float level, double celsius, float time, long seed) {
        float nucleate = unit((celsius - NUCLEATE_C) / (SIMMER_C - NUCLEATE_C)), simmer = unit((celsius - SIMMER_C) / (ROLL_C - SIMMER_C)),
                roll = unit((celsius - ROLL_C) / (PailHeat.BOIL - ROLL_C));
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER);
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        pose.pushPose();
        pose.scale(1F / 16F, 1F / 16F, 1F / 16F);   // mesh px
        pose.translate(0, 0, AXIS_Z);
        Matrix4f m = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        float floor = (float) (PailSlosh.WALL * 100) + 0.15F, rim = (float) (PailSlosh.H * 100) - 0.25F;
        float y0 = Mth.clamp(level, floor, rim - BIG_H), wall = (float) (PailSlosh.radius(y0 / 100.0) * 100) - 0.1F;
        float span = (float) (PailSlosh.RT * 200), bu = sprite.getU(8), bv = sprite.getV(8);
        // a bubble or a droplet is a disc turned to the camera (round from any side): toward the camera in this frame
        Vector3f face = new Matrix3f(m).invert().transform(new Vector3f(0, 0, 1)).normalize();
        Vector3f side = new Vector3f(0, 1, 0).cross(face);
        if (side.lengthSquared() < 1e-4F) side.set(1, 0, 0);
        side.normalize();
        Vector3f lift = new Vector3f(face).cross(side).normalize();

        // bubbles off the bottom, rising faster as they go (seen through the water), sitting a moment at the top
        int bubbles = Math.round(BUBBLES * (0.35F * nucleate + 0.65F * Math.max(simmer, roll)));
        float bottomR = (float) (PailSlosh.radius(floor / 100.0) * 100) * 0.85F;
        for (int i = 0; i < bubbles; i++) {
            float life = 14 + (mix(seed + 7, i, 0) & 7);
            float cycle = time / life + i / (float) Math.max(1, bubbles);
            long n = (long) Math.floor(cycle), h = mix(seed + 7, i, n);
            float u = cycle - n, a = (h & 0xFFFF) / 65535F * Mth.TWO_PI, d = Mth.sqrt((h >>> 16 & 0xFFFF) / 65535F) * bottomR;
            float climb = Math.min(1, u / 0.85F), y = floor + 0.3F + (y0 - floor - 0.5F) * climb * climb;
            float s = (0.1F + 0.12F * (h >>> 32 & 0xFF) / 255F) * (1 + climb * (0.5F + roll));   // 2-4 mm, swelling as they rise
            bubble(out, m, normal, Mth.cos(a) * d, y, Mth.sin(a) * d, s, side, lift, face, 0.05F, BUBBLE_A, bu, bv, light, overlay);
        }

        // the surface: a shiver, small domes of a simmer, big rolling domes
        List<Dome> domes = new ArrayList<>();
        domes(domes, Math.round(SMALL * simmer * (1 - 0.5F * roll)), SMALL_R, SMALL_H, SMALL_LIFE, wall - SMALL_R, time, seed);
        domes(domes, Math.round(BIG * roll), BIG_R, BIG_H, BIG_LIFE, wall - BIG_R * 0.8F, time, seed + 3);
        float shiver = 0.05F * nucleate + 0.1F * simmer, churn = 0.35F * roll;
        float[][] px = new float[RINGS + 1][EDGE + 1], pz = new float[RINGS + 1][EDGE + 1], py = new float[RINGS + 1][EDGE + 1], foam = new float[RINGS + 1][EDGE + 1];
        Vector3f[][] pn = new Vector3f[RINGS + 1][EDGE + 1];
        float e = 0.3F;
        for (int j = 0; j <= RINGS; j++)
            for (int i = 0; i <= EDGE; i++) {
                float an = (float) (2 * Math.PI * i / EDGE), r = wall * j / RINGS, x = r * Mth.cos(an), z = r * Mth.sin(an);
                float held = j == RINGS ? 0.35F : 1;   // the wall holds the edge down
                float top = surface(domes, x, z, time, shiver, churn);
                px[j][i] = x;
                pz[j][i] = z;
                py[j][i] = y0 + held * top;
                float gx = (surface(domes, x + e, z, time, shiver, churn) - surface(domes, x - e, z, time, shiver, churn)) / (2 * e) * held;
                float gz = (surface(domes, x, z + e, time, shiver, churn) - surface(domes, x, z - e, time, shiver, churn)) / (2 * e) * held;
                pn[j][i] = new Vector3f(-gx, 1, -gz).normalize();
                foam[j][i] = roll * (0.15F + 0.75F * unit(top / BIG_H));   // white where it heaves highest
            }
        for (int j = 0; j < RINGS; j++)
            for (int i = 0; i < EDGE; i++) {
                int[][] q = {{j, i}, {j + 1, i}, {j + 1, i + 1}, {j, i + 1}};
                for (int[] c : q) {
                    float f = foam[c[0]][c[1]], x = px[c[0]][c[1]], z = pz[c[0]][c[1]];
                    Vector3f nn = pn[c[0]][c[1]];
                    out.vertex(m, x, py[c[0]][c[1]], z).color(Mth.lerp(f, WR, FR), Mth.lerp(f, WG, FG), Mth.lerp(f, WB, FB), Mth.lerp(f, WA, 0.93F))
                            .uv(sprite.getU(8 + 16 * x / span), sprite.getV(8 + 16 * z / span))
                            .overlayCoords(overlay).uv2(light).normal(normal, nn.x, nn.y, nn.z).endVertex();
                }
            }

        // droplets spat up as a rolling dome bursts (2-3 cm high)
        if (roll > 0.3F)
            for (int k = 0; k < domes.size(); k++) {
                Dome d = domes.get(k);
                if (d.h < BIG_H * 0.7F || d.phase < 0.7F) continue;
                float u = (d.phase - 0.7F) / 0.3F;
                for (int i = 0; i < DROPS; i++) {
                    long h = mix(seed + 11, k * DROPS + i, (long) Math.floor(time / BIG_LIFE));
                    float a = (h & 0xFFFF) / 65535F * Mth.TWO_PI, v = 7 + 6 * (h >>> 16 & 0xFF) / 255F, out2 = 1.5F + 2 * (h >>> 24 & 0xFF) / 255F;
                    float x = d.x + Mth.cos(a) * out2 * u, z = d.z + Mth.sin(a) * out2 * u, y = y0 + d.h * 0.5F + v * u - v * u * u;
                    if (x * x + z * z > wall * wall) continue;
                    bubble(out, m, normal, x, y, z, 0.18F, side, lift, face, 0.75F, 0.9F, bu, bv, light, overlay);
                }
            }
        pose.popPose();
    }

    /** The surface's rise at (x, z) px: the shiver of hot water, the churn of a rolling boil, and the domes. */
    private static float surface(List<Dome> domes, float x, float z, float time, float shiver, float churn) {
        float h = shiver * Mth.sin(0.9F * x + 0.6F * time) * Mth.sin(0.8F * z - 0.5F * time)
                + churn * Mth.sin(0.33F * x + 0.17F * time) * Mth.sin(0.29F * z - 0.13F * time);
        for (Dome d : domes) h += d.at(x, z);
        return h;
    }

    /**
     * A round disc of radius s at (x, y, z) px, turned to the camera (side, lift: its plane; face: toward the camera): pale
     * and clear in the middle ({@code core} alpha), brightest at its edge ({@code edge}), as a bubble in water looks; a
     * droplet has a full core.
     */
    private static void bubble(VertexConsumer out, Matrix4f m, Matrix3f normal, float x, float y, float z, float s, Vector3f side, Vector3f lift,
                               Vector3f face, float core, float edge, float u, float v, int light, int overlay) {
        int n = 12;
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            float c0 = Mth.cos(a0) * s, s0 = Mth.sin(a0) * s, c1 = Mth.cos(a1) * s, s1 = Mth.sin(a1) * s;
            // the middle, the edge, the edge, the middle again (one quad as a triangle): the core fades out to the bright rim
            disc(out, m, normal, x, y, z, 0, 0, side, lift, face, core, u, v, light, overlay);
            disc(out, m, normal, x, y, z, c0, s0, side, lift, face, edge, u, v, light, overlay);
            disc(out, m, normal, x, y, z, c1, s1, side, lift, face, edge, u, v, light, overlay);
            disc(out, m, normal, x, y, z, 0, 0, side, lift, face, core, u, v, light, overlay);
        }
    }

    private static void disc(VertexConsumer out, Matrix4f m, Matrix3f normal, float x, float y, float z, float a, float b, Vector3f side,
                             Vector3f lift, Vector3f face, float alpha, float u, float v, int light, int overlay) {
        out.vertex(m, x + side.x * a + lift.x * b, y + side.y * a + lift.y * b, z + side.z * a + lift.z * b)
                .color(0.93F, 0.97F, 1F, alpha).uv(u, v).overlayCoords(overlay).uv2(light).normal(normal, face.x, face.y + 0.5F, face.z).endVertex();
    }

    /** The player this stack is in the hand of: the camera's in first person, else the one whose main hand it is (a prop too). */
    private static Player holder(Minecraft mc, ItemStack stack, ItemDisplayContext context) {
        if (context.firstPerson()) return mc.player;
        if (mc.level == null) return null;
        for (Player p : mc.level.players()) if (p.getMainHandItem() == stack) return p;
        return null;
    }

    /** A world direction in the pose's frame (unit): world to view by the camera, view to item by the pose's inverse. */
    private static Vector3f local(PoseStack pose, float x, float y, float z) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f d = new Vector3f(x, y, z);
        new Matrix3f().rotationX((float) Math.toRadians(camera.getXRot())).rotateY((float) Math.toRadians(camera.getYRot() + 180)).transform(d);
        new Matrix3f().set(pose.last().pose()).invert().transform(d);
        return d.normalize();
    }

    /**
     * The water: a plane through the axis at the level the water stands, its normal n (the pose's frame), cut by the
     * wall: each edge point lies on the wall's radius at its own height, held between the floor and the rim.
     */
    private static void water(PoseStack pose, MultiBufferSource buffers, int light, int overlay, float level, Vector3f n) {
        if (n.y < 0.25F) return;   // tipped past what a plane in a pail can show
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER);
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        pose.pushPose();
        pose.scale(1F / 16F, 1F / 16F, 1F / 16F);   // mesh px
        pose.translate(0, 0, AXIS_Z);
        Matrix4f m = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        float floor = (float) (PailSlosh.WALL * 100) + 0.15F, rim = (float) (PailSlosh.H * 100) - 0.25F;
        float[] xs = new float[EDGE + 1], ys = new float[EDGE + 1], zs = new float[EDGE + 1];
        for (int i = 0; i <= EDGE; i++) {
            float a = (float) (2 * Math.PI * i / EDGE), c = Mth.cos(a), s = Mth.sin(a);
            float y = level, r = 0;
            for (int k = 0; k < 2; k++) {   // the wall's radius where the plane meets it
                r = (float) (PailSlosh.radius(Mth.clamp(y, floor, rim) / 100.0) * 100) - 0.1F;
                y = level - (n.x * r * c + n.z * r * s) / n.y;
            }
            xs[i] = r * c;
            ys[i] = Mth.clamp(y, floor, rim);
            zs[i] = r * s;
        }
        float span = (float) (PailSlosh.RT * 200);
        for (int i = 0; i < EDGE; i++) {
            vertex(out, m, normal, 0, Mth.clamp(level, floor, rim), 0, sprite, span, light, overlay, n);
            vertex(out, m, normal, xs[i], ys[i], zs[i], sprite, span, light, overlay, n);
            vertex(out, m, normal, xs[i + 1], ys[i + 1], zs[i + 1], sprite, span, light, overlay, n);
            vertex(out, m, normal, xs[i + 1], ys[i + 1], zs[i + 1], sprite, span, light, overlay, n);
        }
        pose.popPose();
    }

    private static void vertex(VertexConsumer out, Matrix4f m, Matrix3f normal, float x, float y, float z, TextureAtlasSprite sprite,
                               float span, int light, int overlay, Vector3f n) {
        out.vertex(m, x, y, z).color(WR, WG, WB, WA)
                .uv(sprite.getU(8 + 16 * x / span), sprite.getV(8 + 16 * z / span))
                .overlayCoords(overlay).uv2(light).normal(normal, n.x, n.y, n.z).endVertex();
    }
}
