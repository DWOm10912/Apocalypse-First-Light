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

    private static final Map<ResourceLocation, Double> REFERENCE_RADII = Map.of(
            new ResourceLocation("tacz", "m1911"), 64.0,
            new ResourceLocation("tacz", "ump45"), 72.0,
            new ResourceLocation("tacz", "ak47"), 96.0,
            new ResourceLocation("tacz", "kar98"), 112.0,
            new ResourceLocation("tacz", "m107"), 160.0
    );

    private GunshotNoiseResolver() {
    }

    public static double resolveRadius(ItemStack gunStack, ResourceLocation gunId) {
        CommonGunIndex gunIndex = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
        double baseRadius = gunIndex == null
                ? 80.0
                : REFERENCE_RADII.getOrDefault(gunId, fallbackRadius(gunIndex));
        return Math.max(MIN_RADIUS, baseRadius + resolveSilenceDistanceAddend(gunStack));
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
