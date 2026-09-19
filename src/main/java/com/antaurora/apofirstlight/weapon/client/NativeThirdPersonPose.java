package com.antaurora.apofirstlight.weapon.client;

import com.eliotlash.mclib.math.IValue;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.keyframe.BoneAnimation;
import software.bernie.geckolib.core.keyframe.Keyframe;
import java.util.*;

/** Frozen loaded baseline on private bones: never samples or mutates the item's live controllers. */
final class NativeThirdPersonPose {
    private BakedGeoModel source, frozen;
    private Animation animation;

    BakedGeoModel resolve(BakedGeoModel model, Animation idle) {
        Objects.requireNonNull(idle, "Native Gun third-person rendering requires a loaded idle clip");
        if (source == model && animation == idle) return frozen;
        var channels = new HashMap<String, BoneAnimation>();
        for (var channel : idle.boneAnimations()) channels.put(channel.boneName(), channel);
        var roots = new ArrayList<GeoBone>();
        for (var root : model.topLevelBones()) {
            var copy = copy(root, null, channels);
            if (copy != null) roots.add(copy);
        }
        frozen = new BakedGeoModel(roots, model.properties());
        source = model;
        animation = idle; // Identity changes on resource reload, including animation-only reloads.
        return frozen;
    }

    private static boolean firstPersonOnly(String name) {
        return name.startsWith("fp_only_") || name.startsWith("empty_old_")
                || name.startsWith("reload_mag") || name.startsWith("new_mag")
                || name.startsWith("ref_") || Set.of("additional_magazine", "lefthand", "righthand",
                "left_hand_anchor", "right_hand_anchor", "camera", "view", "ref", "refit", "positioning").contains(name);
    }

    private static GeoBone copy(GeoBone source, GeoBone parent, Map<String, BoneAnimation> channels) {
        if (firstPersonOnly(source.getName())) return null;
        var initial = Objects.requireNonNull(source.getInitialSnapshot(), "Unregistered Native Gun bone");
        var bone = new GeoBone(parent, source.getName(), source.getMirror(), source.getInflate(),
                source.getReset(), source.shouldNeverRender());
        bone.setPivotX(source.getPivotX()); bone.setPivotY(source.getPivotY()); bone.setPivotZ(source.getPivotZ());
        bone.setRotX(initial.getRotX()); bone.setRotY(initial.getRotY()); bone.setRotZ(initial.getRotZ());
        bone.setPosX(initial.getOffsetX()); bone.setPosY(initial.getOffsetY()); bone.setPosZ(initial.getOffsetZ());
        bone.setScaleX(initial.getScaleX()); bone.setScaleY(initial.getScaleY()); bone.setScaleZ(initial.getScaleZ());
        var channel = channels.get(source.getName());
        if (channel != null) {
            var r = channel.rotationKeyFrames();
            bone.setRotX(initial.getRotX() + first(r.xKeyframes(), 0));
            bone.setRotY(initial.getRotY() + first(r.yKeyframes(), 0));
            bone.setRotZ(initial.getRotZ() + first(r.zKeyframes(), 0));
            var p = channel.positionKeyFrames();
            bone.setPosX(first(p.xKeyframes(), initial.getOffsetX()));
            bone.setPosY(first(p.yKeyframes(), initial.getOffsetY()));
            bone.setPosZ(first(p.zKeyframes(), initial.getOffsetZ()));
            var s = channel.scaleKeyFrames();
            bone.setScaleX(first(s.xKeyframes(), initial.getScaleX()));
            bone.setScaleY(first(s.yKeyframes(), initial.getScaleY()));
            bone.setScaleZ(first(s.zKeyframes(), initial.getScaleZ()));
        }
        bone.getCubes().addAll(source.getCubes());
        for (var child : source.getChildBones()) {
            var copy = copy(child, bone, channels);
            if (copy != null) bone.getChildBones().add(copy);
        }
        return bone;
    }

    private static float first(List<Keyframe<IValue>> frames, float fallback) {
        if (frames.isEmpty()) return fallback;
        var frame = frames.get(0);
        double value = (frame.length() == 0 ? frame.endValue() : frame.startValue()).get();
        return Double.isFinite(value) ? (float)value : fallback;
    }
}
