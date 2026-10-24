package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.TrueDarkness;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * True darkness (client/TrueDarkness, docs/rendering/true_darkness_v1.md; user 2026-10-24: light level 0 is to be dark as
 * real darkness, not vanilla's grey): the lightmap leaves out vanilla's two pulls of every entry toward grey (0.75) by
 * 4 %, before and after the brightness setting, which made light 0 a visible grey. The brightness setting stays: it lifts
 * dim light, and nothing lifts none.
 * <p>V2 (user 2026-10-24, "even with moonlight the night is a bit bright"): the night's sky light follows the moon
 * phase, is grey-blue instead of vanilla's lavender, and the brightness setting no longer lifts it.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureDarknessMixin {
    /** V2 night: the moon phase, the sun's height and the brightness setting, once a frame. */
    @Inject(method = "updateLightTexture", at = @At("HEAD"))
    private void afl$begin(float partialTick, CallbackInfo ci) {
        TrueDarkness.beginLightmap(partialTick);
    }

    /** V2: the sky light of each entry, dimmed at night by the moon phase. */
    @Redirect(method = "updateLightTexture", require = 1,
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/renderer/LightTexture;getBrightness(Lnet/minecraft/world/level/dimension/DimensionType;I)F"))
    private float afl$skyLight(DimensionType type, int level) {
        return TrueDarkness.skyLight(LightTexture.getBrightness(type, level));
    }

    @Redirect(method = "updateLightTexture", require = 1,
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lnet/minecraft/client/renderer/LightTexture;getBrightness(Lnet/minecraft/world/level/dimension/DimensionType;I)F"))
    private float afl$blockLight(DimensionType type, int level) {
        return TrueDarkness.blockLight(LightTexture.getBrightness(type, level));
    }

    /** V1 grey floor left out; V2 grey-blue moonlight and no brightness lift for it (TrueDarkness#lerp). */
    @Redirect(method = "updateLightTexture", require = 1,
            at = @At(value = "INVOKE", target = "Lorg/joml/Vector3f;lerp(Lorg/joml/Vector3fc;F)Lorg/joml/Vector3f;", remap = false))
    private Vector3f afl$lerp(Vector3f self, Vector3fc other, float t) {
        return TrueDarkness.lerp(self, other, t);
    }
}
