package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.entity.OfficeChairEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ApocalypseFirstLight.MOD_ID);

    /** An office chair someone sat on: rideable, rolls with W / S / A / D, stays where it was left (0.85 x 1.15 blocks). */
    public static final RegistryObject<EntityType<OfficeChairEntity>> MODERN_OFFICE_CHAIR = ENTITY_TYPES.register("modern_office_chair",
            () -> EntityType.Builder.<OfficeChairEntity>of(OfficeChairEntity::new, MobCategory.MISC)
                    .sized(0.85F, 1.15F).clientTrackingRange(10).build("modern_office_chair"));

    /**
     * A dead survivor's body (docs/gameplay/survivor_death_succession_v0.md 6): never saved, never summoned (the corpse
     * record in the survivor data puts it back), no physics, 1.2 x 0.4 blocks for the click.
     */
    public static final RegistryObject<EntityType<com.antaurora.apofirstlight.survivor.SurvivorCorpse>> SURVIVOR_CORPSE =
            ENTITY_TYPES.register("survivor_corpse", () -> EntityType.Builder.<com.antaurora.apofirstlight.survivor.SurvivorCorpse>of(
                            com.antaurora.apofirstlight.survivor.SurvivorCorpse::new, MobCategory.MISC)
                    .sized(1.2F, 0.4F).noSave().noSummon().fireImmune().clientTrackingRange(8).updateInterval(100)
                    .build("survivor_corpse"));

    /**
     * The Halstead Tamarack T15 pickup (docs/vehicles/first_pickup_v0_design.md, body V0): the cab's picking box; the front
     * and the bed are its two part entities (vehicle/PickupTruckEntity).
     */
    public static final RegistryObject<EntityType<com.antaurora.apofirstlight.vehicle.PickupTruckEntity>> PICKUP_T15 =
            ENTITY_TYPES.register("pickup_t15", () -> EntityType.Builder.<com.antaurora.apofirstlight.vehicle.PickupTruckEntity>of(
                            com.antaurora.apofirstlight.vehicle.PickupTruckEntity::new, MobCategory.MISC)
                    .sized(1.9F, 1.9F).clientTrackingRange(10).build("pickup_t15"));

    /**
     * Sitting on the ground (docs/gameplay/splint_animation_v2_plan.md 3): the invisible seat under a player who sat down
     * with the sit key; never saved, never summoned.
     */
    public static final RegistryObject<EntityType<com.antaurora.apofirstlight.sit.GroundSeat>> GROUND_SEAT =
            ENTITY_TYPES.register("ground_seat", () -> EntityType.Builder.<com.antaurora.apofirstlight.sit.GroundSeat>of(
                            com.antaurora.apofirstlight.sit.GroundSeat::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).noSave().noSummon().clientTrackingRange(10).build("ground_seat"));

    /**
     * Infected Z1a (docs/gameplay/afl_infected_z0_design.md 12): the common infected, the r6 shambler, the vanilla
     * player's box (0.6 x 1.95); never spawned by the world yet ({@code /afl dev infected spawn}).
     */
    public static final RegistryObject<EntityType<com.antaurora.apofirstlight.creature.infected.InfectedEntity>> INFECTED_SHAMBLER =
            ENTITY_TYPES.register("infected_shambler", () -> EntityType.Builder.<com.antaurora.apofirstlight.creature.infected.InfectedEntity>of(
                            com.antaurora.apofirstlight.creature.infected.InfectedEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).build("infected_shambler"));

    /** An infected's body: saved with its chunk, no physics, 1.2 x 0.4 blocks for the click (as a survivor's). */
    public static final RegistryObject<EntityType<com.antaurora.apofirstlight.creature.infected.InfectedCorpse>> INFECTED_CORPSE =
            ENTITY_TYPES.register("infected_corpse", () -> EntityType.Builder.<com.antaurora.apofirstlight.creature.infected.InfectedCorpse>of(
                            com.antaurora.apofirstlight.creature.infected.InfectedCorpse::new, MobCategory.MISC)
                    .sized(1.2F, 0.4F).noSummon().fireImmune().clientTrackingRange(8).updateInterval(100)
                    .build("infected_corpse"));

    /**
     * White-tailed deer D1 (docs/gameplay/afl_whitetail_deer_d0_design.md 11): the buck's box 0.9 x 1.4 (the doe's 0.92 of
     * it, DeerEntity#getDimensions); never spawned by the world yet ({@code /afl dev deer spawn}).
     */
    public static final RegistryObject<EntityType<com.antaurora.apofirstlight.creature.deer.DeerEntity>> WHITETAIL_DEER =
            ENTITY_TYPES.register("whitetail_deer", () -> EntityType.Builder.<com.antaurora.apofirstlight.creature.deer.DeerEntity>of(
                            com.antaurora.apofirstlight.creature.deer.DeerEntity::new, MobCategory.CREATURE)
                    .sized(0.9F, 1.4F).clientTrackingRange(10).build("whitetail_deer"));

    /** A deer's carcass: saved with its chunk, no physics, not clicked yet (D3 butchers it). */
    public static final RegistryObject<EntityType<com.antaurora.apofirstlight.creature.deer.DeerCorpse>> DEER_CORPSE =
            ENTITY_TYPES.register("deer_corpse", () -> EntityType.Builder.<com.antaurora.apofirstlight.creature.deer.DeerCorpse>of(
                            com.antaurora.apofirstlight.creature.deer.DeerCorpse::new, MobCategory.MISC)
                    .sized(1.4F, 0.5F).noSummon().fireImmune().clientTrackingRange(10).updateInterval(100)
                    .build("deer_corpse"));

    private AflEntities() {
    }
}
