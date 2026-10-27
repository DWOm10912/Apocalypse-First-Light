package com.antaurora.apofirstlight.world;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.brewing.PotionBrewEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Vanilla Survival Rework V1 (docs/gameplay/vanilla_survival_rework_v1.md): retired gear stops working, server side and
 * in every game mode, while the stacks and their NBT stay as they are.
 * <ul>
 *   <li>Attributes: every modifier of a retired stack is dropped, its item defaults and NBT "AttributeModifiers" alike
 *   (both go through ItemAttributeModifierEvent), so swords hit like a bare hand and armour gives no armour, toughness or
 *   knockback resistance, on players and mobs.</li>
 *   <li>Enchantments, sweep, turtle shell, leather freeze and powder snow: the Retired*Mixin classes.</li>
 *   <li>Shield: cannot be raised, and any block still in progress is cancelled.</li>
 *   <li>Swords mine like a bare hand and harvest nothing a hand cannot (cobwebs).</li>
 *   <li>V1.8 (user 2026-10-23): only a tool digs ({@link #DIGGING_TOOLS}); with an empty hand or anything else, in survival
 *   and adventure, no block breaks: no crack, no progress, no stamina, not even those that break at once (grass, flowers).
 *   The client does not even start (client/NoToolDigClient: no sound, particles or swing); these are the server's refusal.
 *   A tool digs as before (its own speed and drops). Creative as before.</li>
 *   <li>Trades whose result is unobtainable are skipped when a trader rolls its offers.</li>
 *   <li>Brewing: no brew finishes (the stand itself is a refused workstation; this covers hopper-fed stands).</li>
 *   <li>V1.2 mobs (VanillaRetirement.MOBS, every vanilla mob except minecraft:zombie): no new one enters a level, whatever
 *   brought it (natural spawning, chunk generation, spawners, eggs, /summon, breeding, raids, golems built by players),
 *   and no entity converts into one (a drowning zombie stays a zombie). Mobs already saved in a chunk load as before.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VanillaRetirementEvents {
    private VanillaRetirementEvents() {
    }

    /** Last, so a mod adding a modifier to a retired stack does not bring it back. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttributes(ItemAttributeModifierEvent event) {
        if (VanillaRetirement.isRetiredGear(event.getItemStack())) event.clearModifiers();
    }

    @SubscribeEvent
    public static void onStartUsing(LivingEntityUseItemEvent.Start event) {
        if (VanillaRetirement.isRetiredShield(event.getItem())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (VanillaRetirement.isRetiredShield(event.getEntity().getUseItem())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        var stack = event.getEntity().getMainHandItem();
        if (!VanillaRetirement.isRetiredSword(stack)) return;
        float tool = stack.getDestroySpeed(event.getState());
        if (tool > 1.0F) event.setNewSpeed(event.getNewSpeed() / tool);
    }

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (event.getTargetBlock().requiresCorrectToolForDrops()
                && VanillaRetirement.isRetiredSword(event.getEntity().getMainHandItem())) event.setCanHarvest(false);
    }

    /** The items that dig at all (V1.8): data/apocalypse_firstlight/tags/items/tools/digging.json. */
    public static final TagKey<Item> DIGGING_TOOLS =
            TagKey.create(Registries.ITEM, new ResourceLocation(ApocalypseFirstLight.MOD_ID, "tools/digging"));

    /** Whether this player's main hand holds no tool, so it digs nothing (V1.8). */
    public static boolean cannotDig(Player player) {
        return VanillaRetirement.enabled() && !player.isCreative() && !player.isSpectator() && !player.getMainHandItem().is(DIGGING_TOOLS);
    }

    /** First, so nothing below counts a dig that does not happen (stamina); cancelled, the speed is -1 on both sides. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onNoToolSpeed(PlayerEvent.BreakSpeed event) {
        if (cannotDig(event.getEntity())) event.setCanceled(true);
    }

    /** The server's last word, for any path that breaks a block for a player without the progress above. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onNoToolBreak(BlockEvent.BreakEvent event) {
        if (cannotDig(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        for (List<VillagerTrades.ItemListing> level : event.getTrades().values()) level.replaceAll(VanillaRetirementEvents::filtered);
    }

    @SubscribeEvent
    public static void onWandererTrades(WandererTradesEvent event) {
        event.getGenericTrades().replaceAll(VanillaRetirementEvents::filtered);
        event.getRareTrades().replaceAll(VanillaRetirementEvents::filtered);
    }

    /** Cheap refusal before a natural or spawner spawn builds the mob; onJoin below is the rule that covers every path. */
    @SubscribeEvent
    public static void onSpawnPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (VanillaRetirement.isRetiredMob(event.getEntityType())) event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        var type = event.getEntity().getType();
        if (event.getLevel().isClientSide() || !VanillaRetirement.isRetiredMob(type)
                || VanillaRetirement.isTestTarget(type, event.getLevel())) return;
        // Saved passive mobs (players' animals) still load; saved hostile ones (the zombies of old worlds) are dropped.
        if (!event.loadedFromDisk() || type.getCategory() == MobCategory.MONSTER) event.setCanceled(true);
    }

    /** The source keeps its form; the timer only spaces out the retries (zombies retry their drowning conversion). */
    @SubscribeEvent
    public static void onConvert(LivingConversionEvent.Pre event) {
        if (!VanillaRetirement.isRetiredMob(event.getOutcome())) return;
        event.setCanceled(true);
        event.setConversionTimer(600);
    }

    @SubscribeEvent
    public static void onBrew(PotionBrewEvent.Pre event) {
        if (VanillaRetirement.enabled()) event.setCanceled(true);
    }

    /** The listing as it was, except that an offer selling something unobtainable is not made (null is skipped). */
    private static VillagerTrades.ItemListing filtered(VillagerTrades.ItemListing listing) {
        return (trader, random) -> {
            MerchantOffer offer = listing.getOffer(trader, random);
            return offer != null && VanillaRetirement.isUnobtainable(offer.getResult()) ? null : offer;
        };
    }
}
