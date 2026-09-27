package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.client.mesh.AflHybridMeshRendering;
import com.antaurora.apofirstlight.weapon.NativeGunAmmo;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;
import java.util.function.Supplier;

/** Draws the configured top round during the existing gun-bone traversal. */
final class NativeMagazineRoundRendering {
    static void render(ItemStack stack, GeoBone bone, PoseStack incoming,
                       MultiBufferSource buffers, int light, int overlay, ItemDisplayContext perspective,
                       Supplier<String> actionClip) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof NativeGunItem gun)) return;
        var definition = gun.definition();
        if (definition == null) return;
        var visual = definition.magazineRoundVisual();
        if (visual == null || bone.isHidden()) return;
        boolean main = visual.anchor().equals(bone.getName());
        boolean loadedAuxiliary = bone.getName().equals(visual.loadedAuxiliaryAnchor());
        boolean oldMagazine = bone.getName().equals(visual.oldMagazineAnchor());
        if (!main && !loadedAuxiliary && !oldMagazine) return;
        if (NativeGunShadowSkip.shouldSkip(stack, perspective, stack.getItem())) return;
        for (GeoBone parent = bone.getParent(); parent != null; parent = parent.getParent())
            if (parent.isHidingChildren() || parent.isHidden()) return;
        int ammo = NativeGunAmmo.read(stack, definition);
        if (main) {
            if (ammo <= 0) return;
        } else {
            String clip = actionClip.get();
            if (loadedAuxiliary) {
                if (!"reload_tactical".equals(clip) && !"reload_empty".equals(clip)
                        && !("inspect".equals(clip) && ammo > 0)) return;
            } else if (!"reload_tactical".equals(clip) || ammo <= 0) return;
        }

        // GeckoLib has already applied this bone and all ancestors to incoming.
        // A zero-scale magazine makes the inherited transform singular, so it cannot
        // leave an independently drawn round floating during reload or inspect.
        float determinant = incoming.last().pose().determinant3x3();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1e-12f) return;

        PoseStack local = NativeRenderMatrices.detachedCopy(incoming);
        RenderUtils.translateToPivotPoint(local, bone);
        // Optional authoring correction in the anchor's local space; defaults to identity.
        float[] offset = visual.localOffset(), rotation = visual.localRotation();
        if (offset[0] != 0 || offset[1] != 0 || offset[2] != 0)
            local.translate(offset[0] / 16F, offset[1] / 16F, offset[2] / 16F);
        if (rotation[0] != 0) local.mulPose(Axis.XP.rotationDegrees(rotation[0]));
        if (rotation[1] != 0) local.mulPose(Axis.YP.rotationDegrees(rotation[1]));
        if (rotation[2] != 0) local.mulPose(Axis.ZP.rotationDegrees(rotation[2]));
        AflHybridMeshRendering.renderAtCurrentPose(visual.geometry(), visual.texture(),
                local, buffers, light, overlay);
    }

    private NativeMagazineRoundRendering() {}
}
