package com.antaurora.apofirstlight.skeleton;

import java.util.List;

/**
 * Old-body clothes V1 (docs/dev/old_body_clothes_v1.md): what each garment covers on the old cube body, as shells over its
 * skin mesh (SkinMesh#shell). Inside out, each one standing off the one below so no two share a depth: the jeans 0.2 px
 * out, the shoes 0.4, the shirt 0.3 over the jeans' waist, the jacket 0.675 (0.375 over the shirt, as the torso gear
 * expects, ClientEquipmentSlots COAT_PX). The same for men and women: on a woman the shells follow the female torso.
 * Textures: textures/entity/clothing/{@link Garment#texture} (and _slim for sleeves on 3 px arms).
 */
public final class OldBodyClothes {
    /** One part a garment covers: the skin part ("body", "right_arm"...) and the shell over it. */
    public record Cover(String part, SkinMesh.Shell shell) {
    }

    public enum Garment {
        /** The jeans: the hips and the legs to the top of the shoe. */
        JEANS("jeans", List.of(new Cover("body", new SkinMesh.Shell(0.2F, 9F, 12F)),
                new Cover("right_leg", new SkinMesh.Shell(0.2F, 0F, 11F)), new Cover("left_leg", new SkinMesh.Shell(0.2F, 0F, 11F)))),
        /** Leather shoes: from just above the ankle, the toe 0.75 px ahead of the foot. */
        SHOES("shoes", List.of(new Cover("right_leg", new SkinMesh.Shell(0.4F, 10.25F, 12F, 10.75F, 0.75F)),
                new Cover("left_leg", new SkinMesh.Shell(0.4F, 10.25F, 12F, 10.75F, 0.75F)))),
        /** The denim shirt, long sleeves to the wrist, untucked over the jeans' waist. */
        SHIRT("shirt", List.of(new Cover("body", new SkinMesh.Shell(0.3F, 0F, 10.5F)),
                new Cover("right_arm", new SkinMesh.Shell(0.3F, 0F, 9.5F)), new Cover("left_arm", new SkinMesh.Shell(0.3F, 0F, 9.5F)))),
        /** The leather jacket, to the hips, sleeves a little longer than the shirt's. */
        JACKET("jacket", List.of(new Cover("body", new SkinMesh.Shell(0.675F, 0F, 11.5F)),
                new Cover("right_arm", new SkinMesh.Shell(0.675F, 0F, 9.75F)), new Cover("left_arm", new SkinMesh.Shell(0.675F, 0F, 9.75F))));

        private final String texture;
        private final List<Cover> covers;

        Garment(String texture, List<Cover> covers) {
            this.texture = texture;
            this.covers = covers;
        }

        /** The texture's file name under textures/entity/clothing/ (without .png): its 3 px arm net for slim arms. */
        public String texture(boolean slim) {
            return slim && covers.stream().anyMatch(c -> c.part().endsWith("_arm")) ? texture + "_slim" : texture;
        }

        public List<Cover> covers() {
            return covers;
        }
    }

    private OldBodyClothes() {
    }

    /** A garment's shells over a posed body, in the order of its covers. */
    public static void emit(SkeletonPose pose, Garment garment, boolean slim, boolean female, float arms, java.util.function.Predicate<String> shown,
                            SkinMesh.Sink sink) {
        for (Cover cover : garment.covers()) {
            if (!shown.test(cover.part())) continue;
            for (SkeletonRig.SkinPart part : pose.rig.skin)
                if (part.part().equals(cover.part()))
                    SkinMesh.shell(pose, part, slim, part.part().endsWith("_arm") ? arms : 1F, female, cover.shell(), sink);
        }
    }
}
