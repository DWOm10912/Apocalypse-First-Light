package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.weapon.NativeGunItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.joml.Matrix4f;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationProcessor;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Player skeleton V1 P2 (docs/dev/player_skeleton_v1_design.md 13): a held gun's bones as GeckoLib poses them this frame,
 * before the gun is drawn, so the hands can reach for anchors that move with every clip (shot, reload, inspect, draw). The
 * evaluation is the one NativeCameraBoneConsumer already runs for the camera bone ahead of the hand pass: same model,
 * manager and frame tick as the draw that follows (GeoModel#handleAnimations does not run twice for one instance and tick),
 * so the anchors are where the drawn gun has them. Frames are in the gun's bare GeckoLib frame (blocks), composed as
 * RenderUtils#prepMatrixForBone does; an anchor's frame is taken at its pivot (as NativeAnimatedWeaponRenderer's arm layer).
 * <p>
 * {@code still}: one bone (with its subtree) held at its bind pose, for the draw and put-away clips whose whole-gun move
 * (up from the bottom of the screen) the skeleton replaces with its own path; {@link SkeletonGunStance#render} holds the
 * same bone still when it draws the gun (NativeGunContextRenderer#skeletonDraw).
 */
final class GunBones {
    /** The action controller's clip this frame, or "" when it is stopped; the clip object (new for every trigger). */
    record Action(String clip, Object instance) {
        static final Action NONE = new Action("", null);
    }

    private GunBones() {
    }

    /**
     * Runs the gun's controllers for this frame. Returns the action clip playing, or null when the gun has no GeckoLib
     * model (yet): the caller keeps its fallback anchors.
     */
    @SuppressWarnings("unchecked")
    static Action evaluate(ItemStack stack, ItemDisplayContext perspective, float partial) {
        if (!(stack.getItem() instanceof NativeGunItem gun) || gun.animationAsset() == null) return null;
        long id = GeoItem.getId(stack);
        if (id == Long.MAX_VALUE) return null;
        if (!(IClientItemExtensions.of(stack).getCustomRenderer() instanceof GeoItemRenderer<?> renderer)) return null;
        var model = (GeoModel<NativeGunItem>) (GeoModel<?>) renderer.getGeoModel();
        var resource = model.getModelResource(gun);
        if (!GeckoLibCache.getBakedModels().containsKey(resource)
                || !GeckoLibCache.getBakedAnimations().containsKey(model.getAnimationResource(gun))) return null;
        model.getBakedModel(resource);
        var manager = gun.getAnimatableInstanceCache().getManagerForId(id);
        var state = new AnimationState<>(gun, 0, 0, partial, false);
        state.setData(DataTickets.TICK, gun.getTick(stack));
        state.setData(DataTickets.ITEM_RENDER_PERSPECTIVE, perspective);
        state.setData(DataTickets.ITEMSTACK, stack);
        manager.setData(DataTickets.ITEM_RENDER_PERSPECTIVE, perspective);
        model.addAdditionalStateData(gun, id, state::setData);
        model.handleAnimations(gun, id, state);
        AnimationController<?> action = manager.getAnimationControllers().get(NativeGunItem.ACTION_CONTROLLER);
        if (action == null || action.getAnimationState() == AnimationController.State.STOPPED || action.getCurrentAnimation() == null)
            return Action.NONE;
        return new Action(action.getCurrentAnimation().animation().name(), action.getCurrentAnimation());
    }

    /** A bone's frame at its pivot as the last {@link #evaluate} left it (gun blocks), or null when the gun has no such bone. */
    @SuppressWarnings("rawtypes")
    static Matrix4f frame(ItemStack stack, String bone, String still) {
        if (!(stack.getItem() instanceof NativeGunItem gun)) return null;
        if (!(IClientItemExtensions.of(stack).getCustomRenderer() instanceof GeoItemRenderer<?> renderer)) return null;
        AnimationProcessor processor = ((GeoModel) renderer.getGeoModel()).getAnimationProcessor();
        if (!(processor.getBone(bone) instanceof GeoBone b)) return null;
        return chain(b, still).translate(b.getPivotX() / 16F, b.getPivotY() / 16F, b.getPivotZ() / 16F);
    }

    private static Matrix4f chain(GeoBone b, String still) {
        Matrix4f m = b.getParent() == null ? new Matrix4f() : chain(b.getParent(), still);
        boolean rest = still != null && stillAbove(b, still);
        var bind = b.getInitialSnapshot();
        float px = b.getPivotX() / 16F, py = b.getPivotY() / 16F, pz = b.getPivotZ() / 16F;
        float x = rest ? bind.getOffsetX() : b.getPosX(), y = rest ? bind.getOffsetY() : b.getPosY(), z = rest ? bind.getOffsetZ() : b.getPosZ();
        float rx = rest ? bind.getRotX() : b.getRotX(), ry = rest ? bind.getRotY() : b.getRotY(), rz = rest ? bind.getRotZ() : b.getRotZ();
        float sx = rest ? bind.getScaleX() : b.getScaleX(), sy = rest ? bind.getScaleY() : b.getScaleY(), sz = rest ? bind.getScaleZ() : b.getScaleZ();
        m.translate(-x / 16F, y / 16F, z / 16F).translate(px, py, pz);
        if (rz != 0) m.rotateZ(rz);
        if (ry != 0) m.rotateY(ry);
        if (rx != 0) m.rotateX(rx);
        return m.scale(sx, sy, sz).translate(-px, -py, -pz);
    }

    /** The still bone is this bone itself (its own motion is cancelled; its subtree keeps its own clip motion). */
    private static boolean stillAbove(GeoBone b, String still) {
        return b.getName().equals(still);
    }

}
