package com.antaurora.apofirstlight.survivor;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.RegistryObject;

/**
 * The one query every system asks for a survivor's numbers (docs/gameplay/survivor_background_skills_v1_plan.md section 10):
 * the player's value of an AflAttributes attribute. Nobody, a Creative or Spectator player, or a player without the
 * attribute counts as an ordinary person (the attribute's default: offset 0, multiplier 1). Works on both sides: the
 * attributes are synced, so a client asks about its own and other players the same way.
 */
public final class SurvivorStats {
    private SurvivorStats() {}

    public static double get(Player player, RegistryObject<Attribute> attribute) {
        Attribute a = attribute.get();
        if (player == null || player.isCreative() || player.isSpectator()) return a.getDefaultValue();
        var instance = player.getAttribute(a);
        if (instance == null) return a.getDefaultValue();
        double value = instance.getValue();
        return Double.isFinite(value) ? value : a.getDefaultValue();
    }

    /** A multiplier attribute, never below min (a zone, a search or a time constant must stay positive). */
    public static double multiplier(Player player, RegistryObject<Attribute> attribute, double min) {
        return Math.max(min, get(player, attribute));
    }

    /** True when the player's value differs from the default (the character page lists these later). */
    public static boolean changed(Player player, RegistryObject<Attribute> attribute) {
        return get(player, attribute) != attribute.get().getDefaultValue();
    }
}
