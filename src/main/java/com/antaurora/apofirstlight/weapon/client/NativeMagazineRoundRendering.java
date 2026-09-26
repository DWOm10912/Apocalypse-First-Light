package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.client.mesh.AflHybridMeshRendering;
import com.antaurora.apofirstlight.weapon.NativeGunAmmo;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;
import java.util.function.Supplier;

/** Draws the configured top round during the existing gun-bone traversal. */
final class NativeMagazineRoundRendering {
    static void render(ItemStack stack, GeoBone bone, PoseStack incoming,
                       MultiBufferSource buffers, int light, int overlay, Supplier<String> actionClip) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof NativeGunItem gun)) return;
        var definition = gun.definition();
        if (definition == null) return;
        var visual = definition.magazineRoundVisual();
        if (visual == null || bone.isHidden()) return;
        boolean main = visual.anchor().equals(bone.getName());
        boolean loadedAuxiliary = bone.getName().equals(visual.loadedAuxiliaryAnchor());
        boolean oldMagazine = bone.getName().equals(visual.oldMagazineAnchor());
        if (!main && !loadedAuxiliary && !oldMagazine) return;
        for (GeoBone parent = bone.getParent(); parent != null; parent = parent.getParent())
            if (parent.isHidingChildren() || (!main && parent.isHidden())) return;
        if (main) {
            if (NativeGunAmmo.read(stack, definition) <= 0) return;
        } else {
            String clip = actionClip.get();
            if (loadedAuxiliary) {
                if (!"reload_tactical".equals(clip) && !"reload_empty".equals(clip)
                        && !("inspect".equals(clip) && NativeGunAmmo.read(stack, definition) > 0)) return;
            } else if (!"reload_tactical".equals(clip)) return;
        }

        // GeckoLib has already applied this bone and all ancestors to incoming.
        // A zero-scale magazine makes the inherited transform singular, so it cannot
        // leave an independently drawn round floating during reload or inspect.
        float determinant = incoming.last().pose().determinant3x3();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1e-12f) return;

        PoseStack local = P901RenderMatrices.detachedCopy(incoming);
        RenderUtils.translateToPivotPoint(local, bone);
        AflHybridMeshRendering.renderAtCurrentPose(visual.geometry(), visual.texture(),
                local, buffers, light, overlay);
    }

    private NativeMagazineRoundRendering() {}
}
