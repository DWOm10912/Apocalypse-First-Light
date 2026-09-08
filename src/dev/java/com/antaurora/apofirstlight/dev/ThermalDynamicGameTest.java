package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.block.ThermalGeneratorBlock;
import com.antaurora.apofirstlight.blockentity.ThermalGeneratorBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

public final class ThermalDynamicGameTest {
    private static void tick(GameTestHelper h,ThermalGeneratorBlockEntity g) {
        ThermalGeneratorBlockEntity.serverTick(h.getLevel(),g.getBlockPos(),g.getBlockState(),g);
    }
    public static void states(GameTestHelper h) {
        var pos=new BlockPos(5,2,5);h.setBlock(pos,AflBlocks.THERMAL_GENERATOR.get());
        var g=(ThermalGeneratorBlockEntity)h.getBlockEntity(pos);
        tick(h,g);h.assertTrue(g.getVisualState()==ThermalGeneratorBlockEntity.VisualState.OFF && g.getBlockState().getLightEmission()==0,"OFF light");
        for(var item:new Item[]{Items.COAL,Items.CHARCOAL,Items.COAL_BLOCK}) {
            g.load(new CompoundTag());g.setItem(0,new ItemStack(item,8));tick(h,g);
            h.assertTrue(g.getVisualState()==ThermalGeneratorBlockEntity.VisualState.RUNNING && g.getBlockState().getLightEmission()==9,"RUNNING "+item);
            var client=new ThermalGeneratorBlockEntity(g.getBlockPos(),g.getBlockState());client.setLevel(h.getLevel());client.handleUpdateTag(g.getUpdateTag());
            h.assertTrue(client.getItem(0).is(item) && client.getVisualState()==g.getVisualState(),"synced fuel/state");
            h.assertTrue(Math.abs(client.getRotorTime(.75f)-client.getRotorTime(.25f)-.5)<.0001,"partial tick smoothness");
        }
        var saved=g.saveWithoutMetadata();long rotor=saved.getLong("RotorTicks");g.load(saved);
        h.assertTrue(g.saveWithoutMetadata().getLong("RotorTicks")==rotor,"rotor persistence");
        g.load(new CompoundTag());g.restoreLiquid(new FluidStack(Fluids.LAVA,4000));tick(h,g);
        h.assertTrue(g.getVisualState()==ThermalGeneratorBlockEntity.VisualState.RUNNING && g.getLiquidAmount()==3999,"liquid live state");
        h.runAfterDelay(8,()->{
            h.assertTrue(h.getLevel().getBrightness(LightLayer.BLOCK,g.getBlockPos())==9,"real block light propagated");
            var full=g.saveWithoutMetadata();full.putInt("EnergyStored",100000);g.load(full);tick(h,g);
            h.assertTrue(g.getVisualState()==ThermalGeneratorBlockEntity.VisualState.FULL && g.getBlockState().getLightEmission()==0,"FULL no light");
            double angle=g.getRotorTime(.1f);h.assertTrue(angle==g.getRotorTime(.9f),"FULL rotor fixed");
            var client=new ThermalGeneratorBlockEntity(g.getBlockPos(),g.getBlockState());client.setLevel(h.getLevel());client.handleUpdateTag(g.getUpdateTag());
            h.assertTrue(client.getVisualState()==ThermalGeneratorBlockEntity.VisualState.FULL && client.getRotorTime(0)==angle,"paused sync");
        });
        h.runAfterDelay(18,()->{
            h.assertTrue(h.getLevel().getBrightness(LightLayer.BLOCK,g.getBlockPos())==0,"light removed after pause");
            for(var facing:Direction.Plane.HORIZONTAL) {
                var state=g.getBlockState().setValue(ThermalGeneratorBlock.FACING,facing);
                h.getLevel().setBlock(g.getBlockPos(),state,3);tick(h,g);
                h.assertTrue(g.getVisualState()==ThermalGeneratorBlockEntity.VisualState.FULL && state.getLightEmission()==0,"facing paused light");
            }
            h.succeed();
        });
    }
}
