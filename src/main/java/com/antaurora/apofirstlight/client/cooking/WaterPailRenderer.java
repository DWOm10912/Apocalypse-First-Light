package com.antaurora.apofirstlight.client.cooking;

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
