package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.skeleton.OldBodyClothes;
import com.antaurora.apofirstlight.skeleton.OldBodyClothes.Garment;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkinMesh;
import com.antaurora.apofirstlight.survivor.SurvivorAppearance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Old-body clothes V1 (docs/dev/old_body_clothes_v1.md, user 2026-10-27 "类似原版的装备，装备之后显示，我们的可以用mesh"):
 * what the old cube body wears, each garment its shells over the body's own skin mesh (skeleton/OldBodyClothes,
 * SkinMesh#shell: the same rings, so they bend at every joint with the body; on a woman the female torso's waist and
 * chest, so nothing pokes through), each from its own texture on the vanilla skin net (textures/entity/clothing/,
 * tools/characters/old_body_skin_paint_v1.py). One set of items for men and women: the shirt in the base layer slot, the
 * jacket in the coat slot, the jeans and shoes in vanilla's legs and feet slots. The underwear is painted on the
 * survivor's skin and never comes off.
 */
public final class OldBodyWardrobe {
    private static final java.util.Map<String, ResourceLocation> TEXTURES = new java.util.HashMap<>();

    private OldBodyWardrobe() {
    }

    private static ResourceLocation texture(Garment garment, boolean slim) {
        return TEXTURES.computeIfAbsent(garment.texture(slim),
                k -> ResourceLocation.fromNamespaceAndPath(ApocalypseFirstLight.MOD_ID, "textures/entity/clothing/" + k + ".png"));
    }

    /** The garments an entity wears, inside out. */
    private static List<Garment> worn(@Nullable LivingEntity entity) {
        List<Garment> out = new ArrayList<>(4);
        if (entity == null) return out;
        if (entity.getItemBySlot(EquipmentSlot.LEGS).is(AflItems.CLOTHING_JEANS.get())) out.add(Garment.JEANS);
        if (entity.getItemBySlot(EquipmentSlot.FEET).is(AflItems.CLOTHING_SHOES.get())) out.add(Garment.SHOES);
        if (AflEquipmentSlots.get(entity, AflEquipmentSlots.BASE).is(AflItems.CLOTHING_SHIRT.get())) out.add(Garment.SHIRT);
        if (AflEquipmentSlots.get(entity, AflEquipmentSlots.COAT).is(AflItems.CLOTHING_JACKET.get())) out.add(Garment.JACKET);
        return out;
    }

    /** The garments of an outfit mask (SurvivorAppearance.Garment bits), inside out: the appearance page's preview. */
    private static List<Garment> outfit(int mask) {
        List<Garment> out = new ArrayList<>(4);
        if ((mask & SurvivorAppearance.Garment.JEANS.bit()) != 0) out.add(Garment.JEANS);
        if ((mask & SurvivorAppearance.Garment.SHOES.bit()) != 0) out.add(Garment.SHOES);
        if ((mask & SurvivorAppearance.Garment.SHIRT.bit()) != 0) out.add(Garment.SHIRT);
        if ((mask & SurvivorAppearance.Garment.JACKET.bit()) != 0) out.add(Garment.JACKET);
        return out;
    }

    /**
     * What an entity wears, on its posed skin mesh ({@code arms}: the own first person's thinner arms, SkinMesh#emit;
     * {@code shown}: whether a skin part is drawn, so a garment hides with the part it covers).
     */
    public static void draw(@Nullable LivingEntity entity, SkeletonPose pose, boolean slim, boolean female, float arms, Matrix4f mp, Matrix3f mn,
                            MultiBufferSource buffers, int light, int overlay, float r, float g, float b, float a, Predicate<String> shown) {
        draw(worn(entity), pose, slim, female, arms, mp, mn, buffers, light, overlay, r, g, b, a, shown);
    }

    /** An outfit mask's garments (the appearance page). */
    public static void drawOutfit(int mask, SkeletonPose pose, boolean slim, boolean female, Matrix4f mp, Matrix3f mn,
                                  MultiBufferSource buffers, int light, int overlay) {
        draw(outfit(mask), pose, slim, female, 1F, mp, mn, buffers, light, overlay, 1, 1, 1, 1, p -> true);
    }

    private static void draw(List<Garment> garments, SkeletonPose pose, boolean slim, boolean female, float arms, Matrix4f mp, Matrix3f mn,
                             MultiBufferSource buffers, int light, int overlay, float r, float g, float b, float a, Predicate<String> shown) {
        Vector3f p = new Vector3f(), n = new Vector3f();
        for (Garment garment : garments) {
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(texture(garment, slim)));
            SkinMesh.Sink sink = (x, y, z, u, v, nx, ny, nz) -> {
                mp.transformPosition(x / 16F, y / 16F, z / 16F, p);
                mn.transform(nx, ny, nz, n);
                consumer.vertex(p.x, p.y, p.z, r, g, b, a, u, v, overlay, light, n.x, n.y, n.z);
            };
            OldBodyClothes.emit(pose, garment, slim, female, arms, shown, sink);
        }
    }
}
