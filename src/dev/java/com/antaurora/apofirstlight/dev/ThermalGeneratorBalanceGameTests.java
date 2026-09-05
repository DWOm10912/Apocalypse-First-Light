package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.PowerCableBlock;
import com.antaurora.apofirstlight.blockentity.ThermalGeneratorBlockEntity;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** DEV-only exact-energy regression; the existing jar task excludes this package. */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThermalGeneratorBalanceGameTests {
    @GameTest(template = "waste_empty")
    public static void exactFuelEnergyAndFullBufferPause(GameTestHelper h) {
        MachineBalanceManager.ThermalGeneratorBalance balance = MachineBalanceManager.thermalGenerator();
        h.assertTrue(balance.capacityFe() == 100_000, "Thermal Generator capacity changed");
        h.assertTrue(balance.generationFePerTick() == 16, "Thermal Generator generation rate changed");
        h.assertTrue(balance.maxOutputFePerTick() == 16, "Thermal Generator output rate changed");
        h.assertTrue(balance.pauseBurnWhenFull(), "Full-buffer pause must remain enabled");
        h.assertTrue(balance.fuels().size() == 4, "Thermal Generator fuel whitelist changed");

        verifyFuel(h, new BlockPos(2, 2, 2), Items.COAL, 500, 31, 4, null);
        verifyFuel(h, new BlockPos(4, 2, 2), Items.CHARCOAL, 500, 31, 4, null);
        verifyFuel(h, new BlockPos(6, 2, 2), Items.COAL_BLOCK, 5_000, 312, 8, null);
        verifyFuel(h, new BlockPos(8, 2, 2), Items.LAVA_BUCKET, 20_000, 1_249, 16, Items.BUCKET);

        ThermalGeneratorBlockEntity paused = generator(h, new BlockPos(10, 2, 2));
        CompoundTag state = new CompoundTag();
        state.putInt("EnergyStored", 99_992);
        state.putInt("FuelEnergyRemaining", 20);
        state.putInt("FuelEnergyTotal", 500);
        paused.load(state);
        tick(h, paused);
        h.assertTrue(paused.getStoredEnergy() == 100_000 && paused.getFuelEnergyRemaining() == 12,
                "Generator did not clamp conversion to 8 FE at full capacity");
        tick(h, paused);
        h.assertTrue(paused.getStoredEnergy() == 100_000 && paused.getFuelEnergyRemaining() == 12,
                "Full buffer consumed active fuel");

        var output = paused.getCapability(ForgeCapabilities.ENERGY,
                PowerCableBlock.utilityPortFace(paused.getBlockState())).orElseThrow(IllegalStateException::new);
        int extracted = output.extractEnergy(100, false);
        h.assertTrue(extracted == 16, "Thermal Generator output exceeded or missed 16 FE/t");
        tick(h, paused);
        h.assertTrue(paused.getStoredEnergy() == 99_996 && paused.getFuelEnergyRemaining() == 0,
                "Paused fuel did not resume from the same 12 FE remainder");

        ApocalypseFirstLight.LOGGER.info("[AFL THERMAL TEST] coal=500, charcoal=500, coal_block=5000, "
                + "lava_bucket=20000, exact remainders, 16 FE/t generation/output and full-buffer pause passed");
        h.succeed();
    }

    private static void verifyFuel(GameTestHelper h, BlockPos relative, Item fuel, int total,
                                   int fullTicksBeforeLast, int lastTickEnergy, Item expectedRemainder) {
        ThermalGeneratorBlockEntity generator = generator(h, relative);
        generator.setItem(ThermalGeneratorBlockEntity.FUEL_SLOT, new ItemStack(fuel));
        for (int tick = 0; tick < fullTicksBeforeLast; tick++) {
            tick(h, generator);
        }
        h.assertTrue(generator.getStoredEnergy() == total - lastTickEnergy,
                fuel + " produced the wrong pre-final total");
        h.assertTrue(generator.getFuelEnergyRemaining() == lastTickEnergy,
                fuel + " has the wrong final remainder");
        h.assertTrue(generator.getFuelEnergyTotal() == total, fuel + " loaded the wrong total energy");
        tick(h, generator);
        h.assertTrue(generator.getStoredEnergy() == total && generator.getFuelEnergyRemaining() == 0,
                fuel + " did not settle to its exact total");
        ItemStack slot = generator.getItem(ThermalGeneratorBlockEntity.FUEL_SLOT);
        if (expectedRemainder == null) {
            h.assertTrue(slot.isEmpty(), fuel + " was not consumed exactly once");
        } else {
            h.assertTrue(slot.is(expectedRemainder) && slot.getCount() == 1,
                    fuel + " did not preserve its container remainder");
        }
    }

    private static ThermalGeneratorBlockEntity generator(GameTestHelper h, BlockPos relative) {
        h.setBlock(relative, AflBlocks.THERMAL_GENERATOR.get());
        if (h.getBlockEntity(relative) instanceof ThermalGeneratorBlockEntity generator) {
            return generator;
        }
        throw new IllegalStateException("Thermal Generator block entity missing at " + relative);
    }

    private static void tick(GameTestHelper h, ThermalGeneratorBlockEntity generator) {
        ThermalGeneratorBlockEntity.serverTick(h.getLevel(), generator.getBlockPos(),
                generator.getBlockState(), generator);
    }
}
