package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

/** Save registry migration only; old command IDs are not new registrations. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class NativeWeaponLegacyMappings {
    private NativeWeaponLegacyMappings() {}
    @SubscribeEvent public static void remap(MissingMappingsEvent event) {
        for (var mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, ApocalypseFirstLight.MOD_ID)) {
            switch (mapping.getKey().getPath()) {
                case "service_pistol" -> mapping.remap(AflItems.P9_01.get());
                case "m14" -> mapping.remap(AflItems.BR51_01.get());
                case "9mm_round" -> mapping.remap(AflItems.ROUND_9MM.get());
                // BR-51 V3 (docs/native_guns/br51_01_v3_game_model_v1.md): the 35-round box became the 30-round curved one
                case "br51_extended_magazine_35" -> mapping.remap(AflItems.BR51_01_EXTENDED_MAGAZINE.get());
            }
        }
        for (var mapping : event.getMappings(ForgeRegistries.Keys.SOUND_EVENTS, ApocalypseFirstLight.MOD_ID)) {
            String old = mapping.getKey().getPath();
            String next = old.startsWith("service_pistol_") ? "p9_01_" + old.substring(15)
                    : old.startsWith("m14_") ? "br51_01_" + old.substring(4) : old;
            if (!next.equals(old)) {
                var target = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(ApocalypseFirstLight.MOD_ID, next));
                if (target != null) mapping.remap(target);
            } else if (old.startsWith("br51_01_reload_") || old.startsWith("br51_01_inspect_slide_")) {
                mapping.ignore();   // the old BR-51 rig's per-step takes, retired with V3 (no one-to-one replacement)
            }
        }
    }
}
