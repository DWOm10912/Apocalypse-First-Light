package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.IndustrialFurnaceBlockEntity;
import com.antaurora.apofirstlight.contamination.ItemContamination;
import com.antaurora.apofirstlight.fluid.RemoveItemsLootModifier;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.temperature.ThermalInsulation;
import com.antaurora.apofirstlight.world.VanillaRetirement;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.brewing.PotionBrewEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Vanilla Survival Rework V1 (docs/gameplay/vanilla_survival_rework_v1.md): the retirement rules on a running server,
 * then the same checks with the switch forced off (VanillaRetirement.setTestOverrideOff) to prove the vanilla rules come
 * back. Run alone: gradlew runGameTestServer -PaflWithoutShaders -I src/dev/vanilla-retirement-gametest.init.gradle.
 * Not covered here (in-game checks): spawn gear on naturally spawned mobs, the turtle shell's breathing, shield blocks
 * against a real attack, the creative tabs and the tooltip (client).
 */
@GameTestHolder("afl_vanilla_retirement_tests")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class VanillaRetirementGameTests {
    private static final String NAMESPACE = "afl_vanilla_retirement_tests";
    private static final List<String> RETIRED_RECIPES = List.of("anvil", "enchanting_table", "brewing_stand", "grindstone",
            "smithing_table", "ender_chest", "shield_decoration", "armor_dye", "shield", "iron_sword", "diamond_sword",
            "wooden_sword", "leather_chestplate", "iron_helmet", "diamond_boots", "golden_leggings", "netherite_sword_smithing",
            "netherite_chestplate_smithing", "turtle_helmet");
    private static final List<String> KEPT_RECIPES = List.of("minecraft:crafting_table", "minecraft:oak_planks", "minecraft:stick",
            "minecraft:wooden_pickaxe", "minecraft:stone_axe", "minecraft:iron_pickaxe", "minecraft:iron_shovel", "minecraft:iron_hoe",
            "minecraft:furnace", "minecraft:iron_ingot_from_smelting_raw_iron", "minecraft:netherite_pickaxe_smithing",
            "apocalypse_firstlight:steel_billet_alloying");

    private VanillaRetirementGameTests() {
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof GameTestServer)) return;
        var level = event.getServer().overworld();
        var tag = net.minecraft.nbt.TagParser.parseTag("{size:[4,3,4],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        level.getStructureManager().getOrCreate(new ResourceLocation(NAMESPACE, "empty"))
                .load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag);
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("vanilla_retirement", NAMESPACE + ":rules",
                NAMESPACE + ":empty", 100, 0L, true, VanillaRetirementGameTests::run));
    }

    private static void run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try {
            VanillaRetirement.setTestOverrideOff(false);
            check(VanillaRetirement.enabled(), "switch is off in the test server");
            recipes(level);
            ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5b3e3f43-0e4a-4b77-9d2c-6a0b1f3c7e01"), "afl_retirement"));
            player.setGameMode(GameType.SURVIVAL);
            gear(level, player, true);
            workstations(helper, player);
            loot(level, true);
            trades(helper, true);
            check(MinecraftForge.EVENT_BUS.post(new PotionBrewEvent.Pre(net.minecraft.core.NonNullList.withSize(5, ItemStack.EMPTY))), "brewing not cancelled");
            check(VanillaRetirement.isClosed(Level.NETHER) && VanillaRetirement.isClosed(Level.END) && !VanillaRetirement.isClosed(Level.OVERWORLD),
                    "closed dimensions wrong");
            industrialFurnace(level);

            VanillaRetirement.setTestOverrideOff(true);
            gear(level, player, false);
            loot(level, false);
            trades(helper, false);
            check(!MinecraftForge.EVENT_BUS.post(new PotionBrewEvent.Pre(net.minecraft.core.NonNullList.withSize(5, ItemStack.EMPTY))), "brewing cancelled with the switch off");
            check(!VanillaRetirement.isClosed(Level.NETHER), "Nether closed with the switch off");
            VanillaRetirement.setTestOverrideOff(false);

            ApocalypseFirstLight.LOGGER.info("[AFL VANILLA RETIREMENT TEST] PASS recipes (retired gone, wood/tools/furnace/iron/steel kept), "
                    + "attributes incl. NBT, enchantments (damage, protection, level) with NBT untouched, no sweep, shield not raised, "
                    + "sword mines like a hand, leather freeze and powder snow, warmth 0, survival workstations refused and creative allowed, "
                    + "loot filter, trades filter, brewing, closed dimensions, industrial furnace keeps contamination, switch off restores");
            helper.succeed();
        } catch (Throwable t) {
            VanillaRetirement.setTestOverrideOff(false);
            ApocalypseFirstLight.LOGGER.error("[AFL VANILLA RETIREMENT TEST] FAIL {}", t.toString());
            helper.fail(t.toString());
        }
    }

    private static void recipes(ServerLevel level) {
        var manager = level.getRecipeManager();
        for (String id : RETIRED_RECIPES) check(manager.byKey(new ResourceLocation("minecraft", id)).isEmpty(), "recipe still there: " + id);
        for (String id : KEPT_RECIPES) check(manager.byKey(new ResourceLocation(id)).isPresent(), "recipe missing: " + id);
        for (Recipe<?> recipe : manager.getRecipes()) {
            check(!"minecraft:smithing_trim".equals(String.valueOf(ForgeRegistries.RECIPE_SERIALIZERS.getKey(recipe.getSerializer()))),
                    "trim recipe left: " + recipe.getId());
            ItemStack out = recipe.getResultItem(level.registryAccess());
            check(out.isEmpty() || !out.is(VanillaRetirement.UNOBTAINABLE), "recipe makes retired " + out + ": " + recipe.getId());
        }
    }

    /** The same stacks with the switch on (retired: nothing works) and off (vanilla). */
    private static void gear(ServerLevel level, ServerPlayer player, boolean retired) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(Enchantments.SHARPNESS, 5);
        sword.enchant(Enchantments.SWEEPING_EDGE, 3);
        String swordNbt = sword.getTag().toString();
        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        chest.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 4);
        ItemStack nbtSword = new ItemStack(Items.IRON_SWORD);
        nbtSword.addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(UUID.randomUUID(), "test", 20, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION),
                EquipmentSlot.MAINHAND);
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        axe.enchant(Enchantments.SHARPNESS, 5);
        ItemStack crowbar = new ItemStack(AflItems.CROWBAR.get());

        check(sword.getAttributeModifiers(EquipmentSlot.MAINHAND).isEmpty() == retired, "sword attributes, retired=" + retired);
        check(chest.getAttributeModifiers(EquipmentSlot.CHEST).isEmpty() == retired, "chestplate attributes, retired=" + retired);
        check(nbtSword.getAttributeModifiers(EquipmentSlot.MAINHAND).isEmpty() == retired, "NBT sword attributes, retired=" + retired);
        check(!axe.getAttributeModifiers(EquipmentSlot.MAINHAND).isEmpty(), "iron axe lost its attributes");
        check(!crowbar.getAttributeModifiers(EquipmentSlot.MAINHAND).isEmpty(), "crowbar lost its attributes");

        check((EnchantmentHelper.getDamageBonus(sword, MobType.UNDEFINED) == 0) == retired, "sharpness bonus, retired=" + retired);
        check(EnchantmentHelper.getDamageBonus(axe, MobType.UNDEFINED) > 0, "axe sharpness lost");
        check((EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, sword) == 0) == retired, "sharpness level, retired=" + retired);
        check((EnchantmentHelper.getDamageProtection(List.of(chest), level.damageSources().generic()) == 0) == retired, "protection, retired=" + retired);
        check(sword.getTag().toString().equals(swordNbt) && sword.getEnchantmentTags().size() == 2, "sword NBT changed");
        check(sword.canPerformAction(ToolActions.SWORD_SWEEP) != retired, "sweep, retired=" + retired);

        var cobweb = Blocks.COBWEB.defaultBlockState();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        float hand = player.getDigSpeed(cobweb, null);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        check((player.getDigSpeed(cobweb, null) == hand) == retired, "sword cobweb speed, retired=" + retired);
        check(player.hasCorrectToolForDrops(cobweb) != retired, "sword harvests cobweb, retired=" + retired);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        check(player.isUsingItem() != retired, "shield raised, retired=" + retired);
        player.stopUsingItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        check(player.canFreeze() == retired, "leather freeze immunity, retired=" + retired);
        check(PowderSnowBlock.canEntityWalkOnPowderSnow(player) != retired, "powder snow walking, retired=" + retired);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        check((ThermalInsulation.of(new ItemStack(Items.LEATHER_CHESTPLATE)) == 0) == retired, "leather warmth, retired=" + retired);
    }

    private static void workstations(GameTestHelper helper, ServerPlayer player) {
        BlockPos anvil = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(anvil, Blocks.ANVIL.defaultBlockState());
        var hit = new BlockHitResult(Vec3.atCenterOf(anvil), Direction.UP, anvil, false);
        check(ForgeHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, anvil, hit).isCanceled(), "survival anvil not refused");
        player.setGameMode(GameType.CREATIVE);
        check(!ForgeHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, anvil, hit).isCanceled(), "creative anvil refused");
        player.setGameMode(GameType.SURVIVAL);
        BlockPos table = anvil.east();
        helper.getLevel().setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
        check(!ForgeHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, table,
                new BlockHitResult(Vec3.atCenterOf(table), Direction.UP, table, false)).isCanceled(), "crafting table refused");
    }

    /** The shipped retired_vanilla.json, decoded as Forge does, on a sample roll. */
    private static void loot(ServerLevel level, boolean retired) throws Exception {
        var file = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "loot_modifiers/retired_vanilla.json");
        var resource = level.getServer().getResourceManager().getResource(file).orElseThrow();
        RemoveItemsLootModifier modifier;
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            modifier = RemoveItemsLootModifier.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow(false, s -> {});
        }
        boolean listed = false;
        for (var stack : level.getServer().getResourceManager().getResourceStack(new ResourceLocation("forge", "loot_modifiers/global_loot_modifiers.json"))) {
            try (var reader = new InputStreamReader(stack.open(), StandardCharsets.UTF_8)) {
                for (var e : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("entries")) listed |= "apocalypse_firstlight:retired_vanilla".equals(e.getAsString());
            }
        }
        check(listed, "retired_vanilla not in global_loot_modifiers");
        var roll = new ObjectArrayList<ItemStack>();
        for (var item : List.of(Items.GOLDEN_SWORD, Items.IRON_HELMET, Items.SHIELD, Items.ENCHANTED_BOOK, Items.EXPERIENCE_BOTTLE, Items.IRON_INGOT, Items.BREAD))
            roll.add(new ItemStack(item));
        var left = modifier.apply(roll, null);
        check(left.size() == (retired ? 2 : 7), "loot left " + left + ", retired=" + retired);
    }

    /** Every armorer, weaponsmith and librarian listing, rolled a few times: no retired result with the switch on. */
    private static void trades(GameTestHelper helper, boolean retired) {
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        RandomSource random = RandomSource.create(20261019L);
        int offers = 0, retiredOffers = 0;
        for (VillagerProfession profession : List.of(VillagerProfession.ARMORER, VillagerProfession.WEAPONSMITH, VillagerProfession.LIBRARIAN)) {
            for (VillagerTrades.ItemListing[] level : VillagerTrades.TRADES.get(profession).values()) {
                for (VillagerTrades.ItemListing listing : level) {
                    for (int i = 0; i < 8; i++) {
                        MerchantOffer offer = listing.getOffer(villager, random);
                        if (offer == null) continue;
                        offers++;
                        if (offer.getResult().is(VanillaRetirement.UNOBTAINABLE) || offer.getResult().is(Items.ENCHANTED_BOOK)) retiredOffers++;
                    }
                }
            }
        }
        villager.discard();
        check(offers > 0, "no trades at all");
        check((retiredOffers == 0) == retired, "retired trades " + retiredOffers + ", retired=" + retired);
    }

    /** Industrial furnace takes vanilla-type results through assemble: contaminated dirty water boils to contaminated water. */
    private static void industrialFurnace(ServerLevel level) throws Exception {
        var furnace = new IndustrialFurnaceBlockEntity(BlockPos.ZERO, AflBlocks.INDUSTRIAL_FURNACE.get().defaultBlockState());
        Method find = IndustrialFurnaceBlockEntity.class.getDeclaredMethod("findPreferredRecipe", Level.class, ItemStack.class);
        find.setAccessible(true);
        ItemStack dirty = new ItemStack(AflItems.DIRTY_WATER_BOTTLE.get());
        ItemContamination.setLevel(dirty, 3);
        Object lane = find.invoke(furnace, level, dirty);
        check(lane != null, "no recipe for dirty water");
        Method result = lane.getClass().getDeclaredMethod("result");
        result.setAccessible(true);
        ItemStack out = (ItemStack) result.invoke(lane);
        check(out.is(AflItems.BOILED_WATER_BOTTLE.get()) && ItemContamination.getLevel(out) == ItemContamination.getLevel(dirty),
                "contaminated dirty water gave " + BuiltInRegistries.ITEM.getKey(out.getItem()) + " " + ItemContamination.getLevel(out));
        Object clean = find.invoke(furnace, level, new ItemStack(AflItems.DIRTY_WATER_BOTTLE.get()));
        check(((ItemStack) result.invoke(clean)).is(AflItems.PURIFIED_WATER_BOTTLE.get()), "clean dirty water did not purify");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
