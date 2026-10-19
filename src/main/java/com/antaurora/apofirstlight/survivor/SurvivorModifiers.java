package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Turns a survivor's body levels, growth levels (items with a "levels" curve: cold / heat tolerance) and traits into
 * attribute modifiers (docs/gameplay/survivor_background_skills_v1_plan.md 10): one modifier per (source, attribute), named
 * {@code afl.survivor:body/<id>}, {@code afl.survivor:growth/<id>} or {@code afl.survivor:trait/<id>}, with
 * a UUID derived from that name. An added value is ADDITION; a factor f is MULTIPLY_TOTAL f − 1, so several factors on
 * one attribute multiply. Permanent, so max health is already raised when the player file's health is read back; every
 * apply first removes all {@code afl.survivor:} modifiers, so a login, respawn or confirm always ends with exactly the
 * current survivor's set. A survivor still choosing (or dead, or none) gets none: an ordinary person.
 */
public final class SurvivorModifiers {
    static final String PREFIX = "afl.survivor:";

    private SurvivorModifiers() {
    }

    /** The current survivor's modifiers on this player (login, respawn, confirm, reset). */
    public static void apply(ServerPlayer player) {
        SurvivorLineage lineage = SurvivorWorldData.get(player.server).lineage(player.getUUID());
        SurvivorRecord record = lineage == null ? null : lineage.current();
        boolean living = record != null && record.state == SurvivorRecord.State.ALIVE
                && lineage.status == SurvivorLineage.Status.ACTIVE;
        apply(player, living ? record : null, SurvivorCatalog.get());
    }

    static void apply(Player player, @Nullable SurvivorRecord record, SurvivorCatalog.Snapshot catalog) {
        remove(player);
        if (record != null && SurvivorSetup.DONE.equals(record.setup)) {
            for (SurvivorCatalog.BodyDef def : catalog.body().values()) {
                int level = SurvivorSetup.bodyLevel(record, def);
                for (SurvivorCatalog.Curve curve : def.curves()) add(player, "body/" + def.id(), curve.at(level));
            }
            for (SurvivorCatalog.GrowthDef def : catalog.growth().values()) {
                int level = SurvivorSetup.growthLevel(record, catalog, def);
                if (level < 1) continue;   // level 0 changes nothing
                for (SurvivorCatalog.Curve curve : def.curves()) add(player, "growth/" + def.id(), curve.at(level));
            }
            for (String id : record.traits) {
                SurvivorCatalog.TraitDef trait = catalog.traits().get(id);
                if (trait == null) continue;
                for (SurvivorCatalog.Effect effect : trait.effects()) add(player, "trait/" + id, effect);
            }
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    /** Removes every survivor modifier from every attribute the player has. */
    static void remove(Player player) {
        for (Attribute attribute : ForgeRegistries.ATTRIBUTES.getValues()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;
            for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
                if (modifier.getName().startsWith(PREFIX)) instance.removeModifier(modifier.getId());
            }
        }
    }

    private static void add(Player player, String source, SurvivorCatalog.Effect effect) {
        if (effect.neutral()) return;
        AttributeInstance instance = player.getAttribute(effect.attribute());
        if (instance == null) return;
        String name = PREFIX + source;
        UUID id = UUID.nameUUIDFromBytes((name + "|" + ForgeRegistries.ATTRIBUTES.getKey(effect.attribute()) + "|"
                + (effect.multiply() ? "multiply" : "add")).getBytes(StandardCharsets.UTF_8));
        if (instance.getModifier(id) != null) {
            ApocalypseFirstLight.LOGGER.warn("[AFL Survivor] {} changes {} twice; the second is ignored", source,
                    ForgeRegistries.ATTRIBUTES.getKey(effect.attribute()));
            return;
        }
        instance.addPermanentModifier(effect.multiply()
                ? new AttributeModifier(id, name, effect.value() - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL)
                : new AttributeModifier(id, name, effect.value(), AttributeModifier.Operation.ADDITION));
    }
}
