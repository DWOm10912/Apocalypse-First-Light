package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.client.mesh.AflMeshCache;
import com.antaurora.apofirstlight.client.mesh.AflMeshModel;
import com.antaurora.apofirstlight.client.mesh.AflMeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventory (ItemDisplayContext.GUI) presentation shared by every attachment item (sights, muzzle devices, magazines):
 * whatever the model's size or authored origin, its bind-pose bounds are centred on the slot and scaled so the longest
 * projected axis fills {@link #FILL} of the slot. The item JSON's gui transform only supplies the common view rotation
 * (all attachments: rotation [20, 135, 0], scale 1), so every attachment shows at one orientation and one size.
 * Other display contexts (hand, ground, frame) keep their own transforms.
 */
public final class NativeAttachmentGuiFit {
    /** Longest projected axis / slot width (the native gun icons use 82-89 %). */
    public static final float FILL = .85F;
    private static final Map<Item, AABB> BOUNDS = new HashMap<>();
    private static long generation = -1;

    /** Call with the pose at the slot centre (after the renderer's +0.5 re-centring and any authored tilt). */
    public static void apply(ItemStack item, PoseStack pose) {
        var box = bounds(item);
        if (box == null) return;
        var m = pose.last().pose();
        // gui scale 1: one model block in this frame spans exactly one slot
        float slot = m.transformDirection(new Vector3f(1, 0, 0)).length();
        var c = new Vector3f((float) box.getCenter().x, (float) box.getCenter().y, (float) box.getCenter().z);
        float minX = Float.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = -minX;
        for (int i = 0; i < 8; i++) {
            var v = m.transformDirection(new Vector3f((float) ((i & 1) == 0 ? box.minX : box.maxX), (float) ((i & 2) == 0 ? box.minY : box.maxY),
                    (float) ((i & 4) == 0 ? box.minZ : box.maxZ)).sub(c));
            minX = Math.min(minX, v.x); maxX = Math.max(maxX, v.x); minY = Math.min(minY, v.y); maxY = Math.max(maxY, v.y);
        }
        float extent = Math.max(maxX - minX, maxY - minY);
        if (!(extent > 1e-6F) || !(slot > 1e-6F)) return;
        float k = FILL * slot / extent;
        pose.scale(k, k, k);
        // an orthographic box projects symmetric about its centre's projection: centring the box centres the icon
        pose.translate(-c.x, -c.y, -c.z);
    }

    private static AABB bounds(ItemStack item) {
        long current = AflMeshCache.snapshot().generation();
        if (current != generation) { BOUNDS.clear(); generation = current; }
        var cached = BOUNDS.get(item.getItem());
        if (cached != null) return cached;
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem());
        var geometry = new ResourceLocation(id.getNamespace(), "geo/" + id.getPath() + ".geo.json");
        var model = GeckoLibCache.getBakedModels().get(geometry);
        if (model == null) return null;
        var boxes = new ArrayList<AABB>();
        var pose = new PoseStack();
        var mesh = AflMeshCache.snapshot().get(geometry);
        for (var bone : model.topLevelBones()) collect(bone, pose, boxes, mesh);
        if (boxes.isEmpty()) return null;
        AABB union = boxes.get(0);
        for (var b : boxes) union = union.minmax(b);
        BOUNDS.put(item.getItem(), union);
        return union;
    }

    /** Bind-pose bounds of Gecko cubes and AFL mesh parts, as the attachment renderers draw them. */
    private static void collect(GeoBone bone, PoseStack pose, List<AABB> out, AflMeshModel mesh) {
        pose.pushPose();
        RenderUtils.prepMatrixForBone(pose, bone);
        for (var cube : bone.getCubes()) {
            pose.pushPose();
            RenderUtils.translateToPivotPoint(pose, cube); RenderUtils.rotateMatrixAroundCube(pose, cube); RenderUtils.translateAwayFromPivotPoint(pose, cube);
            double x = Double.POSITIVE_INFINITY, y = x, z = x, xx = -x, yy = -x, zz = -x;
            for (var q : cube.quads()) if (q != null) for (var v : q.vertices()) {
                var p = pose.last().pose().transformPosition(new Vector3f(v.position()));
                x = Math.min(x, p.x); y = Math.min(y, p.y); z = Math.min(z, p.z); xx = Math.max(xx, p.x); yy = Math.max(yy, p.y); zz = Math.max(zz, p.z);
            }
            if (Double.isFinite(x)) out.add(new AABB(x, y, z, xx, yy, zz));
            pose.popPose();
        }
        AflMeshRenderer.collectBounds(mesh, bone, pose, out);
        for (var child : bone.getChildBones()) collect(child, pose, out, mesh);
        pose.popPose();
    }

    private NativeAttachmentGuiFit() {}
}
