package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Survivor attribute layer P1 (docs/gameplay/survivor_background_skills_v1_plan.md section 10): one vanilla attribute per
 * number a survivor's body, traits and growth may change. Each system reads its attribute through SurvivorStats; body,
 * traits, growth, gear and drugs only add modifiers. Defaults leave every system exactly as before: an offset is 0 (added
 * to the system's own config value), a multiplier is 1. All are on the player and synced to clients (sway, QTE, hearing
 * and step sounds are client side). Max health and attack damage stay vanilla's.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AflAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, ApocalypseFirstLight.MOD_ID);
    private static final List<RegistryObject<Attribute>> ALL = new ArrayList<>();

    // body
    /** kg added to the comfort capacity (item_mass comfort_capacity_kg). */
    public static final RegistryObject<Attribute> CARRY_CAPACITY = offset("carry_capacity", -30, 60);
    /** Added to the stamina maximum (stamina_v1 max). */
    public static final RegistryObject<Attribute> MAX_STAMINA = offset("max_stamina", -90, 200);
    public static final RegistryObject<Attribute> STAMINA_REGEN = multiplier("stamina_regen", 10);
    /** Walking, sprinting, sneaking, swimming, climbing, jumps, the office chair. */
    public static final RegistryObject<Attribute> MOVE_STAMINA_COST = multiplier("move_stamina_cost", 10);
    /** Every other stamina cost: melee, digging, chopping, the recoil pull, the radio crank, shots, reloads. */
    public static final RegistryObject<Attribute> WORK_STAMINA_COST = multiplier("work_stamina_cost", 10);
    public static final RegistryObject<Attribute> FALL_DAMAGE = multiplier("fall_damage", 10);
    /** Health from food (both FoodData#tick branches). */
    public static final RegistryObject<Attribute> NATURAL_REGEN = multiplier("natural_regen", 10);
    // metabolism
    public static final RegistryObject<Attribute> THIRST_RATE = multiplier("thirst_rate", 10);
    /** Every food exhaustion (Player#causeFoodExhaustion). */
    public static final RegistryObject<Attribute> HUNGER_RATE = multiplier("hunger_rate", 10);
    /** The stomach-bug chance of raw water. */
    public static final RegistryObject<Attribute> RAW_WATER_SICKNESS = multiplier("raw_water_sickness", 10);
    /** The absorbed dose the sickness stages see (the dose itself, and the meter, are unchanged). */
    public static final RegistryObject<Attribute> RADIATION_SENSITIVITY = multiplier("radiation_sensitivity", 10);
    // cold and heat
    /** °C added to comfort_low: −1 = comfortable down to 13 °C. */
    public static final RegistryObject<Attribute> COLD_THRESHOLD = offset("cold_threshold", -20, 20);
    /** °C added to comfort_high: +1 = comfortable up to 25 °C. */
    public static final RegistryObject<Attribute> HEAT_THRESHOLD = offset("heat_threshold", -20, 20);
    /** How fast the core moves away from normal (tau_worsen ÷ this); recovery is unchanged. */
    public static final RegistryObject<Attribute> BODY_TEMP_RATE = multiplier("body_temp_rate", 10);
    // noise and notice
    /** Footstep and landing noise radii (and how far those sounds are heard). */
    public static final RegistryObject<Attribute> FOOTSTEP_NOISE = multiplier("footstep_noise", 10);
    /** How fast an infected that sees the player confirms them. */
    public static final RegistryObject<Attribute> VISIBILITY = multiplier("visibility", 10);
    /** Gunshot exposure toward tinnitus. */
    public static final RegistryObject<Attribute> TINNITUS = multiplier("tinnitus", 10);
    /** World sound volume for this listener (client). */
    public static final RegistryObject<Attribute> HEARING = multiplier("hearing", 4);
    // hands
    /** Container search speed (higher = faster). */
    public static final RegistryObject<Attribute> SEARCH_SPEED = multiplier("search_speed", 10);
    /** Width of every QTE zone. */
    public static final RegistryObject<Attribute> QTE_WINDOW = multiplier("qte_window", 3);
    /** Width of machine QTE zones (recoil pull), on top of qte_window. */
    public static final RegistryObject<Attribute> MECH_QTE_WINDOW = multiplier("mech_qte_window", 3);
    /** First-person weapon sway, the tired and cold extra included. */
    public static final RegistryObject<Attribute> WEAPON_SWAY = multiplier("weapon_sway", 10);
    // growth (registered for P3; nothing reads them yet)
    public static final RegistryObject<Attribute> GROWTH_XP = multiplier("growth_xp", 10);
    public static final RegistryObject<Attribute> BODY_GROWTH_XP = multiplier("body_growth_xp", 10);

    private AflAttributes() {}

    public static List<RegistryObject<Attribute>> all() { return List.copyOf(ALL); }

    private static RegistryObject<Attribute> offset(String name, double min, double max) { return attribute(name, 0, min, max); }
    private static RegistryObject<Attribute> multiplier(String name, double max) { return attribute(name, 1, 0, max); }
    private static RegistryObject<Attribute> attribute(String name, double base, double min, double max) {
        var object = ATTRIBUTES.register(name, () -> new RangedAttribute(
                "attribute.name." + ApocalypseFirstLight.MOD_ID + "." + name, base, min, max).setSyncable(true));
        ALL.add(object);
        return object;
    }

    @SubscribeEvent public static void addToPlayer(EntityAttributeModificationEvent event) {
        for (var attribute : ALL) event.add(EntityType.PLAYER, attribute.get());
    }
}
