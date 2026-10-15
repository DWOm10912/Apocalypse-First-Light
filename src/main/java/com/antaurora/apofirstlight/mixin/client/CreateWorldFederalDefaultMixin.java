package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.worldgen.federal.FederalPreset;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Federal default (2026-10-14, docs/worldgen/federal_six_state_c2.md section 12): "Create New World" starts on the AFL
 * Federation preset instead of vanilla's minecraft:normal (in AFL that is the r1 map). Only the fresh screen changes:
 * CreateWorldScreen#openFresh passes exactly minecraft:normal, a re-created world (createFromExisting) passes its own
 * type (flat, debug or none) and keeps its settings. setWorldType also replaces the dimensions, so the type the button
 * shows is the one the world gets. Every other type stays selectable with the button (superflat, r1, other mods' types).
 * Nothing changes when another mod already opened the screen on its own type, or when the federal preset is not in the
 * world-type list (a data pack took it out of #minecraft:normal).
 */
@Mixin(WorldCreationUiState.class)
public abstract class CreateWorldFederalDefaultMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void apocalypse$federalDefault(Path saves, WorldCreationContext context, Optional<ResourceKey<WorldPreset>> preset,
                                           OptionalLong seed, CallbackInfo ci) {
        if (!FederalPreset.replacesDefault(preset)) return;
        WorldCreationUiState self = (WorldCreationUiState) (Object) this;
        for (WorldCreationUiState.WorldTypeEntry entry : self.getNormalPresetList()) {
            Holder<WorldPreset> holder = entry.preset();
            if (holder != null && holder.is(FederalPreset.PRESET)) {
                self.setWorldType(entry);
                return;
            }
        }
    }
}
