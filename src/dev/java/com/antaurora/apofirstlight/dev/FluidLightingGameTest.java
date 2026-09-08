package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.antaurora.apofirstlight.fluid.FluidLighting;
import com.antaurora.apofirstlight.fluid.FluidPipeVisualManager;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import java.util.List;

public final class FluidLightingGameTest {
    public static void lights(GameTestHelper h) {
        var lava = new FluidStack(Fluids.LAVA, 1000);
        h.assertTrue(FluidLighting.emission(lava,9)==9 && FluidLighting.emission(lava,5)==5,"lava mapped light");
        h.assertTrue(FluidLighting.emission(new FluidStack(Fluids.WATER,1000),9)==0,"water unlit");
        var local = new BlockPos(3,2,3);
        h.setBlock(local,AflBlocks.FLUID_TANK.get());
        var tank = (FluidTankBlockEntity)h.getBlockEntity(local);
        var pipe = h.absolutePos(new BlockPos(8,2,8));
        h.getLevel().setBlock(pipe,AflBlocks.FLUID_PIPE.get().defaultBlockState(),3);
        h.runAfterDelay(3,()->{
            tank.restoreControllerFluid(lava);
            FluidTankBlockEntity.serverTick(h.getLevel(),tank.getBlockPos(),tank.getBlockState(),tank);
            FluidPipeVisualManager.markRoute(h.getLevel(),List.of(pipe),pipe.above(),pipe.below(),lava,true);
        });
        h.runAfterDelay(12,()->{
            h.assertTrue(tank.getBlockState().getLightEmission()==9,"tank lava emission");
            h.assertTrue(h.getLevel().getBrightness(LightLayer.BLOCK,tank.getBlockPos())==9,"tank propagated light");
            h.assertTrue(h.getLevel().getBlockState(pipe).getLightEmission()==5,"pipe held light");
            tank.clearLocalFluidForTopology();
            FluidTankBlockEntity.serverTick(h.getLevel(),tank.getBlockPos(),tank.getBlockState(),tank);
        });
        h.runAfterDelay(33,()->{
            h.assertTrue(tank.getBlockState().getLightEmission()==0,"empty tank unlit");
            h.assertTrue(h.getLevel().getBlockState(pipe).getLightEmission()==0,"expired pipe unlit");
            h.assertTrue(h.getLevel().getBrightness(LightLayer.BLOCK,pipe)==0,"pipe light removed");
            tank.restoreControllerFluid(new FluidStack(Fluids.WATER,1000));
            FluidTankBlockEntity.serverTick(h.getLevel(),tank.getBlockPos(),tank.getBlockState(),tank);
            h.assertTrue(tank.getBlockState().getLightEmission()==0,"water tank unlit");
            h.succeed();
        });
    }
}
