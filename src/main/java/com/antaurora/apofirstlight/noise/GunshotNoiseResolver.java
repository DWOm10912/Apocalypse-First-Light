package com.antaurora.apofirstlight.noise;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import it.unimi.dsi.fastutil.Pair;

import java.util.Map;

public final class GunshotNoiseResolver {
    public static final double MIN_RADIUS = 8.0;

    private static final Map<ResourceLocation, Double> REFERENCE_RADII = Map.ofEntries(
            radius("m1911", 64.0),
            radius("b93r", 64.0),
            radius("cz75", 64.0),
            radius("glock_17", 64.0),
            radius("m9a4", 64.0),
            radius("p320", 64.0),

            radius("ump45", 72.0),
            radius("hk_mk23", 72.0),
            radius("hk_mp5a5", 72.0),
            radius("uzi", 72.0),
            radius("vector45", 72.0),

            radius("p90", 80.0),
            radius("rhino357", 80.0),
            radius("m320", 80.0),

            radius("deagle", 88.0),

            radius("ak47", 96.0),
            radius("aug", 96.0),
            radius("g36k", 96.0),
            radius("hk416d", 96.0),
            radius("m16a1", 96.0),
            radius("m16a4", 96.0),
            radius("m4a1", 96.0),
            radius("qbz_191", 96.0),
            radius("scar_l", 96.0),
            radius("type_81", 96.0),
            radius("taurus500", 96.0),
            radius("rpg7", 96.0),

            radius("aa12", 104.0),
            radius("m1014", 104.0),
            radius("m870", 104.0),
            radius("spas_12", 104.0),
            radius("sks_tactical", 104.0),

            radius("kar98", 112.0),
            radius("fn_evolys", 112.0),
            radius("fn_fal", 112.0),
            radius("hk_g3", 112.0),
            radius("m249", 112.0),
            radius("m700", 112.0),
            radius("mk14", 112.0),
            radius("rpk", 112.0),
            radius("scar_h", 112.0),
            radius("springfield1873", 112.0),

            radius("ai_awp", 128.0),

            radius("m95", 160.0),
            radius("m107", 160.0)
    );

    private GunshotNoiseResolver() {
    }

    public static double resolveRadius(ItemStack gunStack, ResourceLocation gunId) {
        Double explicitRadius = REFERENCE_RADII.get(gunId);
        CommonGunIndex gunIndex = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
        double baseRadius = explicitRadius != null
                ? explicitRadius
                : gunIndex == null ? 80.0 : fallbackRadius(gunIndex);
        return Math.max(MIN_RADIUS, baseRadius + resolveSilenceDistanceAddend(gunStack));
    }

    private static Map.Entry<ResourceLocation, Double> radius(String gunPath, double radius) {
        return Map.entry(new ResourceLocation("tacz", gunPath), radius);
    }

    private static double fallbackRadius(CommonGunIndex gunIndex) {
        String type = String.valueOf(gunIndex.getType()).toLowerCase(java.util.Locale.ROOT);
        return switch (type) {
            case "pistol" -> 64.0;
            case "smg" -> 72.0;
            case "rifle" -> 96.0;
            case "sniper" -> 112.0;
            case "shotgun" -> 96.0;
            case "mg", "machine_gun", "lmg" -> 112.0;
            default -> 80.0;
        };
    }

    private static double resolveSilenceDistanceAddend(ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) {
            return 0.0;
        }
        ResourceLocation attachmentId = gun.getAttachmentId(gunStack, AttachmentType.MUZZLE);
        if (attachmentId == null || attachmentId.equals(new ResourceLocation("tacz", "empty"))) {
            return 0.0;
        }
        CommonAttachmentIndex attachment = TimelessAPI.getCommonAttachmentIndex(attachmentId).orElse(null);
        if (attachment == null || attachment.getData() == null) {
            return 0.0;
        }
        JsonProperty<?> silence = attachment.getData().getModifier().get("silence");
        if (silence == null || !(silence.getValue() instanceof Pair<?, ?> pair)) {
            return 0.0;
        }
        if (pair.left() instanceof Modifier modifier) {
            return modifier.getAddend();
        }
        return 0.0;
    }
}
