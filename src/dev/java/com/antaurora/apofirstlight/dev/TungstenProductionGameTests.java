package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.ChemicalReactorBlockEntity;
import com.antaurora.apofirstlight.blockentity.AlloyFurnaceBlockEntity;
import com.antaurora.apofirstlight.blockentity.CompressorBlockEntity;
import com.antaurora.apofirstlight.blockentity.CrusherBlockEntity;
import com.antaurora.apofirstlight.blockentity.IndustrialFurnaceBlockEntity;
import com.antaurora.apofirstlight.recipe.AlloyingRecipe;
import com.antaurora.apofirstlight.recipe.ChemicalReactingRecipe;
import com.antaurora.apofirstlight.recipe.CompressingRecipe;
import com.antaurora.apofirstlight.recipe.CrushingRecipe;
import com.antaurora.apofirstlight.recipe.IndustrialSmeltingRecipe;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflFluids;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.registry.AflRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

/** DEV-only tungsten-chain regression; the release jar excludes this package. */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TungstenProductionGameTests {
    @GameTest(template = "waste_empty")
    public static void recipesLoadWithExactTimesAndNoVanillaBypass(GameTestHelper h) {
        CrushingRecipe crushing = recipe(h, "wolframite_ore_crushing", CrushingRecipe.class);
        h.assertTrue(crushing.processingTime() == 200, "Wolframite crushing must take 200 ticks");
        h.assertTrue(crushing.results().size() == 1
                        && crushing.results().get(0).minCount() == 1
                        && crushing.results().get(0).maxCount() == 3,
                "Wolframite crushing must use the closed 1..3 range");
        Set<Integer> rolledCounts = new HashSet<>();
        RandomSource random = RandomSource.create(0xAFL);
        for (int index = 0; index < 256; index++) {
            rolledCounts.add(crushing.rollResults(random).get(0).getCount());
        }
        h.assertTrue(rolledCounts.equals(Set.of(1, 2, 3)), "Crusher roll escaped or missed the 1..3 range");

        ChemicalReactingRecipe chemical = recipe(h, "wolframite_chemical_processing",
                ChemicalReactingRecipe.class);
        h.assertTrue(chemical.processingTime() == 300
                        && chemical.fluidInput().getFluid() == Fluids.WATER
                        && chemical.fluidInput().getAmount() == 1_000
                        && chemical.wasteOutput().getFluid() == AflFluids.INDUSTRIAL_WASTE.get()
                        && chemical.wasteOutput().getAmount() == 1_000,
                "Chemical recipe fluid amounts or processing time changed");

        IndustrialSmeltingRecipe reduction = recipe(h, "tungsten_oxide_reduction",
                IndustrialSmeltingRecipe.class);
        IndustrialSmeltingRecipe sintering = recipe(h, "tungsten_powder_sintering",
                IndustrialSmeltingRecipe.class);
        h.assertTrue(reduction.processingTime() == 300 && reduction.result().is(AflItems.TUNGSTEN_POWDER.get()),
                "Tungsten oxide reduction recipe changed");
        h.assertTrue(sintering.processingTime() == 400 && sintering.result().is(AflItems.TUNGSTEN_INGOT.get()),
                "Tungsten powder sintering recipe changed");

        CompressingRecipe sheet = recipe(h, "tungsten_sheet_compressing", CompressingRecipe.class);
        h.assertTrue(sheet.processingTime() == 300 && sheet.result().is(AflItems.TUNGSTEN_SHEET.get()),
                "Tungsten sheet recipe must take 300 ticks");
        AlloyingRecipe carbide = recipe(h, "tungsten_carbide_powder_alloying", AlloyingRecipe.class);
        AlloyingRecipe cemented = recipe(h, "cemented_carbide_alloying", AlloyingRecipe.class);
        h.assertTrue(carbide.processingTime() == 600
                        && carbide.result().is(AflItems.TUNGSTEN_CARBIDE_POWDER.get()),
                "Tungsten carbide powder recipe changed");
        h.assertTrue(cemented.processingTime() == 800
                        && cemented.result().is(AflItems.CEMENTED_CARBIDE_INGOT.get()),
                "Cemented carbide recipe changed");
        SimpleContainer coalInput = new SimpleContainer(
                new ItemStack(AflItems.TUNGSTEN_POWDER.get()), new ItemStack(Items.COAL));
        SimpleContainer charcoalInput = new SimpleContainer(
                new ItemStack(AflItems.TUNGSTEN_POWDER.get()), new ItemStack(Items.CHARCOAL));
        h.assertTrue(carbide.matches(coalInput, h.getLevel())
                        && carbide.matches(charcoalInput, h.getLevel()),
                "Tungsten carbide recipe must accept both coal and charcoal");

        assertNoVanillaCookingRecipe(h, new ItemStack(AflItems.TUNGSTEN_OXIDE.get()));
        assertNoVanillaCookingRecipe(h, new ItemStack(AflItems.TUNGSTEN_POWDER.get()));
        h.succeed();
    }

    @GameTest(template = "waste_empty")
    public static void chemicalProcessingIsAtomicAndPausesWhenBlocked(GameTestHelper h) {
        ChemicalReactorBlockEntity reactor = reactor(h, new BlockPos(2, 2, 2), 9_600, 1_000);
        reactor.setItem(ChemicalReactorBlockEntity.INPUT_SLOT, new ItemStack(AflItems.WOLFRAMITE.get()));
        for (int tick = 0; tick < 299; tick++) {
            tickChemical(h, reactor);
        }
        h.assertTrue(reactor.getItem(ChemicalReactorBlockEntity.OUTPUT_SLOT).isEmpty(),
                "Chemical recipe completed before 300 ticks");
        h.assertTrue(reactor.getStoredEnergy() == 32 && reactor.getInputFluid().getAmount() == 1_000
                        && reactor.getWasteFluid().isEmpty(),
                "Chemical recipe consumed inputs before atomic completion");
        tickChemical(h, reactor);
        h.assertTrue(reactor.getStoredEnergy() == 0
                        && reactor.getItem(ChemicalReactorBlockEntity.INPUT_SLOT).isEmpty()
                        && reactor.getItem(ChemicalReactorBlockEntity.OUTPUT_SLOT)
                        .is(AflItems.TUNGSTEN_OXIDE.get())
                        && reactor.getInputFluid().isEmpty()
                        && reactor.getWasteFluid().getFluid() == AflFluids.INDUSTRIAL_WASTE.get()
                        && reactor.getWasteFluid().getAmount() == 1_000,
                "Chemical recipe did not complete as one atomic item/fluid transaction");

        ChemicalReactorBlockEntity blocked = reactor(h, new BlockPos(5, 2, 2), 9_600, 1_000);
        blocked.setItem(ChemicalReactorBlockEntity.INPUT_SLOT, new ItemStack(AflItems.WOLFRAMITE.get()));
        for (int tick = 0; tick < 10; tick++) {
            tickChemical(h, blocked);
        }
        blocked.setItem(ChemicalReactorBlockEntity.OUTPUT_SLOT, new ItemStack(Items.COBBLESTONE));
        int energyBeforePause = blocked.getStoredEnergy();
        for (int tick = 0; tick < 20; tick++) {
            tickChemical(h, blocked);
        }
        h.assertTrue(blocked.getStoredEnergy() == energyBeforePause
                        && blocked.getInputFluid().getAmount() == 1_000
                        && blocked.getWasteFluid().isEmpty(),
                "Blocked Chemical Reactor consumed FE or fluid instead of pausing");
        blocked.setItem(ChemicalReactorBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
        for (int tick = 0; tick < 290; tick++) {
            tickChemical(h, blocked);
        }
        h.assertTrue(blocked.getItem(ChemicalReactorBlockEntity.OUTPUT_SLOT)
                        .is(AflItems.TUNGSTEN_OXIDE.get()),
                "Chemical Reactor did not resume the preserved 10-tick progress");

        ChemicalReactorBlockEntity wasteBlocked = reactor(h, new BlockPos(8, 2, 2), 9_600, 1_000);
        wasteBlocked.setItem(ChemicalReactorBlockEntity.INPUT_SLOT,
                new ItemStack(AflItems.WOLFRAMITE.get()));
        for (int tick = 0; tick < 10; tick++) {
            tickChemical(h, wasteBlocked);
        }
        wasteBlocked.restoreWasteFluid(new FluidStack(AflFluids.INDUSTRIAL_WASTE.get(), 7_501));
        int energyBeforeWastePause = wasteBlocked.getStoredEnergy();
        for (int tick = 0; tick < 20; tick++) {
            tickChemical(h, wasteBlocked);
        }
        h.assertTrue(wasteBlocked.getStoredEnergy() == energyBeforeWastePause
                        && wasteBlocked.getItem(ChemicalReactorBlockEntity.INPUT_SLOT)
                        .is(AflItems.WOLFRAMITE.get())
                        && wasteBlocked.getInputFluid().getAmount() == 1_000,
                "Full waste tank consumed FE, item input, or water");
        CompoundTag resumedState = wasteBlocked.saveWithFullMetadata();
        resumedState.put("WasteTank", new FluidTank(ChemicalReactorBlockEntity.TANK_CAPACITY_MB)
                .writeToNBT(new CompoundTag()));
        wasteBlocked.load(resumedState);
        for (int tick = 0; tick < 290; tick++) {
            tickChemical(h, wasteBlocked);
        }
        h.assertTrue(wasteBlocked.getItem(ChemicalReactorBlockEntity.OUTPUT_SLOT)
                        .is(AflItems.TUNGSTEN_OXIDE.get())
                        && wasteBlocked.getWasteFluid().getAmount() == 1_000,
                "Chemical Reactor did not resume preserved progress after waste space was cleared");
        h.succeed();
    }

    @GameTest(template = "waste_empty")
    public static void industrialSmeltingUsesJsonTicksAndExactEnergy(GameTestHelper h) {
        IndustrialFurnaceBlockEntity reduction = furnace(h, new BlockPos(2, 2, 2), 7_200);
        reduction.setItem(IndustrialFurnaceBlockEntity.inputSlot(0),
                new ItemStack(AflItems.TUNGSTEN_OXIDE.get()));
        for (int tick = 0; tick < 299; tick++) {
            tickIndustrial(h, reduction);
        }
        h.assertTrue(reduction.getItem(IndustrialFurnaceBlockEntity.outputSlot(0)).isEmpty()
                        && reduction.getStoredEnergy() == 24,
                "Industrial reduction was multiplied by 0.5 or used the wrong FE/t");
        tickIndustrial(h, reduction);
        h.assertTrue(reduction.getStoredEnergy() == 0
                        && reduction.getItem(IndustrialFurnaceBlockEntity.outputSlot(0))
                        .is(AflItems.TUNGSTEN_POWDER.get()),
                "Industrial reduction did not complete at 300 ticks / 7,200 FE");

        IndustrialFurnaceBlockEntity sintering = furnace(h, new BlockPos(5, 2, 2), 9_600);
        sintering.setItem(IndustrialFurnaceBlockEntity.inputSlot(0),
                new ItemStack(AflItems.TUNGSTEN_POWDER.get()));
        for (int tick = 0; tick < 400; tick++) {
            tickIndustrial(h, sintering);
        }
        h.assertTrue(sintering.getStoredEnergy() == 0
                        && sintering.getItem(IndustrialFurnaceBlockEntity.outputSlot(0))
                        .is(AflItems.TUNGSTEN_INGOT.get()),
                "Industrial sintering did not complete at 400 ticks / 9,600 FE");
        h.succeed();
    }

    @GameTest(template = "waste_empty")
    public static void crusherCompressorAndAlloyCompleteTungstenRecipes(GameTestHelper h) {
        BlockPos crusherPosition = new BlockPos(2, 2, 2);
        for (int run = 0; run < 10; run++) {
            h.setBlock(crusherPosition, Blocks.AIR);
            CrusherBlockEntity crusher = crusher(h, crusherPosition, 3_200);
            crusher.setItem(CrusherBlockEntity.INPUT_SLOT,
                    new ItemStack(AflItems.WOLFRAMITE_ORE.get()));
            for (int tick = 0; tick < 200; tick++) {
                CrusherBlockEntity.serverTick(h.getLevel(), crusher.getBlockPos(),
                        crusher.getBlockState(), crusher);
            }
            ItemStack crushed = crusher.getItem(CrusherBlockEntity.FIRST_OUTPUT_SLOT);
            h.assertTrue(crusher.getStoredEnergy() == 0 && crushed.is(AflItems.WOLFRAMITE.get())
                            && crushed.getCount() >= 1 && crushed.getCount() <= 3,
                    "Crusher run " + run + " did not produce 1..3 Wolframite for exactly 3,200 FE");
        }

        CompressorBlockEntity compressor = compressor(h, new BlockPos(5, 2, 2), 4_800);
        compressor.setItem(CompressorBlockEntity.INPUT_SLOT, new ItemStack(AflItems.TUNGSTEN_INGOT.get()));
        for (int tick = 0; tick < 300; tick++) {
            CompressorBlockEntity.serverTick(h.getLevel(), compressor.getBlockPos(),
                    compressor.getBlockState(), compressor);
        }
        h.assertTrue(compressor.getStoredEnergy() == 0
                        && compressor.getOutputStack().is(AflItems.TUNGSTEN_SHEET.get()),
                "Compressor did not produce Tungsten Sheet in 300 ticks / 4,800 FE");

        AlloyFurnaceBlockEntity carbide = alloyFurnace(h, new BlockPos(8, 2, 2), 14_400);
        carbide.setItem(AlloyFurnaceBlockEntity.INPUT_A_SLOT,
                new ItemStack(AflItems.TUNGSTEN_POWDER.get()));
        carbide.setItem(AlloyFurnaceBlockEntity.INPUT_B_SLOT, new ItemStack(Items.CHARCOAL));
        for (int tick = 0; tick < 600; tick++) {
            AlloyFurnaceBlockEntity.serverTick(h.getLevel(), carbide.getBlockPos(),
                    carbide.getBlockState(), carbide);
        }
        h.assertTrue(carbide.getStoredEnergy() == 0
                        && carbide.getOutputStack().is(AflItems.TUNGSTEN_CARBIDE_POWDER.get()),
                "Alloy Furnace did not produce carbide powder in 600 ticks / 14,400 FE");

        AlloyFurnaceBlockEntity cemented = alloyFurnace(h, new BlockPos(11, 2, 2), 19_200);
        cemented.setItem(AlloyFurnaceBlockEntity.INPUT_A_SLOT,
                new ItemStack(AflItems.TUNGSTEN_CARBIDE_POWDER.get()));
        cemented.setItem(AlloyFurnaceBlockEntity.INPUT_B_SLOT,
                new ItemStack(AflItems.NICKEL_INGOT.get()));
        for (int tick = 0; tick < 800; tick++) {
            AlloyFurnaceBlockEntity.serverTick(h.getLevel(), cemented.getBlockPos(),
                    cemented.getBlockState(), cemented);
        }
        h.assertTrue(cemented.getStoredEnergy() == 0
                        && cemented.getOutputStack().is(AflItems.CEMENTED_CARBIDE_INGOT.get()),
                "Alloy Furnace did not produce cemented carbide in 800 ticks / 19,200 FE");
        h.succeed();
    }

    private static <T extends Recipe<?>> T recipe(GameTestHelper h, String path, Class<T> type) {
        Recipe<?> recipe = h.getLevel().getRecipeManager()
                .byKey(new ResourceLocation(ApocalypseFirstLight.MOD_ID, path)).orElse(null);
        h.assertTrue(type.isInstance(recipe), "Missing or wrong recipe type for " + path);
        return type.cast(recipe);
    }

    private static void assertNoVanillaCookingRecipe(GameTestHelper h, ItemStack inputStack) {
        SimpleContainer input = new SimpleContainer(inputStack);
        h.assertTrue(h.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, h.getLevel()).isEmpty(),
                inputStack.getItem() + " has an unintended vanilla smelting bypass");
        h.assertTrue(h.getLevel().getRecipeManager().getRecipeFor(RecipeType.BLASTING, input, h.getLevel()).isEmpty(),
                inputStack.getItem() + " has an unintended vanilla blasting bypass");
    }

    private static ChemicalReactorBlockEntity reactor(GameTestHelper h, BlockPos position,
                                                       int energy, int waterAmount) {
        h.setBlock(position, AflBlocks.CHEMICAL_REACTOR.get());
        if (!(h.getBlockEntity(position) instanceof ChemicalReactorBlockEntity reactor)) {
            throw new IllegalStateException("Chemical Reactor block entity missing at " + position);
        }
        FluidTank inputTank = new FluidTank(ChemicalReactorBlockEntity.TANK_CAPACITY_MB);
        inputTank.setFluid(new FluidStack(Fluids.WATER, waterAmount));
        CompoundTag state = new CompoundTag();
        state.putInt("EnergyStored", energy);
        state.put("InputTank", inputTank.writeToNBT(new CompoundTag()));
        reactor.load(state);
        return reactor;
    }

    private static IndustrialFurnaceBlockEntity furnace(GameTestHelper h, BlockPos position, int energy) {
        h.setBlock(position, AflBlocks.INDUSTRIAL_FURNACE.get());
        if (!(h.getBlockEntity(position) instanceof IndustrialFurnaceBlockEntity furnace)) {
            throw new IllegalStateException("Industrial Furnace block entity missing at " + position);
        }
        CompoundTag state = new CompoundTag();
        state.putInt("EnergyStored", energy);
        furnace.load(state);
        return furnace;
    }

    private static CrusherBlockEntity crusher(GameTestHelper h, BlockPos position, int energy) {
        h.setBlock(position, AflBlocks.CRUSHER.get());
        CrusherBlockEntity crusher = (CrusherBlockEntity) h.getBlockEntity(position);
        CompoundTag state = new CompoundTag();
        state.putInt("EnergyStored", energy);
        crusher.load(state);
        return crusher;
    }

    private static CompressorBlockEntity compressor(GameTestHelper h, BlockPos position, int energy) {
        h.setBlock(position, AflBlocks.COMPRESSOR.get());
        CompressorBlockEntity compressor = (CompressorBlockEntity) h.getBlockEntity(position);
        CompoundTag state = new CompoundTag();
        state.putInt("EnergyStored", energy);
        compressor.load(state);
        return compressor;
    }

    private static AlloyFurnaceBlockEntity alloyFurnace(GameTestHelper h, BlockPos position, int energy) {
        h.setBlock(position, AflBlocks.ALLOY_FURNACE.get());
        AlloyFurnaceBlockEntity furnace = (AlloyFurnaceBlockEntity) h.getBlockEntity(position);
        CompoundTag state = new CompoundTag();
        state.putInt("EnergyStored", energy);
        furnace.load(state);
        return furnace;
    }

    private static void tickChemical(GameTestHelper h, ChemicalReactorBlockEntity reactor) {
        ChemicalReactorBlockEntity.serverTick(h.getLevel(), reactor.getBlockPos(),
                reactor.getBlockState(), reactor);
    }

    private static void tickIndustrial(GameTestHelper h, IndustrialFurnaceBlockEntity furnace) {
        IndustrialFurnaceBlockEntity.serverTick(h.getLevel(), furnace.getBlockPos(),
                furnace.getBlockState(), furnace);
    }
}
