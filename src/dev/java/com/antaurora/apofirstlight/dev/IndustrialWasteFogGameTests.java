package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.fluid.IndustrialWasteFog;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Tests actual camera-volume selection in a server world, not client rendering or subjective visibility. */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IndustrialWasteFogGameTests {
    @GameTest(template = "waste_empty")
    public static void onlySubmergedWasteCamera(GameTestHelper h) {
        BlockPos relative = new BlockPos(4, 2, 4);
        BlockPos absolute = h.absolutePos(relative);
        h.setBlock(relative.below(), Blocks.STONE);
        h.setBlock(relative, AflBlocks.INDUSTRIAL_WASTE.get());
        Vec3 base = Vec3.atBottomCenterOf(absolute);
        double surface = h.getLevel().getFluidState(absolute).getHeight(h.getLevel(), absolute);
        for (int i = 0; i < 10; i++) {
            h.assertTrue(IndustrialWasteFog.isCameraSubmerged(h.getLevel(), base.add(0, surface - 0.001, 0)),
                    "Submerged camera in source was not detected");
            h.assertTrue(!IndustrialWasteFog.isCameraSubmerged(h.getLevel(), base.add(0, surface, 0)),
                    "Camera exactly on surface must not be submerged");
            h.assertTrue(!IndustrialWasteFog.isCameraSubmerged(h.getLevel(), base.add(0, surface + 0.001, 0)),
                    "Camera above surface incorrectly fogged");
        }
        h.assertTrue(!IndustrialWasteFog.isCameraSubmerged(h.getLevel(), base.add(0, 1.62, 0)),
                "Feet in fluid but camera in air incorrectly fogged");
        h.assertTrue(!IndustrialWasteFog.isCameraSubmerged(h.getLevel(), base.add(1, 0.1, 0)),
                "Camera merely next to waste incorrectly fogged");

        h.setBlock(relative, AflFluids.FLOWING_INDUSTRIAL_WASTE.get().getFlowing(3, false).createLegacyBlock());
        double shallow = h.getLevel().getFluidState(absolute).getHeight(h.getLevel(), absolute);
        h.assertTrue(IndustrialWasteFog.isCameraSubmerged(h.getLevel(), base.add(0, shallow - 0.001, 0)),
                "Shallow flowing waste not detected");
        h.assertTrue(!IndustrialWasteFog.isCameraSubmerged(h.getLevel(), base.add(0, shallow + 0.001, 0)),
                "Shallow fluid used full-block height");

        for (var block : new net.minecraft.world.level.block.Block[] {Blocks.WATER, Blocks.LAVA, Blocks.POWDER_SNOW, Blocks.AIR}) {
            h.setBlock(relative, block);
            h.assertTrue(!IndustrialWasteFog.isCameraSubmerged(h.getLevel(), base.add(0, 0.1, 0)),
                    "Waste fog leaked into " + block);
        }
        h.assertTrue(IndustrialWasteFog.START == 1.5F && IndustrialWasteFog.END == 10.0F,
                "Unexpected V1 fog distances");
        ApocalypseFirstLight.LOGGER.info("[AFL WASTE FOG TEST] source/flowing camera surface boundary, "
                + "10 enter/exit cycles, feet-only, adjacent air, water/lava/powder snow exclusions passed; "
                + "RGB=(70,71,54), start=1.5, end=10. Client visuals NOT tested.");
        h.succeed();
    }
}
