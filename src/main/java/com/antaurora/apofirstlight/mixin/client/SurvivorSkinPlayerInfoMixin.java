package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.survivor.SurvivorIdentityClient;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Survivor looks (docs/gameplay/survivor_death_succession_v0.md 13): the single place where a player's skin is
 * replaced. Every renderer reads the skin and arm model through PlayerInfo (AbstractClientPlayer delegates here), so
 * third person, first-person arms, AFL gun arms, other players and tab heads all show the same survivor. A successor
 * wears no account cape or elytra texture. Nothing is replaced for an account look; the GameProfile is never touched.
 */
@Mixin(PlayerInfo.class)
public abstract class SurvivorSkinPlayerInfoMixin {
    @Shadow
    @Final
    private GameProfile profile;

    @Inject(method = "getSkinLocation", at = @At("HEAD"), cancellable = true)
    private void afl$survivorSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation skin = SurvivorIdentityClient.skin(profile.getId());
        if (skin != null) cir.setReturnValue(skin);
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void afl$survivorModel(CallbackInfoReturnable<String> cir) {
        String model = SurvivorIdentityClient.model(profile.getId());
        if (model != null) cir.setReturnValue(model);
    }

    @Inject(method = "getCapeLocation", at = @At("HEAD"), cancellable = true)
    private void afl$survivorCape(CallbackInfoReturnable<ResourceLocation> cir) {
        if (SurvivorIdentityClient.replaced(profile.getId())) cir.setReturnValue(null);
    }

    @Inject(method = "getElytraLocation", at = @At("HEAD"), cancellable = true)
    private void afl$survivorElytra(CallbackInfoReturnable<ResourceLocation> cir) {
        if (SurvivorIdentityClient.replaced(profile.getId())) cir.setReturnValue(null);
    }
}
